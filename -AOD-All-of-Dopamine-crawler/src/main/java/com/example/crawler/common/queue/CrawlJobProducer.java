package com.example.crawler.common.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 크롤링 작업 생성기 (Producer)
 * 
 * 대량의 크롤링 대상을 큐에 등록하는 역할을 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlJobProducer {

    private final CrawlJobRepository crawlJobRepository;

    /**
     * 크롤링 작업을 배치로 등록합니다.
     * 
     * @param jobType 작업 타입
     * @param targetIds 크롤링 대상 ID 리스트
     * @param priority 우선순위
     * @return 등록된 작업 수
     */
    @Transactional
    public int createJobs(JobType jobType, List<String> targetIds, Integer priority) {
        log.info("🔄 [Producer] {} 작업 생성 시작: {} 개", jobType, targetIds.size());
        
        List<CrawlJob> jobsToCreate = new ArrayList<>();
        int skippedCount = 0;

        for (String targetId : targetIds) {
            // 중복 체크
            if (crawlJobRepository.existsByJobTypeAndTargetId(jobType, targetId)) {
                skippedCount++;
                continue;
            }

            CrawlJob job = CrawlJob.builder()
                    .jobType(jobType)
                    .targetId(targetId)
                    .priority(priority != null ? priority : 5)
                    .build();
            
            jobsToCreate.add(job);
        }

        crawlJobRepository.saveAll(jobsToCreate);
        
        log.info("✅ [Producer] {} 작업 생성 완료: {} 개 등록, {} 개 중복 스킵", 
                jobType, jobsToCreate.size(), skippedCount);
        
        return jobsToCreate.size();
    }

    /**
     * 단일 작업 생성
     */
    @Transactional
    public CrawlJob createJob(JobType jobType, String targetId, Integer priority, String metadata) {
        if (crawlJobRepository.existsByJobTypeAndTargetId(jobType, targetId)) {
            log.debug("⚠️ [Producer] 중복 작업 건너뜀: {} - {}", jobType, targetId);
            return null;
        }

        CrawlJob job = CrawlJob.builder()
                .jobType(jobType)
                .targetId(targetId)
                .priority(priority != null ? priority : 5)
                .metadata(metadata)
                .build();

        return crawlJobRepository.save(job);
    }
}
