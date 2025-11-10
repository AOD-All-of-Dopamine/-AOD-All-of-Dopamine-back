package com.example.AOD.ranking.steam.fetcher;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SteamRankingFetcher {

    private static final String TOP_SELLERS_URL = "https://store.steampowered.com/charts/topsellers/KR";

    public Document fetchTopSellersPage() {
        log.info("Fetching Steam top sellers from: {} (using Selenium)", TOP_SELLERS_URL);
        
        WebDriver driver = null;
        try {
            // WebDriverManager로 ChromeDriver 자동 설정
            WebDriverManager.chromedriver().setup();
            
            // Chrome 옵션 설정
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless"); // 브라우저 창 띄우지 않기
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            options.addArguments("--lang=ko-KR");
            options.addArguments("--accept-language=ko-KR,ko;q=0.9");
            
            driver = new ChromeDriver(options);
            
            // 페이지 로드 전에 쿠키 설정을 위해 먼저 Steam 메인 페이지 방문
            driver.get("https://store.steampowered.com");
            
            // 언어 쿠키 설정
            org.openqa.selenium.Cookie langCookie = new org.openqa.selenium.Cookie("Steam_Language", "koreana");
            driver.manage().addCookie(langCookie);
            
            // 이제 차트 페이지 로드
            driver.get(TOP_SELLERS_URL);
            
            // JavaScript 렌더링 대기 (5초)
            Thread.sleep(5000);
            
            // 렌더링된 HTML을 Jsoup Document로 변환
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);
            
            log.info("Steam 페이지 로드 완료 (Selenium)");
            return doc;
            
        } catch (InterruptedException e) {
            log.error("Steam 페이지 로딩 중 인터럽트 발생: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("Steam 최고 판매 랭킹 페이지를 가져오는 중 오류 발생: url={}, error={}", TOP_SELLERS_URL, e.getMessage());
            return null;
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
}
