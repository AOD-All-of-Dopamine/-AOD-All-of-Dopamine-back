package com.example.AOD.recommend.router;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 추천 라우터 전용 RestTemplate (REC_TAB_DESIGN §6-2 · §8-5 지연 예산).
 * 기존 config/RestTemplateConfig 의 restTemplate 빈(제한 시간 없음)을 쓰면 라우터 장애가 요청 스레드를
 * 무한히 잡는다 — 그래서 따로 만든다. 타입 주입이 모호해지지 않게 이름을 주고, 쓰는 쪽은 @Qualifier 를 쓴다.
 *
 * Spring Framework 6.2 의 SimpleClientHttpRequestFactory 는 Duration 을 받는 타임아웃 설정자를 갖는다 —
 * 새 HTTP 클라이언트 의존성이 필요 없다.
 */
@Configuration
public class RecRouterConfig {

    public static final String ROUTER_REST_TEMPLATE = "recRouterRestTemplate";

    @Bean(ROUTER_REST_TEMPLATE)
    public RestTemplate recRouterRestTemplate(
            @Value("${rec.router.connect-timeout-ms:300}") int connectTimeoutMs,
            @Value("${rec.router.read-timeout-ms:2000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(factory);
    }
}
