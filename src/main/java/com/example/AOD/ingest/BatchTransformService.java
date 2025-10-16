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
            case "AV" -> switch (platformName) {
                case "TMDB" -> "rules/av/tmdb.yml";
                default -> throw new IllegalArgumentException("No rule for AV platform: " + platformName);
            };
            // [신규 추가] GAME 도메인 규칙 추가
            case "GAME" -> switch (platformName) {
                case "Steam" -> "rules/game/steam.yml";
                default -> throw new IllegalArgumentException("No rule for GAME platform: " + platformName);
            };
            case "WEBTOON" -> switch (platformName) {
                case "NaverWebtoon" -> "rules/webtoon/naverwebtoon.yml";
                default -> throw new IllegalArgumentException("No rule for WEBTOON platform: " + platformName);
            };
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

                // [수정] Steam의 steam_appid를 가져오도록 경로 추가
                String psid = firstNonNull(raw.getPlatformSpecificId(),
                        asString(deepGet(raw.getSourcePayload(), "platformSpecificId")),
                        asString(deepGet(raw.getSourcePayload(), "steam_appid")), // Steam
                        asString(deepGet(raw.getSourcePayload(), "movie_details.id")),
                        asString(deepGet(raw.getSourcePayload(), "tv_details.id")),
                        asString(deepGet(raw.getSourcePayload(), "titleId")),
                        asString(deepGet(raw.getSourcePayload(), "seriesId"))
                );

                String url = firstNonNull(raw.getUrl(), asString(deepGet(raw.getSourcePayload(), "url")));

                Long contentId = upsert.upsert(
                        Domain.valueOf(rule.getDomain()),
                        tri.master(), tri.platform(), tri.domain(),
                        psid, url,
                        rule // [ ✨ 수정 ] 로드한 rule 객체를 upsert 메서드에 전달
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