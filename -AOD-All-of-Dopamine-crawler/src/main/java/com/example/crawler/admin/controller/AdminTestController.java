package com.example.crawler.admin.controller;

import com.example.crawler.contents.Novel.KakaoPageNovel.KakaoPageCrawler;
import com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesCrawler;

import com.example.crawler.contents.Webtoon.NaverWebtoon.NaverWebtoonService;
import com.example.crawler.ingest.BatchTransformService;
import com.example.crawler.ingest.BatchTransformServiceOptimized;
import com.example.shared.entity.RawItem;
import com.example.shared.repository.RawItemRepository;
import com.example.crawler.rules.MappingRule;
import com.example.crawler.service.RuleLoader;
import com.example.crawler.service.TransformEngine;
import com.example.crawler.service.UpsertService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AdminTestController {

    private final NaverSeriesCrawler naverSeriesCrawler;
    private final KakaoPageCrawler kakaoPageCrawler;
    private final NaverWebtoonService naverWebtoonService;

    private final BatchTransformService batchService;
    private final BatchTransformServiceOptimized batchServiceOptimized;
    private final RawItemRepository rawRepo;
    private final RuleLoader ruleLoader;
    private final TransformEngine transformEngine;
    private final UpsertService upsertService;

    public AdminTestController(NaverSeriesCrawler naverSeriesCrawler,
                               KakaoPageCrawler kakaoPageCrawler,
                               NaverWebtoonService naverWebtoonService,  // 추가
                               BatchTransformService batchService,
                               BatchTransformServiceOptimized batchServiceOptimized,
                               RawItemRepository rawRepo,
                               RuleLoader ruleLoader,
                               TransformEngine transformEngine,
                               UpsertService upsertService) {
        this.naverSeriesCrawler = naverSeriesCrawler;
        this.kakaoPageCrawler = kakaoPageCrawler;
        this.naverWebtoonService = naverWebtoonService;  // 추가
        this.batchService = batchService;
        this.batchServiceOptimized = batchServiceOptimized;
        this.rawRepo = rawRepo;
        this.ruleLoader = ruleLoader;
        this.transformEngine = transformEngine;
        this.upsertService = upsertService;
    }

    // 헬스체크
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true);
    }

    /* ===================== NAVER WEBTOON ===================== */
