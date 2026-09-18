package com.example.AOD.recommend.reaction;

import com.example.AOD.recommend.context.RecContextHolder;
import com.example.AOD.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

/**
 * 반응 상태 지정 API (REC_TAB_DESIGN §4-1).
 * 기존 POST /like·/dislike 토글은 InteractionController 에 그대로 있다 — 둘 다 ReactionService 를 거친다.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;
    private final JwtTokenProvider jwtTokenProvider;

    @PutMapping("/works/{contentId}/reaction")
    public ResponseEntity<?> setReaction(
            @PathVariable Long contentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ReactionRequest request
    ) {
        String username = authenticatedUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "인증이 필요합니다."));
        }

        ReactionState target;
        try {
            target = ReactionState.valueOf(request.state() == null ? "" : request.state().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "state 는 LIKE·DISLIKE·NONE 중 하나여야 합니다."));
        }

        // 본문의 맥락이 헤더보다 우선한다. 요청이 끝나면 RecContextFilter 가 비운다.
        RecContextHolder.set(RecContextHolder.current()
                .withBody(request.source(), request.requestId(), request.impressionId()));

        try {
            ReactionResult r = reactionService.setReaction(contentId, username, target);
            return ResponseEntity.ok(new ReactionResponse(
                    r.state().name(), r.previousState().name(), r.likeCount(), r.dislikeCount()));
        } catch (ContentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (UnauthenticatedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    /** 토큰이 없거나 만료·위조면 null. */
    private String authenticatedUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        try {
            return jwtTokenProvider.validateToken(token) ? jwtTokenProvider.getUsername(token) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
