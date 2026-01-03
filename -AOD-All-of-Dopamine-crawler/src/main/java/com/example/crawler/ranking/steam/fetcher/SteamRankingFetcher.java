package com.example.crawler.ranking.steam.fetcher;

import com.example.crawler.ranking.steam.parser.SteamRankingParser;
import com.example.crawler.util.ChromeDriverProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Steam 랭킹 페이지 Fetcher (Selenium 사용)
 * - 한국 Top Sellers 차트 페이지 크롤링
 * - ChromeDriverProvider를 통해 Headless Chrome 사용
 * - 파싱은 SteamRankingParser에 위임 (SRP 준수)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamRankingFetcher {

    private static final long PAGE_LOAD_WAIT_MS = 5000; // 5초 대기
    private static final String STEAM_MAIN_URL = "https://store.steampowered.com";
    private static final String TOP_SELLERS_URL = "https://store.steampowered.com/charts/topsellers/KR";

    private final SteamRankingParser steamRankingParser;
    private final ChromeDriverProvider chromeDriverProvider;

    /**
     * Steam Top Sellers 페이지에서 게임 데이터 가져오기
     * @return Steam 게임 데이터 리스트
     */
    public List<SteamRankingParser.SteamGameData> fetchTopSellers() {
        log.info("Fetching Steam Korea top sellers from: {} (using Selenium)", TOP_SELLERS_URL);

        WebDriver driver = null;
        try {
            driver = chromeDriverProvider.getDriver();
            setupSteamLanguage(driver);

            // 차트 페이지 로드
            driver.get(TOP_SELLERS_URL);

            // JavaScript 렌더링 대기
            Thread.sleep(PAGE_LOAD_WAIT_MS);

            // 렌더링된 HTML을 Jsoup Document로 변환
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);

            log.info("Steam 페이지 로드 완료 (Selenium)");

            // HTML 파싱은 Parser에 위임
            return steamRankingParser.parseRankings(doc);

        } catch (InterruptedException e) {
            log.error("Steam 페이지 로딩 중 인터럽트 발생: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Steam Top Sellers 페이지를 가져오는 중 오류 발생: url={}, error={}", TOP_SELLERS_URL, e.getMessage(), e);
            return new ArrayList<>();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("WebDriver 종료 중 오류: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Steam 언어 설정 (쿠키)
     */
    private void setupSteamLanguage(WebDriver driver) {
        // 페이지 로드 전에 쿠키 설정을 위해 먼저 Steam 메인 페이지 방문
        driver.get(STEAM_MAIN_URL);
        
        // 언어 쿠키 설정
        Cookie langCookie = new Cookie("Steam_Language", "koreana");
        driver.manage().addCookie(langCookie);
        
        log.debug("Steam 언어 쿠키 설정 완료: koreana");
    }
}

