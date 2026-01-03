package com.example.AOD.performance;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 스레드풀 성능 메트릭
 */
@Data
@Builder
public class ThreadPoolMetrics {
    
    // 기본 정보
    private String testName;
    private LocalDateTime timestamp;
    
    // 스레드풀 설정
    private int corePoolSize;
    private int maxPoolSize;
    private int queueCapacity;
    
    // 실행 중 측정값
    private int activeThreadCount;      // 활성 스레드 수
    private int poolSize;                // 현재 풀 크기
    private int queueSize;               // 큐 대기 작업 수
    private long completedTaskCount;     // 완료된 작업 수
    private long totalTaskCount;         // 총 제출된 작업 수
    
    // 리소스
    private long memoryUsageMb;
    private int totalJvmThreads;         // 전체 JVM 스레드 수
    
    // 처리 성능
    private double tasksPerSecond;       // 작업 처리 속도
    private long avgTaskDurationMs;      // 평균 작업 소요 시간
    
    public String toFormattedString() {
        return String.format("""
                
                ═══════════════════════════════════════════════════════
                🧵 스레드풀 메트릭: %s
                ═══════════════════════════════════════════════════════
                시간: %s
                
                📐 스레드풀 설정:
                   - Core Pool Size: %d
                   - Max Pool Size: %d
                   - Queue Capacity: %d
                
                📊 현재 상태:
                   - 활성 스레드: %d / %d
                   - 풀 크기: %d
                   - 큐 대기: %d / %d
                   - 완료된 작업: %,d
                   - 총 작업: %,d
                
                🚀 성능:
                   - 작업 처리 속도: %.2f 작업/초
                   - 평균 작업 시간: %,d ms
                
                💾 리소스:
                   - 메모리 사용: %,d MB
                   - 전체 JVM 스레드: %d
                
                ═══════════════════════════════════════════════════════
                """,
                testName,
                timestamp,
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                activeThreadCount, maxPoolSize,
                poolSize,
                queueSize, queueCapacity,
                completedTaskCount,
                totalTaskCount,
                tasksPerSecond,
                avgTaskDurationMs,
                memoryUsageMb,
                totalJvmThreads
        );
    }
    
    /**
     * CSV 헤더
     */
    public static String csvHeader() {
        return "TestName,Timestamp,CorePoolSize,MaxPoolSize,QueueCapacity," +
               "ActiveThreads,PoolSize,QueueSize,CompletedTasks,TotalTasks," +
               "TasksPerSecond,AvgTaskDurationMs,MemoryUsageMb,TotalJvmThreads";
    }
    
    /**
     * CSV 행
     */
    public String toCsvRow() {
        return String.format("%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%d,%d,%d",
                testName,
                timestamp,
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                activeThreadCount,
                poolSize,
                queueSize,
                completedTaskCount,
                totalTaskCount,
                tasksPerSecond,
                avgTaskDurationMs,
                memoryUsageMb,
                totalJvmThreads
        );
    }
}


