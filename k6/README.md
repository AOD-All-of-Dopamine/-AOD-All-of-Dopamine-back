# K6 Performance Testing for AOD Crawler

## 개요

EC2 t3.small (2 vCPU, 2GB RAM) 환경에서의 성능 테스트를 위한 K6 스크립트 모음입니다.

## 디렉토리 구조

```
k6/
├── docker-compose.yml          # K6 + InfluxDB + Grafana
├── scripts/
│   ├── crawler-smoke-test.js  # ✅ Crawler 기본 연결 테스트
│   ├── crawler-load-test.js   # ✅ Crawler 부하 테스트 (9분)
│   └── api-load-test.js        # ✅ API 서버 부하 테스트 (5분)
├── data/                        # 테스트 데이터 (필요시)
├── reports/                     # 테스트 결과 JSON
└── README.md
```

---

## 사전 준비

### 1. 테스트 대상 서버 실행

```bash
# Crawler 서버 (포트 8081)
cd -AOD-All-of-Dopamine-crawler
./gradlew bootRun

# API 서버 (포트 8080) - 선택
cd -AOD-All-of-Dopamine-api
./gradlew bootRun
```

### 2. K6 모니터링 스택 시작

```bash
cd k6
docker-compose up -d influxdb grafana
```

- **Grafana**: http://localhost:3000 (admin/admin)
- **InfluxDB**: http://localhost:8086

---

## 테스트 실행

### 1. Crawler Smoke Test (기본 연결 테스트)

**목적**: 빠른 Health Check 및 기본 연결 확인  
**부하**: 5 VUs, 30초  
**임계값**: P95 < 2초, 실패율 < 5%

```bash
docker-compose run --rm k6 run /scripts/crawler-smoke-test.js
```

**예상 결과**:
```
✓ Smoke Test Summary
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Requests:     150
Duration:     30s
Success Rate: 100%
Avg Response: 85.23ms
P95 Response: 156.78ms
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### 2. Crawler Load Test (부하 테스트)

**목적**: EC2 t3.small에서 안정성 테스트  
**부하**: 최대 20 VUs, 9분  
**시나리오**:
- 0-1분: 0 → 10 VUs (Warm up)
- 1-4분: 10 VUs 유지 (Steady)
- 4-5분: 10 → 20 VUs (Peak)
- 5-8분: 20 VUs 유지 (Stress)
- 8-9분: 20 → 0 VUs (Cool down)

**임계값**: P95 < 3초, P99 < 5초, 실패율 < 10%

```bash
docker-compose run --rm k6 run /scripts/crawler-load-test.js
```

**결과 파일**:
- `/reports/crawler-load-test.json`: 상세 메트릭
- `/reports/crawler-load-test-summary.txt`: 텍스트 요약

---

### 3. API Load Test (API 서버 테스트)

**목적**: Recommendation 및 Ranking API 성능 테스트  
**부하**: 15 VUs, 5분  
**엔드포인트**:
- `/actuator/health`
- `/api/recommendations/traditional`
- `/api/rankings/{domain}`

**임계값**: P95 < 2초, 실패율 < 5%

```bash
docker-compose run --rm k6 run /scripts/api-load-test.js
```

---

## 커스텀 실행

### 환경변수로 URL 변경

```bash
# Crawler 서버 URL 변경
docker-compose run --rm k6 run /scripts/crawler-smoke-test.js \
  -e BASE_URL=http://your-server:8081

# API 서버 URL 변경
docker-compose run --rm k6 run /scripts/api-load-test.js \
  -e BASE_URL=http://your-server:8080
```

### VUs와 Duration 조정

```bash
# VUs=30, 2분 실행
docker-compose run --rm k6 run /scripts/crawler-smoke-test.js \
  --vus 30 --duration 2m

# Stages 무시하고 간단히
docker-compose run --rm k6 run /scripts/crawler-load-test.js \
  --vus 50 --duration 3m
```

---

## Grafana 대시보드

1. Grafana 접속: http://localhost:3000
2. 로그인: `admin` / `admin`
3. Data Source 추가:
   - Type: InfluxDB
   - URL: `http://influxdb:8086`
   - Database: `k6`
4. Import Dashboard: K6 Load Testing Results (ID: 2587)

---

## 결과 분석

### 성공 기준

| 테스트 | P95 | P99 | 실패율 | VUs |
|--------|-----|-----|--------|-----|
| Smoke | < 2s | - | < 5% | 5 |
| Load | < 3s | < 5s | < 10% | 20 |
| API | < 2s | - | < 5% | 15 |

### 실패 시 체크리스트

1. **High Response Time (P95 > 3s)**
   - Chrome 좀비 프로세스 확인: `ps aux | grep chrome`
   - JVM 메모리: `/actuator/metrics/jvm.memory.used`
   - Thread 수: `/actuator/threaddump`

2. **High Failure Rate (> 10%)**
   - Application 로그 확인: `logs/crawler.log`
   - OutOfMemoryError 발생 여부
   - DB 연결 풀 고갈 확인

3. **Timeout Errors**
   - Jsoup timeout 설정 확인 (application.yml)
   - WebDriver timeout 확인
   - Network 문제 (서버 간 통신)

---

## CI/CD 연동 (선택)

### GitHub Actions 예시

```yaml
name: Performance Test
on: [push]

jobs:
  k6-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Start Crawler
        run: |
          docker-compose up -d crawler
          sleep 30  # Warm up
      
      - name: Run K6 Smoke Test
        run: |
          docker-compose -f k6/docker-compose.yml run --rm k6 \
            run /scripts/crawler-smoke-test.js
      
      - name: Upload Results
        uses: actions/upload-artifact@v2
        with:
          name: k6-results
          path: k6/reports/
```

---

## 문제 해결

### Docker network 오류

```bash
# host.docker.internal이 동작하지 않으면
docker-compose run --rm k6 run /scripts/crawler-smoke-test.js \
  -e BASE_URL=http://172.17.0.1:8081  # Docker bridge IP
```

### Permission denied (reports 폴더)

```bash
chmod -R 777 k6/reports
```

### InfluxDB 연결 실패

```bash
# InfluxDB 재시작
docker-compose restart influxdb

# 로그 확인
docker-compose logs influxdb
```

---

## 다음 단계

Phase 1 완료 후:
- [ ] Phase 2: Prometheus + Loki + Grafana 모니터링 설정
- [ ] Phase 3: Grafana 대시보드 구성
- [ ] Phase 4: Production 환경 배포

자세한 내용은 `k6-monitoring-plan.md` 참조.