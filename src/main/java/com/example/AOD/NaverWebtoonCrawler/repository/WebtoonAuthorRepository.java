package com.example.AOD.NaverWebtoonCrawler.repository;

import com.example.AOD.NaverWebtoonCrawler.domain.WebtoonAuthor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebtoonAuthorRepository extends JpaRepository<WebtoonAuthor,Long> {
    Optional<WebtoonAuthor> findByName(String name);
}
