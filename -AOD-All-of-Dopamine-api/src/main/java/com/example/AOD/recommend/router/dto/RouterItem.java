package com.example.AOD.recommend.router.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 라우터가 돌려준 후보 1개 (서빙 README §2-1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RouterItem(String platform, String key, Integer rank, String dominantSeed,
                         String candidateSource, Boolean isExploration, Double propensity,
                         RouterScore score, String factorSchema) { }
