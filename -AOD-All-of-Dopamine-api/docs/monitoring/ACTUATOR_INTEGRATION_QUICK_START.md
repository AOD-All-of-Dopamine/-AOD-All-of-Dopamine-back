# 🔥 Actuator 통합 버전 - 빠른 시작 가이드

## ✅ 변경 완료!

이제 성능 측정이 **자동으로 Prometheus/Grafana에 기록**됩니다!

---

## 🚀 1단계: 서버 실행

```powershell
cd D:\AOD\-AOD-All-of-Dopamine-back
.\gradlew.bat bootRun
```

**로그에서 확인:**
```
✅ Actuator 통합 활성화
   → Prometheus: performance_test_duration_seconds
   → Prometheus: performance_test_items_total
```

---

## 📊 2단계: 성능 테스트 실행

### A. Before/After 비교 테스트
```bash
POST http://localhost:8080/api/performance/test/compare?beforeBatchSize=100&afterBatchSize=500&iterations=5
```

### B. 개별 테스트
```bash
# Before
POST http://localhost:8080/api/performance/test/before?batchSize=100&iterations=5

# After
POST http://localhost:8080/api/performance/test/after?batchSize=500&iterations=5

# Parallel
POST http://localhost:8080/api/performance/test/parallel?totalItems=5000&batchSize=500&numWorkers=4
```

---

## 🔍 3단계: Actuator 메트릭 확인

### 1️⃣ 성능 테스트 메트릭

```bash
# 처리 시간 메트릭
GET http://localhost:8080/actuator/metrics/performance.test.duration
```

**응답 예시:**
```json
{
  "name": "performance.test.duration",
  "description": null,
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 10.0           // 10번 실행됨
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 25.6           // 총 25.6초
    },
    {
      "statistic": "MAX",
      "value": 3.2            // 최대 3.2초
    }
  ],
  "availableTags": [
    {
      "tag": "test",
      "values": ["Batch Processing", "Parallel Batch Processing"]
    },
    {
      "tag": "version",
      "values": ["BEFORE", "AFTER", "AFTER_PARALLEL"]
    }
  ]
}
```

#### 특정 버전만 조회
```bash
GET http://localhost:8080/actuator/metrics/performance.test.duration?tag=version:BEFORE
GET http://localhost:8080/actuator/metrics/performance.test.duration?tag=version:AFTER
```

---

### 2️⃣ 처리 항목 카운터

```bash
GET http://localhost:8080/actuator/metrics/performance.test.items
```

**응답 예시:**
```json
{
  "name": "performance.test.items",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 12550.0        // 총 12,550개 처리
    }
  ],
  "availableTags": [
    {
      "tag": "status",
      "values": ["success", "failed"]
    },
    {
      "tag": "version",
      "values": ["BEFORE", "AFTER"]
    }
  ]
}
```

#### 성공/실패 개별 조회
```bash
GET http://localhost:8080/actuator/metrics/performance.test.items?tag=status:success
GET http://localhost:8080/actuator/metrics/performance.test.items?tag=status:failed
```

---

### 3️⃣ 스레드풀 메트릭 (자동 수집)

```bash
# 스레드풀 활성 스레드 수
GET http://localhost:8080/actuator/metrics/executor.active?tag=name:crawlerTaskExecutor

# 큐 대기 작업 수
GET http://localhost:8080/actuator/metrics/executor.queued?tag=name:crawlerTaskExecutor

# 완료된 작업 수
GET http://localhost:8080/actuator/metrics/executor.completed?tag=name:crawlerTaskExecutor
```

---

### 4️⃣ 데이터베이스 메트릭 (자동 수집)

```bash
# HikariCP 활성 연결 수
GET http://localhost:8080/actuator/metrics/hikaricp.connections.active

# 대기 중인 연결 요청
GET http://localhost:8080/actuator/metrics/hikaricp.connections.pending

# 연결 생성 시간
GET http://localhost:8080/actuator/metrics/hikaricp.connections.creation
```

---

### 5️⃣ JVM 메트릭 (자동 수집)

```bash
# 메모리 사용량
GET http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap

# GC 시간
GET http://localhost:8080/actuator/metrics/jvm.gc.pause

# 활성 스레드 수
GET http://localhost:8080/actuator/metrics/jvm.threads.live

# CPU 사용률
GET http://localhost:8080/actuator/metrics/system.cpu.usage
```

---

## 📈 4단계: Prometheus 엔드포인트

모든 메트릭을 한 번에 Prometheus 포맷으로 조회:

```bash
GET http://localhost:8080/actuator/prometheus
```

