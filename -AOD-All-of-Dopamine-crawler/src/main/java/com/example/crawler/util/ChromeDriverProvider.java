package com.example.crawler.util;


import io.github.bonigarcia.wdm.WebDriverManager;
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
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();

        options.addArguments("--headless");

        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--no-sandbox");
        options.addArguments("--mute-audio");
        options.addArguments("--disable-dev-shm-usage");
        return new ChromeDriver(options);
    }

}


