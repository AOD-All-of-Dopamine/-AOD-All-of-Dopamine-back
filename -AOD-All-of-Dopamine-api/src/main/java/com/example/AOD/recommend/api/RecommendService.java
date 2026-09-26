package com.example.AOD.recommend.api;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.AOD.recommend.api.dto.RecommendItem;
import com.example.AOD.recommend.api.dto.RecommendResponse;
import com.example.AOD.recommend.card.AssembledCard;
import com.example.AOD.recommend.card.Assembly;
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
import com.example.AOD.recommend.key.CorpusKeyMapper;
import com.example.AOD.recommend.key.CorpusKeyService;
import com.example.AOD.recommend.log.LogQueue;
import com.example.AOD.recommend.log.LogRecord;
import com.example.AOD.recommend.log.RecItemServedLogRecord;
import com.example.AOD.recommend.log.RecRequestLogRecord;
import com.example.AOD.recommend.notinterested.NotInterestedService;
import com.example.AOD.recommend.reason.RecReason;
import com.example.AOD.recommend.reason.ReasonBuilder;
import com.example.AOD.recommend.router.RecRouterClient;
import com.example.AOD.recommend.router.RouterResult;
import com.example.AOD.recommend.router.RouterText;
import com.example.AOD.recommend.router.dto.RouterItem;
import com.example.AOD.recommend.router.dto.RouterRequest;
import com.example.AOD.recommend.router.dto.RouterVersions;
import com.example.AOD.recommend.seed.Seed;
import com.example.AOD.recommend.seed.SeedResolver;
import com.example.shared.entity.Content;
import com.example.shared.repository.ContentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 추천 응답 조립 (설계 §6).
 * 추천을 줄 수 없는 모든 경우(기능 꺼짐·시드 없음·라우터 실패·카드 0장·예상 밖 예외)는
 * 같은 모양의 대체 응답이 된다. 밖으로 새는 예외는 체인 404 하나뿐이다.
 *
 * 트랜잭션을 걸지 않는다(의도적):
 * 1) 흐름 한가운데에 라우터 HTTP 호출(최대 2초)이 있다. 메서드 전체를 트랜잭션으로 묶으면 그동안
 *    커넥션을 쥐고 있게 되는데, 이 서비스의 HikariCP 최대 풀은 5다(EC2 t3.small).
 * 2) readOnly 트랜잭션으로 묶으면 같은 데이터소스를 쓰는 체인 INSERT/UPDATE 가 PostgreSQL 의
 *    read-only 트랜잭션에서 거부된다.
 * 읽기는 각 리포지토리 호출이 스스로 짧은 트랜잭션을 열고 닫고, 체인 쓰기는 JdbcTemplate 자동 커밋으로
 * 즉시 확정된다(로그만 비동기다).
 */
@Slf4j
@Service
public class RecommendService {

    public static final String SURFACE = "rec_tab";
    /** 홈 추천 릴 (프론트 docs/superpowers/specs/2026-09-25-home-rec-rail-design.md). */
    public static final String HOME_SURFACE = "home_rec";
    /** 요청 로그에 적을 수 있는 surface. 밖의 값은 추천 탭으로 적는다 — 아무 문자열이나 지표를 쪼개지 않게. */
    static final Set<String> SURFACES = Set.of(SURFACE, HOME_SURFACE);
    /** 라우터에 요청할 총 후보 수 = k + buffer (설계 §4 — 커버리지가 낮아 버퍼를 크게 준다). */
    public static final int ROUTER_BUDGET = 50;
    /** 요청 전체 지연 예산 (REC_TAB_DESIGN §8-5). */
    public static final long TOTAL_BUDGET_MS = 2_500L;
    /**
     * 커버리지 재호출을 포기하는 선. **요청이 시작된 뒤 흐른 시간** 기준이다 —
     * 첫 호출의 지연만 보면 그 앞의 DB 조회 시간이 예산에서 빠진다.
     * 재호출은 읽기 제한(rec.router.read-timeout-ms, 기본 2.0초)까지 걸릴 수 있으므로
     * 0.7 + 2.0 = 2.7초가 최악이고, 전체 예산 2.5초와 거의 같다.
     */
    public static final long REFILL_DEADLINE_MS = 700L;
    /** 플랫폼당 시드 상한 (라우터 한도). */
    public static final int MAX_SEEDS_PER_PLATFORM = 50;
    /**
     * 플랫폼당 excluded 상한. 라우터는 플랫폼별 disliked+excluded+seen ≤ 5,000 을 강제한다(422).
     * disliked ≤ 1,000({@link SeedResolver#MAX_DISLIKED}) · seen ≤ 500({@link ChainService#SEEN_MAX})
     * + 재호출 때 덧붙는 이번 쪽 카드 ≤ 30(RecommendController.MAX_SIZE) · excluded ≤ 3,000 → 최대 4,530.
     */
    public static final int MAX_EXCLUDED_PER_PLATFORM = 3_000;

