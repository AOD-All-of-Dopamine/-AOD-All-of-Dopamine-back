# AOD Monitoring Stack 시작 가이드

## 로컬 개발 환경 시작

### 1. 모니터링 스택 시작

```bash
cd monitoring
docker-compose -f docker-compose.monitoring-local.yml up -d
```

### 2. 서비스 확인

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **Loki**: http://localhost:3100

### 3. Crawler 애플리케이션 시작

```bash
cd -AOD-All-of-Dopamine-crawler
./gradlew bootRun
```

### 4. Grafana 접속 및 설정

1. http://localhost:3000 접속
2. 로그인: `admin` / `admin`
3. Data Sources 자동 추가 확인:
   - Prometheus (기본)
   - Loki
4. Explore 탭에서 데이터 확인

---

## Prometheus 쿼리 예시

### JVM 메모리 사용률
```promql
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

### Job Queue 크기
```promql
crawl_job_queue_size{status="PENDING"}
```

### 처리 속도 (jobs/sec)
```promql
rate(crawl_job_completed_total[5m])
```

---

## Loki 쿼리 예시

### 에러 로그만
```logql
{app="aod-crawler", level="ERROR"}
```

### 특정 Job Type
```logql
{app="aod-crawler"} |= "jobType=NAVER_SERIES_NOVEL"
```

### Timeout 발생
```logql
{app="aod-crawler", timeout="true"}
```

### 마지막 100줄 에러
```logql
{app="aod-crawler"} |= "error" | tail 100
```

---

## 종료

```bash
docker-compose -f docker-compose.monitoring-local.yml down
```

데이터 보관하면서 종료:
```bash
docker-compose -f docker-compose.monitoring-local.yml stop
```

데이터까지 삭제:
```bash
docker-compose -f docker-compose.monitoring-local.yml down -v
```
