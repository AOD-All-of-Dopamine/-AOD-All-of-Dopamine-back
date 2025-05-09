package com.example.AOD.common.dto;

import lombok.Data;

@Data
public class CustomFieldCalculationDTO {
    private Long id;
    private String targetField;
    private String calculationType;
    private String calculationExpression;
    private Boolean isRequired;
}