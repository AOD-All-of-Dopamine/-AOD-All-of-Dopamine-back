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

    // 도메인별 페이징 조회 (목록 경로 — 성인 제외)
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND c.isAdult = false")
    Page<Content> findByDomain(@Param("domain") Domain domain, Pageable pageable);

    // 전 도메인 기본 목록 (성인 제외) — 구 findAll(pageable) 폴백 경로 대체 (전체 탭 노출 구멍 방지)
    @Query("SELECT c FROM Content c WHERE c.isAdult = false")
    Page<Content> findAllVisible(Pageable pageable);

       // 도메인별 페이징 조회 (NULLS LAST + Tie-breaker 보장, release_date가 :maxDate 이하인 콘텐츠만 노출)
    @Query(value = "SELECT * FROM contents WHERE domain = :domain AND is_adult = false AND (release_date IS NULL OR release_date <= :maxDate) " +
           "ORDER BY release_date DESC NULLS LAST, content_id ASC",
           countQuery = "SELECT COUNT(*) FROM contents WHERE domain = :domain AND is_adult = false AND (release_date IS NULL OR release_date <= :maxDate)",
           nativeQuery = true)
    Page<Content> findByDomainOrderByReleaseDateDesc(@Param("domain") String domain, @Param("maxDate") LocalDate maxDate, Pageable pageable);

    // 검색을 위한 메서드
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND c.isAdult = false AND " +
           "(LOWER(c.masterTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Content> searchByDomainAndKeyword(@Param("domain") Domain domain,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);

    // 전체 검색
    @Query("SELECT c FROM Content c WHERE c.isAdult = false AND " +
           "(LOWER(c.masterTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Content> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    // ID 리스트로 조회 (추천용)
    List<Content> findByContentIdIn(List<Long> ids);
    
    // [✨ 신작/공개예정 조회용 메서드]
    // 특정 날짜 이후 출시된 작품 (신작) - 도메인별
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND c.isAdult = false AND c.releaseDate <= :endDate " +
           "ORDER BY c.releaseDate DESC")
    Page<Content> findRecentReleases(@Param("domain") Domain domain,
                                     @Param("endDate") LocalDate endDate,
                                     Pageable pageable);

    // 특정 날짜 이후 출시된 작품 (신작) - 전체 도메인
    @Query("SELECT c FROM Content c WHERE c.isAdult = false AND c.releaseDate <= :endDate " +
           "ORDER BY c.releaseDate DESC")
    Page<Content> findRecentReleases(@Param("endDate") LocalDate endDate,
                                     Pageable pageable);

    // 특정 날짜 이후 출시 예정인 작품 (공개예정) - 도메인별 (오늘 기준 1년 이내 상한선)
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND c.isAdult = false AND c.releaseDate > :startDate AND c.releaseDate <= :endDate " +
           "ORDER BY c.releaseDate ASC, c.contentId ASC")
    Page<Content> findUpcomingReleases(@Param("domain") Domain domain,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       Pageable pageable);

    // 특정 날짜 이후 출시 예정인 작품 (공개예정) - 전체 도메인 (오늘 기준 1년 이내 상한선)
    @Query("SELECT c FROM Content c WHERE c.isAdult = false AND c.releaseDate > :startDate AND c.releaseDate <= :endDate " +
           "ORDER BY c.releaseDate ASC, c.contentId ASC")
    Page<Content> findUpcomingReleases(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       Pageable pageable);

    // 특정 날짜 범위의 신작 조회 - 도메인별
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND c.isAdult = false AND c.releaseDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.releaseDate DESC")
    Page<Content> findReleasesInDateRange(@Param("domain") Domain domain,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate,
                                          Pageable pageable);

    // 특정 날짜 범위의 신작 조회 - 전체 도메인
    @Query("SELECT c FROM Content c WHERE c.isAdult = false AND c.releaseDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.releaseDate DESC")
    Page<Content> findReleasesInDateRange(@Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate,
                                          Pageable pageable);
    
    /**
     * findWorks 공통 WHERE 조각 (main/count 쿼리 중복 방지용 상수).
     *
     * 필터 출처 규율: contents 마스터 컬럼 + 도메인 엔티티 컬럼만 필터 축으로 쓴다.
     * platform_data.attributes(JSONB)는 필터 금지 (표시 전용).
     *
     * - genres: @> 포함(AND) — "무협이면서 회귀"
     * - platforms: && 겹침(OR) — "넷플릭스나 왓챠 중 하나라도" (2026-08 @>에서 전환, GIN 인덱스 동일 사용)
     * - releaseFrom/To: 출시 시기 범위 (yyyy-MM-dd 문자열, null이면 무시)
     * - status/weekdays/ageRatings: 웹툰 도메인 컬럼 — EXISTS 서브쿼리 (타 도메인에선 null로 무시)
     * - is_adult=false 고정: 성인 콘텐츠는 목록에 노출하지 않는다 (2026-08 Steam 정제)
     * - reviewCountMin: 게임 도메인 컬럼(game_contents.review_count) — EXISTS 서브쿼리.
     *   게임 외 도메인에 보내면 EXISTS 불성립으로 0건 — 프론트가 게임 탭 한정으로 전송하는 계약.
     *   review_count IS NULL(미수집)인 게임도 제외된다.
     */
    String WORKS_FILTER =
           "c.domain = :domain " +
           "AND c.is_adult = false " +
           "AND (CAST(:genres AS text[]) IS NULL OR c.genres @> CAST(:genres AS text[])) " +
           "AND (CAST(:platforms AS text[]) IS NULL OR c.platforms && CAST(:platforms AS text[])) " +
           "AND (CAST(:keyword AS text) IS NULL OR c.master_title ILIKE ('%' || :keyword || '%') OR c.original_title ILIKE ('%' || :keyword || '%')) " +
           "AND (CAST(:releaseFrom AS date) IS NULL OR c.release_date >= CAST(:releaseFrom AS date)) " +
           "AND (CAST(:releaseTo AS date) IS NULL OR c.release_date <= CAST(:releaseTo AS date)) " +
           "AND ((CAST(:status AS text) IS NULL AND CAST(:weekdays AS text[]) IS NULL AND CAST(:ageRatings AS text[]) IS NULL) " +
           "     OR EXISTS (SELECT 1 FROM webtoon_contents w WHERE w.content_id = c.content_id " +
           "         AND (CAST(:status AS text) IS NULL OR w.status = :status) " +
           "         AND (CAST(:weekdays AS text[]) IS NULL OR w.weekday = ANY(CAST(:weekdays AS text[]))) " +
           "         AND (CAST(:ageRatings AS text[]) IS NULL OR w.age_rating = ANY(CAST(:ageRatings AS text[]))))) " +
           "AND (CAST(:reviewCountMin AS integer) IS NULL " +
           "     OR EXISTS (SELECT 1 FROM game_contents g WHERE g.content_id = c.content_id " +
           "         AND g.review_count >= CAST(:reviewCountMin AS integer))) ";

    /**
     * 통합 필터 조회 — 각 파라미터가 null이면 해당 축 무시.
     * genres/platforms가 contents로 승격되면서(2026-07) 도메인 repo의 findWorks 5개를 대체한 단일 쿼리.
     * 2026-08: 출시 시기·웹툰 도메인 컬럼(상태/요일/연령) 축 추가, platforms를 OR 매칭으로 전환.
     * 2026-08 Steam 정제: is_adult 제외 고정 + 게임 축 reviewCountMin(웹툰 3축과 같은 EXISTS 패턴).
     */
    @Query(value = "SELECT c.* FROM contents c WHERE " + WORKS_FILTER +
           "ORDER BY c.release_date DESC NULLS LAST, c.content_id ASC",
           countQuery = "SELECT COUNT(*) FROM contents c WHERE " + WORKS_FILTER,
           nativeQuery = true)
    Page<Content> findWorks(@Param("domain") String domain,
                            @Param("genres") String[] genres,
                            @Param("platforms") String[] platforms,
                            @Param("keyword") String keyword,
                            @Param("releaseFrom") String releaseFrom,
                            @Param("releaseTo") String releaseTo,
                            @Param("status") String status,
                            @Param("weekdays") String[] weekdays,
                            @Param("ageRatings") String[] ageRatings,
                            @Param("reviewCountMin") Integer reviewCountMin,
                            Pageable pageable);

    // 플랫폼 필터링만 (도메인 무관, 목록 경로 — 성인 제외)
    @Query("SELECT DISTINCT c FROM Content c " +
           "JOIN PlatformData pd ON pd.content = c " +
           "WHERE LOWER(pd.platformName) IN :platforms AND c.isAdult = false")
    Page<Content> findByPlatforms(@Param("platforms") List<String> platforms,
                                  Pageable pageable);
    
    // [✨ 최적화: 벌크 업데이트 (Dirty Checking 방지)]
    // 엔티티를 조회(Select)하고 값 변경 후 자동 감지(Dirty Checking)를 쓰면 N+1 쿼리가 또 발생하므로,
    // Native Update 쿼리로 한 방에 별점만 갱신하여 Lock 점유 시간을 최소화합니다.
    @Modifying
    @Query("UPDATE Content c SET c.averageScore = :avg, c.reviewCount = :cnt WHERE c.contentId = :contentId")
    void updateRatingInfo(@Param("contentId") Long contentId, @Param("avg") Double avg, @Param("cnt") Integer cnt);

    // [✨ 신규 기능: 최근 리뷰 달린 작품 목록 조회] (발견 경로 — 성인 제외)
    @Query(value = "SELECT c.* FROM contents c " +
                   "JOIN (SELECT content_id, MAX(created_at) as latest_review FROM reviews GROUP BY content_id) r " +
                   "ON c.content_id = r.content_id " +
                   "WHERE c.is_adult = false " +
                   "ORDER BY r.latest_review DESC",
           countQuery = "SELECT COUNT(DISTINCT r.content_id) FROM reviews r JOIN contents c ON r.content_id = c.content_id WHERE c.is_adult = false",
           nativeQuery = true)
    Page<Content> findRecentlyReviewedContentsNative(Pageable pageable);

    @Query(value = "SELECT c.* FROM contents c " +
                   "JOIN (SELECT content_id, MAX(created_at) as latest_review FROM reviews GROUP BY content_id) r " +
                   "ON c.content_id = r.content_id " +
                   "WHERE c.domain = :domain AND c.is_adult = false " +
                   "ORDER BY r.latest_review DESC",
           countQuery = "SELECT COUNT(DISTINCT r.content_id) FROM reviews r JOIN contents c ON r.content_id = c.content_id WHERE c.domain = :domain AND c.is_adult = false",
           nativeQuery = true)
    Page<Content> findRecentlyReviewedContentsByDomainNative(@Param("domain") String domain, Pageable pageable);

    // ===== 장르 (2026-07 도메인 테이블에서 contents로 승격 — 구 도메인 repo의 countByGenre/findDistinctGenres 대체) =====

    /**
     * 도메인별 장르 작품 수 집계 (UNNEST + GROUP BY) — 테이블 전체 로드 없이 DB에서 카운트.
     * is_adult=false: 목록에 안 보이는 성인 콘텐츠가 집계·필터 옵션에 새지 않게 (정합)
     */
    @Query(value = "SELECT g AS genre, COUNT(*) AS cnt FROM contents, UNNEST(genres) AS g " +
           "WHERE domain = :domain AND is_adult = false AND g IS NOT NULL AND g <> '' GROUP BY g ORDER BY cnt DESC", nativeQuery = true)
    List<Object[]> countByGenre(@Param("domain") String domain);

    /**
     * 도메인별 사용 중인 장르 목록 (중복 제거, 성인 제외 — 필터 옵션 정합)
     */
    @Query(value = "SELECT DISTINCT g FROM contents, UNNEST(genres) AS g " +
           "WHERE domain = :domain AND is_adult = false AND g IS NOT NULL AND g <> ''", nativeQuery = true)
    List<String> findDistinctGenres(@Param("domain") String domain);

    /**
     * 도메인별 사용 중인 플랫폼 목록 (contents.platforms 실데이터 — 수집 소스 + OTT 등,
     * "볼 수 있는 곳" 필터 옵션의 원천, 성인 제외 — 필터 옵션 정합)
     */
    @Query(value = "SELECT DISTINCT p FROM contents, UNNEST(platforms) AS p " +
           "WHERE domain = :domain AND is_adult = false AND p IS NOT NULL AND p <> ''", nativeQuery = true)
    List<String> findDistinctPlatforms(@Param("domain") String domain);
}
