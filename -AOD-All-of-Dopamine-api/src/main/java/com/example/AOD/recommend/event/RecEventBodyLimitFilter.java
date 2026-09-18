package com.example.AOD.recommend.event;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * POST /api/rec-events 는 인증 없는 공개 엔드포인트다. @RequestBody String 은 본문을 전부 메모리에 올린 뒤에야
 * 검증·속도 제한이 돌기 때문에, 본문을 읽기 전에 크기를 막는다 (정상 최대: 50건 × payload 4KB ≈ 200KB).
 */
@Component
public class RecEventBodyLimitFilter extends OncePerRequestFilter {

    static final String PATH = "/api/rec-events";
    static final long MAX_BODY_BYTES = 256 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length < 0) {   // chunked 등 길이를 알 수 없는 본문은 받지 않는다 (sendBeacon·fetch 는 길이를 보낸다)
            response.sendError(HttpServletResponse.SC_LENGTH_REQUIRED, "Content-Length required");
            return;
        }
        if (length > MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "body too large");
            return;
        }
        chain.doFilter(request, response);
    }
}
