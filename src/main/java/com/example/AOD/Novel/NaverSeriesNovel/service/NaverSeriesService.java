package com.example.AOD.Novel.NaverSeriesNovel.service;

import com.example.AOD.Novel.NaverSeriesNovel.crawler.NaverSeriesCrawler;
import com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovel;
import com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovelAuthor;
import com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovelGenre;
import com.example.AOD.Novel.NaverSeriesNovel.dto.NaverSeriesNovelDTO;
import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesAuthorRepository;
import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelGenreRepository;
import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;

import com.example.AOD.util.ChromeDriverProvider;
import com.example.AOD.Webtoon.NaverWebtoon.util.NaverLoginHandler;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NaverSeriesService {

    private final NaverSeriesCrawler crawler;
    private final NaverSeriesAuthorRepository authorRepository;
    private final NaverSeriesNovelRepository novelRepository;
    private final NaverSeriesNovelGenreRepository naverSeriesNovelGenreRepository;

    public NaverSeriesNovel saveNovel(NaverSeriesNovelDTO novelDTO) {
        // 작가 처리 - 현재는 단일 작가지만 리스트로 변환하여 처리
        List<NaverSeriesNovelAuthor> authors = new ArrayList<>();

        if (novelDTO.getAuthor() != null && !novelDTO.getAuthor().isEmpty()) {
            NaverSeriesNovelAuthor author = authorRepository.findByName(novelDTO.getAuthor())
                    .orElseGet(() -> {
                        NaverSeriesNovelAuthor newAuthor = new NaverSeriesNovelAuthor();
                        newAuthor.setName(novelDTO.getAuthor());
                        return authorRepository.save(newAuthor);
                    });
            authors.add(author);
        }

        // 장르 처리 (기존 코드 유지)
        List<NaverSeriesNovelGenre> genres = novelDTO.getGenres().stream()
                .map(genre -> naverSeriesNovelGenreRepository.findByName(genre)
                        .orElseGet(() -> naverSeriesNovelGenreRepository.save(new NaverSeriesNovelGenre(genre))))
                .collect(Collectors.toList());

        NaverSeriesNovel novel = new NaverSeriesNovel(novelDTO);
        novel.setGenres(genres);
        novel.setAuthors(authors);  // 복수 작가 설정

        return novelRepository.save(novel);
    }


    /*@Async
    public void NaverSeriesNovelCrawl() throws InterruptedException, AWTException {
        ChromeDriverProvider chromeDriverProvider = new ChromeDriverProvider();
        NaverLoginHandler loginHandler = new NaverLoginHandler();
        WebDriver driver = chromeDriverProvider.getDriver();

        String id = System.getenv("naverId");
        String pw = System.getenv("naverPw");
        System.out.println(id+" "+pw);

        loginHandler.naverLogin(driver, id, pw);
        String cookieString = loginHandler.getCookieString(driver);

        // 로그인 후 WebDriver를 활용해 쿠키를 획득한 상태로 크롤링 실행
        List<NaverSeriesNovelDTO> dtos = crawler.crawlRecentNovels(cookieString);
        System.out.println("크롤링 완료 - 저장 시작");

        // 크롤링된 각 DTO를 도메인 엔티티로 변환 후 저장
        for (NaverSeriesNovelDTO dto : dtos) {
            saveNovel(dto);
        }

        // 작업 완료 후 드라이버 종료
        driver.quit();
    }*/

    /**
     * 공통 크롤링 메서드 - URL을 매개변수로 받아 해당 URL의 소설을 크롤링
     */
    @Async
    public void crawlNovelsByUrl(String baseUrl, String logMessage) throws InterruptedException, AWTException {
        ChromeDriverProvider chromeDriverProvider = new ChromeDriverProvider();
        NaverLoginHandler loginHandler = new NaverLoginHandler();
        WebDriver driver = chromeDriverProvider.getDriver();

        String id = System.getenv("naverId");
        String pw = System.getenv("naverPw");
        System.out.println(id + " " + pw);

        loginHandler.naverLogin(driver, id, pw);
        String cookieString = loginHandler.getCookieString(driver);

        // 로그인 후 WebDriver를 활용해 쿠키를 획득한 상태로 크롤링 실행
        List<NaverSeriesNovelDTO> dtos;

        if (baseUrl.contains("recentList")) {
            dtos = crawler.crawlRecentNovels(cookieString);
        } else if (baseUrl.contains("categoryProductList")) {
            dtos = crawler.crawlAllNovels(cookieString);
        } else {
            // 기본적으로 URL을 직접 크롤링
            dtos = crawler.crawlNovels(baseUrl, cookieString);
        }

        System.out.println(logMessage + " - 저장 시작");

        // 크롤링된 각 DTO를 도메인 엔티티로 변환 후 저장
        for (NaverSeriesNovelDTO dto : dtos) {
            saveNovel(dto);
        }

        // 작업 완료 후 드라이버 종료
        driver.quit();
    }

    /**
     * 기존 최신 소설 크롤링 메서드 - 공통 메서드 호출
     */
    @Async
    public void NaverSeriesNovelCrawl() throws InterruptedException, AWTException {
        String recentListUrl = "https://series.naver.com/novel/recentList.series";
        crawlNovelsByUrl(recentListUrl, "최신 소설 크롤링 완료");
    }



    /**
     * 전체 소설 크롤링 메서드 - 공통 메서드 호출
     */
    @Async
    public void crawlAllNaverSeriesNovels() throws InterruptedException, AWTException {
        String allListUrl = "https://series.naver.com/novel/categoryProductList.series?categoryTypeCode=all";
        crawlNovelsByUrl(allListUrl, "전체 소설 크롤링 완료");
    }
}
