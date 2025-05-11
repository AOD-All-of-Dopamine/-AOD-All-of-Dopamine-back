package com.example.AOD.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentIntegrationRequestDTO {
    private Long configId;
    private List<Long> sourceIds = new ArrayList<>();
    private Map<String, Object> additionalParams = new HashMap<>();
}