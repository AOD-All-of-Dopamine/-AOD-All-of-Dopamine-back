package com.example.crawler.common.queue;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 범용 크롤링 작업 큐
 * 
 * 모든 크롤링 작업(Steam, TMDB, 웹툰, 웹소설 등)을 통합 관리합니다.
 * Producer-Consumer 패턴의 핵심 엔티티입니다.
 */
@Entity
@Table(
    name = "crawl_job_queue",
    indexes = {
        @Index(name = "idx_job_status_priority", columnList = "status, priority, createdAt"),
        @Index(name = "idx_job_type_status", columnList = "jobType, status")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 작업 타입 (STEAM_GAME, TMDB_MOVIE, NAVER_WEBTOON, NAVER_SERIES 등)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobType jobType;

    /**
     * 크롤링 대상 ID (appId, movieId, webtoonId 등)
     */
    @Column(nullable = false, length = 100)
    private String targetId;

    /**
     * 추가 메타데이터 (JSON 형태로 저장 가능)
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * 작업 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    /**
     * 우선순위 (낮을수록 먼저 처리, 기본값 5)
     */
    @Builder.Default
    private Integer priority = 5;

    /**
     * 재시도 횟수
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 최대 재시도 횟수
     */
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * 에러 메시지
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 작업 생성 시간
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 마지막 업데이트 시간
     */
    private LocalDateTime updatedAt;

    /**
     * 작업 시작 시간
     */
    private LocalDateTime startedAt;

    /**
     * 작업 완료 시간
     */
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 작업 시작 처리
     */
    public void markAsProcessing() {
        this.status = JobStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 작업 성공 처리
     */
    public void markAsCompleted() {
        this.status = JobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 작업 실패 처리 (재시도 가능 여부 판단)
     */
    public void markAsFailed(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;
        
        if (this.retryCount >= this.maxRetries) {
            this.status = JobStatus.FAILED;
        } else {
            this.status = JobStatus.RETRY;
        }
    }

    /**
     * 재시도 가능 여부
     */
    public boolean canRetry() {
        return this.retryCount < this.maxRetries && this.status == JobStatus.RETRY;
    }
}
