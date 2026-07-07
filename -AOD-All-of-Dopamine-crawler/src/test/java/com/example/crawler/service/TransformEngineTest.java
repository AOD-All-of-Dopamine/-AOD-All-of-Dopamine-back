package com.example.crawler.service;

import com.example.crawler.rules.MappingRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TransformEngine 특성화 테스트.
 * 리팩토링(RuleRegistry/defaults/platformsFrom) 전 현재 동작을 고정하는 안전망 —
 * 이후 각 단계에서 동일 출력이 yml 선언 기반으로 재현되어야 한다.
 */
class TransformEngineTest {

    private final TransformEngine engine = new TransformEngine();

    private MappingRule rule(Map<String, String> fieldMappings) {
        MappingRule r = new MappingRule();
        r.setPlatformName("TestPlatform");
        r.setDomain("WEBNOVEL");
        r.setFieldMappings(fieldMappings);
        return r;
    }

    @Test
    void deepGetSupportsNestedPathsAndBracketIndex() {
        Map<String, Object> raw = Map.of(
                "a", Map.of("b", "v"),
                "list", List.of(Map.of("name", "first"), Map.of("name", "second")));
        assertEquals("v", TransformEngine.deepGet(raw, "a.b"));
        assertEquals("first", TransformEngine.deepGet(raw, "list[0].name"));
        assertNull(TransformEngine.deepGet(raw, "a.missing"));
        assertNull(TransformEngine.deepGet(raw, "list[9].name"));
    }

    @Test
    void transformRoutesToMasterDomainAndPlatformAttributes() {
        var rule = rule(Map.of(
                "title", "master_title",
                "author", "domain.author",
                "prodUrl", "platform.url",
                "rating", "platform.attributes.rating"));
        var out = engine.transform(Map.of(
                "title", "제목", "author", "작가", "prodUrl", "http://u", "rating", 4.5), rule);

        assertEquals("제목", out.master().get("master_title"));
        assertEquals("작가", out.domain().get("author"));
        assertEquals("http://u", out.platform().get("url"));
        assertEquals(4.5, out.platform().attributes().get("rating"));
        assertEquals("TestPlatform", out.platform().get("platformName"));
    }

    @Test
    void missingPlatformAttributeGetsTypeAwareDefault() {
        // 현재 동작: 이름 휴리스틱 (count/runtime→0, cast/crew→[], 그 외 "")
        // RF-3 이후: yml defaults 선언으로 동일 출력이 재현되어야 함
        var rule = rule(Map.of(
                "missingCount", "platform.attributes.comment_count",
                "missingCast", "platform.attributes.cast",
                "missingEtc", "platform.attributes.note"));
        var out = engine.transform(Map.of(), rule);

        assertEquals(0, out.platform().attributes().get("comment_count"));
        assertEquals(List.of(), out.platform().attributes().get("cast"));
        assertEquals("", out.platform().attributes().get("note"));
    }

    @Test
    void missingMasterOrDomainFieldIsSkippedEntirely() {
        var rule = rule(Map.of("missing", "master_title", "missing2", "domain.author"));
        var out = engine.transform(Map.of(), rule);
        assertFalse(out.master().containsKey("master_title"));
        assertFalse(out.domain().containsKey("author"));
    }

    @Test
    void platformsArrayContainsPlatformNameAndWatchProviders() {
        // 현재 동작: watch_providers 특례 하드코딩.
        // RF-4 이후: yml platformsFrom 선언으로 동일 출력이 재현되어야 함
        var rule = rule(Map.of("wp", "platform.attributes.watch_providers"));
        var out = engine.transform(
                Map.of("wp", List.of("Netflix", "Disney Plus")), rule);

        assertEquals(List.of("TestPlatform", "Netflix", "Disney Plus"),
                out.domain().get("platforms"));
        // attributes에도 유지 (상세 페이지 참조용)
        assertEquals(List.of("Netflix", "Disney Plus"),
                out.platform().attributes().get("watch_providers"));
    }

    @Test
    void platformsArrayIsJustPlatformNameWithoutWatchProviders() {
        var out = engine.transform(Map.of(), rule(Map.of()));
        assertEquals(List.of("TestPlatform"), out.domain().get("platforms"));
    }

    @Test
    void normalizersApplyToMasterFields() {
        MappingRule r = rule(Map.of("title", "master_title"));
        var step = new com.example.crawler.rules.NormalizerStep();
        step.setType("strip_parentheses");
        step.setFields(List.of("master_title"));
        var step2 = new com.example.crawler.rules.NormalizerStep();
        step2.setType("collapse_spaces");
        step2.setFields(List.of("master_title"));
        r.setNormalizers(List.of(step, step2));

        var out = engine.transform(Map.of("title", "제목  (개정판)  "), r);
        assertEquals("제목", out.master().get("master_title"));
    }
}
