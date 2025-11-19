package com.example.AOD.ranking.repo;

import com.example.AOD.ranking.entity.ExternalRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 외부 랭킹 데이터 Repository
 * 
 * 쿼리 최적화:
 * - Fetch Join을 사용하여 N+1 문제 해결
 * - LEFT JOIN으로 content가 null인 경우도 조회
 * 
 * 파라미터 타입:
 * - platform: String (NaverWebtoon, Steam, TMDB_MOVIE, etc.)
 * - platformSpecificId: String (플랫폼별 ID, 숫자/문자 혼합)
 */
@Repository
public interface ExternalRankingRepository extends JpaRepository<ExternalRanking, Long> {
    
    /**
     * 특정 플랫폼의 랭킹 조회 (Content 포함)
     * N+1 쿼리 방지를 위한 Fetch Join 사용
     * 
     * @param platform 플랫폼 이름 (예: "NaverWebtoon", "Steam")
     * @return 해당 플랫폼의 랭킹 리스트 (순위 오름차순)
     */
    @Query("SELECT r FROM ExternalRanking r LEFT JOIN FETCH r.content WHERE r.platform = :platform ORDER BY r.ranking ASC")
    List<ExternalRanking> findByPlatformWithContent(@Param("platform") String platform);
    
    /**
     * 전체 랭킹 조회 (Content 포함)
     * N+1 쿼리 방지를 위한 Fetch Join 사용
     * 
     * @return 전체 랭킹 리스트 (플랫폼명, 순위 오름차순)
     */
    @Query("SELECT r FROM ExternalRanking r LEFT JOIN FETCH r.content ORDER BY r.platform ASC, r.ranking ASC")
    List<ExternalRanking> findAllWithContent();
    
    /**
     * 특정 플랫폼의 랭킹 조회 (Content 미포함)
     * Upsert 작업용
     * 
     * @param platform 플랫폼 이름
     * @return 해당 플랫폼의 랭킹 리스트
     */
    List<ExternalRanking> findByPlatform(String platform);
    
    /**
     * 플랫폼과 플랫폼별 ID로 랭킹 조회
     * Upsert 작업 시 기존 데이터 확인용
     * 
     * @param platform 플랫폼 이름
     * @param platformSpecificId 플랫폼별 고유 ID (String 타입)
     * @return 매칭되는 랭킹 데이터
     */
    Optional<ExternalRanking> findByPlatformAndPlatformSpecificId(String platform, String platformSpecificId);
}
