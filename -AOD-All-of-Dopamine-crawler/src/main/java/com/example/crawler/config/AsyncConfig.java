package com.example.crawler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 작업(@Async) 스레드 풀 설정
 * - 크롤링 작업용 전용 Executor 제공
 * - ThreadLocal 메모리 누수 방지
 * - 예외 처리 통합 관리
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 크롤링 전용 ThreadPoolTaskExecutor
     * - 기본 SimpleAsyncTaskExecutor 대신 스레드풀 재사용
     * - ThreadLocal 관리 용이
     */
    @Bean(name = "crawlerTaskExecutor")
    public Executor crawlerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 기본 스레드 수: 크롤러 종류만큼 (Naver, Steam, TMDB 등)
        executor.setCorePoolSize(1);
        
        // 최대 스레드 수: 동시 크롤링 최대치
        executor.setMaxPoolSize(1);
        
        // 대기 큐 용량
        executor.setQueueCapacity(5);
        
        // 스레드 이름 prefix (디버깅 편의)
        executor.setThreadNamePrefix("Crawler-Async-");
        
        // 큐가 가득 찰 때 정책: 호출한 스레드에서 직접 실행
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 애플리케이션 종료 시 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        log.info("Crawler TaskExecutor 초기화 완료 - Core: {}, Max: {}, Queue: {}", 
            executor.getCorePoolSize(), 
            executor.getMaxPoolSize(), 
            executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 기본 Executor 설정 (명시적으로 executor 지정하지 않은 @Async)
     */
    @Override
    public Executor getAsyncExecutor() {
        return crawlerTaskExecutor();
    }

    /**
     * 비동기 작업 예외 핸들러
     * - @Async 메서드에서 발생한 예외를 통합 처리
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("=== 비동기 작업 예외 발생 ===");
            log.error("메서드: {}", method.getName());
            log.error("파라미터: {}", Arrays.toString(params));
            log.error("예외: {}", ex.getMessage(), ex);
            log.error("===========================");
        };
    }
}


