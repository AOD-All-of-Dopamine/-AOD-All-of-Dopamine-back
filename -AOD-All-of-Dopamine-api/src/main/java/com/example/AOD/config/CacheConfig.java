package com.example.AOD.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 목록의 단일 출처.
 *
 * 기존에는 gitignore 대상인 application.properties의 spring.cache.cache-names(고정 목록)에
 * 의존했는데, 목록이 로컬별 파일에 있어 @Cacheable 캐시 추가 시 등록 누락이 조용히 발생했다.
 * (2026-07 genres 승격 때 추가된 availableGenres/genresWithCount 미등록 →
 * "Cannot find cache named ..." 500 — 2026-08-14 발견)
 *
 * 이 빈이 있으면 Spring Boot 캐시 자동 설정(spring.cache.*)은 물러나므로,
 * 새 @Cacheable 캐시는 반드시 여기에 등록한다. 미등록 캐시는 기동 후 첫 호출에서
 * IllegalArgumentException으로 드러난다 (오타·누락을 런타임 초기에 감지하는 안전판).
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "traditional-recommendations",
                "llm-recommendations",
                "availableGenres",
                "genresWithCount",
                "availablePlatforms");
    }
}
