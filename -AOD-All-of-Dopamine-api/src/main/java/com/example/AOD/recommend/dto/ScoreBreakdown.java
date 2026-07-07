package com.example.AOD.recommend.dto;

public record ScoreBreakdown(
        double funTag, double profileSim, double quality,
        double metadata, double recency, double finalScore) {}
