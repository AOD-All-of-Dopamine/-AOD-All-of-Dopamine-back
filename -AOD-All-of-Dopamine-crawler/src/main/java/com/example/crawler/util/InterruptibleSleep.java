package com.example.crawler.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 인터럽트 처리가 표준화된 sleep 유틸리티
 * - InterruptedException 발생 시 인터럽트 상태를 복원
 * - 로깅 일관성 유지
 * - 크롤링 및 비동기 작업에서 안전한 대기 처리
 */
@Slf4j
public class InterruptibleSleep {

    private InterruptibleSleep() {
        // 유틸리티 클래스는 인스턴스화 방지
    }

    /**
     * 지정된 시간(밀리초) 동안 대기
     * 
     * @param millis 대기 시간 (밀리초)
     * @return true: 정상 완료, false: 인터럽트 발생
     */
    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            log.debug("Thread sleep 인터럽트 발생 ({}ms 대기 중단)", millis);
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            return false;
        }
    }

    /**
     * 지정된 시간 동안 대기 (TimeUnit 사용)
     * 
     * @param duration 대기 시간
     * @param unit 시간 단위
     * @return true: 정상 완료, false: 인터럽트 발생
     */
    public static boolean sleep(long duration, TimeUnit unit) {
        try {
            unit.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            log.debug("Thread sleep 인터럽트 발생 ({} {} 대기 중단)", duration, unit);
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            return false;
        }
    }

    /**
     * 인터럽트 발생 시 예외를 던지는 sleep
     * - 크롤링 루프를 즉시 중단해야 할 때 사용
     * 
     * @param millis 대기 시간 (밀리초)
     * @throws InterruptedException 인터럽트 발생 시
     */
    public static void sleepOrThrow(long millis) throws InterruptedException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.info("Thread sleep 인터럽트 발생, 작업 중단 ({}ms)", millis);
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            throw e; // 상위로 전파하여 작업 중단
        }
    }

    /**
     * 인터럽트 발생 시 예외를 던지는 sleep (TimeUnit 사용)
     * 
     * @param duration 대기 시간
     * @param unit 시간 단위
     * @throws InterruptedException 인터럽트 발생 시
     */
    public static void sleepOrThrow(long duration, TimeUnit unit) throws InterruptedException {
        try {
            unit.sleep(duration);
        } catch (InterruptedException e) {
            log.info("Thread sleep 인터럽트 발생, 작업 중단 ({} {})", duration, unit);
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            throw e; // 상위로 전파하여 작업 중단
        }
    }
}


