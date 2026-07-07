package com.example.AOD.recommend.dto;

public record RecRequest(String location, Long selectedContentId, int page, int size, Long userId) {}
