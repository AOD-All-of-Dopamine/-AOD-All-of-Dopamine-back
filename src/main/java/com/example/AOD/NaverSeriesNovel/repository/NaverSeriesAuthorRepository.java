package com.example.AOD.NaverSeriesNovel.repository;

import com.example.AOD.NaverSeriesNovel.domain.NaverSeriesNovel;
import com.example.AOD.NaverSeriesNovel.domain.NaverSeriesNovelAuthor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NaverSeriesAuthorRepository extends JpaRepository<NaverSeriesNovelAuthor, Long> {
    Optional<NaverSeriesNovelAuthor> findByName(String name);
}
