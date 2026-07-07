package com.example.crawler.ingest;

import com.example.shared.entity.Domain;
import com.example.shared.entity.RawItem;
import com.example.shared.repository.RawItemRepository;
import com.example.crawler.rules.MappingRule;
import com.example.crawler.service.RuleRegistry;
import com.example.crawler.service.TransformEngine;
import com.example.crawler.service.UpsertService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 🚀 최적화된 배치 변환 서비스
 * 
 * 주요 개선사항:
 * 1. 배치 크기 증가 (100 → 500~1000)
 * 2. 벌크 처리 (saveAll 사용)
 * 3. 병렬 워커 지원
 * 4. 규칙 캐싱
 * 5. 주기적 flush/clear
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTransformServiceOptimized {

    private final RawItemRepository rawRepo;
    private final TransformRunRepository runRepo;
    private final RuleRegistry ruleRegistry;   // 기동 시 1회 파싱·캐싱 — 별도 룰 캐시 불필요
    private final TransformEngine transform;
    private final UpsertService upsert;
    private final EntityManager entityManager;

    /**
     * 🚀 단일 배치 처리 (최적화 버전)
     * 
     * @param batchSize 배치 크기 (권장: 500~1000)
     * @return 성공적으로 처리된 항목 수
     */
    @Transactional
    public int processBatchOptimized(int batchSize) {
        List<RawItem> batch = rawRepo.lockNextBatch(batchSize);
        if (batch.isEmpty()) {
            return 0;
        }

        log.info("📦 배치 처리 시작: {} 건", batch.size());
        long startTime = System.currentTimeMillis();

        int ok = 0;
        Set<Long> processedContentIds = new HashSet<>();
        List<TransformRun> runsToSave = new ArrayList<>();
        List<RawItem> itemsToUpdate = new ArrayList<>();

        for (int i = 0; i < batch.size(); i++) {
            RawItem raw = batch.get(i);
            TransformRun run = new TransformRun();
            run.setRawId(raw.getRawId());
            run.setPlatformName(raw.getPlatformName());
            run.setDomain(raw.getDomain());

            try {
                MappingRule rule = ruleRegistry.resolve(raw.getDomain(), raw.getPlatformName());
                run.setRulePath(ruleRegistry.pathOf(raw.getPlatformName()));

                var tri = transform.transform(raw.getSourcePayload(), rule);

                String psid = extractPlatformSpecificId(raw);
                String url = firstNonNull(raw.getUrl(), asString(deepGet(raw.getSourcePayload(), "url")));

                Long contentId = upsert.upsert(
                        Domain.valueOf(rule.getDomain()),
                        tri.master(), tri.platform(), tri.domain(),
                        psid, url, rule
                );

                // 중복 체크
                if (processedContentIds.contains(contentId)) {
                    run.setStatus("SUCCESS_DUPLICATE");
                    run.setProducedContentId(contentId);
                } else {
                    processedContentIds.add(contentId);
                    run.setStatus("SUCCESS");
                    run.setProducedContentId(contentId);
                }

                ok++;
                raw.setProcessed(true);
                raw.setProcessedAt(Instant.now());
                itemsToUpdate.add(raw);

            } catch (Exception e) {
                log.error("처리 실패 (rawId={}): {}", raw.getRawId(), e.getMessage());
                run.setStatus("FAILED");
                run.setError(truncate(e.toString(), 500));
                // 실패 시에도 계속 진행 (전체 배치 롤백 방지)
            } finally {
                run.setFinishedAt(Instant.now());
                runsToSave.add(run);
            }

            // 주기적 flush (메모리 관리)
            if (i % 100 == 0 && i > 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        // 🚀 벌크 저장
        runRepo.saveAll(runsToSave);
        rawRepo.saveAll(itemsToUpdate);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("✅ 배치 처리 완료: {} / {} 성공 (소요시간: {}ms, 초당 {} 건)", 
                ok, batch.size(), elapsed, (batch.size() * 1000L / Math.max(elapsed, 1)));

        return ok;
    }

    /**
     * 🔥 병렬 워커로 대량 처리
     * 
     * @param totalItems 처리할 총 항목 수
     * @param batchSize 배치당 크기
     * @param numWorkers 워커 수
     * @return 총 처리 성공 건수
     */
    public int processInParallel(int totalItems, int batchSize, int numWorkers) {
        log.info("🚀 병렬 배치 처리 시작: 총 {} 건, 워커 {} 개", totalItems, numWorkers);
        
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        int iterations = (int) Math.ceil((double) totalItems / batchSize);
        
        for (int i = 0; i < iterations; i++) {
            final int workerNum = i % numWorkers + 1;
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                log.info("🔧 워커 #{} 시작", workerNum);
                return processBatchOptimized(batchSize);
            }, executor);
            
            futures.add(future);
        }

        // 모든 워커 완료 대기
        int totalProcessed = futures.stream()
                .map(CompletableFuture::join)
                .mapToInt(Integer::intValue)
                .sum();

        executor.shutdown();
        log.info("✅ 병렬 배치 처리 완료: 총 {} 건 성공", totalProcessed);
        
        return totalProcessed;
    }

    /**
     * 플랫폼별 ID 추출
     */
    private String extractPlatformSpecificId(RawItem raw) {
        return firstNonNull(
                raw.getPlatformSpecificId(),
                asString(deepGet(raw.getSourcePayload(), "platformSpecificId")),
                asString(deepGet(raw.getSourcePayload(), "steam_appid")),
                asString(deepGet(raw.getSourcePayload(), "movie_details.id")),
                asString(deepGet(raw.getSourcePayload(), "tv_details.id")),
                asString(deepGet(raw.getSourcePayload(), "titleId")),
                asString(deepGet(raw.getSourcePayload(), "seriesId"))
        );
    }

    // ========== 헬퍼 메서드 ==========


    private static Object deepGet(Object obj, String path) {
        if (obj == null || path == null) return null;
        String[] parts = path.split("\\.");
        Object cur = obj;
        for (String p : parts) {
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = m.get(p);
        }
        return cur;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... vals) {
        for (T v : vals) if (v != null && !(v instanceof String s && s.isBlank())) return v;
        return null;
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}


