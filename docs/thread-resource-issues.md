# 크롤링 스레드 및 자원 관리 문제점 분석

## 발견된 문제점

### 1. ThreadLocal 메모리 누수 위험 (심각도: 높음)
**위치:** `NaverWebtoonSeleniumPageParser.java`

**문제:**
- ThreadLocal에 WebDriver를 저장하지만, @Async 작업 완료 후 cleanup이 보장되지 않음
- 스레드풀 환경에서 스레드가 재사용되면서 ThreadLocal이 정리되지 않아 메모리 누수 발생

**현재 코드:**
```java
@Async
public CompletableFuture<Integer> crawlAllWeekdays() {
    try {
        int totalSaved = naverWebtoonCrawler.crawlAllWeekdays();
        return CompletableFuture.completedFuture(totalSaved);
    } catch (Exception e) {
        return CompletableFuture.failedFuture(e);
    }
    // cleanup() 호출 없음!
}
```

**해결방안:**
```java
@Async
public CompletableFuture<Integer> crawlAllWeekdays() {
    try {
        int totalSaved = naverWebtoonCrawler.crawlAllWeekdays();
        return CompletableFuture.completedFuture(totalSaved);
    } catch (Exception e) {
        return CompletableFuture.failedFuture(e);
    } finally {
        // ThreadLocal 자원 정리 보장
        if (naverWebtoonCrawler.getPageParser() instanceof NaverWebtoonSeleniumPageParser) {
            ((NaverWebtoonSeleniumPageParser) naverWebtoonCrawler.getPageParser()).cleanup();
        }
    }
}
```

---

### 2. @Async 기본 Executor 미설정 (심각도: 중간)
**위치:** `NaverWebtoonService`, `SteamCrawlService`, `TmdbSchedulingService`

**문제:**
- @Async 어노테이션에 executor를 지정하지 않아 SimpleAsyncTaskExecutor 사용
- 매 요청마다 새 스레드 생성 → 리소스 낭비 및 성능 저하

**해결방안:**
1. 전역 AsyncConfigurer 설정:

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("Crawler-Async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("비동기 작업 예외 발생 - 메서드: {}, 파라미터: {}, 오류: {}", 
                method.getName(), Arrays.toString(params), ex.getMessage(), ex);
        };
    }
}
```

2. 또는 각 @Async에 명시적으로 지정:

```java
@Async("crawlerTaskExecutor")
public CompletableFuture<Integer> crawlAllWeekdays() { ... }
```

---

### 3. WebDriver 재생성 시 예외 처리 미흡 (심각도: 중간)
**위치:** `NaverWebtoonSeleniumPageParser.getOrCreateDriver()`

**문제:**
- driver.quit() 중 예외 발생 시 ThreadLocal에 잘못된 상태가 남을 수 있음

**현재 코드:**
```java
if (driver != null) {
    try {
        driver.quit();
        log.debug("WebDriver 재생성 (사용 횟수: {}회)", count);
    } catch (Exception e) {
        log.warn("기존 WebDriver 종료 실패: {}", e.getMessage());
    }
}
driver = chromeDriverProvider.getDriver();
driverThreadLocal.set(driver);
usageCount.set(0);
```

**해결방안:**
```java
if (driver != null) {
    try {
        driver.quit();
        log.debug("WebDriver 재생성 (사용 횟수: {}회)", count);
    } catch (Exception e) {
        log.warn("기존 WebDriver 종료 실패: {}", e.getMessage());
    } finally {
        // 실패 여부와 관계없이 ThreadLocal 정리
        driverThreadLocal.remove();
        usageCount.remove();
    }
}

try {
    driver = chromeDriverProvider.getDriver();
    driverThreadLocal.set(driver);
    usageCount.set(0);
} catch (Exception e) {
    log.error("새 WebDriver 생성 실패: {}", e.getMessage());
    throw new RuntimeException("WebDriver 초기화 실패", e);
}
```

---

### 4. Scheduled 작업 중복 실행 방지 필요 (심각도: 낮음)
**위치:** `TmdbSchedulingService`

**문제:**
- 이전 작업이 완료되지 않았는데 다음 스케줄 시작 가능
- 동시에 같은 데이터 크롤링하여 리소스 낭비

**해결방안:**
```java
private final AtomicBoolean isRunning = new AtomicBoolean(false);

