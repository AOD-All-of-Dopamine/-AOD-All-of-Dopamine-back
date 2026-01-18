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
    
    /**
     * 특정 Watch Provider (OTT)를 가진 Content ID 조회 - 단일 OTT
     * PlatformData.attributes JSONB 내의 watch_providers 배열에서 검색
     * @param watchProvider 소문자로 변환된 OTT 플랫폼 이름 (예: "netflix")
     */
    @Query(value = "SELECT DISTINCT pd.content_id FROM platform_data pd " +
           "WHERE pd.attributes -> 'watch_providers' IS NOT NULL " +
           "AND jsonb_typeof(pd.attributes -> 'watch_providers') = 'array' " +
           "AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(pd.attributes -> 'watch_providers') AS wp " +
           "            WHERE LOWER(wp) = :watchProvider)",
           nativeQuery = true)
    List<Long> findContentIdsByWatchProvider(@Param("watchProvider") String watchProvider);
    
    /**
     * 특정 도메인의 Watch Provider (OTT)를 가진 Content ID 조회 - 단일 OTT
     * @param domain 도메인 (MOVIE, TV 등)
     * @param watchProvider 소문자로 변환된 OTT 플랫폼 이름
     */
    @Query(value = "SELECT DISTINCT pd.content_id FROM platform_data pd " +
           "JOIN contents c ON pd.content_id = c.content_id " +
           "WHERE c.domain = :domain " +
           "AND pd.attributes -> 'watch_providers' IS NOT NULL " +
           "AND jsonb_typeof(pd.attributes -> 'watch_providers') = 'array' " +
           "AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(pd.attributes -> 'watch_providers') AS wp " +
           "            WHERE LOWER(wp) = :watchProvider)",
           nativeQuery = true)
    List<Long> findContentIdsByDomainAndWatchProvider(@Param("domain") String domain,
                                                       @Param("watchProvider") String watchProvider);
}