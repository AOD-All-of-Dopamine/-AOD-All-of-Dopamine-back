package com.example.AOD.performance;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 🔥 Actuator 통합 버전 성능 모니터
 * 
 * 차이점:
 * 1. 커스텀 측정 (기존) - 포트폴리오용 포맷팅된 결과
 * 2. Micrometer 통합 (NEW) - Prometheus/Grafana 자동 수집
 */
@Slf4j
@Component
public class PerformanceMonitorWithActuator {
    
    private static final Runtime runtime = Runtime.getRuntime();
    
    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    
    /**
     * 현재 메모리 사용량 (MB)
     */
    public static long getCurrentMemoryMb() {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    /**
     * GC 실행
     */
    public static void runGC() {
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 🔥 통합 세션 - 커스텀 + Actuator
     */
    public class IntegratedSession {
        private final String testName;
        private final String version;
        private final LocalDateTime startTime;
        private final long startMemory;
        private long peakMemory;
        private int totalItems;
        private int successItems;
        private int failedItems;
        
        // Actuator 통합
        private final Timer.Sample timerSample;
        private final Counter successCounter;
        private final Counter failureCounter;
        
        public IntegratedSession(String testName, String version) {
            this.testName = testName;
            this.version = version;
            
            // 기존 커스텀 측정
            runGC();
            this.startTime = LocalDateTime.now();
            this.startMemory = getCurrentMemoryMb();
            this.peakMemory = startMemory;
            
            // NEW: Actuator 통합
            if (meterRegistry != null) {
                this.timerSample = Timer.start(meterRegistry);
                this.successCounter = meterRegistry.counter(
                    "performance.test.items",
                    "test", testName,
                    "version", version,
                    "status", "success"
                );
                this.failureCounter = meterRegistry.counter(
                    "performance.test.items",
                    "test", testName,
                    "version", version,
                    "status", "failed"
                );
            } else {
                this.timerSample = null;
                this.successCounter = null;
                this.failureCounter = null;
            }
            
            log.info("🔬 성능 측정 시작 (통합 모드): {} ({})", testName, version);
            log.info("   시작 시간: {}", startTime);
            log.info("   시작 메모리: {} MB", startMemory);
            if (meterRegistry != null) {
                log.info("   ✅ Actuator 통합 활성화");
            }
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
            
            // NEW: Actuator 카운터 증가
            if (successCounter != null && failureCounter != null) {
                successCounter.increment(success);
                failureCounter.increment(failed);
            }
        }
        
        /**
         * 단일 항목 성공 기록
         */
        public void recordSuccess() {
            this.totalItems++;
            this.successItems++;
            updatePeakMemory();
            
            // NEW: Actuator 카운터
            if (successCounter != null) {
                successCounter.increment();
            }
        }
        
        /**
         * 단일 항목 실패 기록
         */
        public void recordFailure() {
            this.totalItems++;
            this.failedItems++;
            updatePeakMemory();
            
            // NEW: Actuator 카운터
            if (failureCounter != null) {
                failureCounter.increment();
            }
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
            runGC();
            
            LocalDateTime endTime = LocalDateTime.now();
            long endMemory = getCurrentMemoryMb();
            
            // NEW: Actuator 타이머 종료
            if (timerSample != null && meterRegistry != null) {
                timerSample.stop(meterRegistry.timer(
                    "performance.test.duration",
                    "test", testName,
                    "version", version
                ));
                
                log.info("   ✅ Actuator 메트릭 기록 완료");
                log.info("      → Prometheus: performance_test_duration_seconds");
                log.info("      → Prometheus: performance_test_items_total");
            }
            
            // 기존 커스텀 메트릭 계산
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
     * 새로운 통합 세션 시작
     */
    public IntegratedSession startSession(String testName, String version) {
        return new IntegratedSession(testName, version);
    }
    
    /**
     * Actuator 사용 가능 여부 확인
     */
    public boolean isActuatorAvailable() {
        return meterRegistry != null;
    }
}


