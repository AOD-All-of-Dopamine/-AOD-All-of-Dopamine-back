# 7. 모니터링 (Monitoring)

> **이 문서를 먼저 읽어라.** 모니터링 관련 작업(대시보드/알람/메트릭 추가·수정)을 하기 전 반드시 이 문서를 읽고 시작한다.
> 빠른 실행 방법은 [`monitoring/README.md`](../monitoring/README.md), 이 문서는 **아키텍처·메트릭 계약·확장 방법·설계 배경**을 다룬다.

## ⛔ 제1원칙 (이것만 지키면 90%는 안전)

> **대시보드/알람/PromQL은 "코드가 실제로 내보내는 메트릭"만 참조한다.**

과거 이 모니터링 스택 전체가 망가졌던 단 하나의 근본 원인이 이것이다. 대시보드·알람·문서가 `crawl_job_total`, `webdriver_timeout_total`, `crawling_success_total`, `api_errors_total`, `performance_test_*`, `node_filesystem_*` 같은 **어떤 코드도 emit하지 않는 유령 메트릭**을 참조해서, 거의 모든 패널이 "No data"이고 모든 알람이 영원히 안 울렸다.

**새 메트릭을 PromQL에 쓰기 전, 반드시 `/actuator/prometheus`에서 그 이름이 실제로 나오는지 확인하라.** (방법은 §9 참고)

---

## 1. 아키텍처 (통합 구조)

두 앱은 **서로 다른 EC2**에 있지만, 모니터링은 **하나로 통합**해서 본다.

```
   [api EC2 :8080]   ──┐  (HTTP pull: GET /actuator/prometheus)
                       ├──→  Prometheus  ──→  Grafana  ("AOD Overview" 대시보드 1개)
   [crawler EC2 :8081]─┘     │                ↑
                             └──→ Alertmanager │  (Loki 데이터소스)
   [crawler EC2] Promtail ───────────────→ Loki ┘   (로그 push)
```

- **Prometheus = pull 방식**: Prometheus가 각 앱의 `/actuator/prometheus`를 긁어간다. 앱은 Prometheus에 연결을 시도하지 않는다 → **모니터링 스택이 없어도 앱은 정상 동작**한다(그냥 아무도 안 긁을 뿐).
- 한 Prometheus가 **2개 scrape job**(`job="api"`, `job="crawler"`)으로 두 앱을 수집한다.
- 앱 구분은 **Prometheus `job` 라벨**(`api`/`crawler`)로 한다. (Micrometer의 `application` 태그는 api=`AOD`, crawler=`aod-crawler`로 불일치하므로 **쓰지 않는다**.)
- 대시보드는 1개(`AOD Overview`)에서 둘을 같이 본다. 공통 지표(JVM/CPU/메모리)는 두 앱을 나란히, API 지연·크롤러 작업큐처럼 한쪽에만 의미 있는 건 해당 job만 필터링.

---

## 2. 파일 구조 (`monitoring/`)

| 파일 | 역할 |
|------|------|
| `prometheus-prod.yml` | **프로드** Prometheus 설정. api/crawler 두 job 스크랩 (타깃은 private IP placeholder) |
| `prometheus-local.yml` | **로컬** Prometheus 설정 (Docker Desktop, `host.docker.internal`) |
| `alert-rules.yml` | **통합** 알람 룰 (12개, 전부 실제 메트릭) |
| `alertmanager.yml` | 알람 라우팅 + inhibit + Slack 수신자(`${SLACK_WEBHOOK_URL}`) |
| `grafana/dashboards/aod-overview-dashboard.json` | **유일한** 대시보드 "AOD Overview" (27패널, 자동 프로비저닝) |
| `grafana/dashboards/dashboard-provisioning.yml` | 대시보드 자동 등록 provider |
| `grafana/datasources/datasources.yml` | Prometheus(uid `prometheus`) / Loki(uid `loki`) 프로비저닝 |
| `loki-config.yml` | Loki (retention 7일 + compactor) |
| `promtail-config-aod.yml` | crawler.log 수집 → Loki push (`job: crawler`, `app: aod-crawler` 라벨) |
| `docker-compose.monitoring-{local,prod}.yml` | 스택 기동 (services: prometheus/grafana/loki/promtail/alertmanager) |
| `README.md` | 실행/배포 런북 |

