package com.example.shared.repository;

import com.example.shared.entity.GameContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameContentRepository extends JpaRepository<GameContent, Long> {
    
    /**
     * 장르 필터링 (AND 조건) - 모든 장르를 포함하는 게임만 반환
     * PostgreSQL 배열 연산자 @> 사용 (contains)
     */
    @Query(value = "SELECT g.* FROM game_contents g " +
           "JOIN contents c ON g.content_id = c.content_id " +
           "WHERE g.genres @> CAST(:genres AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST",
           countQuery = "SELECT COUNT(*) FROM game_contents g " +
                       "WHERE g.genres @> CAST(:genres AS text[])",
           nativeQuery = true)
    Page<GameContent> findByGenresContainingAll(@Param("genres") String[] genres, Pageable pageable);
    
    /**
     * 플랫폼 필터링 (AND 조건) - 모든 플랫폼을 제공하는 게임만 반환
     * genres 필터링과 동일한 패턴
     */
    @Query(value = "SELECT g.* FROM game_contents g " +
           "JOIN contents c ON g.content_id = c.content_id " +
           "WHERE g.platforms @> CAST(:platforms AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST",
           countQuery = "SELECT COUNT(*) FROM game_contents g " +
                       "WHERE g.platforms @> CAST(:platforms AS text[])",
           nativeQuery = true)
    Page<GameContent> findByPlatformsContainingAll(@Param("platforms") String[] platforms, Pageable pageable);
    
    /**
     * 장르 + 플랫폼 복합 필터링
     */
    @Query(value = "SELECT g.* FROM game_contents g " +
           "JOIN contents c ON g.content_id = c.content_id " +
           "WHERE g.genres @> CAST(:genres AS text[]) " +
           "AND g.platforms @> CAST(:platforms AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST",
           countQuery = "SELECT COUNT(*) FROM game_contents g " +
                       "WHERE g.genres @> CAST(:genres AS text[]) " +
                       "AND g.platforms @> CAST(:platforms AS text[])",
           nativeQuery = true)
    Page<GameContent> findByGenresAndPlatforms(
        @Param("genres") String[] genres,
        @Param("platforms") String[] platforms,
        Pageable pageable
    );
    
    /**
     * Developer로 게임 작품 검색 (중복 탐지용)
     */
    @Query("SELECT gc FROM GameContent gc WHERE gc.developer = :developer")
    List<GameContent> findByDeveloper(@Param("developer") String developer);
    
    /**
     * Publisher로 게임 작품 검색 (중복 탐지용)
     */
    @Query("SELECT gc FROM GameContent gc WHERE gc.publisher = :publisher")
    List<GameContent> findByPublisher(@Param("publisher") String publisher);
    
    /**
     * Content ID 목록으로 조회
     */
    List<GameContent> findByContentIdIn(List<Long> contentIds);
}
