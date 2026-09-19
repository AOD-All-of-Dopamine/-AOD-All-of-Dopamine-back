package com.example.AOD.recommend.router.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** 결과가 바뀐 원인(배포·코퍼스 교체·설정 변경)을 나중에 가릴 유일한 단서 — rec_request.versions 에 남긴다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RouterVersions(String router, Map<String, RouterVersionInfo> engines) { }
