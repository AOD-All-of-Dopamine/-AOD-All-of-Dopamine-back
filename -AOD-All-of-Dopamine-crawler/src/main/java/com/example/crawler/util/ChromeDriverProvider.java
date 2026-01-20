package com.example.crawler.util;


import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;  // ✅ 추가
import org.springframework.beans.factory.annotation.Value;  // ✅ 이게 맞음!
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;  // ✅ 추가
import java.net.URL;  // ✅
import java.util.List;

@Slf4j
@Component
public class ChromeDriverProvider {

    /**
     * 🚀 앱 시작 시 1회만 ChromeDriver 설정
     * - 매번 setup() 호출 시 HTTP 클라이언트 스레드 누수 발생
     * - 75분간 462개 HTTP 클라이언트 생성 문제 해결
     */
    @PostConstruct
    public void setupChromeDriver() {
        log.info("🔧 ChromeDriver 초기 설정 시작 (1회만 실행)");
        WebDriverManager.chromedriver().setup();
        log.info("✅ ChromeDriver 설정 완료");
    }

    /*@Value("${SELENIUM_REMOTE_URL:}")
    private String seleniumRemoteUrl;

    public WebDriver getDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--mute-audio");
        options.setExperimentalOption("excludeSwitches", java.util.List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        if (seleniumRemoteUrl != null && !seleniumRemoteUrl.isEmpty()) {
            // ✅ Docker 환경: Remote WebDriver 사용
            try {
                log.info("Selenium Remote URL로 연결: {}", seleniumRemoteUrl);
                return new RemoteWebDriver(new URL(seleniumRemoteUrl), options);
            } catch (MalformedURLException e) {
                log.error("잘못된 Selenium URL: {}", seleniumRemoteUrl, e);
                throw new RuntimeException("잘못된 Selenium URL: " + seleniumRemoteUrl, e);
            }
        } else {
            // ✅ 로컬 환경: 기존 방식
            log.info("로컬 ChromeDriver 사용");
            WebDriverManager.chromedriver().setup();
            return new ChromeDriver(options);
        }
    }*/


    public ChromeDriverProvider() {
    }

    public WebDriver getDriver() {
        // 🚀 setup()는 @PostConstruct에서 1회만 실행됨
        // 매번 호출하지 않음!
        ChromeOptions options = new ChromeOptions();

        // 기본 옵션
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--mute-audio");
        
        // 🚀 메모리 최적화 옵션 (EC2 t3.small용)
        options.addArguments("--disable-gpu");              // GPU 비활성화
        options.addArguments("--disable-extensions");       // 확장 프로그램 비활성화
        options.addArguments("--disable-images");           // 이미지 로딩 비활성화
        options.addArguments("--blink-settings=imagesEnabled=false");  // Blink 엔진 이미지 비활성화
        options.addArguments("--disable-plugins");          // 플러그인 비활성화
        options.addArguments("--disable-software-rasterizer");  // 소프트웨어 래스터라이저 비활성화

        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        
        ChromeDriver driver = new ChromeDriver(options);
        
        // 🚀 핵심: Timeout 설정 (좀비 프로세스 방지!)
        // timeout 없이 driver.get() 호출 시 무한 대기 → quit() 도달 못 함 → 좀비 프로세스
        driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(java.time.Duration.ofSeconds(30));
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
        
        log.debug("ChromeDriver 생성 완료 (timeout 설정: pageLoad=30s, script=30s, implicit=10s)");
        
        return driver;
    }

}


