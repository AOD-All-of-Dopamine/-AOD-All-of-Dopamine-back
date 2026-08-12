# Steam 리뷰 집계 기능 → 크롤러 표준형 구조 통합 Plan

- 날짜: 2026-08-12
- 대상 브랜치: `feature/crawler-standardization` (작업 베이스) ← `feature/steam-review-summary` (이식 원천, 2커밋: 26a1271, 1b526dd)
- 상태: Step 1~4 완료 (2026-08-12). 잔여: Step 5 스모크, Step 6 main 머지

## 1. 배경 — 왜 그냥 머지가 안 되는가

두 브랜치 모두 main(0efed64)에서 갈라졌고, Steam 코어 파일에서 정면 충돌한다:

| 파일 | crawler-standardization | steam-review-summary |
|---|---|---|
| `SteamApiFetcher` | `SteamFetcher`로 **rename** | `fetchReviewSummary()` 메서드 추가 |
| `SteamCrawlService` | **삭제** (로직이 Executor로 흡수) | 수집 경로 2곳에 review_summary 주입 |
| `SteamGameExecutor` | 재작성 (`collectGameByAppId` 흡수, 1000ms) | 2000ms + 배치 2로 변경 |
| `SteamCrawlServiceTest` | (해당 클래스 소멸) | 신규 작성 — 삭제된 클래스를 테스트 |

특히 `SteamCrawlService`는 delete/modify 충돌이라 rebase/cherry-pick 해도 결국 손으로 로직을 옮겨야 한다.

## 2. 결정 — 통합 방향

**표준화 브랜치를 베이스로, 리뷰 집계 기능(실질 ~40줄 + 테스트)을 표준형 구조 위로 손 포팅한다.**

- 근거: 표준화는 구조 변경(50파일, −1,602줄), 리뷰 집계는 작은 기능 추가. 작은 쪽을 새 구조에 맞추는 게 옳고, 포팅 지점도 수집 경로가 Executor 1곳으로 단일화되어 원본(2곳 주입)보다 오히려 단순해진다.
- rebase/cherry-pick 대신 손 포팅 1커밋: delete/modify 충돌 해소 비용 > 포팅 비용. 원 커밋 2개는 브랜치 삭제 전까지 origin에 남으므로 추적 가능.
- 결과적으로 main에는 `feature/crawler-standardization` 하나만 머지하면 두 작업이 모두 들어간다.

## 3. 포팅 맵 (원천 → 이식처)

| # | 원천 (steam-review-summary) | 이식처 (표준형) | 변형 |
|---|---|---|---|
| 1 | `SteamApiFetcher.fetchReviewSummary(Long)` (+`REVIEW_SUMMARY_URL` 상수) | `SteamFetcher` 동일 위치 | 본문 그대로, 클래스명만 |
| 2 | `SteamCrawlService` 2곳의 `reviewSummary` 주입 | `SteamGameExecutor.collectGameByAppId` **1곳** — `payloadProcessor.process()` 직후 | `if (reviewSummary != null) processedDetails.put("review_summary", ...)` 그대로 |
| 3 | `getAverageExecutionTime()` 1000→2000 + 주석 | `SteamGameExecutor` 동일 메서드 | 그대로 (배치 5→2는 파생값) |
| 4 | `steam.yml` `review_summary: attr.review_summary` +1줄 | 그대로 | 충돌 없음 (표준화는 yml 무접촉) |
| 5 | `RuleFilesTest` goldenSteam에 review_summary 입력+assert 2줄 | 그대로 | 충돌 없음 |
| 6 | `SteamApiFetcherTest` (3케이스: 파싱·missing·HTTP실패) | `SteamFetcherTest`로 파일/클래스명 rename | 생성자 `SteamFetcher(...)` 참조만 갱신 |
| 7 | `SteamCrawlServiceTest` (2케이스: payload 병합·null 시 키 부재) | `SteamGameExecutorTest`로 **통합** — `execute("70")` 경유, `saveRaw` payload ArgumentCaptor 검증 | 생성자를 3-mock(`SteamFetcher`, `SteamPayloadProcessor` 실물, `CollectorService`)으로 |
| 8 | `SteamGameExecutorTest` (2000ms·배치2 assert) | 위 7과 같은 파일에 병합 | 구 1-arg 생성자 → 3-mock |

주의: #7에서 `SteamPayloadProcessor`는 원본 테스트처럼 실물(`new`)을 쓴다 — process()가 payload를 변형하므로 mock이면 검증이 공허해짐.

## 4. 실행 순서

- [x] **Step 1**: `feature/crawler-standardization` 위에서 포팅 맵 #1~#5 적용 (main 코드 + yml + 골든)
- [x] **Step 2**: 테스트 이식 #6~#8 (신규 2파일: `SteamFetcherTest`, `SteamGameExecutorTest`)
- [x] **Step 3**: 게이트
  - `./gradlew.bat :-AOD-All-of-Dopamine-crawler:clean :-AOD-All-of-Dopamine-crawler:build` → SUCCESS (git mv 캐시 오염 대비 clean)
  - grep `SteamCrawlService|SteamApiFetcher` → 코드 참조 0건 (SteamJobProducer의 계보 주석 1건만 잔존 — 허용)
- [x] **Step 4**: 커밋 1개 — `feat(steam): 리뷰 집계 수집(query_summary)을 표준형 구조로 이식` (본문에 원 커밋 26a1271·1b526dd 명기) + push
- [ ] **Step 5**: Steam 스모크 (표준화 Phase 5의 미결 스모크와 겸함) — admin `/api/crawl/steam/by-appid`로 1건 큐 등록 → `raw_items` payload에 `review_summary` 키 확인
- [ ] **Step 6**: (사용자 결정) `feature/crawler-standardization` → main 머지 → `feature/steam-review-summary` 브랜치 삭제 (local+origin)

## 5. 리스크

| 리스크 | 대응 |
|---|---|
| 포팅 누락 (손 포팅이라 컴파일러가 못 잡는 부분) | 원천 diff 8항목을 §3 맵으로 전수 대조, 테스트 5케이스가 동작을 고정 |
| 스모크 환경 부재 (DB·API 키) | Step 5만 별도 세션 이월 가능 — 단 main 머지 전 권장 |
| 리뷰 집계는 재크롤 전까지 DB에 없음 | 데이터 노트일 뿐 (배포 후 Steam 재크롤 한 바퀴 필요, 배치 2라 종전 대비 시간 2.5배) |
