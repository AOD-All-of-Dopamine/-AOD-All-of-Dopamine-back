package com.example.AOD.admin.controller;

import com.example.AOD.Novel.KakaoPageNovel.KakaoPageCrawler;
import com.example.AOD.Novel.NaverSeriesNovel.NaverSeriesCrawler;

import com.example.AOD.Webtoon.NaverWebtoon.NaverWebtoonService;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.ingest.BatchTransformService;
import com.example.AOD.ingest.RawItemRepository;
import com.example.AOD.rules.MappingRule;
import com.example.AOD.service.RuleLoader;
import com.example.AOD.service.TransformEngine;
import com.example.AOD.service.UpsertService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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
    private final RawItemRepository rawRepo;
    private final RuleLoader ruleLoader;
    private final TransformEngine transformEngine;
    private final UpsertService upsertService;

    public AdminTestController(NaverSeriesCrawler naverSeriesCrawler,
                               KakaoPageCrawler kakaoPageCrawler,
                               NaverWebtoonService naverWebtoonService,  // 추가
                               BatchTransformService batchService,
                               RawItemRepository rawRepo,
                               RuleLoader ruleLoader,
                               TransformEngine transformEngine,
                               UpsertService upsertService) {
        this.naverSeriesCrawler = naverSeriesCrawler;
        this.kakaoPageCrawler = kakaoPageCrawler;
        this.naverWebtoonService = naverWebtoonService;  // 추가
        this.batchService = batchService;
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

    // 네이버 시리즈 크롤 → raw_items 적재
    @PostMapping(path = "/crawl/naver-series", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> crawlNaverSeries(@RequestBody CrawlRequest req) throws Exception {
        String base = (req.baseListUrl() == null || req.baseListUrl().isBlank())
                ? "https://series.naver.com/novel/top100List.series?rankingTypeCode=DAILY&categoryCode=ALL&page="
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
    public record PreviewRequest(String platformName, String domain, String rulePath, Map<String,Object> payload) {}
    public record UpsertDirectRequest(String domain,
                                      Map<String,Object> master,
                                      Map<String,Object> platform,
                                      Map<String,Object> domainDoc,
                                      String platformSpecificId,
                                      String url) {}
}
