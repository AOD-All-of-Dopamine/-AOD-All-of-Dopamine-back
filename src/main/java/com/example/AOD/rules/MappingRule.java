package com.example.AOD.rules;

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
    // getters/setters
}

