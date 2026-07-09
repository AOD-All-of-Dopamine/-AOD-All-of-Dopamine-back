package com.example.crawler.ingest;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValuesTest {

    @Test
    void deepGetSupportsNestedPathsAndBracketIndex() {
        Map<String, Object> raw = Map.of(
                "a", Map.of("b", "v"),
                "list", List.of(Map.of("name", "first"), Map.of("name", "second")));
        assertEquals("v", Values.deepGet(raw, "a.b"));
        assertEquals("first", Values.deepGet(raw, "list[0].name"));
        assertNull(Values.deepGet(raw, "a.missing"));
        assertNull(Values.deepGet(raw, "list[9].name"));
    }

    @Test
    void convertHandlesCoreTypes() {
        assertEquals("4.5", Values.convert(4.5, String.class));
        assertEquals(3, Values.convert("3", Integer.class));
        assertEquals(45, Values.convert(List.of(45, 60), Integer.class)); // TMDB episode_run_time 배열 → 첫 값
        assertNull(Values.convert(List.of(), Integer.class));
        assertEquals(LocalDate.of(2018, 5, 14), Values.convert("2018-05-14", LocalDate.class));
        assertEquals(List.of("판타지"), Values.convert(List.of("판타지"), List.class));
        assertEquals(List.of("단일값"), Values.convert("단일값", List.class)); // 비리스트 → 단일 원소 리스트 (구 동작)
        assertEquals("이름", Values.convert(Map.of("name", "이름"), String.class)); // {name:...} → name (구 동작)
        Map<String, Object> os = Map.of("windows", true);
        assertEquals(os, Values.convert(os, Map.class)); // jsonb 통과
        assertNull(Values.convert(null, String.class));
    }

    @Test
    void normalizeAppliesStepsInOrder() {
        assertEquals("제목", Values.normalize("제목  (개정판)  ", List.of("strip_parentheses", "collapse_spaces")));
        assertEquals("전지적 독자 시점", Values.normalize("[신작] 전지적 독자 시점 외전", List.of("strip_brackets", "strip_series_qualifiers", "collapse_spaces")));
        assertThrows(IllegalArgumentException.class, () -> Values.normalize("x", List.of("no_such_step")));
    }

    @Test
    void sameTitleNormalizesThenComparesExactly() {
        assertTrue(Values.sameTitle("전지적 독자 시점", "전지적  독자-시점!"));
        assertFalse(Values.sameTitle("전지적 독자 시점", "전지적 독자 시점 2"));
        assertFalse(Values.sameTitle(null, "x"));
    }
}
