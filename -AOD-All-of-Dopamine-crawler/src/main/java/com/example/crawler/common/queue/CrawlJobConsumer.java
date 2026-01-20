package com.example.crawler.common.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 크롤링 작업 소비자 (Consumer)
 * 
 * 큐에서 작업을 가져와 실제 크롤링을 수행합니다.
 * 플랫폼별 처리 속도에 따라 동적으로 배치 크기를 조정합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlJobConsumer {

    private final CrawlJobRepository crawlJobRepository;
    private final JobExecutorRegistry executorRegistry;
    
    // 🚀 리소스 제어: EC2 t3.small 안전 한계
    private static final int MAX_CONCURRENT_JOBS = 10;  // 전역 최대 동시 처리
    private static final int MAX_SELENIUM_JOBS = 2;     // Selenium 최대 동시 처리
    
    // 현재 처리 중인 Job 수 추적
    private final java.util.concurrent.atomic.AtomicInteger currentProcessing = 
        new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger seleniumProcessing = 
        new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * 주기적으로 큐에서 작업을 동적으로 가져와 처리합니다.
     * 
     * fixedDelay: 이전 작업이 끝나고 5초 후 다시 실행
     * 플랫폼별 처리 속도에 따라 자동으로 배치 크기 조정
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 3000)  // 🚀 5초 → 10초 (EC2 t3.small 최적화)
    @Transactional
    public void processBatchBalanced() {
        log.debug("🔍 [Consumer] 배치 처리 시작 - 큐에서 작업 조회 중...");
        try {
            Map<JobType, Integer> processedCounts = new HashMap<>();
            
            // 등록된 모든 Executor에 대해 동적으로 처리
            for (Map.Entry<JobType, JobExecutor> entry : executorRegistry.getAllExecutors().entrySet()) {
                JobType jobType = entry.getKey();
                JobExecutor executor = entry.getValue();
                
                // Executor가 권장하는 배치 크기로 처리
                int batchSize = executor.getRecommendedBatchSize();
                int processed = processByType(jobType, executor, batchSize);
                
                if (processed > 0) {
                    processedCounts.put(jobType, processed);
                }
            }

            if (!processedCounts.isEmpty()) {
                log.info("📦 [Consumer] 배치 처리 완료 - {}", formatProcessedCounts(processedCounts));
            } else {
                log.debug("⏸️ [Consumer] 처리할 작업 없음 - 큐가 비어있습니다");
            }

        } catch (Exception e) {
            log.error("❌ [Consumer] 배치 처리 중 오류 발생", e);
        }
    }

    /**
     * 특정 타입의 작업을 지정된 개수만큼 처리
     */
    private int processByType(JobType jobType, JobExecutor executor, int batchSize) {
        // 🚀 Phase 1: 전역 동시 처리 제한 체크
        int currentJobs = currentProcessing.get();
        if (currentJobs >= MAX_CONCURRENT_JOBS) {
            log.warn("🚫 [Consumer] 전역 동시 처리 한계 도달 ({}/{}), {} 작업 대기",
                currentJobs, MAX_CONCURRENT_JOBS, jobType);
            return 0;
        }
        
        // 🚀 Phase 1: Selenium Job 특별 제한 체크
        boolean isSeleniumJob = isSeleniumJob(jobType);
        if (isSeleniumJob && seleniumProcessing.get() >= MAX_SELENIUM_JOBS) {
            log.warn("🚫 [Consumer] Selenium Job 한계 도달 ({}/{}), {} 작업 대기",
                seleniumProcessing.get(), MAX_SELENIUM_JOBS, jobType);
            return 0;
        }
        
        // 처리 가능한 Job 수 계산
        int available = MAX_CONCURRENT_JOBS - currentJobs;
        int effectiveBatchSize = Math.min(batchSize, available);
        
        // Selenium Job인 경우 추가 제한
        if (isSeleniumJob) {
            int seleniumAvailable = MAX_SELENIUM_JOBS - seleniumProcessing.get();
            effectiveBatchSize = Math.min(effectiveBatchSize, seleniumAvailable);
        }
        
        List<CrawlJob> jobs = crawlJobRepository.findPendingJobsByTypeWithLock(jobType, effectiveBatchSize);
        
        if (jobs.isEmpty()) {
            return 0;
        }
        
        log.info("🎯 [Consumer] {} 작업 {}개 처리 시작 (권장: {}, 제한 적용: {}, 평균 {}ms)",
                jobType, jobs.size(), batchSize, effectiveBatchSize, executor.getAverageExecutionTime());
        
        int processed = 0;
        for (CrawlJob job : jobs) {
            // 처리 시작 전 카운터 증가
            currentProcessing.incrementAndGet();
            if (isSeleniumJob) {
                seleniumProcessing.incrementAndGet();
            }
            
            try {
                processJob(job, executor);
                processed++;
            } finally {
                // 처리 완료 후 카운터 감소
                currentProcessing.decrementAndGet();
                if (isSeleniumJob) {
                    seleniumProcessing.decrementAndGet();
                }
            }
        }
        
        return processed;
    }
    
    /**
     * Selenium을 사용하는 Job인지 확인
     */
    private boolean isSeleniumJob(JobType jobType) {
        return jobType == JobType.NAVER_WEBTOON;
    }

    /**
     * 개별 작업 처리 (Executor 위임)
     */
    private void processJob(CrawlJob job, JobExecutor executor) {
        job.markAsProcessing();

        try {
            boolean success = executor.execute(job.getTargetId());

            if (success) {
                job.markAsCompleted();
                log.debug("✅ [Consumer] 작업 성공: {} - {}", job.getJobType(), job.getTargetId());
            } else {
                job.markAsFailed("크롤링 실패 (상세 정보 없음)");
                log.warn("❌ [Consumer] 작업 실패: {} - {}", job.getJobType(), job.getTargetId());
            }

        } catch (Exception e) {
            job.markAsFailed(e.getMessage());
            log.error("❌ [Consumer] 작업 처리 중 예외 발생: {} - {}",
                    job.getJobType(), job.getTargetId(), e);
        }
    }

    /**
     * 처리된 작업 수를 보기 좋게 포맷팅
     */
    private String formatProcessedCounts(Map<JobType, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        counts.forEach((type, count) -> sb.append(type).append(":").append(count).append(", "));
        if (sb.length() > 2) sb.setLength(sb.length() - 2); // 마지막 ", " 제거
        return sb.toString();
    }

    /**
     * 재시도 가능한 작업들을 다시 PENDING 상태로 변경
     */
    @Scheduled(cron = "0 0 * * * *") // 1시간마다
    @Transactional
    public void retryFailedJobs() {
        // TODO: RETRY 상태인 작업들을 PENDING으로 변경
        log.debug("🔄 재시도 작업 처리 스케줄 실행");
    }
}
