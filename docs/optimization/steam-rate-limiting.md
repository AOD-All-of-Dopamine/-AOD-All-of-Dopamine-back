# Steam API Rate Limiting 구현 가이드

## 개요

Steam API는 엄격한 Rate Limit 정책을 가지고 있어, 대량의 게임 데이터를 수집할 때 적절한 속도 제한이 필수적입니다. 이 문서는 AOD 프로젝트에서 구현한 Steam API Rate Limiting 메커니즘을 설명합니다.

## Steam API Rate Limit 규칙

Steam은 다음과 같은 세 가지 레벨의 Rate Limit을 적용합니다:

| 제한 유형 | 제한 값 | 설명 |
|---------|--------|------|
| IP당 초당 요청 | 10개 | 동일 IP 주소에서 1초에 최대 10개 요청 |
| IP당 분당 요청 | 150개 | 동일 IP 주소에서 1분에 최대 150개 요청 |
| API Key당 분당 요청 | 300개 | 동일 API Key로 1분에 최대 300개 요청 |

### 가장 제한적인 규칙

실제 운영 환경에서는 **IP당 분당 150개 요청**이 가장 제한적인 규칙으로 작용합니다.

## 구현 상세

### 1. SteamRateLimiter 클래스

위치: `com.example.AOD.game.steam.util.SteamRateLimiter`

#### 안전 마진 설정

공식 제한보다 여유있게 설정하여 429(Too Many Requests) 에러를 예방합니다:

```java
// 초당 제한: 10개 → 8개로 설정 (20% 여유)
private static final int MAX_REQUESTS_PER_SECOND = 8;

// 분당 제한: 150개 → 120개로 설정 (20% 여유)
private static final int MAX_REQUESTS_PER_MINUTE = 120;
```

#### 핵심 메커니즘

**1. 슬라이딩 윈도우(Sliding Window) 방식**

고정된 시간 창이 아닌, 현재 시점 기준으로 과거 1초/1분의 요청을 추적합니다:

```java
private final ConcurrentLinkedQueue<Long> secondQueue;  // 초당 요청 타임스탬프
private final ConcurrentLinkedQueue<Long> minuteQueue;  // 분당 요청 타임스탬프
```

**2. 자동 대기 계산**

제한에 도달하면 가장 오래된 요청이 만료될 때까지 자동으로 대기합니다:

```java
// 초당 제한 도달 시
if (currentSecondRequests.get() >= MAX_REQUESTS_PER_SECOND) {
    Long oldestInSecond = secondQueue.peek();
    long waitTime = 1000 - (now - oldestInSecond);  // 남은 대기 시간 계산
    sleep(waitTime);
}
```

**3. Thread-Safe 구현**

동시성 문제를 방지하기 위한 안전한 구현:

- `synchronized` 메서드로 동시 접근 제어
- `AtomicInteger`로 카운터 안전하게 관리
- `ConcurrentLinkedQueue`로 타임스탬프 큐 관리

### 2. 적용 방법

#### SteamApiFetcher에 통합

모든 Steam API 요청 전에 Rate Limiter를 거치도록 구현:

```java
@Component
@RequiredArgsConstructor
public class SteamApiFetcher {
    private final SteamRateLimiter rateLimiter;
    
    public Map<String, Object> fetchGameDetails(Long appId) {
        // Rate Limiter를 통한 요청 제한 준수
        rateLimiter.acquirePermit();
        
        // API 요청 실행
        Map<String, Object> response = restTemplate.getForObject(APP_DETAILS_URL, Map.class, appId);
        // ...
    }
}
```

#### 기존 딜레이 제거

Rate Limiter가 동적으로 최적의 대기 시간을 계산하므로, 기존의 고정 딜레이는 제거되었습니다:

```java
// ❌ 제거된 코드
if (!InterruptibleSleep.sleep(500, TimeUnit.MILLISECONDS)) {
    break;
}

// ✅ Rate Limiter가 자동으로 처리
```

## 성능 개선

### 크롤링 속도 비교

