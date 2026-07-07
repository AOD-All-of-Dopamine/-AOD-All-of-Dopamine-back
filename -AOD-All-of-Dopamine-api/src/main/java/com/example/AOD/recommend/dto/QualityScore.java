package com.example.AOD.recommend.dto;

public record QualityScore(
        double bayesianScore,
        double platformRankScore,
        double reviewCountScore,
        double recencyScore,
        double qualityPopularityScore) {}
