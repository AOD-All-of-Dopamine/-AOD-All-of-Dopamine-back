package com.example.crawler.contents.game.steam;

import com.example.crawler.common.queue.CrawlJobProducer;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.game.steam.SteamApiFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Steam 크롤링 스케줄링 서비스
 * 
 * Job Queue 기반으로 작업을 생성합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteamSchedulingService {

    private final SteamApiFetcher steamApiFetcher;
    private final CrawlJobProducer crawlJobProducer;

    /**
     * Steam 게임 목록을 Job Queue에 등록합니다.
     * 
     * 주 1회 실행 (목요일 새벽 3시)
     * 15만개 목록을 가져와 DB에 저장합니다.
     */
    public void collectSteamGamesWeekly() {
        log.info("🎮 [Steam Producer] Steam 게임 목록 수집 시작");
        
        try {
            // 1. Steam API에서 전체 게임 목록 가져오기 (15만개)
            List<Map<String, Object>> gameApps = steamApiFetcher.fetchGameApps();
            
            if (gameApps.isEmpty()) {
                log.warn("⚠️ [Steam Producer] 게임 목록이 비어있습니다.");
                return;
            }

            // 2. appId만 추출
            List<String> appIds = gameApps.stream()
                    .map(app -> String.valueOf(((Number) app.get("appid")).longValue()))
                    .collect(Collectors.toList());

            // 3. Job Queue에 등록 (우선순위: 5 - 보통)
            int created = crawlJobProducer.createJobs(JobType.STEAM_GAME, appIds, 5);
            
            log.info("✅ [Steam Producer] Steam 게임 {} 개 작업 생성 완료", created);
            
        } catch (Exception e) {
            log.error("❌ [Steam Producer] Steam 게임 목록 수집 중 오류 발생", e);
        }
    }
}


