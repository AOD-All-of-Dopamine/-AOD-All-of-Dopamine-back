package com.example.AOD.recommend;

import com.example.AOD.domain.Bookmark;
import com.example.AOD.domain.ContentLike;
import com.example.AOD.domain.Review;
import com.example.AOD.repo.BookmarkRepository;
import com.example.AOD.repo.ContentLikeRepository;
import com.example.AOD.repo.ReviewRepository;
import com.example.AOD.recommend.api.RecommendService;
import com.example.AOD.recommend.api.dto.RecommendResponse;
import com.example.AOD.recommend.catalog.CatalogKeyService;
import com.example.AOD.recommend.chain.Chain;
import com.example.AOD.recommend.chain.ChainService;
import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.key.CorpusKey;
import com.example.AOD.recommend.key.CorpusKeyService;
import com.example.AOD.recommend.log.LogQueue;
import com.example.AOD.recommend.log.LogWriter;
import com.example.AOD.recommend.log.PartitionMaintenanceJob;
import com.example.AOD.recommend.log.RecItemServedLogRecord;
import com.example.AOD.recommend.log.RecLogJdbc;
import com.example.AOD.recommend.log.RecRequestLogRecord;
import com.example.AOD.recommend.notinterested.NotInterestedService;
import com.example.AOD.recommend.reaction.ContentNotFoundException;
import com.example.AOD.recommend.router.RecRouterClient;
import com.example.AOD.recommend.router.RouterResult;
import com.example.AOD.recommend.router.dto.RouterItem;
import com.example.AOD.recommend.router.dto.RouterResponse;
import com.example.AOD.recommend.router.dto.RouterVersions;
import com.example.AOD.recommend.seed.Seed;
import com.example.AOD.recommend.seed.SeedResolver;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import com.example.shared.repository.ContentRepository;
import com.example.shared.repository.PlatformDataRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 실제 PostgreSQL 에서 V9 · 체인 · 관심 없음 · 로그 2종 · 키 매핑 쿼리 · 시드 규칙 ·
 * 추천 흐름 조립(라우터만 스텁) · 정리 작업을 검증한다.
 * DB 선택 규칙과 Flyway baseline 처리는 RecLogIntegrationTest 와 같다.
 *
 * RecommendService.recommend(...) 는 RecRouterClient 만 @MockBean 으로 스텁하고 나머지는 전부 실제
 * 빈(SeedResolver·CorpusKeyService·ChainService·CardAssembler·WorkApiService·FallbackProvider 등)을
 * 실 DB 위에서 그대로 돌린다 — Task 2·4 에서 추가한 JPQL 이 Hibernate 6 + 실 스키마에서 정말 도는지는
 * 여기서만 확인할 수 있다(단위 테스트는 리포지토리를 목으로 대체한다).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("databaseAvailable")
class RecommendIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("REC_IT_JDBC_URL");
    private static PostgreSQLContainer<?> postgres;

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
                throw new IllegalStateException(
                        "REC_IT_JDBC_URL 은 버려도 되는 DB 여야 한다 — URL 에 'rec_it' 가 있어야 실행한다: " + EXTERNAL_URL);
            }
            r.add("spring.datasource.url", () -> EXTERNAL_URL);
            r.add("spring.datasource.username", () -> envOr("REC_IT_JDBC_USER", "postgres"));
            r.add("spring.datasource.password", () -> envOr("REC_IT_JDBC_PASSWORD", ""));
        } else {
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

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate mainJdbc;
    @Autowired RecLogJdbc recLogJdbc;
    @Autowired LogQueue queue;
    @Autowired LogWriter writer;
    @Autowired PartitionMaintenanceJob job;
    @Autowired ChainService chainService;
    @Autowired NotInterestedService notInterestedService;
    @Autowired CorpusKeyService corpusKeyService;
    @Autowired CatalogKeyService catalogKeyService;
    @Autowired SeedResolver seedResolver;
    @Autowired RecommendService recommendService;
    @Autowired UserRepository userRepository;
    @Autowired ContentRepository contentRepository;
    @Autowired PlatformDataRepository platformDataRepository;
    @Autowired ContentLikeRepository contentLikeRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired ReviewRepository reviewRepository;

    /** 추천 흐름의 유일한 외부 호출 지점 — 라우터만 목으로 바꾸고 나머지는 실 DB 그대로 돈다. */
    @MockBean RecRouterClient routerClient;

    private JdbcTemplate logJdbc;

    @BeforeAll
    void migrateOnTopOfExistingSchema() {
        logJdbc = recLogJdbc.jdbc();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("7")
                .load()
                .migrate();
    }

    // ---------- 픽스처 도우미 ----------

    private Long newUserId() {
        return newUser().getId();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("rec4-it-" + suffix);
        user.setPassword("x");
        user.setEmail("rec4-it-" + suffix + "@example.com");
        return userRepository.save(user);
    }

    private Content newContent(Domain domain, String title, boolean adult) {
        Content content = new Content();
        content.setDomain(domain);
        content.setMasterTitle(title);
        content.setIsAdult(adult);
        return contentRepository.save(content);
    }

    private void newPlatformData(Content content, String platformName, String psid) {
        PlatformData pd = new PlatformData();
        pd.setContent(content);
        pd.setPlatformName(platformName);
        pd.setPlatformSpecificId(psid);
        platformDataRepository.save(pd);
    }

    private void like(Long userId, Long contentId, ContentLike.LikeType type) {
        ContentLike like = new ContentLike();
        like.setUser(userRepository.getReferenceById(userId));
        like.setContent(contentRepository.getReferenceById(contentId));
        like.setLikeType(type);
        contentLikeRepository.save(like);
    }

    private void bookmark(Long userId, Long contentId) {
        Bookmark bookmark = new Bookmark();
        bookmark.setUser(userRepository.getReferenceById(userId));
        bookmark.setContent(contentRepository.getReferenceById(contentId));
        bookmarkRepository.save(bookmark);
    }

    private void review(Long userId, Long contentId, double rating) {
        Review review = new Review();
        review.setUser(userRepository.getReferenceById(userId));
        review.setContent(contentRepository.getReferenceById(contentId));
        review.setRating(rating);
        reviewRepository.save(review);
    }

    /** @CreationTimestamp 가 insert 시각을 못박으므로, 순서를 통제하려면 insert 뒤 네이티브 UPDATE 로 덮어써야 한다. */
    private void setLikeCreatedAt(Long userId, Long contentId, LocalDateTime at) {
        mainJdbc.update("UPDATE content_likes SET created_at = ? WHERE user_id = ? AND content_id = ?",
                at, userId, contentId);
    }

    private void setBookmarkCreatedAt(Long userId, Long contentId, LocalDateTime at) {
        mainJdbc.update("UPDATE bookmarks SET created_at = ? WHERE user_id = ? AND content_id = ?",
                at, userId, contentId);
    }

    private void setReviewUpdatedAt(Long userId, Long contentId, LocalDateTime at) {
        mainJdbc.update("UPDATE reviews SET updated_at = ? WHERE user_id = ? AND content_id = ?",
                at, userId, contentId);
    }

    private static RouterItem routerItem(String platform, String key, int rank) {
        return new RouterItem(platform, key, rank, null, "content_sim", false, 1.0, null, null);
    }

    private int countRecChains(Long userId) {
        return mainJdbc.queryForObject(
                "SELECT count(*) FROM aod_rec.rec_chain WHERE user_id = ?", Integer.class, userId);
    }

    // ---------- V9 ----------

    @Test
    void v9AddsSkippedKeysColumn() {
        String type = mainJdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns "
              + "WHERE table_schema = 'aod_rec' AND table_name = 'rec_chain' AND column_name = 'skipped_keys'",
                String.class);
        assertEquals("ARRAY", type);

        String nullable = mainJdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
              + "WHERE table_schema = 'aod_rec' AND table_name = 'rec_chain' AND column_name = 'skipped_keys'",
                String.class);
        assertEquals("NO", nullable);
    }

    // ---------- 체인 ----------

    @Test
    void chainRoundTripsArraysThroughPostgres() {
        Long userId = newUserId();
        UUID chainId = UUID.randomUUID();

        chainService.create(chainId, userId, "all", List.of(11L, 12L), List.of("steam:730", "tmdb:movie_603"));
        Chain found = chainService.find(chainId, userId, "all").orElseThrow();

        assertEquals(List.of(11L, 12L), found.seenIds(), "bigint[] 가 리터럴로 제대로 들어가고 읽힌다");
        assertEquals(List.of("steam:730", "tmdb:movie_603"), found.skippedKeys());
        assertEquals(0, found.pageDepth());

        Chain advanced = chainService.append(found, List.of(12L, 13L), List.of("steam:240"));
        Chain reread = chainService.find(chainId, userId, "all").orElseThrow();

        assertEquals(List.of(11L, 12L, 13L), reread.seenIds(), "중복은 합쳐진다");
        assertEquals(List.of("steam:730", "tmdb:movie_603", "steam:240"), reread.skippedKeys());
        assertEquals(1, reread.pageDepth());
        assertEquals(advanced.pageDepth(), reread.pageDepth());
    }

    @Test
    void chainIsInvisibleToOtherOwnersOtherTabsAndAfterOneDay() {
        Long userId = newUserId();
        Long otherUserId = newUserId();
        UUID chainId = UUID.randomUUID();
        chainService.create(chainId, userId, "all", List.of(1L), List.of());

        assertTrue(chainService.find(chainId, otherUserId, "all").isEmpty(), "남의 체인");
        assertTrue(chainService.find(chainId, userId, "game").isEmpty(), "다른 탭");
        assertTrue(chainService.find(UUID.randomUUID(), userId, "all").isEmpty(), "없는 체인");

        mainJdbc.update("UPDATE aod_rec.rec_chain SET updated_at = ? WHERE chain_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(25), chainId);
        assertTrue(chainService.find(chainId, userId, "all").isEmpty(), "24시간 지남");
    }

    // ---------- 관심 없음 ----------

    @Test
    void notInterestedIsIdempotentAndLogsOnlyOnChange() {
        Long userId = newUserId();
        Content content = newContent(Domain.GAME, "관심 없음 테스트", false);
        Long contentId = content.getContentId();

        assertTrue(notInterestedService.turnOn(userId, contentId));
        assertTrue(notInterestedService.turnOn(userId, contentId));
        assertEquals(List.of(contentId), notInterestedService.activeContentIds(userId));

        assertFalse(notInterestedService.turnOff(userId, contentId));
        assertFalse(notInterestedService.turnOff(userId, contentId));
        assertEquals(List.of(), notInterestedService.activeContentIds(userId));

        writer.flushNow();
        List<String> states = logJdbc.queryForList(
                "SELECT payload->>'state' FROM aod_log.event "
              + "WHERE event_type = 'not_interested_changed' AND user_id = ? AND content_id = ? "
              + "ORDER BY server_ts", String.class, userId, contentId);
        assertEquals(List.of("on", "off"), states, "상태가 바뀔 때만 이벤트가 남는다");

        assertThrows(ContentNotFoundException.class, () -> notInterestedService.turnOn(userId, -1L));
    }

    @Test
    void notInterestedOlderThanNinetyDaysIsIgnored() {
        Long userId = newUserId();
        Content content = newContent(Domain.GAME, "오래된 관심 없음", false);
        notInterestedService.turnOn(userId, content.getContentId());

        mainJdbc.update("UPDATE aod_rec.not_interested SET created_at = ? WHERE user_id = ? AND content_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(91), userId, content.getContentId());

        assertEquals(List.of(), notInterestedService.activeContentIds(userId));
    }

    // ---------- 로그 2종 ----------

    @Test
    void recRequestAndItemServedLandInThePartitionsWithRealArrays() {
        UUID requestId = UUID.randomUUID();
        UUID chainId = UUID.randomUUID();
        UUID impressionId = UUID.randomUUID();
        OffsetDateTime servedAt = OffsetDateTime.now(ZoneOffset.UTC);

        queue.offer(new RecRequestLogRecord(requestId, servedAt, chainId, 2, 7L, null, null, "rec_tab", "all",
                List.of(1L, 2L), List.of("like", "bookmark"), List.of(9L), List.of(8L), List.of(5L), List.of(4L),
                "{\"rec_ranker\":\"control\"}", "{\"backend\":\"it\",\"router\":\"c8ec317\"}",
                false, null, List.of("tmdb"), 412, null, null));
        queue.offer(new RecItemServedLogRecord(impressionId, servedAt, requestId, 11L, "steam", "730", 0,
                "content_sim", "like", 1L, "{\"final\":1.19}", "steam.v1", false, 1.0f, null, true, null));
        queue.offer(new RecItemServedLogRecord(UUID.randomUUID(), servedAt, requestId, 0L, "steam", "999", 1,
                "content_sim", null, null, "{}", null, false, 1.0f, null, false, "not_in_db"));
        writer.flushNow();

        Map<String, Object> row = logJdbc.queryForMap(
                "SELECT array_to_string(seed_ids, ',') AS seed_ids, "
              + "array_to_string(seed_sources, ',') AS seed_sources, "
              + "array_to_string(partial, ',') AS partial, "
              + "anon_id::text AS anon_id, session_id::text AS session_id, "
              + "versions->>'backend' AS backend, experiments->>'rec_ranker' AS variant, "
              + "page_depth, latency_ms, fallback "
              + "FROM aod_log.rec_request WHERE request_id = ?", requestId);

        assertEquals("1,2", row.get("seed_ids"));
        assertEquals("like,bookmark", row.get("seed_sources"));
        assertEquals("tmdb", row.get("partial"));
        assertEquals("00000000-0000-0000-0000-000000000000", row.get("anon_id"), "헤더가 없으면 nil UUID");
        assertEquals("00000000-0000-0000-0000-000000000000", row.get("session_id"));
        assertEquals("it", row.get("backend"));
        assertEquals("control", row.get("variant"));
        assertEquals(2, row.get("page_depth"));
        assertEquals(412, row.get("latency_ms"));
        assertEquals(false, row.get("fallback"));

        assertEquals(2, logJdbc.queryForObject(
                "SELECT count(*) FROM aod_log.rec_item_served WHERE request_id = ?", Integer.class, requestId));
        assertEquals("not_in_db", logJdbc.queryForObject(
                "SELECT dropped_reason FROM aod_log.rec_item_served WHERE request_id = ? AND is_served = false",
                String.class, requestId));
        assertEquals(1.19, logJdbc.queryForObject(
                "SELECT (score->>'final')::float8 FROM aod_log.rec_item_served WHERE impression_id = ?",
                Double.class, impressionId), 1e-9);

        assertEquals(0, logJdbc.queryForObject(
                "SELECT count(*) FROM aod_log.rec_request_default", Integer.class), "DEFAULT 파티션은 0행");
        assertEquals(0, logJdbc.queryForObject(
                "SELECT count(*) FROM aod_log.rec_item_served_default", Integer.class));
    }

    // ---------- 키 매핑 쿼리 ----------

    @Test
    void corpusKeyQueriesRunAgainstRealSchema() {
        Content game = newContent(Domain.GAME, "키 매핑 게임", false);
        Content movie = newContent(Domain.MOVIE, "키 매핑 영화", false);
        String steamId = "it-" + UUID.randomUUID();
        String tmdbId = "it-" + UUID.randomUUID();
        newPlatformData(game, "Steam", steamId);
        newPlatformData(movie, "TMDB_MOVIE", tmdbId);

        Map<Long, CorpusKey> keys = corpusKeyService.keysByContentId(
                List.of(game.getContentId(), movie.getContentId()));
        assertEquals(new CorpusKey("steam", steamId), keys.get(game.getContentId()));
        assertEquals(new CorpusKey("tmdb", "movie_" + tmdbId), keys.get(movie.getContentId()));

        Map<CorpusKey, Content> back = corpusKeyService.contentsByKey(List.of(
                new CorpusKey("steam", steamId),
                new CorpusKey("tmdb", "movie_" + tmdbId),
                new CorpusKey("steam", "it-missing")));
        assertEquals(2, back.size());
        assertEquals(game.getContentId(), back.get(new CorpusKey("steam", steamId)).getContentId());
        assertEquals(movie.getContentId(), back.get(new CorpusKey("tmdb", "movie_" + tmdbId)).getContentId());

        // JOIN FETCH 로 Content 를 함께 실었는지 — 트랜잭션 밖(이 테스트 메서드)에서 LAZY 필드를 읽어도 터지지 않아야 한다.
        assertEquals("키 매핑 게임", back.get(new CorpusKey("steam", steamId)).getMasterTitle(),
                "LazyInitializationException 없이 트랜잭션 밖에서 읽힌다");
        assertEquals(Boolean.FALSE, back.get(new CorpusKey("tmdb", "movie_" + tmdbId)).getIsAdult());
    }

    @Test
    void catalogKeysListNonAdultWorksOnly() {
        Content visible = newContent(Domain.WEBTOON, "공개 웹툰", false);
        Content adult = newContent(Domain.WEBTOON, "성인 웹툰", true);
        String visibleId = "wt-" + UUID.randomUUID();
        String adultId = "wt-" + UUID.randomUUID();
        newPlatformData(visible, "NaverWebtoon", visibleId);
        newPlatformData(adult, "NaverWebtoon", adultId);

        List<String> lines = List.of(catalogKeyService.keysText("webtoon").split("\n"));

        assertTrue(lines.contains(visibleId));
        assertFalse(lines.contains(adultId), "성인 작품은 엔진에 주지 않는다");
    }

    @Test
    void catalogKeysCoverSteamAndTmdbWithMovieAndTvPrefixes() {
        Content game = newContent(Domain.GAME, "카탈로그 게임", false);
        String steamId = "cat-steam-" + UUID.randomUUID();
        newPlatformData(game, "Steam", steamId);

        Content movie = newContent(Domain.MOVIE, "카탈로그 영화", false);
        String movieId = "cat-movie-" + UUID.randomUUID();
        newPlatformData(movie, "TMDB_MOVIE", movieId);

        Content tv = newContent(Domain.TV, "카탈로그 시리즈", false);
        String tvId = "cat-tv-" + UUID.randomUUID();
        newPlatformData(tv, "TMDB_TV", tvId);

        Content adultMovie = newContent(Domain.MOVIE, "카탈로그 성인 영화", true);
        String adultMovieId = "cat-adult-" + UUID.randomUUID();
        newPlatformData(adultMovie, "TMDB_MOVIE", adultMovieId);

        List<String> steamLines = List.of(catalogKeyService.keysText("steam").split("\n"));
        assertTrue(steamLines.contains(steamId));

        List<String> tmdbLines = List.of(catalogKeyService.keysText("tmdb").split("\n"));
        assertTrue(tmdbLines.contains("movie_" + movieId), "TMDB_MOVIE 는 movie_ 접두어");
        assertTrue(tmdbLines.contains("tv_" + tvId), "TMDB_TV 는 tv_ 접두어");
        assertFalse(tmdbLines.contains("movie_" + adultMovieId), "성인 영화는 제외된다");

        assertThrows(IllegalArgumentException.class, () -> catalogKeyService.keysText("unknown"),
                "라우터가 모르는 platform 은 거부한다");
    }

    // ---------- 시드 규칙 ----------

    @Test
    void seedResolverAppliesPriorityRecencyAndBlockingAgainstRealRows() {
        Long userId = newUserId();
        Content likedContent = newContent(Domain.GAME, "좋아요 시드", false);
        Content bookmarkedContent = newContent(Domain.GAME, "북마크 시드", false);
        Content reviewedGoodContent = newContent(Domain.GAME, "높은 평점 시드", false);
        Content bothLikeAndReview = newContent(Domain.GAME, "좋아요+리뷰 동시", false);
        Content reviewedBadContent = newContent(Domain.GAME, "낮은 평점 제외", false);
        Content dislikedContent = newContent(Domain.GAME, "싫어요 제외", false);

        like(userId, likedContent.getContentId(), ContentLike.LikeType.LIKE);
        like(userId, dislikedContent.getContentId(), ContentLike.LikeType.DISLIKE);
        like(userId, reviewedBadContent.getContentId(), ContentLike.LikeType.LIKE);   // 낮은 평점 리뷰가 이 좋아요도 막아야 한다
        bookmark(userId, bookmarkedContent.getContentId());
        review(userId, reviewedGoodContent.getContentId(), 4.5);
        review(userId, reviewedBadContent.getContentId(), 1.5);
        like(userId, bothLikeAndReview.getContentId(), ContentLike.LikeType.LIKE);
        review(userId, bothLikeAndReview.getContentId(), 5.0);

        // 최근순 검증을 위해 시각을 흩어 둔다 (오래된 것 → 최신 순: liked, bookmarked, reviewedGood, bothLikeAndReview)
        LocalDateTime base = LocalDateTime.now().minusDays(10);
        setLikeCreatedAt(userId, likedContent.getContentId(), base);
        setBookmarkCreatedAt(userId, bookmarkedContent.getContentId(), base.plusHours(1));
        setReviewUpdatedAt(userId, reviewedGoodContent.getContentId(), base.plusHours(2));
        setLikeCreatedAt(userId, bothLikeAndReview.getContentId(), base.plusHours(3));
        setReviewUpdatedAt(userId, bothLikeAndReview.getContentId(), base.plusHours(3));

        List<Long> disliked = seedResolver.disliked(userId);
        assertEquals(List.of(dislikedContent.getContentId()), disliked, "LikeType.DISLIKE 바인딩");

        List<Seed> seeds = seedResolver.resolve(userId, disliked);
        List<Long> seedIds = seeds.stream().map(Seed::contentId).toList();

        assertFalse(seedIds.contains(dislikedContent.getContentId()), "싫어요는 시드에서 빠진다");
        assertFalse(seedIds.contains(reviewedBadContent.getContentId()),
                "평점 2 이하는 좋아요가 있어도 통째로 제외된다");
        assertEquals(List.of(bothLikeAndReview.getContentId(), reviewedGoodContent.getContentId(),
                bookmarkedContent.getContentId(), likedContent.getContentId()), seedIds,
                "최근순 정렬 (updated_at/created_at 바인딩)");

        Map<Long, String> sourceByContentId = new HashMap<>();
        for (Seed seed : seeds) sourceByContentId.put(seed.contentId(), seed.source());
        assertEquals(Seed.LIKE, sourceByContentId.get(likedContent.getContentId()));
        assertEquals(Seed.BOOKMARK, sourceByContentId.get(bookmarkedContent.getContentId()));
        assertEquals(Seed.REVIEW, sourceByContentId.get(reviewedGoodContent.getContentId()));
        assertEquals(Seed.LIKE, sourceByContentId.get(bothLikeAndReview.getContentId()),
                "좋아요+리뷰 동시면 더 센 출처(좋아요)를 쓴다");
    }

    // ---------- 추천 흐름 (라우터만 스텁) ----------

    @Test
    void recommendEndToEndServesCardsInRouterOrderAndLogsSkippedKeys() {
        Long userId = newUserId();
        Content seedContent = newContent(Domain.GAME, "e2e 시드", false);
        newPlatformData(seedContent, "Steam", "e2e-seed-" + UUID.randomUUID());
        like(userId, seedContent.getContentId(), ContentLike.LikeType.LIKE);

        Content servedFirst = newContent(Domain.GAME, "1위 카드", false);
        String firstSteamId = "e2e-first-" + UUID.randomUUID();
        newPlatformData(servedFirst, "Steam", firstSteamId);

        Content servedSecond = newContent(Domain.GAME, "2위 카드", false);
        String secondSteamId = "e2e-second-" + UUID.randomUUID();
        newPlatformData(servedSecond, "Steam", secondSteamId);

        Content adultContent = newContent(Domain.GAME, "성인 카드", true);
        String adultSteamId = "e2e-adult-" + UUID.randomUUID();
        newPlatformData(adultContent, "Steam", adultSteamId);

        String notInDbSteamId = "e2e-missing-" + UUID.randomUUID();

        RouterItem firstItem = routerItem("steam", firstSteamId, 0);
        RouterItem missingItem = routerItem("steam", notInDbSteamId, 1);
        RouterItem secondItem = routerItem("steam", secondSteamId, 2);
        RouterItem adultItem = routerItem("steam", adultSteamId, 3);
        RouterResponse routerBody = new RouterResponse(
                List.of(firstItem, missingItem, secondItem, adultItem),
                Map.of("steam", true), Map.of(), List.of(),
                new RouterVersions("router-v1", Map.of()));
        given(routerClient.recommend(any())).willReturn(RouterResult.ok(routerBody, 120L));

        RecommendResponse response = recommendService.recommend(
                "game", null, 2, userId, "e2e-user", RecContext.EMPTY);

        assertFalse(response.fallback());
        assertEquals(2, response.items().size());
        assertEquals(servedFirst.getContentId(), response.items().get(0).work().getId(),
                "라우터 순서대로 — DB 에 있는 첫 후보가 0번 카드");
        assertEquals(servedSecond.getContentId(), response.items().get(1).work().getId(),
                "중간에 끼인 not_in_db 후보를 건너뛰고 다음 실후보가 1번 카드");
        assertFalse(response.hasMore(), "steam exhausted=true 라 이어 볼 것이 없다");

        UUID chainId = UUID.fromString(response.chainId());
        Map<String, Object> chainRow = mainJdbc.queryForMap(
                "SELECT array_to_string(seen_ids, ',') AS seen, array_to_string(skipped_keys, ',') AS skipped, "
              + "page_depth FROM aod_rec.rec_chain WHERE chain_id = ?", chainId);
        assertEquals(Set.of(servedFirst.getContentId().toString(), servedSecond.getContentId().toString()),
                Set.copyOf(List.of(((String) chainRow.get("seen")).split(","))), "서빙한 작품만 seen 에 남는다");
        assertEquals(Set.of("steam:" + notInDbSteamId, "steam:" + adultSteamId),
                Set.copyOf(List.of(((String) chainRow.get("skipped")).split(","))),
                "DB 에 없거나 성인이라 버린 키만 skipped_keys 에 남는다");
        assertEquals(0, chainRow.get("page_depth"));

        writer.flushNow();
        UUID requestId = UUID.fromString(response.requestId());

        Map<String, Object> requestRow = logJdbc.queryForMap(
                "SELECT fallback, chain_id::text AS chain_id FROM aod_log.rec_request WHERE request_id = ?", requestId);
        assertEquals(false, requestRow.get("fallback"));
        assertEquals(chainId.toString(), requestRow.get("chain_id"));

        List<Map<String, Object>> itemRows = logJdbc.queryForList(
                "SELECT corpus_key, content_id, is_served, dropped_reason, impression_id::text AS impression_id "
              + "FROM aod_log.rec_item_served WHERE request_id = ?", requestId);
        assertEquals(4, itemRows.size(), "서빙 2 + 탈락 2(not_in_db, adult)");
        Map<String, Map<String, Object>> byKey = new HashMap<>();
        for (Map<String, Object> row : itemRows) byKey.put((String) row.get("corpus_key"), row);

        Map<String, Object> firstRow = byKey.get(firstSteamId);
        assertEquals(true, firstRow.get("is_served"));
        assertEquals(servedFirst.getContentId(), ((Number) firstRow.get("content_id")).longValue());
        assertEquals(response.items().get(0).impressionId(), firstRow.get("impression_id"),
                "응답과 로그가 같은 impression_id 를 쓴다");

        Map<String, Object> secondRow = byKey.get(secondSteamId);
        assertEquals(true, secondRow.get("is_served"));
        assertEquals(servedSecond.getContentId(), ((Number) secondRow.get("content_id")).longValue());
        assertEquals(response.items().get(1).impressionId(), secondRow.get("impression_id"));

        Map<String, Object> missingRow = byKey.get(notInDbSteamId);
        assertEquals(false, missingRow.get("is_served"));
        assertEquals("not_in_db", missingRow.get("dropped_reason"));
        assertEquals(0L, ((Number) missingRow.get("content_id")).longValue(), "DB 에 없는 후보는 content_id 0");

        Map<String, Object> adultRow = byKey.get(adultSteamId);
        assertEquals(false, adultRow.get("is_served"));
        assertEquals("adult", adultRow.get("dropped_reason"));
        assertEquals(adultContent.getContentId(), ((Number) adultRow.get("content_id")).longValue());
    }

    @Test
    void recommendFallsBackWhenRouterFailsAndStillLogsWithoutAChain() {
        Long userId = newUserId();
        Content seedContent = newContent(Domain.GAME, "폴백 시드", false);
        newPlatformData(seedContent, "Steam", "fallback-seed-" + UUID.randomUUID());
        like(userId, seedContent.getContentId(), ContentLike.LikeType.LIKE);
        int chainCountBefore = countRecChains(userId);

        given(routerClient.recommend(any())).willReturn(RouterResult.failed(RouterResult.TIMEOUT, 2000L));

        RecommendResponse response = recommendService.recommend(
                "game", null, 5, userId, "e2e-fallback-user", RecContext.EMPTY);

        assertTrue(response.fallback());
        assertEquals("timeout", response.fallbackReason());
        assertEquals(0, response.pageDepth());
        assertFalse(response.hasMore());
        assertEquals(chainCountBefore, countRecChains(userId), "대체 응답은 체인을 만들지 않는다");

        writer.flushNow();
        UUID requestId = UUID.fromString(response.requestId());
        Map<String, Object> row = logJdbc.queryForMap(
                "SELECT fallback, fallback_reason FROM aod_log.rec_request WHERE request_id = ?", requestId);
        assertEquals(true, row.get("fallback"));
        assertEquals("timeout", row.get("fallback_reason"));

        Long itemCount = logJdbc.queryForObject(
                "SELECT count(*) FROM aod_log.rec_item_served WHERE request_id = ?", Long.class, requestId);
        assertEquals(response.items().size(), itemCount.intValue(), "대체 목록도 노출 로그를 남긴다");
    }

    // ---------- 정리 ----------

    @Test
    void dailyJobPurgesExpiredChainsAndOldNotInterested() {
        Long userId = newUserId();
        UUID staleChain = UUID.randomUUID();
        UUID freshChain = UUID.randomUUID();
        chainService.create(staleChain, userId, "all", List.of(1L), List.of());
        chainService.create(freshChain, userId, "game", List.of(1L), List.of());
        mainJdbc.update("UPDATE aod_rec.rec_chain SET updated_at = ? WHERE chain_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(25), staleChain);

        Content content = newContent(Domain.GAME, "정리 대상", false);
        notInterestedService.turnOn(userId, content.getContentId());
        mainJdbc.update("UPDATE aod_rec.not_interested SET created_at = ? WHERE user_id = ? AND content_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(91), userId, content.getContentId());

        job.purgeRecTables();

        assertEquals(0, mainJdbc.queryForObject(
                "SELECT count(*) FROM aod_rec.rec_chain WHERE chain_id = ?", Integer.class, staleChain));
        assertEquals(1, mainJdbc.queryForObject(
                "SELECT count(*) FROM aod_rec.rec_chain WHERE chain_id = ?", Integer.class, freshChain));
        assertEquals(0, mainJdbc.queryForObject(
                "SELECT count(*) FROM aod_rec.not_interested WHERE user_id = ?", Integer.class, userId));
    }

    @Test
    void chainServicePurgeMatchesTheJob() {
        Long userId = newUserId();
        UUID staleChain = UUID.randomUUID();
        chainService.create(staleChain, userId, "webnovel", List.of(1L), List.of());
        mainJdbc.update("UPDATE aod_rec.rec_chain SET updated_at = ? WHERE chain_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(30), staleChain);

        assertTrue(chainService.purgeExpired() >= 1);
        Optional<Chain> gone = chainService.find(staleChain, userId, "webnovel");
        assertTrue(gone.isEmpty());
    }
}
