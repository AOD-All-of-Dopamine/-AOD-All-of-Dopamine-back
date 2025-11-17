# 🔥 통합 전후 실제 사용 비교

## 📌 시나리오: 배치 처리 성능 테스트

---

## 1️⃣ 커스텀만 사용 (현재)

### 코드
```java
@PostMapping("/test/before")
public PerformanceTestResult testBefore() {
    // 커스텀 측정 시작
    var session = PerformanceMonitor.startSession("Batch", "BEFORE");
    
    // 처리
    int processed = batchService.processBatch(100);
    session.recordBatch(processed, processed, 0);
    
    // 결과 반환
    return PerformanceTestResult.builder()
            .metrics(session.finish())
            .build();
}
```

### 실행
```bash
curl -X POST http://localhost:8080/api/performance/test/before
```

### 결과
```json
{
  "metrics": {
    "durationMs": 2560,
    "throughputPerSecond": 1167,
    "successItems": 1000
  }
}
```

### 로그
```
🔬 성능 측정 시작: Batch (BEFORE)
   시작 시간: 2025-11-11T14:30:00
   시작 메모리: 512 MB
...
✅ 배치 처리 완료
```

### 확인 가능한 것
- ✅ API 응답으로 측정 결과 확인
- ✅ 콘솔에서 포맷팅된 로그 확인
- ❌ Grafana에서 확인 불가
- ❌ 과거 기록 조회 불가
- ❌ 알림 설정 불가

---

## 2️⃣ Actuator 통합 사용 (NEW)

### 코드 (거의 동일, 한 줄만 변경)
```java
@PostMapping("/test/before")
public PerformanceTestResult testBefore() {
    // ❌ var session = PerformanceMonitor.startSession("Batch", "BEFORE");
    // ✅ var session = actuatorMonitor.startSession("Batch", "BEFORE");  // 이것만 변경!
    
    // 나머지 코드는 동일
    int processed = batchService.processBatch(100);
    session.recordBatch(processed, processed, 0);
    
    return PerformanceTestResult.builder()
            .metrics(session.finish())
            .build();
}
```

### 실행 (동일)
```bash
curl -X POST http://localhost:8080/api/performance/test/before
```

### 결과 1: API 응답 (기존과 동일)
```json
{
  "metrics": {
    "durationMs": 2560,
    "throughputPerSecond": 1167,
    "successItems": 1000
  }
}
```

### 결과 2: 로그 (추가 정보 포함)
```
🔬 성능 측정 시작 (통합 모드): Batch (BEFORE)
   시작 시간: 2025-11-11T14:30:00
   시작 메모리: 512 MB
   ✅ Actuator 통합 활성화              ← NEW!
...
✅ 배치 처리 완료
   ✅ Actuator 메트릭 기록 완료          ← NEW!
      → Prometheus: performance_test_duration_seconds
      → Prometheus: performance_test_items_total
```

### 결과 3: Actuator 메트릭 (NEW!)
```bash
# 새로운 메트릭 자동 생성됨
curl http://localhost:8080/actuator/metrics/performance.test.duration

{
  "name": "performance.test.duration",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 10.0           # 10번 실행됨
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 25.6           # 총 25.6초 소요
    },
    {
      "statistic": "MAX",
      "value": 3.2            # 최대 3.2초
    }
  ],
  "availableTags": [
    {
      "tag": "version",
      "values": ["BEFORE", "AFTER"]
    }
  ]
}
```

### 결과 4: Prometheus 엔드포인트 (NEW!)
```bash
curl http://localhost:8080/actuator/prometheus

# 출력
performance_test_duration_seconds_count{test="Batch",version="BEFORE"} 10.0
performance_test_duration_seconds_sum{test="Batch",version="BEFORE"} 25.6
performance_test_duration_seconds_max{test="Batch",version="BEFORE"} 3.2

performance_test_items_total{test="Batch",version="BEFORE",status="success"} 10000.0
performance_test_items_total{test="Batch",version="BEFORE",status="failed"} 5.0
```

