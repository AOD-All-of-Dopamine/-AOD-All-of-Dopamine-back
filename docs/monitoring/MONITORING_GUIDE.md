# 모니터링 시스템 가이드

## 📊 개요

AOD 크롤링 시스템의 실시간 모니터링 시스템입니다.

### 주요 기능
1. **커스텀 메트릭**: 크롤링 성공/실패, 작품 저장 수, 에러 카운트
2. **에러 로그 수집**: 최근 1000개 에러 메모리 저장
3. **작품 저장 로그**: 최근 1000개 저장 작품 추적
4. **REST API**: 실시간 모니터링 데이터 조회
5. **Actuator 통합**: Prometheus 메트릭 노출

---

## 🔧 사용법

### 1. 크롤러에서 로그 수집하기

```java
@Service
@RequiredArgsConstructor
public class YourCrawlerService {
    
    private final LogCollectionService logCollectionService;
    private final MetricsService metricsService;
    
    public void crawl() {
        String domain = "WEBTOON";
        String platform = "NAVER";
        
        try {
            // 크롤링 시작
            List<Item> items = fetchItems();
            
            // 각 아이템 저장
            for (Item item : items) {
                try {
                    save(item);
                    
                    // ✅ 저장 성공 로그 기록
                    logCollectionService.recordSavedItem(
                        domain,
                        platform,
                        item.getTitle(),
                        item.getId(),
                        item.getExternalId(),
                        true,  // isNew
                        false, // isDuplicate
                        item.getGenres(),
                        item.getScore()
                    );
                    
                } catch (Exception e) {
                    // ❌ 저장 실패 로그 기록
                    logCollectionService.recordCrawlingError(
                        domain,
                        platform,
                        item.getTitle(),
                        "작품 저장 실패: " + e.getMessage(),
                        e
                    );
                }
            }
            
            // ✅ 크롤링 성공 메트릭 기록
            metricsService.recordCrawlingSuccess(platform, domain);
            metricsService.recordItemsSaved(items.size(), domain);
            
        } catch (Exception e) {
            // ❌ 크롤링 실패 메트릭 기록
            metricsService.recordCrawlingFailure(platform, domain, "NETWORK_ERROR");
            
            logCollectionService.recordError(
                "CRAWLING_ERROR",
                "CRITICAL",
                domain,
                platform,
                "크롤링 실패: " + e.getMessage(),
                getStackTrace(e),
                Map.of("url", "https://...")
            );
        }
    }
}
```

### 2. API 에러 로깅

```java
@RestController
@RequiredArgsConstructor
public class WorkController {
    
    private final LogCollectionService logCollectionService;
    
    @GetMapping("/api/works")
    public ResponseEntity<?> getWorks() {
        try {
            return ResponseEntity.ok(works);
        } catch (Exception e) {
            // API 에러 로그
            logCollectionService.recordApiError(
                "/api/works",
                "데이터 조회 실패: " + e.getMessage(),
                e
            );
            throw e;
        }
    }
}
```

---

## 📡 API 엔드포인트

### 1. 대시보드 데이터
```bash
GET /api/monitoring/dashboard
```

**응답 예시:**
```json
{
  "metrics": {
    "crawling_success_total": 150,
    "crawling_failure_total": 5,
    "saved_items_total": 1450,
    "api_errors_total": 2,
    "crawling_success_rate": "96.77%"
  },
  "recent_errors": [
    {
      "id": 1,
      "timestamp": "2025-12-15T10:30:00",
      "errorType": "CRAWLING_ERROR",
      "severity": "ERROR",
      "domain": "WEBTOON",
      "platform": "NAVER",
      "errorMessage": "크롤링 실패: Connection timeout",
      "itemTitle": "참교육"
    }
  ],
  "recent_saved_items": [
    {
      "id": 1,
      "timestamp": "2025-12-15T10:35:00",
      "domain": "WEBTOON",
      "platform": "NAVER",
      "itemTitle": "무직전생",
      "isNew": true,
      "isDuplicate": false,
      "genres": "판타지, 액션",
      "score": 9.5
    }
  ],
  "error_stats_by_type": {
    "CRAWLING_ERROR": 3,
    "API_ERROR": 2
  },
  "saved_stats_by_domain": {
    "WEBTOON": 850,
    "GAME": 450,
    "MOVIE": 150
  }
}
```

### 2. 에러 로그 조회
```bash
# 전체 에러 로그 (최대 50개)
GET /api/monitoring/errors?limit=50

# 특정 도메인 에러만
GET /api/monitoring/errors?domain=WEBTOON&limit=20
```

### 3. 저장된 작품 로그
```bash
GET /api/monitoring/saved?limit=50
```

### 4. 실시간 메트릭
```bash
GET /api/monitoring/metrics
```

### 5. 로그 초기화 (관리자용)
```bash
DELETE /api/monitoring/logs
```

---

## 🎯 Actuator 엔드포인트

### 커스텀 모니터링 엔드포인트
```bash
# 전체 모니터링 현황
GET /actuator/monitoring

# 에러만 조회
GET /actuator/monitoring/errors

# 저장 로그만 조회
GET /actuator/monitoring/saved
```

