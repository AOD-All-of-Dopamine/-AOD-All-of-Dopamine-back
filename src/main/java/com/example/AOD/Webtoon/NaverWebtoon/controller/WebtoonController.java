package com.example.AOD.Webtoon.NaverWebtoon.controller;

import com.example.AOD.Webtoon.NaverWebtoon.crawler.NaverWebtoonCrawler;
import com.example.AOD.Webtoon.NaverWebtoon.domain.dto.NaverWebtoonDTO;
import com.example.AOD.Webtoon.NaverWebtoon.service.WebtoonService;
import com.example.AOD.util.ChromeDriverProvider;
import java.awt.AWTException;
import java.util.HashMap;
import java.util.Map;
import org.openqa.selenium.WebDriver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebtoonController {

    private final WebtoonService webtoonService;

    public WebtoonController(WebtoonService webtoonService) {
        this.webtoonService = webtoonService;
    }

    @GetMapping("/startCrawl")
    public ResponseEntity<Map<String,Object>> startCrawl() throws InterruptedException, AWTException {
        webtoonService.crawl();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "크롤링 작업이 백그라운드에서 실행 중입니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String,Object>> test() {
        NaverWebtoonCrawler crawler = new NaverWebtoonCrawler();
        ChromeDriverProvider chromeDriverProvider = new ChromeDriverProvider();
        WebDriver driver = chromeDriverProvider.getDriver();
        NaverWebtoonDTO webtoon = crawler.crawlWebtoonDetails("https://comic.naver.com/webtoon/list?titleId=758037&tab=mon",driver);
        webtoonService.saveWebtoon(webtoon);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

}