### 결과 5: Grafana 대시보드 (NEW!)
```
http://localhost:3000

[대시보드 예시]
┌──────────────────────────────────────────┐
│ 배치 처리 속도 (최근 1시간)              │
│                                          │
│ 1200 ┤     ╭──╮                         │
│ 1000 ┤  ╭──╯  ╰──╮                      │
│  800 ┤╭─╯        ╰─╮                    │
│  600 ┤╯            ╰─                   │
│      └────────────────────────────────  │
│      14:00  14:30  15:00  15:30        │
└──────────────────────────────────────────┘

[알림 설정 가능]
⚠️  처리 시간 > 5초 → Slack 알림
🔴 실패율 > 1%    → Email 알림
```

---

## 📊 비교표

| 기능 | 커스텀만 | Actuator 통합 |
|-----|---------|---------------|
| **사용 난이도** | 쉬움 | 쉬움 (한 줄만 변경) |
| **API 응답** | ✅ | ✅ (동일) |
| **포맷팅 로그** | ✅ | ✅ (동일) |
| **Actuator 메트릭** | ❌ | ✅ |
| **Prometheus 수집** | ❌ | ✅ |
| **Grafana 대시보드** | ❌ | ✅ |
| **히스토리 조회** | ❌ | ✅ |
| **알림 설정** | ❌ | ✅ |
| **통계 (P95, MAX)** | ❌ | ✅ 자동 |

---

## 🎯 실전 예시

### 상황: 10번 테스트 실행 후 결과 분석

#### ❌ 커스텀만 사용
```bash
# 10번 실행
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/performance/test/before > result_$i.json
done

# 분석을 위해 수동으로 처리 필요
cat result_*.json | jq '.metrics.durationMs' | awk '{sum+=$1} END {print sum/NR}'
# → 평균 계산: 2560ms
```

**문제점:**
- 수동으로 JSON 파싱
- 평균/최대/최소 직접 계산
- 그래프 못 그림

---

#### ✅ Actuator 통합
```bash
# 10번 실행 (동일)
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/performance/test/before
done

# 분석은 Actuator가 자동으로!
curl http://localhost:8080/actuator/metrics/performance.test.duration
```

**응답 (자동 계산됨):**
```json
{
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 10.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 25.6
    },
    {
      "statistic": "MEAN",
      "value": 2.56        // ✅ 평균 자동 계산
    },
    {
      "statistic": "MAX",
      "value": 3.2         // ✅ 최대값 자동
    }
  ]
}
```

**Prometheus 쿼리:**
```promql
# 5분 이동 평균
rate(performance_test_duration_seconds_sum[5m]) / 
rate(performance_test_duration_seconds_count[5m])

# P95 레이턴시
histogram_quantile(0.95, performance_test_duration_seconds_bucket)
```

**Grafana에서 자동 그래프화!**

---

## 💡 결론

### 통합하면 추가로 얻는 것

1. **히스토리 관리**
   - 과거 모든 실행 기록 저장
   - 시간대별 추이 확인

2. **자동 통계**
   - 평균/최대/최소/P95/P99
   - 수동 계산 불필요

3. **시각화**
   - Grafana 실시간 그래프
   - 대시보드 자동 업데이트

4. **알림**
   - 성능 저하 시 자동 알림
   - Slack/Email 연동

5. **운영 모니터링**
   - 실제 서비스 운영 중 성능 추적
   - 문제 조기 발견

### 추가 작업은?
- 코드 변경: **1줄** (PerformanceMonitor → PerformanceMonitorWithActuator)
- 의존성 추가: **이미 있음** (Micrometer는 Spring Boot Actuator에 포함)
- 설정 추가: **필요없음** (이미 application.properties에 있음)

### 포트폴리오 효과
```markdown
# Before
- 성능 측정 API 구현

# After  
- 성능 측정 API 구현
- Prometheus/Grafana 모니터링 시스템 구축
- 실시간 대시보드 및 알림 시스템
```

**→ 훨씬 더 풍부한 이력서/포트폴리오!**
