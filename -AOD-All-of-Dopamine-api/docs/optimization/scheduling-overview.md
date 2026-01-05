# 📅 AOD 정기 크롤링 스케줄 현황

## 🎯 개요

모든 정기 크롤링 작업은 **crawlerTaskExecutor** 스레드풀을 사용하여 비동기로 실행됩니다.

**스레드풀 설정:**
- Core Pool Size: 5
- Max Pool Size: 10
- Queue Capacity: 200
- Thread Name: `Crawler-Async-*`

---

## 📊 전체 스케줄 타임라인

### 매일 실행

| 시간 | 작업 | 플랫폼 | 파일 |
|------|------|--------|------|
| 02:00 | 신작 웹소설 수집 | 네이버 시리즈 | `NaverSeriesSchedulingService` |
| 02:00 | 전체 요일 웹툰 수집 | 네이버 웹툰 | `NaverWebtoonSchedulingService` |
| 04:00 | 신규 콘텐츠 수집 (최근 7일) | TMDB | `TmdbSchedulingService` |
| 06:00 | raw_items 배치 변환 | Transform | `TransformSchedulingService` |

### 매주 실행

| 요일 | 시간 | 작업 | 플랫폼 | 파일 |
|------|------|------|--------|------|
| 일요일 | 03:00 | 완결 웹툰 수집 | 네이버 웹툰 | `NaverWebtoonSchedulingService` |
| 일요일 | 03:00 | 완결 웹소설 대규모 수집 | 네이버 시리즈 | `NaverSeriesSchedulingService` |
| 일요일 | 05:00 | 과거 콘텐츠 최신화 (연도별) | TMDB | `TmdbSchedulingService` |
| 일요일 | 07:00 | raw_items 주간 대규모 변환 | Transform | `TransformSchedulingService` |
| 목요일 | 03:00 | 전체 게임 수집 | Steam | `SteamSchedulingService` |

### 매월 실행

| 날짜 | 시간 | 작업 | 플랫폼 | 파일 |
|------|------|------|--------|------|
| 15일 | 04:00 | 기존 게임 정보 업데이트 | Steam | `SteamSchedulingService` |

---

## 🕐 시간대별 스케줄 (새벽 시간대)

```
00:00 ┃
01:00 ┃
02:00 ┃ ▶ 네이버 시리즈 신작 (매일)
      ┃ ▶ 네이버 웹툰 요일별 (매일)
03:00 ┃ ▶ 네이버 웹툰 완결작 (일)
      ┃ ▶ 네이버 시리즈 완결작 (일)
      ┃ ▶ Steam 전체 수집 (목)
04:00 ┃ ▶ TMDB 신규 콘텐츠 (매일)
      ┃ ▶ Steam 업데이트 (매월 15일)
05:00 ┃ ▶ TMDB 과거 데이터 (일)
06:00 ┃ ▶ Transform 배치 변환 (매일)
07:00 ┃ ▶ Transform 대규모 변환 (일)
```

**💡 시간대 분산 이유:**
- 서버 부하 분산
- API Rate Limit 회피
- 데이터베이스 부하 최소화

---

## 📁 파일별 상세 정보

### 1. **NaverWebtoonSchedulingService**
**경로:** `src/main/java/com/example/AOD/contents/Webtoon/NaverWebtoon/NaverWebtoonSchedulingService.java`

```java
@Scheduled(cron = "0 0 2 * * *")  // 매일 02:00
public void collectAllWeekdaysDaily()

@Scheduled(cron = "0 0 3 * * SUN")  // 일요일 03:00
public void collectFinishedWebtoonsWeekly()
```

**수집 데이터:**
- 월~일 연재 웹툰 (매일)
- 완결 웹툰 최대 100페이지 (주 1회)

---

### 2. **TmdbSchedulingService**
**경로:** `src/main/java/com/example/AOD/contents/TMDB/service/TmdbSchedulingService.java`

```java
@Scheduled(cron = "0 0 4 * * *")  // 매일 04:00
public void collectNewContentDaily()

@Scheduled(cron = "0 0 5 * * SUN")  // 일요일 05:00
public void updatePastContentWeekly()
```

**수집 데이터:**
- 신규: 최근 7일간 영화/TV (매일)
- 과거: 1980년부터 연도별 순환 업데이트 (주 1회)

**과거 데이터 업데이트 로직:**
```
Week 1: 2025년 → Week 2: 2024년 → ... → Week N: 1980년 → 다시 2025년부터
```

---

### 3. **NaverSeriesSchedulingService**
**경로:** `src/main/java/com/example/AOD/contents/Novel/NaverSeriesNovel/NaverSeriesSchedulingService.java`

```java
@Scheduled(cron = "0 0 2 * * *")  // 매일 02:00
public void collectRecentNovelsDaily()

@Scheduled(cron = "0 0 3 * * SUN")  // 일요일 03:00
public void collectCompletedNovelsWeekly()
```

