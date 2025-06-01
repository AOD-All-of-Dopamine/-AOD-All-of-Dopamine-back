package com.example.AOD.commonV2.repository;

import com.example.AOD.commonV2.domain.MoviePlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MoviePlatformMappingRepository extends JpaRepository<MoviePlatformMapping, Long> {

    // CGV ID로 검색
    Optional<MoviePlatformMapping> findByCgvId(Long cgvId);

    // 메가박스 ID로 검색
    Optional<MoviePlatformMapping> findByMegaboxId(Long megaboxId);

    // 롯데시네마 ID로 검색
    Optional<MoviePlatformMapping> findByLotteCinemaId(Long lotteCinemaId);

    // 특정 플랫폼에만 있는 영화들
    @Query("SELECT mpm FROM MoviePlatformMapping mpm WHERE mpm.cgvId > 0 AND mpm.megaboxId IS NULL AND mpm.lotteCinemaId IS NULL")
    List<MoviePlatformMapping> findCgvExclusives();

    // 모든 플랫폼에 있는 영화들
    @Query("SELECT mpm FROM MoviePlatformMapping mpm WHERE mpm.cgvId > 0 AND mpm.megaboxId > 0 AND mpm.lotteCinemaId > 0")
    List<MoviePlatformMapping> findAllPlatformMovies();
}
