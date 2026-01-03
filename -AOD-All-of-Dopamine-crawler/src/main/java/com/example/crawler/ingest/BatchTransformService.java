package com.example.crawler.ingest;


import com.example.shared.entity.Domain;
import com.example.shared.entity.RawItem;
import com.example.shared.repository.RawItemRepository;
import com.example.crawler.rules.MappingRule;
import com.example.crawler.service.RuleLoader;
import com.example.crawler.service.TransformEngine;
import com.example.crawler.service.UpsertService;
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
            case "MOVIE" -> switch (platformName) {
                case "TMDB_MOVIE" -> "rules/movie/tmdb_movie.yml";
                default -> throw new IllegalArgumentException("No rule for MOVIE platform: " + platformName);
            };
            case "TV" -> switch (platformName) {
                case "TMDB_TV" -> "rules/tv/tmdb_tv.yml";
                default -> throw new IllegalArgumentException("No rule for TV platform: " + platformName);
            };
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
        Set<Long> processedContentIds = new HashSet<>(); // 처리된 콘텐츠 ID를 추적하기 위한 Set

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

                // 중복 처리 방지 로직
                if (processedContentIds.contains(contentId)) {
                    // 이미 처리된 콘텐츠 ID인 경우, 성공으로 간주하고 다음 항목으로 넘어감
                    run.setStatus("SUCCESS_DUPLICATE");
                    run.setProducedContentId(contentId);
                    ok++;
                    raw.setProcessed(true);
                    raw.setProcessedAt(Instant.now());
                    continue; // 루프의 다음 반복으로 이동
                }
                processedContentIds.add(contentId); // 새로 처리된 콘텐츠 ID 추가

                run.setStatus("SUCCESS");
                run.setProducedContentId(contentId);
                ok++;
                raw.setProcessed(true);
                raw.setProcessedAt(Instant.now());
            } catch (Exception e) {
                run.setStatus("FAILED");
                run.setError(e.toString());
                throw e; // 디버깅을 위해 예외를 다시 던져서 트랜잭션 롤백의 근본 원인을 확인합니다.
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


