package com.example.AOD.OTT.Netflix.controller;

import com.example.AOD.OTT.Netflix.service.NetflixCrawlerService;
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
public class NetflixController {
    private static final Logger logger = LoggerFactory.getLogger(NetflixController.class);

    private final NetflixCrawlerService netflixCrawlerService;

    public NetflixController(NetflixCrawlerService netflixCrawlerService) {
        this.netflixCrawlerService = netflixCrawlerService;
    }

    /**
     * 일반 콘텐츠 크롤링 시작
     */
    @GetMapping("/crawl/regular")
    public ResponseEntity<Map<String, Object>> startRegularCrawl() {
        logger.info("일반 넷플릭스 콘텐츠 크롤링 요청 받음");

        // 환경 변수에서 인증 정보 가져오기
        String email = System.getenv("NETFLIX_EMAIL");
        String password = System.getenv("NETFLIX_PASSWORD");

        // 인증 정보 검증
        if (isInvalidCredentials(email, password)) {
            return createErrorResponse("넷플릭스 인증 정보가 유효하지 않습니다. 환경 변수를 확인해주세요.");
        }

        // 비동기 크롤링 시작
        netflixCrawlerService.runCrawler(email, password);

        return createSuccessResponse("일반 넷플릭스 콘텐츠 크롤링이 백그라운드에서 시작되었습니다.");
    }

    /**
     * 최신 콘텐츠 크롤링 시작
     */
    @GetMapping("/crawl/latest")
    public ResponseEntity<Map<String, Object>> startLatestCrawl() {
        logger.info("최신 넷플릭스 콘텐츠 크롤링 요청 받음");

        // 환경 변수에서 인증 정보 가져오기
        String email = System.getenv("NETFLIX_EMAIL");
        String password = System.getenv("NETFLIX_PASSWORD");

        // 인증 정보 검증
        if (isInvalidCredentials(email, password)) {
            return createErrorResponse("넷플릭스 인증 정보가 유효하지 않습니다. 환경 변수를 확인해주세요.");
        }

        // 최신 콘텐츠 크롤링 시작
        netflixCrawlerService.runLatestContentCrawler(email, password);

        return createSuccessResponse("최신 넷플릭스 콘텐츠 크롤링이 백그라운드에서 시작되었습니다.");
    }

    /**
     * 모든 콘텐츠 크롤링 (일반 + 최신)
     */
    @GetMapping("/crawl/all")
    public ResponseEntity<Map<String, Object>> startAllCrawl() {
        logger.info("모든 넷플릭스 콘텐츠 크롤링 요청 받음");

        // 환경 변수에서 인증 정보 가져오기
        String email = System.getenv("NETFLIX_EMAIL");
        String password = System.getenv("NETFLIX_PASSWORD");

        // 인증 정보 검증
        if (isInvalidCredentials(email, password)) {
            return createErrorResponse("넷플릭스 인증 정보가 유효하지 않습니다. 환경 변수를 확인해주세요.");
        }

        // 모든 콘텐츠 크롤링 시작
        netflixCrawlerService.runAllContentCrawler(email, password);

        return createSuccessResponse("모든 넷플릭스 콘텐츠 크롤링이 백그라운드에서 시작되었습니다.");
    }


    /**
     * 이번주 공개 콘텐츠만 크롤링 시작
     */
    @GetMapping("/crawl/this-week")
    public ResponseEntity<Map<String, Object>> startThisWeekCrawl() {
        logger.info("이번주 공개 넷플릭스 콘텐츠 크롤링 요청 받음");

        // 환경 변수에서 인증 정보 가져오기
        String email = System.getenv("NETFLIX_EMAIL");
        String password = System.getenv("NETFLIX_PASSWORD");

        // 인증 정보 검증
        if (isInvalidCredentials(email, password)) {
            return createErrorResponse("넷플릭스 인증 정보가 유효하지 않습니다. 환경 변수를 확인해주세요.");
        }

        // 이번주 공개 콘텐츠 크롤링 시작
        netflixCrawlerService.runThisWeekContentCrawler(email, password);

        return createSuccessResponse("이번주 공개 넷플릭스 콘텐츠 크롤링이 백그라운드에서 시작되었습니다.");
    }

    /**
     * 이전 버전 호환을 위한 엔드포인트
     */
    @GetMapping("/startCrawl")
    public ResponseEntity<Map<String, Object>> startCrawlLegacy() {
        logger.info("레거시 API로 크롤링 요청 받음");
        return startAllCrawl();
    }

    /**
     * 인증 정보가 유효한지 검증
     */
    private boolean isInvalidCredentials(String email, String password) {
        return email == null || email.isBlank() || password == null || password.isBlank();
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