    static final String FALLBACK_PLATFORM = "fallback";
    static final String FALLBACK_SOURCE = "ranking_fallback";
    public static final String DEFAULT_CANDIDATE_SOURCE = "content_sim";

    /** 탭 → 부를 라우터 플랫폼 (설계 §6). movie·tv 는 tmdb 시드를 모두 쓴다 — 엔진이 media 로 후보만 가른다. */
    static final Map<String, List<String>> PLATFORMS_BY_TAB = Map.of(
            "all", List.of(CorpusKeyMapper.STEAM, CorpusKeyMapper.TMDB, CorpusKeyMapper.WEBNOVEL),
            "game", List.of(CorpusKeyMapper.STEAM),
            "movie", List.of(CorpusKeyMapper.TMDB),
            "tv", List.of(CorpusKeyMapper.TMDB),
            "webtoon", List.of(CorpusKeyMapper.WEBTOON),
            "webnovel", List.of(CorpusKeyMapper.WEBNOVEL));

    /** 컨트롤러의 tab 검증에 쓴다. */
    public static final Set<String> TABS = PLATFORMS_BY_TAB.keySet();

    private final RecFeatureFlag featureFlag;
    private final SeedResolver seedResolver;
    private final CorpusKeyService corpusKeyService;
    private final NotInterestedService notInterestedService;
    private final ChainService chainService;
    private final RecRouterClient routerClient;
    private final CardAssembler cardAssembler;
    private final ReasonBuilder reasonBuilder;
    private final FallbackProvider fallbackProvider;
    private final ExperimentAssigner experimentAssigner;
    private final WorkApiService workApiService;
    private final ContentRepository contentRepository;
    private final LogQueue logQueue;
    private final ObjectMapper objectMapper;
    private final String backendVersion;

    // @Value 를 생성자 파라미터에 쓰므로 Lombok 대신 명시적 생성자를 쓴다.
    public RecommendService(RecFeatureFlag featureFlag,
                            SeedResolver seedResolver,
                            CorpusKeyService corpusKeyService,
                            NotInterestedService notInterestedService,
                            ChainService chainService,
                            RecRouterClient routerClient,
                            CardAssembler cardAssembler,
                            ReasonBuilder reasonBuilder,
                            FallbackProvider fallbackProvider,
                            ExperimentAssigner experimentAssigner,
                            WorkApiService workApiService,
                            ContentRepository contentRepository,
                            LogQueue logQueue,
                            ObjectMapper objectMapper,
                            @Value("${rec.backend-version:dev}") String backendVersion) {
        this.featureFlag = featureFlag;
        this.seedResolver = seedResolver;
        this.corpusKeyService = corpusKeyService;
        this.notInterestedService = notInterestedService;
        this.chainService = chainService;
        this.routerClient = routerClient;
        this.cardAssembler = cardAssembler;
        this.reasonBuilder = reasonBuilder;
        this.fallbackProvider = fallbackProvider;
        this.experimentAssigner = experimentAssigner;
        this.workApiService = workApiService;
        this.contentRepository = contentRepository;
        this.logQueue = logQueue;
        this.objectMapper = objectMapper;
        this.backendVersion = backendVersion;
    }

    /** 토큰이 없는 요청 — 401 이 아니라 랭킹 대체를 준다 (REC_TAB_DESIGN §2-7). */
    public RecommendResponse anonymousFallback(String tab, int size, RecContext ctx) {
        return fallback(new Flow(tab, size, null, ctx), "anonymous");
    }

