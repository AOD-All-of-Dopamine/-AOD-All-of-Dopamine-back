package com.example.AOD.performance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 성능 모니터링 유틸리티
 */
@Slf4j
@Component
public class PerformanceMonitor {
    
    private static final Runtime runtime = Runtime.getRuntime();
    
    /**
     * 현재 메모리 사용량 (MB)
     */
    public static long getCurrentMemoryMb() {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    /**
     * GC 실행 (측정 전후에 호출)
     */
    public static void runGC() {
        System.gc();
        try {
            Thread.sleep(100);  // GC가 완료될 시간 제공
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 성능 측정 세션
     */
    public static class Session {
        private final String testName;
        private final String version;
        private final LocalDateTime startTime;
        private final long startMemory;
        private long peakMemory;
        private int totalItems;
        private int successItems;
        private int failedItems;
        
        public Session(String testName, String version) {
            this.testName = testName;
            this.version = version;
            
            // 시작 전 GC
            runGC();
            
            this.startTime = LocalDateTime.now();
            this.startMemory = getCurrentMemoryMb();
            this.peakMemory = startMemory;
            
            log.info("🔬 성능 측정 시작: {} ({})", testName, version);
            log.info("   시작 시간: {}", startTime);
            log.info("   시작 메모리: {} MB", startMemory);
        }
        
        /**
         * 처리 결과 기록
         */
        public void recordBatch(int total, int success, int failed) {
            this.totalItems += total;
            this.successItems += success;
            this.failedItems += failed;
            
            // 피크 메모리 업데이트
            long currentMemory = getCurrentMemoryMb();
            if (currentMemory > peakMemory) {
                peakMemory = currentMemory;
            }
        }
        
        /**
         * 단일 항목 성공 기록
         */
        public void recordSuccess() {
            this.totalItems++;
            this.successItems++;
            updatePeakMemory();
        }
        
        /**
         * 단일 항목 실패 기록
         */
        public void recordFailure() {
            this.totalItems++;
            this.failedItems++;
            updatePeakMemory();
        }
        
        private void updatePeakMemory() {
            long currentMemory = getCurrentMemoryMb();
            if (currentMemory > peakMemory) {
                peakMemory = currentMemory;
            }
        }
        
        /**
         * 측정 종료 및 결과 반환
         */
        public PerformanceMetrics finish() {
            // 종료 전 GC
            runGC();
            
            LocalDateTime endTime = LocalDateTime.now();
            long endMemory = getCurrentMemoryMb();
            
            PerformanceMetrics metrics = PerformanceMetrics.calculate(
                    testName,
                    version,
                    startTime,
                    endTime,
                    totalItems,
                    successItems,
                    failedItems,
                    startMemory,
                    endMemory,
                    peakMemory
            );
            
            log.info(metrics.toFormattedString());
            
            return metrics;
        }
    }
    
    /**
     * 새로운 성능 측정 세션 시작
     */
    public static Session startSession(String testName, String version) {
        return new Session(testName, version);
    }
}


