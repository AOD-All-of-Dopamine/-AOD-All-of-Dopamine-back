package com.example.crawler.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Transform 정기 스케줄러
 * - 크롤링된 raw_items를 정기적으로 변환하여 contents로 upsert
 * - 처리되지 않은 데이터를 배치로 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransformSchedulingService {

    private final IngestPipeline ingestPipeline;

    /**
     * 매일 새벽 6시에 미처리 raw_items 배치 변환
     * - 모든 크롤러의 크롤링이 완료된 후 실행
     * - 배치 크기: 100개씩 처리
     */
    @Scheduled(cron = "0 0 6 * * *") // 매일 새벽 6시
    public void transformRawItemsDaily() {
        log.info("🚀 [정기 스케줄] raw_items 배치 변환 시작");
        
        try {
            int batchSize = 100;
            int totalProcessed = 0;
            int processed;
            
            // 미처리 데이터가 완전히 없을 때까지 반복 처리
            do {
                processed = ingestPipeline.processBatch(batchSize);
                totalProcessed += processed;

                if (processed > 0) {
                    log.info("📦 배치 처리 완료: {}개 (누적: {}개)", processed, totalProcessed);
                    // 다음 배치 처리 전 잠시 대기 (DB 부하 완화)
                    Thread.sleep(1000); // 1초 대기
                }
            } while (processed > 0);
            
            log.info("✅ [정기 스케줄] raw_items 배치 변환 완료: 총 {}개 처리", totalProcessed);
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] raw_items 배치 변환 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매주 일요일 새벽 7시에 대규모 배치 변환
     * - 주간 누적된 미처리 데이터 일괄 처리
     * - 배치 크기: 200개씩 처리 (대량 처리)
     */
    @Scheduled(cron = "0 0 7 * * SUN") // 매주 일요일 새벽 7시
    public void transformRawItemsWeekly() {
        log.info("🚀 [정기 스케줄] raw_items 주간 대규모 배치 변환 시작");
        
        try {
            int batchSize = 200;
            int totalProcessed = 0;
            int processed;
            
            // 미처리 데이터가 완전히 없을 때까지 반복 처리
            do {
                processed = ingestPipeline.processBatch(batchSize);
                totalProcessed += processed;

                if (processed > 0) {
                    log.info("📦 대규모 배치 처리 완료: {}개 (누적: {}개)", processed, totalProcessed);
                    // 다음 배치 처리 전 잠시 대기 (DB 부하 완화)
                    Thread.sleep(500); // 0.5초 대기
                }
            } while (processed > 0);
            
            log.info("✅ [정기 스케줄] raw_items 주간 대규모 배치 변환 완료: 총 {}개 처리", totalProcessed);
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] raw_items 주간 대규모 배치 변환 실패: {}", e.getMessage(), e);
        }
    }
}


