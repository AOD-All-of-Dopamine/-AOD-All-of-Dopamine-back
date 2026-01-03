package com.example.AOD.performance;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 성능 측정 메트릭
 */
@Data
@Builder
public class PerformanceMetrics {
    
    // 기본 정보
    private String testName;
    private String version;  // "BEFORE" or "AFTER"
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    // 처리량
    private int totalItems;
    private int successItems;
    private int failedItems;
    
    // 시간
    private long durationMs;
    private double throughputPerSecond;  // 처리량/초
    
    // 리소스
    private long startMemoryMb;
    private long endMemoryMb;
    private long peakMemoryMb;
    private int threadCount;
    
    // 데이터베이스
    private long dbQueryCount;
    private long dbInsertCount;
    private long dbUpdateCount;
    
    // 계산된 값들
    public static PerformanceMetrics calculate(
            String testName,
            String version,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int totalItems,
            int successItems,
            int failedItems,
            long startMemoryMb,
            long endMemoryMb,
            long peakMemoryMb) {
        
        Duration duration = Duration.between(startTime, endTime);
        long durationMs = duration.toMillis();
        double durationSeconds = durationMs / 1000.0;
        double throughput = durationSeconds > 0 ? successItems / durationSeconds : 0;
        
        return PerformanceMetrics.builder()
                .testName(testName)
                .version(version)
                .startTime(startTime)
                .endTime(endTime)
                .totalItems(totalItems)
                .successItems(successItems)
                .failedItems(failedItems)
                .durationMs(durationMs)
                .throughputPerSecond(throughput)
                .startMemoryMb(startMemoryMb)
                .endMemoryMb(endMemoryMb)
                .peakMemoryMb(peakMemoryMb)
                .build();
    }
    
    /**
     * 포맷된 결과 출력
     */
    public String toFormattedString() {
        return String.format("""
                
                ═══════════════════════════════════════════════════════
                📊 성능 측정 결과: %s
                ═══════════════════════════════════════════════════════
                버전: %s
                
                ⏱️  처리 시간:
                   - 시작: %s
                   - 종료: %s
                   - 소요: %,d ms (%.2f 초)
                
                📦 처리량:
                   - 전체: %,d 건
                   - 성공: %,d 건
                   - 실패: %,d 건
                   - 성공률: %.2f%%
                
                🚀 처리 속도:
                   - %.2f 건/초
                   - %.2f 건/분
                
                💾 메모리 사용:
                   - 시작: %,d MB
                   - 종료: %,d MB
                   - 피크: %,d MB
                   - 증가량: %,d MB
                
                ═══════════════════════════════════════════════════════
                """,
                testName,
                version,
                startTime,
                endTime,
                durationMs,
                durationMs / 1000.0,
                totalItems,
                successItems,
                failedItems,
                totalItems > 0 ? (successItems * 100.0 / totalItems) : 0,
                throughputPerSecond,
                throughputPerSecond * 60,
                startMemoryMb,
                endMemoryMb,
                peakMemoryMb,
                endMemoryMb - startMemoryMb
        );
    }
    
    /**
     * CSV 헤더
     */
    public static String csvHeader() {
        return "TestName,Version,StartTime,EndTime,DurationMs,TotalItems,SuccessItems,FailedItems," +
               "ThroughputPerSec,StartMemoryMb,EndMemoryMb,PeakMemoryMb";
    }
    
    /**
     * CSV 행
     */
    public String toCsvRow() {
        return String.format("%s,%s,%s,%s,%d,%d,%d,%d,%.2f,%d,%d,%d",
                testName,
                version,
                startTime,
                endTime,
                durationMs,
                totalItems,
                successItems,
                failedItems,
                throughputPerSecond,
                startMemoryMb,
                endMemoryMb,
                peakMemoryMb
        );
    }
}