    /** 인증 판정 자체가 DB 문제로 실패했을 때 — 로그인 여부를 모르니 사용자 없이 같은 모양의 대체를 준다. */
    public RecommendResponse unavailableFallback(String tab, int size, RecContext ctx) {
        return fallback(new Flow(tab, size, null, ctx), "service_error");
    }

    /**
     * 로그인 사용자 추천.
     * @throws ChainNotFoundException chainId 가 없거나 만료·남의 것·다른 탭이다 (404)
     */
    public RecommendResponse recommend(String tab, UUID chainId, int size,
                                       Long userId, String username, RecContext ctx) {
        Flow flow = new Flow(tab, size, userId, ctx);
        try {
            return personalized(flow, chainId, username);
        } catch (ChainNotFoundException e) {
            throw e;                       // 404 — 프론트가 chainId 를 버리고 다시 물어야 한다
        } catch (RuntimeException e) {
            // @ControllerAdvice 가 없어서 여기서 안 잡으면 흰 화면(500)이 된다. 대체 목록이 언제나 낫다.
            log.error("추천 흐름 실패 — 대체 목록으로 돌린다 (userId={}, tab={})", userId, tab, e);
            return fallback(flow, "service_error");
        }
    }

    private RecommendResponse personalized(Flow flow, UUID chainId, String username) {
        if (!featureFlag.allows(username)) return fallback(flow, "disabled");

        if (chainId != null) {
            flow.chain = chainService.find(chainId, flow.userId, flow.tab)
                    .orElseThrow(() -> new ChainNotFoundException(chainId));
        }

        flow.disliked = seedResolver.disliked(flow.userId);
        List<Seed> seeds = seedResolver.resolve(flow.userId, flow.disliked);
        if (seeds.isEmpty()) return fallback(flow, "no_seed");

        Map<Long, CorpusKey> seedKeys =
                corpusKeyService.keysByContentId(seeds.stream().map(Seed::contentId).toList());
        // 키가 없는 시드(카카오페이지 전용 웹소설 등)는 라우터에 못 보낸다 — 로그에 남겨 커버리지를 쫓는다(설계 §3).
        flow.droppedSeedIds = seeds.stream()
                .map(Seed::contentId)
                .filter(id -> !seedKeys.containsKey(id))
                .toList();

        List<String> platforms = PLATFORMS_BY_TAB.getOrDefault(flow.tab, List.of());
        Map<String, List<String>> seedsByPlatform = new LinkedHashMap<>();
        Map<String, Long> seedContentIdByFlatKey = new HashMap<>();
        List<Seed> sentSeeds = new ArrayList<>();
        List<CorpusKey> overflowSeedKeys = new ArrayList<>();
        for (Seed seed : seeds) {
            CorpusKey key = seedKeys.get(seed.contentId());
            if (key == null || !platforms.contains(key.platform())) continue;
            List<String> lane = seedsByPlatform.computeIfAbsent(key.platform(), platform -> new ArrayList<>());
            if (lane.size() >= MAX_SEEDS_PER_PLATFORM) {
                // 엔진은 "받은 시드"만 후보에서 뺀다 — 정원을 넘겨 못 보낸 시드는 제외로 보내야
                // 이미 좋아요를 누른 작품이 추천으로 되돌아오지 않는다.
                overflowSeedKeys.add(key);
                continue;
            }
            lane.add(key.key());
            sentSeeds.add(seed);
            seedContentIdByFlatKey.put(CorpusKeyMapper.flat(key), seed.contentId());
        }
        if (seedsByPlatform.isEmpty()) return fallback(flow, "no_seed_platform");
        flow.sentSeeds = sentSeeds;
        flow.calledPlatforms = seedsByPlatform.keySet();
        flow.seedContentIdByFlatKey = seedContentIdByFlatKey;

        flow.notInterested = notInterestedService.activeContentIds(flow.userId);
        flow.chainSeen = flow.chain == null ? List.of() : flow.chain.seenIds();
        List<String> chainSkipped = flow.chain == null ? List.of() : flow.chain.skippedKeys();

        // 싫어요·관심 없음·체인 seen 의 키를 한 번의 조회로 가져온다(각각 부르면 platform_data 를 3번 읽는다).
        Map<Long, CorpusKey> keys =
                corpusKeyService.keysByContentId(union(flow.disliked, flow.notInterested, flow.chainSeen));
        Map<String, Set<String>> dislikedKeys = keysByPlatform(flow.disliked, keys, platforms);
        Map<String, Set<String>> seenKeys = keysByPlatform(flow.chainSeen, keys, platforms);
        // 상한을 넘겨 잘라야 할 때 살아남을 순서대로 넣는다: 이번 체인이 버린 키 > 못 보낸 시드 > 관심 없음(최신순).
        Map<String, Set<String>> excludedKeys = new LinkedHashMap<>();
        addFlatKeys(excludedKeys, chainSkipped, platforms);
        for (CorpusKey key : overflowSeedKeys) {
            excludedKeys.computeIfAbsent(key.platform(), platform -> new LinkedHashSet<>()).add(key.key());
        }
        for (Map.Entry<String, Set<String>> entry
                : keysByPlatform(flow.notInterested, keys, platforms).entrySet()) {
            excludedKeys.computeIfAbsent(entry.getKey(), platform -> new LinkedHashSet<>()).addAll(entry.getValue());
        }

        int buffer = Math.max(0, ROUTER_BUDGET - flow.size);
        RouterResult first = routerClient.recommend(new RouterRequest(flow.tab, flow.size, buffer,
                seedsByPlatform, toLists(dislikedKeys), toCappedLists(excludedKeys), toLists(seenKeys)));
        if (!first.ok()) return fallback(flow, first.failure());

        Assembly assembly = cardAssembler.assemble(first.response().items(), flow.size, new HashSet<>(flow.chainSeen));
        List<AssembledCard> cards = new ArrayList<>(assembly.cards());
        flow.dropped = new ArrayList<>(assembly.dropped());
        Map<String, Boolean> exhausted = new HashMap<>(
                first.response().exhausted() == null ? Map.of() : first.response().exhausted());
        Set<String> partial = new LinkedHashSet<>(
                first.response().partial() == null ? List.of() : first.response().partial());
        RouterVersions versions = first.response().versions();
        flow.droppedSeedIds = withRouterDroppedSeeds(flow.droppedSeedIds,
                first.response().droppedSeeds(), seedContentIdByFlatKey);

        // 커버리지 보정 (설계 §4): 카드가 모자라고 예산이 남았으면 한 번만 더 부른다.
        // 방금 버린 키는 excluded 로, 방금 담은 카드는 seen 으로 알린다 — 아니면 같은 후보가 또 와서 버퍼만 먹는다.
        // 버린 키가 없으면 부르지 않는다: 제외가 그대로면 엔진은 같은 목록을 돌려줘 왕복만 는다.
        if (cards.size() < flow.size && elapsedMs(flow.startedAt) < REFILL_DEADLINE_MS) {
            List<String> newlySkipped = skippedFlatKeys(flow.dropped);
            if (!newlySkipped.isEmpty()) {
                Map<String, Set<String>> excludedAgain = copyOf(excludedKeys);
                addFlatKeys(excludedAgain, newlySkipped, platforms);
                Map<String, Set<String>> seenAgain = copyOf(seenKeys);
                for (AssembledCard card : cards) {
                    seenAgain.computeIfAbsent(card.item().platform(), platform -> new LinkedHashSet<>())
                            .add(card.item().key());
                }
                RouterResult second = routerClient.recommend(new RouterRequest(flow.tab, flow.size, buffer,
                        seedsByPlatform, toLists(dislikedKeys), toCappedLists(excludedAgain), toLists(seenAgain)));
                if (second.ok()) {
                    Set<Long> used = new HashSet<>(flow.chainSeen);
                    for (AssembledCard card : cards) used.add(card.content().getContentId());
                    Assembly more = cardAssembler.assemble(
                            second.response().items(), flow.size - cards.size(), used);
                    cards.addAll(more.cards());
                    flow.dropped.addAll(more.dropped());
                    if (second.response().exhausted() != null) exhausted.putAll(second.response().exhausted());
                    if (second.response().partial() != null) partial.addAll(second.response().partial());
                    if (second.response().versions() != null) versions = second.response().versions();
                    flow.droppedSeedIds = withRouterDroppedSeeds(flow.droppedSeedIds,
                            second.response().droppedSeeds(), seedContentIdByFlatKey);
                }
            }
        }

        if (cards.isEmpty()) return fallback(flow, "empty");

        return respond(flow, cards, exhausted, new ArrayList<>(partial), versions);
    }

