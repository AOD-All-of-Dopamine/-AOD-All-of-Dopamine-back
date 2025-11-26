# SteamRateLimiter 테스트 가이드

## 개요
`SteamRateLimiter` 클래스의 요청 제한 기능을 검증하는 테스트 코드입니다.

## 테스트 목적
- **초당 요청 제한**: 초당 최대 8개 요청 제한이 정상 동작하는지 확인
- **분당 요청 제한**: 분당 최대 120개 요청 제한이 정상 동작하는지 확인
- **대기 시간**: 제한 초과 시 적절한 대기 시간이 발생하는지 확인
- **동시성**: 여러 스레드에서 동시 요청 시에도 제한이 준수되는지 확인

## 테스트 실행 방법

### 전체 테스트 실행
```powershell
.\gradlew test --tests "com.example.AOD.game.steam.util.SteamRateLimiterTest"
```

### 개별 테스트 실행
```powershell
# 초당 요청 제한 테스트
.\gradlew test --tests "com.example.AOD.game.steam.util.SteamRateLimiterTest.testPerSecondRateLimit"

# 동시성 테스트
.\gradlew test --tests "com.example.AOD.game.steam.util.SteamRateLimiterTest.testConcurrentRequests"

# 대량 요청 테스트
.\gradlew test --tests "com.example.AOD.game.steam.util.SteamRateLimiterTest.testLargeNumberOfRequests"
```

## 테스트 케이스 설명

### 1. `testPerSecondRateLimit` - 초당 요청 제한 테스트
- **목적**: 초당 8개 요청 제한 확인
- **방법**: 10개 요청을 연속으로 보냄
- **예상 결과**: 
  - 최소 1초 이상 소요
  - 첫 1초 동안 최대 8개까지만 요청

### 2. `testPerMinuteRateLimit` - 분당 요청 제한 테스트 (간소화)
- **목적**: 분당 제한이 초당 제한과 독립적으로 동작함을 확인
- **방법**: 20개 요청을 연속으로 보냄
- **예상 결과**: 
  - 최소 2초 이상 소요 (초당 8개 제한 적용)
  - 최대 5초 이내 완료

### 3. `testExactlyMaxRequestsPerSecond` - 초당 제한 경계값 테스트
- **목적**: 정확히 8개 요청 시 대기 없이 처리
- **방법**: 정확히 8개 요청
- **예상 결과**: 1초 이내 완료 (대기 시간 없음)

### 4. `testExactlyMaxRequestsPerMinute` - 분당 제한 경계값 테스트 (간소화)
- **목적**: 일정량의 요청이 적절한 시간 내에 처리됨을 확인
- **방법**: 16개 요청
- **예상 결과**: 1~4초 사이에 완료

### 5. `testConcurrentRequests` - 동시성 테스트
- **목적**: 멀티스레드 환경에서도 제한이 정확히 준수되는지 확인
- **방법**: 5개 스레드가 각각 10개씩 요청 (총 50개)
- **예상 결과**: 
  - 모든 요청 완료
  - 어떤 1초 구간에서도 8개 초과 없음

### 6. `testReset` - 리셋 테스트
- **목적**: reset() 호출 시 카운터가 초기화되는지 확인
- **방법**: 5개 요청 후 reset() 호출
- **예상 결과**: 카운터가 0/8, 0/120으로 초기화

### 7. `testRateLimitReleaseAfterTime` - 시간 경과 후 제한 해제 확인
- **목적**: 시간이 지나면 제한이 자동으로 해제되는지 확인
- **방법**: 8개 요청 후 1.1초 대기 후 추가 요청
- **예상 결과**: 추가 요청이 즉시 처리 (대기 시간 100ms 미만)

### 8. `testLargeNumberOfRequests` - 대량 요청 테스트
- **목적**: 대량 요청 시 적절한 대기 시간 발생 확인
- **방법**: 25개 요청 연속 전송
- **예상 결과**: 
  - 최소 2초 소요
  - 최대 6초 이내 완료

## 테스트 결과 해석

### 성공 케이스
```
SteamRateLimiter 테스트 > 초당 요청 제한 테스트 - 8개 초과 시 대기 PASSED
SteamRateLimiter 테스트 > 동시성 테스트 - 여러 스레드에서 요청 시 제한 준수 PASSED
```

### 실패 케이스
- 테스트가 실패하면 로그를 확인하여 어떤 제한이 위반되었는지 확인
- 예: "첫 1초 동안의 요청 수: 10" → 초당 8개 제한 위반

## Rate Limiter 설정값

현재 `SteamRateLimiter`의 설정:
- **초당 최대 요청**: 8개 (실제 Steam API 제한: 10개)
- **분당 최대 요청**: 120개 (실제 Steam API 제한: 150개)

안전 마진을 두고 설정하여 API 제한에 걸리지 않도록 함.

## 주의사항

1. **테스트 시간**: 일부 테스트는 실제 대기 시간을 포함하므로 2-10초 정도 소요될 수 있습니다.
2. **동시성 테스트**: 멀티스레드 테스트는 최대 2분까지 소요될 수 있습니다.
3. **CI/CD 환경**: 성능이 낮은 환경에서는 타이밍이 정확하지 않을 수 있으므로 허용 범위를 조정해야 할 수 있습니다.

## 테스트 로그 확인

테스트 실행 시 다음과 같은 로그가 출력됩니다:
```
요청 1 완료: 5ms
요청 2 완료: 10ms
...
총 소요 시간: 1234ms
첫 1초 동안의 요청 수: 8
```

이 로그를 통해 실제 요청이 어떻게 처리되었는지 확인할 수 있습니다.

## 문제 해결

### Lombok 관련 에러
```
error: package lombok.extern.slf4j does not exist
```
→ `build.gradle`에 다음 추가:
```gradle
testCompileOnly 'org.projectlombok:lombok'
testAnnotationProcessor 'org.projectlombok:lombok'
```

### 테스트 타임아웃
일부 테스트가 너무 오래 걸린다면 요청 개수를 줄이거나 허용 시간을 늘려주세요.

## 추가 정보

- 테스트 클래스 위치: `src/test/java/com/example/AOD/game/steam/util/SteamRateLimiterTest.java`
- 대상 클래스: `src/main/java/com/example/AOD/game/steam/util/SteamRateLimiter.java`
