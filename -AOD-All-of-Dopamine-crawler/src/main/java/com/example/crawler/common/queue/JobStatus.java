package com.example.crawler.common.queue;

/**
 * 작업 상태
 */
public enum JobStatus {
    /**
     * 대기 중 (아직 처리되지 않음)
     */
    PENDING,
    
    /**
     * 처리 중 (현재 작업 실행 중)
     */
    PROCESSING,
    
    /**
     * 완료 (성공적으로 처리됨)
     */
    COMPLETED,
    
    /**
     * 재시도 대기 (실패했지만 재시도 가능)
     */
    RETRY,
    
    /**
     * 최종 실패 (재시도 횟수 초과)
     */
    FAILED,
    
    /**
     * 건너뜀 (처리하지 않기로 결정)
     */
    SKIPPED
}
