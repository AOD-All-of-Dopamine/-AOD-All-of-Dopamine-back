package com.example.shared.repository;

import com.example.shared.entity.WebtoonContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WebtoonContentRepository extends JpaRepository<WebtoonContent, Long> {
    
    /**
     * 플랫폼 필터링 (AND 조건) - 모든 플랫폼을 제공하는 웹툰만 반환
     * genres 필터링과 동일한 패턴
     */
    @Query(value = "SELECT w.* FROM webtoon_contents w " +
           "JOIN contents c ON w.content_id = c.content_id " +
           "WHERE w.platforms @> CAST(:platforms AS text[]) " +
           "ORDER BY c.release_date DESC NULLS LAST, w.content_id ASC",
           countQuery = "SELECT COUNT(*) FROM webtoon_contents w " +
                       "WHERE w.platforms @> CAST(:platforms AS text[])",
           nativeQuery = true)
    Page<WebtoonContent> findByPlatformsContainingAll(@Param("platforms") String[] platforms, Pageable pageable);
    
    /**
     * Author로 웹툰 작품 검색 (중복 탐지용)
     */
    @Query("SELECT wc FROM WebtoonContent wc WHERE wc.author = :author")
    List<WebtoonContent> findByAuthor(@Param("author") String author);
    
    /**
     * 통합 필터 조회: 장르(contents.genres, 2026-07 승격)/플랫폼(@>, AND) + 키워드(ILIKE). 각 파라미터가 null이면 해당 조건 무시.
     */
    @Query(value = "SELECT d.* FROM webtoon_contents d JOIN contents c ON d.content_id = c.content_id " +
           "WHERE (CAST(:genres AS text[]) IS NULL OR c.genres @> CAST(:genres AS text[])) " +
           "AND (CAST(:platforms AS text[]) IS NULL OR d.platforms @> CAST(:platforms AS text[])) " +
           "AND (CAST(:keyword AS text) IS NULL OR c.master_title ILIKE ('%' || :keyword || '%') OR c.original_title ILIKE ('%' || :keyword || '%')) " +
           "ORDER BY c.release_date DESC NULLS LAST, d.content_id ASC",
           countQuery = "SELECT COUNT(*) FROM webtoon_contents d JOIN contents c ON d.content_id = c.content_id " +
           "WHERE (CAST(:genres AS text[]) IS NULL OR c.genres @> CAST(:genres AS text[])) " +
           "AND (CAST(:platforms AS text[]) IS NULL OR d.platforms @> CAST(:platforms AS text[])) " +
           "AND (CAST(:keyword AS text) IS NULL OR c.master_title ILIKE ('%' || :keyword || '%') OR c.original_title ILIKE ('%' || :keyword || '%'))",
           nativeQuery = true)
    Page<WebtoonContent> findWorks(@Param("genres") String[] genres,
                                   @Param("platforms") String[] platforms,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    /**
     * Content ID 목록으로 조회
     */
    List<WebtoonContent> findByContentIdIn(List<Long> contentIds);

}