package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.OTTCommonV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OTTCommonV2Repository extends JpaRepository<OTTCommonV2, Long> {

    // 제목으로 검색
    List<OTTCommonV2> findByTitleContainingIgnoreCase(String title);

    // 정확한 제목 매칭
    Optional<OTTCommonV2> findByTitleIgnoreCase(String title);

    // 타입으로 검색 (series, movie)
    List<OTTCommonV2> findByType(String type);

    // 크리에이터로 검색
    List<OTTCommonV2> findByCreatorContainingIgnoreCase(String creator);

    // 배우로 검색
    @Query("SELECT o FROM OTTCommonV2 o WHERE :actor MEMBER OF o.actors")
    List<OTTCommonV2> findByActor(@Param("actor") String actor);

    // 장르로 검색
    @Query("SELECT o FROM OTTCommonV2 o WHERE :genre MEMBER OF o.genres")
    List<OTTCommonV2> findByGenre(@Param("genre") String genre);

    // 특징으로 검색
    @Query("SELECT o FROM OTTCommonV2 o WHERE :feature MEMBER OF o.features")
    List<OTTCommonV2> findByFeature(@Param("feature") String feature);

    // 연도로 검색
    List<OTTCommonV2> findByReleaseYear(String releaseYear);

    // 연령 등급으로 검색
    List<OTTCommonV2> findByMaturityRating(String maturityRating);

    // 설명에서 키워드 검색
    List<OTTCommonV2> findByDescriptionContainingIgnoreCase(String keyword);

    // 넷플릭스에 있는 콘텐츠들 검색
    @Query("SELECT o FROM OTTCommonV2 o WHERE o.platformMapping.netflixId > 0")
    List<OTTCommonV2> findByNetflixAvailable();

    // 디즈니플러스에 있는 콘텐츠들 검색
    @Query("SELECT o FROM OTTCommonV2 o WHERE o.platformMapping.disneyPlusId > 0")
    List<OTTCommonV2> findByDisneyPlusAvailable();

    // 특정 플랫폼 ID로 검색
    @Query("SELECT o FROM OTTCommonV2 o WHERE o.platformMapping.netflixId = :netflixId")
    Optional<OTTCommonV2> findByNetflixId(@Param("netflixId") Long netflixId);

    @Query("SELECT o FROM OTTCommonV2 o WHERE o.platformMapping.watchaId = :watchaId")
    Optional<OTTCommonV2> findByWatchaId(@Param("watchaId") Long watchaId);

    // 페이징된 제목 검색
    Page<OTTCommonV2> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}