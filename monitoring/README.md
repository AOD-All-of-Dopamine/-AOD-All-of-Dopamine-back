# AOD Monitoring Stack

Prometheus + Grafana + Loki + Promtail + Alertmanager. 두 앱(api :8080 / crawler :8081)이 `/actuator/prometheus`로 내보내는 Micrometer 메트릭과 crawler 로그를 수집한다.

> 모든 알람/대시보드는 **코드가 실제로 내보내는 메트릭만** 참조한다. 앱 구분은 Prometheus `job` 라벨(`job="api"` / `job="crawler"`)로 한다.

## 구성 파일
| 파일 | 용도 |
|------|------|
| `prometheus-local.yml` / `prometheus-prod.yml` | Prometheus 스크랩 설정 (로컬/프로드) |
| `alert-rules.yml` | 통합 알람 룰 (실제 메트릭 기반) |
| `alertmanager.yml` | 알람 라우팅 + Slack 수신자(`${SLACK_WEBHOOK_URL}`) |
| `grafana/dashboards/aod-overview-dashboard.json` | 통합 대시보드 (자동 프로비저닝, 27패널) |
| `grafana/datasources/datasources.yml` | Prometheus(uid `prometheus`) / Loki(uid `loki`) |
| `loki-config.yml` | Loki (retention 7일) |
| `promtail-config-aod.yml` | crawler.log 수집 |

## 로컬 실행
```bash
cd monitoring
docker compose -f docker-compose.monitoring-local.yml up -d
```
- Prometheus http://localhost:9090 · Grafana http://localhost:3000 (admin/admin) · Loki http://localhost:3100
- Grafana 접속 시 **"AOD Overview"** 대시보드와 Prometheus/Loki 데이터소스가 자동 등록됨
- 크롤러 실행: `cd -AOD-All-of-Dopamine-crawler && ./gradlew bootRun`

## 프로덕션 배포
앱 2대가 **서로 다른 EC2**에 있으므로, 모니터링 스택은 한 호스트에서 두 앱을 private IP로 스크랩한다.

1. `prometheus-prod.yml`의 `API_PRIVATE_IP:8080` / `CRAWLER_PRIVATE_IP:8081`을 실제 EC2 private IP로 교체. 각 앱 SG가 Prometheus 호스트의 `:8080`/`:8081` 인바운드를 허용해야 함 (**actuator는 공개 금지**).
2. 환경변수(또는 compose 옆 `.env`) 설정:
   ```bash
   export GF_ADMIN_USER=...       GF_ADMIN_PASSWORD=...      # Grafana 관리자
   export SLACK_WEBHOOK_URL=...                              # 알람 Slack (없으면 Alertmanager UI에만 표시)
   ```
3. 실행:
   ```bash
   cd monitoring
   docker compose -f docker-compose.monitoring-prod.yml up -d
   ```
4. **보안**: 9090/9093/3000/3100 포트는 host 방화벽/EC2 SG로 외부 차단 (특히 Grafana 3000).

> ⚠️ **로그(Loki) 한계**: `promtail`은 같은 호스트의 `../logs/crawler.log`를 읽는다. 모니터링을 크롤러와 **다른** 호스트에서 돌리면 크롤러 로그가 안 들어온다 → 모니터링을 크롤러 호스트에 두거나 로그를 중앙으로 ship해야 함. (api는 현재 파일 로그를 안 써서 Loki에 안 들어옴 — 필요 시 api에 logback 파일 appender 추가)

## 실제 존재하는 주요 메트릭
- **자동(양쪽 앱)**: `http_server_requests_seconds_*`(uri별 p95), `jvm_memory_*`, `jvm_gc_pause_seconds_*`, `process_cpu_usage`/`system_cpu_usage`, `jvm_threads_live_threads`, `hikaricp_connections_*`, `logback_events_total`, `up`
- **크롤러 커스텀**: `crawl_job_queue_size{status}`, `crawl_job_completed_total{job_type}`, `crawl_job_failed_total{job_type}`, `crawl_job_duration_seconds{job_type}`
- **크롤러(NaverWebtoon만 채워짐)**: `crawler_success_total`, `crawler_failure_total`, `crawler_items_processed_total`

### PromQL 예시
```promql
# API uri별 p95 지연
histogram_quantile(0.95, sum by (uri, le) (rate(http_server_requests_seconds_bucket{job="api"}[5m])))
# 큐 적재량(대기)
crawl_job_queue_size{status="PENDING"}
# 작업 처리율 (jobs/sec)
rate(crawl_job_completed_total{job="crawler"}[5m])
# JVM 힙 사용률
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

### LogQL 예시 (crawler)
```logql
{job="crawler"}                  # 전체
{job="crawler", level="ERROR"}   # 에러만
{job="crawler", error="true"}    # error/exception/failed 패턴
{job="crawler", oom="true"}      # OOM
```

## 종료
```bash
docker compose -f docker-compose.monitoring-local.yml down      # 데이터 유지
docker compose -f docker-compose.monitoring-local.yml down -v   # 데이터까지 삭제
```
