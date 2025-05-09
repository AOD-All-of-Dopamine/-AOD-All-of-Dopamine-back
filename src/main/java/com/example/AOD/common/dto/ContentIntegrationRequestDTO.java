package com.example.AOD.common.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ContentIntegrationRequestDTO {
    private Long configId;
    private List<Long> sourceIds;
    private Map<String, Object> additionalParams;
}