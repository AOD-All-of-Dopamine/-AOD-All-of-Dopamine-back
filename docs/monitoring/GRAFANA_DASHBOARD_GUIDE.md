# 📊 Grafana 대시보드 사용 가이드

## 🎯 세 가지 방법

---

## 방법 1: 커뮤니티 대시보드 Import ⭐ (가장 쉬움)

### 1️⃣ Grafana 접속
```
http://localhost:3000
ID: admin
PW: admin
```

### 2️⃣ 대시보드 Import
```
1. 좌측 메뉴 → "Dashboards" → "Import"
2. "Import via grafana.com" 란에 ID 입력
3. "Load" 클릭
4. Prometheus 데이터소스 선택
5. "Import" 클릭
```

### 3️⃣ 추천 대시보드 ID

#### 🔥 Spring Boot 2.1 System Monitor (ID: 11378)
- JVM 메모리/GC
- HTTP 요청
- 스레드
- DB 커넥션
- 로그백

#### 🔥 JVM (Micrometer) (ID: 4701)
- 상세 JVM 메트릭
- GC 분석
- 메모리 풀

#### 🔥 Spring Boot Statistics (ID: 12900)
- HTTP 통계
- Tomcat 메트릭
- 캐시

---

## 방법 2: 제공된 커스텀 대시보드 Import ⭐⭐ (추천!)

### 프로젝트 전용 대시보드가 이미 준비되어 있습니다!

```
파일: monitoring/grafana/dashboards/aod-performance-dashboard.json
```

### Import 방법
```
1. Grafana 접속 (http://localhost:3000)
2. 좌측 메뉴 → "Dashboards" → "Import"
3. "Upload JSON file" 클릭
4. 파일 선택: monitoring/grafana/dashboards/aod-performance-dashboard.json
5. Prometheus 데이터소스 선택
6. "Import" 클릭
```

### 포함된 패널
- ✅ 배치 처리 시간 (Before vs After 비교)
- ✅ 처리 속도 (items/sec)
- ✅ 스레드풀 활용률
- ✅ DB 커넥션 활성
- ✅ JVM 메모리 사용률
- ✅ 처리 성공/실패율
- ✅ 총 처리량 (누적)
- ✅ 평균 처리 시간
- ✅ 시스템 CPU 사용률
- ✅ JVM 스레드 수
- ✅ GC 시간

---

## 방법 3: 직접 만들기 (커스터마이징 필요할 때)

### 1️⃣ 새 대시보드 생성
```
1. Grafana 접속
2. 좌측 메뉴 → "Dashboards" → "New" → "New Dashboard"
3. "Add visualization" 클릭
4. 데이터소스: Prometheus 선택
```

### 2️⃣ 패널 추가 예시

#### 배치 처리 시간 (Before vs After)
```promql
# Before
rate(performance_test_duration_seconds_sum{version="BEFORE"}[5m]) / 
rate(performance_test_duration_seconds_count{version="BEFORE"}[5m])

# After
rate(performance_test_duration_seconds_sum{version="AFTER"}[5m]) / 
rate(performance_test_duration_seconds_count{version="AFTER"}[5m])
```

**설정:**
- Visualization: Time series (Graph)
- Unit: seconds (s)
- Legend: {{version}}

---

#### 처리 속도 (items/sec)
```promql
rate(performance_test_items_total{status="success"}[5m])
```

**설정:**
- Visualization: Time series
- Unit: ops (operations per second)

---

#### 스레드풀 활용률
```promql
executor_active_threads{name="crawlerTaskExecutor"} / 
executor_pool_max_threads{name="crawlerTaskExecutor"} * 100
```

**설정:**
- Visualization: Gauge
- Unit: percent (%)
- Thresholds:
  - Green: 0-70%
  - Yellow: 70-90%
  - Red: 90-100%

---

#### JVM 메모리 사용률
```promql
jvm_memory_used_bytes{area="heap"} / 
jvm_memory_max_bytes{area="heap"} * 100
```

**설정:**
- Visualization: Gauge
- Unit: percent (%)
- Thresholds:
  - Green: 0-70%
  - Yellow: 70-85%
  - Red: 85-100%

---

#### DB 커넥션 수
```promql
hikaricp_connections_active
```

