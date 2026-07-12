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

    private IllegalStateException parseError(String yml) {
        return assertThrows(IllegalStateException.class, () -> PlatformRule.parse("p", load(yml)));
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
    void optionalSectionsDefaultToEmptyAndSchemaVersionTo4() {
        PlatformRule r = PlatformRule.parse("rules/min.yml", load("""
                platformName: X
                domain: GAME
                mappings:
                  name: master.masterTitle
                """));
        assertTrue(r.defaults().isEmpty());
        assertTrue(r.normalizers().isEmpty());
        assertTrue(r.platformsFrom().isEmpty());
        assertEquals(4, r.schemaVersion());
    }

    @Test
    void rejectsUnknownTopLevelKeyAndBadPrefixAndMissingRequired() {
        IllegalStateException legacy = parseError("""
                platformName: X
                domain: GAME
                fieldMappings: {a: master.masterTitle}
                """);
        assertTrue(legacy.getMessage().contains("p") && legacy.getMessage().contains("fieldMappings"),
                "구 v3 키 거부 + 경로/키가 메시지에: " + legacy.getMessage());

        IllegalStateException prefix = parseError("""
                platformName: X
                domain: GAME
                mappings: {a: masterTitle}
                """);
        assertTrue(prefix.getMessage().contains("p") && prefix.getMessage().contains("masterTitle"),
                "접두사 오류 + 경로/목적지가 메시지에: " + prefix.getMessage());

        IllegalStateException required = parseError("""
                domain: GAME
                mappings: {a: master.masterTitle}
                """);
        assertTrue(required.getMessage().contains("p") && required.getMessage().contains("platformName"),
                "필수키 누락 + 경로가 메시지에: " + required.getMessage());
    }

    @Test
    void rejectsBadDefaultsKeyAndMalformedShapesWithPathInMessage() {
        assertTrue(parseError("""
                platformName: X
                domain: GAME
                defaults: {rating: 0}
                """).getMessage().contains("rating"), "defaults 키도 접두사 검증");

        assertTrue(parseError("""
                platformName: X
                domain: GAME
                mappings: [a, b]
                """).getMessage().contains("mappings"), "mappings가 맵이 아니면 경로 포함 ISE");

        assertTrue(parseError("""
                platformName: X
                domain: GAME
                mappings: {a: }
                """).getMessage().contains("mappings.a"), "null 목적지도 경로 포함 ISE");

        assertTrue(parseError("""
                platformName: X
                domain: GAME
                normalizers: {master.masterTitle: nfkc}
                """).getMessage().contains("master.masterTitle"), "normalizer 스칼라 값 거부");

        assertTrue(parseError("""
                platformName: X
                domain: GAME
                platformsFrom: watch_providers
                """).getMessage().contains("platformsFrom"), "platformsFrom 스칼라 거부");

        assertTrue(assertThrows(IllegalStateException.class, () -> PlatformRule.parse("p", null))
                .getMessage().contains("p"), "빈 yml도 경로 포함 ISE");

        assertTrue(parseError("""
                platformName: X
                domain: GAME
                schemaVersion: four
                """).getMessage().contains("schemaVersion"), "비숫자 schemaVersion 거부");
    }

    @Test
    void rejectsNonMasterNormalizerKey() {
        assertTrue(parseError("""
                platformName: X
                domain: GAME
                normalizers:
                  platform.url: [nfkc]
                """).getMessage().contains("platform.url"), "normalizer 키는 master.*만 허용");
    }
}