| 구분 | 기존 방식 | Rate Limiter 적용 |
|-----|---------|-----------------|
| 요청 간격 | 고정 500ms | 동적 조절 |
| 분당 처리량 | 약 120개 | 120개 (제한에 최적화) |
| 시간당 처리량 | 약 7,200개 | 7,200개 |
| 15만 게임 수집 시간 | 약 21시간 | 약 21시간 |
| 429 에러 발생 | 가능성 있음 | 거의 없음 |

### 주요 개선사항

1. **동적 속도 조절**: 요청이 적을 때는 빠르게, 많을 때는 자동 대기
2. **에러 방지**: Rate Limit을 미리 준수하여 429 에러 최소화
3. **최적화된 처리**: 제한 범위 내에서 최대 속도로 처리
4. **안정성 향상**: 여유있는 제한 설정으로 안전성 확보

## 모니터링 및 디버깅

### 로그 출력

Rate Limiter는 다음과 같은 로그를 출력합니다:

```
DEBUG - Rate Limiter - 초당: 8/8, 분당: 115/120
WARN  - 분당 요청 제한 도달. 5234ms 대기 중...
```

### 통계 확인

프로그래밍 방식으로 현재 상태 확인 가능:

```java
String stats = rateLimiter.getStats();
// 출력: "초당: 8/8, 분당: 115/120"
```

### 상태 초기화

필요시 Rate Limiter 상태를 초기화할 수 있습니다:

```java
rateLimiter.reset();
```

## 사용 예시

### 1. 개별 게임 수집 (Admin 페이지)

```java
@PostMapping("/collect/by-appid")
public ResponseEntity<Map<String, Object>> collectGameByAppId(@RequestBody Map<String, Object> request) {
    Long appId = ((Number) request.get("appId")).longValue();
    boolean success = steamCrawlService.collectGameByAppId(appId);
    // Rate Limiter가 자동으로 처리
}
```

### 2. 대량 게임 수집

```java
@Async("crawlerTaskExecutor")
public CompletableFuture<Integer> collectAllGamesInBatches() {
    for (Map<String, Object> app : gameApps) {
        // Rate Limiter가 각 요청 전에 자동으로 제한 관리
        Map<String, Object> gameDetails = steamApiFetcher.fetchGameDetails(appId);
    }
}
```

## 429 에러 처리

Rate Limiter를 통과하더라도 네트워크 지연 등으로 429 에러가 발생할 수 있습니다. 이를 위한 백업 메커니즘:

```java
catch (HttpClientErrorException.TooManyRequests e) {
    retryCount++;
    log.warn("Steam API Rate Limit exceeded for AppID {}. Retry {}/{}. Waiting 60 seconds...", 
             appId, retryCount, maxRetries);
    Thread.sleep(60000);  // 60초 대기 후 재시도 (최대 3회)
}
```

## 멀티 인스턴스 환경

여러 서버나 인스턴스에서 동시에 크롤링할 경우 주의사항:

1. **IP 기반 제한**: 같은 IP를 사용하는 모든 인스턴스가 제한을 공유
2. **권장 방식**: 
   - 각 인스턴스에서 서로 다른 범위를 할당하여 크롤링
   - 전체 제한을 인스턴스 수로 나누어 설정
   
예: 2개 인스턴스 운영 시
```java
private static final int MAX_REQUESTS_PER_MINUTE = 60;  // 120 / 2
```

## 참고 자료

- [Steam Web API Documentation](https://partner.steamgames.com/doc/webapi/IStoreService)
- [Steam Rate Limiting 실험 블로그](https://velog.io/@kjjdsa/%EC%8A%A4%ED%8C%80-%EA%B2%8C%EC%9E%84%EA%B3%BC-%EB%A6%AC%EB%B7%B0-%ED%81%AC%EB%A1%A4%EB%A7%81)

## 향후 개선 방향

1. **분산 Rate Limiter**: Redis 기반 중앙 집중식 Rate Limiter 구현
2. **동적 조절**: 실제 에러 발생률을 모니터링하여 제한값 자동 조정
3. **우선순위 큐**: 중요한 요청에 높은 우선순위 부여
4. **백프레셔(Backpressure)**: 다운스트림 처리 속도에 맞춰 자동 조절
