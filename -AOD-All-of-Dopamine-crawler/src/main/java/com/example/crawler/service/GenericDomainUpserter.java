package com.example.crawler.service;

import com.example.crawler.rules.DomainObjectMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
                    Object convertedValue = convertType(value, mapping.getType());

                    // 대상 필드 타입 확인 및 변환
                    Class<?> propertyType = accessor.getPropertyType(targetField);
                    if (propertyType != null && propertyType.equals(String.class)) {
                        if (convertedValue instanceof Map || convertedValue instanceof List) {
                            // 필드가 String인데 값이 Map이나 List인 경우, 문자열로 변환
                            if (convertedValue instanceof Map mapValue && mapValue.containsKey("name")) {
                                convertedValue = mapValue.get("name").toString();
                            } else {
                                convertedValue = convertedValue.toString();
                            }
                        }
                    }

                    // accessor를 통해 targetField에 변환된 값을 설정합니다.
                    accessor.setPropertyValue(targetField, convertedValue);
                } catch (Exception e) {
                    log.error("Failed to set property '{}' on {} with value '{}' (type: {})",
                            targetField, domainEntity.getClass().getSimpleName(), value, value.getClass().getSimpleName(), e);
                }
            }
        }
    }

    // 간단한 타입 변환기 예시
    private Object convertType(Object value, String type) {
        if (type == null) return value;

        return switch (type) {
            case "integer" -> convertToInteger(value);
            case "long" -> (value instanceof Number n) ? n.longValue() : Long.parseLong(value.toString());
            case "webtoon_status" -> "true".equalsIgnoreCase(value.toString()) ? "완결" : "연재중";
            case "date" -> parseDate(value);
            case "list" -> (value instanceof List<?> list) ? list : List.of(value);
            // TODO: double 등 필요한 타입 변환 로직 추가
            default -> value;
        };
    }

    /**
     * Integer 타입 변환 (배열인 경우 첫 번째 값 사용)
     * TMDB API의 episode_run_time 등이 배열로 오는 경우 처리
     */
    private Integer convertToInteger(Object value) {
        if (value == null) return null;
        
        // 배열/리스트인 경우 첫 번째 값 사용
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return null;
            value = list.get(0);
        }
        
        if (value instanceof Number n) {
            return n.intValue();
        }
        
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to convert '{}' to Integer", value);
            return null;
        }
    }

    private LocalDate parseDate(Object s) {
        if (s == null) return null;
        String v = s.toString().trim();
        String[] patterns = {"uuuu년 M월 d일", "yyyy-MM-dd", "yyyy.MM.dd", "yyyy/MM/dd", "MMM d, yyyy"};
        for (String p : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(p, Locale.KOREAN);
                return LocalDate.parse(v, formatter);
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalDate.of(Integer.parseInt(v), 1, 1);
        } catch (Exception ignored) {
        }
        return null;
    }
}


