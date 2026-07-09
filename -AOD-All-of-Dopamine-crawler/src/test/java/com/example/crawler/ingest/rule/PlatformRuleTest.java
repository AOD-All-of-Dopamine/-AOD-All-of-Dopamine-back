package com.example.crawler.ingest.rule;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlatformRuleTest {

    private Map<String, Object> load(String yml) {
        return new Yaml().load(yml);
    }

    @Test
    void parsesFullV4Rule() {
        PlatformRule r = PlatformRule.parse("rules/test.yml", load("""
                platformName: NaverSeries
                domain: WEBNOVEL
                schemaVersion: 4
                mappings:
                  title: master.masterTitle
                  author: domain.author
                  titleId: platform.platformSpecificId
                  rating: attr.rating
                defaults:
                  attr.rating: 0
                normalizers:
                  master.masterTitle: [nfkc, collapse_spaces]
                platformsFrom: [watch_providers]
                """));
        assertEquals("NaverSeries", r.platformName());
        assertEquals("WEBNOVEL", r.domain());
        assertEquals("master.masterTitle", r.mappings().get("title"));
        assertEquals(0, r.defaults().get("attr.rating"));
        assertEquals(List.of("nfkc", "collapse_spaces"), r.normalizers().get("master.masterTitle"));
        assertEquals(List.of("watch_providers"), r.platformsFrom());
    }

    @Test
    void optionalSectionsDefaultToEmpty() {
        PlatformRule r = PlatformRule.parse("rules/min.yml", load("""
                platformName: X
                domain: GAME
                mappings:
                  name: master.masterTitle
                """));
        assertTrue(r.defaults().isEmpty());
        assertTrue(r.normalizers().isEmpty());
        assertTrue(r.platformsFrom().isEmpty());
    }

    @Test
    void rejectsUnknownTopLevelKeyAndBadPrefixAndMissingRequired() {
        assertThrows(IllegalStateException.class, () -> PlatformRule.parse("p", load("""
                platformName: X
                domain: GAME
                fieldMappings: {a: master.masterTitle}
                """)), "구 v3 키(fieldMappings)는 거부되어야 한다");
        assertThrows(IllegalStateException.class, () -> PlatformRule.parse("p", load("""
                platformName: X
                domain: GAME
                mappings: {a: masterTitle}
                """)), "접두사 없는 목적지는 거부");
        assertThrows(IllegalStateException.class, () -> PlatformRule.parse("p", load("""
                domain: GAME
                mappings: {a: master.masterTitle}
                """)), "platformName 누락 거부");
    }
}
