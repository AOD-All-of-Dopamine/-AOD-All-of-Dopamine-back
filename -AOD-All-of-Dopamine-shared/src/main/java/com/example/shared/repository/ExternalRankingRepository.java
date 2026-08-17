package com.example.shared.repository;

import com.example.shared.entity.ExternalRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalRankingRepository extends JpaRepository<ExternalRanking, Long> {
    
    List<ExternalRanking> findByPlatform(String platform);
    
    Optional<ExternalRanking> findByPlatformAndPlatformSpecificId(String platform, String platformSpecificId);
    
    @Query("SELECT er FROM ExternalRanking er WHERE er.platform = :platform ORDER BY er.ranking ASC")
    List<ExternalRanking> findByPlatformOrdered(@Param("platform") String platform);
    
    /**
     * 플랫폼별 랭킹 조회 (Content와 JOIN FETCH)
     * - N+1 문제 방지를 위해 Content를 함께 조회
     * - 성인 제외(2026-08): 매핑된 콘텐츠가 is_adult=true면 행 제외.
     *   미매핑 행(c IS NULL)은 성인 여부 판별 불가라 유지 — 크롤 시점 title/썸네일 노출 허용 (편차 기록)
     */
    @Query("SELECT er FROM ExternalRanking er LEFT JOIN FETCH er.content c " +
           "WHERE er.platform = :platform AND (c IS NULL OR c.isAdult = false) " +
           "ORDER BY er.ranking ASC")
    List<ExternalRanking> findByPlatformWithContent(@Param("platform") String platform);

    /**
     * 전체 랭킹 조회 (Content와 JOIN FETCH)
     * - N+1 문제 방지를 위해 Content를 함께 조회
     * - 성인 제외 규칙은 findByPlatformWithContent와 동일 (매핑된 성인만 제외, 미매핑 유지)
     */
    @Query("SELECT er FROM ExternalRanking er LEFT JOIN FETCH er.content c " +
           "WHERE c IS NULL OR c.isAdult = false " +
           "ORDER BY er.platform ASC, er.ranking ASC")
    List<ExternalRanking> findAllWithContent();
}
