package com.example.AOD.recommend.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 선택 헤더에서 추천 맥락을 읽어 요청 스레드에 둔다. 헤더가 없으면 모든 값이 null 인 맥락이 된다. */
@Component
public class RecContextFilter extends OncePerRequestFilter {

    public static final String H_SOURCE = "X-Rec-Source";
    public static final String H_REQUEST_ID = "X-Rec-Request-Id";
    public static final String H_IMPRESSION_ID = "X-Rec-Impression-Id";
    public static final String H_ANON_ID = "X-Anon-Id";
    public static final String H_SESSION_ID = "X-Session-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RecContextHolder.set(new RecContext(
                request.getHeader(H_SOURCE),
                RecContext.parseUuid(request.getHeader(H_REQUEST_ID)),
                RecContext.parseUuid(request.getHeader(H_IMPRESSION_ID)),
                RecContext.parseUuid(request.getHeader(H_ANON_ID)),
                RecContext.parseUuid(request.getHeader(H_SESSION_ID))));
        try {
            chain.doFilter(request, response);
        } finally {
            RecContextHolder.clear();
        }
    }
}
