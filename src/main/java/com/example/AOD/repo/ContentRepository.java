package com.example.AOD.repo;

import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.Domain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Long> {
    Optional<Content> findFirstByDomainAndMasterTitleAndReleaseDate(Domain domain, String masterTitle, LocalDate releaseDate);

    // 도메인별 페이징 조회
    Page<Content> findByDomain(Domain domain, Pageable pageable);
    
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
    
    // 특정 날짜 이후 출시 예정인 작품 (공개예정) - 도메인별
    @Query("SELECT c FROM Content c WHERE c.domain = :domain AND c.releaseDate > :startDate " +
           "ORDER BY c.releaseDate ASC")
    Page<Content> findUpcomingReleases(@Param("domain") Domain domain, 
                                       @Param("startDate") LocalDate startDate, 
                                       Pageable pageable);
    
    // 특정 날짜 이후 출시 예정인 작품 (공개예정) - 전체 도메인
    @Query("SELECT c FROM Content c WHERE c.releaseDate > :startDate " +
           "ORDER BY c.releaseDate ASC")
    Page<Content> findUpcomingReleases(@Param("startDate") LocalDate startDate, 
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
}