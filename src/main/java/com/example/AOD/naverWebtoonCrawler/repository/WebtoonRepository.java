package com.example.AOD.naverWebtoonCrawler.repository;

import com.example.AOD.naverWebtoonCrawler.domain.Webtoon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebtoonRepository extends JpaRepository<Webtoon,Long> {
}
