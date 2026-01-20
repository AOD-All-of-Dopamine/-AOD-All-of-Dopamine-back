package com.example.crawler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HttpClient 설정
 * 
 * Connection Pool을 사용하여 스레드 생성을 최소화합니다.
 * Jsoup의 매 요청마다 새 소켓/스레드를 생성하는 문제를 해결합니다.
 */
@Configuration
public class HttpClientConfig {
    
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)  // HTTP/2 지원 (더 효율적)
                // Connection Pool은 기본으로 활성화됨
                .build();
    }
}
