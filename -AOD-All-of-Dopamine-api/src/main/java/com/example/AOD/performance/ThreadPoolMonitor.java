package com.example.AOD.performance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 스레드풀 모니터링 유틸리티
 */
@Slf4j
@Component
public class ThreadPoolMonitor {
    
    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    
    /**
     * 현재 JVM의 전체 스레드 수
     */
    public static int getTotalJvmThreadCount() {
        return threadMXBean.getThreadCount();
    }
    
    /**
     * ThreadPoolTaskExecutor의 현재 메트릭 수집
     */
    public static ThreadPoolMetrics captureMetrics(String testName, ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        
        return ThreadPoolMetrics.builder()
                .testName(testName)
                .timestamp(LocalDateTime.now())
                // 설정값
                .corePoolSize(threadPoolExecutor.getCorePoolSize())
                .maxPoolSize(threadPoolExecutor.getMaximumPoolSize())
                .queueCapacity(executor.getQueueCapacity())
                // 현재 상태
                .activeThreadCount(threadPoolExecutor.getActiveCount())
                .poolSize(threadPoolExecutor.getPoolSize())
                .queueSize(threadPoolExecutor.getQueue().size())
                .completedTaskCount(threadPoolExecutor.getCompletedTaskCount())
                .totalTaskCount(threadPoolExecutor.getTaskCount())
                // 리소스
                .memoryUsageMb(PerformanceMonitor.getCurrentMemoryMb())
                .totalJvmThreads(getTotalJvmThreadCount())
                .build();
    }
    
    /**
     * 스레드풀 상태를 주기적으로 로깅
     */
    public static void logThreadPoolStatus(String name, ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        
        log.info("🧵 [{}] 스레드풀 상태:", name);
        log.info("   활성 스레드: {} / {}", 
                threadPoolExecutor.getActiveCount(), 
                threadPoolExecutor.getMaximumPoolSize());
        log.info("   풀 크기: {}", threadPoolExecutor.getPoolSize());
        log.info("   큐 대기: {} / {}", 
                threadPoolExecutor.getQueue().size(), 
                executor.getQueueCapacity());
        log.info("   완료된 작업: {}", threadPoolExecutor.getCompletedTaskCount());
        log.info("   총 JVM 스레드: {}", getTotalJvmThreadCount());
    }
    
    /**
     * 스레드풀 과부하 여부 체크
     */
    public static boolean isOverloaded(ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        
        int activeCount = threadPoolExecutor.getActiveCount();
        int maxPoolSize = threadPoolExecutor.getMaximumPoolSize();
        int queueSize = threadPoolExecutor.getQueue().size();
        int queueCapacity = executor.getQueueCapacity();
        
        // 활성 스레드가 최대치의 90% 이상 + 큐가 50% 이상 찬 경우
        boolean threadOverload = activeCount >= (maxPoolSize * 0.9);
        boolean queueOverload = queueSize >= (queueCapacity * 0.5);
        
        return threadOverload && queueOverload;
    }
    
    /**
     * 스레드풀 활용률 계산 (0.0 ~ 1.0)
     */
    public static double calculateUtilization(ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        
        int activeCount = threadPoolExecutor.getActiveCount();
        int maxPoolSize = threadPoolExecutor.getMaximumPoolSize();
        
        return maxPoolSize > 0 ? (double) activeCount / maxPoolSize : 0.0;
    }
    
    /**
     * 스레드풀 건강도 체크
     */
    public static HealthStatus checkHealth(ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        
        int activeCount = threadPoolExecutor.getActiveCount();
        int maxPoolSize = threadPoolExecutor.getMaximumPoolSize();
        int queueSize = threadPoolExecutor.getQueue().size();
        int queueCapacity = executor.getQueueCapacity();
        
        double threadUtilization = (double) activeCount / maxPoolSize;
        double queueUtilization = (double) queueSize / queueCapacity;
        
        if (threadUtilization >= 0.9 || queueUtilization >= 0.8) {
            return HealthStatus.CRITICAL;  // 위험
        } else if (threadUtilization >= 0.7 || queueUtilization >= 0.5) {
            return HealthStatus.WARNING;   // 경고
        } else {
            return HealthStatus.HEALTHY;   // 정상
        }
    }
    
    public enum HealthStatus {
        HEALTHY("✅ 정상"),
        WARNING("⚠️ 경고"),
        CRITICAL("🔴 위험");
        
        private final String label;
        
        HealthStatus(String label) {
            this.label = label;
        }
        
        public String getLabel() {
            return label;
        }
    }
}


