package com.example.AOD.common.dto;

import lombok.Data;

@Data
public class FieldMappingDTO {
    private Long id;
    private String commonField;
    private String platform;
    private String platformField;
    private Integer priority;
}