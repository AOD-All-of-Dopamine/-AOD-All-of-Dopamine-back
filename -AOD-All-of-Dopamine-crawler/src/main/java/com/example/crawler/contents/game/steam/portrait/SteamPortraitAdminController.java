package com.example.crawler.contents.game.steam.portrait;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Steam 세로 표지 동기화 수동 실행 — 첫 채움용. 비동기로 시작하고 바로 202 를 돌려준다(진행은 로그 · 메트릭).
 * 이미 돌고 있으면 409. calls 는 1 ~ {@value SteamPortraitSyncService#MAX_CALLS} (호출 하나 = 200개).
 */
@RestController
@RequestMapping("/api/crawl/steam")
@RequiredArgsConstructor
public class SteamPortraitAdminController {

    private final SteamPortraitSyncService syncService;

    @PostMapping("/portraits")
    public ResponseEntity<Map<String, Object>> sync(
            @RequestParam(defaultValue = "" + SteamPortraitSyncService.DAILY_CALLS) int calls) {
        int bounded = Math.max(1, Math.min(SteamPortraitSyncService.MAX_CALLS, calls));
        if (!syncService.start(bounded)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("started", false, "message", "이미 돌고 있다"));
        }
        return ResponseEntity.accepted().body(Map.of(
                "started", true,
                "calls", bounded,
                "maxGames", bounded * SteamPortraitClient.MAX_BATCH,
                "message", "로그 'Steam 세로 표지 동기화' 한 줄과 steam_portrait_* 메트릭으로 확인"));
    }
}
