package com.example.AOD.Novel.NaverSeriesNovel.repository;

import com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovelAuthor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NaverSeriesAuthorRepository extends JpaRepository<NaverSeriesNovelAuthor, Long> {
    Optional<NaverSeriesNovelAuthor> findByName(String name);
}
