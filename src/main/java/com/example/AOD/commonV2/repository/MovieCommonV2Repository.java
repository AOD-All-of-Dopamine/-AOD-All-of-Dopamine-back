package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.MovieCommonV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovieCommonV2Repository extends JpaRepository<MovieCommonV2, Long> {

    // 제목으로 검색
    List<MovieCommonV2> findByTitleContainingIgnoreCase(String title);

    // 정확한 제목 매칭
    Optional<MovieCommonV2> findByTitleIgnoreCase(String title);

    // 감독으로 검색
    List<MovieCommonV2> findByDirectorContainingIgnoreCase(String director);

    // 배우로 검색
    @Query("SELECT m FROM MovieCommonV2 m WHERE :actor MEMBER OF m.actors")
    List<MovieCommonV2> findByActor(@Param("actor") String actor);

    // 장르로 검색
    @Query("SELECT m FROM MovieCommonV2 m WHERE :genre MEMBER OF m.genres")
    List<MovieCommonV2> findByGenre(@Param("genre") String genre);

    // 개봉일 범위로 검색
    List<MovieCommonV2> findByReleaseDateBetween(LocalDate startDate, LocalDate endDate);

    // 특정 연도 영화 검색
    @Query("SELECT m FROM MovieCommonV2 m WHERE YEAR(m.releaseDate) = :year")
    List<MovieCommonV2> findByReleaseYear(@Param("year") int year);

    // 평점 이상 검색
    List<MovieCommonV2> findByRatingGreaterThanEqual(Double rating);

    // 상영시간 범위 검색
    List<MovieCommonV2> findByRunningTimeBetween(Integer minTime, Integer maxTime);

    // 재개봉 영화 검색
    List<MovieCommonV2> findByIsRerelease(Boolean isRerelease);

    // 연령 등급으로 검색
    List<MovieCommonV2> findByAgeRating(String ageRating);

    // CGV에 있는 영화들 검색
    @Query("SELECT m FROM MovieCommonV2 m WHERE m.platformMapping.cgvId > 0")
    List<MovieCommonV2> findByCgvAvailable();

    // 특정 플랫폼 ID로 검색
    @Query("SELECT m FROM MovieCommonV2 m WHERE m.platformMapping.cgvId = :cgvId")
    Optional<MovieCommonV2> findByCgvId(@Param("cgvId") Long cgvId);

    // 페이징된 제목 검색
    Page<MovieCommonV2> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}