package com.example.AOD.Webtoon.NaverWebtoon.service;

import com.example.AOD.Webtoon.NaverWebtoon.crawler.NaverWebtoonCrawler;
import com.example.AOD.Webtoon.NaverWebtoon.domain.Webtoon;
import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonAuthor;
import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonGenre;
import com.example.AOD.Webtoon.NaverWebtoon.domain.dto.NaverWebtoonDTO;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonAuthorRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonGenreRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
import com.example.AOD.util.ChromeDriverProvider;
import com.example.AOD.util.NaverLoginHandler;
import java.awt.AWTException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebtoonService {
    private final WebtoonRepository webtoonRepository;
    private final WebtoonGenreRepository webtoonGenreRepository;
    private final WebtoonAuthorRepository webtoonAuthorRepository;

    private final NaverWebtoonCrawler naverWebtoonCrawler;
    private final NaverLoginHandler naverLoginHandler;

    @Transactional
    public Webtoon saveWebtoon(NaverWebtoonDTO dto) {
        List<WebtoonAuthor> authors = dto.getAuthors().stream()
                .map(name -> webtoonAuthorRepository.findByName(name)
                        .orElseGet(() -> webtoonAuthorRepository.save(new WebtoonAuthor(name))))
                .collect(Collectors.toList());

        List<WebtoonGenre> genres = dto.getGenres().stream()
                .map(genre -> webtoonGenreRepository.findByGenre(genre)
                        .orElseGet(() -> webtoonGenreRepository.save(new WebtoonGenre(genre))))
                .collect(Collectors.toList());

        Webtoon webtoon = new Webtoon(dto);
        webtoon.setWebtoonAuthors(authors);
        webtoon.setWebtoonGenres(genres);

        return webtoonRepository.save(webtoon);
    }

    @Async
    public void crawl() throws InterruptedException {
        ChromeDriverProvider chromeDriverProvider = new ChromeDriverProvider();
        WebDriver driver = chromeDriverProvider.getDriver();

        naverLoginHandler.naverLogin(driver);
        log.debug("login success");

        ArrayList<NaverWebtoonDTO> naverWebtoonDTOS = naverWebtoonCrawler.crawlAllOngoingWebtoons(driver);

        log.debug("Webtoon DTO conversion success");
        for (NaverWebtoonDTO naverWebtoonDTO : naverWebtoonDTOS) {
            saveWebtoon(naverWebtoonDTO);
        }
        log.debug("Webtoon save complete!");

        driver.quit();
    }

    /**
     * 매일 자정에 실행되는 네이버 웹툰 신작 크롤링 메서드
     */
    @Async
    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정에 실행
    @Transactional
    public void crawlNewWebtoons() throws InterruptedException {
        ChromeDriverProvider chromeDriverProvider = new ChromeDriverProvider();
        WebDriver driver = chromeDriverProvider.getDriver();

        naverLoginHandler.naverLogin(driver);

        ArrayList<NaverWebtoonDTO> newWebtoons = naverWebtoonCrawler.crawlNewWebtoons(driver);
        log.debug("newWebtoons DTO conversion success : {} detected", newWebtoons.size());

        // 이미 저장된 웹툰은 제외하고 새로운 웹툰만 저장
        int savedCount = 0;
        for (NaverWebtoonDTO webtoonDTO : newWebtoons) {
            // URL로 기존 웹툰 확인
            Optional<Webtoon> existingWebtoon = webtoonRepository.findByUrl(webtoonDTO.getUrl());

            if (existingWebtoon.isEmpty()) {
                saveWebtoon(webtoonDTO);
                savedCount++;
            }
        }
        log.debug("newWebtoons save complete! : {} saved", savedCount);
        driver.quit();
    }

}
