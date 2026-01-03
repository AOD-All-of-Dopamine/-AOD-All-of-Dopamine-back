package com.example.crawler.game.steam.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Steam API Rate Limiter
 * 
 * Steam API 제한 규칙:
 * - IP당 초당 10개 요청 제한
 * - IP당 분당 150개 요청 제한
 * - API Key당 분당 300개 요청 제한
 * 
 * 가장 제한적인 규칙(IP당 분당 150개)을 기준으로 안전하게 제한
 */
@Slf4j
@Component
public class SteamRateLimiter {
    
    // 초당 최대 요청 수 (10개 제한이지만 안전하게 8개로 설정)
    private static final int MAX_REQUESTS_PER_SECOND = 8;
    
    // 분당 최대 요청 수 (150개 제한이지만 안전하게 120개로 설정)
    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    
    // 초당 요청 타임스탬프 큐
    private final ConcurrentLinkedQueue<Long> secondQueue = new ConcurrentLinkedQueue<>();
    
    // 분당 요청 타임스탬프 큐
    private final ConcurrentLinkedQueue<Long> minuteQueue = new ConcurrentLinkedQueue<>();
    
    // 현재 초당 요청 수
    private final AtomicInteger currentSecondRequests = new AtomicInteger(0);
    
    // 현재 분당 요청 수
    private final AtomicInteger currentMinuteRequests = new AtomicInteger(0);
    
    /**
     * API 요청 전에 호출하여 Rate Limit을 준수하며 대기합니다.
     * 필요한 경우 자동으로 대기 시간을 계산하여 대기합니다.
     */
    public synchronized void acquirePermit() {
        long now = System.currentTimeMillis();
        
        // 1초(1000ms), 1분(60000ms) 이전 요청 제거
        cleanOldRequests(now);
        
        // 초당 제한 확인 및 대기
        if (currentSecondRequests.get() >= MAX_REQUESTS_PER_SECOND) {
            Long oldestInSecond = secondQueue.peek();
            if (oldestInSecond != null) {
                long waitTime = 1000 - (now - oldestInSecond);
                if (waitTime > 0) {
                    log.debug("초당 요청 제한 도달. {}ms 대기 중...", waitTime);
                    sleep(waitTime);
                    now = System.currentTimeMillis();
                    cleanOldRequests(now);
                }
            }
        }
        
        // 분당 제한 확인 및 대기
        if (currentMinuteRequests.get() >= MAX_REQUESTS_PER_MINUTE) {
            Long oldestInMinute = minuteQueue.peek();
            if (oldestInMinute != null) {
                long waitTime = 60000 - (now - oldestInMinute);
                if (waitTime > 0) {
                    log.warn("분당 요청 제한 도달. {}ms 대기 중...", waitTime);
                    sleep(waitTime);
                    now = System.currentTimeMillis();
                    cleanOldRequests(now);
                }
            }
        }
        
        // 요청 기록
        secondQueue.offer(now);
        minuteQueue.offer(now);
        currentSecondRequests.incrementAndGet();
        currentMinuteRequests.incrementAndGet();
        
        log.debug("Rate Limiter - 초당: {}/{}, 분당: {}/{}", 
                currentSecondRequests.get(), MAX_REQUESTS_PER_SECOND,
                currentMinuteRequests.get(), MAX_REQUESTS_PER_MINUTE);
    }
    
    /**
     * 1초 및 1분 이전의 오래된 요청 기록을 제거합니다.
     */
    private void cleanOldRequests(long now) {
        // 1초 이전 요청 제거
        while (!secondQueue.isEmpty()) {
            Long timestamp = secondQueue.peek();
            if (timestamp != null && now - timestamp > 1000) {
                secondQueue.poll();
                currentSecondRequests.decrementAndGet();
            } else {
                break;
            }
        }
        
        // 1분 이전 요청 제거
        while (!minuteQueue.isEmpty()) {
            Long timestamp = minuteQueue.peek();
            if (timestamp != null && now - timestamp > 60000) {
                minuteQueue.poll();
                currentMinuteRequests.decrementAndGet();
            } else {
                break;
            }
        }
    }
    
    /**
     * 지정된 시간만큼 대기합니다.
     */
    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Rate Limiter 대기 중 인터럽트 발생");
        }
    }
    
    /**
     * 현재 Rate Limiter 상태를 초기화합니다.
     */
    public synchronized void reset() {
        secondQueue.clear();
        minuteQueue.clear();
        currentSecondRequests.set(0);
        currentMinuteRequests.set(0);
        log.info("Steam Rate Limiter 초기화됨");
    }
    
    /**
     * 현재 Rate Limiter 통계를 반환합니다.
     */
    public String getStats() {
        return String.format("초당: %d/%d, 분당: %d/%d", 
                currentSecondRequests.get(), MAX_REQUESTS_PER_SECOND,
                currentMinuteRequests.get(), MAX_REQUESTS_PER_MINUTE);
    }
}