**설정:**
- Visualization: Gauge
- Unit: short
- Max: 20 (maximum pool size)

---

## 🎨 대시보드 레이아웃 추천

```
┌─────────────────────────────┬─────────────────────────────┐
│  배치 처리 시간             │  처리 속도 (items/sec)      │
│  (Before vs After)          │  (Before vs After)          │
│                             │                             │
│  [그래프]                   │  [그래프]                   │
│                             │                             │
└─────────────────────────────┴─────────────────────────────┘
┌──────────┬──────────┬──────────┬──────────┬──────────┬────┐
│스레드풀  │ DB 연결  │ 메모리   │ 성공률   │ CPU      │    │
│  75%     │   5/20   │  65%     │  99.9%   │  45%     │    │
│ [게이지] │ [게이지] │ [게이지] │ [Stat]   │ [Stat]   │    │
└──────────┴──────────┴──────────┴──────────┴──────────┴────┘
┌─────────────────────────────┬─────────────────────────────┐
│  JVM 스레드 수              │  GC 시간                    │
│                             │                             │
│  [그래프]                   │  [그래프]                   │
│                             │                             │
└─────────────────────────────┴─────────────────────────────┘
```

---

## 🚀 빠른 시작 (권장 순서)

### 1단계: Prometheus + Grafana 시작
```bash
cd monitoring
docker-compose -f monitoring-compose.local.yml up -d
```

### 2단계: Grafana 접속
```
http://localhost:3000
ID: admin
PW: admin
```

### 3단계: 대시보드 Import (둘 중 선택)

#### 옵션 A: 커뮤니티 대시보드 (범용)
```
Dashboards → Import → ID: 11378 입력 → Load → Import
```

#### 옵션 B: 커스텀 대시보드 (프로젝트 전용)
```
Dashboards → Import → Upload JSON file 
→ monitoring/grafana/dashboards/aod-performance-dashboard.json
```

### 4단계: 성능 테스트 실행
```powershell
# 백엔드에서
.\test-performance.ps1
```

### 5단계: 대시보드 확인
```
Grafana에서 실시간으로 그래프 업데이트 확인!
```

---

## 📊 포트폴리오 스크린샷 가이드

### 캡처할 화면

1. **대시보드 전체 뷰**
   - Before/After 비교 그래프
   - 게이지들 (스레드풀, DB, 메모리)
   - 통계 패널들

2. **처리 시간 그래프 (클로즈업)**
   - Before 선 (높음)
   - After 선 (낮음)
   - 확실한 차이 보이도록

3. **처리 속도 그래프 (클로즈업)**
   - Before 선 (낮음)
   - After 선 (높음)
   - 배수 차이 명확하게

4. **게이지 패널들**
   - 스레드풀: 정상 범위 (녹색)
   - 메모리: 안정적 (녹색/노란색)
   - 성공률: 99% 이상 (녹색)

### 캡처 타이밍
```
1. 테스트 실행 중 (활발한 그래프)
2. 테스트 완료 직후 (피크 메트릭)
3. 30분 후 (추이 확인)
```

---

## 💡 팁

### 대시보드 커스터마이징
- 패널 추가/삭제: 자유롭게
- 색상 변경: 패널 설정 → Overrides
- 임계값 조정: 패널 설정 → Thresholds

### 알림 설정
```
1. 패널 설정 → Alert 탭
2. "Create alert rule" 클릭
3. 조건 설정 (예: CPU > 90%)
4. 알림 채널 선택 (Email, Slack 등)
```

### 대시보드 공유
```
1. 대시보드 상단 → Share 아이콘
2. "Export" 탭
3. "Save to file" → JSON 다운로드
4. 포트폴리오에 첨부 가능
```

---

## 🎯 결론

### 추천 방법 순위

1. **제공된 커스텀 대시보드** (가장 추천!)
   - 프로젝트 전용으로 최적화됨
   - Before/After 비교에 최적화
   - JSON 파일 바로 import

2. **커뮤니티 대시보드 (ID: 11378)**
   - 범용적으로 좋음
   - 표준 Spring Boot 메트릭
   - 바로 사용 가능

3. **직접 만들기**
   - 특별한 커스터마이징 필요할 때만
   - 시간 많이 걸림

**대부분의 경우 1번 또는 2번으로 충분합니다!**
