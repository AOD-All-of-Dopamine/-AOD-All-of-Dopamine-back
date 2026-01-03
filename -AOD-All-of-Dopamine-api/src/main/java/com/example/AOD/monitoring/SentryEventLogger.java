package com.example.AOD.monitoring;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 커스텀 Sentry 이벤트 전송 헬퍼
 * 
 * 비즈니스 로직에서 명시적으로 에러를 추적하고 싶을 때 사용
 * 
 * 예시:
 * - 크롤링 실패 시 작품 정보와 함께 에러 전송
 * - API 호출 실패 시 요청 파라미터와 함께 전송
 */
@Slf4j
@Component
public class SentryEventLogger {

    /**
     * 크롤링 에러 추적
     * 
     * @param platform 플랫폼명 (Steam, TMDB, Naver 등)
     * @param url 크롤링 대상 URL
     * @param errorMessage 에러 메시지
     * @param exception 발생한 예외
     */
    public void logCrawlingError(String platform, String url, String errorMessage, Throwable exception) {
        Map<String, String> extra = new HashMap<>();
        extra.put("platform", platform);
        extra.put("url", url);
        extra.put("error_type", "crawling_failure");

        Sentry.withScope(scope -> {
            scope.setTag("error_category", "crawling");
            scope.setTag("platform", platform);
            scope.setContexts("crawling_context", extra);
            
            if (exception != null) {
                Sentry.captureException(exception);
            } else {
                SentryEvent event = new SentryEvent();
                Message message = new Message();
                message.setMessage(errorMessage);
                event.setMessage(message);
                event.setLevel(SentryLevel.ERROR);
                Sentry.captureEvent(event);
            }
        });

        log.error("🚨 [Sentry] 크롤링 에러: platform={}, url={}, message={}", platform, url, errorMessage);
    }

    /**
     * 작품 저장 실패 추적
     */
    public void logContentSaveError(String domain, String title, String errorMessage, Throwable exception) {
        Map<String, String> extra = new HashMap<>();
        extra.put("domain", domain);
        extra.put("title", title);
        extra.put("error_type", "save_failure");

        Sentry.withScope(scope -> {
            scope.setTag("error_category", "persistence");
            scope.setTag("domain", domain);
            scope.setContexts("save_context", extra);
            
            if (exception != null) {
                Sentry.captureException(exception);
            } else {
                SentryEvent event = new SentryEvent();
                Message message = new Message();
                message.setMessage(errorMessage);
                event.setMessage(message);
                event.setLevel(SentryLevel.WARNING);
                Sentry.captureEvent(event);
            }
        });

        log.warn("⚠️ [Sentry] 작품 저장 실패: domain={}, title={}, message={}", domain, title, errorMessage);
    }

    /**
     * API 호출 에러 추적
     */
    public void logApiError(String apiName, String endpoint, int statusCode, String errorMessage, Throwable exception) {
        Map<String, String> extra = new HashMap<>();
        extra.put("api_name", apiName);
        extra.put("endpoint", endpoint);
        extra.put("status_code", String.valueOf(statusCode));
        extra.put("error_type", "api_failure");

        Sentry.withScope(scope -> {
            scope.setTag("error_category", "api");
            scope.setTag("api_name", apiName);
            scope.setTag("status_code", String.valueOf(statusCode));
            scope.setContexts("api_context", extra);
            
            if (exception != null) {
                Sentry.captureException(exception);
            } else {
                SentryEvent event = new SentryEvent();
                Message message = new Message();
                message.setMessage(errorMessage);
                event.setMessage(message);
                event.setLevel(statusCode >= 500 ? SentryLevel.ERROR : SentryLevel.WARNING);
                Sentry.captureEvent(event);
            }
        });

        log.error("🚨 [Sentry] API 에러: api={}, endpoint={}, status={}, message={}", 
                  apiName, endpoint, statusCode, errorMessage);
    }

    /**
     * 일반 에러 메시지 전송 (예외 없이 메시지만)
     */
    public void logWarning(String message, Map<String, String> context) {
        Sentry.withScope(scope -> {
            if (context != null && !context.isEmpty()) {
                scope.setContexts("custom_context", context);
                context.forEach(scope::setTag);
            }
            
            SentryEvent event = new SentryEvent();
            Message msg = new Message();
            msg.setMessage(message);
            event.setMessage(msg);
            event.setLevel(SentryLevel.WARNING);
            Sentry.captureEvent(event);
        });

        log.warn("⚠️ [Sentry] {}", message);
    }

    /**
     * 커스텀 예외 전송
     */
    public void captureException(Throwable exception, Map<String, String> tags) {
        Sentry.withScope(scope -> {
            if (tags != null && !tags.isEmpty()) {
                tags.forEach(scope::setTag);
            }
            Sentry.captureException(exception);
        });

        log.error("🚨 [Sentry] 예외 발생: {}", exception.getMessage(), exception);
    }
}


