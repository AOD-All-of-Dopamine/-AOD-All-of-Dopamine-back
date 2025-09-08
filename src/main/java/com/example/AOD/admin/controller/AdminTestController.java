package com.example.AOD.admin.controller;

import com.example.AOD.Novel.NaverSeriesNovel.crawler.NaverSeriesCrawler;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.ingest.BatchTransformService;
import com.example.AOD.ingest.RawItemRepository;
import com.example.AOD.rules.MappingRule;
import com.example.AOD.service.RuleLoader;
import com.example.AOD.service.TransformEngine;
import com.example.AOD.service.UpsertService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AdminTestController {

    private final NaverSeriesCrawler naverSeriesCrawler;
    private final BatchTransformService batchService;
    private final RawItemRepository rawRepo;
    private final RuleLoader ruleLoader;
    private final TransformEngine transformEngine;
    private final UpsertService upsertService;

    public AdminTestController(NaverSeriesCrawler naverSeriesCrawler,
                               BatchTransformService batchService,
                               RawItemRepository rawRepo,
                               RuleLoader ruleLoader,
                               TransformEngine transformEngine,
                               UpsertService upsertService) {
        this.naverSeriesCrawler = naverSeriesCrawler;
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

    // 1) 네이버 시리즈 크롤 → raw_items 적재
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

    // 2) 배치 변환/업서트 실행 (raw_items → contents/platform_data)
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

    // 3) 규칙 프리뷰: payload + rulePath로 transform만 수행해 확인 (DB 반영 X)
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

    // 4) 직접 업서트 테스트: 변환 결과를 수동으로 던져 DB 반영해보기
    @PostMapping(path = "/upsert/direct", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> upsertDirect(@RequestBody UpsertDirectRequest req) {
        Long contentId = upsertService.upsert(
                Domain.valueOf(req.domain()),
                req.master(),
                req.platform(),
                req.domainDoc(),
                req.platformSpecificId(),
                req.url()
        );
        return Map.of("contentId", contentId);
    }

    private String defaultRulePath(String domain, String platform) {
        if ("WEBNOVEL".equalsIgnoreCase(domain)) {
            if ("NaverSeries".equalsIgnoreCase(platform)) return "rules/webnovel/naverseries.yml";
            if ("KakaoPage".equalsIgnoreCase(platform))   return "rules/webnovel/kakaopage.yml";
        }
        throw new IllegalArgumentException("No default rule for domain=" + domain + ", platform=" + platform);
    }

    // ==== 요청 DTO ====

    public record CrawlRequest(String baseListUrl, String cookie, Integer pages) {}
    public record BatchRequest(Integer batchSize) {}
    public record PreviewRequest(String platformName, String domain, String rulePath, Map<String,Object> payload) {}
    public record UpsertDirectRequest(String domain,
                                      Map<String,Object> master,
                                      Map<String,Object> platform,
                                      Map<String,Object> domainDoc,
                                      String platformSpecificId,
                                      String url) {}
}