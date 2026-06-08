# 03. 웹소설 장르가 필터에 안 뜸 (크롤 스케줄 누락 → stale 데이터)

- **날짜**: 2026-06-05
- **영향**: explore의 웹소설 카테고리에서 장르 필터 칩이 하나도 안 뜸
- **상태**: ✅ 커밋됨 (commit `1ae0084`, 미푸시)

## 증상
`GET /api/works/genres-with-count?domain=WEBNOVEL` → `{}` (빈 응답). 웹툰/게임/영화/TV는 정상.

## 진단 (증상 ≠ 원인)
1. 웹소설 작품 자체는 DB에 존재(작품 상세 조회됨). 하지만 `domainInfo: {}` — **장르뿐 아니라 author/publisher 등 도메인 코어 전체가 비어 있음.**
2. 크롤러엔 장르 추출 코드가 있고(2026-01-18 추가) YAML 매핑도 정상. 라이브 NaverSeries 페이지를 직접 긁어보니 **셀렉터로 장르(`현판`)·작가·출판사가 다 추출됨** → 셀렉터 안 깨짐.
3. `MasterScheduler` 확인 — Steam/TMDB/웹툰은 `@Scheduled` 크롤이 있는데 **`naverSeriesSchedulingService`는 주입만 되고 호출되는 곳이 없음.**

## 원인
**웹소설(NaverSeries) 콘텐츠 크롤이 자동 스케줄에서 누락.** (새벽 4시 랭킹 크롤은 ExternalRanking만 채우고 장르/도메인 데이터는 안 건드림.) 초기 1회(장르 추출 동작 전) 크롤된 데이터가 그대로 방치되어 genres/author가 빈 상태.

## 이전 (Before)
`MasterScheduler.java` — `naverSeriesSchedulingService` 필드는 있으나 호출 스케줄 없음.

## 이후 (After)
```java
// 네이버 시리즈 웹소설 신작 - 매일 새벽 1시 30분
@Scheduled(cron = "0 30 1 * * *")
public void scheduleNaverSeriesNovel() {
    naverSeriesSchedulingService.collectRecentNovelsDaily();
}
// 네이버 시리즈 웹소설 완결작 - 매주 토요일 새벽 3시 30분
@Scheduled(cron = "0 30 3 * * SAT")
public void scheduleNaverSeriesNovelCompleted() {
    naverSeriesSchedulingService.collectCompletedNovelsWeekly();
}
```

## 왜 이렇게 고쳤나
- 셀렉터·매핑·추출 코드는 멀쩡했고 문제는 "그 코드가 실행되지 않는 것"이었다. 누락된 스케줄을 웹툰과 동일 패턴으로 추가하는 것이 최소·정석 수정.

## 개선 효과
- 스케줄 가동 후 재크롤되면 payload에 genres가 들어가 hash가 바뀌고 → transform 재실행 → **webnovel_contents.genres/author 채워짐 → 필터에 장르 노출.**
- 즉시 효과를 보려면 NaverSeries 크롤을 1회 수동 트리거(백필).

## 교훈
- 데이터가 비어 보이면 코드 버그 전에 **"그 데이터가 실제로 수집/갱신되고 있나(스케줄·파이프라인)"** 부터 확인.