    /**
     * 한 요청 동안 모이는 상태. 대체 응답도 **여기까지 모은 것**을 로그에 남긴다 —
     * 대체라고 빈 행을 남기면 "왜 대체가 나갔는지"를 나중에 가릴 수 없다.
     */
    private static final class Flow {
        final String tab;
        final int size;
        final Long userId;
        final RecContext ctx;
        final long startedAt = System.nanoTime();
        final UUID requestId = UUID.randomUUID();

        Chain chain;
        Set<String> calledPlatforms = Set.of();
        List<Seed> sentSeeds = List.of();          // 실제로 라우터에 보낸 시드만
        Map<String, Long> seedContentIdByFlatKey = Map.of();   // "platform:key" → 시드 content_id (이유 문구용)
        List<Long> disliked = List.of();
        List<Long> notInterested = List.of();
        List<Long> chainSeen = List.of();
        List<Long> droppedSeedIds = List.of();
        List<DroppedCandidate> dropped = List.of();

        Flow(String tab, int size, Long userId, RecContext ctx) {
            this.tab = tab;
            this.size = size;
            this.userId = userId;
            this.ctx = ctx == null ? RecContext.EMPTY : ctx;
        }
    }

    // ---------- 응답 조립 ----------

    private RecommendResponse respond(Flow flow, List<AssembledCard> cards, Map<String, Boolean> exhausted,
                                      List<String> partial, RouterVersions versions) {
        OffsetDateTime servedAt = OffsetDateTime.now(ZoneOffset.UTC);

        List<WorkSummaryDTO> works =
                workApiService.toEnrichedSummaries(cards.stream().map(AssembledCard::content).toList());

        Map<Integer, Long> seedIdByCardIndex = new LinkedHashMap<>();
        for (int i = 0; i < cards.size(); i++) {
            RouterItem item = cards.get(i).item();
            if (item.dominantSeed() == null) continue;
            Long seedContentId = flow.seedContentIdByFlatKey.get(
                    CorpusKeyMapper.flat(new CorpusKey(item.platform(), item.dominantSeed())));
            if (seedContentId != null) seedIdByCardIndex.put(i, seedContentId);
        }
        Map<Long, String> seedTitles = seedTitles(seedIdByCardIndex.values());
        Map<Long, String> sourceBySeedId = new HashMap<>();
        for (Seed seed : flow.sentSeeds) sourceBySeedId.putIfAbsent(seed.contentId(), seed.source());

        List<RecommendItem> items = new ArrayList<>(cards.size());
        List<RecItemServedLogRecord> itemLogs = new ArrayList<>(cards.size() + flow.dropped.size());
        List<Long> servedContentIds = new ArrayList<>(cards.size());
        for (int i = 0; i < cards.size(); i++) {
            AssembledCard card = cards.get(i);
            RouterItem item = card.item();
            Long seedContentId = seedIdByCardIndex.get(i);
            RecReason reason = seedContentId == null ? null
                    : reasonBuilder.build(sourceBySeedId.get(seedContentId), seedContentId,
                        seedTitles.get(seedContentId));
            UUID impressionId = UUID.randomUUID();
            items.add(new RecommendItem(impressionId.toString(), i, works.get(i), reason));
            servedContentIds.add(card.content().getContentId());
            itemLogs.add(new RecItemServedLogRecord(impressionId, servedAt, flow.requestId,
                    card.content().getContentId(), item.platform(), item.key(), i,
                    candidateSource(item), reason == null ? null : reason.type(),
                    reason == null ? null : reason.seedContentId(), toJson(item.score()),
                    RouterText.safeOrNull(item.factorSchema()),
                    Boolean.TRUE.equals(item.isExploration()), propensity(item), null, true, null));
        }
        for (DroppedCandidate candidate : flow.dropped) itemLogs.add(droppedLog(flow, servedAt, candidate));

        Chain saved = saveChain(flow, servedContentIds, skippedFlatKeys(flow.dropped));
        UUID chainId = saved != null ? saved.chainId()
                : (flow.chain != null ? flow.chain.chainId() : UUID.randomUUID());
        int pageDepth = saved != null ? saved.pageDepth()
                : (flow.chain == null ? 0 : flow.chain.pageDepth() + 1);

        // 부른 플랫폼 중 하나라도 안 끝났고(exhausted 에 없으면 partial = 안 끝남), seen 이 상한 밑이어야 더 준다.
        // 체인을 못 남겼으면 다음 쪽이 같은 것을 또 주게 되므로 여기서 멈춘다.
        boolean anyPlatformLeft = flow.calledPlatforms.stream().anyMatch(p -> !Boolean.TRUE.equals(exhausted.get(p)));
        boolean hasMore = saved != null && anyPlatformLeft
                && saved.seenIds().size() < ChainService.SEEN_MAX && !items.isEmpty();

        offer(requestLog(flow, servedAt, chainId, pageDepth, toJson(experimentAssigner.assign(flow.userId)),
                versionsJson(versions), false, null, partial));
        for (RecItemServedLogRecord record : itemLogs) offer(record);

        return new RecommendResponse(flow.requestId.toString(), chainId.toString(), pageDepth,
                false, null, items, hasMore);
    }

