package com.example.AOD.NaverWebtoonCrawler.repository;

import com.example.AOD.NaverWebtoonCrawler.domain.Webtoon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebtoonRepository extends JpaRepository<Webtoon,Long> {
}
