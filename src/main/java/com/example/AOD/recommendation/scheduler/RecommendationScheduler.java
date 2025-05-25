package com.example.AOD.recommendation.scheduler;

import com.example.AOD.recommendation.service.TraditionalRecommendationService;
import com.example.AOD.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.cache.CacheManager;

@Component
public class RecommendationScheduler {

    @Autowired
    private TraditionalRecommendationService recommendationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    // 매일 새벽 3시에 추천 캐시 갱신
    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshRecommendationCache() {
        try {
            // 기존 캐시 클리어
            cacheManager.getCache("traditional-recommendations").clear();
            cacheManager.getCache("llm-recommendations").clear();

            System.out.println("추천 시스템 캐시가 갱신되었습니다.");
        } catch (Exception e) {
            System.err.println("추천 캐시 갱신 중 오류: " + e.getMessage());
        }
    }

    // 매주 일요일에 추천 시스템 성능 분석
    @Scheduled(cron = "0 0 2 * * SUN")
    public void analyzeRecommendationPerformance() {
        // 추천 시스템 성능 분석 로직
        System.out.println("추천 시스템 성능 분석을 시작합니다.");
    }
}