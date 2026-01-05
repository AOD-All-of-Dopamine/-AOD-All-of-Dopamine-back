// package com.example.AOD.game.steam.util;

// import lombok.extern.slf4j.Slf4j;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;

// import java.util.ArrayList;
// import java.util.List;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
// import java.util.concurrent.TimeUnit;
// import java.util.concurrent.atomic.AtomicInteger;

// import static org.assertj.core.api.Assertions.assertThat;

// @Slf4j
// @DisplayName("SteamRateLimiter 테스트")
// class SteamRateLimiterTest {

//     private SteamRateLimiter rateLimiter;

//     @BeforeEach
//     void setUp() {
//         rateLimiter = new SteamRateLimiter();
//         rateLimiter.reset();
//     }

//     @Test
//     @DisplayName("초당 요청 제한 테스트 - 8개 초과 시 대기")
//     void testPerSecondRateLimit() {
//         // given
//         int requestCount = 10;
//         long startTime = System.currentTimeMillis();
//         List<Long> requestTimes = new ArrayList<>();

//         // when
//         for (int i = 0; i < requestCount; i++) {
//             rateLimiter.acquirePermit();
//             requestTimes.add(System.currentTimeMillis());
//             log.info("요청 {} 완료: {}ms", i + 1, System.currentTimeMillis() - startTime);
//         }

//         long endTime = System.currentTimeMillis();
//         long totalTime = endTime - startTime;

//         // then
//         // 10개 요청이 완료되는데 최소 1초 이상 걸려야 함 (8개/초 제한)
//         log.info("총 소요 시간: {}ms", totalTime);
//         assertThat(totalTime).isGreaterThanOrEqualTo(1000);

//         // 첫 1초 동안 최대 8개까지만 요청되어야 함
//         long oneSecondMark = startTime + 1000;
//         long requestsInFirstSecond = requestTimes.stream()
//                 .filter(time -> time <= oneSecondMark)
//                 .count();
        
//         log.info("첫 1초 동안의 요청 수: {}", requestsInFirstSecond);
//         assertThat(requestsInFirstSecond).isLessThanOrEqualTo(8);
//     }

//     @Test
//     @DisplayName("분당 요청 제한 테스트 - 초기 120개는 빠르게 처리")
//     void testPerMinuteRateLimit() {
//         // given
//         int requestCount = 20;  // 테스트 시간 단축을 위해 20개로 축소
//         long startTime = System.currentTimeMillis();
//         List<Long> requestTimes = new ArrayList<>();

//         // when
//         for (int i = 0; i < requestCount; i++) {
//             rateLimiter.acquirePermit();
//             long currentTime = System.currentTimeMillis();
//             requestTimes.add(currentTime);
            
//             if (i % 5 == 0) {
//                 log.info("요청 {} 완료: {}ms", i + 1, currentTime - startTime);
//             }
//         }

//         long endTime = System.currentTimeMillis();
//         long totalTime = endTime - startTime;

//         // then
//         // 20개 요청은 초당 8개 제한으로 약 3초 내에 완료되어야 함
//         log.info("총 소요 시간: {}ms ({}초)", totalTime, totalTime / 1000.0);
//         assertThat(totalTime).isGreaterThanOrEqualTo(2000);  // 최소 2초
//         assertThat(totalTime).isLessThan(5000);  // 최대 5초 이내
        
//         log.info("분당 제한(120개)은 초과하지 않았으므로 분당 제한 대기 없음");
//     }

//     @Test
//     @DisplayName("초당 제한 경계값 테스트 - 정확히 8개 요청")
//     void testExactlyMaxRequestsPerSecond() {
//         // given
//         int requestCount = 8;
//         long startTime = System.currentTimeMillis();

//         // when
//         for (int i = 0; i < requestCount; i++) {
//             rateLimiter.acquirePermit();
//         }

//         long endTime = System.currentTimeMillis();
//         long totalTime = endTime - startTime;

//         // then
//         // 8개 요청은 대기 없이 1초 이내에 완료되어야 함
//         log.info("8개 요청 소요 시간: {}ms", totalTime);
//         assertThat(totalTime).isLessThan(1000);
//     }

//     @Test
//     @DisplayName("분당 제한 경계값 테스트 - 16개 요청으로 간소화")
//     void testExactlyMaxRequestsPerMinute() {
//         // given
//         int requestCount = 16;  // 초당 8개 제한 기준 2초 소요
//         long startTime = System.currentTimeMillis();

//         // when
//         for (int i = 0; i < requestCount; i++) {
//             rateLimiter.acquirePermit();
//             if (i % 4 == 0) {
//                 log.info("요청 {} 완료: {}ms", i + 1, System.currentTimeMillis() - startTime);
//             }
//         }

//         long endTime = System.currentTimeMillis();
//         long totalTime = endTime - startTime;

//         // then
//         // 16개 요청은 초당 8개 제한으로 약 2초 소요
//         log.info("16개 요청 소요 시간: {}ms ({}초)", totalTime, totalTime / 1000.0);
//         assertThat(totalTime).isGreaterThanOrEqualTo(1000);
//         assertThat(totalTime).isLessThan(4000);
//     }

