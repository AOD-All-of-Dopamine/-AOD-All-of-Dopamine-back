package com.example.shared.repository;

import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Long> {
    Optional<Content> findFirstByDomainAndMasterTitleAndReleaseDate(Domain domain, String masterTitle, LocalDate releaseDate);

    // 도메인별 페이징 조회
    Page<Content> findByDomain(Domain domain, Pageable pageable);
    
       // 도메인별 페이징 조회 (NULLS LAST + Tie-breaker 보장, release_date가 :maxDate 이하인 콘텐츠만 노출)
    @Query(value = "SELECT * FROM contents WHERE domain = :domain AND (release_date IS NULL OR release_date <= :maxDate) " +
           "ORDER BY release_date DESC NULLS LAST, content_id ASC",
           countQuery = "SELECT COUNT(*) FROM contents WHERE domain = :domain AND (release_date IS NULL OR release_date <= :maxDate)",
           nativeQuery = true)
    Page<Content> findByDomainOrderByReleaseDateDesc(@Param("domain") String domain, @Param("maxDate") LocalDate maxDate, Pageable pageable);
    
    // 검색을 위한 메서드
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND " +
           "(LOWER(c.masterTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Content> searchByDomainAndKeyword(@Param("domain") Domain domain, 
                                           @Param("keyword") String keyword, 
                                           Pageable pageable);
    
    // 전체 검색
    @Query("SELECT c FROM Content c WHERE " +
           "LOWER(c.masterTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Content> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    // ID 리스트로 조회 (추천용)
    List<Content> findByContentIdIn(List<Long> ids);
    
    // [✨ 신작/공개예정 조회용 메서드]
    // 특정 날짜 이후 출시된 작품 (신작) - 도메인별
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND c.releaseDate <= :endDate " +
           "ORDER BY c.releaseDate DESC")
    Page<Content> findRecentReleases(@Param("domain") Domain domain, 
                                     @Param("endDate") LocalDate endDate, 
                                     Pageable pageable);
    
    // 특정 날짜 이후 출시된 작품 (신작) - 전체 도메인
    @Query("SELECT c FROM Content c WHERE c.releaseDate <= :endDate " +
           "ORDER BY c.releaseDate DESC")
    Page<Content> findRecentReleases(@Param("endDate") LocalDate endDate, 
                                     Pageable pageable);
    
    // 특정 날짜 이후 출시 예정인 작품 (공개예정) - 도메인별 (오늘 기준 1년 이내 상한선)
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND c.releaseDate > :startDate AND c.releaseDate <= :endDate " +
           "ORDER BY c.releaseDate ASC, c.contentId ASC")
    Page<Content> findUpcomingReleases(@Param("domain") Domain domain,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       Pageable pageable);
    
    // 특정 날짜 이후 출시 예정인 작품 (공개예정) - 전체 도메인 (오늘 기준 1년 이내 상한선)
    @Query("SELECT c FROM Content c WHERE c.releaseDate > :startDate AND c.releaseDate <= :endDate " +
           "ORDER BY c.releaseDate ASC, c.contentId ASC")
    Page<Content> findUpcomingReleases(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       Pageable pageable);
    
    // 특정 날짜 범위의 신작 조회 - 도메인별
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND c.releaseDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.releaseDate DESC")
    Page<Content> findReleasesInDateRange(@Param("domain") Domain domain,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate,
                                          Pageable pageable);
    
    // 특정 날짜 범위의 신작 조회 - 전체 도메인
    @Query("SELECT c FROM Content c WHERE c.releaseDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.releaseDate DESC")
    Page<Content> findReleasesInDateRange(@Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate,
                                          Pageable pageable);
    
    // [✨ 플랫폼 필터링 쿼리 - 메모리 부하 해결]
    // 도메인 + 플랫폼 필터링
    @Query("SELECT DISTINCT c FROM Content c " +
           "JOIN PlatformData pd ON pd.content = c " +
           "WHERE c.domain = :domain AND LOWER(pd.platformName) IN :platforms")
    Page<Content> findByDomainAndPlatforms(@Param("domain") Domain domain,
                                           @Param("platforms") List<String> platforms,
                                           Pageable pageable);
    
    // 플랫폼 필터링만 (도메인 무관)
    @Query("SELECT DISTINCT c FROM Content c " +
           "JOIN PlatformData pd ON pd.content = c " +
           "WHERE LOWER(pd.platformName) IN :platforms")
    Page<Content> findByPlatforms(@Param("platforms") List<String> platforms,
                                  Pageable pageable);
    
    // 도메인 + 키워드 + 플랫폼 필터링
    @Query("SELECT DISTINCT c FROM Content c " +
           "JOIN PlatformData pd ON pd.content = c " +
           "WHERE c.domain = :domain " +
           "AND (LOWER(c.masterTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(c.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND LOWER(pd.platformName) IN :platforms")
    Page<Content> findByDomainAndKeywordAndPlatforms(@Param("domain") Domain domain,
                                                     @Param("keyword") String keyword,
                                                     @Param("platforms") List<String> platforms,
                                                     Pageable pageable);
    
    // 키워드 + 플랫폼 필터링 (도메인 무관)
    @Query("SELECT DISTINCT c FROM Content c " +
           "JOIN PlatformData pd ON pd.content = c " +
           "WHERE (LOWER(c.masterTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "       LOWER(c.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND LOWER(pd.platformName) IN :platforms")
    Page<Content> findByKeywordAndPlatforms(@Param("keyword") String keyword,
                                           @Param("platforms") List<String> platforms,
                                           Pageable pageable);

    // [✨ 최적화: 벌크 업데이트 (Dirty Checking 방지)]
    // 엔티티를 조회(Select)하고 값 변경 후 자동 감지(Dirty Checking)를 쓰면 N+1 쿼리가 또 발생하므로,
    // Native Update 쿼리로 한 방에 별점만 갱신하여 Lock 점유 시간을 최소화합니다.
    @Modifying
    @Query("UPDATE Content c SET c.averageScore = :avg, c.reviewCount = :cnt WHERE c.contentId = :contentId")
    void updateRatingInfo(@Param("contentId") Long contentId, @Param("avg") Double avg, @Param("cnt") Integer cnt);

    // [✨ 신규 기능: 최근 리뷰 달린 작품 목록 조회]
    @Query(value = "SELECT c.* FROM contents c " +
                   "JOIN (SELECT content_id, MAX(created_at) as latest_review FROM reviews GROUP BY content_id) r " +
                   "ON c.content_id = r.content_id " +
                   "ORDER BY r.latest_review DESC",
           countQuery = "SELECT COUNT(DISTINCT content_id) FROM reviews",
           nativeQuery = true)
    Page<Content> findRecentlyReviewedContentsNative(Pageable pageable);

    @Query(value = "SELECT c.* FROM contents c " +
                   "JOIN (SELECT content_id, MAX(created_at) as latest_review FROM reviews GROUP BY content_id) r " +
                   "ON c.content_id = r.content_id " +
                   "WHERE c.domain = :domain " +
                   "ORDER BY r.latest_review DESC",
           countQuery = "SELECT COUNT(DISTINCT r.content_id) FROM reviews r JOIN contents c ON r.content_id = c.content_id WHERE c.domain = :domain",
           nativeQuery = true)
    Page<Content> findRecentlyReviewedContentsByDomainNative(@Param("domain") String domain, Pageable pageable);
}