    /** 이유 문구에 필요한 시드 제목을 한 번에 읽는다 (N+1 방지). 실패해도 카드는 나간다 — 문구만 빠진다. */
    private Map<Long, String> seedTitles(Collection<Long> seedContentIds) {
        if (seedContentIds.isEmpty()) return Map.of();
        Map<Long, String> titles = new HashMap<>();
        try {
            List<Long> ids = new ArrayList<>(new LinkedHashSet<>(seedContentIds));
            for (Content content : contentRepository.findByContentIdIn(ids)) {
                titles.put(content.getContentId(), content.getMasterTitle());
            }
        } catch (RuntimeException e) {
            log.warn("시드 제목 조회 실패 — 이유 문구 없이 카드만 준다", e);
            return Map.of();
        }
        return titles;
    }

    /**
     * 체인 저장. 실패해도 카드는 이미 만들어 놨으므로 응답을 버리지 않는다 — 대신 이어 보기를 끈다
     * (seen 을 못 남겼으니 다음 쪽은 같은 작품을 다시 준다). DB 가 상한 상황이라 error 로 남긴다.
     */
    private Chain saveChain(Flow flow, List<Long> servedIds, List<String> skipped) {
        try {
            Chain saved = (flow.chain == null)
                    ? chainService.create(UUID.randomUUID(), flow.userId, flow.tab, servedIds, skipped)
                    : chainService.append(flow.chain, servedIds, skipped);
            if (saved == null) {
                log.error("추천 체인을 남기지 못했다 — 이어 보기를 끈다 (userId={}, tab={})", flow.userId, flow.tab);
            }
            return saved;
        } catch (RuntimeException e) {
            log.error("추천 체인 저장 실패 — 카드는 그대로 주고 이어 보기만 끈다 (userId={}, tab={})",
                    flow.userId, flow.tab, e);
            return null;
        }
    }

