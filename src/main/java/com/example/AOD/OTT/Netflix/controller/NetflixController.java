package com.example.AOD.OTT.Netflix.controller;

import com.example.AOD.OTT.Netflix.service.NetflixCrawlerService;
import com.example.AOD.util.ChromeDriverProvider;
import com.example.AOD.util.NetflixLoginHandler;
import java.util.function.Function;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/netflix")
@RequiredArgsConstructor
@Slf4j
public class NetflixController {

    private final NetflixCrawlerService netflixCrawlerService;

    /**
     * 일반 콘텐츠 크롤링 시작
     */
    @GetMapping("/crawl/regular")
    public ResponseEntity<Map<String, Object>> startRegularCrawl() {
        try{
            netflixCrawlerService.runCrawler();
            return createSuccessResponse("일반 넷플릭스 콘텐츠 크롤링이 백그라운드에서 시작되었습니다.");
        }catch (Exception e){
            return createErrorResponse("일반 넷플릭스 콘텐츠 크롤링을 실패했습니다.: "+e.getMessage());
        }

    }

    /**
     * 최신 콘텐츠 크롤링 시작
     */
    @GetMapping("/crawl/latest")
    public ResponseEntity<Map<String, Object>> startLatestCrawl() {
        try{
            netflixCrawlerService.runLatestContentCrawler();
            return createSuccessResponse("최신 넷플릭스 콘텐츠 크롤링이 백그라운드에서 시작되었습니다.");
        }catch (Exception e){
            return createErrorResponse("최신 넷플릭스 콘텐츠 크롤링을 실패했습니다.: "+e.getMessage());
        }

    }

    /**
     * 모든 콘텐츠 크롤링 (일반 + 최신)
     */
    @GetMapping("/crawl/all")
    public ResponseEntity<Map<String, Object>> startAllCrawl() {
        try{
            netflixCrawlerService.runAllContentCrawler();
            return createSuccessResponse("모든 넷플릭스 콘텐츠 크롤링이 백그라운드에서 시작되었습니다.");
        }catch (Exception e){
            return createErrorResponse("모든 공개 넷플릭스 콘텐츠 크롤링을 실패했습니다.: "+e.getMessage());
        }
    }


    /**
     * 이번주 공개 콘텐츠만 크롤링 시작
     */
    @GetMapping("/crawl/this-week")
    public ResponseEntity<Map<String, Object>> startThisWeekCrawl() {
        try{
            netflixCrawlerService.runThisWeekContentCrawler();
            return createSuccessResponse("이번주 공개 넷플릭스 콘텐츠 크롤링이 백그라운드에서 시작되었습니다.");
        }catch (Exception e){
            return createErrorResponse("이번주 공개 넷플릭스 콘텐츠 크롤링을 실패했습니다.: "+e.getMessage());
        }
    }

    /**
     * 이전 버전 호환을 위한 엔드포인트
     */
    @GetMapping("/startCrawl")
    public ResponseEntity<Map<String, Object>> startCrawlLegacy() {
        return startAllCrawl();
    }


    /**
     * 성공 응답 생성
     */
    private ResponseEntity<Map<String, Object>> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * 에러 응답 생성
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return ResponseEntity.badRequest().body(response);
    }
}