### Prometheus 메트릭
```bash
GET /actuator/prometheus
```

**커스텀 메트릭 목록:**
- `crawling_success_total` - 크롤링 성공 횟수
- `crawling_failure_total` - 크롤링 실패 횟수
- `crawling_items_saved_total` - 저장된 작품 수
- `crawling_items_duplicate_total` - 중복 작품 수
- `crawling_success_by_platform_total{platform, domain}` - 플랫폼별 성공 횟수
- `crawling_failure_by_platform_total{platform, domain, error_type}` - 플랫폼별 실패 횟수
- `crawling_domain_movie_total` - 영화 크롤링 수
- `crawling_domain_tv_total` - TV 크롤링 수
- `crawling_domain_game_total` - 게임 크롤링 수
- `crawling_domain_webtoon_total` - 웹툰 크롤링 수
- `crawling_domain_webnovel_total` - 웹소설 크롤링 수
- `api_errors_total` - API 에러 수
- `db_connection_errors_total` - DB 연결 에러 수
- `db_query_errors_total` - DB 쿼리 에러 수

---

## 📊 Prometheus & Grafana 연동

### 1. Prometheus 설정 (이미 완료)
```yaml
# monitoring/prometheus.yml
scrape_configs:
  - job_name: 'aod-ec2-app'
    metrics_path: '/actuator/prometheus'
    scheme: 'https'
    static_configs:
      - targets: ['api.allofdophamin.com']
```

### 2. Grafana 대시보드 쿼리 예시

**크롤링 성공률:**
```promql
(crawling_success_total / (crawling_success_total + crawling_failure_total)) * 100
```

**시간별 저장 작품 수:**
```promql
rate(crawling_items_saved_total[5m]) * 300
```

**도메인별 크롤링 분포:**
```promql
sum by (domain) (rate(crawling_success_by_platform_total[5m]))
```

**에러율 추이:**
```promql
rate(crawling_failure_total[5m])
```

---

## 🚨 Alert 설정

Prometheus Alert 규칙이 이미 설정되어 있습니다 (`monitoring/alerts.yml`):

- **ApplicationDown**: 앱 다운 감지
- **HighMemoryUsage**: 메모리 85% 이상
- **HighCPUUsage**: CPU 80% 이상
- **HighErrorRate**: 5xx 에러율 높음

### 커스텀 Alert 추가 예시

```yaml
# monitoring/alerts.yml에 추가
- alert: HighCrawlingFailureRate
  expr: (rate(crawling_failure_total[5m]) / rate(crawling_success_total[5m])) > 0.1
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "높은 크롤링 실패율"
    description: "크롤링 실패율이 10%를 초과했습니다."

- alert: NoCrawlingActivity
  expr: rate(crawling_success_total[10m]) == 0
  for: 30m
  labels:
    severity: critical
  annotations:
    summary: "크롤링 활동 없음"
    description: "30분 동안 크롤링 활동이 없습니다."
```

---

## 💡 Best Practices

### 1. 메모리 관리
- 로그는 최대 1000개만 메모리에 보관
- 장기 보관이 필요한 경우 DB 저장 구현 필요

### 2. 성능 최적화
- 로그 수집은 비동기로 처리 (현재 동기)
- 대량 크롤링 시 배치로 메트릭 기록

### 3. 에러 분류
```java
// 에러 타입 상수화
public class ErrorType {
    public static final String CRAWLING_ERROR = "CRAWLING_ERROR";
    public static final String API_ERROR = "API_ERROR";
    public static final String DB_ERROR = "DB_ERROR";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
}

// 심각도 레벨
public class Severity {
    public static final String CRITICAL = "CRITICAL";  // 즉시 조치 필요
    public static final String ERROR = "ERROR";        // 기능 장애
    public static final String WARNING = "WARNING";    // 주의 필요
    public static final String INFO = "INFO";          // 정보성
}
```

### 4. 로그 정리 스케줄링
```java
@Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
public void clearOldLogs() {
    logCollectionService.clearLogs();
}
```

---

## 🔍 실시간 모니터링 화면 만들기

### 프론트엔드에서 조회하기

```typescript
// 대시보드 데이터 fetch
const fetchDashboard = async () => {
  const response = await fetch('/api/monitoring/dashboard');
  const data = await response.json();
  return data;
};

// 5초마다 갱신
useEffect(() => {
  const interval = setInterval(() => {
    fetchDashboard().then(setDashboardData);
  }, 5000);
  return () => clearInterval(interval);
}, []);
```

---

## ✅ 체크리스트

- [x] MetricsService 구현
- [x] LogCollectionService 구현
- [x] MonitoringController API 구현
- [x] Actuator 커스텀 엔드포인트
- [x] Prometheus 메트릭 노출
- [ ] 크롤러에 로그 수집 적용
- [ ] Grafana 대시보드 구성
- [ ] Slack 알림 연동
- [ ] 로그 DB 영구 저장
