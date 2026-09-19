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

    /**
     * 추천 키 매핑(정방향) — content_id → 라우터 키 재료. 지원 플랫폼 이름만 받는다.
     * 엔티티가 아니라 record 프로젝션이라 LAZY content 프록시·N+1 이 생기지 않는다.
     * 한 작품에 지원 플랫폼 행이 여럿이면 호출자가 platform_data_id 가 가장 작은 행을 고른다(ORDER BY).
     */
    @Query("SELECT new com.example.shared.repository.PlatformKeyRow(pd.content.contentId, pd.platformName, "
         + "pd.platformSpecificId, pd.platformDataId) FROM PlatformData pd "
         + "WHERE pd.content.contentId IN :contentIds AND pd.platformName IN :platformNames "
         + "AND pd.platformSpecificId IS NOT NULL "
         + "ORDER BY pd.platformDataId ASC")
    List<PlatformKeyRow> findRecKeyRowsByContentIds(@Param("contentIds") java.util.Collection<Long> contentIds,
                                                    @Param("platformNames") java.util.Collection<String> platformNames);

    /**
     * 추천 카드 조립(역방향) — (플랫폼, 플랫폼 고유 ID) → Content.
     * JOIN FETCH 로 Content 를 함께 싣는다(카드가 제목·포스터·성인 여부를 바로 읽는다).
     * uk_platform_id(platformName, platformSpecificId) 유니크라 한 쌍에 행은 최대 하나다.
     */
    @Query("SELECT pd FROM PlatformData pd JOIN FETCH pd.content "
         + "WHERE pd.platformName = :platformName AND pd.platformSpecificId IN :platformSpecificIds")
    List<PlatformData> findWithContentByPlatformNameAndIds(
            @Param("platformName") String platformName,
            @Param("platformSpecificIds") java.util.Collection<String> platformSpecificIds);

    /**
     * 서빙 가능 키 목록(설계 §10) — 성인이 아닌 작품의 지원 플랫폼 행 전부.
     * 추천 엔진 4개가 10분마다 받아 가므로 호출당 쿼리 하나로 끝낸다(프로젝션, 엔티티 없음).
     */
    @Query("SELECT new com.example.shared.repository.PlatformKeyRow(c.contentId, pd.platformName, "
         + "pd.platformSpecificId, pd.platformDataId) FROM PlatformData pd JOIN pd.content c "
         + "WHERE pd.platformName IN :platformNames AND pd.platformSpecificId IS NOT NULL "
         + "AND c.isAdult = false")
    List<PlatformKeyRow> findCatalogKeyRows(@Param("platformNames") java.util.Collection<String> platformNames);
}