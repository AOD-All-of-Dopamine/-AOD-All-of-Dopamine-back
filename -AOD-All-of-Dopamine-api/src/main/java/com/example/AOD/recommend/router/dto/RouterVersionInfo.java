package com.example.AOD.recommend.router.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 엔진 1개의 버전 (sha · config 해시 · 코퍼스 버전). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RouterVersionInfo(String sha, String config, String corpus) { }
