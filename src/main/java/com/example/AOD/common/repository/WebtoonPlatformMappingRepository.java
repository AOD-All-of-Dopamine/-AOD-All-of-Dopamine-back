package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.WebtoonPlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebtoonPlatformMappingRepository extends JpaRepository<WebtoonPlatformMapping, Long> {

    Optional<WebtoonPlatformMapping> findByNaverId(Long naverId);
    Optional<WebtoonPlatformMapping> findByKakaoId(Long kakaoId);

    @Query("SELECT wpm FROM WebtoonPlatformMapping wpm WHERE wpm.naverId > 0")
    List<WebtoonPlatformMapping> findByNaverAvailable();

    @Query("SELECT wpm FROM WebtoonPlatformMapping wpm WHERE wpm.naverId > 0 AND wpm.kakaoId > 0")
    List<WebtoonPlatformMapping> findMultiPlatformWebtoons();
}
