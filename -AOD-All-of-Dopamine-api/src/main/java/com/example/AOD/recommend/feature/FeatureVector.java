package com.example.AOD.recommend.feature;

public record FeatureVector(double funTag, double profileSim, double quality,
                            double metadata, double recency) {}
