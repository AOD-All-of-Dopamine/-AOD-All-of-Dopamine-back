package com.example.AOD.recommend.event;

import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * 클라이언트 이벤트 묶음 수신 (REC_TAB_DESIGN §4-1).
 * text/plain 을 받는 이유: sendBeacon·keepalive 가 교차 오리진 사전 요청 없이 보낼 수 있는 타입이기 때문이다.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecEventController {

    private static final int BODY_SAMPLE_CHARS = 500;

    private final ObjectMapper objectMapper;
    private final RecEventValidator validator;
    private final RecEventRateLimiter rateLimiter;
    private final RejectedEventSampler sampler;
    private final RecEventRecorder recorder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @PostMapping(value = "/rec-events", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> ingest(
            @RequestBody String body,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        RecEventBatch batch;
        try {
            batch = objectMapper.readValue(body, RecEventBatch.class);
        } catch (JsonProcessingException e) {
            sampler.maybeStore(Map.of("body", body.length() > BODY_SAMPLE_CHARS ? body.substring(0, BODY_SAMPLE_CHARS) : body),
                    "bad_json");
            return ResponseEntity.badRequest().body(Map.of("error", "본문이 JSON 이 아닙니다."));
        }

        UUID anonId = RecContext.parseUuid(batch.anonId());
        UUID sessionId = RecContext.parseUuid(batch.sessionId());
        if (anonId == null || sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "anonId·sessionId 는 uuid 여야 합니다."));
        }
        if (batch.events() == null || batch.events().isEmpty() || batch.events().size() > RecEventValidator.MAX_EVENTS) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "events 는 1~" + RecEventValidator.MAX_EVENTS + "개여야 합니다."));
        }
        if (!rateLimiter.allow(anonId.toString())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "too_many_requests"));
        }

        RecEventValidator.BatchHeader header = new RecEventValidator.BatchHeader(
                anonId, sessionId, resolveUserId(authHeader), batch.appVersion(), batch.device(),
                OffsetDateTime.now(ZoneOffset.UTC));

        int accepted = 0;
        int rejected = 0;
        for (RecEventItem item : batch.events()) {
            RecEventValidator.Outcome outcome = validator.validate(item, header);
            if (outcome instanceof RecEventValidator.Accepted a) {
                recorder.clientEvent(a.record());
                accepted++;
            } else if (outcome instanceof RecEventValidator.Rejected r) {
                rejected++;
                sampler.maybeStore(item == null ? Map.of() : item, r.reason());
            }
        }
        recorder.clientAgentOnce(sessionId, userAgent);

        return ResponseEntity.accepted().body(Map.of("accepted", accepted, "rejected", rejected));
    }

    /** 토큰이 유효하면 user_id, 아니면 null (비콘은 헤더를 못 싣는다 — 분석에서 session_id 로 잇는다). */
    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        try {
            if (!jwtTokenProvider.validateToken(token)) return null;
            return userRepository.findByUsername(jwtTokenProvider.getUsername(token)).map(User::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
