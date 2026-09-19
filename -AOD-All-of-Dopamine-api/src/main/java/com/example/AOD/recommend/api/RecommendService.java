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
 * 추천을 줄 수 없는 모든 경우(기능 꺼짐·시드 없음·라우터 실패·카드 0장)는 같은 모양의 대체 응답이 된다.
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
    /** 라우터에 요청할 총 후보 수 = k + buffer (설계 §4 — 커버리지가 낮아 버퍼를 크게 준다). */
    public static final int ROUTER_BUDGET = 50;
    /** 첫 호출이 이보다 오래 걸렸으면 재호출하지 않는다 (전체 예산 2.5초). */
    public static final long REFILL_DEADLINE_MS = 1_200L;
    /** 플랫폼당 시드 상한 (라우터 한도). */
    public static final int MAX_SEEDS_PER_PLATFORM = 50;
    /**
     * 플랫폼당 excluded 상한. 라우터는 플랫폼별 disliked+excluded+seen ≤ 5,000 을 강제한다(422).
     * disliked 는 {@link SeedResolver#MAX_DISLIKED}(1,000), seen 은 {@link ChainService#SEEN_MAX}(500)
     * 로 이미 묶여 있고, 관심 없음은 상한이 없다 → 여기서만 자른다. 1,000 + 500 + 3,000 = 4,500.
     */
    public static final int MAX_EXCLUDED_PER_PLATFORM = 3_000;

    static final String FALLBACK_PLATFORM = "fallback";
    static final String FALLBACK_SOURCE = "ranking_fallback";
    static final String DEFAULT_CANDIDATE_SOURCE = "content_sim";

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
        return fallback(tab, size, "anonymous", null, ctx, System.nanoTime(), List.of());
    }

    /**
     * 로그인 사용자 추천.
     * @throws ChainNotFoundException chainId 가 없거나 만료·남의 것·다른 탭이다 (404)
     */
    public RecommendResponse recommend(String tab, UUID chainId, int size,
                                       Long userId, String username, RecContext ctx) {
        long startedAt = System.nanoTime();

        if (!featureFlag.allows(username)) return fallback(tab, size, "disabled", userId, ctx, startedAt, List.of());

        Chain chain = null;
        if (chainId != null) {
            chain = chainService.find(chainId, userId, tab).orElseThrow(() -> new ChainNotFoundException(chainId));
        }

        List<Long> disliked = seedResolver.disliked(userId);
        List<Seed> seeds = seedResolver.resolve(userId, disliked);
        if (seeds.isEmpty()) return fallback(tab, size, "no_seed", userId, ctx, startedAt, List.of());

        Map<Long, CorpusKey> seedKeys = corpusKeyService.keysByContentId(seeds.stream().map(Seed::contentId).toList());
        // 키가 없는 시드(카카오페이지 전용 웹소설 등)는 라우터에 못 보낸다 — 로그에 남겨 커버리지를 쫓는다(설계 §3).
        List<Long> unmappedSeedIds = seeds.stream()
                .map(Seed::contentId)
                .filter(id -> !seedKeys.containsKey(id))
                .toList();

        List<String> platforms = PLATFORMS_BY_TAB.getOrDefault(tab, List.of());
        Map<String, List<String>> seedsByPlatform = new LinkedHashMap<>();
        Map<String, Long> seedContentIdByFlatKey = new HashMap<>();
        for (Seed seed : seeds) {
            CorpusKey key = seedKeys.get(seed.contentId());
            if (key == null || !platforms.contains(key.platform())) continue;
            seedContentIdByFlatKey.put(CorpusKeyMapper.flat(key), seed.contentId());
            List<String> lane = seedsByPlatform.computeIfAbsent(key.platform(), platform -> new ArrayList<>());
            if (lane.size() < MAX_SEEDS_PER_PLATFORM) lane.add(key.key());
        }
        if (seedsByPlatform.isEmpty()) {
            return fallback(tab, size, "no_seed_platform", userId, ctx, startedAt, unmappedSeedIds);
        }

        List<Long> notInterested = notInterestedService.activeContentIds(userId);
        List<Long> chainSeen = chain == null ? List.of() : chain.seenIds();
        List<String> chainSkipped = chain == null ? List.of() : chain.skippedKeys();

        // 싫어요·관심 없음·체인 seen 의 키를 한 번의 조회로 가져온다(각각 부르면 platform_data 를 3번 읽는다).
        Map<Long, CorpusKey> keys = corpusKeyService.keysByContentId(union(disliked, notInterested, chainSeen));
        Map<String, Set<String>> dislikedKeys = keysByPlatform(disliked, keys, platforms);
        Map<String, Set<String>> seenKeys = keysByPlatform(chainSeen, keys, platforms);
        Map<String, Set<String>> excludedKeys = keysByPlatform(notInterested, keys, platforms);
        addFlatKeys(excludedKeys, chainSkipped, platforms);

        int buffer = Math.max(0, ROUTER_BUDGET - size);
        RouterResult first = routerClient.recommend(new RouterRequest(tab, size, buffer,
                seedsByPlatform, toLists(dislikedKeys), toCappedLists(excludedKeys), toLists(seenKeys)));
        if (!first.ok()) return fallback(tab, size, first.failure(), userId, ctx, startedAt, unmappedSeedIds);

        Assembly assembly = cardAssembler.assemble(first.response().items(), size, new HashSet<>(chainSeen));
        List<AssembledCard> cards = new ArrayList<>(assembly.cards());
        List<DroppedCandidate> dropped = new ArrayList<>(assembly.dropped());
        Map<String, Boolean> exhausted = new HashMap<>(
                first.response().exhausted() == null ? Map.of() : first.response().exhausted());
        List<String> partial = first.response().partial() == null ? List.of() : first.response().partial();
        RouterVersions versions = first.response().versions();
        List<Long> droppedSeedIds = withRouterDroppedSeeds(unmappedSeedIds,
                first.response().droppedSeeds(), seedContentIdByFlatKey);

        // 커버리지 보정 (설계 §4): 카드가 모자라고 첫 호출이 예산 안이면 한 번만 더 부른다.
        // 방금 버린 키는 excluded 로, 방금 담은 카드는 seen 으로 알린다 — 아니면 같은 후보가 또 와서 버퍼만 먹는다.
        if (cards.size() < size && first.latencyMs() < REFILL_DEADLINE_MS) {
            List<String> newlySkipped = skippedFlatKeys(dropped);
            if (!newlySkipped.isEmpty()) {
                Map<String, Set<String>> excludedAgain = copyOf(excludedKeys);
                addFlatKeys(excludedAgain, newlySkipped, platforms);
                Map<String, Set<String>> seenAgain = copyOf(seenKeys);
                for (AssembledCard card : cards) {
                    seenAgain.computeIfAbsent(card.item().platform(), platform -> new LinkedHashSet<>())
                            .add(card.item().key());
                }
                RouterResult second = routerClient.recommend(new RouterRequest(tab, size, buffer,
                        seedsByPlatform, toLists(dislikedKeys), toCappedLists(excludedAgain), toLists(seenAgain)));
                if (second.ok()) {
                    Set<Long> used = new HashSet<>(chainSeen);
                    for (AssembledCard card : cards) used.add(card.content().getContentId());
                    Assembly more = cardAssembler.assemble(second.response().items(), size - cards.size(), used);
                    cards.addAll(more.cards());
                    dropped.addAll(more.dropped());
                    if (second.response().exhausted() != null) exhausted.putAll(second.response().exhausted());
                    if (second.response().partial() != null) partial = second.response().partial();
                    if (second.response().versions() != null) versions = second.response().versions();
                    droppedSeedIds = withRouterDroppedSeeds(droppedSeedIds,
                            second.response().droppedSeeds(), seedContentIdByFlatKey);
                }
            }
        }

        if (cards.isEmpty()) return fallback(tab, size, "empty", userId, ctx, startedAt, droppedSeedIds);

        Materials materials = new Materials(tab, userId, ctx, startedAt, chain, seeds, disliked, notInterested,
                chainSeen, seedContentIdByFlatKey, seedsByPlatform.keySet());
        return respond(materials, cards, dropped, droppedSeedIds, exhausted, partial, versions);
    }

    /** 라우터를 부르기까지 모은 재료. respond 의 파라미터가 20개가 되는 것을 막는다. */
    private record Materials(String tab, Long userId, RecContext ctx, long startedAt, Chain chain,
                             List<Seed> seeds, List<Long> disliked, List<Long> notInterested, List<Long> chainSeen,
                             Map<String, Long> seedContentIdByFlatKey, Set<String> calledPlatforms) { }

    // ---------- 응답 조립 ----------

    private RecommendResponse respond(Materials m, List<AssembledCard> cards, List<DroppedCandidate> dropped,
                                      List<Long> droppedSeedIds, Map<String, Boolean> exhausted,
                                      List<String> partial, RouterVersions versions) {
        UUID requestId = UUID.randomUUID();
        OffsetDateTime servedAt = OffsetDateTime.now(ZoneOffset.UTC);

        List<WorkSummaryDTO> works =
                workApiService.toEnrichedSummaries(cards.stream().map(AssembledCard::content).toList());

        // 이유 문구에 필요한 시드 제목을 한 번에 읽는다 (N+1 방지).
        Map<Integer, Long> seedIdByCardIndex = new LinkedHashMap<>();
        for (int i = 0; i < cards.size(); i++) {
            RouterItem item = cards.get(i).item();
            if (item.dominantSeed() == null) continue;
            Long seedContentId = m.seedContentIdByFlatKey().get(
                    CorpusKeyMapper.flat(new CorpusKey(item.platform(), item.dominantSeed())));
            if (seedContentId != null) seedIdByCardIndex.put(i, seedContentId);
        }
        Map<Long, String> seedTitles = new HashMap<>();
        if (!seedIdByCardIndex.isEmpty()) {
            List<Long> ids = new ArrayList<>(new LinkedHashSet<>(seedIdByCardIndex.values()));
            for (Content content : contentRepository.findByContentIdIn(ids)) {
                seedTitles.put(content.getContentId(), content.getMasterTitle());
            }
        }
        Map<Long, String> sourceBySeedId = new HashMap<>();
        for (Seed seed : m.seeds()) sourceBySeedId.putIfAbsent(seed.contentId(), seed.source());

        List<RecommendItem> items = new ArrayList<>(cards.size());
        List<RecItemServedLogRecord> itemLogs = new ArrayList<>(cards.size() + dropped.size());
        List<Long> servedContentIds = new ArrayList<>(cards.size());
        for (int i = 0; i < cards.size(); i++) {
            AssembledCard card = cards.get(i);
            RouterItem item = card.item();
            Long seedContentId = seedIdByCardIndex.get(i);
            RecReason reason = seedContentId == null ? null
                    : reasonBuilder.build(sourceBySeedId.get(seedContentId), seedContentId, seedTitles.get(seedContentId));
            UUID impressionId = UUID.randomUUID();
            items.add(new RecommendItem(impressionId.toString(), i, works.get(i), reason));
            servedContentIds.add(card.content().getContentId());
            itemLogs.add(new RecItemServedLogRecord(impressionId, servedAt, requestId,
                    card.content().getContentId(), item.platform(), item.key(), i,
                    candidateSource(item), reason == null ? null : reason.type(),
                    reason == null ? null : reason.seedContentId(), toJson(item.score()), item.factorSchema(),
                    Boolean.TRUE.equals(item.isExploration()), propensity(item), null, true, null));
        }
        for (DroppedCandidate candidate : dropped) {
            RouterItem item = candidate.item();
            itemLogs.add(new RecItemServedLogRecord(UUID.randomUUID(), servedAt, requestId,
                    candidate.contentId() == null ? 0L : candidate.contentId(), item.platform(), item.key(),
                    item.rank() == null ? -1 : item.rank(), candidateSource(item), null, null,
                    toJson(item.score()), item.factorSchema(), Boolean.TRUE.equals(item.isExploration()),
                    propensity(item), null, false, candidate.reason()));
        }

        Chain saved = saveChain(m.chain(), m.userId(), m.tab(), servedContentIds, skippedFlatKeys(dropped));
        UUID chainId = saved != null ? saved.chainId() : UUID.randomUUID();
        int pageDepth = saved != null ? saved.pageDepth() : (m.chain() == null ? 0 : m.chain().pageDepth() + 1);

        // 부른 플랫폼 중 하나라도 안 끝났고(exhausted 에 없으면 partial = 안 끝남), seen 이 상한 밑이어야 더 준다.
        // 체인을 못 남겼으면 다음 쪽이 같은 것을 또 주게 되므로 여기서 멈춘다.
        boolean anyPlatformLeft = m.calledPlatforms().stream().anyMatch(p -> !Boolean.TRUE.equals(exhausted.get(p)));
        boolean hasMore = saved != null && anyPlatformLeft
                && saved.seenIds().size() < ChainService.SEEN_MAX && !items.isEmpty();

        offer(new RecRequestLogRecord(requestId, servedAt, chainId, pageDepth,
                m.userId(), m.ctx().anonId(), m.ctx().sessionId(), SURFACE, m.tab(),
                m.seeds().stream().map(Seed::contentId).toList(),
                m.seeds().stream().map(Seed::source).toList(),
                m.disliked(), m.notInterested(), m.chainSeen(), droppedSeedIds,
                toJson(experimentAssigner.assign(m.userId())), versionsJson(versions),
                false, null, partial, elapsedMs(m.startedAt()), null, null));
        for (RecItemServedLogRecord record : itemLogs) offer(record);

        return new RecommendResponse(requestId.toString(), chainId.toString(), pageDepth,
                false, null, items, hasMore);
    }

    /**
     * 체인 저장. 실패해도 카드는 이미 만들어 놨으므로 응답을 버리지 않는다 — 대신 이어 보기를 끈다
     * (seen 을 못 남겼으니 다음 쪽은 같은 작품을 다시 준다). DB 가 상한 상황이라 error 로 남긴다.
     */
    private Chain saveChain(Chain chain, Long userId, String tab, List<Long> servedIds, List<String> skipped) {
        try {
            Chain saved = (chain == null)
                    ? chainService.create(UUID.randomUUID(), userId, tab, servedIds, skipped)
                    : chainService.append(chain, servedIds, skipped);
            if (saved == null) log.error("추천 체인 저장 결과가 비었다 — 이어 보기를 끈다 (userId={}, tab={})", userId, tab);
            return saved;
        } catch (RuntimeException e) {
            log.error("추천 체인 저장 실패 — 카드는 그대로 주고 이어 보기만 끈다 (userId={}, tab={})", userId, tab, e);
            return null;
        }
    }

    /** 대체 응답. 체인을 만들지 않는다 — chainId 는 로그 상관용 새 UUID 다 (설계 §2). */
    private RecommendResponse fallback(String tab, int size, String reason, Long userId, RecContext ctx,
                                       long startedAt, List<Long> droppedSeedIds) {
        List<WorkSummaryDTO> works = fallbackProvider.byTab(tab, size);
        UUID requestId = UUID.randomUUID();
        UUID chainId = UUID.randomUUID();
        OffsetDateTime servedAt = OffsetDateTime.now(ZoneOffset.UTC);

        List<RecommendItem> items = new ArrayList<>(works.size());
        List<RecItemServedLogRecord> itemLogs = new ArrayList<>(works.size());
        for (int i = 0; i < works.size(); i++) {
            WorkSummaryDTO work = works.get(i);
            UUID impressionId = UUID.randomUUID();
            items.add(new RecommendItem(impressionId.toString(), i, work, null));
            itemLogs.add(new RecItemServedLogRecord(impressionId, servedAt, requestId, work.getId(),
                    FALLBACK_PLATFORM, String.valueOf(work.getId()), i, FALLBACK_SOURCE,
                    null, null, "{}", null, false, 1.0f, null, true, null));
        }

        offer(new RecRequestLogRecord(requestId, servedAt, chainId, 0, userId,
                ctx.anonId(), ctx.sessionId(), SURFACE, tab,
                List.of(), List.of(), List.of(), List.of(), List.of(), droppedSeedIds,
                "{}", versionsJson(null), true, reason, List.of(), elapsedMs(startedAt), null, null));
        for (RecItemServedLogRecord record : itemLogs) offer(record);

        return new RecommendResponse(requestId.toString(), chainId.toString(), 0, true, reason, items, false);
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

    /** 상한이 이미 걸린 목록(disliked·seen)은 그대로 넘긴다. 라우터는 null 맵을 싫어하므로 빈 맵이라도 준다. */
    private static Map<String, List<String>> toLists(Map<String, Set<String>> source) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            out.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return out;
    }

    /**
     * excluded 전용. 관심 없음은 상한이 없어서 라우터 한도(플랫폼당 5,000)를 넘길 수 있다.
     * 넘치면 앞(오래된 관심 없음)을 버린다 — 뒤쪽에 이번 체인에서 방금 버린 키가 있고 그게 더 급하다.
     */
    private static Map<String, List<String>> toCappedLists(Map<String, Set<String>> source) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            List<String> keys = new ArrayList<>(entry.getValue());
            if (keys.size() > MAX_EXCLUDED_PER_PLATFORM) {
                keys = new ArrayList<>(keys.subList(keys.size() - MAX_EXCLUDED_PER_PLATFORM, keys.size()));
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

    private static String candidateSource(RouterItem item) {
        return item.candidateSource() == null ? DEFAULT_CANDIDATE_SOURCE : item.candidateSource();
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
