package com.example.AOD.game.steam.controller;

import com.example.AOD.game.steam.service.SteamCrawlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test/steam")
@RequiredArgsConstructor
public class SteamTestController {

    private final SteamCrawlService steamCrawlService;

    /**
     * DB 저장 없이 Steam에 등록된 모든 게임의 기본 목록(ID, 이름)만 조회합니다.
     * @return 전체 게임 목록
     */
    @GetMapping("/all-games-list")
    public ResponseEntity<List<Map<String, Object>>> getAllGamesList() {
        List<Map<String, Object>> allApps = steamCrawlService.fetchAllGamesList();
        return ResponseEntity.ok(allApps);
    }

    /**
     * (이동된 기능) 지정된 인덱스 범위의 게임 상세 정보를 수집하여 DB에 저장합니다.
     * @param start 수집 시작 인덱스
     * @param end   수집 종료 인덱스
     * @return 작업 시작 확인 메시지
     */
    @PostMapping("/collect-games-by-range")
    public ResponseEntity<Map<String, String>> collectGamesByRange(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "100") int end) {

        steamCrawlService.collectGamesByRange(start, end);
        return ResponseEntity.ok(Map.of("message", "지정된 범위의 Steam 게임 데이터 수집을 시작합니다. 범위: " + start + " - " + end));
    }
}