    /**
     * 대체 응답. 체인을 **만들지** 않는다 — chainId 는 로그 상관용 새 UUID 다 (설계 §2).
     * 다만 카드가 0장이라 버린 후보가 있고 이어 보던 체인이 있으면, 그 키는 체인에 기억해 둔다
     * (같은 후보가 다음 쪽에서 또 버퍼를 먹지 않게).
     */
    private RecommendResponse fallback(Flow flow, String reason) {
        List<WorkSummaryDTO> works = fallbackProvider.byTab(flow.tab, flow.size);
        UUID chainId = UUID.randomUUID();
        OffsetDateTime servedAt = OffsetDateTime.now(ZoneOffset.UTC);

        List<RecommendItem> items = new ArrayList<>(works.size());
        List<RecItemServedLogRecord> itemLogs = new ArrayList<>(works.size() + flow.dropped.size());
        for (int i = 0; i < works.size(); i++) {
            WorkSummaryDTO work = works.get(i);
            UUID impressionId = UUID.randomUUID();
            items.add(new RecommendItem(impressionId.toString(), i, work, null));
            itemLogs.add(new RecItemServedLogRecord(impressionId, servedAt, flow.requestId, work.getId(),
                    FALLBACK_PLATFORM, String.valueOf(work.getId()), i, FALLBACK_SOURCE,
                    null, null, "{}", null, false, 1.0f, null, true, null));
        }
        // 카드가 0장이 된 이유(not_in_db·adult…)를 같은 request_id 밑에 남긴다 — 커버리지 추적의 유일한 단서다.
        for (DroppedCandidate candidate : flow.dropped) itemLogs.add(droppedLog(flow, servedAt, candidate));
        rememberSkipped(flow);

        offer(requestLog(flow, servedAt, chainId, 0, "{}", versionsJson(null), true, reason, List.of()));
        for (RecItemServedLogRecord record : itemLogs) offer(record);

        return new RecommendResponse(flow.requestId.toString(), chainId.toString(), 0, true, reason, items, false);
    }

