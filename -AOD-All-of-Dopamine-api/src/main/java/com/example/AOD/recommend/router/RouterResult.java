package com.example.AOD.recommend.router;

import com.example.AOD.recommend.router.dto.RouterResponse;

/**
 * 라우터 호출 결과. 실패 종류가 그대로 응답의 fallbackReason 이 된다 (설계 §2).
 * latencyMs 는 커버리지 재호출 예산(설계 §4, 1.2초)을 판단하는 데도 쓴다.
 */
public record RouterResult(RouterResponse response, String failure, long latencyMs) {

    public static final String TIMEOUT = "timeout";
    public static final String SERVICE_ERROR = "service_error";
    public static final String CIRCUIT_OPEN = "circuit_open";

    public static RouterResult ok(RouterResponse response, long latencyMs) {
        return new RouterResult(response, null, latencyMs);
    }

    public static RouterResult failed(String failure, long latencyMs) {
        return new RouterResult(null, failure, latencyMs);
    }

    public boolean ok() {
        return response != null;
    }
}
