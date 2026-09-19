package com.example.AOD.recommend.router.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * POST /v1/recommend 200 응답.
 * exhausted 는 **실제로 부른 플랫폼만** 키로 갖는다 — 실패한 플랫폼은 exhausted 에 없고 partial 에 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RouterResponse(List<RouterItem> items,
                             Map<String, Boolean> exhausted,
                             Map<String, List<String>> droppedSeeds,
                             List<String> partial,
                             RouterVersions versions) { }
