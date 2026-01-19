package com.example.crawler.common.queue;

/**
 * 크롤링 작업 실행 인터페이스
 * 
 * 각 플랫폼별 크롤러가 이 인터페이스를 구현하여
 * Consumer의 하드코딩을 제거합니다.
 */
public interface JobExecutor {
    
    /**
     * 지원하는 작업 타입 반환
     */
    JobType getJobType();
    
    /**
     * 작업 실행
     * 
     * @param targetId 크롤링할 대상의 ID (appId, movieId, titleId 등)
     * @return 성공 여부
     */
    boolean execute(String targetId);
    
    /**
     * 이 작업의 평균 처리 시간 (밀리초)
     * Consumer가 동적으로 배치 크기를 조정하는데 사용
     * 
     * @return 평균 처리 시간 (ms)
     */
    default long getAverageExecutionTime() {
        return 1000; // 기본 1초
    }
    
    /**
     * 5초 배치에서 처리 가능한 최대 작업 수 계산
     */
    default int getRecommendedBatchSize() {
        long avgTime = getAverageExecutionTime();
        if (avgTime <= 0) return 1;
        
        // 5000ms / 평균 처리 시간 (최소 1개, 최대 20개)
        int batchSize = (int) (5000 / avgTime);
        return Math.max(1, Math.min(20, batchSize));
    }
}
