package com.example.AOD.movie.runner;

import com.example.AOD.movie.crawler.CgvCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MovieCrawlerRunner implements CommandLineRunner {

    private final CgvCrawler cgvCrawler;

    @Override
    public void run(String... args) {
        log.info("영화 크롤링 실행기 시작");

        // 최근 영화만 크롤링 (전체 크롤링은 주석을 해제하여 사용)
        // cgvCrawler.crawlAll(); // 주의: 첫 실행 시에만 사용 (모든 영화를 크롤링)

        cgvCrawler.crawlRecent(); // 최신 영화만 크롤링

        log.info("영화 크롤링 실행기 완료");
    }
}