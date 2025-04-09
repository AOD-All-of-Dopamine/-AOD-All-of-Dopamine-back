package com.example.AOD.naverWebtoonCrawler.repository;

import java.util.Optional;
import com.example.AOD.naverWebtoonCrawler.domain.WebtoonAuthor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebtoonAuthorRepository extends JpaRepository<WebtoonAuthor,Long> {
    Optional<WebtoonAuthor> findByName(String name);
}
