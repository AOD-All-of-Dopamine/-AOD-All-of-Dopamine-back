package com.example.AOD.OTT.Netflix.repository;

import com.example.AOD.OTT.Netflix.domain.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    Optional<Feature> findByName(String featureName);
}
