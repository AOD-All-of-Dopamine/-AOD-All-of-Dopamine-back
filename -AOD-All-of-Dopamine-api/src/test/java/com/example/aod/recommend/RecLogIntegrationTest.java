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
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.repository.ContentRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
 * 실제 PostgreSQL 에서 V8 · 로그 풀 격리 · 중복 제거 · 반응 흐름 · 파티션 작업을 검증한다.
 *
 * DB 는 둘 중 하나:
 *  - 환경변수 REC_IT_JDBC_URL(+ REC_IT_JDBC_USER / REC_IT_JDBC_PASSWORD) — 버려도 되는 DB 여야 한다(URL 에 "rec_it" 필수)
 *  - 없으면 Testcontainers postgres:16-alpine (Docker 필요)
 *
 * 빈 DB 에서는 Flyway V1 이 실패하므로(기존 문제) Flyway 자동 실행을 끄고, Hibernate 가 public 스키마를 만든 뒤
 * baseline 7 로 V8 만 돌린다 — 운영과 같은 상황(기존 스키마 + V8).
 * @Transactional 을 붙이지 않는다: 서버 이벤트는 커밋 뒤에 큐에 들어간다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecLogIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("REC_IT_JDBC_URL");
    private static PostgreSQLContainer<?> postgres;

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

    private static EventLogRecord clientEvent(UUID eventId, OffsetDateTime serverTs) {
        return new EventLogRecord(eventId, serverTs, "card_clicked", "client",
                null, UUID.randomUUID(), UUID.randomUUID(), 1L, null, null,
                "for_you", "{\"k\":1}", serverTs, "test", "desktop");
    }
}
