package com.example.AOD.Novel.NaverSeriesNovel.repository;

import com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovelGenre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NaverSeriesNovelGenreRepository extends JpaRepository<NaverSeriesNovelGenre, Long> {
    Optional<NaverSeriesNovelGenre> findByName(String Name);
}
