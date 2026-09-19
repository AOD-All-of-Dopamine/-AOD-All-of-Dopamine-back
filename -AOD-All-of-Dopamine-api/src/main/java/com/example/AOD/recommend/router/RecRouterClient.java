package com.example.AOD.recommend.router;

import com.example.AOD.recommend.router.dto.RouterRequest;
import com.example.AOD.recommend.router.dto.RouterResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.concurrent.Semaphore;

/**
 * 추천 라우터 호출 (서빙 README §2-1·§3).
 * 세마포어로 동시 호출을 묶고(기본 20), 서킷이 열려 있으면 바로 대체로 보낸다.
 * 실패 종류(timeout · service_error · circuit_open)가 그대로 응답의 fallbackReason 이 된다.
 */
@Slf4j
@Component
public class RecRouterClient {

    private final RestTemplate restTemplate;
    private final RecCircuitBreaker breaker;
    private final String recommendUrl;
    private final Semaphore permits;

    public RecRouterClient(@Qualifier(RecRouterConfig.ROUTER_REST_TEMPLATE) RestTemplate restTemplate,
                           RecCircuitBreaker breaker,
                           @Value("${rec.router.base-url:http://localhost:18080}") String baseUrl,
                           @Value("${rec.router.max-concurrent:20}") int maxConcurrent) {
        this.restTemplate = restTemplate;
        this.breaker = breaker;
        this.recommendUrl = baseUrl.replaceAll("/+$", "") + "/v1/recommend";
        this.permits = new Semaphore(Math.max(0, maxConcurrent));
    }

    public RouterResult recommend(RouterRequest request) {
        // 자리부터 잡는다 — 서킷을 먼저 보면 반열림 시험권을 받아 놓고 자리가 없어 못 쓰는 일이 생긴다.
        if (!permits.tryAcquire()) {
            log.warn("추천 라우터 동시 호출 상한 초과 — 대체 목록으로 간다");
            return RouterResult.failed(RouterResult.SERVICE_ERROR, 0L);   // 라우터 장애가 아니므로 서킷에 세지 않는다
        }
        try {
            if (!breaker.allow()) return RouterResult.failed(RouterResult.CIRCUIT_OPEN, 0L);

            long startedAt = System.nanoTime();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            try {
                RouterResponse body = restTemplate.postForObject(
                        recommendUrl, new HttpEntity<>(request, headers), RouterResponse.class);
                long latencyMs = elapsedMs(startedAt);
                if (body == null || body.items() == null) {
                    breaker.recordFailure();
                    return RouterResult.failed(RouterResult.SERVICE_ERROR, latencyMs);
                }
                breaker.recordSuccess();
                return RouterResult.ok(body, latencyMs);
            } catch (ResourceAccessException e) {
                breaker.recordFailure();
                boolean timedOut = hasCause(e, SocketTimeoutException.class);
                return RouterResult.failed(timedOut ? RouterResult.TIMEOUT : RouterResult.SERVICE_ERROR,
                        elapsedMs(startedAt));
            } catch (RestClientException e) {          // 4xx·5xx(503 engines_unavailable 포함) · 본문 형식 오류
                breaker.recordFailure();
                return RouterResult.failed(RouterResult.SERVICE_ERROR, elapsedMs(startedAt));
            }
        } finally {
            permits.release();
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
