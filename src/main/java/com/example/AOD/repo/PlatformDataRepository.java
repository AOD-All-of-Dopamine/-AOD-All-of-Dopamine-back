package com.example.AOD.repo;

import com.example.AOD.domain.entity.PlatformData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformDataRepository extends JpaRepository<PlatformData, Long> {
    Optional<PlatformData> findByPlatformNameAndPlatformSpecificId(String platformName, String platformSpecificId);
}