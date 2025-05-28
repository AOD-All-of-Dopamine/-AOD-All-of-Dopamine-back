package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.WebtoonCommonV2;
import com.example.AOD.Webtoon.NaverWebtoon.domain.Days;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebtoonCommonV2Repository extends JpaRepository<WebtoonCommonV2, Long> {

    // 제목으로 검색
    List<WebtoonCommonV2> findByTitleContainingIgnoreCase(String title);

    // 정확한 제목 매칭
    Optional<WebtoonCommonV2> findByTitleIgnoreCase(String title);

    // 작가로 검색
    @Query("SELECT w FROM WebtoonCommonV2 w WHERE :author MEMBER OF w.authors")
    List<WebtoonCommonV2> findByAuthor(@Param("author") String author);

    // 장르로 검색
    @Query("SELECT w FROM WebtoonCommonV2 w WHERE :genre MEMBER OF w.genres")
    List<WebtoonCommonV2> findByGenre(@Param("genre") String genre);

    // 업로드 요일로 검색
    @Query("SELECT w FROM WebtoonCommonV2 w WHERE :day MEMBER OF w.uploadDays")
    List<WebtoonCommonV2> findByUploadDay(@Param("day") Days day);

    // 요약에서 키워드 검색
    List<WebtoonCommonV2> findBySummaryContainingIgnoreCase(String keyword);

    // 발행일 범위로 검색
    List<WebtoonCommonV2> findByPublishDateBetween(String startDate, String endDate);

    // 네이버웹툰에 있는 웹툰들 검색
    @Query("SELECT w FROM WebtoonCommonV2 w WHERE w.platformMapping.naverId > 0")
    List<WebtoonCommonV2> findByNaverAvailable();

    // 카카오웹툰에 있는 웹툰들 검색
    @Query("SELECT w FROM WebtoonCommonV2 w WHERE w.platformMapping.kakaoId > 0")
    List<WebtoonCommonV2> findByKakaoAvailable();

    // 특정 플랫폼 ID로 검색
    @Query("SELECT w FROM WebtoonCommonV2 w WHERE w.platformMapping.naverId = :naverId")
    Optional<WebtoonCommonV2> findByNaverId(@Param("naverId") Long naverId);

    @Query("SELECT w FROM WebtoonCommonV2 w WHERE w.platformMapping.kakaoId = :kakaoId")
    Optional<WebtoonCommonV2> findByKakaoId(@Param("kakaoId") Long kakaoId);

    // 페이징된 제목 검색
    Page<WebtoonCommonV2> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}