package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.NovelCommonV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NovelCommonV2Repository extends JpaRepository<NovelCommonV2, Long> {

    // 제목으로 검색
    List<NovelCommonV2> findByTitleContainingIgnoreCase(String title);

    // 정확한 제목 매칭
    Optional<NovelCommonV2> findByTitleIgnoreCase(String title);

    // 작가로 검색
    @Query("SELECT n FROM NovelCommonV2 n WHERE :author MEMBER OF n.authors")
    List<NovelCommonV2> findByAuthor(@Param("author") String author);

    // 장르로 검색
    @Query("SELECT n FROM NovelCommonV2 n WHERE :genre MEMBER OF n.genres")
    List<NovelCommonV2> findByGenre(@Param("genre") String genre);

    // 연재 상태로 검색
    List<NovelCommonV2> findByStatus(String status);

    // 출판사로 검색
    List<NovelCommonV2> findByPublisherContainingIgnoreCase(String publisher);

    // 연령 등급으로 검색
    List<NovelCommonV2> findByAgeRating(String ageRating);

    // 네이버시리즈에 있는 소설들 검색
    @Query("SELECT n FROM NovelCommonV2 n WHERE n.platformMapping.naverSeriesId > 0")
    List<NovelCommonV2> findByNaverSeriesAvailable();

    // 카카오페이지에 있는 소설들 검색
    @Query("SELECT n FROM NovelCommonV2 n WHERE n.platformMapping.kakaoPageId > 0")
    List<NovelCommonV2> findByKakaoPageAvailable();

    // 특정 플랫폼 ID로 검색
    @Query("SELECT n FROM NovelCommonV2 n WHERE n.platformMapping.naverSeriesId = :naverSeriesId")
    Optional<NovelCommonV2> findByNaverSeriesId(@Param("naverSeriesId") Long naverSeriesId);

    @Query("SELECT n FROM NovelCommonV2 n WHERE n.platformMapping.kakaoPageId = :kakaoPageId")
    Optional<NovelCommonV2> findByKakaoPageId(@Param("kakaoPageId") Long kakaoPageId);

    // 페이징된 제목 검색
    Page<NovelCommonV2> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}