> ⚠️ **구형 트리**: `-AOD-All-of-Dopamine-api/monitoring/`는 과거 api-era 잔재(유령 메트릭 대시보드·알람·compose)다. **현재 스택은 이걸 안 쓴다.** 삭제 권장. 헷갈리면 `back/monitoring/`만 본다.

---

## 3. 메트릭 계약 (THE CONTRACT) ⭐

모든 대시보드/알람은 아래 이름만 참조한다. 라벨 필터는 항상 `{job="api"}` 또는 `{job="crawler"}`.

### 3-1. 자동 메트릭 (양쪽 앱 자동 emit — Micrometer + actuator)
| 메트릭 | 용도 |
|--------|------|
| `http_server_requests_seconds_bucket` / `_count` / `_sum` | HTTP 요청 (uri/method/status/outcome). **히스토그램 ON** → uri별 p95 가능 |
| `jvm_memory_used_bytes` / `jvm_memory_max_bytes` | 힙/논힙 (area, id) |
| `jvm_gc_pause_seconds_sum` / `_count` | GC |
| `process_cpu_usage` / `system_cpu_usage` | CPU (0~1) |
| `jvm_threads_live_threads` / `jvm_threads_daemon_threads` | 스레드 (**`_threads` 접미사 필수**) |
| `hikaricp_connections_active` / `_max` / `_pending` | DB 커넥션풀 |
| `tomcat_threads_busy_threads` / `_current_threads` | Tomcat |
| `logback_events_total{level}` | 로그 레벨별 카운트 |
| `up{job}` | 스크랩 성공(1)/실패(0) |

활성화 위치: 크롤러 `src/main/resources/application.yml`(`management.*`, `metrics.distribution.percentiles-histogram.http.server.requests=true`), api `src/main/resources/application.properties`(동일). actuator exposure에 `prometheus` 포함.

### 3-2. 커스텀 — 크롤 작업큐 (`job="crawler"` 전용)
코드: `crawler/common/queue/CrawlJobMetrics.java` + `CrawlJobConsumer`가 호출.
| 메트릭 | 타입 | 라벨 | emit 지점 |
|--------|------|------|-----------|
| `crawl_job_queue_size` | gauge | `status` | `CrawlJobMetrics.refreshQueueGauges()` (30초마다 `getStatusStatistics()` 집계) |
| `crawl_job_completed_total` | counter | `job_type` | `CrawlJobConsumer.processJob()` 성공 시 |
| `crawl_job_failed_total` | counter | `job_type` | `processJob()` 실패/예외 시 |
| `crawl_job_duration_seconds` | timer(histogram) | `job_type` | `processJob()` finally |

- `status` 값 = `JobStatus` enum (PENDING/PROCESSING/COMPLETED/RETRY/FAILED/SKIPPED)
- `job_type` 값 = `JobType` enum (STEAM_GAME/TMDB_MOVIE/TMDB_TV/NAVER_WEBTOON/... )
- 작업 에러율 PromQL: `sum(rate(crawl_job_failed_total[10m])) / clamp_min(sum(rate(crawl_job_completed_total[10m])) + sum(rate(crawl_job_failed_total[10m])), 1)`

### 3-3. 커스텀 — 플랫폼 크롤링 (`job="crawler"`, ⚠️ NaverWebtoon만 채워짐)
코드: `crawler/monitoring/CustomMetrics.java`. **현재 `NaverWebtoonService`에서만 호출**되므로 다른 플랫폼은 빈 시리즈다.
- `crawler_success_total` / `crawler_failure_total{reason}` / `crawler_items_processed_total{platform}` / `crawler_success_by_platform_total{platform}` / `crawler_failure_by_platform_total{platform}`
- 확장하려면 Steam/TMDB/소설 서비스·`CrawlJobConsumer`에도 `CustomMetrics` 주입·호출 필요 (§10).

### 3-4. ❌ 절대 쓰지 말 것 (유령 — 어떤 코드도 emit 안 함)
`crawl_job_total`, `webdriver_timeout_total`, `crawling_*_total`, `api_errors_total`, `db_connection_errors_total`, `db_query_errors_total`, `performance_test_*`, `node_filesystem_*`(node_exporter 미배포), `up{job="spring-boot-app"}`(존재하는 job명은 api/crawler).