    /**
     * 이어 보던 체인이 있을 때만, 버린 키만 덧붙인다(대체는 체인을 새로 만들지 않는다).
     * 이 호출도 page_depth 를 1 올린다 — 카드가 0장이어도 사용자가 한 쪽을 더 넘긴 것은 맞다.
     */
    private void rememberSkipped(Flow flow) {
        if (flow.chain == null) return;
        List<String> skipped = skippedFlatKeys(flow.dropped);
        if (skipped.isEmpty()) return;
        try {
            chainService.append(flow.chain, List.of(), skipped);
        } catch (RuntimeException e) {
            log.warn("대체 응답에서 체인 갱신 실패 — 버린 키를 기억하지 못한다", e);
        }
    }

    /** 요청을 보낸 화면. 컨트롤러가 `surface` 파라미터를 맥락의 source 로 싣는다. 없거나 모르는 값이면 추천 탭. */
    static String surfaceOf(RecContext ctx) {
        String source = ctx == null ? null : ctx.source();
        return source != null && SURFACES.contains(source) ? source : SURFACE;
    }

    private RecRequestLogRecord requestLog(Flow flow, OffsetDateTime servedAt, UUID chainId, int pageDepth,
                                           String experimentsJson, String versionsJson,
                                           boolean fallback, String fallbackReason, List<String> partial) {
        return new RecRequestLogRecord(flow.requestId, servedAt, chainId, pageDepth,
                flow.userId, flow.ctx.anonId(), flow.ctx.sessionId(), surfaceOf(flow.ctx), flow.tab,
                flow.sentSeeds.stream().map(Seed::contentId).toList(),
                flow.sentSeeds.stream().map(Seed::source).toList(),
                flow.disliked, flow.notInterested, flow.chainSeen, flow.droppedSeedIds,
                experimentsJson, versionsJson, fallback, fallbackReason, partial,
                elapsedMs(flow.startedAt), null, null);
    }

    private RecItemServedLogRecord droppedLog(Flow flow, OffsetDateTime servedAt, DroppedCandidate candidate) {
        RouterItem item = candidate.item();
        return new RecItemServedLogRecord(UUID.randomUUID(), servedAt, flow.requestId,
                candidate.contentId() == null ? 0L : candidate.contentId(), item.platform(), item.key(),
                item.rank() == null ? -1 : item.rank(), candidateSource(item), null, null,
                toJson(item.score()), RouterText.safeOrNull(item.factorSchema()),
                Boolean.TRUE.equals(item.isExploration()), propensity(item), null, false, candidate.reason());
    }

    // ---------- 보조 ----------

    /** 로그는 응답의 곁가지다 — 큐가 어떤 이유로 터져도 이미 만든 응답을 버리지 않는다. */
    private void offer(LogRecord record) {
        try {
            logQueue.offer(record);
        } catch (RuntimeException e) {
            log.warn("추천 로그 적재 실패 — 응답은 그대로 준다", e);
        }
    }

    private static List<Long> union(List<Long> a, List<Long> b, List<Long> c) {
        Set<Long> out = new LinkedHashSet<>();
        for (List<Long> part : List.of(a, b, c)) out.addAll(part);
        return new ArrayList<>(out);
    }