// 하이브리드 크롤링: 목록(모바일) + 상세(PC)

    // 모든 요일별 웹툰 크롤링
    @PostMapping("/crawl/naver-webtoon/all-weekdays")
    public Map<String, Object> crawlNaverWebtoonAllWeekdays() {
        try {
            naverWebtoonService.crawlAllWeekdays(); // 비동기 실행
            return Map.of(
                    "success", true,
                    "message", "네이버 웹툰 전체 크롤링 작업이 비동기로 시작되었습니다."
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    // 특정 요일 웹툰 크롤링
    @PostMapping("/crawl/naver-webtoon/weekday")
    public Map<String, Object> crawlNaverWebtoonWeekday(@RequestBody Map<String, Object> request) {
        try {
            String weekday = (String) request.get("weekday");
            if (weekday == null || weekday.isBlank()) {
                return Map.of(
                        "success", false,
                        "error", "weekday 파라미터가 필요합니다. (mon, tue, wed, thu, fri, sat, sun)"
                );
            }

            naverWebtoonService.crawlWeekday(weekday); // 비동기 실행
            return Map.of(
                    "success", true,
                    "message", weekday + " 요일 웹툰 크롤링 작업이 비동기로 시작되었습니다.",
                    "weekday", weekday
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    // 완결 웹툰 크롤링
    @PostMapping("/crawl/naver-webtoon/finished")
    public Map<String, Object> crawlNaverWebtoonFinished(@RequestBody Map<String, Object> request) {
        try {
            Integer maxPages = request.get("maxPages") != null
                    ? (Integer) request.get("maxPages")
                    : 10; // 기본값 10페이지

            naverWebtoonService.crawlFinishedWebtoons(maxPages); // 비동기 실행
            return Map.of(
                    "success", true,
                    "message", "완결 웹툰 크롤링 작업이 비동기로 시작되었습니다. (최대 " + maxPages + "페이지)",
                    "maxPages", maxPages
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    // 동기 버전 - 테스트용 (즉시 결과 반환)
    @PostMapping("/crawl/naver-webtoon/weekday/sync")
    public Map<String, Object> crawlNaverWebtoonWeekdaySync(@RequestBody Map<String, Object> request) {
        try {
            String weekday = (String) request.get("weekday");
            if (weekday == null || weekday.isBlank()) {
                return Map.of(
                        "success", false,
                        "error", "weekday 파라미터가 필요합니다. (mon, tue, wed, thu, fri, sat, sun)"
                );
            }

            int saved = naverWebtoonService.crawlWeekdaySync(weekday); // 동기 실행
            return Map.of(
                    "success", true,
                    "message", weekday + " 요일 웹툰 크롤링이 완료되었습니다.",
                    "weekday", weekday,
                    "savedCount", saved
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
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
                    "totalSavedCount", totalSaved
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
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
                    "savedCount", saved
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }


    /* ===================== NAVER SERIES ===================== */

    // 네이버 시리즈 크롤 → raw_items 적재 (완결작품 페이지)
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

    /* ===================== KAKAO PAGE ===================== */

    // (1) 카카오페이지 목록 URL 기반 수집 → raw_items
    @PostMapping(path = "/crawl/kakaopage/api", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> crawlKakaoPageByApi(@RequestBody KpApiRequest req) {
        try {
            // 요청 파라미터가 null일 경우 기본값 설정
            String sectionId = (req.sectionId() == null || req.sectionId().isBlank())
                    ? "static-landing-Genre-section-Landing-11-0-UPDATE-false" : req.sectionId();
            int categoryUid = (req.categoryUid() == null) ? 11 : req.categoryUid(); // 11: 웹소설
            String subcategoryUid = (req.subcategoryUid() == null) ? "0" : req.subcategoryUid(); // 0: 전체
            String sortType = (req.sortType() == null || req.sortType().isBlank()) ? "UPDATE" : req.sortType(); // UPDATE: 업데이트순
            boolean isComplete = (req.isComplete() == null) ? false : req.isComplete(); // false: 연재중
            int pages = (req.pages() == null || req.pages() <= 0) ? 10 : req.pages(); // 기본 10페이지

            int saved = kakaoPageCrawler.crawlToRaw(
                    sectionId, categoryUid, subcategoryUid, sortType, isComplete, req.cookie(), pages
            );
            long pending = rawRepo.countByProcessedFalse();

            Map<String, Object> usedParams = Map.of(
                    "sectionId", sectionId, "categoryUid", categoryUid, "subcategoryUid", subcategoryUid,
                    "sortType", sortType, "isComplete", isComplete, "pages", pages
            );

            return Map.of(
                    "success", true,
                    "message", "KakaoPage API crawling completed.",
                    "saved", saved,
                    "pendingRaw", pending,
                    "parameters", usedParams
            );
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }



    /* ===================== BATCH / TRANSFORM / UPSERT ===================== */

    // 배치 변환/업서트 실행 (raw_items → contents/platform_data)
    @PostMapping(path = "/batch/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runBatch(@RequestBody BatchRequest req) {
        int size = (req.batchSize() == null || req.batchSize() <= 0) ? 100 : req.batchSize();
        int processed = batchService.processBatch(size);
        long stillPending = rawRepo.countByProcessedFalse();

        return Map.of(
                "batchSize", size,
                "processed", processed,
                "pendingRaw", stillPending
        );
    }

    // 🚀 최적화된 배치 처리 (대용량 처리용)
    @PostMapping(path = "/batch/process-optimized", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runBatchOptimized(@RequestBody BatchRequestOptimized req) {
        long startTime = System.currentTimeMillis();
        int batchSize = req.batchSize() != null && req.batchSize() > 0 ? req.batchSize() : 500;
        
        int processed = batchServiceOptimized.processBatchOptimized(batchSize);
        long stillPending = rawRepo.countByProcessedFalse();
        long elapsed = System.currentTimeMillis() - startTime;
        
        return Map.of(
                "batchSize", batchSize,
                "processed", processed,
                "pendingRaw", stillPending,
                "elapsedMs", elapsed,
                "itemsPerSecond", processed * 1000L / Math.max(elapsed, 1)
        );
    }

    // 🔥 병렬 배치 처리 (초고속 대량 처리)
    @PostMapping(path = "/batch/process-parallel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runBatchParallel(@RequestBody BatchRequestParallel req) {
        long startTime = System.currentTimeMillis();
        
        int totalItems = req.totalItems() != null && req.totalItems() > 0 ? req.totalItems() : 10000;
        int batchSize = req.batchSize() != null && req.batchSize() > 0 ? req.batchSize() : 500;
        int numWorkers = req.numWorkers() != null && req.numWorkers() > 0 ? req.numWorkers() : 4;
        
        int processed = batchServiceOptimized.processInParallel(totalItems, batchSize, numWorkers);
        long stillPending = rawRepo.countByProcessedFalse();
        long elapsed = System.currentTimeMillis() - startTime;
        
        return Map.of(
                "totalItems", totalItems,
                "batchSize", batchSize,
                "numWorkers", numWorkers,
                "processed", processed,
                "pendingRaw", stillPending,
                "elapsedMs", elapsed,
                "itemsPerSecond", processed * 1000L / Math.max(elapsed, 1)
        );
    }

    // 규칙 프리뷰: payload + rulePath로 transform만 수행해 확인 (DB 반영 X)
    @PostMapping(path = "/transform/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> previewTransform(@RequestBody PreviewRequest req) {
        String rulePath = (req.rulePath() != null && !req.rulePath().isBlank())
                ? req.rulePath()
                : defaultRulePath(req.domain(), req.platformName());

        MappingRule rule = ruleLoader.load(rulePath);
        var tri = transformEngine.transform(req.payload(), rule);
        return Map.of(
                "rulePath", rulePath,
                "master", tri.master(),
                "platform", tri.platform(),
                "domain", tri.domain()
        );
    }

    private String defaultRulePath(String domain, String platform) {
        if ("WEBNOVEL".equalsIgnoreCase(domain)) {
            if ("NaverSeries".equalsIgnoreCase(platform)) return "rules/webnovel/naverseries.yml";
            if ("KakaoPage".equalsIgnoreCase(platform))   return "rules/webnovel/kakaopage.yml";
        }
        if ("WEBTOON".equalsIgnoreCase(domain)) {
            if ("NaverWebtoon".equalsIgnoreCase(platform)) return "rules/webtoon/naverwebtoon.yml";
        }
        if ("AV".equalsIgnoreCase(domain)) {
            if ("TMDB".equalsIgnoreCase(platform)) return "rules/av/tmdb.yml";
        }
        if ("GAME".equalsIgnoreCase(domain)) {
            if ("Steam".equalsIgnoreCase(platform)) return "rules/game/steam.yml";
        }
        throw new IllegalArgumentException("No default rule for domain=" + domain + ", platform=" + platform);
    }

    /* ===================== 요청 DTO ===================== */

    public record CrawlRequest(String baseListUrl, String cookie, Integer pages) {}

    // 카카오페이지 API 요청을 위한 새로운 DTO
    public record KpApiRequest(
            String sectionId,
            Integer categoryUid,
            String subcategoryUid,
            String sortType,
            Boolean isComplete,
            String cookie,
            Integer pages
    ) {}


    //public record CrawlRequest(String baseListUrl, String cookie, Integer pages) {}
    public record KpListRequest(String listUrl, String cookie, Integer pages) {}
    public record KpCollectRequest(List<String> urls, String cookie) {}

    public record BatchRequest(Integer batchSize) {}
    public record BatchRequestOptimized(Integer batchSize) {}
    public record BatchRequestParallel(Integer totalItems, Integer batchSize, Integer numWorkers) {}
    public record PreviewRequest(String platformName, String domain, String rulePath, Map<String,Object> payload) {}
    public record UpsertDirectRequest(String domain,
                                      Map<String,Object> master,
                                      Map<String,Object> platform,
                                      Map<String,Object> domainDoc,
                                      String platformSpecificId,
                                      String url,
                                      String rulePath) {}

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
        int processed = batchService.processBatch(1);
        
        return Map.of(
                "message", "RawItem 재처리 완료",
                "rawId", rawId,
                "processed", processed > 0
        );
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
        int processed = batchService.processBatch(count);
        
        return Map.of(
                "message", "최근 " + count + "개 RawItem 재처리 완료",
                "reprocessedIds", recentRaws.stream().map(RawItem::getRawId).toList(),
                "successCount", processed
        );
    }

    /**
     * 배치 처리 API - Admin UI에서 호출
     */
    @PostMapping("/batch/process")
    public Map<String, Object> processBatch(@RequestParam(defaultValue = "100") int batchSize) {
        try {
            long pendingCount = rawRepo.countByProcessedFalse();
            int processed = batchService.processBatch(batchSize);
            
            return Map.of(
                    "success", true,
                    "message", "배치 처리 완료",
                    "pendingBefore", pendingCount,
                    "processedCount", processed,
                    "pendingAfter", rawRepo.countByProcessedFalse()
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }
}


