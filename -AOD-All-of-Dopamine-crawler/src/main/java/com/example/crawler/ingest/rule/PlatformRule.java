package com.example.crawler.ingest.rule;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * yml 룰 1개 = 이 record 1개 (스키마 v4).
 * mappings: 소스 payload 경로 → 목적지. 목적지 접두사가 저장 위치를 결정:
 *   master.*  → Content 프로퍼티 / domain.* → 도메인 엔티티 프로퍼티
 *   platform.* → PlatformData 프로퍼티 / attr.* → platform_data.attributes JSONB 키 리터럴
 * SnakeYAML 빈 바인딩 대신 명시적 Map 파싱 — 에러 메시지에 파일 경로를 담기 위함.
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
        Set<String> unknown = new HashSet<>(yaml.keySet());
        unknown.removeAll(TOP_KEYS);
        if (!unknown.isEmpty()) throw new IllegalStateException(path + ": 알 수 없는 최상위 키 " + unknown);

        String platformName = (String) yaml.get("platformName");
        String domain = (String) yaml.get("domain");
        if (platformName == null || domain == null)
            throw new IllegalStateException(path + ": platformName/domain 필수");

        Map<String, String> mappings = (Map<String, String>) yaml.getOrDefault("mappings", Map.of());
        Map<String, Object> defaults = (Map<String, Object>) yaml.getOrDefault("defaults", Map.of());
        for (String dst : mappings.values()) requirePrefix(path, dst);
        for (String dst : defaults.keySet()) requirePrefix(path, dst);

        return new PlatformRule(
                platformName, domain,
                ((Number) yaml.getOrDefault("schemaVersion", 4)).intValue(),
                mappings, defaults,
                (Map<String, List<String>>) yaml.getOrDefault("normalizers", Map.of()),
                (List<String>) yaml.getOrDefault("platformsFrom", List.of()));
    }

    private static void requirePrefix(String path, String dst) {
        if (PREFIXES.stream().noneMatch(dst::startsWith))
            throw new IllegalStateException(path + ": 목적지 접두사 오류 '" + dst + "' (master./domain./platform./attr. 중 하나)");
    }
}
