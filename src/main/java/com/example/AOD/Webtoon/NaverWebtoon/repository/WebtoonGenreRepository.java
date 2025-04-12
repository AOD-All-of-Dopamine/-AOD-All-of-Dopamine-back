package com.example.AOD.Webtoon.NaverWebtoon.repository;

import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonGenre;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebtoonGenreRepository extends JpaRepository<WebtoonGenre, Long> {
    Optional<WebtoonGenre> findByGenre(String genre);
}
