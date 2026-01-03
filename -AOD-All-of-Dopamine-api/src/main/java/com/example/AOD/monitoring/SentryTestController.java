package com.example.AOD.monitoring;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Sentry 테스트용 컨트롤러
 * 
 * Sentry가 제대로 작동하는지 확인하기 위한 테스트 엔드포인트
 * 
 * 테스트 후 프로덕션에 배포할 때는 이 파일을 삭제하거나
 * @Profile("local")을 추가하여 로컬에서만 사용
 */
@Slf4j
@RestController
@RequestMapping("/api/test/sentry")
public class SentryTestController {

    /**
     * 1. 간단한 예외 테스트
     * GET http://localhost:8080/api/test/sentry/exception
     */
    @GetMapping("/exception")
    public String testException() {
        log.info("🧪 Sentry 예외 테스트 시작");
        
        try {
            throw new Exception("This is a test exception for Sentry!");
        } catch (Exception e) {
            Sentry.captureException(e);
            log.error("테스트 예외 발생 및 Sentry 전송 완료", e);
        }
        
        return "✅ Test exception captured! Check Sentry dashboard.";
    }

    /**
     * 2. RuntimeException 테스트 (자동 캡처)
     * GET http://localhost:8080/api/test/sentry/runtime-error
     */
    @GetMapping("/runtime-error")
    public String testRuntimeError() {
        log.info("🧪 Sentry RuntimeException 테스트 시작");
        
        // Spring이 자동으로 캡처함
        throw new RuntimeException("This is a test RuntimeException - will be auto-captured by Sentry!");
    }

    /**
     * 3. 커스텀 메시지 + 컨텍스트 테스트
     * GET http://localhost:8080/api/test/sentry/custom-context
     */
    @GetMapping("/custom-context")
    public String testCustomContext() {
        log.info("🧪 Sentry 커스텀 컨텍스트 테스트 시작");
        
        try {
            // 의도적 에러 발생
            String nullString = null;
            nullString.length(); // NullPointerException
        } catch (Exception e) {
            // 컨텍스트 정보와 함께 전송
            Sentry.withScope(scope -> {
                scope.setTag("test_type", "custom_context");
                scope.setTag("feature", "monitoring");
                
                Map<String, String> contextData = new HashMap<>();
                contextData.put("user_action", "testing_sentry");
                contextData.put("test_time", String.valueOf(System.currentTimeMillis()));
                contextData.put("endpoint", "/api/test/sentry/custom-context");
                
                scope.setContexts("test_context", contextData);
                
                Sentry.captureException(e);
            });
            
            log.error("커스텀 컨텍스트와 함께 Sentry 전송 완료", e);
        }
        
        return "✅ Custom context exception captured! Check Sentry dashboard for tags and context.";
    }

    /**
     * 4. 크롤링 에러 시뮬레이션
     * GET http://localhost:8080/api/test/sentry/crawling-error
     */
    @GetMapping("/crawling-error")
    public String testCrawlingError() {
        log.info("🧪 Sentry 크롤링 에러 시뮬레이션");
        
        String platform = "TestPlatform";
        String url = "https://example.com/test";
        
        try {
            throw new Exception("Failed to crawl content: Connection timeout");
        } catch (Exception e) {
            Sentry.withScope(scope -> {
                scope.setTag("error_category", "crawling");
                scope.setTag("platform", platform);
                
                Map<String, String> crawlingContext = new HashMap<>();
                crawlingContext.put("platform", platform);
                crawlingContext.put("url", url);
                crawlingContext.put("error_type", "connection_timeout");
                
                scope.setContexts("crawling_context", crawlingContext);
                
                Sentry.captureException(e);
            });
            
            log.error("크롤링 에러 시뮬레이션 - Sentry 전송 완료", e);
        }
        
        return "✅ Crawling error simulation captured! Check Sentry dashboard with 'crawling' tag.";
    }

    /**
     * 5. 헬스체크 (Sentry 연결 확인)
     * GET http://localhost:8080/api/test/sentry/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Sentry test endpoints are ready");
        response.put("endpoints", new String[]{
            "GET /api/test/sentry/exception",
            "GET /api/test/sentry/runtime-error",
            "GET /api/test/sentry/custom-context",
            "GET /api/test/sentry/crawling-error"
        });
        
        return response;
    }
}


