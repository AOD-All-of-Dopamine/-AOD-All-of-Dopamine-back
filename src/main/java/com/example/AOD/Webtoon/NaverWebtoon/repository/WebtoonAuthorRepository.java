package com.example.AOD.Webtoon.NaverWebtoon.repository;

import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonAuthor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebtoonAuthorRepository extends JpaRepository<WebtoonAuthor,Long> {
    Optional<WebtoonAuthor> findByName(String name);
}
