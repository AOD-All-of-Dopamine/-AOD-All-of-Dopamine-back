package com.example.AOD.recommend.api;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.AOD.recommend.api.dto.RecommendResponse;
import com.example.AOD.recommend.card.Assembly;
import com.example.AOD.recommend.card.AssembledCard;
import com.example.AOD.recommend.card.CardAssembler;
import com.example.AOD.recommend.card.DroppedCandidate;
import com.example.AOD.recommend.chain.Chain;
import com.example.AOD.recommend.chain.ChainNotFoundException;
import com.example.AOD.recommend.chain.ChainService;
import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.experiment.ExperimentAssigner;
import com.example.AOD.recommend.fallback.FallbackProvider;
import com.example.AOD.recommend.flag.RecFeatureFlag;
import com.example.AOD.recommend.key.CorpusKey;
import com.example.AOD.recommend.key.CorpusKeyService;
import com.example.AOD.recommend.log.LogQueue;
import com.example.AOD.recommend.log.LogRecord;
import com.example.AOD.recommend.log.RecItemServedLogRecord;
import com.example.AOD.recommend.log.RecRequestLogRecord;
import com.example.AOD.recommend.notinterested.NotInterestedService;
import com.example.AOD.recommend.reason.ReasonBuilder;
import com.example.AOD.recommend.router.RecRouterClient;
import com.example.AOD.recommend.router.RouterResult;
import com.example.AOD.recommend.router.dto.RouterItem;
import com.example.AOD.recommend.router.dto.RouterRequest;
import com.example.AOD.recommend.router.dto.RouterResponse;
import com.example.AOD.recommend.router.dto.RouterScore;
import com.example.AOD.recommend.router.dto.RouterVersionInfo;
import com.example.AOD.recommend.router.dto.RouterVersions;
import com.example.AOD.recommend.seed.Seed;
import com.example.AOD.recommend.seed.SeedResolver;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.repository.ContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RecommendServiceTest {

    private final RecFeatureFlag featureFlag = mock(RecFeatureFlag.class);
    private final SeedResolver seedResolver = mock(SeedResolver.class);
    private final CorpusKeyService corpusKeyService = mock(CorpusKeyService.class);
    private final NotInterestedService notInterestedService = mock(NotInterestedService.class);
    private final ChainService chainService = mock(ChainService.class);
    private final RecRouterClient routerClient = mock(RecRouterClient.class);
    private final CardAssembler cardAssembler = mock(CardAssembler.class);
    private final ReasonBuilder reasonBuilder = new ReasonBuilder();
    private final FallbackProvider fallbackProvider = mock(FallbackProvider.class);
    private final ExperimentAssigner experimentAssigner = new ExperimentAssigner();
    private final WorkApiService workApiService = mock(WorkApiService.class);
    private final ContentRepository contentRepository = mock(ContentRepository.class);
    private final LogQueue logQueue = mock(LogQueue.class);

    private final RecommendService service = new RecommendService(
            featureFlag, seedResolver, corpusKeyService, notInterestedService, chainService, routerClient,
            cardAssembler, reasonBuilder, fallbackProvider, experimentAssigner, workApiService,
            contentRepository, logQueue, new ObjectMapper(), "abc1234");

    private static final RecContext CTX = new RecContext("rec_tab", null, null,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

    private static Content content(long id, String title, boolean adult) {
        Content c = new Content();
        c.setContentId(id);
        c.setDomain(Domain.GAME);
        c.setMasterTitle(title);
        c.setIsAdult(adult);
        return c;
    }

    private static RouterItem item(String platform, String key, int rank, String dominantSeed) {
        return new RouterItem(platform, key, rank, dominantSeed, "content_sim", false, 1.0,
                new RouterScore(1.19, 0.58, Map.of("rec_pct", 0.99)), "steam.v1");
    }

    private static RouterResponse routerResponse(List<RouterItem> items, Map<String, Boolean> exhausted) {
        return new RouterResponse(items, exhausted, Map.of(), List.of(),
                new RouterVersions("c8ec317", Map.of("steam", new RouterVersionInfo("c8ec317", "a10c", "tags_full"))));
    }

    private static WorkSummaryDTO dto(long id, String title) {
        return WorkSummaryDTO.builder().id(id).domain("GAME").title(title).build();
    }

    @BeforeEach
    void defaultHappyPathStubs() {
        given(featureFlag.allows(anyString())).willReturn(true);
        given(seedResolver.disliked(7L)).willReturn(List.of(90L));
        given(seedResolver.resolve(eq(7L), anyCollection()))
                .willReturn(List.of(new Seed(1L, Seed.LIKE, LocalDateTime.of(2026, 9, 18, 0, 0))));
        given(notInterestedService.activeContentIds(7L)).willReturn(List.of(80L));
        given(corpusKeyService.keysByContentId(anyCollection())).willAnswer(invocation -> {
            Map<Long, CorpusKey> out = new java.util.LinkedHashMap<>();
            for (Object id : (Iterable<?>) invocation.getArgument(0)) {
                out.put((Long) id, new CorpusKey("steam", "k" + id));
            }
            return out;
        });
        given(workApiService.toEnrichedSummaries(anyList())).willAnswer(invocation -> {
            List<Content> contents = invocation.getArgument(0);
            return contents.stream().map(c -> dto(c.getContentId(), c.getMasterTitle())).toList();
        });
        given(contentRepository.findByContentIdIn(anyList()))
                .willReturn(List.of(content(1L, "코코", false)));
        given(fallbackProvider.byTab(anyString(), anyInt())).willReturn(List.of(dto(500L, "대체작")));
    }

    private List<LogRecord> offeredLogs() {
        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(logQueue, org.mockito.Mockito.atLeastOnce()).offer(captor.capture());
        return captor.getAllValues();
    }

    // ---------- 대체 경로 ----------

    @Test
    void anonymousFallbackNeverTouchesTheRouterOrChain() {
        RecommendResponse response = service.anonymousFallback("all", 20, CTX);

        assertTrue(response.fallback());
        assertEquals("anonymous", response.fallbackReason());
        assertFalse(response.hasMore());
        assertEquals(0, response.pageDepth());
        assertNotNull(UUID.fromString(response.chainId()), "대체도 chainId 를 주지만 저장하지 않는다");
        assertEquals(1, response.items().size());
        assertNull(response.items().get(0).reason(), "대체 목록은 이유를 달지 않는다");
        verify(routerClient, never()).recommend(any());
        verify(chainService, never()).create(any(), anyLong(), anyString(), anyList(), anyList());

        RecRequestLogRecord requestLog = (RecRequestLogRecord) offeredLogs().get(0);
        assertTrue(requestLog.fallback());
        assertEquals("anonymous", requestLog.fallbackReason());
        assertNull(requestLog.userId());
        assertEquals("{}", requestLog.experimentsJson());
        assertTrue(requestLog.versionsJson().contains("\"backend\":\"abc1234\""));
    }

    @Test
    void requestLogRecordsTheHomeSurface() {
        RecContext home = new RecContext("home_rec", null, null, CTX.anonId(), CTX.sessionId());
        service.anonymousFallback("all", 20, home);

        RecRequestLogRecord requestLog = (RecRequestLogRecord) offeredLogs().get(0);
        assertEquals("home_rec", requestLog.surface(), "홈 요청은 추천 탭 지표와 갈라서 적는다");
    }

    @Test
    void requestLogWritesRecTabForAnUnknownSurface() {
        RecContext unknown = new RecContext("anything", null, null, CTX.anonId(), CTX.sessionId());
        service.anonymousFallback("all", 20, unknown);

        RecRequestLogRecord requestLog = (RecRequestLogRecord) offeredLogs().get(0);
        assertEquals("rec_tab", requestLog.surface(), "허용 목록 밖의 값이 지표를 쪼개지 않는다");
    }

    @Test
    void requestLogWritesRecTabWhenNoSurfaceIsGiven() {
        RecContext none = new RecContext(null, null, null, CTX.anonId(), CTX.sessionId());
        service.anonymousFallback("all", 20, none);

        RecRequestLogRecord requestLog = (RecRequestLogRecord) offeredLogs().get(0);
        assertEquals("rec_tab", requestLog.surface(), "파라미터가 없던 기존 요청은 그대로 추천 탭");
    }

    @Test
    void disabledFlagFallsBack() {
        given(featureFlag.allows("tester")).willReturn(false);

        RecommendResponse response = service.recommend("all", null, 20, 7L, "tester", CTX);

        assertEquals("disabled", response.fallbackReason());
        verify(seedResolver, never()).resolve(anyLong(), anyCollection());
    }

    @Test
    void noSeedAndNoSeedPlatformAreDifferentReasons() {
        given(seedResolver.resolve(eq(7L), anyCollection())).willReturn(List.of());
        assertEquals("no_seed", service.recommend("all", null, 20, 7L, "tester", CTX).fallbackReason());

        given(seedResolver.resolve(eq(7L), anyCollection()))
                .willReturn(List.of(new Seed(1L, Seed.LIKE, LocalDateTime.of(2026, 9, 18, 0, 0))));
        // 시드는 steam 키인데 탭은 webtoon 이다
        assertEquals("no_seed_platform", service.recommend("webtoon", null, 20, 7L, "tester", CTX).fallbackReason());
        verify(routerClient, never()).recommend(any());
    }

    @Test
    void routerFailureBecomesTheFallbackReason() {
        given(routerClient.recommend(any())).willReturn(RouterResult.failed(RouterResult.TIMEOUT, 2000));
        assertEquals("timeout", service.recommend("all", null, 20, 7L, "tester", CTX).fallbackReason());

        given(routerClient.recommend(any())).willReturn(RouterResult.failed(RouterResult.CIRCUIT_OPEN, 0));
        assertEquals("circuit_open", service.recommend("all", null, 20, 7L, "tester", CTX).fallbackReason());

        given(routerClient.recommend(any())).willReturn(RouterResult.failed(RouterResult.SERVICE_ERROR, 5));
        assertEquals("service_error", service.recommend("all", null, 20, 7L, "tester", CTX).fallbackReason());
    }

    @Test
    void noCardAtAllIsEmpty() {
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(item("steam", "x", 0, "k1")), Map.of("steam", true)), 100));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(new Assembly(List.of(),
                List.of(new DroppedCandidate(item("steam", "x", 0, "k1"), null, DroppedCandidate.NOT_IN_DB))));

        assertEquals("empty", service.recommend("all", null, 20, 7L, "tester", CTX).fallbackReason());
    }

    @Test
    void unknownOrForeignChainIs404() {
        UUID chainId = UUID.randomUUID();
        given(chainService.find(chainId, 7L, "all")).willReturn(Optional.empty());

        assertThrows(ChainNotFoundException.class,
                () -> service.recommend("all", chainId, 20, 7L, "tester", CTX));
        verify(routerClient, never()).recommend(any());
    }

    // ---------- 정상 경로 ----------

    @Test
    void buildsCardsReasonsChainAndLogs() {
        RouterItem first = item("steam", "k11", 0, "k1");
        RouterItem second = item("steam", "k12", 1, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(first, second), Map.of("steam", false)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(new Assembly(
                List.of(new AssembledCard(first, content(11L, "작품11", false)),
                        new AssembledCard(second, content(12L, "작품12", false))),
                List.of(new DroppedCandidate(item("steam", "k99", 2, "k1"), null, DroppedCandidate.NOT_IN_DB))));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        // size 를 카드 수에 맞춰 둔다 — 정원이 차면 커버리지 재호출이 없다(재호출은 아래 전용 테스트에서).
        RecommendResponse response = service.recommend("all", null, 2, 7L, "tester", CTX);

        assertFalse(response.fallback());
        assertNull(response.fallbackReason());
        assertTrue(response.hasMore(), "steam 이 exhausted=false 다");
        assertEquals(2, response.items().size());
        assertEquals(0, response.items().get(0).rank());
        assertEquals(1, response.items().get(1).rank());
        assertEquals(11L, response.items().get(0).work().getId());
        assertEquals("like", response.items().get(0).reason().type());
        assertEquals(1L, response.items().get(0).reason().seedContentId());
        assertEquals("코코를 좋아해서", response.items().get(0).reason().text());
        assertNull(response.items().get(1).reason(), "주도 시드가 없으면 이유가 없다");
        assertNotNull(UUID.fromString(response.items().get(0).impressionId()));

        // 라우터 요청: k=size · buffer=50-size · 시드·싫어요·관심없음이 플랫폼별로 들어간다
        ArgumentCaptor<RouterRequest> request = ArgumentCaptor.forClass(RouterRequest.class);
        verify(routerClient).recommend(request.capture());
        assertEquals("all", request.getValue().tab());
        assertEquals(2, request.getValue().k());
        assertEquals(48, request.getValue().buffer(), "buffer = 50 - size");
        assertEquals(List.of("k1"), request.getValue().seeds().get("steam"));
        assertEquals(List.of("k90"), request.getValue().disliked().get("steam"));
        assertEquals(List.of("k80"), request.getValue().excluded().get("steam"));
        assertTrue(request.getValue().seen().isEmpty(), "새 체인이라 seen 이 없다");

        // 체인: 서빙한 content_id 만 seen 에, 버린 키만 skipped 에
        ArgumentCaptor<List<Long>> seen = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> skipped = ArgumentCaptor.forClass(List.class);
        verify(chainService).create(any(), eq(7L), eq("all"), seen.capture(), skipped.capture());
        assertEquals(List.of(11L, 12L), seen.getValue());
        assertEquals(List.of("steam:k99"), skipped.getValue());

        // 로그: rec_request 1 + rec_item_served 3 (서빙 2 + 탈락 1)
        List<LogRecord> logs = offeredLogs();
        RecRequestLogRecord requestLog = (RecRequestLogRecord) logs.get(0);
        assertEquals(List.of(1L), requestLog.seedIds());
        assertEquals(List.of("like"), requestLog.seedSources());
        assertEquals(List.of(90L), requestLog.dislikedIds());
        assertEquals(List.of(80L), requestLog.excludedIds());
        assertEquals("rec_tab", requestLog.surface());
        assertFalse(requestLog.fallback());
        assertTrue(requestLog.versionsJson().contains("\"router\":\"c8ec317\""));
        assertTrue(requestLog.versionsJson().contains("\"backend\":\"abc1234\""));
        assertTrue(requestLog.experimentsJson().contains("control"));

        List<RecItemServedLogRecord> itemLogs = logs.stream()
                .filter(r -> r instanceof RecItemServedLogRecord)
                .map(r -> (RecItemServedLogRecord) r).toList();
        assertEquals(3, itemLogs.size());
        assertTrue(itemLogs.get(0).isServed());
        assertEquals("like", itemLogs.get(0).reasonType());
        assertEquals(1L, itemLogs.get(0).reasonSeedId());
        assertEquals(response.items().get(0).impressionId(), itemLogs.get(0).impressionId().toString(),
                "응답과 로그가 같은 impression_id 를 쓴다");
        assertFalse(itemLogs.get(2).isServed());
        assertEquals("not_in_db", itemLogs.get(2).droppedReason());
        assertEquals(0L, itemLogs.get(2).contentId());
    }

    @Test
    void continuesAnExistingChainWithSeenAndSkipped() {
        UUID chainId = UUID.randomUUID();
        Chain existing = new Chain(chainId, 7L, "all", List.of(11L), List.of("steam:k99"), 0,
                java.time.OffsetDateTime.now());
        given(chainService.find(chainId, 7L, "all")).willReturn(Optional.of(existing));
        RouterItem next = item("steam", "k13", 0, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(next), Map.of("steam", false)), 200));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(next, content(13L, "작품13", false))), List.of()));
        given(chainService.append(eq(existing), anyList(), anyList())).willReturn(
                new Chain(chainId, 7L, "all", List.of(11L, 13L), List.of("steam:k99"), 1,
                        java.time.OffsetDateTime.now()));

        RecommendResponse response = service.recommend("all", chainId, 20, 7L, "tester", CTX);

        assertEquals(1, response.pageDepth());
        assertEquals(chainId.toString(), response.chainId());
        ArgumentCaptor<RouterRequest> request = ArgumentCaptor.forClass(RouterRequest.class);
        verify(routerClient).recommend(request.capture());
        assertEquals(List.of("k11"), request.getValue().seen().get("steam"));
        assertTrue(request.getValue().excluded().get("steam").contains("k99"),
                "체인에 기억해 둔 버린 키가 excluded 로 다시 나간다");
    }

    @Test
    void refillsOnceWhenCardsAreShortAndFirstCallWasFast() {
        RouterItem got = item("steam", "k11", 0, null);
        RouterItem more = item("steam", "k12", 0, null);
        given(routerClient.recommend(any()))
                .willReturn(RouterResult.ok(routerResponse(List.of(got), Map.of("steam", false)), 300))
                .willReturn(RouterResult.ok(routerResponse(List.of(more), Map.of("steam", false)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any()))
                .willReturn(new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))),
                        List.of(new DroppedCandidate(item("steam", "k99", 1, null), null, DroppedCandidate.NOT_IN_DB))))
                .willReturn(new Assembly(List.of(new AssembledCard(more, content(12L, "작품12", false))), List.of()));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        RecommendResponse response = service.recommend("all", null, 3, 7L, "tester", CTX);

        assertEquals(2, response.items().size());
        ArgumentCaptor<RouterRequest> requests = ArgumentCaptor.forClass(RouterRequest.class);
        verify(routerClient, times(2)).recommend(requests.capture());
        assertTrue(requests.getAllValues().get(1).excluded().get("steam").contains("k99"),
                "두 번째 호출은 방금 버린 키를 제외한다");
        assertTrue(requests.getAllValues().get(1).seen().get("steam").contains("k11"),
                "두 번째 호출은 방금 담은 카드를 seen 으로 알린다 — 같은 후보가 또 오면 버퍼만 먹는다");
        ArgumentCaptor<Set<Long>> used = ArgumentCaptor.forClass(Set.class);
        verify(cardAssembler, times(2)).assemble(anyList(), anyInt(), used.capture());
        assertTrue(used.getAllValues().get(1).contains(11L), "두 번째 조립은 이미 쓴 작품을 피한다");

        // 응답 순서·순위는 이어 붙인 뒤에도 0부터다
        assertEquals(0, response.items().get(0).rank());
        assertEquals(1, response.items().get(1).rank());
        assertEquals(12L, response.items().get(1).work().getId());
    }

    @Test
    void doesNotRefillWhenTheRequestBudgetIsAlreadySpent() {
        RouterItem got = item("steam", "k11", 0, null);
        // 재호출 판단 기준은 "요청이 시작된 뒤 흐른 시간"이다 — 첫 호출이 예산을 다 쓰면 두 번째를 부르지 않는다.
        given(routerClient.recommend(any())).willAnswer(invocation -> {
            Thread.sleep(RecommendService.REFILL_DEADLINE_MS + 150);
            return RouterResult.ok(routerResponse(List.of(got), Map.of("steam", false)), 300);
        });
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))),
                        List.of(new DroppedCandidate(item("steam", "k99", 1, null), null, DroppedCandidate.NOT_IN_DB))));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        service.recommend("all", null, 20, 7L, "tester", CTX);

        verify(routerClient, times(1)).recommend(any());
    }

    @Test
    void hasMoreIsFalseWhenEveryCalledPlatformIsExhausted() {
        RouterItem got = item("steam", "k11", 0, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(got), Map.of("steam", true)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        assertFalse(service.recommend("all", null, 20, 7L, "tester", CTX).hasMore());
    }

    @Test
    void platformMissingFromExhaustedCountsAsNotFinished() {
        RouterItem got = item("steam", "k11", 0, null);
        // 라우터가 steam 을 부르다 실패하면 exhausted 에 steam 이 없다(=partial) → "안 끝남"으로 본다
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(got), Map.of()), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        assertTrue(service.recommend("all", null, 20, 7L, "tester", CTX).hasMore());
    }

    @Test
    void chainAtTheSeenCapStopsPaging() {
        UUID chainId = UUID.randomUUID();
        List<Long> full = new ArrayList<>();
        for (int i = 0; i < ChainService.SEEN_MAX; i++) full.add((long) i);
        Chain existing = new Chain(chainId, 7L, "all", full, List.of(), 24, java.time.OffsetDateTime.now());
        given(chainService.find(chainId, 7L, "all")).willReturn(Optional.of(existing));
        RouterItem got = item("steam", "k11", 0, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(got), Map.of("steam", false)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(chainService.append(eq(existing), anyList(), anyList())).willReturn(
                new Chain(chainId, 7L, "all", full, List.of(), 25, java.time.OffsetDateTime.now()));

        assertFalse(service.recommend("all", chainId, 20, 7L, "tester", CTX).hasMore());
    }

    @Test
    void movieTabSendsAllTmdbSeedsRegardlessOfMedia() {
        given(seedResolver.resolve(eq(7L), anyCollection())).willReturn(List.of(
                new Seed(1L, Seed.LIKE, LocalDateTime.of(2026, 9, 18, 0, 0)),
                new Seed(2L, Seed.LIKE, LocalDateTime.of(2026, 9, 17, 0, 0))));
        given(corpusKeyService.keysByContentId(anyCollection())).willReturn(Map.of(
                1L, new CorpusKey("tmdb", "movie_603"),
                2L, new CorpusKey("tmdb", "tv_1399")));
        given(routerClient.recommend(any())).willReturn(RouterResult.failed(RouterResult.TIMEOUT, 2000));

        service.recommend("movie", null, 20, 7L, "tester", CTX);

        ArgumentCaptor<RouterRequest> request = ArgumentCaptor.forClass(RouterRequest.class);
        verify(routerClient).recommend(request.capture());
        assertEquals(Set.of("movie_603", "tv_1399"), Set.copyOf(request.getValue().seeds().get("tmdb")));
    }

    // ---------- 계획에 없던 방어 (Task 13 검토에서 추가) ----------

    @Test
    void chainWriteFailureStillServesTheCardsButStopsPaging() {
        RouterItem got = item("steam", "k11", 0, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(got), Map.of("steam", false)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList()))
                .willThrow(new DataAccessResourceFailureException("db down"));

        RecommendResponse response = service.recommend("all", null, 20, 7L, "tester", CTX);

        assertFalse(response.fallback(), "카드는 이미 만들었다 — 체인 저장 실패로 응답을 버리지 않는다");
        assertEquals(1, response.items().size());
        assertFalse(response.hasMore(), "다음 쪽에서 쓸 체인이 없으니 이어 볼 수 없다");
        assertEquals(0, response.pageDepth());
        assertNotNull(UUID.fromString(response.chainId()));
    }

    @Test
    void loggingFailureNeverBreaksTheResponse() {
        given(logQueue.offer(any())).willThrow(new IllegalStateException("큐 고장"));
        RouterItem got = item("steam", "k11", 0, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(got), Map.of("steam", false)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        assertEquals(1, service.recommend("all", null, 20, 7L, "tester", CTX).items().size());
        assertEquals(1, service.anonymousFallback("all", 20, CTX).items().size());
    }

    // ---------- 최종 리뷰 반영 ----------

    @Test
    void seedsBeyondThePlatformCapGoToExcludedSoTheyDoNotComeBackAsCards() {
        List<Seed> many = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            many.add(new Seed((long) i, Seed.LIKE, LocalDateTime.of(2026, 9, 18, 0, 0).minusHours(i)));
        }
        given(seedResolver.resolve(eq(7L), anyCollection())).willReturn(many);
        given(seedResolver.disliked(7L)).willReturn(List.of());
        given(notInterestedService.activeContentIds(7L)).willReturn(List.of());
        given(routerClient.recommend(any())).willReturn(RouterResult.failed(RouterResult.TIMEOUT, 2000));

        service.recommend("all", null, 20, 7L, "tester", CTX);

        ArgumentCaptor<RouterRequest> request = ArgumentCaptor.forClass(RouterRequest.class);
        verify(routerClient).recommend(request.capture());
        List<String> sent = request.getValue().seeds().get("steam");
        List<String> excluded = request.getValue().excluded().get("steam");
        assertEquals(RecommendService.MAX_SEEDS_PER_PLATFORM, sent.size());
        assertEquals(10, excluded.size(), "정원을 넘친 시드는 제외로 보낸다 — 엔진은 받은 시드만 제외한다");
        assertTrue(excluded.contains("k51"));
        assertFalse(sent.contains("k51"));

        // 로그의 seed_ids 는 "실제로 보낸" 시드다
        RecRequestLogRecord requestLog = (RecRequestLogRecord) offeredLogs().get(0);
        assertEquals(RecommendService.MAX_SEEDS_PER_PLATFORM, requestLog.seedIds().size());
        assertEquals(1L, requestLog.seedIds().get(0));
    }

    @Test
    void fallbackAfterARouterFailureKeepsTheStateWeAlreadyRead() {
        given(routerClient.recommend(any())).willReturn(RouterResult.failed(RouterResult.TIMEOUT, 2000));

        service.recommend("all", null, 20, 7L, "tester", CTX);

        RecRequestLogRecord requestLog = (RecRequestLogRecord) offeredLogs().get(0);
        assertEquals(List.of(1L), requestLog.seedIds(), "대체라고 빈 행을 남기면 원인 분석이 안 된다");
        assertEquals(List.of("like"), requestLog.seedSources());
        assertEquals(List.of(90L), requestLog.dislikedIds());
        assertEquals(List.of(80L), requestLog.excludedIds());
        assertEquals("timeout", requestLog.fallbackReason());
    }

    @Test
    void anyRepositoryFailureBecomesAFallbackNotAFiveHundred() {
        RouterItem got = item("steam", "k11", 0, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(got), Map.of("steam", true)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(workApiService.toEnrichedSummaries(anyList()))
                .willThrow(new DataAccessResourceFailureException("카드 보강 실패"));

        RecommendResponse response = service.recommend("all", null, 20, 7L, "tester", CTX);

        assertTrue(response.fallback());
        assertEquals("service_error", response.fallbackReason());
        assertEquals(1, response.items().size(), "대체 목록이라도 준다");
    }

    @Test
    void chainNotFoundStillPropagatesThroughTheSafetyNet() {
        UUID chainId = UUID.randomUUID();
        given(chainService.find(chainId, 7L, "all")).willReturn(Optional.empty());

        assertThrows(ChainNotFoundException.class,
                () -> service.recommend("all", chainId, 20, 7L, "tester", CTX),
                "404 는 대체로 삼키면 안 된다 — 프론트가 chainId 를 버려야 한다");
    }

    @Test
    void reasonTitleLookupFailureOnlyCostsTheReasonText() {
        RouterItem got = item("steam", "k11", 0, "k1");
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(got), Map.of("steam", true)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(contentRepository.findByContentIdIn(anyList()))
                .willThrow(new DataAccessResourceFailureException("제목 조회 실패"));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        RecommendResponse response = service.recommend("all", null, 20, 7L, "tester", CTX);

        assertFalse(response.fallback(), "이유 문구 하나 때문에 카드를 버리지 않는다");
        assertEquals(1, response.items().size());
        assertNull(response.items().get(0).reason());
    }

    @Test
    void emptyFallbackStillLogsWhatWasDroppedAndRemembersItInTheChain() {
        UUID chainId = UUID.randomUUID();
        Chain existing = new Chain(chainId, 7L, "all", List.of(5L), List.of(), 1, java.time.OffsetDateTime.now());
        given(chainService.find(chainId, 7L, "all")).willReturn(Optional.of(existing));
        RouterItem missing = item("steam", "k97", 0, null);
        RouterItem adult = item("steam", "k98", 1, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(missing, adult), Map.of("steam", false)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any()))
                .willReturn(new Assembly(List.of(),
                        List.of(new DroppedCandidate(missing, null, DroppedCandidate.NOT_IN_DB),
                                new DroppedCandidate(adult, 98L, DroppedCandidate.ADULT))))
                .willReturn(new Assembly(List.of(), List.of()));   // 커버리지 재호출도 빈손

        RecommendResponse response = service.recommend("all", chainId, 20, 7L, "tester", CTX);

        assertEquals("empty", response.fallbackReason());

        List<LogRecord> logs = offeredLogs();
        RecRequestLogRecord requestLog = (RecRequestLogRecord) logs.get(0);
        assertEquals(1, logs.stream().filter(r -> r instanceof RecRequestLogRecord).count(),
                "한 요청에 rec_request 행은 하나다");
        List<RecItemServedLogRecord> itemLogs = logs.stream()
                .filter(r -> r instanceof RecItemServedLogRecord)
                .map(r -> (RecItemServedLogRecord) r).toList();
        assertTrue(itemLogs.stream().allMatch(r -> r.requestId().equals(requestLog.requestId())),
                "버린 후보도 같은 request_id 밑에 남는다");
        assertEquals(List.of("not_in_db", "adult"),
                itemLogs.stream().filter(r -> !r.isServed()).map(RecItemServedLogRecord::droppedReason).toList(),
                "카드가 0장인 이유를 남겨야 커버리지를 쫓을 수 있다");

        verify(chainService).append(existing, List.of(), List.of("steam:k97", "steam:k98"));
        verify(chainService, never()).create(any(), anyLong(), anyString(), anyList(), anyList());
    }

    @Test
    void emptyFallbackWithoutAChainNeverCreatesOne() {
        RouterItem missing = item("steam", "k97", 0, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(missing), Map.of("steam", false)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(new Assembly(List.of(),
                List.of(new DroppedCandidate(missing, null, DroppedCandidate.NOT_IN_DB))));

        assertEquals("empty", service.recommend("all", null, 20, 7L, "tester", CTX).fallbackReason());

        verify(chainService, never()).create(any(), anyLong(), anyString(), anyList(), anyList());
        verify(chainService, never()).append(any(), anyList(), anyList());
    }

    @Test
    void chainSaveFailureOnAnExistingChainKeepsThatChainId() {
        UUID chainId = UUID.randomUUID();
        Chain existing = new Chain(chainId, 7L, "all", List.of(5L), List.of(), 2, java.time.OffsetDateTime.now());
        given(chainService.find(chainId, 7L, "all")).willReturn(Optional.of(existing));
        RouterItem got = item("steam", "k11", 0, null);
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(got), Map.of("steam", false)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(chainService.append(eq(existing), anyList(), anyList()))
                .willThrow(new DataAccessResourceFailureException("db down"));

        RecommendResponse response = service.recommend("all", chainId, 20, 7L, "tester", CTX);

        assertEquals(chainId.toString(), response.chainId(), "쓰던 체인의 id 를 엉뚱한 UUID 로 바꾸지 않는다");
        assertFalse(response.hasMore());
        assertEquals(1, response.items().size());
    }

    @Test
    void routerStringsThatCannotGoIntoSqlAreNotLogged() {
        RouterItem got = new RouterItem("steam", "k11", 0, null, "content\0sim", false, 1.0, null,
                "x".repeat(300));
        given(routerClient.recommend(any())).willReturn(
                RouterResult.ok(routerResponse(List.of(got), Map.of("steam", true)), 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        service.recommend("all", null, 20, 7L, "tester", CTX);

        RecItemServedLogRecord itemLog = (RecItemServedLogRecord) offeredLogs().stream()
                .filter(r -> r instanceof RecItemServedLogRecord).findFirst().orElseThrow();
        assertEquals(RecommendService.DEFAULT_CANDIDATE_SOURCE, itemLog.candidateSource(),
                "NUL 문자 하나가 200행 로그 배치를 통째로 깨뜨린다");
        assertNull(itemLog.factorSchema(), "200자를 넘는 값도 버린다");
    }

    @Test
    void refillMergesPartialFromBothCalls() {
        RouterItem got = item("steam", "k11", 0, null);
        RouterItem more = item("steam", "k12", 0, null);
        given(routerClient.recommend(any()))
                .willReturn(RouterResult.ok(new RouterResponse(List.of(got), Map.of("steam", false), Map.of(),
                        List.of("tmdb"), new RouterVersions("c8ec317", Map.of())), 100))
                .willReturn(RouterResult.ok(new RouterResponse(List.of(more), Map.of("steam", false), Map.of(),
                        List.of("webnovel"), new RouterVersions("c8ec317", Map.of())), 100));
        given(cardAssembler.assemble(anyList(), anyInt(), any()))
                .willReturn(new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))),
                        List.of(new DroppedCandidate(item("steam", "k99", 1, null), null, DroppedCandidate.NOT_IN_DB))))
                .willReturn(new Assembly(List.of(new AssembledCard(more, content(12L, "작품12", false))), List.of()));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        service.recommend("all", null, 3, 7L, "tester", CTX);

        RecRequestLogRecord requestLog = (RecRequestLogRecord) offeredLogs().get(0);
        assertEquals(List.of("tmdb", "webnovel"), requestLog.partial(),
                "두 호출 중 어느 쪽에서든 못 부른 플랫폼은 전부 남긴다");
    }

    @Test
    void routerDroppedSeedsAreRecordedAsDroppedSeedIds() {
        RouterItem got = item("steam", "k11", 0, null);
        RouterResponse body = new RouterResponse(List.of(got), Map.of("steam", false),
                Map.of("steam", List.of("k1")), List.of(),
                new RouterVersions("c8ec317", Map.of()));
        given(routerClient.recommend(any())).willReturn(RouterResult.ok(body, 300));
        given(cardAssembler.assemble(anyList(), anyInt(), any())).willReturn(
                new Assembly(List.of(new AssembledCard(got, content(11L, "작품11", false))), List.of()));
        given(chainService.create(any(), eq(7L), eq("all"), anyList(), anyList())).willAnswer(invocation ->
                new Chain(invocation.getArgument(0), 7L, "all", invocation.getArgument(3),
                        invocation.getArgument(4), 0, java.time.OffsetDateTime.now()));

        service.recommend("all", null, 20, 7L, "tester", CTX);

        RecRequestLogRecord requestLog = (RecRequestLogRecord) offeredLogs().get(0);
        assertEquals(List.of(1L), requestLog.droppedSeedIds(),
                "엔진이 버린 시드 키도 content_id 로 되돌려 남긴다 (설계 §3)");
    }
}
