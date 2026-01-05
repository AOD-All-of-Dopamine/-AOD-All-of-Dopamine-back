package com.example.crawler.game.steam.controller;

import com.example.crawler.game.steam.service.SteamCrawlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/test/steam")
@RequiredArgsConstructor
public class SteamTestController {

    private final SteamCrawlService steamCrawlService;

    /**
     * 지정된 인덱스 범위의 게임 상세 정보를 수집하여 DB에 저장합니다.
     * 이제 이 메서드는 '게임만 필터링된' 목록을 기준으로 동작합니다.
     * @param start 수집 시작 인덱스
     * @param end   수집 종료 인덱스
     * @return 작업 시작 확인 메시지
     */
    @PostMapping("/collect-games-by-range")
    public ResponseEntity<Map<String, String>> collectGamesByRange(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "100") int end) {

        // 개선된 collectAllGamesInRange 메서드를 호출합니다. (이름은 같지만 내부 로직이 변경됨)
        steamCrawlService.collectAllGamesInRange(start, end);
        return ResponseEntity.ok(Map.of("message", "지정된 범위의 Steam 게임 데이터 수집을 시작합니다. 범위: " + start + " - " + end));
    }
}

