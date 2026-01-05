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
     */
    @Query("SELECT er FROM ExternalRanking er LEFT JOIN FETCH er.content WHERE er.platform = :platform ORDER BY er.ranking ASC")
    List<ExternalRanking> findByPlatformWithContent(@Param("platform") String platform);
    
    /**
     * 전체 랭킹 조회 (Content와 JOIN FETCH)
     * - N+1 문제 방지를 위해 Content를 함께 조회
     */
    @Query("SELECT er FROM ExternalRanking er LEFT JOIN FETCH er.content ORDER BY er.platform ASC, er.ranking ASC")
    List<ExternalRanking> findAllWithContent();
}
