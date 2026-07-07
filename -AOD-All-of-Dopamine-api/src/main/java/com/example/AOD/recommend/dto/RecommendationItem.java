package com.example.AOD.recommend.dto;

public record RecommendationItem(
        Long contentId,
        String domain,
        double score,
        String candidateSource,
        int rankPosition,
        String scoreBreakdownJson) {}
