package com.example.AOD.recommend.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 추천 엔진이 주기적으로 받아 가는 서빙 가능 키 목록 (설계 §10).
 * 플랫폼 고유 ID 는 공개 정보라 인증하지 않는다. 엔진 쪽 소비는 AI 레포(3번 서빙)의 몫이다.
 */
@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class CatalogKeyController {

    private final CatalogKeyService catalogKeyService;

    @GetMapping(value = "/catalog-keys", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> catalogKeys(@RequestParam("platform") String platform) {
        String normalized = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        try {
            return ResponseEntity.ok(catalogKeyService.keysText(normalized));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("unknown platform: " + normalized + "\n");
        }
    }
}
