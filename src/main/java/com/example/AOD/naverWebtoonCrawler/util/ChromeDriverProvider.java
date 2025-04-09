package com.example.AOD.naverWebtoonCrawler.util;

import java.util.List;
import lombok.Getter;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

@Component
public class ChromeDriverProvider {

    private String driver_path = "C:\\Users\\oms01\\OneDrive\\바탕 화면\\Develop\\projects\\webtoon\\src\\main\\resources\\static\\chromedriver.exe";
    private final String driver_id = "webdriver.chrome.driver";

    public ChromeDriverProvider() {
    }

    public WebDriver getDriver() {
        System.setProperty(driver_id, driver_path);
        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--headless"); 네이버 로그인 할라면 창 띄워서 하는 방법밖에 없음..
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        return new ChromeDriver(options);
    }




}
