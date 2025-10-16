package com.example.AOD.service;

import com.example.AOD.rules.DomainObjectMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class GenericDomainUpserter {

    /**
     * 리플렉션을 사용해 도메인 엔티티 객체의 필드 값을 동적으로 설정합니다.
     * @param domainEntity 값을 설정할 대상 엔티티 (e.g., WebtoonContent, GameContent 객체)
     * @param domainDoc    소스 데이터가 담긴 Map
     * @param mappings     어떤 키를 어떤 필드에 매핑할지에 대한 규칙
     */
    public void upsert(Object domainEntity, Map<String, Object> domainDoc, Map<String, DomainObjectMapping> mappings) {
        if (domainDoc == null || mappings == null) return;

        // Spring의 PropertyAccessor를 사용하면 안전하고 편리하게 리플렉션 처리가 가능합니다.
        var accessor = PropertyAccessorFactory.forBeanPropertyAccess(domainEntity);

        for (var entry : domainDoc.entrySet()) {
            String sourceKey = entry.getKey(); // domainDoc의 키 (e.g., "author")
            Object value = entry.getValue();

            DomainObjectMapping mapping = mappings.get(sourceKey);

            if (mapping != null && value != null) {
                String targetField = mapping.getTargetField(); // 엔티티의 필드명 (e.g., "author")
                try {
                    // 특수 타입 변환 로직 (예시)
                    Object convertedValue = convertType(value, mapping.getType());
                    // accessor를 통해 targetField에 변환된 값을 설정합니다.
                    accessor.setPropertyValue(targetField, convertedValue);
                } catch (Exception e) {
                    log.error("Failed to set property '{}' on {} with value '{}'",
                              targetField, domainEntity.getClass().getSimpleName(), value, e);
                }
            }
        }
    }

    // 간단한 타입 변환기 예시
    private Object convertType(Object value, String type) {
        if (type == null) return value;

        return switch (type) {
            case "integer" -> (value instanceof Number n) ? n.intValue() : Integer.parseInt(value.toString());
            case "long" -> (value instanceof Number n) ? n.longValue() : Long.parseLong(value.toString());
            case "webtoon_status" -> "true".equalsIgnoreCase(value.toString()) ? "완결" : "연재중";
            // TODO: date, double 등 필요한 타입 변환 로직 추가
            default -> value;
        };
    }
}
