package com.example.crawler.admin.controller;

import com.example.crawler.common.queue.CrawlJobProducer;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.Novel.KakaoPageNovel.KakaoPageCrawler;
import com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesCrawler;
import com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesSchedulingService;
import com.example.crawler.contents.TMDB.service.TmdbSchedulingService;
import com.example.crawler.contents.Webtoon.NaverWebtoon.NaverWebtoonSchedulingService;
import com.example.crawler.contents.Webtoon.NaverWebtoon.NaverWebtoonService;
import com.example.crawler.game.steam.service.SteamSchedulingService;
import com.example.crawler.ingest.DraftAssembler;
import com.example.crawler.ingest.IngestPipeline;
import com.example.crawler.ingest.TransformSchedulingService;
import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.entity.RawItem;
import com.example.shared.repository.RawItemRepository;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AdminTestController {

    private final NaverSeriesCrawler naverSeriesCrawler;
    private final KakaoPageCrawler kakaoPageCrawler;
    private final NaverWebtoonService naverWebtoonService;

    // Job Queue Producers
    private final SteamSchedulingService steamSchedulingService;
    private final TmdbSchedulingService tmdbSchedulingService;
    private final NaverWebtoonSchedulingService webtoonSchedulingService;
    private final NaverSeriesSchedulingService naverSeriesSchedulingService;
    private final CrawlJobProducer crawlJobProducer;

    private final IngestPipeline ingestPipeline;
    private final TransformSchedulingService transformSchedulingService;
    private final RawItemRepository rawRepo;
    private final RuleRegistry ingestRuleRegistry;
    private final DraftAssembler draftAssembler;

    public AdminTestController(NaverSeriesCrawler naverSeriesCrawler,
            KakaoPageCrawler kakaoPageCrawler,
            NaverWebtoonService naverWebtoonService,
            SteamSchedulingService steamSchedulingService,
            TmdbSchedulingService tmdbSchedulingService,
            NaverWebtoonSchedulingService webtoonSchedulingService,
            NaverSeriesSchedulingService naverSeriesSchedulingService,
            CrawlJobProducer crawlJobProducer,
            IngestPipeline ingestPipeline,
            TransformSchedulingService transformSchedulingService,
            RawItemRepository rawRepo,
            RuleRegistry ingestRuleRegistry,
            DraftAssembler draftAssembler) {
        this.naverSeriesCrawler = naverSeriesCrawler;
        this.kakaoPageCrawler = kakaoPageCrawler;
        this.naverWebtoonService = naverWebtoonService;
        this.steamSchedulingService = steamSchedulingService;
        this.tmdbSchedulingService = tmdbSchedulingService;
        this.webtoonSchedulingService = webtoonSchedulingService;
        this.naverSeriesSchedulingService = naverSeriesSchedulingService;
        this.crawlJobProducer = crawlJobProducer;
        this.ingestPipeline = ingestPipeline;
        this.transformSchedulingService = transformSchedulingService;
        this.rawRepo = rawRepo;
        this.ingestRuleRegistry = ingestRuleRegistry;
        this.draftAssembler = draftAssembler;
    }

    // 헬스체크
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true);
    }

    /* ===================== STEAM ===================== */

    // Steam 전체 게임 크롤링 (Job Queue 등록)
    @PostMapping("/crawl/steam/all-games")
    public Map<String, Object> crawlSteamAllGames() {
        try {
            steamSchedulingService.collectSteamGamesWeekly();
            return Map.of(
                    "success", true,
                    "message", "Steam 게임 크롤링 작업이 Job Queue에 등록되었습니다. Consumer가 5초마다 처리합니다.");
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    /* ===================== NAVER WEBTOON ===================== */
    // Job Queue 기반 크롤링 (권장)

    // 모든 요일별 웹툰 크롤링 (Job Queue 등록)
    @PostMapping("/crawl/naver-webtoon/all-weekdays")
    public Map<String, Object> crawlNaverWebtoonAllWeekdays() {
        try {
            webtoonSchedulingService.collectAllWeekdaysDaily();
            return Map.of(
                    "success", true,
                    "message", "네이버 웹툰 전체 크롤링 작업이 Job Queue에 등록되었습니다. Consumer가 5초마다 처리합니다.");
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // 특정 요일 웹툰 크롤링 (Job Queue 등록)
    @PostMapping("/crawl/naver-webtoon/weekday")
    public Map<String, Object> crawlNaverWebtoonWeekday(@RequestBody Map<String, Object> request) {
        try {
            String weekday = (String) request.get("weekday");
            if (weekday == null || weekday.isBlank()) {
                return Map.of(
                        "success", false,
                        "error", "weekday 파라미터가 필요합니다. (mon, tue, wed, thu, fri, sat, sun)");
            }

            webtoonSchedulingService.collectAllWeekdaysDaily(); // 전체 수집
            return Map.of(
                    "success", true,
                    "message", weekday + " 요일 포함 전체 웹툰 크롤링 작업이 Job Queue에 등록되었습니다.",
                    "weekday", weekday);
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // 완결 웹툰 크롤링 (Job Queue 등록)
    @PostMapping("/crawl/naver-webtoon/finished")
    public Map<String, Object> crawlNaverWebtoonFinished(@RequestBody Map<String, Object> request) {
        try {
            webtoonSchedulingService.collectFinishedWebtoonsWeekly();
            return Map.of(
                    "success", true,
                    "message", "완결 웹툰 크롤링 작업이 Job Queue에 등록되었습니다.");
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // 동기 버전 - 테스트용 (즉시 실행, Job Queue 우회 - 권장하지 않음)
    @PostMapping("/crawl/naver-webtoon/weekday/sync")
    public Map<String, Object> crawlNaverWebtoonWeekdaySync(@RequestBody Map<String, Object> request) {
        try {
            String weekday = (String) request.get("weekday");
            if (weekday == null || weekday.isBlank()) {
                return Map.of(
                        "success", false,
                        "error", "weekday 파라미터가 필요합니다. (mon, tue, wed, thu, fri, sat, sun)");
            }

            int saved = naverWebtoonService.crawlWeekdaySync(weekday); // 동기 실행
            return Map.of(
                    "success", true,
                    "message", weekday + " 요일 웹툰 크롤링이 완료되었습니다.",
                    "weekday", weekday,
                    "savedCount", saved);
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // 동기 버전 - 전체 요일 테스트용
    @PostMapping("/crawl/naver-webtoon/all-weekdays/sync")
    public Map<String, Object> crawlNaverWebtoonAllWeekdaysSync() {
        try {
            int totalSaved = naverWebtoonService.crawlAllWeekdaysSync(); // 동기 실행
            return Map.of(
                    "success", true,
                    "message", "네이버 웹툰 전체 크롤링이 완료되었습니다.",
                    "totalSavedCount", totalSaved);
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // 동기 버전 - 완결 웹툰 테스트용
    @PostMapping("/crawl/naver-webtoon/finished/sync")
    public Map<String, Object> crawlNaverWebtoonFinishedSync(@RequestBody Map<String, Object> request) {
        try {
            Integer maxPages = request.get("maxPages") != null
                    ? (Integer) request.get("maxPages")
                    : 10; // 기본값 10페이지

            int saved = naverWebtoonService.crawlFinishedWebtoonsSync(maxPages); // 동기 실행
            return Map.of(
                    "success", true,
                    "message", "완결 웹툰 크롤링이 완료되었습니다.",
                    "maxPages", maxPages,
                    "savedCount", saved);
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    /**
     * 네이버 시리즈 소설 ID 목록 수집 후 Job Queue에 등록 (Producer 패턴)
     * 즉시 리턴, 실제 크롤링은 Consumer가 5초마다 처리
     */
    @PostMapping("/crawl/naver-series/popular")
    public Map<String, Object> queueNaverSeriesPopular(@RequestParam(defaultValue = "5") int maxPages) {
        try {
            String baseUrl = "https://series.naver.com/novel/categoryProductList.series?categoryTypeCode=all&page=";
            java.util.List<String> novelIds = naverSeriesSchedulingService.fetchNovelIdsByUrl(baseUrl, maxPages);

            int created = 0;
            if (!novelIds.isEmpty()) {
                created = crawlJobProducer.createJobs(JobType.NAVER_SERIES_NOVEL, novelIds, 3);
            }

            Map<String, Object> res = new HashMap<>();
            res.put("foundNovelIds", novelIds.size());
            res.put("jobsCreated", created);
            res.put("maxPages", maxPages);
            res.put("message", "작업이 큐에 등록되었습니다. Consumer가 5초마다 2개씩 처리합니다.");
            return res;

        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 네이버 시리즈 신작 수집 후 Job Queue에 등록
     */
    @PostMapping("/crawl/naver-series/recent")
    public Map<String, Object> queueNaverSeriesRecent(@RequestParam(defaultValue = "3") int maxPages) {
        try {
            String baseUrl = "https://series.naver.com/novel/recentList.series?page=";
            java.util.List<String> novelIds = naverSeriesSchedulingService.fetchNovelIdsByUrl(baseUrl, maxPages);

            int created = 0;
            if (!novelIds.isEmpty()) {
                created = crawlJobProducer.createJobs(JobType.NAVER_SERIES_NOVEL, novelIds, 3);
            }

            Map<String, Object> res = new HashMap<>();
            res.put("foundNovelIds", novelIds.size());
            res.put("jobsCreated", created);
            res.put("maxPages", maxPages);
            res.put("message", "신작 작업이 큐에 등록되었습니다. Consumer가 5초마다 2개씩 처리합니다.");
            return res;

        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 레거시: 네이버 시리즈 직접 크롤링 (즉시 실행, 권장하지 않음)
     * 
     * @deprecated Job Queue 패턴 사용 권장 (/api/crawl/naver-series/popular 또는 /recent)
     */
    @Deprecated
    @PostMapping(path = "/crawl/naver-series", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> crawlNaverSeries(@RequestBody CrawlRequest req) throws Exception {
        String base = (req.baseListUrl() == null || req.baseListUrl().isBlank())
                ? "https://series.naver.com/novel/categoryProductList.series?categoryTypeCode=all&page="
                : req.baseListUrl();
        int pages = req.pages() != null ? req.pages() : 1;

        int saved = naverSeriesCrawler.crawlToRaw(base, req.cookie(), pages);
        long pending = rawRepo.countByProcessedFalse();

        Map<String, Object> res = new HashMap<>();
        res.put("saved", saved);
        res.put("pendingRaw", pending);
        res.put("baseListUrl", base);
        res.put("pages", pages);
        return res;
    }

    /* ===================== TMDB (The Movie Database) ===================== */

    // TMDB 신규 콘텐츠 크롤링 (Job Queue 등록)
    @PostMapping("/crawl/tmdb/new-content")
    public Map<String, Object> crawlTmdbNewContent() {
        try {
            // TmdbSchedulingService를 통해 최근 7일간 영화/TV 쇼 ID를 Job Queue에 등록
            tmdbSchedulingService.collectNewContentDaily();

            return Map.of(
                    "success", true,
                    "message", "TMDB 신규 콘텐츠 크롤링 작업이 Job Queue에 등록되었습니다. Consumer가 5초마다 처리합니다.",
                    "note", "최근 7일간의 영화 및 TV 쇼가 크롤링 대상입니다.");

        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // TMDB 전체 영화 크롤링 (Job Queue 등록)
    @PostMapping("/crawl/tmdb/all-movies")
    public Map<String, Object> crawlTmdbAllMovies() {
        try {
            tmdbSchedulingService.collectAllMovies();
            return Map.of(
                    "success", true,
                    "message", "TMDB 전체 영화 크롤링 작업이 Job Queue에 등록되었습니다. Consumer가 5초마다 처리합니다.");
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // TMDB 전체 TV 쇼 크롤링 (Job Queue 등록)
    @PostMapping("/crawl/tmdb/all-tv")
    public Map<String, Object> crawlTmdbAllTvShows() {
        try {
            tmdbSchedulingService.collectAllTvShows();
            return Map.of(
                    "success", true,
                    "message", "TMDB 전체 TV 쇼 크롤링 작업이 Job Queue에 등록되었습니다. Consumer가 5초마다 처리합니다.");
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // TMDB 전체 콘텐츠 크롤링 (영화 + TV 쇼, Job Queue 등록)
    @PostMapping("/crawl/tmdb/all-content")
    public Map<String, Object> crawlTmdbAllContent() {
        try {
            tmdbSchedulingService.collectAllContent();
            return Map.of(
                    "success", true,
                    "message", "TMDB 전체 콘텐츠(영화+TV) 크롤링 작업이 Job Queue에 등록되었습니다. Consumer가 5초마다 처리합니다.");
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // TMDB 연도 범위 지정 영화 크롤링 (Job Queue 등록)
    @PostMapping("/crawl/tmdb/movies-by-year")
    public Map<String, Object> crawlTmdbMoviesByYear(@RequestParam int startYear, @RequestParam int endYear) {
        try {
            int currentYear = Year.now().getValue();
            
            // 유효성 검사
            if (startYear < 1900 || startYear > currentYear) {
                return Map.of(
                        "success", false,
                        "error", "시작 연도는 1900년부터 " + currentYear + "년 사이여야 합니다.");
            }
            if (endYear < 1900 || endYear > currentYear) {
                return Map.of(
                        "success", false,
                        "error", "종료 연도는 1900년부터 " + currentYear + "년 사이여야 합니다.");
            }
            if (startYear > endYear) {
                return Map.of(
                        "success", false,
                        "error", "시작 연도가 종료 연도보다 클 수 없습니다.");
            }
            
            tmdbSchedulingService.collectMoviesByYearRange(startYear, endYear);
            return Map.of(
                    "success", true,
                    "message", String.format("TMDB %d년~%d년 영화 크롤링 작업이 Job Queue에 등록되었습니다.", startYear, endYear));
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // TMDB 연도 범위 지정 TV 쇼 크롤링 (Job Queue 등록)
    @PostMapping("/crawl/tmdb/tv-by-year")
    public Map<String, Object> crawlTmdbTvShowsByYear(@RequestParam int startYear, @RequestParam int endYear) {
        try {
            int currentYear = Year.now().getValue();
            
            // 유효성 검사
            if (startYear < 1900 || startYear > currentYear) {
                return Map.of(
                        "success", false,
                        "error", "시작 연도는 1900년부터 " + currentYear + "년 사이여야 합니다.");
            }
            if (endYear < 1900 || endYear > currentYear) {
                return Map.of(
                        "success", false,
                        "error", "종료 연도는 1900년부터 " + currentYear + "년 사이여야 합니다.");
            }
            if (startYear > endYear) {
                return Map.of(
                        "success", false,
                        "error", "시작 연도가 종료 연도보다 클 수 없습니다.");
            }
            
            tmdbSchedulingService.collectTvShowsByYearRange(startYear, endYear);
            return Map.of(
                    "success", true,
                    "message", String.format("TMDB %d년~%d년 TV 쇼 크롤링 작업이 Job Queue에 등록되었습니다.", startYear, endYear));
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    /* ===================== KAKAO PAGE ===================== */

    /**
     * 카카오페이지 직접 크롤링 (즉시 실행, 권장하지 않음)
     * 
     * @deprecated Job Queue 패턴으로 변경 예정 - 현재는 Producer가 구현되지 않음
     */
    @Deprecated
    @PostMapping(path = "/crawl/kakaopage/api", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> crawlKakaoPageByApi(@RequestBody KpApiRequest req) {
        try {
            // 요청 파라미터가 null일 경우 기본값 설정
            String sectionId = (req.sectionId() == null || req.sectionId().isBlank())
                    ? "static-landing-Genre-section-Landing-11-0-UPDATE-false"
                    : req.sectionId();
            int categoryUid = (req.categoryUid() == null) ? 11 : req.categoryUid(); // 11: 웹소설
            String subcategoryUid = (req.subcategoryUid() == null) ? "0" : req.subcategoryUid(); // 0: 전체
            String sortType = (req.sortType() == null || req.sortType().isBlank()) ? "UPDATE" : req.sortType(); // UPDATE:
                                                                                                                // 업데이트순
            boolean isComplete = (req.isComplete() == null) ? false : req.isComplete(); // false: 연재중
            int pages = (req.pages() == null || req.pages() <= 0) ? 10 : req.pages(); // 기본 10페이지

            int saved = kakaoPageCrawler.crawlToRaw(
                    sectionId, categoryUid, subcategoryUid, sortType, isComplete, req.cookie(), pages);
            long pending = rawRepo.countByProcessedFalse();

            Map<String, Object> usedParams = Map.of(
                    "sectionId", sectionId, "categoryUid", categoryUid, "subcategoryUid", subcategoryUid,
                    "sortType", sortType, "isComplete", isComplete, "pages", pages);

            return Map.of(
                    "success", true,
                    "message", "KakaoPage API crawling completed. (직접 실행 - 권장하지 않음)",
                    "warning", "이 API는 deprecated입니다. Job Queue 패턴 사용을 권장합니다.",
                    "saved", saved,
                    "pendingRaw", pending,
                    "parameters", usedParams);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /* ===================== BATCH / TRANSFORM / UPSERT ===================== */

    // 🚀 수동으로 일일 배치 변환 트리거 (스케줄러와 동일한 로직)
    @PostMapping("/batch/transform-daily")
    public Map<String, Object> triggerDailyTransform() {
        try {
            transformSchedulingService.transformRawItemsDaily();
            return Map.of(
                    "success", true,
                    "message", "일일 배치 변환이 시작되었습니다. 로그를 확인하세요.");
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }

    // 배치 변환/업서트 실행 (raw_items → contents/platform_data)
    @PostMapping(path = "/batch/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runBatch(@RequestBody BatchRequest req) {
        int size = (req.batchSize() == null || req.batchSize() <= 0) ? 100 : req.batchSize();
        int processed = ingestPipeline.processBatch(size);
        long stillPending = rawRepo.countByProcessedFalse();

        return Map.of(
                "batchSize", size,
                "processed", processed,
                "pendingRaw", stillPending);
    }

    // 🚀 최적화된 배치 처리 (대용량 처리용)
    @PostMapping(path = "/batch/process-optimized", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runBatchOptimized(@RequestBody BatchRequestOptimized req) {
        long startTime = System.currentTimeMillis();
        int batchSize = req.batchSize() != null && req.batchSize() > 0 ? req.batchSize() : 500;

        int processed = ingestPipeline.processBatch(batchSize);
        long stillPending = rawRepo.countByProcessedFalse();
        long elapsed = System.currentTimeMillis() - startTime;

        return Map.of(
                "batchSize", batchSize,
                "processed", processed,
                "pendingRaw", stillPending,
                "elapsedMs", elapsed,
                "itemsPerSecond", processed * 1000L / Math.max(elapsed, 1));
    }

    // 대량 배치 처리 (구 병렬 엔드포인트 — 순차 루프로 대체, 경로는 호환용 유지)
    @PostMapping(path = "/batch/process-parallel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runBatchParallel(@RequestBody BatchRequestParallel req) {
        long startTime = System.currentTimeMillis();

        int totalItems = req.totalItems() != null && req.totalItems() > 0 ? req.totalItems() : 10000;
        int batchSize = req.batchSize() != null && req.batchSize() > 0 ? req.batchSize() : 500;
        int numWorkers = req.numWorkers() != null && req.numWorkers() > 0 ? req.numWorkers() : 4;

        // 구 processInParallel(스레드풀) 대체: IngestPipeline은 item별 트랜잭션 격리라 순차 루프로 동일 계약 유지
        // (numWorkers는 하위호환용으로 받되 무시 — 병렬화는 컷오버로 제거됨)
        int processed = 0;
        int iterations = (int) Math.ceil((double) totalItems / batchSize);
        for (int i = 0; i < iterations; i++) {
            int n = ingestPipeline.processBatch(batchSize);
            processed += n;
            if (n == 0) break;
        }
        long stillPending = rawRepo.countByProcessedFalse();
        long elapsed = System.currentTimeMillis() - startTime;

        return Map.of(
                "totalItems", totalItems,
                "batchSize", batchSize,
                "numWorkers", numWorkers,
                "processed", processed,
                "pendingRaw", stillPending,
                "elapsedMs", elapsed,
                "itemsPerSecond", processed * 1000L / Math.max(elapsed, 1));
    }

    // 규칙 프리뷰: payload로 assemble만 수행해 확인 (DB 반영 X)
    // 컷오버 노트: 룰은 (domain, platformName)으로 레지스트리에서 해석 — 임의 rulePath 로드는 폐지(req.rulePath 무시).
    @PostMapping(path = "/transform/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> previewTransform(@RequestBody PreviewRequest req) {
        var rule = ingestRuleRegistry.resolve(req.domain(), req.platformName());
        var draft = draftAssembler.assemble(req.payload(), rule);
        return Map.of(
                "rulePath", ingestRuleRegistry.pathOf(rule.platformName()),
                "master", draft.content(),
                "platform", draft.platformData(),
                "domain", draft.domainEntity());
    }

    /* ===================== 요청 DTO ===================== */

    public record CrawlRequest(String baseListUrl, String cookie, Integer pages) {
    }

    // 카카오페이지 API 요청을 위한 새로운 DTO
    public record KpApiRequest(
            String sectionId,
            Integer categoryUid,
            String subcategoryUid,
            String sortType,
            Boolean isComplete,
            String cookie,
            Integer pages) {
    }

    // public record CrawlRequest(String baseListUrl, String cookie, Integer pages)
    // {}
    public record KpListRequest(String listUrl, String cookie, Integer pages) {
    }

    public record KpCollectRequest(List<String> urls, String cookie) {
    }

    public record BatchRequest(Integer batchSize) {
    }

    public record BatchRequestOptimized(Integer batchSize) {
    }

    public record BatchRequestParallel(Integer totalItems, Integer batchSize, Integer numWorkers) {
    }

    public record PreviewRequest(String platformName, String domain, String rulePath, Map<String, Object> payload) {
    }

    /**
     * 중복 검사 테스트용: 특정 RawItem을 다시 처리하도록 강제
     */
    @PostMapping("/test/reprocess-raw/{rawId}")
    public Map<String, Object> reprocessRawItem(@PathVariable Long rawId) {
        var raw = rawRepo.findById(rawId)
                .orElseThrow(() -> new IllegalArgumentException("RawItem not found: " + rawId));

        // processed를 false로 변경
        raw.setProcessed(false);
        raw.setProcessedAt(null);
        rawRepo.save(raw);

        // 다시 처리
        int processed = ingestPipeline.processBatch(1);

        return Map.of(
                "message", "RawItem 재처리 완료",
                "rawId", rawId,
                "processed", processed > 0);
    }

    /**
     * 중복 검사 테스트용: 최근 처리된 N개를 다시 처리
     */
    @PostMapping("/test/reprocess-recent")
    public Map<String, Object> reprocessRecent(@RequestParam(defaultValue = "5") int count) {
        var recentRaws = rawRepo.findAll().stream()
                .filter(RawItem::isProcessed)
                .sorted((a, b) -> b.getProcessedAt().compareTo(a.getProcessedAt()))
                .limit(count)
                .toList();

        // processed를 false로 변경
        recentRaws.forEach(raw -> {
            raw.setProcessed(false);
            raw.setProcessedAt(null);
        });
        rawRepo.saveAll(recentRaws);

        // 다시 처리
        int processed = ingestPipeline.processBatch(count);

        return Map.of(
                "message", "최근 " + count + "개 RawItem 재처리 완료",
                "reprocessedIds", recentRaws.stream().map(RawItem::getRawId).toList(),
                "successCount", processed);
    }

    /**
     * 배치 처리 API - Admin UI에서 호출
     */
    @PostMapping("/batch/process")
    public Map<String, Object> processBatch(@RequestParam(defaultValue = "100") int batchSize) {
        try {
            long pendingCount = rawRepo.countByProcessedFalse();
            int processed = ingestPipeline.processBatch(batchSize);

            return Map.of(
                    "success", true,
                    "message", "배치 처리 완료",
                    "pendingBefore", pendingCount,
                    "processedCount", processed,
                    "pendingAfter", rawRepo.countByProcessedFalse());
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }
}
