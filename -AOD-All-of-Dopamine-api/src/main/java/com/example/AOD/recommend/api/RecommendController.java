package com.example.AOD.recommend.api;

import com.example.AOD.recommend.auth.RecAuth;
import com.example.AOD.recommend.chain.ChainNotFoundException;
import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.context.RecContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 추천 목록 (REC_TAB_DESIGN §4-1 · 설계 §2).
 * 인증은 ReactionController 와 같은 방식으로 Authorization 헤더를 직접 판정한다.
 * **토큰이 없으면 401 이 아니라 anonymous 대체(200)** — 비로그인도 화면이 돌아야 한다.
 * anonId·sessionId 는 RecContextFilter(1번)가 채운 요청 맥락에서 읽는다.
 */
@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendController {

    static final int MIN_SIZE = 1;
    /** 홈 추천 한 묶음 = 30개(프론트 설계 2026-09-26-home-rec-only). 엔진 k 는 늘지 않는다 — k+buffer 가 ROUTER_BUDGET(50)로 고정. */
    static final int MAX_SIZE = 30;

    private final RecommendService recommendService;
    private final RecAuth recAuth;

    @GetMapping
    public ResponseEntity<?> recommendations(
            @RequestParam(value = "tab", defaultValue = "all") String tab,
            @RequestParam(value = "chainId", required = false) String chainId,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "surface", required = false) String surface,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String normalizedTab = tab == null ? "" : tab.trim().toLowerCase(Locale.ROOT);
        if (!RecommendService.TABS.contains(normalizedTab)) {
            return badRequest("tab 은 all·movie·tv·game·webtoon·webnovel 중 하나여야 합니다.");
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            return badRequest("size 는 " + MIN_SIZE + "~" + MAX_SIZE + " 이어야 합니다.");
        }
        UUID chain = null;
        if (chainId != null && !chainId.isBlank()) {
            chain = RecContext.parseUuid(chainId);
            if (chain == null) return badRequest("chainId 는 uuid 여야 합니다.");
        }

        // 요청을 보낸 화면(홈·추천 탭)을 맥락의 source 로 싣는다 — 요청 로그의 surface 가 된다.
        // 허용 목록 검사는 서비스가 한다(RecommendService.surfaceOf). 요청을 실패시키지는 않는다.
        String normalizedSurface = surface == null ? null : surface.trim().toLowerCase(Locale.ROOT);
        RecContext ctx = RecContextHolder.current().withBody(normalizedSurface, null, null);
        RecAuth.Result auth;
        try {
            auth = recAuth.authenticate(authHeader);
        } catch (RuntimeException e) {
            // 토큰이 아니라 사용자 조회(DB)가 터진 경우 — 401 도 500 도 아니고 대체 목록이 맞다.
            log.error("추천 인증 판정 실패 — 대체 목록으로 답한다", e);
            return ResponseEntity.ok(recommendService.unavailableFallback(normalizedTab, size, ctx));
        }
        if (auth.status() == RecAuth.Status.INVALID) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 만료되었거나 올바르지 않습니다."));
        }
        if (auth.status() == RecAuth.Status.NONE) {
            return ResponseEntity.ok(recommendService.anonymousFallback(normalizedTab, size, ctx));
        }
        try {
            return ResponseEntity.ok(
                    recommendService.recommend(normalizedTab, chain, size, auth.userId(), auth.username(), ctx));
        } catch (ChainNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