    /** content_id 목록 → 탭에서 부를 플랫폼의 키들. 입력 순서를 지킨다(잘라야 할 때 기준이 된다). */
    private static Map<String, Set<String>> keysByPlatform(Collection<Long> contentIds,
                                                           Map<Long, CorpusKey> keys, List<String> platforms) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Long contentId : contentIds) {
            CorpusKey key = keys.get(contentId);
            if (key == null || !platforms.contains(key.platform())) continue;
            out.computeIfAbsent(key.platform(), platform -> new LinkedHashSet<>()).add(key.key());
        }
        return out;
    }

    private static void addFlatKeys(Map<String, Set<String>> target, Collection<String> flatKeys,
                                    List<String> platforms) {
        for (String flat : flatKeys) {
            CorpusKey key = CorpusKeyMapper.parseFlat(flat);
            if (key == null || !platforms.contains(key.platform())) continue;
            target.computeIfAbsent(key.platform(), platform -> new LinkedHashSet<>()).add(key.key());
        }
    }

    private static Map<String, Set<String>> copyOf(Map<String, Set<String>> source) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            out.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return out;
    }

    /** 상한이 이미 걸린 목록(disliked·seen). 라우터는 null 맵을 싫어하므로 빈 맵이라도 준다. */
    private static Map<String, List<String>> toLists(Map<String, Set<String>> source) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            out.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return out;
    }

    /** excluded 전용. 넘치면 뒤(오래된 관심 없음)를 버린다 — 앞쪽에 더 급한 키를 넣어 뒀다. */
    private static Map<String, List<String>> toCappedLists(Map<String, Set<String>> source) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            List<String> keys = new ArrayList<>(entry.getValue());
            if (keys.size() > MAX_EXCLUDED_PER_PLATFORM) {
                keys = new ArrayList<>(keys.subList(0, MAX_EXCLUDED_PER_PLATFORM));
            }
            out.put(entry.getKey(), keys);
        }
        return out;
    }

    /** 체인에 기억할 키 — DB 에 없거나 성인이라 버린 것만. 중복·정원 초과는 다음 쪽에서 다시 쓸 수 있다. */
    private static List<String> skippedFlatKeys(List<DroppedCandidate> dropped) {
        List<String> out = new ArrayList<>();
        for (DroppedCandidate candidate : dropped) {
            String reason = candidate.reason();
            if (!DroppedCandidate.NOT_IN_DB.equals(reason) && !DroppedCandidate.ADULT.equals(reason)) continue;
            out.add(CorpusKeyMapper.flat(new CorpusKey(candidate.item().platform(), candidate.item().key())));
        }
        return out;
    }

    /** 엔진이 "모르는 시드"라며 돌려준 키도 content_id 로 되돌려 dropped_seed_ids 에 합친다 (설계 §3). */
    private static List<Long> withRouterDroppedSeeds(List<Long> current, Map<String, List<String>> droppedSeeds,
                                                     Map<String, Long> seedContentIdByFlatKey) {
        if (droppedSeeds == null || droppedSeeds.isEmpty()) return current;
        Set<Long> out = new LinkedHashSet<>(current);
        for (Map.Entry<String, List<String>> entry : droppedSeeds.entrySet()) {
            if (entry.getValue() == null) continue;
            for (String key : entry.getValue()) {
                Long contentId = seedContentIdByFlatKey.get(
                        CorpusKeyMapper.flat(new CorpusKey(entry.getKey(), key)));
                if (contentId != null) out.add(contentId);
            }
        }
        return new ArrayList<>(out);
    }

    /** NOT NULL 칸이라 쓸 수 없는 값이면 기본값으로 바꾼다(라우터 문자열은 외부 입력이다). */
    private static String candidateSource(RouterItem item) {
        String source = RouterText.safeOrNull(item.candidateSource());
        return source == null ? DEFAULT_CANDIDATE_SOURCE : source;
    }

    private static float propensity(RouterItem item) {
        return item.propensity() == null ? 1.0f : item.propensity().floatValue();
    }

    private static Integer elapsedMs(long startedAtNanos) {
        return (int) Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    /** 결과가 바뀐 원인을 나중에 가릴 단서 — 라우터·엔진 버전 + 백엔드 버전 (서빙 README §3). */
    private String versionsJson(RouterVersions versions) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("backend", backendVersion);
        if (versions != null) {
            out.put("router", versions.router());
            out.put("engines", versions.engines() == null ? Map.of() : versions.engines());
        }
        return toJson(out);
    }

    private String toJson(Object value) {
        if (value == null) return "{}";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("추천 로그 직렬화 실패 — 빈 객체로 대체", e);
            return "{}";
        }
    }
}
