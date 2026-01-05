package com.example.shared.repository;

import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformDataRepository extends JpaRepository<PlatformData, Long> {
    Optional<PlatformData> findByPlatformNameAndPlatformSpecificId(String platformName, String platformSpecificId);
    List<PlatformData> findByContent(Content content);
    
    /**
     * 도메인별 고유 플랫폼 이름 조회 (N+1 쿼리 방지)
     * - JOIN을 사용하여 단일 쿼리로 조회
     * - DISTINCT로 중복 제거
     */
    @Query("SELECT DISTINCT pd.platformName FROM PlatformData pd " +
           "JOIN pd.content c " +
           "WHERE c.domain = :domain AND pd.platformName IS NOT NULL " +
           "ORDER BY pd.platformName")
    List<String> findDistinctPlatformNamesByDomain(@Param("domain") Domain domain);
    
    /**
     * 전체 고유 플랫폼 이름 조회
     */
    @Query("SELECT DISTINCT pd.platformName FROM PlatformData pd " +
           "WHERE pd.platformName IS NOT NULL " +
           "ORDER BY pd.platformName")
    List<String> findDistinctPlatformNames();
}