### 3-5. Micrometer → Prometheus 이름 변환 규칙
코드에서 `a.b.c`로 등록하면 Prometheus에선 `a_b_c`가 된다. Counter는 `_total` 접미사, Timer는 `_seconds`(+`_bucket`/`_count`/`_sum`)가 붙는다.
예: `crawl.job.completed`(Counter) → `crawl_job_completed_total`, `crawl.job.duration`(Timer) → `crawl_job_duration_seconds_*`.

---

## 4. 대시보드 "AOD Overview"

`grafana/dashboards/aod-overview-dashboard.json` (27패널). Grafana 기동 시 자동 등록(import 불필요). 데이터소스는 uid `prometheus`/`loki` 하드코딩.

| 섹션 | 내용 | job |
|------|------|-----|
| 1. Overview | api/crawler UP, 총 요청률, 5xx 에러율 | 혼합 |
| 2. API Latency | p50/p95/p99, 상태별 요청률, **uri별 p95 테이블** | api |
| 3. JVM & System | 힙/GC/CPU/스레드 (앱별 시리즈) | 둘 다 |
| 4. DB Pool | Hikari active/max/pending | 둘 다 |
| 5. Crawler Jobs | 큐 상태별, 완료/실패율, 에러비율, duration p95, 처리율 | crawler |
| 6. Logs | 에러로그율, Crawler 로그(Loki) | crawler |

---

## 5. 알람 + Alertmanager

`alert-rules.yml` (12개): `ServiceDown`, `HighHeapUsage`(warn>0.90/crit>0.95), `HighCPU`, `HighThreadCount`, `LongGCPause`, `HighErrorRate`(api 5xx 비율), `HighLatencyP95`(api), `HikariPoolExhaustion`, `CrawlJobBacklog`(PENDING>1000), `HighCrawlJobFailureRate`(>0.30), `CrawlJobStalled`.

`alertmanager.yml`: alertname+job 그룹핑, severity 라우팅, **inhibit_rule**(critical이 같은 warning 억제), Slack 수신자 템플릿.
- Slack 쓰려면 ① `alertmanager.yml`의 `${SLACK_WEBHOOK_URL}` 환경변수 설정 ② alertmanager 컨테이너에 `--config.expand-env=true` 플래그(프로드 compose에 이미 추가됨). 둘 다 없으면 알람은 Alertmanager UI(:9093)에만 보인다.

---

## 6. 로그 (Loki / Promtail)

- `promtail-config-aod.yml`가 `crawler.log`를 정규식 파싱(timestamp/thread/level/logger/message) → Loki push. 라벨: `job=crawler`, `app=aod-crawler`, `level`, `error/timeout/oom`(메시지 패턴).
- LogQL: `{job="crawler"}`, `{job="crawler", level="ERROR"}`, `{job="crawler", error="true"}`.

**한계 (작업 시 인지):**
1. **api는 파일 로그를 안 쓴다** → Loki에 api 로그 없음. 필요하면 api에 logback 파일 appender 추가.
2. **멀티호스트**: Promtail은 같은 호스트의 `../logs`를 읽는다. 모니터링을 크롤러와 다른 EC2에서 돌리면 크롤러 로그가 안 들어온다 → 모니터링을 크롤러 호스트에 colocate하거나 로그 중앙화 필요.
3. `thread` 라벨은 고카디널리티(스레드명 다양) → 스트림 폭증 위험. 필요 시 라벨에서 제거.

---

## 7. 배포

### 로컬
```bash
cd monitoring && docker compose -f docker-compose.monitoring-local.yml up -d
# Grafana http://localhost:3000 (admin/admin) → "AOD Overview" 자동
```

### 프로드 (배포 전 TODO)
1. `prometheus-prod.yml`의 `API_PRIVATE_IP:8080` / `CRAWLER_PRIVATE_IP:8081`을 실제 EC2 private IP로 교체 + 각 앱 SG가 Prometheus 호스트의 인바운드를 허용(actuator는 공개 금지).
2. `export GF_ADMIN_USER GF_ADMIN_PASSWORD SLACK_WEBHOOK_URL`
3. `docker compose -f docker-compose.monitoring-prod.yml up -d`
4. 9090/9093/3000/3100 포트는 방화벽/SG로 외부 차단.

