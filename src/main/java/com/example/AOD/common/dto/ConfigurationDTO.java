package com.example.AOD.common.dto;

import lombok.Data;

import java.util.List;

@Data
public class ConfigurationDTO {
    private Long id;
    private String contentType;
    private String name;
    private String description;
    // 'isActive' 대신 'active'로 필드명 변경 (lombok의 @Data에서는 이게 더 일관성 있게 동작)
    private boolean active;
    private List<FieldMappingDTO> fieldMappings;
    private List<CustomFieldCalculationDTO> customCalculations;

    // 추가로 아래 메서드도 제공해주면 좋습니다
    // Thymeleaf에서 th:field="*{isActive}"와 호환되도록
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}