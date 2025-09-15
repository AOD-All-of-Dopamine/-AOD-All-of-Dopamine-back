package com.example.AOD.ingest;


import com.example.AOD.domain.entity.Domain;
import com.example.AOD.rules.MappingRule;
import com.example.AOD.service.RuleLoader;
import com.example.AOD.service.TransformEngine;
import com.example.AOD.service.UpsertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class BatchTransformService {

    private final RawItemRepository rawRepo;
    private final TransformRunRepository runRepo;
    private final RuleLoader ruleLoader;
    private final TransformEngine transform;
    private final UpsertService upsert;

    // 플랫폼/도메인 → 규칙 경로 매핑
    private String rulePath(String domain, String platformName) {
        return switch (domain) {
            case "WEBNOVEL" -> switch (platformName) {
                case "NaverSeries" -> "rules/webnovel/naverseries.yml";
                case "KakaoPage"   -> "rules/webnovel/kakaopage.yml";
                default -> throw new IllegalArgumentException("No rule for webnovel platform: " + platformName);
            };
            case "AV" -> switch (platformName) { // [수정] AV 도메인 추가
                case "TMDB" -> "rules/av/tmdb.yml";
                default -> throw new IllegalArgumentException("No rule for AV platform: " + platformName);
            };
            case "GAME" -> /* ... */ "";
            default -> throw new IllegalArgumentException("No rule for domain "+domain);
        };
    }

    @Transactional
    public int processBatch(int batchSize) {
        List<RawItem> batch = rawRepo.lockNextBatch(batchSize);
        int ok = 0;
        for (RawItem raw : batch) {
            TransformRun run = new TransformRun();
            run.setRawId(raw.getRawId());
            run.setPlatformName(raw.getPlatformName());
            run.setDomain(raw.getDomain());
            try {
                String rp = rulePath(raw.getDomain(), raw.getPlatformName());
                run.setRulePath(rp);

                MappingRule rule = ruleLoader.load(rp);
                var tri = transform.transform(raw.getSourcePayload(), rule);

                // platformSpecificId / url 은 raw에 우선, 없으면 payload에서 추정
                String psid = firstNonNull(raw.getPlatformSpecificId(), // 1순위 (공통)
                        asString(deepGet(raw.getSourcePayload(), "platformSpecificId")), // 2순위 (공통)
                        asString(deepGet(raw.getSourcePayload(), "movie_details.id")), // 3순위 (TMDB 영화)
                        asString(deepGet(raw.getSourcePayload(), "tv_details.id")), // 4순위 (TMDB TV)
                        asString(deepGet(raw.getSourcePayload(), "titleId")), // 5순위 (네이버 시리즈)
                        asString(deepGet(raw.getSourcePayload(), "seriesId")) // 6순위 (카카오페이지)
                );

                String url = firstNonNull(raw.getUrl(), asString(deepGet(raw.getSourcePayload(), "url")));

                Long contentId = upsert.upsert(
                        Domain.valueOf(rule.getDomain()),
                        tri.master(), tri.platform(), tri.domain(),
                        psid, url
                );

                run.setStatus("SUCCESS");
                run.setProducedContentId(contentId);
                ok++;
                raw.setProcessed(true);
                raw.setProcessedAt(Instant.now());
            } catch (Exception e) {
                run.setStatus("FAILED");
                run.setError(e.toString());
            } finally {
                run.setFinishedAt(Instant.now());
                runRepo.save(run);
            }
        }
        return ok;
    }

    /* -------- helpers -------- */
    private static Object deepGet(Object obj, String path) {
        if (obj == null || path == null) return null;
        String[] parts = path.split("\\.");
        Object cur = obj;
        for (String p : parts) {
            if (!(cur instanceof Map<?,?> m)) return null;
            cur = m.get(p);
        }
        return cur;
    }
    private static String asString(Object o){ return o==null? null : String.valueOf(o); }
    @SafeVarargs
    private static <T> T firstNonNull(T... vals) {
        for (T v: vals) if (v!=null && !(v instanceof String s && s.isBlank())) return v;
        return null;
    }
}
