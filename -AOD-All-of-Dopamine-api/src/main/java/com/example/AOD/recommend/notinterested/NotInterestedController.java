package com.example.AOD.recommend.notinterested;

import com.example.AOD.recommend.auth.RecAuth;
import com.example.AOD.recommend.context.RecContextHolder;
import com.example.AOD.recommend.reaction.ContentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 관심 없음 켜기·끄기 (REC_TAB_DESIGN §4-1). 인증은 추천 API 와 같은 RecAuth 로 직접 판정한다. */
@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class NotInterestedController {

    private final NotInterestedService notInterestedService;
    private final RecAuth recAuth;

    @PutMapping("/not-interested/{contentId}")
    public ResponseEntity<?> turnOn(
            @PathVariable Long contentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) NotInterestedRequest request) {
        RecAuth.Result auth = recAuth.authenticate(authHeader);
        if (!auth.ok()) return unauthorized();
        applyBodyContext(request);
        try {
            return ResponseEntity.ok(Map.of("on", notInterestedService.turnOn(auth.userId(), contentId)));
        } catch (ContentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/not-interested/{contentId}")
    public ResponseEntity<?> turnOff(
            @PathVariable Long contentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        RecAuth.Result auth = recAuth.authenticate(authHeader);
        if (!auth.ok()) return unauthorized();
        try {
            return ResponseEntity.ok(Map.of("on", notInterestedService.turnOff(auth.userId(), contentId)));
        } catch (ContentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** 본문의 식별자로 요청 맥락을 보강한다 — source 는 null 로 둬서 X-Rec-Source 헤더를 살린다. */
    private void applyBodyContext(NotInterestedRequest request) {
        if (request == null) return;
        RecContextHolder.set(RecContextHolder.current()
                .withBody(null, request.requestId(), request.impressionId()));
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "인증이 필요합니다."));
    }
}
