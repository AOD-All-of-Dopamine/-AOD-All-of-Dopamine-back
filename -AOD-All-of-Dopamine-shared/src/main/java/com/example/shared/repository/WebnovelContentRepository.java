package com.example.shared.repository;


import com.example.shared.entity.WebnovelContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WebnovelContentRepository extends JpaRepository<WebnovelContent, Long> {
    
    /**
     * 장르 필터링 (AND 조건) - 모든 장르를 포함하는 웹소설만 반환
     * PostgreSQL 배열 연산자 @> 사용 (contains)
     */
    @Query(value = "SELECT w.* FROM webnovel_contents w " +
           "JOIN contents c ON w.content_id = c.content_id " +
           "WHERE w.genres @> CAST(:genres AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST",
           countQuery = "SELECT COUNT(*) FROM webnovel_contents w " +
                       "WHERE w.genres @> CAST(:genres AS text[])",
           nativeQuery = true)
    Page<WebnovelContent> findByGenresContainingAll(@Param("genres") String[] genres, Pageable pageable);
    
    /**
     * 플랫폼 필터링 (AND 조건) - 모든 플랫폼을 제공하는 웹소설만 반환
     * genres 필터링과 동일한 패턴
     */
    @Query(value = "SELECT w.* FROM webnovel_contents w " +
           "JOIN contents c ON w.content_id = c.content_id " +
           "WHERE w.platforms @> CAST(:platforms AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST",
           countQuery = "SELECT COUNT(*) FROM webnovel_contents w " +
                       "WHERE w.platforms @> CAST(:platforms AS text[])",
           nativeQuery = true)
    Page<WebnovelContent> findByPlatformsContainingAll(@Param("platforms") String[] platforms, Pageable pageable);
    
    /**
     * 장르 + 플랫폼 복합 필터링
     */
    @Query(value = "SELECT w.* FROM webnovel_contents w " +
           "JOIN contents c ON w.content_id = c.content_id " +
           "WHERE w.genres @> CAST(:genres AS text[]) " +
           "AND w.platforms @> CAST(:platforms AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST",
           countQuery = "SELECT COUNT(*) FROM webnovel_contents w " +
                       "WHERE w.genres @> CAST(:genres AS text[]) " +
                       "AND w.platforms @> CAST(:platforms AS text[])",
           nativeQuery = true)
    Page<WebnovelContent> findByGenresAndPlatforms(
        @Param("genres") String[] genres,
        @Param("platforms") String[] platforms,
        Pageable pageable
    );
    
    /**
     * Author로 웹소설 작품 검색 (중복 탐지용)
     */
    @Query("SELECT wn FROM WebnovelContent wn WHERE wn.author = :author")
    List<WebnovelContent> findByAuthor(@Param("author") String author);
    
    /**
     * Content ID 목록으로 조회
     */
    List<WebnovelContent> findByContentIdIn(List<Long> contentIds);
}
