# 🧵 스레드풀 성능 측정 가이드

## 📌 개요

크롤링 작업에 사용되는 **스레드풀의 자원 관리 효과**를 측정하여 포트폴리오에 활용하기 위한 가이드입니다.

---

## 🎯 측정 목적

### 스레드풀을 사용하는 이유
1. **메모리 폭발 방지**: 무제한 스레드 생성 차단
2. **CPU 효율**: 적절한 동시 작업 수 유지
3. **안정성**: 큐를 통한 부하 조절
4. **모니터링**: 스레드 상태 추적 가능

### 포트폴리오에서 강조할 포인트
- 스레드풀 설정 전후 비교
- 과부하 상황에서의 안정성
- 메모리 사용량 제어
- 작업 처리 속도 최적화

---

## 🚀 사용 방법

### 1️⃣ 현재 스레드풀 상태 조회

```bash
GET http://localhost:8080/api/performance/threadpool/status
```

**응답 예시:**
```json
{
  "available": true,
  "metrics": {
    "testName": "Crawler Pool",
    "timestamp": "2025-11-11T14:30:00",
    "corePoolSize": 5,
    "maxPoolSize": 10,
    "queueCapacity": 200,
    "activeThreadCount": 3,
    "poolSize": 5,
    "queueSize": 12,
    "completedTaskCount": 1250,
    "totalTaskCount": 1265,
    "memoryUsageMb": 768,
    "totalJvmThreads": 45
  },
  "healthStatus": "HEALTHY",
  "healthLabel": "✅ 정상",
  "utilization": 0.3,
  "message": "Thread pool status captured"
}
```

**콘솔 로그:**
```
═══════════════════════════════════════════════════════
🧵 스레드풀 메트릭: Crawler Pool
═══════════════════════════════════════════════════════
시간: 2025-11-11T14:30:00

📐 스레드풀 설정:
   - Core Pool Size: 5
   - Max Pool Size: 10
   - Queue Capacity: 200

📊 현재 상태:
   - 활성 스레드: 3 / 10
   - 풀 크기: 5
   - 큐 대기: 12 / 200
   - 완료된 작업: 1,250
   - 총 작업: 1,265

🚀 성능:
   - 작업 처리 속도: 0.00 작업/초
   - 평균 작업 시간: 0 ms

💾 리소스:
   - 메모리 사용: 768 MB
   - 전체 JVM 스레드: 45

═══════════════════════════════════════════════════════
```

---

### 2️⃣ 스레드풀 부하 테스트

```bash
POST http://localhost:8080/api/performance/threadpool/load-test?taskCount=50&taskDurationMs=1000
```

**파라미터:**
- `taskCount`: 제출할 작업 수 (기본: 50)
- `taskDurationMs`: 각 작업의 소요 시간 (기본: 1000ms)

**응답 예시:**
```json
{
  "taskCount": 50,
  "totalDurationMs": 5240,
  "tasksPerSecond": 9.54,
  "beforeMetrics": { ... },
  "afterMetrics": { ... },
  "snapshots": [ ... ],
  "summary": "..."
}
```

**콘솔 로그:**
```
═══════════════════════════════════════════════════════
🧵 스레드풀 부하 테스트 결과
═══════════════════════════════════════════════════════

📋 테스트 설정:
   - 작업 수: 50
   - 작업 소요 시간: 1,000 ms
   - 총 소요 시간: 5,240 ms (5.24초)

🚀 처리 성능:
   - 작업 처리 속도: 9.54 작업/초
   - 평균 대기 시간: 104.80 ms

🧵 스레드풀 활용:
   - Core Pool Size: 5
   - Max Pool Size: 10
   - 최대 활성 스레드: 10
   - 큐 용량: 200
   - 최대 큐 사용: 40

💾 리소스:
   - 시작 메모리: 512 MB
   - 종료 메모리: 580 MB
   - 메모리 증가: +68 MB

═══════════════════════════════════════════════════════
```

---

## 📊 테스트 시나리오

### 시나리오 1: 정상 부하 (스레드풀 내에서 처리)

```bash
POST /api/performance/threadpool/load-test
{
  "taskCount": 20,      # Core Pool Size보다 작은 수
  "taskDurationMs": 500
}
```

**기대 결과:**
- 큐에 쌓이지 않고 바로 처리
- 활성 스레드 5개 이하 유지
- 메모리 안정적

---

### 시나리오 2: 중간 부하 (큐 활용)

```bash
POST /api/performance/threadpool/load-test
{
  "taskCount": 50,      # Max Pool Size를 초과
  "taskDurationMs": 1000
}
```

**기대 결과:**
- 큐에 작업 대기
- 활성 스레드 Max까지 증가
- 순차적으로 처리

---

### 시나리오 3: 과부하 (큐 가득참)

```bash
POST /api/performance/threadpool/load-test
{
  "taskCount": 250,     # Queue Capacity 초과
  "taskDurationMs": 2000
}
```

