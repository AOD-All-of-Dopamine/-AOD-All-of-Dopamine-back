package com.example.AOD.recommend.router.dto;

import java.util.List;
import java.util.Map;

/**
 * POST /v1/recommend 요청 (서빙 README §2-1).
 * 값은 전부 "라우터 platform → 문자열 키 목록". 한도는 라우터가 422 로 막는다:
 * k 1~50 · buffer 0~50 · 플랫폼별 seeds ≤ 50 · disliked+excluded+seen 합계 ≤ 5,000.
 */
public record RouterRequest(String tab, int k, int buffer,
                            Map<String, List<String>> seeds,
                            Map<String, List<String>> disliked,
                            Map<String, List<String>> excluded,
                            Map<String, List<String>> seen) { }
