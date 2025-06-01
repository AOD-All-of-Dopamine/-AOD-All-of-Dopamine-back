package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.MoviePlatformMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MoviePlatformMappingRepository extends JpaRepository<MoviePlatformMapping, Long> {

    Optional<MoviePlatformMapping> findByCgvId(Long cgvId);
    Optional<MoviePlatformMapping> findByMegaboxId(Long megaboxId);
    Optional<MoviePlatformMapping> findByLotteCinemaId(Long lotteCinemaId);

    @Query("SELECT mpm FROM MoviePlatformMapping mpm WHERE mpm.cgvId > 0")
    List<MoviePlatformMapping> findByCgvAvailable();

    @Query("SELECT mpm FROM MoviePlatformMapping mpm WHERE mpm.cgvId > 0 AND mpm.megaboxId IS NULL AND mpm.lotteCinemaId IS NULL")
    List<MoviePlatformMapping> findCgvExclusives();

    @Query("SELECT mpm FROM MoviePlatformMapping mpm WHERE mpm.cgvId > 0 AND mpm.megaboxId > 0 AND mpm.lotteCinemaId > 0")
    List<MoviePlatformMapping> findAllPlatformMovies();
}
