package com.example.AOD.config;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Sentry 에러 추적 설정
 * 
 * 주요 기능:
 * - 런타임 에러 자동 캡처 (Spring Boot Auto-Configuration)
 * - 스택 트레이스 + 컨텍스트 정보 수집
 * - 에러 발생 빈도/트렌드 분석
 * - Slack/이메일 알림 연동
 * 
 * 환경변수 설정:
 * SENTRY_DSN=https://your-dsn@sentry.io/project-id
 * 
 * Note: @EnableSentry 제거 - Spring Boot가 자동으로 설정함
 */
@Slf4j
@Configuration
public class SentryConfig {

    @Value("${sentry.dsn:}")
    private String sentryDsn;

    @Value("${sentry.environment:local}")
    private String environment;

    @Value("${spring.application.name:AOD}")
    private String applicationName;

    @PostConstruct
    public void init() {
        if (sentryDsn == null || sentryDsn.isBlank()) {
            log.warn("⚠️ Sentry DSN이 설정되지 않았습니다. 에러 추적이 비활성화됩니다.");
            log.warn("환경변수 SENTRY_DSN을 설정하거나 application.properties에서 sentry.dsn을 설정하세요.");
            return;
        }

        log.info("✅ Sentry 에러 추적 활성화됨");
        log.info("  - Environment: {}", environment);
        log.info("  - Application: {}", applicationName);
        log.info("  - DSN: {}***", sentryDsn.substring(0, Math.min(20, sentryDsn.length())));
    }

    /**
     * Sentry 커스텀 옵션 설정
     */
    @PostConstruct
    public void configureSentry() {
        Sentry.configureScope(scope -> {
            scope.setTag("application", applicationName);
            scope.setTag("environment", environment);
        });
    }
}


