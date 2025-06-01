package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.NovelPlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NovelPlatformMappingRepository extends JpaRepository<NovelPlatformMapping, Long> {

    Optional<NovelPlatformMapping> findByNaverSeriesId(Long naverSeriesId);
    Optional<NovelPlatformMapping> findByKakaoPageId(Long kakaoPageId);
    Optional<NovelPlatformMapping> findByRidibooksId(Long ridibooksId);

    @Query("SELECT npm FROM NovelPlatformMapping npm WHERE npm.naverSeriesId > 0")
    List<NovelPlatformMapping> findByNaverSeriesAvailable();

    @Query("SELECT npm FROM NovelPlatformMapping npm WHERE npm.kakaoPageId > 0")
    List<NovelPlatformMapping> findByKakaoPageAvailable();

    @Query("SELECT npm FROM NovelPlatformMapping npm WHERE " +
            "(CASE WHEN npm.naverSeriesId > 0 THEN 1 ELSE 0 END) + " +
            "(CASE WHEN npm.kakaoPageId > 0 THEN 1 ELSE 0 END) + " +
            "(CASE WHEN npm.ridibooksId > 0 THEN 1 ELSE 0 END) >= 2")
    List<NovelPlatformMapping> findMultiPlatformNovels();
}
