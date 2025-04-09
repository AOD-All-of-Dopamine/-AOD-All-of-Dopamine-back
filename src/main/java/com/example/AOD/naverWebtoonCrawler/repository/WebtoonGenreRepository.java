package com.example.AOD.naverWebtoonCrawler.repository;

import java.util.Optional;
import com.example.AOD.naverWebtoonCrawler.domain.WebtoonGenre;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebtoonGenreRepository extends JpaRepository<WebtoonGenre, Long> {
    Optional<WebtoonGenre> findByGenre(String genre);
}
