package com.example.AOD.recommend;

import com.example.AOD.api.service.BookmarkService;
import com.example.AOD.recommend.log.EventLogRecord;
import com.example.AOD.recommend.log.LogQueue;
import com.example.AOD.recommend.log.LogWriter;
import com.example.AOD.recommend.log.PartitionMaintenanceJob;
import com.example.AOD.recommend.log.PartitionNames;
import com.example.AOD.recommend.log.RecLogJdbc;
import com.example.AOD.recommend.reaction.ReactionService;
import com.example.AOD.recommend.reaction.ReactionState;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import com.example.AOD.user.service.WithdrawService;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.repository.ContentRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 PostgreSQL 에서 V8 · 로그 풀 격리 · 중복 제거 · 반응 흐름 · 파티션 작업 · 회원 탈퇴를 검증한다.
 *
 * DB 는 둘 중 하나:
 *  - 환경변수 REC_IT_JDBC_URL(+ REC_IT_JDBC_USER / REC_IT_JDBC_PASSWORD) — 버려도 되는 DB 여야 한다(URL 에 "rec_it" 필수)
 *  - 없으면 Testcontainers postgres:16-alpine (Docker 필요)
 *
 * 빈 DB 에서는 Flyway V1 이 실패하므로(기존 문제) Flyway 자동 실행을 끄고, Hibernate 가 public 스키마를 만든 뒤
 * baseline 7 로 V8 만 돌린다 — 운영과 같은 상황(기존 스키마 + V8).
 * @Transactional 을 붙이지 않는다: 서버 이벤트는 커밋 뒤에 큐에 들어간다.
 * 둘 다 없으면 테스트는 건너뛴다(@EnabledIf).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("databaseAvailable")
class RecLogIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("REC_IT_JDBC_URL");
    private static PostgreSQLContainer<?> postgres;

    /** 외부 DB 환경변수도 없고 Docker 도 없으면 건너뛴다 (전체 테스트 실행을 깨뜨리지 않는다). */
    static boolean databaseAvailable() {
        if (EXTERNAL_URL != null && !EXTERNAL_URL.isBlank()) return true;
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        if (EXTERNAL_URL != null && !EXTERNAL_URL.isBlank()) {
            if (!EXTERNAL_URL.contains("rec_it")) {
                throw new IllegalStateException("REC_IT_JDBC_URL 은 버려도 되는 DB 여야 한다 — URL 에 'rec_it' 가 있어야 실행한다: " + EXTERNAL_URL);
            }
            r.add("spring.datasource.url", () -> EXTERNAL_URL);
            r.add("spring.datasource.username", () -> envOr("REC_IT_JDBC_USER", "postgres"));
            r.add("spring.datasource.password", () -> envOr("REC_IT_JDBC_PASSWORD", ""));
        } else {
            // PER_CLASS 수명에서는 Spring 컨텍스트가 @Testcontainers 의 beforeAll 보다 먼저 뜨므로 여기서 직접 시작한다
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("rec_it")
                    .withUsername("postgres")
                    .withPassword("postgres");
            postgres.start();
            r.add("spring.datasource.url", postgres::getJdbcUrl);
            r.add("spring.datasource.username", postgres::getUsername);
            r.add("spring.datasource.password", postgres::getPassword);
        }
        r.add("spring.flyway.enabled", () -> "false");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("rec.log.writer.enabled", () -> "false");
        r.add("sentry.dsn", () -> "");
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return v == null ? fallback : v;
    }

    private static final String CHILDREN_OF =
            "SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid "
          + "JOIN pg_class p ON p.oid = i.inhparent JOIN pg_namespace n ON n.oid = p.relnamespace "
          + "WHERE n.nspname = 'aod_log' AND p.relname = ?";

    @Autowired ApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate mainJdbc;
    @Autowired RecLogJdbc recLogJdbc;
    @Autowired LogQueue queue;
    @Autowired LogWriter writer;
    @Autowired PartitionMaintenanceJob job;
    @Autowired ReactionService reactionService;
    @Autowired BookmarkService bookmarkService;
    @Autowired UserRepository userRepository;
    @Autowired ContentRepository contentRepository;
    @Autowired WithdrawService withdrawService;

    private JdbcTemplate logJdbc;

    @BeforeAll
    void migrateV8OnTopOfExistingSchema() {
        logJdbc = recLogJdbc.jdbc();
        // Hibernate 가 만든 public 스키마 = "기존 DB". baseline 7 → V8 만 적용된다.
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("7")
                .load()
                .migrate();
    }

    @Test
    void logPoolIsHiddenFromBootAutoConfiguration() throws Exception {
        assertEquals(1, context.getBeansOfType(DataSource.class).size(),
                "로그 풀이 DataSource 빈이면 Boot 기본 DataSource·헬스 집계에 끼어든다");
        assertEquals(1, context.getBeansOfType(JdbcOperations.class).size(),
                "JdbcTemplate 빈을 하나 더 두면 Boot 의 JdbcTemplate 이 사라져 기존 주입 지점이 로그 풀에 붙는다");

        HikariDataSource logPool = recLogJdbc.pool();
        assertEquals("rec-log", logPool.getPoolName());
        assertEquals(2, logPool.getMaximumPoolSize());

        HikariDataSource mainPool = mainJdbc.getDataSource().unwrap(HikariDataSource.class);
        assertNotEquals("rec-log", mainPool.getPoolName(), "기존 JdbcTemplate 은 서비스 풀을 써야 한다");
    }

    @Test
    void v8CreatesSchemasDefaultAndMonthPartitions() {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        for (String table : List.of("rec_request", "rec_item_served", "event", "client_agent")) {
            List<String> children = logJdbc.queryForList(CHILDREN_OF, String.class, table);
            assertTrue(children.contains(table + "_default"), table + " DEFAULT 파티션");
            assertTrue(children.contains(PartitionNames.of(table, now)), table + " 이번 달 파티션");
            assertTrue(children.contains(PartitionNames.of(table, now.plusMonths(1))), table + " 다음 달 파티션");
        }
        Integer recTables = logJdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'aod_rec'", Integer.class);
        assertEquals(3, recTables, "rec_chain · not_interested · corpus_map");
    }

    @Test
    void writerDedupesByEventIdAcrossBatches() {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime t1 = OffsetDateTime.now(ZoneOffset.UTC);

        queue.offer(clientEvent(eventId, t1));
        writer.flushNow();
        queue.offer(clientEvent(eventId, t1.plusSeconds(3)));   // 재시도로 다시 온 같은 이벤트
        writer.flushNow();

        assertEquals(1, logJdbc.queryForObject(
                "SELECT count(*) FROM aod_log.event WHERE event_id = ?", Integer.class, eventId));
        assertEquals(1, logJdbc.queryForObject(
                "SELECT count(*) FROM aod_log.event_seen WHERE event_id = ?", Integer.class, eventId));
    }

    @Test
    void reactionAndBookmarkFlowIsLoggedWithFromTo() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("rec-it-" + suffix);
        user.setPassword("x");
        user.setEmail("rec-it-" + suffix + "@example.com");
        user = userRepository.save(user);

        Content content = new Content();
        content.setDomain(Domain.GAME);
        content.setMasterTitle("통합 테스트 작품 " + suffix);
        content = contentRepository.save(content);
        Long contentId = content.getContentId();

        reactionService.setReaction(contentId, user.getUsername(), ReactionState.LIKE);
        reactionService.setReaction(contentId, user.getUsername(), ReactionState.DISLIKE);
        reactionService.setReaction(contentId, user.getUsername(), ReactionState.DISLIKE);   // 멱등 — 이벤트 없음
        reactionService.toggle(contentId, user.getUsername(), ReactionState.DISLIKE);        // 기존 토글 경로 → NONE
        bookmarkService.toggleBookmark(contentId, user.getUsername());
        writer.flushNow();

        List<String> transitions = logJdbc.queryForList(
                "SELECT (payload->>'from') || '>' || (payload->>'to') FROM aod_log.event "
              + "WHERE event_type = 'reaction_changed' AND content_id = ? AND user_id = ?",
                String.class, contentId, user.getId());
        assertEquals(3, transitions.size(), "같은 상태 재지정은 이벤트를 남기지 않는다");
        assertEquals(Set.of("NONE>LIKE", "LIKE>DISLIKE", "DISLIKE>NONE"), Set.copyOf(transitions));

        assertEquals(1, logJdbc.queryForObject(
                "SELECT count(*) FROM aod_log.event WHERE event_type = 'bookmark_changed' AND content_id = ? "
              + "AND payload->>'state' = 'on'", Integer.class, contentId));

        assertEquals(0, logJdbc.queryForObject("SELECT count(*) FROM aod_log.event_default", Integer.class),
                "DEFAULT 파티션은 0행이어야 한다 (완료 조건)");
    }

    @Test
    void partitionJobEnsuresTwoMonthsAheadAndDropsExpired() {
        logJdbc.execute("CREATE TABLE IF NOT EXISTS aod_log.event_y2020m01 PARTITION OF aod_log.event "
                + "FOR VALUES FROM ('2020-01-01T00:00:00Z') TO ('2020-02-01T00:00:00Z')");

        job.ensureFuturePartitions();
        List<String> dropped = job.dropExpiredPartitions();
        job.purgeAuxTables();

        assertTrue(dropped.contains("event_y2020m01"));
        List<String> children = logJdbc.queryForList(CHILDREN_OF, String.class, "event");
        assertTrue(children.contains(PartitionNames.of("event", YearMonth.now(ZoneOffset.UTC).plusMonths(2))));
        assertFalse(children.contains("event_y2020m01"));
        assertTrue(children.contains("event_default"), "DEFAULT 는 지우지 않는다");
    }

    /**
     * 탈퇴: 그 회원의 활동·추천 상태·계정·로그(함께 쓰인 익명 ID 의 비로그인 행 포함)가 사라지고,
     * 리뷰는 작성자 없이 남으며, 같은 브라우저를 쓴 다른 회원의 행은 그대로다.
     */
    @Test
    void withdrawRemovesAccountAndLinkedLogsButKeepsReviewAndOtherUsers() {
        User leaver = newUser("leaver");
        User other = newUser("other");
        Long a = leaver.getId();
        Long b = other.getId();

        Content content = new Content();
        content.setDomain(Domain.GAME);
        content.setMasterTitle("탈퇴 테스트 작품 " + UUID.randomUUID());
        Long contentId = contentRepository.save(content).getContentId();

        reactionService.setReaction(contentId, leaver.getUsername(), ReactionState.LIKE);
        reactionService.setReaction(contentId, other.getUsername(), ReactionState.LIKE);
        bookmarkService.toggleBookmark(contentId, leaver.getUsername());
        mainJdbc.update("INSERT INTO reviews (content_id, user_id, rating, review_content, created_at) "
                + "VALUES (?, ?, 4.5, '남는 글', now())", contentId, a);

        Long mine = newCollection(a);
        Long theirs = newCollection(b);
        mainJdbc.update("INSERT INTO collection_items (collection_id, content_id, position, created_at) "
                + "VALUES (?, ?, 0, now())", mine, contentId);
        likeCollection(mine, b);      // 내 컬렉션이 받은 좋아요 — 컬렉션과 함께 사라진다
        likeCollection(theirs, a);    // 내가 누른 좋아요 — 그 컬렉션의 좋아요 수가 내려간다

        mainJdbc.update("INSERT INTO aod_rec.not_interested (user_id, content_id, created_at) VALUES (?, ?, now())", a, contentId);
        mainJdbc.update("INSERT INTO aod_rec.not_interested (user_id, content_id, created_at) VALUES (?, ?, now())", b, contentId);
        mainJdbc.update("INSERT INTO aod_rec.rec_chain (chain_id, user_id, tab, updated_at) VALUES (?, ?, 'all', now())",
                UUID.randomUUID(), a);

        UUID sharedAnon = UUID.randomUUID();     // 같은 브라우저를 두 회원이 썼다
        UUID session = UUID.randomUUID();
        UUID mineReq = recRequest(a, sharedAnon, session);
        UUID anonReq = recRequest(null, sharedAnon, UUID.randomUUID());       // 로그인 전 — 익명 ID 로 이어진다
        UUID otherReq = recRequest(b, sharedAnon, UUID.randomUUID());
        UUID strangerReq = recRequest(null, UUID.randomUUID(), UUID.randomUUID());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID strangerAnon = UUID.randomUUID();
        queue.offer(event(a, sharedAnon, session, now));
        queue.offer(event(null, sharedAnon, UUID.randomUUID(), now));
        queue.offer(event(null, UUID.randomUUID(), session, now));            // 세션으로만 이어지는 익명 행
        queue.offer(event(b, sharedAnon, UUID.randomUUID(), now));
        queue.offer(event(null, strangerAnon, UUID.randomUUID(), now));
        writer.flushNow();
        logJdbc.update("INSERT INTO aod_log.client_agent (session_id, first_seen, user_agent) VALUES (?, now(), 'it')", session);

        withdrawService.withdraw(leaver);

        assertFalse(userRepository.existsById(a));
        assertTrue(userRepository.existsById(b));
        for (String table : List.of("content_likes", "bookmarks", "collections", "collection_likes",
                "aod_rec.not_interested", "aod_rec.rec_chain")) {
            assertEquals(0, count(mainJdbc, "SELECT count(*) FROM " + table + " WHERE user_id = ?", a), table);
        }
        assertEquals(0, count(mainJdbc, "SELECT count(*) FROM collection_items WHERE collection_id = ?", mine));
        assertEquals(0, count(mainJdbc, "SELECT count(*) FROM collection_likes WHERE collection_id = ?", mine));
        assertEquals(1, count(mainJdbc, "SELECT count(*) FROM reviews WHERE content_id = ? AND user_id IS NULL", contentId),
                "리뷰는 작성자 없이 남는다");

        assertEquals(1, count(mainJdbc, "SELECT count(*) FROM content_likes WHERE user_id = ?", b), "다른 회원의 좋아요");
        assertEquals(1, count(mainJdbc, "SELECT count(*) FROM aod_rec.not_interested WHERE user_id = ?", b));
        assertEquals(0, count(mainJdbc, "SELECT like_count FROM collections WHERE id = ?", theirs), "누른 좋아요 수를 되돌린다");

        assertEquals(0, count(logJdbc, "SELECT count(*) FROM aod_log.event WHERE user_id = ?", a));
        assertEquals(0, count(logJdbc, "SELECT count(*) FROM aod_log.event WHERE user_id IS NULL AND (anon_id = ? OR session_id = ?)",
                sharedAnon, session), "함께 쓰인 익명 ID · 세션의 비로그인 행");
        assertTrue(count(logJdbc, "SELECT count(*) FROM aod_log.event WHERE user_id = ?", b) >= 2, "다른 회원의 로그(반응 + 클릭)");
        assertEquals(1, count(logJdbc, "SELECT count(*) FROM aod_log.event WHERE anon_id = ?", strangerAnon), "무관한 익명 행");
        assertEquals(0, count(logJdbc, "SELECT count(*) FROM aod_log.rec_request WHERE request_id IN (?, ?)", mineReq, anonReq));
        assertEquals(0, count(logJdbc, "SELECT count(*) FROM aod_log.rec_item_served WHERE request_id IN (?, ?)", mineReq, anonReq));
        assertEquals(2, count(logJdbc, "SELECT count(*) FROM aod_log.rec_request WHERE request_id IN (?, ?)", otherReq, strangerReq));
        assertEquals(2, count(logJdbc, "SELECT count(*) FROM aod_log.rec_item_served WHERE request_id IN (?, ?)", otherReq, strangerReq));
        assertEquals(0, count(logJdbc, "SELECT count(*) FROM aod_log.client_agent WHERE session_id = ?", session));

        // 같은 아이디 · 이메일로 다시 가입된다
        User again = new User();
        again.setUsername(leaver.getUsername());
        again.setPassword("x");
        again.setEmail(leaver.getEmail());
        assertNotEquals(a, userRepository.save(again).getId());
    }

    private User newUser(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(label + "-" + suffix);
        user.setPassword("x");
        user.setEmail(label + "-" + suffix + "@example.com");
        return userRepository.save(user);
    }

    private Long newCollection(Long userId) {
        return mainJdbc.queryForObject("INSERT INTO collections (user_id, title, domain, tint, visibility, like_count, view_count, created_at) "
                + "VALUES (?, '탈퇴 테스트', 'GAME', 'PINE', 'PUBLIC', 0, 0, now()) RETURNING id", Long.class, userId);
    }

    private void likeCollection(Long collectionId, Long userId) {
        mainJdbc.update("INSERT INTO collection_likes (collection_id, user_id, created_at) VALUES (?, ?, now())", collectionId, userId);
        mainJdbc.update("UPDATE collections SET like_count = like_count + 1 WHERE id = ?", collectionId);
    }

    /** 요청 1건 + 응답 작품 1개. */
    private UUID recRequest(Long userId, UUID anonId, UUID sessionId) {
        UUID requestId = UUID.randomUUID();
        logJdbc.update("INSERT INTO aod_log.rec_request (request_id, served_at, chain_id, page_depth, user_id, anon_id, session_id, "
                + "surface, tab, seed_ids, seed_sources, disliked_ids, excluded_ids, seen_ids, versions, fallback) "
                + "VALUES (?, now(), ?, 0, ?, ?, ?, 'home_rec', 'all', '{}', '{}', '{}', '{}', '{}', '{}', false)",
                requestId, UUID.randomUUID(), userId, anonId, sessionId);
        logJdbc.update("INSERT INTO aod_log.rec_item_served (impression_id, served_at, request_id, content_id, platform, corpus_key, "
                + "rank_position, candidate_source, score) VALUES (?, now(), ?, 1, 'steam', '1', 0, 'content_sim', '{}')",
                UUID.randomUUID(), requestId);
        return requestId;
    }

    private static int count(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static EventLogRecord event(Long userId, UUID anonId, UUID sessionId, OffsetDateTime serverTs) {
        return new EventLogRecord(UUID.randomUUID(), serverTs, "card_clicked", "client",
                userId, anonId, sessionId, 1L, null, null, "home_rec", "{}", serverTs, "test", "desktop");
    }

    private static EventLogRecord clientEvent(UUID eventId, OffsetDateTime serverTs) {
        return new EventLogRecord(eventId, serverTs, "card_clicked", "client",
                null, UUID.randomUUID(), UUID.randomUUID(), 1L, null, null,
                "for_you", "{\"k\":1}", serverTs, "test", "desktop");
    }
}
