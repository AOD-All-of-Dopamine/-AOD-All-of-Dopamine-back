package com.example.crawler.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleRegistry: classpath:rules/**\/*.yml 자동 스캔 → (platformName) 인덱싱.
 * "새 플랫폼 추가 = yml 파일 1개 추가"의 진실 공급원. 자바 switch 불필요.
 */
class RuleRegistryTest {

    private final RuleRegistry registry = new RuleRegistry(new RuleLoader());

    @Test
    void discoversAllRuleYmlsOnClasspath() {
        // 현재 리소스 7개 (game/epic, game/steam, movie/tmdb_movie, tv/tmdb_tv,
        // webnovel/kakaopage, webnovel/naverseries, webtoon/naverwebtoon)
        assertTrue(registry.size() >= 7, "rules/**/*.yml 전부 발견해야 함, got " + registry.size());
    }

    @Test
    void looksUpRuleByPlatformName() {
        var rule = registry.byPlatform("NaverSeries");
        assertTrue(rule.isPresent());
        assertEquals("WEBNOVEL", rule.get().getDomain());

        var steam = registry.byPlatform("Steam");
        assertTrue(steam.isPresent());
        assertEquals("GAME", steam.get().getDomain());

        var tmdbMovie = registry.byPlatform("TMDB_MOVIE");
        assertTrue(tmdbMovie.isPresent());
        assertEquals("MOVIE", tmdbMovie.get().getDomain());
    }

    @Test
    void unknownPlatformReturnsEmpty() {
        assertTrue(registry.byPlatform("NoSuchPlatform").isEmpty());
    }

    @Test
    void resolveChecksDomainConsistency() {
        // 기존 switch의 (domain, platform) 쌍 매칭 의미 보존
        assertEquals("WEBNOVEL", registry.resolve("WEBNOVEL", "NaverSeries").getDomain());
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("MOVIE", "NaverSeries")); // 도메인 불일치
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("GAME", "NoSuchPlatform")); // 미지 플랫폼
    }

    @Test
    void pathOfExposesSourceYmlForTransformRunTracking() {
        assertEquals("rules/webnovel/naverseries.yml", registry.pathOf("NaverSeries"));
        assertNull(registry.pathOf("NoSuchPlatform"));
    }

    @Test
    void everyDiscoveredRuleHasPlatformNameAndDomain() {
        // yml 무결성: 인덱스 키가 되는 두 필드는 모든 룰에 필수
        registry.all().forEach(r -> {
            assertNotNull(r.getPlatformName(), "platformName 누락된 룰 존재");
            assertNotNull(r.getDomain(), "domain 누락: " + r.getPlatformName());
        });
    }
}
