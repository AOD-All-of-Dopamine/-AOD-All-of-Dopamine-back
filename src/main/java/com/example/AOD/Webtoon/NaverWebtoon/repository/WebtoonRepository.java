package com.example.AOD.Webtoon.NaverWebtoon.repository;

import com.example.AOD.Webtoon.NaverWebtoon.domain.Webtoon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebtoonRepository extends JpaRepository<Webtoon,Long> {
    Optional<Webtoon> findByUrl(String url);
}
