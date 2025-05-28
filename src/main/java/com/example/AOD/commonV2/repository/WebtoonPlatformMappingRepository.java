package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.WebtoonPlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebtoonPlatformMappingRepository extends JpaRepository<WebtoonPlatformMapping, Long> {

    // 네이버웹툰 ID로 검색
    Optional<WebtoonPlatformMapping> findByNaverId(Long naverId);

    // 카카오웹툰 ID로 검색
    Optional<WebtoonPlatformMapping> findByKakaoId(Long kakaoId);

    // 네이버웹툰 독점 웹툰들
    @Query("SELECT wpm FROM WebtoonPlatformMapping wpm WHERE wpm.naverId > 0 AND wpm.kakaoId IS NULL")
    List<WebtoonPlatformMapping> findNaverExclusives();

    // 카카오웹툰 독점 웹툰들
    @Query("SELECT wpm FROM WebtoonPlatformMapping wpm WHERE wpm.kakaoId > 0 AND wpm.naverId IS NULL")
    List<WebtoonPlatformMapping> findKakaoExclusives();

    // 멀티플랫폼 웹툰들 (네이버+카카오 동시 연재)
    @Query("SELECT wpm FROM WebtoonPlatformMapping wpm WHERE wpm.naverId > 0 AND wpm.kakaoId > 0")
    List<WebtoonPlatformMapping> findMultiPlatformWebtoons();
}