//     @Test
//     @DisplayName("동시성 테스트 - 여러 스레드에서 요청 시 제한 준수")
//     void testConcurrentRequests() throws InterruptedException {
//         // given
//         int threadCount = 5;
//         int requestsPerThread = 10;
//         int totalRequests = threadCount * requestsPerThread;
//         ExecutorService executor = Executors.newFixedThreadPool(threadCount);
//         CountDownLatch latch = new CountDownLatch(threadCount);
//         AtomicInteger completedRequests = new AtomicInteger(0);
//         List<Long> requestTimes = new ArrayList<>();
//         long startTime = System.currentTimeMillis();

//         // when
//         for (int i = 0; i < threadCount; i++) {
//             final int threadNum = i;
//             executor.submit(() -> {
//                 try {
//                     for (int j = 0; j < requestsPerThread; j++) {
//                         rateLimiter.acquirePermit();
//                         synchronized (requestTimes) {
//                             requestTimes.add(System.currentTimeMillis());
//                         }
//                         int completed = completedRequests.incrementAndGet();
//                         if (completed % 10 == 0) {
//                             log.info("스레드 {}: 요청 {}/{} 완료", threadNum, j + 1, requestsPerThread);
//                         }
//                     }
//                 } finally {
//                     latch.countDown();
//                 }
//             });
//         }

//         latch.await(120, TimeUnit.SECONDS);
//         executor.shutdown();
//         long endTime = System.currentTimeMillis();
//         long totalTime = endTime - startTime;

//         // then
//         log.info("총 {} 스레드, {} 요청 완료. 소요 시간: {}ms ({}초)", 
//                 threadCount, completedRequests.get(), totalTime, totalTime / 1000.0);
//         assertThat(completedRequests.get()).isEqualTo(totalRequests);

//         // 초당 제한 검증: 어떤 1초 구간에서도 8개 초과하지 않아야 함
//         for (long checkTime = startTime; checkTime < endTime; checkTime += 1000) {
//             final long checkStart = checkTime;
//             final long checkEnd = checkTime + 1000;
//             long requestsInWindow = requestTimes.stream()
//                     .filter(time -> time >= checkStart && time < checkEnd)
//                     .count();
            
//             if (requestsInWindow > 8) {
//                 log.warn("{}ms ~ {}ms 구간에서 {}개 요청 발견", 
//                         checkStart - startTime, checkEnd - startTime, requestsInWindow);
//             }
//             assertThat(requestsInWindow).isLessThanOrEqualTo(8);
//         }
//     }

//     @Test
//     @DisplayName("reset 테스트 - 초기화 후 카운터 리셋 확인")
//     void testReset() {
//         // given
//         for (int i = 0; i < 5; i++) {
//             rateLimiter.acquirePermit();
//         }
//         String statsBefore = rateLimiter.getStats();
//         log.info("리셋 전 상태: {}", statsBefore);

//         // when
//         rateLimiter.reset();
//         String statsAfter = rateLimiter.getStats();
//         log.info("리셋 후 상태: {}", statsAfter);

//         // then
//         assertThat(statsAfter).contains("초당: 0/8");
//         assertThat(statsAfter).contains("분당: 0/120");
//     }

//     @Test
//     @DisplayName("연속 요청 후 시간 경과 시 제한 해제 확인")
//     void testRateLimitReleaseAfterTime() throws InterruptedException {
//         // given - 8개 요청으로 초당 제한 도달
//         for (int i = 0; i < 8; i++) {
//             rateLimiter.acquirePermit();
//         }
//         log.info("8개 요청 완료: {}", rateLimiter.getStats());

//         // when - 1초 대기 후 추가 요청
//         Thread.sleep(1100);
//         long startTime = System.currentTimeMillis();
//         rateLimiter.acquirePermit();
//         long elapsed = System.currentTimeMillis() - startTime;

//         // then - 대기 시간 없이 즉시 처리되어야 함
//         log.info("1초 대기 후 추가 요청 소요 시간: {}ms", elapsed);
//         assertThat(elapsed).isLessThan(100); // 대기 시간이 거의 없어야 함
//     }

//     @Test
//     @DisplayName("짧은 시간에 대량 요청 시 적절한 대기 시간 확인")
//     void testLargeNumberOfRequests() {
//         // given
//         int requestCount = 25;
//         long startTime = System.currentTimeMillis();

//         // when
//         for (int i = 0; i < requestCount; i++) {
//             rateLimiter.acquirePermit();
//             if (i % 5 == 0) {
//                 log.info("요청 {}/{} 완료: {}ms", i + 1, requestCount, 
//                         System.currentTimeMillis() - startTime);
//             }
//         }

//         long endTime = System.currentTimeMillis();
//         long totalTime = endTime - startTime;

//         // then
//         // 25개 요청 = 8(첫 1초) + 8(두번째 1초) + 8(세번째 1초) + 1(네번째 1초)
//         // 최소 2초 이상 소요되어야 함
//         log.info("25개 요청 총 소요 시간: {}ms ({}초)", totalTime, totalTime / 1000.0);
//         assertThat(totalTime).isGreaterThanOrEqualTo(2000);  // 최소 2초
        
//         // 예상 시간 대비 큰 차이가 없어야 함 (여유 시간 고려하여 6초 이내)
//         assertThat(totalTime).isLessThan(6000);
//     }
// }