> **CI/CD는 모니터링을 배포하지 않는다** (`.github/workflows/main.yml`은 앱 compose만 배포). 모니터링은 별도 수동 기동.

### AWS 앱을 로컬에서 모니터링 (pull + 집 NAT 문제 해결)
- **SSH 터널**: `ssh -N -L 9101:localhost:8080 user@API_EC2` / `9102:localhost:8081` → `prometheus-local.yml` 타깃을 `host.docker.internal:9101/9102`로.
- **Tailscale**(권장): 3대 메시 네트워크 → EC2의 `100.x` IP로 스크랩, 로그 push도 해결.
- ⚠️ 노트북은 상시 가동이 아니므로 데이터에 구멍 생김. 상시 모니터링은 작은 상시 호스트나 Grafana Cloud 고려.

---

## 8. 새 메트릭/패널/알람 추가하는 법 (확장 절차) ⭐

미래 작업의 핵심. **순서를 지켜라.**

1. **코드에 미터 등록** — 적절한 컴포넌트에 `MeterRegistry`로 `Counter`/`Gauge`/`Timer` 등록 + 실제 호출 지점에서 increment/record. (예: `CrawlJobMetrics` 패턴 참고)
2. **실제 노출 확인** — 앱 실행 후 `curl localhost:8081/actuator/prometheus | grep <metric_name>`로 **Prometheus 변환 이름이 진짜 나오는지** 확인. (§3-5 변환 규칙 주의)
3. **대시보드 패널 추가** — `aod-overview-dashboard.json`에 패널 추가(datasource uid `prometheus`/`loki`, 적절한 unit). 2단계에서 확인한 정확한 이름만 사용.
4. **알람 추가** — `alert-rules.yml`에 추가(같은 실제 이름). `clamp_min`으로 0분모 방어, severity 라벨, `{{ $labels }}`/`{{ $value }}` annotation.
5. **검증** — `promtool check rules alert-rules.yml`, 대시보드 JSON 파싱(`python -c "import json;json.load(open('...'))"`).

> 🚫 2단계를 건너뛰고 "있을 법한" 이름으로 패널/알람을 만들지 마라. 그게 이 스택을 망가뜨린 정확한 실수다.

---

## 9. 알려진 한계 / TODO

- [ ] api 모듈 파일 로그 부재 → Loki에 api 로그 없음
- [ ] `crawler_*` 커스텀 메트릭이 NaverWebtoon에서만 채워짐 → Steam/TMDB/소설 서비스·`CrawlJobConsumer`에 `CustomMetrics` 주입·확장
- [ ] 멀티호스트 로그 수집(Promtail colocate 또는 중앙화)
- [ ] `prometheus-prod.yml` private IP placeholder 채우기 + SG
- [ ] actuator 엔드포인트 보안(현재 인증 없음 — 사설 경로로만 접근)
- [ ] 구형 `-AOD-All-of-Dopamine-api/monitoring/` 트리 삭제
- [ ] (선택) 상시 모니터링 호스트 또는 Grafana Cloud

---

## 10. 설계 배경 / 교훈 (왜 이렇게 됐나)

- **분기된 두 트리**: `api/monitoring`(구형)과 `back/monitoring`(신규)이 공존하며 job명·datasource·메트릭 이름이 전부 달랐다 → 하나(`back/monitoring`)로 통합.
- **유령 메트릭**: 대시보드·알람·문서가 코드에 없는 메트릭을 참조 → §0 제1원칙으로 못 박음.
- **"CORS 에러"의 정체**: 프론트의 CORS 에러는 사실 API 과부하로 인한 502였다(모니터링이 제대로 됐으면 바로 잡혔을 것). per-uri p95 패널이 이런 걸 잡는 목적. (관련: `genres-with-count` findAll → API 다운 사건)
- **통합 구분 방식**: 서버는 분리(EC2 2대)지만 모니터링은 통합, `job` 라벨로 구분 — "single pane of glass".
