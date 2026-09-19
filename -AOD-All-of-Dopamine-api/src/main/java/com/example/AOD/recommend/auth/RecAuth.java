package com.example.AOD.recommend.auth;

import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Authorization 헤더 판정 (설계 §2 "인증은 기존 관례대로 직접 파싱").
 * SecurityConfig 가 /api/** 를 permitAll 이라 SecurityContext 에는 사용자가 없다.
 *
 * RecEventController 에 있던 username→user id 캐시를 이 빈으로 옮겼다 — 추천 API 도 요청마다
 * user id 가 필요하다. id 는 바뀌지 않으므로 만료가 필요 없고, 넘치면 통째로 비운다.
 */
@Component
@RequiredArgsConstructor
public class RecAuth {

    static final int USER_ID_CACHE_MAX = 10_000;

    /** NONE = 토큰 없음(익명 대체) · INVALID = 토큰이 있는데 못 씀(401) · OK = 사용자 확정. */
    public enum Status { NONE, INVALID, OK }

    /** status 가 OK 일 때만 userId·username 이 채워진다. */
    public record Result(Status status, Long userId, String username) {

        public static final Result NO_TOKEN = new Result(Status.NONE, null, null);
        public static final Result BAD_TOKEN = new Result(Status.INVALID, null, null);

        public boolean ok() {
            return status == Status.OK;
        }
    }

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final ConcurrentHashMap<String, Long> userIdCache = new ConcurrentHashMap<>();

    public Result authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return Result.NO_TOKEN;
        String token = authHeader.substring(7);
        try {
            if (!jwtTokenProvider.validateToken(token)) return Result.BAD_TOKEN;
            String username = jwtTokenProvider.getUsername(token);
            if (username == null || username.isBlank()) return Result.BAD_TOKEN;
            Long userId = resolveUserId(username);
            return userId == null ? Result.BAD_TOKEN : new Result(Status.OK, userId, username);
        } catch (Exception e) {
            return Result.BAD_TOKEN;   // 위조·형식 오류로 파서가 던지는 경우
        }
    }

    /**
     * 토큰이 쓸 만하면 user id, 아니면 null.
     * 비콘(POST /api/rec-events)은 헤더를 못 싣는 경우가 있어 null 이어도 요청을 거절하지 않는다.
     */
    public Long userIdOrNull(String authHeader) {
        return authenticate(authHeader).userId();
    }

    private Long resolveUserId(String username) {
        Long cached = userIdCache.get(username);
        if (cached != null) return cached;
        Long id = userRepository.findByUsername(username).map(User::getId).orElse(null);
        if (id != null) {
            if (userIdCache.size() >= USER_ID_CACHE_MAX) userIdCache.clear();
            userIdCache.put(username, id);
        }
        return id;
    }
}