**수집 데이터:**
- 매일: 신작 웹소설 (3페이지, ~60개 작품)
  - URL: `https://series.naver.com/novel/recentList.series`
- 주간: 완결작품 대규모 수집 (50페이지, ~1000개 작품)
  - URL: `https://series.naver.com/novel/categoryProductList.series?categoryTypeCode=all`

---

### 4. **SteamSchedulingService**
**경로:** `src/main/java/com/example/AOD/game/steam/service/SteamSchedulingService.java`

```java
@Scheduled(cron = "0 0 3 * * THU")  // 목요일 03:00
public void collectSteamGamesWeekly()

@Scheduled(cron = "0 0 4 15 * *")  // 매월 15일 04:00
public void updateExistingGamesMonthly()
```

**수집 데이터:**
- 주간: 신규 게임 추가 (1000개씩 자동 분할)
- 월간: 기존 게임 정보 업데이트 (가격, 리뷰 등)

---

### 5. **TransformSchedulingService**
**경로:** `src/main/java/com/example/AOD/ingest/TransformSchedulingService.java`

```java
@Scheduled(cron = "0 0 6 * * *")  // 매일 06:00
public void transformRawItemsDaily()

@Scheduled(cron = "0 0 7 * * SUN")  // 일요일 07:00
public void transformRawItemsWeekly()
```

**처리 데이터:**
- 매일: 미처리 raw_items 배치 변환 (100개씩)
  - 모든 크롤러의 크롤링 완료 후 실행
  - 미처리 데이터가 없을 때까지 반복
- 주간: 대규모 배치 변환 (200개씩)
  - 주간 누적된 데이터 일괄 처리
  - 더 큰 배치 크기로 빠른 처리

**변환 프로세스:**
1. raw_items 테이블에서 미처리 데이터 조회
2. 플랫폼/도메인별 YAML 규칙 로드
3. TransformEngine으로 변환 (master/platform/domain)
4. UpsertService로 contents/platform_data 저장
5. transform_runs 테이블에 이력 기록

---

## ⚙️ 기술 구현

### 비동기 처리 구조

```java
// 스케줄러 (즉시 반환)
@Scheduled(cron = "...")
public void triggerCrawling() {
    service.crawlAsync();  // 비동기 호출
    log.info("트리거 완료");
}

// 서비스 (별도 스레드에서 실행)
@Async("crawlerTaskExecutor")
public CompletableFuture<Integer> crawlAsync() {
    // 실제 크롤링 작업
    return CompletableFuture.completedFuture(result);
}
```

**장점:**
- ✅ 스케줄러 스레드 블로킹 방지
- ✅ crawlerTaskExecutor 스레드풀에서 병렬 실행
- ✅ 작업 실패 시 다른 작업에 영향 없음

---

## 🔍 모니터링

### Actuator 메트릭

```
# 스레드풀 상태
/actuator/metrics/executor.active
/actuator/metrics/executor.pool.size
/actuator/metrics/executor.queue.size

# 크롤링 성공/실패
/actuator/metrics/crawler.success
/actuator/metrics/crawler.failure

# 처리 항목 수
/actuator/metrics/items.processed
```

### Grafana 대시보드

**패널:**
- 시간대별 크롤링 작업 현황
- 스레드풀 사용률 (Active/Max)
- 크롤링 성공/실패 비율
- 플랫폼별 수집 데이터 수

---

## ⚠️ 주의사항

### 1. API Rate Limit
- **TMDB:** 40 requests/10초
- **Steam:** 200 requests/5분
- **네이버:** 실제 브라우저 시뮬레이션 (Selenium)

### 2. 리소스 관리
- Selenium WebDriver ThreadLocal 정리 필수
- 대량 데이터 수집 시 메모리 모니터링
- DB 커넥션 풀 상태 확인

### 3. 실패 처리
- 개별 작업 실패 시 로그 기록
- 전체 스케줄에 영향 없음
- 다음 스케줄 시 자동 재시도

---

## 🚀 추가 계획

### 개선 사항
- [ ] 스케줄 동적 변경 (Admin UI)
- [ ] 실시간 진행 상황 표시
- [ ] 실패 시 자동 재시도 로직
- [ ] 우선순위 큐 도입

---

## 📞 담당자

**개발팀**
- 스케줄링 설정: Backend Team
- 모니터링: DevOps Team
- 문의: [이메일]

---

**최종 업데이트:** 2025-12-03

**주요 변경사항:**
- 네이버 시리즈: 신작 매일 수집 추가 (recentList.series)
- 네이버 시리즈: 완결작 주간 수집으로 변경 (화요일 → 일요일)
- Transform 스케줄러: raw_items 자동 변환 추가 (매일 06:00, 주간 07:00)
