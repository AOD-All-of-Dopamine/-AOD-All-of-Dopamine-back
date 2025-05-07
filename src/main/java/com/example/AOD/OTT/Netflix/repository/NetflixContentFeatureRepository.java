package com.example.AOD.OTT.Netflix.repository;

import com.example.AOD.OTT.Netflix.domain.NetflixContentFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NetflixContentFeatureRepository extends JpaRepository<NetflixContentFeature, Long> {
    Optional<NetflixContentFeature> findByName(String featureName);
}