**응답 예시:**
```
# HELP performance_test_duration_seconds  
# TYPE performance_test_duration_seconds summary
performance_test_duration_seconds_count{test="Batch Processing",version="BEFORE"} 5.0
performance_test_duration_seconds_sum{test="Batch Processing",version="BEFORE"} 12.8
performance_test_duration_seconds_max{test="Batch Processing",version="BEFORE"} 3.2

performance_test_duration_seconds_count{test="Batch Processing",version="AFTER"} 5.0
performance_test_duration_seconds_sum{test="Batch Processing",version="AFTER"} 0.65
performance_test_duration_seconds_max{test="Batch Processing",version="AFTER"} 0.15

# HELP performance_test_items_total  
# TYPE performance_test_items_total counter
performance_test_items_total{status="success",test="Batch Processing",version="BEFORE"} 500.0
performance_test_items_total{status="success",test="Batch Processing",version="AFTER"} 2500.0
performance_test_items_total{status="failed",test="Batch Processing",version="BEFORE"} 0.0
performance_test_items_total{status="failed",test="Batch Processing",version="AFTER"} 0.0

# HELP executor_active_threads  
# TYPE executor_active_threads gauge
executor_active_threads{name="crawlerTaskExecutor"} 3.0

# HELP hikaricp_connections_active  
# TYPE hikaricp_connections_active gauge
hikaricp_connections_active{pool="HikariPool-1"} 5.0

# HELP jvm_memory_used_bytes  
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="PS Eden Space"} 5.36870912E8
```

---

## 🎨 5단계: Grafana 대시보드 (선택)

### Grafana 시작
```bash
cd monitoring
docker-compose -f monitoring-compose.local.yml up -d
```

### 접속
```
http://localhost:3000
ID: admin
PW: admin
```

### Prometheus 쿼리 예시

#### 1. 처리 시간 비교 (Before vs After)
```promql
rate(performance_test_duration_seconds_sum{version="BEFORE"}[5m]) / 
rate(performance_test_duration_seconds_count{version="BEFORE"}[5m])

vs

rate(performance_test_duration_seconds_sum{version="AFTER"}[5m]) / 
rate(performance_test_duration_seconds_count{version="AFTER"}[5m])
```

#### 2. 처리 속도 (items/sec)
```promql
rate(performance_test_items_total{status="success"}[5m])
```

#### 3. 실패율
```promql
rate(performance_test_items_total{status="failed"}[5m]) / 
rate(performance_test_items_total[5m]) * 100
```

#### 4. 스레드풀 활용률
```promql
executor_active_threads{name="crawlerTaskExecutor"} / 
executor_pool_max_threads{name="crawlerTaskExecutor"} * 100
```

#### 5. 메모리 사용률
```promql
jvm_memory_used_bytes{area="heap"} / 
jvm_memory_max_bytes{area="heap"} * 100
```

---

## 📊 6단계: 결과 분석

### 자동 계산되는 통계

#### 평균 처리 시간
```bash
GET /actuator/metrics/performance.test.duration?tag=version:BEFORE
# → "MEAN" 값 확인

GET /actuator/metrics/performance.test.duration?tag=version:AFTER
# → "MEAN" 값 확인
```

#### 속도 향상 배율 계산
```
개선율 = BEFORE_MEAN / AFTER_MEAN

예: 2.56초 / 0.13초 = 19.7배 향상
```

#### 총 처리량
```bash
GET /actuator/metrics/performance.test.items?tag=status:success
# → "COUNT" 값 = 총 성공 건수
```

---

## 🎯 포트폴리오 스크린샷 체크리스트

### 1. Actuator 메트릭 응답
- [ ] `/actuator/metrics/performance.test.duration` (Before/After 비교)
- [ ] `/actuator/metrics/performance.test.items` (처리량)
- [ ] `/actuator/metrics/executor.active` (스레드풀)

### 2. Prometheus 엔드포인트
- [ ] `/actuator/prometheus` (모든 메트릭 한 번에)

### 3. Grafana 대시보드 (선택)
- [ ] 처리 시간 그래프
- [ ] 처리 속도 그래프
- [ ] 스레드풀 활용률
- [ ] 메모리 사용량

### 4. 콘솔 로그
- [ ] "✅ Actuator 통합 활성화" 로그
- [ ] "✅ Actuator 메트릭 기록 완료" 로그
- [ ] 비교 결과 포맷팅된 로그

---

## 💡 팁

### 메트릭 초기화
```bash
# 애플리케이션 재시작하면 메트릭 리셋
# 또는 Prometheus에서 시간 범위 선택으로 특정 기간만 조회
```

### 여러 번 테스트 후 통계
```bash
# 10번 실행
for ($i=1; $i -le 10; $i++) {
    curl -X POST http://localhost:8080/api/performance/test/after
    Start-Sleep -Seconds 1
}

# 결과 확인 (자동으로 COUNT=10, 평균/최대/최소 계산됨)
curl http://localhost:8080/actuator/metrics/performance.test.duration?tag=version:AFTER
```

### 실시간 모니터링
```bash
# PowerShell에서 1초마다 갱신
while ($true) {
    Clear-Host
    curl http://localhost:8080/actuator/metrics/executor.active?tag=name:crawlerTaskExecutor | ConvertFrom-Json | ConvertTo-Json -Depth 10
    Start-Sleep -Seconds 1
}
```

---

## 🎉 완료!

이제 성능 측정이 자동으로:
- ✅ Actuator 메트릭으로 수집
- ✅ Prometheus로 저장
- ✅ Grafana로 시각화
- ✅ 히스토리 관리

**모두 자동으로 동작합니다!** 🚀
