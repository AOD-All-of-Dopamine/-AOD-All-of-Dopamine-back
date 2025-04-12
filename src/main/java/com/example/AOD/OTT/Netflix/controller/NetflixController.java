package com.example.AOD.OTT.Netflix.controller;

import com.example.AOD.OTT.Netflix.service.NetflixCrawlerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class NetflixController {

    private final NetflixCrawlerService netflixCrawlerService;

    public NetflixController(NetflixCrawlerService netflixCrawlerService) {
        this.netflixCrawlerService = netflixCrawlerService;
    }

    @GetMapping("/netflixStartCrawl")
    public ResponseEntity<Map<String, Object>> startCrawl() {
        // 1) 환경 변수에서 이메일/패스워드 불러오기
        String email = System.getenv("NETFLIX_EMAIL");
        String password = System.getenv("NETFLIX_PASSWORD");

        // 2) null 혹은 빈 문자열 체크 (필요하다면 에러 처리)
        if (email == null || password == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "fail");
            errorResponse.put("message", "환경 변수(NETFLIX_EMAIL, NETFLIX_PASSWORD)가 설정되지 않았습니다.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // 3) 비동기로 크롤링 실행
        netflixCrawlerService.runCrawler(email, password);

        // 4) 성공 응답
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "넷플릭스 크롤링 작업을 백그라운드에서 실행했습니다.");
        return ResponseEntity.ok(response);
    }
}
