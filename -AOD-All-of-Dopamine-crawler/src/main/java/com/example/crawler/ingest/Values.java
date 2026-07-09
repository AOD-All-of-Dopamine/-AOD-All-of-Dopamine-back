package com.example.crawler.ingest;

import com.example.crawler.util.FlexibleDateParser;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ingest 전용 값 유틸 — 전부 static 순수 함수.
 * payload 접근(deepGet) · 타입 변환(convert) · 제목 정규화(normalize) · 병합용 제목 비교(sameTitle).
 * (구 TransformEngine.deepGet + GenericDomainUpserter.convertType + ContentSimilarityService 흡수)
 */
public final class Values {

    private Values() {}

    /** "a.b[0].c" 경로로 중첩 Map/List에서 값 추출. 없으면 null. */
    public static Object deepGet(Object obj, String path) {
        if (obj == null || path == null) return null;
        Object cur = obj;
        for (String rawPart : path.split("\\.")) {
            String part = rawPart;
            Integer idx = null;
            if (part.contains("[") && part.endsWith("]")) {
                int i = part.indexOf('[');
                try { idx = Integer.parseInt(part.substring(i + 1, part.length() - 1)); } catch (NumberFormatException ignored) {}
                part = part.substring(0, i);
            }
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = m.get(part);
            if (idx != null) {
                if (!(cur instanceof List<?> list) || idx < 0 || idx >= list.size()) return null;
                cur = list.get(idx);
            }
        }
        return cur;
    }

    /** null-안전 String.valueOf — psid/url 문자열화용 (IngestPipeline). */
    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 목적지 프로퍼티 타입에 맞춰 변환. 모르는 타입은 원본 유지(리플렉션 바인딩이 실패 시 예외). */
    public static Object convert(Object value, Class<?> targetType) {
        if (value == null || targetType == null) return value;
        if (targetType != String.class && targetType.isInstance(value)) return value;
        if (targetType == String.class) return stringify(value);
        if (targetType == Integer.class || targetType == int.class) return toInteger(value);
        if (targetType == Long.class || targetType == long.class)
            return (value instanceof Number n) ? n.longValue() : Long.parseLong(value.toString());
        if (targetType == Double.class || targetType == double.class)
            return (value instanceof Number n) ? n.doubleValue() : Double.parseDouble(value.toString());
        if (targetType == LocalDate.class) return FlexibleDateParser.parse(value);
        if (List.class.isAssignableFrom(targetType)) return (value instanceof List<?> l) ? l : List.of(value);
        return value;
    }

    /** 구 GenericDomainUpserter 호환: Map에 name 키가 있으면 name 값 사용. */
    private static String stringify(Object v) {
        if (v instanceof Map<?, ?> m && m.containsKey("name")) return String.valueOf(m.get("name"));
        return String.valueOf(v);
    }

    /** 배열이면 첫 값 사용 (TMDB episode_run_time). 파싱 불가 → null (구 동작). */
    private static Integer toInteger(Object v) {
        if (v instanceof List<?> l) {
            if (l.isEmpty()) return null;
            v = l.get(0);
        }
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    /** yml normalizers 어휘 (RuleRegistry 기동검증이 참조). */
    public static final Set<String> NORMALIZERS = Set.of(
            "lowercase", "strip_parentheses", "collapse_spaces", "nfkc", "strip_brackets", "strip_series_qualifiers");

    /** normalizer 스텝들을 순서대로 적용 (구 TransformEngine.applyNormalizers 이동). */
    public static String normalize(String value, List<String> steps) {
        String s = value;
        for (String step : steps) {
            s = switch (step) {
                case "lowercase" -> s.toLowerCase();
                case "strip_parentheses" -> s.replaceAll("\\([^)]*\\)", "");
                case "collapse_spaces" -> s.replaceAll("\\s+", " ").trim();
                case "nfkc" -> Normalizer.normalize(s, Normalizer.Form.NFKC);
                case "strip_brackets" -> s.replaceAll("\\[[^\\]]*\\]", "");
                case "strip_series_qualifiers" -> s.replaceAll("(시즌\\s*\\d+|외전|스페셜)$", "").trim();
                default -> throw new IllegalArgumentException("unknown normalizer: " + step);
            };
        }
        return s;
    }

    /** 병합용 제목 동일성: 소문자화+공백/구두점 제거 후 정확 일치 (구 ContentSimilarityService 흡수). */
    public static boolean sameTitle(String a, String b) {
        return a != null && b != null && titleKey(a).equals(titleKey(b));
    }

    private static String titleKey(String t) {
        return t.toLowerCase().replaceAll("[\\s\\-_:;,.'\"!?()\\[\\]{}]", "");
    }
}
