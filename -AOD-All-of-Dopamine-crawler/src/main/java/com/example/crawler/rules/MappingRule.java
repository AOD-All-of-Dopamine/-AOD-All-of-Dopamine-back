package com.example.crawler.rules;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;


@Getter
@Setter
public class MappingRule {
    private String platformName;
    private String domain;          // "AV","GAME"... 문자열로 둠
    private int schemaVersion;
    private Map<String, String> fieldMappings; // src -> dst
    private List<NormalizerStep> normalizers;

    // RF-3: 원본에 값이 없을 때 채울 명시적 기본값 (key = dst 경로, 예: "platform.attributes.comment_count")
    // 과거의 필드명 휴리스틱(count→0 등)을 대체 — 선언 없으면 해당 필드는 스킵된다.
    private Map<String, Object> defaults;

    // [ ✨ 핵심 추가 ] YAML의 domainObjectMappings를 담을 필드
    private Map<String, DomainObjectMapping> domainObjectMappings;
    // getters/setters
}



