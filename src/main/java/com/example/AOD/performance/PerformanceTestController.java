package com.example.AOD.performance;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.ingest.BatchTransformService;
import com.example.AOD.ingest.BatchTransformServiceOptimized;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 🔬 성능 측정 전용 컨트롤러
 * 포트폴리오용 Before/After 비교 데이터 수집
 */
@Slf4j
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceTestController {

    private final BatchTransformService originalService;
    private final BatchTransformServiceOptimized optimizedService;
    private final WorkApiService workApiService;
    
    // 🔥 Actuator 통합 모니터 (기존 PerformanceMonitor 대신)
    private final PerformanceMonitorWithActuator actuatorMonitor;
    
    // 스레드풀 참조 (AsyncConfig에서 정의된 빈)
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("crawlerTaskExecutor")
    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor crawlerExecutor;
    
    /**
     * 🔴 BEFORE: 최적화 전 배치 처리 성능 측정
     * 🔥 Actuator 통합: Prometheus/Grafana 자동 수집
     */
    @PostMapping("/test/before")
    public PerformanceTestResult testBeforeOptimization(
            @RequestParam(defaultValue = "100") int batchSize,
            @RequestParam(defaultValue = "10") int iterations) {
        
        log.info("🔴 최적화 전 성능 테스트 시작 (Actuator 통합)");
        log.info("   배치 크기: {}", batchSize);
        log.info("   반복 횟수: {}", iterations);
        
        var session = actuatorMonitor.startSession("Batch Processing", "BEFORE");
        
        List<Integer> processedCounts = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            log.info("   반복 {}/{}", i + 1, iterations);
            
            int processed = originalService.processBatch(batchSize);
            processedCounts.add(processed);
            
            if (processed > 0) {
                session.recordBatch(processed, processed, 0);
            }
            
            if (processed == 0) {
                log.warn("   더 이상 처리할 항목이 없습니다.");
                break;
            }
            
            // 배치 간 잠깐 대기 (안정화)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        PerformanceMetrics metrics = session.finish();
        
        return PerformanceTestResult.builder()
                .metrics(metrics)
                .processedCounts(processedCounts)
                .message("최적화 전 테스트 완료")
                .build();
    }
    
    /**
     * 🟢 AFTER: 최적화 후 배치 처리 성능 측정
     * 🔥 Actuator 통합: Prometheus/Grafana 자동 수집
     */
    @PostMapping("/test/after")
    public PerformanceTestResult testAfterOptimization(
            @RequestParam(defaultValue = "500") int batchSize,
            @RequestParam(defaultValue = "10") int iterations) {
        
        log.info("🟢 최적화 후 성능 테스트 시작 (Actuator 통합)");
        log.info("   배치 크기: {}", batchSize);
        log.info("   반복 횟수: {}", iterations);
        
        var session = actuatorMonitor.startSession("Batch Processing", "AFTER");
        
        List<Integer> processedCounts = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            log.info("   반복 {}/{}", i + 1, iterations);
            
            int processed = optimizedService.processBatchOptimized(batchSize);
            processedCounts.add(processed);
            
            if (processed > 0) {
                session.recordBatch(processed, processed, 0);
            }
            
            if (processed == 0) {
                log.warn("   더 이상 처리할 항목이 없습니다.");
                break;
            }
            
            // 배치 간 잠깐 대기 (안정화)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        PerformanceMetrics metrics = session.finish();
        
        return PerformanceTestResult.builder()
                .metrics(metrics)
                .processedCounts(processedCounts)
                .message("최적화 후 테스트 완료")
                .build();
    }
    
    /**
     * 🔥 병렬 처리 성능 측정 (최적화 AFTER+)
     * 🔥 Actuator 통합: Prometheus/Grafana 자동 수집
     */
    @PostMapping("/test/parallel")
    public PerformanceTestResult testParallelProcessing(
            @RequestParam(defaultValue = "1000") int totalItems,
            @RequestParam(defaultValue = "500") int batchSize,
            @RequestParam(defaultValue = "4") int numWorkers) {
        
        log.info("🔥 병렬 처리 성능 테스트 시작 (Actuator 통합)");
        log.info("   전체 항목: {}", totalItems);
        log.info("   배치 크기: {}", batchSize);
        log.info("   워커 수: {}", numWorkers);
        
        var session = actuatorMonitor.startSession("Parallel Batch Processing", "AFTER_PARALLEL");
        
        int processed = optimizedService.processInParallel(totalItems, batchSize, numWorkers);
        
        if (processed > 0) {
            session.recordBatch(processed, processed, 0);
        }
        
        PerformanceMetrics metrics = session.finish();
        
        return PerformanceTestResult.builder()
                .metrics(metrics)
                .processedCounts(List.of(processed))
                .message("병렬 처리 테스트 완료")
                .build();
    }
    
    /**
     * 📊 비교 테스트 실행 (Before + After 자동 비교)
     */
    @PostMapping("/test/compare")
    public ComparisonResult runComparisonTest(
            @RequestParam(defaultValue = "100") int beforeBatchSize,
            @RequestParam(defaultValue = "500") int afterBatchSize,
            @RequestParam(defaultValue = "5") int iterations) {
        
        log.info("📊 비교 테스트 시작");
        
        // Before 테스트
        PerformanceTestResult beforeResult = testBeforeOptimization(beforeBatchSize, iterations);
        
        // 잠깐 대기
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // After 테스트
        PerformanceTestResult afterResult = testAfterOptimization(afterBatchSize, iterations);
        
        // 비교 결과 계산
        PerformanceMetrics before = beforeResult.getMetrics();
        PerformanceMetrics after = afterResult.getMetrics();
        
        double speedImprovement = after.getThroughputPerSecond() / before.getThroughputPerSecond();
        double timeReduction = (1 - (after.getDurationMs() / (double) before.getDurationMs())) * 100;
        
        String comparison = String.format("""
                
                ═══════════════════════════════════════════════════════
                📊 최적화 전후 비교 결과
                ═══════════════════════════════════════════════════════
                
                ⏱️  처리 시간:
                   Before: %,d ms (%.2f초)
                   After:  %,d ms (%.2f초)
                   개선:   %.1f%% 단축 ⭐
                
                🚀 처리 속도:
                   Before: %.2f 건/초
                   After:  %.2f 건/초
                   개선:   %.1f배 향상 ⭐⭐⭐
                
                📦 처리량:
                   Before: %,d 건
                   After:  %,d 건
                
                💾 메모리:
                   Before: %,d MB
                   After:  %,d MB
                   차이:   %+d MB
                
                ═══════════════════════════════════════════════════════
                """,
                before.getDurationMs(), before.getDurationMs() / 1000.0,
                after.getDurationMs(), after.getDurationMs() / 1000.0,
                timeReduction,
                before.getThroughputPerSecond(),
                after.getThroughputPerSecond(),
                speedImprovement,
                before.getSuccessItems(),
                after.getSuccessItems(),
                before.getPeakMemoryMb(),
                after.getPeakMemoryMb(),
                after.getPeakMemoryMb() - before.getPeakMemoryMb()
        );
        
        log.info(comparison);
        
        return ComparisonResult.builder()
                .beforeMetrics(before)
                .afterMetrics(after)
                .speedImprovementFactor(speedImprovement)
                .timeReductionPercent(timeReduction)
                .comparisonSummary(comparison)
                .build();
    }
    
    /**
     * 💾 결과를 CSV 파일로 저장
     */
    @PostMapping("/export/csv")
    public String exportToCsv(@RequestBody List<PerformanceMetrics> metricsList) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "performance_results_" + timestamp + ".csv";
        Path path = Paths.get("performance-reports", filename);
        
        // 디렉토리 생성
        Files.createDirectories(path.getParent());
        
        try (FileWriter writer = new FileWriter(path.toFile())) {
            // 헤더
            writer.write(PerformanceMetrics.csvHeader() + "\n");
            
            // 데이터
            for (PerformanceMetrics metrics : metricsList) {
                writer.write(metrics.toCsvRow() + "\n");
            }
        }
        
        log.info("📄 CSV 파일 저장 완료: {}", path.toAbsolutePath());
        
        return path.toAbsolutePath().toString();
    }
    
    /**
     * 🧵 스레드풀 상태 조회
     */
    @GetMapping("/threadpool/status")
    public ThreadPoolStatusResponse getThreadPoolStatus() {
        if (crawlerExecutor == null) {
            return ThreadPoolStatusResponse.builder()
                    .available(false)
                    .message("Crawler ThreadPool not available")
                    .build();
        }
        
        ThreadPoolMetrics metrics = ThreadPoolMonitor.captureMetrics("Crawler Pool", crawlerExecutor);
        ThreadPoolMonitor.HealthStatus health = ThreadPoolMonitor.checkHealth(crawlerExecutor);
        double utilization = ThreadPoolMonitor.calculateUtilization(crawlerExecutor);
        
        log.info(metrics.toFormattedString());
        
        return ThreadPoolStatusResponse.builder()
                .available(true)
                .metrics(metrics)
                .healthStatus(health.name())
                .healthLabel(health.getLabel())
                .utilization(utilization)
                .message("Thread pool status captured")
                .build();
    }
    
    /**
     * 🧵 스레드풀 부하 테스트
     */
    @PostMapping("/threadpool/load-test")
    public ThreadPoolLoadTestResult runThreadPoolLoadTest(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "1000") int taskDurationMs) {
        
        if (crawlerExecutor == null) {
            throw new IllegalStateException("Crawler ThreadPool not available");
        }
        
        log.info("🧵 스레드풀 부하 테스트 시작");
        log.info("   작업 수: {}", taskCount);
        log.info("   작업 소요 시간: {} ms", taskDurationMs);
        
        // 테스트 시작 전 메트릭
        ThreadPoolMetrics beforeMetrics = ThreadPoolMonitor.captureMetrics("Before Load Test", crawlerExecutor);
        
        long startTime = System.currentTimeMillis();
        List<ThreadPoolMetrics> snapshots = new ArrayList<>();
        
        // 작업 제출
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            crawlerExecutor.submit(() -> {
                try {
                    log.debug("   작업 {} 시작", taskId);
                    Thread.sleep(taskDurationMs);
                    log.debug("   작업 {} 완료", taskId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("   작업 {} 중단됨", taskId);
                }
            });
            
            // 10개 작업마다 스냅샷
            if (i % 10 == 0 && i > 0) {
                ThreadPoolMetrics snapshot = ThreadPoolMonitor.captureMetrics(
                        "Snapshot at task " + i, 
                        crawlerExecutor
                );
                snapshots.add(snapshot);
                ThreadPoolMonitor.logThreadPoolStatus("Load Test", crawlerExecutor);
                
                // 잠깐 대기
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        log.info("   모든 작업 제출 완료, 완료 대기 중...");
        
        // 모든 작업 완료 대기 (최대 taskCount * taskDurationMs + 여유시간)
        long maxWaitTime = (long) taskCount * taskDurationMs + 10000;
        long waitStart = System.currentTimeMillis();
        
        while (crawlerExecutor.getThreadPoolExecutor().getActiveCount() > 0) {
            if (System.currentTimeMillis() - waitStart > maxWaitTime) {
                log.warn("   타임아웃: 일부 작업이 완료되지 않았습니다.");
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;
        
        // 테스트 종료 후 메트릭
        ThreadPoolMetrics afterMetrics = ThreadPoolMonitor.captureMetrics("After Load Test", crawlerExecutor);
        
        double tasksPerSecond = taskCount / (totalDuration / 1000.0);
        
        String summary = String.format("""
                
                ═══════════════════════════════════════════════════════
                🧵 스레드풀 부하 테스트 결과
                ═══════════════════════════════════════════════════════
                
                📋 테스트 설정:
                   - 작업 수: %,d
                   - 작업 소요 시간: %,d ms
                   - 총 소요 시간: %,d ms (%.2f초)
                
                🚀 처리 성능:
                   - 작업 처리 속도: %.2f 작업/초
                   - 평균 대기 시간: %.2f ms
                
                🧵 스레드풀 활용:
                   - Core Pool Size: %d
                   - Max Pool Size: %d
                   - 최대 활성 스레드: %d
                   - 큐 용량: %d
                   - 최대 큐 사용: %d
                
                💾 리소스:
                   - 시작 메모리: %,d MB
                   - 종료 메모리: %,d MB
                   - 메모리 증가: %+d MB
                
                ═══════════════════════════════════════════════════════
                """,
                taskCount,
                taskDurationMs,
                totalDuration,
                totalDuration / 1000.0,
                tasksPerSecond,
                (double) totalDuration / taskCount,
                beforeMetrics.getCorePoolSize(),
                beforeMetrics.getMaxPoolSize(),
                afterMetrics.getPoolSize(),
                beforeMetrics.getQueueCapacity(),
                snapshots.stream().mapToInt(ThreadPoolMetrics::getQueueSize).max().orElse(0),
                beforeMetrics.getMemoryUsageMb(),
                afterMetrics.getMemoryUsageMb(),
                afterMetrics.getMemoryUsageMb() - beforeMetrics.getMemoryUsageMb()
        );
        
        log.info(summary);
        
        return ThreadPoolLoadTestResult.builder()
                .taskCount(taskCount)
                .totalDurationMs(totalDuration)
                .tasksPerSecond(tasksPerSecond)
                .beforeMetrics(beforeMetrics)
                .afterMetrics(afterMetrics)
                .snapshots(snapshots)
                .summary(summary)
                .build();
    }
    
    // DTO 클래스들
    
    @lombok.Data
    @lombok.Builder
    public static class ThreadPoolStatusResponse {
        private boolean available;
        private ThreadPoolMetrics metrics;
        private String healthStatus;
        private String healthLabel;
        private double utilization;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ThreadPoolLoadTestResult {
        private int taskCount;
        private long totalDurationMs;
        private double tasksPerSecond;
        private ThreadPoolMetrics beforeMetrics;
        private ThreadPoolMetrics afterMetrics;
        private List<ThreadPoolMetrics> snapshots;
        private String summary;
    }
    
    // DTO 클래스들
    
    @lombok.Data
    @lombok.Builder
    public static class PerformanceTestResult {
        private PerformanceMetrics metrics;
        private List<Integer> processedCounts;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ComparisonResult {
        private PerformanceMetrics beforeMetrics;
        private PerformanceMetrics afterMetrics;
        private double speedImprovementFactor;
        private double timeReductionPercent;
        private String comparisonSummary;
    }
    
    // ========================================
    // 🔥 장르 필터링 성능 테스트
    // ========================================
    
    /**
     * 장르 필터링 성능 테스트 - DB 레벨 필터링
     */
    @GetMapping("/test/genre-filtering")
    public Map<String, Object> testGenreFiltering(
            @RequestParam(required = false) Domain domain,
            @RequestParam(required = false) List<String> genres,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (genres == null || genres.isEmpty()) {
            return Map.of("error", "genres parameter is required");
        }
        
        Pageable pageable = PageRequest.of(page, size);
        
        // DB 레벨 필터링 테스트
        long dbStartTime = System.currentTimeMillis();
        PageResponse<WorkSummaryDTO> dbResult = workApiService.getWorks(domain, null, null, genres, pageable);
        long dbEndTime = System.currentTimeMillis();
        long dbDuration = dbEndTime - dbStartTime;
        
        // 결과 구성
        Map<String, Object> response = new HashMap<>();
        response.put("testInfo", Map.of(
                "domain", domain != null ? domain.name() : "ALL",
                "genres", genres,
                "page", page,
                "size", size
        ));
        
        response.put("dbLevelFiltering", Map.of(
                "duration", dbDuration + "ms",
                "totalElements", dbResult.getTotalElements(),
                "totalPages", dbResult.getTotalPages(),
                "resultCount", dbResult.getContent().size()
        ));
        
        log.info("🔍 Genre filtering test - Domain: {}, Genres: {}, Duration: {}ms, Results: {}",
                domain, genres, dbDuration, dbResult.getTotalElements());
        
        return response;
    }
    
    /**
     * 쿼리 실행 계획 확인 가이드
     */
    @GetMapping("/test/query-plan-guide")
    public Map<String, String> getQueryPlanGuide(
            @RequestParam Domain domain,
            @RequestParam List<String> genres) {
        
        String queryPlanInfo = String.format("""
                PostgreSQL JSONB Query Plan Test
                =================================
                
                Current Query:
                SELECT * FROM %s_contents 
                WHERE genres ?& CAST(ARRAY[%s] AS text[])
                
                To check execution plan in psql:
                EXPLAIN ANALYZE 
                SELECT * FROM %s_contents 
                WHERE genres ?& CAST(ARRAY[%s] AS text[]);
                
                Recommended Index (auto-created on startup):
                CREATE INDEX IF NOT EXISTS idx_%s_genres ON %s_contents USING GIN (genres);
                
                Check if index exists:
                SELECT indexname, indexdef 
                FROM pg_indexes 
                WHERE tablename = '%s_contents' AND indexname LIKE '%%genres%%';
                """,
                domain.name().toLowerCase(),
                String.join(",", genres.stream().map(g -> "'" + g + "'").toArray(String[]::new)),
                domain.name().toLowerCase(),
                String.join(",", genres.stream().map(g -> "'" + g + "'").toArray(String[]::new)),
                domain.name().toLowerCase(),
                domain.name().toLowerCase(),
                domain.name().toLowerCase()
        );
        
        return Map.of("queryPlan", queryPlanInfo);
    }
}
