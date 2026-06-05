package com.example.shared.repository;

import com.example.shared.entity.MovieContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieContentRepository extends JpaRepository<MovieContent, Long> {
    
    /**
     * 장르 필터링 (AND 조건) - 모든 장르를 포함하는 영화만 반환
     * PostgreSQL 배열 연산자 @> 사용 (contains)
     */
    @Query(value = "SELECT m.* FROM movie_contents m " +
           "JOIN contents c ON m.content_id = c.content_id " +
           "WHERE m.genres @> CAST(:genres AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST, m.content_id ASC",
           countQuery = "SELECT COUNT(*) FROM movie_contents m " +
                       "WHERE m.genres @> CAST(:genres AS text[])",
           nativeQuery = true)
    Page<MovieContent> findByGenresContainingAll(@Param("genres") String[] genres, Pageable pageable);
    
    /**
     * 플랫폼 필터링 (AND 조건) - 모든 플랫폼을 제공하는 영화만 반환
     * genres 필터링과 동일한 패턴
     */
    @Query(value = "SELECT m.* FROM movie_contents m " +
           "JOIN contents c ON m.content_id = c.content_id " +
           "WHERE m.platforms @> CAST(:platforms AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST, m.content_id ASC",
           countQuery = "SELECT COUNT(*) FROM movie_contents m " +
                       "WHERE m.platforms @> CAST(:platforms AS text[])",
           nativeQuery = true)
    Page<MovieContent> findByPlatformsContainingAll(@Param("platforms") String[] platforms, Pageable pageable);
    
    /**
     * 장르 + 플랫폼 복합 필터링
     */
    @Query(value = "SELECT m.* FROM movie_contents m " +
           "JOIN contents c ON m.content_id = c.content_id " +
           "WHERE m.genres @> CAST(:genres AS text[]) " +
           "AND m.platforms @> CAST(:platforms AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST, m.content_id ASC",
           countQuery = "SELECT COUNT(*) FROM movie_contents m " +
                       "WHERE m.genres @> CAST(:genres AS text[]) " +
                       "AND m.platforms @> CAST(:platforms AS text[])",
           nativeQuery = true)
    Page<MovieContent> findByGenresAndPlatforms(
        @Param("genres") String[] genres,
        @Param("platforms") String[] platforms,
        Pageable pageable
    );
    
    /**
     * Content ID 목록으로 조회
     */
    List<MovieContent> findByContentIdIn(List<Long> contentIds);

    /**
     * 장르별 작품 수 집계 (UNNEST + GROUP BY) — 테이블 전체 로드 없이 DB에서 카운트
     */
    @Query(value = "SELECT g AS genre, COUNT(*) AS cnt FROM movie_contents, UNNEST(genres) AS g " +
           "WHERE g IS NOT NULL AND g <> '' GROUP BY g ORDER BY cnt DESC", nativeQuery = true)
    List<Object[]> countByGenre();

    /**
     * 사용 중인 장르 목록 (중복 제거)
     */
    @Query(value = "SELECT DISTINCT g FROM movie_contents, UNNEST(genres) AS g " +
           "WHERE g IS NOT NULL AND g <> ''", nativeQuery = true)
    List<String> findDistinctGenres();
}
