package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.OTTPlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OTTPlatformMappingRepository extends JpaRepository<OTTPlatformMapping, Long> {

    // 넷플릭스 ID로 검색
    Optional<OTTPlatformMapping> findByNetflixId(Long netflixId);

    // 디즈니플러스 ID로 검색
    Optional<OTTPlatformMapping> findByDisneyPlusId(Long disneyPlusId);

    // 왓챠 ID로 검색
    Optional<OTTPlatformMapping> findByWatchaId(Long watchaId);

    // 웨이브 ID로 검색
    Optional<OTTPlatformMapping> findByWavveId(Long wavveId);

    // 넷플릭스 독점 콘텐츠들
    @Query("SELECT opm FROM OTTPlatformMapping opm WHERE opm.netflixId > 0 AND opm.disneyPlusId IS NULL AND opm.watchaId IS NULL AND opm.wavveId IS NULL")
    List<OTTPlatformMapping> findNetflixExclusives();

    // 멀티플랫폼 콘텐츠들
    @Query("SELECT opm FROM OTTPlatformMapping opm WHERE " +
            "(CASE WHEN opm.netflixId > 0 THEN 1 ELSE 0 END) + " +
            "(CASE WHEN opm.disneyPlusId > 0 THEN 1 ELSE 0 END) + " +
            "(CASE WHEN opm.watchaId > 0 THEN 1 ELSE 0 END) + " +
            "(CASE WHEN opm.wavveId > 0 THEN 1 ELSE 0 END) >= 2")
    List<OTTPlatformMapping> findMultiPlatformContent();
}
