package com.example.crawler.ranking.steam.fetcher;

import com.example.crawler.ranking.steam.parser.SteamRankingParser;
import com.example.crawler.util.ChromeDriverProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Steam 랭킹 페이지 Fetcher (Selenium 사용)
 * - Steam 검색 페이지(Top Sellers 필터)에서 게임만 크롤링 (DLC 제외)
 * - ChromeDriverProvider를 통해 Headless Chrome 사용
 * - 무한 스크롤로 100개 이상 로딩
 * - 파싱은 SteamRankingParser에 위임 (SRP 준수)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamRankingFetcher {

    private static final long PAGE_LOAD_WAIT_MS = 5000; // 초기 페이지 로딩 대기
    private static final long SCROLL_WAIT_MS = 2000; // 스크롤 후 AJAX 로딩 대기
    private static final int MAX_SCROLL_ATTEMPTS = 3; // 최대 스크롤 횟수 (25개 × 4 = 100개)
    private static final int TARGET_ITEM_COUNT = 110; // 목표 로딩 수 (100개 + 여유분)
    private static final String STEAM_MAIN_URL = "https://store.steampowered.com";
    // category1=998: 게임만 (DLC, 사운드트랙 등 제외)
    private static final String TOP_SELLERS_URL = "https://store.steampowered.com/search/?filter=topsellers&category1=998";

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

            // 검색 페이지 로드
            driver.get(TOP_SELLERS_URL);

            // 초기 페이지 렌더링 대기
            Thread.sleep(PAGE_LOAD_WAIT_MS);

            // 무한 스크롤로 100개 이상의 결과 로딩
            scrollToLoadItems(driver);

            // 렌더링된 HTML을 Jsoup Document로 변환
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);

            log.info("Steam 검색 페이지 로드 완료 (Selenium)");

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

    /**
     * 무한 스크롤로 충분한 수의 검색 결과를 로딩
     * Steam 검색 페이지는 초기 25개를 표시하고 스크롤 시 25개씩 추가 로딩
     */
    private void scrollToLoadItems(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        for (int i = 0; i < MAX_SCROLL_ATTEMPTS; i++) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(SCROLL_WAIT_MS);

            Long currentCount = (Long) js.executeScript(
                    "return document.querySelectorAll('#search_resultsRows a.search_result_row').length;");
            log.info("스크롤 {}/{}: 현재 {}개 항목 로드됨", i + 1, MAX_SCROLL_ATTEMPTS, currentCount);

            if (currentCount != null && currentCount >= TARGET_ITEM_COUNT) {
                log.info("목표 항목 수({})에 도달하여 스크롤을 중단합니다.", TARGET_ITEM_COUNT);
                break;
            }
        }
    }
}

