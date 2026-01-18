package com.example.crawler.common.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;

/**
 * 크롤링 작업 큐 레포지토리
 */
@Repository
public interface CrawlJobRepository extends JpaRepository<CrawlJob, Long> {

    /**
     * 대기중인 작업을 우선순위 순으로 가져옵니다.
     * 
     * SKIP LOCKED: 다른 트랜잭션이 이미 처리 중인 행은 건너뜁니다.
     * 이를 통해 멀티 인스턴스 환경에서도 동일 작업이 중복 처리되지 않습니다.
     * 
     * @param limit 가져올 작업 수
     * @return 대기중인 작업 리스트
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
        SELECT j FROM CrawlJob j 
        WHERE j.status IN ('PENDING', 'RETRY') 
        ORDER BY j.priority ASC, j.createdAt ASC 
        LIMIT :limit
        """)
    List<CrawlJob> findPendingJobsWithLock(@Param("limit") int limit);

    /**
     * 특정 타입의 대기중인 작업을 우선순위 순으로 가져옵니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
        SELECT j FROM CrawlJob j 
        WHERE j.jobType = :jobType 
        AND j.status IN ('PENDING', 'RETRY') 
        ORDER BY j.priority ASC, j.createdAt ASC 
        LIMIT :limit
        """)
    List<CrawlJob> findPendingJobsByTypeWithLock(@Param("jobType") JobType jobType, @Param("limit") int limit);

    /**
     * 특정 타입의 대기중인 작업 수 조회
     */
    long countByJobTypeAndStatus(JobType jobType, JobStatus status);

    /**
     * 특정 타입과 타겟 ID로 작업 존재 여부 확인 (중복 방지)
     */
    boolean existsByJobTypeAndTargetId(JobType jobType, String targetId);

    /**
     * 상태별 작업 수 통계
     */
    @Query("SELECT j.status, COUNT(j) FROM CrawlJob j GROUP BY j.status")
    List<Object[]> getStatusStatistics();
}
