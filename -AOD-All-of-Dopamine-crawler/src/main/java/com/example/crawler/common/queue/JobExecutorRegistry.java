package com.example.crawler.common.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Job Executor 레지스트리
 * 
 * Spring이 모든 JobExecutor 구현체를 자동 주입하고
 * JobType에 따라 적절한 Executor를 반환합니다.
 */
@Slf4j
@Component
public class JobExecutorRegistry {

    private final Map<JobType, JobExecutor> executors = new HashMap<>();

    /**
     * Spring이 모든 JobExecutor 빈을 주입
     */
    public JobExecutorRegistry(List<JobExecutor> jobExecutors) {
        for (JobExecutor executor : jobExecutors) {
            JobType jobType = executor.getJobType();
            executors.put(jobType, executor);
            log.info("📌 [Registry] JobExecutor 등록: {} -> {} (평균 {}ms, 권장 배치 {}개)",
                    jobType,
                    executor.getClass().getSimpleName(),
                    executor.getAverageExecutionTime(),
                    executor.getRecommendedBatchSize());
        }
    }

    /**
     * JobType에 해당하는 Executor 가져오기
     */
    public JobExecutor getExecutor(JobType jobType) {
        JobExecutor executor = executors.get(jobType);
        if (executor == null) {
            throw new IllegalArgumentException("처리할 수 없는 작업 타입: " + jobType);
        }
        return executor;
    }

    /**
     * 등록된 모든 JobType 가져오기
     */
    public Map<JobType, JobExecutor> getAllExecutors() {
        return new HashMap<>(executors);
    }
}