**기대 결과:**
- CallerRunsPolicy 동작 (호출 스레드에서 직접 실행)
- 메모리 제한적 증가 (무제한 증가 방지)
- 시스템 안정성 유지

---

## 🎨 포트폴리오 활용 방법

### 1. 스레드풀 설정 설명

```markdown
## 크롤링 스레드풀 관리

### 문제점
- Selenium WebDriver는 작업당 200~400MB 메모리 사용
- 무제한 스레드 생성 시 OutOfMemoryError 위험

### 해결책
ThreadPoolTaskExecutor 설정:
- Core Pool Size: 5
- Max Pool Size: 10 (동시 크롤링 최대 10개)
- Queue Capacity: 200 (대기 작업)
- RejectedExecutionHandler: CallerRunsPolicy

### 효과
- 메모리 사용량: 최대 4GB로 제한 (10 × 400MB)
- 과부하 시에도 안정적 처리
- 모니터링 가능
```

---

### 2. 부하 테스트 결과 표

| 작업 수 | 소요 시간 | 처리 속도 | 최대 활성 스레드 | 최대 큐 사용 | 메모리 증가 |
|--------|----------|----------|----------------|-------------|------------|
| 20     | 2.1초    | 9.5/초   | 5              | 0           | +32 MB     |
| 50     | 5.2초    | 9.6/초   | 10             | 40          | +68 MB     |
| 100    | 10.5초   | 9.5/초   | 10             | 90          | +95 MB     |
| 250    | 26.3초   | 9.5/초   | 10             | 200 (Full)  | +120 MB    |

**결론**: 
- 스레드풀 덕분에 작업 수에 관계없이 **일정한 처리 속도 유지**
- 메모리 사용량이 **선형 증가하지 않음** (제한됨)
- 큐가 가득 차도 **시스템이 안정적**

---

### 3. Before/After 비교

#### ❌ Before (스레드풀 없음)

```java
// 각 크롤링 작업마다 새 스레드 생성
for (Task task : tasks) {
    new Thread(() -> crawl(task)).start();  // 위험!
}
```

**문제점:**
- 100개 작업 → 100개 스레드 생성
- 메모리 사용: 100 × 400MB = **40GB** (불가능)
- OutOfMemoryError 발생

#### ✅ After (스레드풀 사용)

```java
// ThreadPoolTaskExecutor로 제한
for (Task task : tasks) {
    crawlerExecutor.submit(() -> crawl(task));  // 안전!
}
```

**개선:**
- 100개 작업 → 최대 10개 스레드만 사용
- 메모리 사용: 10 × 400MB = **4GB** (안정)
- 큐로 순차 처리

---

## 📈 그래프 제안

### 1. 스레드 수 vs 작업 수
- X축: 제출된 작업 수 (0~300)
- Y축: 활성 스레드 수
- 결과: 10개에서 평평 (제한 효과)

### 2. 메모리 사용량 비교
- 스레드풀 없음: 선형 증가 (위험)
- 스레드풀 있음: 제한된 증가 (안정)

### 3. 처리 속도
- 부하에 관계없이 일정한 처리 속도 유지

---

## 🔍 건강도 체크

스레드풀 상태를 자동으로 평가합니다:

- **✅ HEALTHY (정상)**: 활용률 70% 미만, 큐 50% 미만
- **⚠️ WARNING (경고)**: 활용률 70~90%, 큐 50~80%
- **🔴 CRITICAL (위험)**: 활용률 90% 이상, 큐 80% 이상

---

## 💡 측정 팁

### 1. 실제 크롤링 작업으로 테스트

```java
// 부하 테스트 대신 실제 크롤링 실행
@Async("crawlerTaskExecutor")
public void crawl() {
    // 크롤링 로직
}

// 상태 조회
GET /api/performance/threadpool/status
```

### 2. 주기적 모니터링

```java
@Scheduled(fixedRate = 10000)  // 10초마다
public void monitorThreadPool() {
    ThreadPoolMonitor.logThreadPoolStatus("Periodic Check", crawlerExecutor);
}
```

### 3. Grafana 대시보드 활용

Prometheus 메트릭으로 실시간 모니터링:
- `executor.active`
- `executor.pool.size`
- `executor.queued`

---

## 📚 관련 문서

- [AsyncConfig.java](../src/main/java/com/example/AOD/config/AsyncConfig.java) - 스레드풀 설정
- [resource-limits.md](./resource-limits.md) - 리소스 제한 가이드
- [thread-resource-issues.md](./thread-resource-issues.md) - ThreadLocal 메모리 누수 해결

---

## ✅ 체크리스트

포트폴리오 자료 준비:

- [ ] 스레드풀 상태 조회 및 스크린샷
- [ ] 부하 테스트 실행 (여러 시나리오)
- [ ] 결과를 표/그래프로 정리
- [ ] Before/After 코드 비교
- [ ] 메모리 제한 효과 강조
- [ ] README에 추가
