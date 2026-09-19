package com.example.AOD.recommend.router.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 항목 점수. JSON 의 "final" 은 자바 예약어라 이름을 바꿔 매핑한다.
 * factors 안쪽 키는 라우터가 snake_case 원문 그대로 준다(서빙 README §2-1) — 손대지 않고 jsonb 로 넘긴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RouterScore(@JsonProperty("final") Double finalScore, Double sim, Map<String, Double> factors) { }