@Async
@Scheduled(cron = "0 0 4 * * *")
public CompletableFuture<Void> collectNewContentDaily() {
    if (!isRunning.compareAndSet(false, true)) {
        log.warn("이전 신규 콘텐츠 수집 작업이 아직 진행 중입니다. 스킵합니다.");
        return CompletableFuture.completedFuture(null);
    }
    
    try {
        // ... 기존 로직
    } finally {
        isRunning.set(false);
    }
}
```

또는 Spring의 built-in 기능 사용:
```properties
# application.properties
spring.task.scheduling.pool.size=2
```

---

### 5. ChromeDriver 프로세스 좀비화 방지 (심각도: 중간)
**위치:** `ChromeDriverProvider`

**문제:**
- cleanup 미호출 시 Chrome 프로세스가 남아있을 수 있음
- 장시간 운영 시 메모리 및 프로세스 누적

**해결방안:**
1. Application 종료 시 전역 정리:

```java
@Component
@Slf4j
public class WebDriverCleanupListener {
    
    @PreDestroy
    public void cleanupAllDrivers() {
        log.info("애플리케이션 종료 - 모든 WebDriver 정리 시작");
        
        // 실행 중인 모든 크롬 프로세스 강제 종료 (Linux)
        try {
            Runtime.getRuntime().exec("pkill -f chrome");
            Runtime.getRuntime().exec("pkill -f chromedriver");
        } catch (Exception e) {
            log.warn("Chrome 프로세스 정리 실패: {}", e.getMessage());
        }
    }
}
```

2. Docker 환경에서는 컨테이너 재시작으로 자동 정리되므로 덜 심각

---

### 6. 크롤링 중 인터럽트 처리 개선 (심각도: 낮음)
**위치:** `NaverWebtoonCrawler`

**현재 상태:** 양호함
- `InterruptibleSleep` 사용으로 인터럽트 처리 잘 되어 있음
- Thread.currentThread().interrupt() 상태 복원도 잘 됨

**추가 개선사항:**
```java
// 크롤링 시작 시 인터럽트 플래그 초기화
Thread.interrupted(); // 이전 인터럽트 상태 클리어
```

---

## 우선순위별 조치 계획

### 🔴 High Priority (즉시 수정 필요)
1. ✅ ThreadLocal cleanup을 @Async 메서드의 finally 블록에 추가
2. ✅ 전역 AsyncConfigurer 설정하여 스레드풀 관리

### 🟡 Medium Priority (1주 내 수정)
3. ✅ WebDriver 재생성 시 예외 처리 강화
4. ✅ Application 종료 시 WebDriver 정리 로직 추가

### 🟢 Low Priority (리팩토링 시 고려)
5. Scheduled 작업 중복 실행 방지
6. 크롤링 메트릭 및 모니터링 강화

---

## 테스트 계획

### 1. ThreadLocal 메모리 누수 테스트
```java
@Test
void testThreadLocalCleanup() {
    ExecutorService executor = Executors.newFixedThreadPool(3);
    
    for (int i = 0; i < 100; i++) {
        executor.submit(() -> {
            try {
                naverWebtoonService.crawlWeekdaySync("mon");
            } finally {
                // ThreadLocal이 정리되었는지 확인
                assertNull(parser.getDriverThreadLocal().get());
            }
        });
    }
    
    executor.shutdown();
}
```

### 2. 동시성 스트레스 테스트
```bash
# JMeter 또는 Gatling으로 동시 크롤링 요청
# - 50개 동시 요청
# - 메모리 누수 모니터링
# - Chrome 프로세스 개수 확인
```

### 3. 장시간 운영 테스트
```bash
# 24시간 동안 주기적 크롤링
# - 메모리 사용량 그래프
# - 스레드 개수 추이
# - WebDriver 프로세스 모니터링
```

---

## 참고 자료

- [Spring @Async Best Practices](https://spring.io/guides/gs/async-method/)
- [ThreadLocal Memory Leak in Thread Pools](https://www.baeldung.com/java-memory-leaks#threadlocal)
- [Selenium WebDriver Lifecycle Management](https://www.selenium.dev/documentation/webdriver/drivers/)
