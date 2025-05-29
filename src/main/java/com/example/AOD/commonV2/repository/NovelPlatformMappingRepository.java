package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.NovelPlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NovelPlatformMappingRepository extends JpaRepository<NovelPlatformMapping, Long> {

    // 네이버시리즈 ID로 검색
    Optional<NovelPlatformMapping> findByNaverSeriesId(Long naverSeriesId);

    // 카카오페이지 ID로 검색
    Optional<NovelPlatformMapping> findByKakaoPageId(Long kakaoPageId);

    // 리디북스 ID로 검색
    Optional<NovelPlatformMapping> findByRidibooksId(Long ridibooksId);

    // 네이버시리즈 독점 소설들
    @Query("SELECT npm FROM NovelPlatformMapping npm WHERE npm.naverSeriesId > 0 AND npm.kakaoPageId IS NULL AND npm.ridibooksId IS NULL")
    List<NovelPlatformMapping> findNaverSeriesExclusives();

    // 멀티플랫폼 소설들
    @Query("SELECT npm FROM NovelPlatformMapping npm WHERE " +
            "(CASE WHEN npm.naverSeriesId > 0 THEN 1 ELSE 0 END) + " +
            "(CASE WHEN npm.kakaoPageId > 0 THEN 1 ELSE 0 END) + " +
            "(CASE WHEN npm.ridibooksId > 0 THEN 1 ELSE 0 END) >= 2")
    List<NovelPlatformMapping> findMultiPlatformNovels();
}