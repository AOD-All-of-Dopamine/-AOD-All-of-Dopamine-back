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
     * 장르 필터링 (AND 조건) - 모든 장르를 포함하는 웹툰만 반환
     * PostgreSQL 배열 연산자 @> 사용 (contains)
     */
    @Query(value = "SELECT w.* FROM webtoon_contents w " +
           "WHERE w.genres @> CAST(:genres AS text[]) " +
           "ORDER BY w.content_id",
           countQuery = "SELECT COUNT(*) FROM webtoon_contents w " +
                       "WHERE w.genres @> CAST(:genres AS text[])",
           nativeQuery = true)
    Page<WebtoonContent> findByGenresContainingAll(@Param("genres") String[] genres, Pageable pageable);
    
    /**
     * Author로 웹툰 작품 검색 (중복 탐지용)
     */
    @Query("SELECT wc FROM WebtoonContent wc WHERE wc.author = :author")
    List<WebtoonContent> findByAuthor(@Param("author") String author);
    
    /**
     * Content ID 목록으로 조회
     */
    List<WebtoonContent> findByContentIdIn(List<Long> contentIds);
}