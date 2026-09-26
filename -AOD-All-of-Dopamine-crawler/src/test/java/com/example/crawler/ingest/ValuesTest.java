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
    void strIsNullSafe() {
        assertNull(Values.str(null));
        assertEquals("42", Values.str(42));
    }

    @Test
    void everyDeclaredNormalizerStepIsExecutable() {
        for (String step : Values.NORMALIZERS)
            assertDoesNotThrow(() -> Values.normalize("x (y) [z]", List.of(step)),
                    "NORMALIZERS 집합과 normalize switch가 어긋남: " + step);
    }

    @Test
    void normalizeAppliesStepsInOrder() {
        assertEquals("제목", Values.normalize("제목  (개정판)  ", List.of("strip_parentheses", "collapse_spaces")));
        // [독점]·[2부] 같은 대괄호 태그와 시즌/외전 표기는 다른 시즌을 구분하는 정보 — 정제하지 않고 보존 (2026-09)
        assertEquals("[독점] 전지적 독자 시점 외전", Values.normalize("[독점]  전지적 독자 시점 외전 ", List.of("nfkc", "collapse_spaces")));
        assertThrows(IllegalArgumentException.class, () -> Values.normalize("x", List.of("no_such_step")));
    }

    @Test
    void bracketAndSeriesStrippersAreRemovedFromVocabulary() {
        // 대괄호 태그·시즌 접미 제거는 다른 시즌을 같은 작품으로 병합시키던 원인 — yml에서 선언 자체가 불가해야 한다
        assertFalse(Values.NORMALIZERS.contains("strip_brackets"));
        assertFalse(Values.NORMALIZERS.contains("strip_series_qualifiers"));
        assertThrows(IllegalArgumentException.class, () -> Values.normalize("[독점] x", List.of("strip_brackets")));
        assertThrows(IllegalArgumentException.class, () -> Values.normalize("x 외전", List.of("strip_series_qualifiers")));
    }

    @Test
    void sameTitleNormalizesThenComparesExactly() {
        assertTrue(Values.sameTitle("전지적 독자 시점", "전지적  독자-시점!"));
        assertFalse(Values.sameTitle("전지적 독자 시점", "전지적 독자 시점 2"));
        assertFalse(Values.sameTitle(null, "x"));
    }

    @Test
    void sameTitleKeepsDifferentSeasonsApart() {
        // 대괄호 문자는 지워도 안의 글자는 남으므로 [2부]/외전이 붙은 제목은 원작과 병합되지 않는다
        assertFalse(Values.sameTitle("화산귀환", "화산귀환 [2부]"));
        assertFalse(Values.sameTitle("전지적 독자 시점", "전지적 독자 시점 외전"));
        assertFalse(Values.sameTitle("화산귀환", "[독점] 화산귀환"));
    }
}
