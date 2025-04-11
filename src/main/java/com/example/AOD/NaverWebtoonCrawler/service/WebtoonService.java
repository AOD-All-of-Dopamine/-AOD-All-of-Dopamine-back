package com.example.AOD.NaverWebtoonCrawler.service;

import com.example.AOD.NaverWebtoonCrawler.crawler.NaverWebtoonCrawler;
import com.example.AOD.NaverWebtoonCrawler.domain.Webtoon;
import com.example.AOD.NaverWebtoonCrawler.domain.WebtoonAuthor;
import com.example.AOD.NaverWebtoonCrawler.domain.WebtoonGenre;
import com.example.AOD.NaverWebtoonCrawler.domain.dto.NaverWebtoonDTO;
import com.example.AOD.NaverWebtoonCrawler.repository.WebtoonAuthorRepository;
import com.example.AOD.NaverWebtoonCrawler.repository.WebtoonGenreRepository;
import com.example.AOD.NaverWebtoonCrawler.repository.WebtoonRepository;
import com.example.AOD.NaverWebtoonCrawler.util.ChromeDriverProvider;
import com.example.AOD.NaverWebtoonCrawler.util.NaverLoginHandler;
import java.awt.AWTException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
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
        System.out.println(id+" "+pw);
        loginHandler.naverLogin(driver, id, pw);
        //전부 DTO를 들고 한번에 처리하는걸 트랜잭션 개선이 필요해보임
        ArrayList<NaverWebtoonDTO> naverWebtoonDTOS = naverWebtoonCrawler.crawlAllOngoingWebtoons(driver);
        System.out.println("Save Webtoon Start");
        for (NaverWebtoonDTO naverWebtoonDTO : naverWebtoonDTOS) {
            saveWebtoon(naverWebtoonDTO);
        }
    }
}
