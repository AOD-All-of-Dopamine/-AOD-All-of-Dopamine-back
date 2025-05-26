package com.example.AOD.Webtoon.NaverWebtoon.controller;

import com.example.AOD.Webtoon.NaverWebtoon.crawler.NaverWebtoonCrawler;
import com.example.AOD.Webtoon.NaverWebtoon.domain.dto.NaverWebtoonDTO;
import com.example.AOD.Webtoon.NaverWebtoon.service.WebtoonService;
import com.example.AOD.util.ChromeDriverProvider;
import java.awt.AWTException;
import java.util.HashMap;
import java.util.Map;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webtoon/naver")
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

    @GetMapping("/startNewWebtoonCrawl")
    public ResponseEntity<Map<String,Object>> startNewWebtoonCrawl() throws InterruptedException, AWTException {
        webtoonService.crawlNewWebtoons();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "신작 웹툰 크롤링 작업이 백그라운드에서 실행 중입니다.");
        return ResponseEntity.ok(response);
    }

}
