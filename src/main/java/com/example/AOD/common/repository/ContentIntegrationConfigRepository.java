package com.example.AOD.common.repository;

import com.example.AOD.common.config.ContentIntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentIntegrationConfigRepository extends JpaRepository<ContentIntegrationConfig, Long> {
    List<ContentIntegrationConfig> findByContentTypeAndIsActiveTrue(String contentType);
    Optional<ContentIntegrationConfig> findByNameAndContentType(String name, String contentType);
}