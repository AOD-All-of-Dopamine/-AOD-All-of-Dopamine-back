# 04. 모니터링 스택 재구축 (유령 메트릭 → 실제 메트릭)

- **날짜**: 2026-06-06
- **영향**: Grafana 대시보드 대부분 "No data", 알람 안 울림, prod에 사실상 미배포
- **상태**: 🟡 코드/설정 적용 (미배포). 상세 레퍼런스는 [docs/7_MONITORING.md](../7_MONITORING.md)

## 증상
"Grafana 대시보드가 너무 구리고 안 됨." 패널이 비어 있고 알람이 동작 안 함.

## 진단 (6개 영역 점검)
- **두 갈래 트리**: `back/monitoring`(신규)과 `api/monitoring`(구형)이 공존하며 job명·datasource·메트릭 이름이 전부 다름.
- **유령 메트릭**: 대시보드/알람/문서가 코드가 **emit하지 않는** 메트릭(`crawl_job_queue_size`, `crawl_job_completed_total`, `crawling_*_total`, `api_errors_total`, `webdriver_timeout_total` 등)을 참조 → 거의 모든 패널 No data, 모든 커스텀 알람 영원히 안 울림.
- **prod 미기동**: `prometheus-prod.yml` 파일 자체가 없음, prod compose가 없는 파일/미정의 네트워크 마운트, CI/CD가 모니터링을 배포하지 않음.
- **계측 빈약**: CustomMetrics가 NaverWebtoonService 한 곳에서만 호출. `performance/` 패키지는 전부 죽은 코드.

## 원인
설정(대시보드/알람)이 **코드가 실제로 내보내는 메트릭과 불일치**. + 두 모니터링 트리의 config drift.

## 이전 (Before)
- `crawler-dashboard.json`: 10패널 중 5개가 `crawl_job_*`(유령) → No data
- `alert-rules-crawler.yml`: 8개 알람이 유령 메트릭 → 절대 안 울림
- 코드: `crawl_job_*` 메트릭을 emit하는 곳 0

## 이후 (After)
1. **코드에 실제 메트릭 추가** (`CrawlJobMetrics.java` 신규 + `CrawlJobConsumer` 계측):
   `crawl_job_queue_size{status}`(게이지, 30초 갱신) · `crawl_job_completed_total{job_type}` · `crawl_job_failed_total{job_type}` · `crawl_job_duration_seconds{job_type}`(히스토그램)
2. **Prometheus**: `prometheus-prod.yml` 신규 — api/crawler 둘 다 스크랩(job명 통일), compose 기동 가능하게 수정(자체 네트워크/named volume/retention)
3. **Grafana**: `aod-overview-dashboard.json` 신규(27패널, **실제 메트릭만**, uri별 p95 포함). 유령 패널 대시보드 삭제
4. **알람**: `alert-rules.yml` 통합 재작성(12개, 전부 실제 메트릭) + `alertmanager.yml`(severity 라우팅·inhibit·Slack 템플릿)
5. **정리**: 통합되어 중복된 `alert-rules-crawler.yml`·구 `crawler-dashboard.json` 삭제, datasource 고정 uid

## 왜 이렇게 고쳤나
- 자동 메트릭(`http_server_requests_seconds`, `jvm_*`, `hikaricp_*` 등)은 **이미 정상 emit** 중이라 "데이터 소스는 멀쩡, 그 위 설정만 잘못"된 상태 → 패치보다 **실제 메트릭 기준 재작성**이 빠르고 정확.
- 메트릭 이름을 D(코드)에서 확정하고 A/B/C(설정)가 그대로 참조하게 해 **이름 불일치 재발 차단**.

## 개선 효과
- 대시보드/알람이 실제 데이터로 동작(스택 기동 + Prometheus가 앱에 닿을 때).
- 자동 메트릭 기반 **API uri별 p95**, JVM/메모리/DB풀/에러율, 진짜 울리는 알람 확보.

## 남은 작업 (미배포 + TODO)
- `prometheus-prod.yml`의 `API_PRIVATE_IP`/`CRAWLER_PRIVATE_IP` 채우기 + SG
- `GF_ADMIN_*`, `SLACK_WEBHOOK_URL` env
- 멀티호스트 로그(Promtail) 토폴로지, api 파일 로그 부재
- 구형 `api/monitoring/` 트리 삭제
- 확장 절차/주의는 [docs/7_MONITORING.md](../7_MONITORING.md) §8 참고
