package com.example.crawler.test;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sentry 테스트용 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class SentryTestController {

    @GetMapping("/error")
    public String throwError() {
        log.info("🔥 테스트 에러 발생 시작");
        throw new RuntimeException("Sentry 테스트 에러 - 크롤러 서버에서 발생!");
    }

    @GetMapping("/sentry-manual")
    public String sendManualError() {
        log.info("📤 Sentry 수동 전송 테스트");
        
        try {
            throw new IllegalStateException("수동으로 전송하는 테스트 에러입니다!");
        } catch (Exception e) {
            Sentry.captureException(e);
            log.info("✅ Sentry로 에러 전송 완료");
            return "Sentry로 에러를 수동 전송했습니다!";
        }
    }

    @GetMapping("/health")
    public String health() {
        return "✅ 크롤러 서버 정상 작동 중!";
    }
}
