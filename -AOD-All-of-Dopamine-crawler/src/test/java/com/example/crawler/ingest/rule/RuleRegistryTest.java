package com.example.crawler.ingest.rule;

import com.example.crawler.ingest.DomainCatalog;
import com.example.shared.repository.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RuleRegistryTest {

    private DomainCatalog catalog() {
        return new DomainCatalog(
                mock(MovieContentRepository.class), mock(TvContentRepository.class),
                mock(GameContentRepository.class), mock(WebtoonContentRepository.class),
                mock(WebnovelContentRepository.class));
    }

    @Test
    void loadsAndResolvesValidRules() {
        RuleRegistry reg = new RuleRegistry("classpath*:rules_v4_good/*.yml", catalog());
        PlatformRule r = reg.resolve("WEBNOVEL", "NaverSeries");
        assertEquals("NaverSeries", r.platformName());
        assertTrue(reg.pathOf("NaverSeries").endsWith("naverseries.yml"));
    }

    @Test
    void resolveRejectsUnknownPlatformAndDomainMismatch() {
        RuleRegistry reg = new RuleRegistry("classpath*:rules_v4_good/*.yml", catalog());
        assertThrows(IllegalArgumentException.class, () -> reg.resolve("WEBNOVEL", "NoSuch"));
        assertThrows(IllegalArgumentException.class, () -> reg.resolve("GAME", "NaverSeries"));
    }

    @Test
    void bootFailsOnTypoDestinationProperty() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new RuleRegistry("classpath*:rules_v4_bad/*.yml", catalog()));
        assertTrue(e.getMessage().contains("wrtier"), "오타 프로퍼티명이 에러 메시지에 있어야: " + e.getMessage());
        assertTrue(e.getMessage().contains("badprop.yml"), "실패한 파일 경로가 에러 메시지에 있어야: " + e.getMessage());
    }

    @Test
    void bootFailsWhenNoRulesFound() {
        assertThrows(IllegalStateException.class,
                () -> new RuleRegistry("classpath*:rules_v4_none/*.yml", catalog()));
    }

    @Test
    void bootFailsOnDuplicatePlatformName() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new RuleRegistry("classpath*:rules_v4_dup/*.yml", catalog()));
        assertTrue(e.getMessage().contains("중복") && e.getMessage().contains("Dup"),
                "중복 platformName과 원인이 메시지에: " + e.getMessage());
    }

    @Test
    void bootFailsOnUnknownNormalizerStep() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new RuleRegistry("classpath*:rules_v4_badstep/*.yml", catalog()));
        assertTrue(e.getMessage().contains("nfck") && e.getMessage().contains("badstep.yml"),
                "미지 스텝명과 파일 경로가 메시지에: " + e.getMessage());
    }

    @Test
    void bootFailsOnNonStringNormalizerTarget() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new RuleRegistry("classpath*:rules_v4_badnormtype/*.yml", catalog()));
        assertTrue(e.getMessage().contains("releaseDate") && e.getMessage().contains("String"),
                "비문자열 타깃과 요구타입이 메시지에: " + e.getMessage());
    }
}
