package com.example.AOD.naverWebtoonCrawler.service;

import java.awt.AWTException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.example.AOD.naverWebtoonCrawler.crawler.NaverWebtoonCrawler;
import com.example.AOD.naverWebtoonCrawler.domain.Webtoon;
import com.example.AOD.naverWebtoonCrawler.domain.WebtoonAuthor;
import com.example.AOD.naverWebtoonCrawler.domain.WebtoonGenre;
import com.example.AOD.naverWebtoonCrawler.domain.dto.NaverWebtoonDTO;
import com.example.AOD.naverWebtoonCrawler.repository.WebtoonAuthorRepository;
import com.example.AOD.naverWebtoonCrawler.repository.WebtoonGenreRepository;
import com.example.AOD.naverWebtoonCrawler.repository.WebtoonRepository;
import com.example.AOD.naverWebtoonCrawler.util.ChromeDriverProvider;
import com.example.AOD.naverWebtoonCrawler.util.NaverLoginHandler;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebtoonService {
    private final WebtoonRepository webtoonRepository;
    private final NaverWebtoonCrawler naverWebtoonCrawler;
    private final WebtoonGenreRepository webtoonGenreRepository;
    private final WebtoonAuthorRepository webtoonAuthorRepository;

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
    public void crawl() throws InterruptedException, AWTException {
        ChromeDriverProvider chromeDriverProvider = new ChromeDriverProvider();
        NaverLoginHandler loginHandler = new NaverLoginHandler();
        WebDriver driver = chromeDriverProvider.getDriver();


        String id = System.getenv("naverId");
        String pw = System.getenv("naverPw");
        loginHandler.naverLogin(driver, id, pw);

        ArrayList<NaverWebtoonDTO> naverWebtoonDTOS = naverWebtoonCrawler.crawlAllOngoingWebtoons(driver);
        System.out.println("Save Webtoon Start");
        for (NaverWebtoonDTO naverWebtoonDTO : naverWebtoonDTOS) {
            saveWebtoon(naverWebtoonDTO);
        }
    }
}
