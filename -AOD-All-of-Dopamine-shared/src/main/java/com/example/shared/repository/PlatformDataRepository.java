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

    /** 목록 카드 보강용 배치 조회 (페이지당 1쿼리 — N+1 방지) */
    List<PlatformData> findByContentContentIdIn(java.util.Collection<Long> contentIds);

    /** 라이브러리 재크롤용: 플랫폼의 수집 대상 ID 전체 (null 제외) */
    @Query("SELECT pd.platformSpecificId FROM PlatformData pd " +
           "WHERE pd.platformName = :platformName AND pd.platformSpecificId IS NOT NULL")
    List<String> findSpecificIdsByPlatformName(@Param("platformName") String platformName);
    
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

    // watch_providers JSONB 검색 쿼리 2종은 2026-07 platforms 승격으로 제거됨
    // (OTT 필터는 contents.platforms 배열이 담당 — 호출자였던 deprecated 경로도 함께 삭제)
}