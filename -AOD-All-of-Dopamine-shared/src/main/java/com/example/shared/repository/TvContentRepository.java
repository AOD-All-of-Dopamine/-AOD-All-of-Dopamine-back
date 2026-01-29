package com.example.shared.repository;

import com.example.shared.entity.TvContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TvContentRepository extends JpaRepository<TvContent, Long> {
    
    /**
     * 장르 필터링 (AND 조건) - 모든 장르를 포함하는 TV만 반환
     * PostgreSQL 배열 연산자 @> 사용 (contains)
     */
    @Query(value = "SELECT t.* FROM tv_contents t " +
           "JOIN contents c ON t.content_id = c.content_id " +
           "WHERE t.genres @> CAST(:genres AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST",
           countQuery = "SELECT COUNT(*) FROM tv_contents t " +
                       "WHERE t.genres @> CAST(:genres AS text[])",
           nativeQuery = true)
    Page<TvContent> findByGenresContainingAll(@Param("genres") String[] genres, Pageable pageable);
    
    /**
     * 플랫폼 필터링 (AND 조건) - 모든 플랫폼을 제공하는 TV만 반환
     * genres 필터링과 동일한 패턴
     */
    @Query(value = "SELECT t.* FROM tv_contents t " +
           "JOIN contents c ON t.content_id = c.content_id " +
           "WHERE t.platforms @> CAST(:platforms AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST",
           countQuery = "SELECT COUNT(*) FROM tv_contents t " +
                       "WHERE t.platforms @> CAST(:platforms AS text[])",
           nativeQuery = true)
    Page<TvContent> findByPlatformsContainingAll(@Param("platforms") String[] platforms, Pageable pageable);
    
    /**
     * 장르 + 플랫폼 복합 필터링
     */
    @Query(value = "SELECT t.* FROM tv_contents t " +
           "JOIN contents c ON t.content_id = c.content_id " +
           "WHERE t.genres @> CAST(:genres AS text[]) " +
           "AND t.platforms @> CAST(:platforms AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST",
           countQuery = "SELECT COUNT(*) FROM tv_contents t " +
                       "WHERE t.genres @> CAST(:genres AS text[]) " +
                       "AND t.platforms @> CAST(:platforms AS text[])",
           nativeQuery = true)
    Page<TvContent> findByGenresAndPlatforms(
        @Param("genres") String[] genres,
        @Param("platforms") String[] platforms,
        Pageable pageable
    );
    
    /**
     * Content ID 목록으로 조회
     */
    List<TvContent> findByContentIdIn(List<Long> contentIds);
}
