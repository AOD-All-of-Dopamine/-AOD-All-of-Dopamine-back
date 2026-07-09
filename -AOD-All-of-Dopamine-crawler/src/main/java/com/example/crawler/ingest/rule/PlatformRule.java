package com.example.crawler.ingest.rule;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * yml 룰 1개 = 이 record 1개 (스키마 v4).
 * mappings: 소스 payload 경로 → 목적지. 목적지 접두사가 저장 위치를 결정:
 *   master.*  → Content 프로퍼티 / domain.* → 도메인 엔티티 프로퍼티
 *   platform.* → PlatformData 프로퍼티 / attr.* → platform_data.attributes JSONB 키 리터럴
 * defaults: 원본에 값이 없을 때 채울 목적지별 기본값 (키 = 목적지, 접두사 규칙 동일).
 * normalizers: master.* 목적지별 정규화 스텝 리스트 (어휘는 Values.NORMALIZERS, 기동검증은 RuleRegistry).
 * platformsFrom: attributes에서 domain.platforms 배열로 병합할 키 목록 (예: watch_providers).
 * SnakeYAML 빈 바인딩 대신 명시적 Map 파싱 — 모든 에러 메시지에 파일 경로를 담기 위함.
 */
public record PlatformRule(
        String platformName,
        String domain,
        int schemaVersion,
        Map<String, String> mappings,
        Map<String, Object> defaults,
        Map<String, List<String>> normalizers,
        List<String> platformsFrom) {

    private static final Set<String> TOP_KEYS =
            Set.of("platformName", "domain", "schemaVersion", "mappings", "defaults", "normalizers", "platformsFrom");
    private static final List<String> PREFIXES = List.of("master.", "domain.", "platform.", "attr.");

    @SuppressWarnings("unchecked")
    public static PlatformRule parse(String path, Map<String, Object> yaml) {
        if (yaml == null) throw new IllegalStateException(path + ": 빈 yml (파싱 결과 없음)");

        Set<String> unknown = new HashSet<>(yaml.keySet());
        unknown.removeAll(TOP_KEYS);
        if (!unknown.isEmpty()) throw new IllegalStateException(path + ": 알 수 없는 최상위 키 " + unknown);

        String platformName = (String) yaml.get("platformName");
        String domain = (String) yaml.get("domain");
        if (platformName == null || domain == null)
            throw new IllegalStateException(path + ": platformName/domain 필수");

        Object schemaVersion = yaml.getOrDefault("schemaVersion", 4);
        if (!(schemaVersion instanceof Number version))
            throw new IllegalStateException(path + ": schemaVersion은 숫자여야 함 (현재: " + schemaVersion + ")");

        Map<String, Object> mappingsRaw = section(path, yaml, "mappings");
        Map<String, Object> defaults = section(path, yaml, "defaults");
        Map<String, Object> normalizersRaw = section(path, yaml, "normalizers");
        Object platformsFrom = yaml.getOrDefault("platformsFrom", List.of());
        if (!(platformsFrom instanceof List))
            throw new IllegalStateException(path + ": platformsFrom은 리스트여야 함");

        Map<String, String> mappings = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : mappingsRaw.entrySet()) {
            if (!(e.getValue() instanceof String dst))
                throw new IllegalStateException(path + ": mappings." + e.getKey()
                        + " 목적지는 문자열이어야 함 (현재: " + e.getValue() + ")");
            requirePrefix(path, dst);
            mappings.put(e.getKey(), dst);
        }
        for (String dst : defaults.keySet()) requirePrefix(path, dst);

        Map<String, List<String>> normalizers = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : normalizersRaw.entrySet()) {
            if (!(e.getValue() instanceof List<?> steps))
                throw new IllegalStateException(path + ": normalizers." + e.getKey() + " 는 스텝 리스트여야 함");
            normalizers.put(e.getKey(), (List<String>) steps);
        }

        return new PlatformRule(platformName, domain, version.intValue(),
                mappings, defaults, normalizers, (List<String>) platformsFrom);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(String path, Map<String, Object> yaml, String key) {
        Object v = yaml.getOrDefault(key, Map.of());
        if (!(v instanceof Map))
            throw new IllegalStateException(path + ": " + key + " 섹션은 맵이어야 함");
        return (Map<String, Object>) v;
    }

    private static void requirePrefix(String path, String dst) {
        if (PREFIXES.stream().noneMatch(dst::startsWith))
            throw new IllegalStateException(path + ": 목적지 접두사 오류 '" + dst + "' (master./domain./platform./attr. 중 하나)");
    }
}
