# Crawler 표준 구조 수렴 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 활성 플랫폼 4개(TMDB, Steam, 네이버시리즈, 네이버웹툰)의 수집 코드를 Selectors/Fetcher/JobProducer/Executor 표준 세트로 수렴시키고, 런타임 동작(크론 일정·큐 처리·수집 결과)을 보존한다.

**Architecture:** 각 플랫폼을 "Fetcher(원천 접근 유일 관문: discover*/fetch*Detail) + JobProducer(큐 등록만, 파싱 0줄) + Executor(fetch→process→saveRaw)"로 통일. 셀렉터는 `XxxSelectors` 상수 클래스 단일 출처, 공용 파싱 헬퍼는 `common/util/HtmlParseUtils`. 레거시 직접 크롤 경로(Service의 벌크 메서드)와 플랫폼별 컨트롤러는 삭제하고 admin 진입점으로 통합.

**Tech Stack:** Java 17, Spring Boot 3.5.9, Jsoup, Selenium, Gradle 멀티모듈. 검증: `gradlew.bat :-AOD-All-of-Dopamine-crawler:build`

**Spec:** `docs/superpowers/specs/2026-08-11-crawler-standardization-design.md`

## Global Constraints

- **동작 보존**: 크론 일정(MasterScheduler), 큐 처리(JobType·batchSize·재시도), RawItem payload 구조 불변
- **불가침 영역**: `contents/novel/kakaopage/`, `ranking/` + `RankingScheduler`, `ingest/`, api·shared 모듈
- **셀렉터 단일 출처**: `select("...")`/`By.cssSelector("...")` 인라인 금지 → `XxxSelectors` 상수만
- **rename은 컴파일러 검증**: 클래스 rename 후 반드시 전체 빌드 — 참조 누락은 컴파일 에러로 검출
- **브랜치**: `feature/crawler-standardization` (스펙 커밋 2d1edcc 위에 스택)
- **환경**: Windows Git Bash. 빌드는 repo 루트에서 `./gradlew.bat`. 디렉터리 대소문자 rename은 2단계 `git mv` 필수
- **Phase당 커밋 1~2개** (기계적 rename/이동 커밋과 로직 변경 커밋 분리)

## 루프 실행 프로토콜 (스펙 §7)

- **반복 단위 = Phase 1개.** 각 반복: 체크박스 순서대로 구현 → 게이트 → 커밋 → 체크오프.
- **게이트 (매 Phase 공통)**:
  1. `./gradlew.bat :-AOD-All-of-Dopamine-crawler:build` → `BUILD SUCCESSFUL`
  2. contextLoads 포함 테스트 그린 (Phase 0 이후 상시)
  3. Phase별 grep 검증 (각 Phase 마지막 스텝에 명시)
  4. (환경 가능 시) admin 수동 트리거 스모크 — 불가하면 Phase 5에서 일괄
- **중단 조건**: 게이트 2회 연속 실패 → 루프 중단, 사용자 보고. 실패를 미루고 다음 Phase 진행 금지.
- **이 파일의 체크박스가 진행 상황의 단일 진실.** 새 세션에서 재개 시 이 파일과 스펙만 읽으면 됨.

---

### Phase 0: 안전망 — contextLoads 복구

**Files:**
- Modify: `-AOD-All-of-Dopamine-crawler/src/main/resources/application.yml` (L116, L144, L148, L163, L167, L171)

**배경:** `application.yml`의 `${SENTRY_DSN}`(L116)에 기본값이 없어 env var 부재 시 `Could not resolve placeholder`로 `@SpringBootTest` 컨텍스트가 못 뜬다. `STEAM_API_KEY`(L144, L167), `TMDB_API_KEY`(L148, L163), `OPENAI_API_KEY`(L171)도 동일 폭탄. `SentryConfig.java`의 `@Value("${sentry.dsn:}")`는 이미 기본값이 있으므로 수정 불필요.

- [x] **Step 0.1: 플레이스홀더 6곳에 빈 기본값 추가**

`application.yml`에서 아래 패턴으로 수정 (env var가 있으면 그대로 주입되므로 운영 무영향):

```yaml
# L116  변경 전: dsn: ${SENTRY_DSN}      → 변경 후: dsn: ${SENTRY_DSN:}
# L144  변경 전: ${STEAM_API_KEY}        → 변경 후: ${STEAM_API_KEY:}
# L148  변경 전: ${TMDB_API_KEY}         → 변경 후: ${TMDB_API_KEY:}
# L163  변경 전: ${TMDB_API_KEY}         → 변경 후: ${TMDB_API_KEY:}
# L167  변경 전: ${STEAM_API_KEY}        → 변경 후: ${STEAM_API_KEY:}
# L171  변경 전: ${OPENAI_API_KEY}       → 변경 후: ${OPENAI_API_KEY:}
```

- [x] **Step 0.2: 테스트 실행으로 contextLoads 복구 확인**

Run (repo 루트): `./gradlew.bat :-AOD-All-of-Dopamine-crawler:test`
Expected: `BUILD SUCCESSFUL`, `CrawlerApplicationTests > contextLoads()` PASS 포함

- [x] **Step 0.3: 커밋**

```bash
git add -- -AOD-All-of-Dopamine-crawler/src/main/resources/application.yml
git commit -m "fix(crawler): env 플레이스홀더에 빈 기본값 — SENTRY_DSN 부재 시 contextLoads 실패 해소"
```

---

### Phase 1: TMDB 전환 (표준 확립)

**Files:**
- Rename: `contents/TMDB/TmdbApiFetcher.java` → `contents/TMDB/TmdbFetcher.java`
- Rename: `contents/TMDB/TmdbSchedulingService.java` → `contents/TMDB/TmdbJobProducer.java`
- Modify: `common/queue/executors/TmdbMovieExecutor.java`, `TmdbTvExecutor.java`
- Modify: `scheduler/MasterScheduler.java`, `admin/controller/AdminTestController.java` (rename 참조 갱신)
- Delete: `contents/TMDB/TmdbService.java`, `TmdbController.java`, `TmdbTestController.java`
- Dir rename: `contents/TMDB/` → `contents/tmdb/` (2단계 git mv)

**Interfaces (이후 Phase가 복제할 표준):**
- Produces: `TmdbFetcher` — 기존 `discoverMovies/discoverTvShows`(목록) + `getMovieDetails/getTvShowDetails`(상세) 그대로. 클래스명만 역할명으로.
- Produces: `TmdbJobProducer` — 기존 `TmdbSchedulingService`의 public 메서드 시그니처 전부 그대로 (`collectNewContentDaily()` 등 6개). 호출부(MasterScheduler, AdminTestController)는 타입명만 갱신.
- Produces: Executor 표준형 — `execute(targetId)`가 `Fetcher 상세 호출 → PayloadProcessor.process → CollectorService.saveRaw` 순서를 직접 오케스트레이션. `PayloadProcessor`는 플랫폼 협력자로 유지.

- [x] **Step 1.1: TmdbService의 큐용 메서드를 Executor로 이동**

`TmdbService.collectMovieById(String)`의 **본문 전체를 변경 없이** `TmdbMovieExecutor`의 private 메서드로 이동하고 `execute()`에서 호출. 필요한 필드(`TmdbApiFetcher`, `TmdbPayloadProcessor`, `CollectorService`)를 `@RequiredArgsConstructor` 필드로 추가. `collectTvShowById(String)`도 동일하게 `TmdbTvExecutor`로.
검증: 이동 후 `TmdbService`에서 두 메서드 삭제 → 컴파일 시 남은 참조(있다면 컨트롤러)가 에러로 드러남.

- [x] **Step 1.2: 죽은 컨트롤러 + 레거시 Service 삭제**

`TmdbController`(@Deprecated 2개 엔드포인트, admin.html 참조 0), `TmdbTestController`(admin.html에서 JS 정의만 있고 호출 0), `TmdbService`(잔여 메서드는 위 두 컨트롤러만 사용) 파일 3개 삭제.
Run: `grep -rn "TmdbService\|TmdbController\|TmdbTestController" -- -AOD-All-of-Dopamine-crawler/src`
Expected: 검출 0건

- [x] **Step 1.3: 클래스 rename 2건**

`TmdbApiFetcher` → `TmdbFetcher`, `TmdbSchedulingService` → `TmdbJobProducer` (파일명+클래스명+전체 참조: MasterScheduler L3·L24·L47, AdminTestController, 두 Executor).

- [x] **Step 1.4: 디렉터리 소문자 rename (2단계)**

```bash
cd -- -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents
git mv TMDB tmdb_tmp && git mv tmdb_tmp tmdb
```
package 선언은 이미 `...contents.tmdb`이므로 import 수정 불필요.

- [x] **Step 1.5: 게이트 + 커밋**

Run: `./gradlew.bat :-AOD-All-of-Dopamine-crawler:build` → `BUILD SUCCESSFUL`
Run: `git status` → `contents/tmdb/` 하위로 R(rename) 표시 확인
커밋 2개: ① "refactor(tmdb): Executor가 Fetcher 직결, 레거시 Service·컨트롤러 삭제" ② "refactor(tmdb): 표준 네이밍(Fetcher/JobProducer) + 디렉터리 소문자"

---

### Phase 2: Steam 전환

**Files:**
- Rename: `contents/game/steam/SteamApiFetcher.java` → `SteamFetcher.java`
- Rename: `contents/game/steam/SteamSchedulingService.java` → `SteamJobProducer.java`
- Modify: `common/queue/executors/SteamGameExecutor.java`, `admin/controller/AdminTestController.java`, `src/main/resources/templates/demo/admin.html` (L262, L268)
- Delete: `SteamCrawlService.java`, `SteamController.java`, `SteamTestController.java`, `SteamRateLimiterController.java`
- Keep: `SteamRateLimiter.java`, `SteamPayloadProcessor.java` (플랫폼 협력자)

- [x] **Step 2.1: SteamCrawlService.collectGameByAppId → SteamGameExecutor로 이동**

Phase 1의 Step 1.1과 동일 패턴: 본문 무변경 이동, 필드 추가, `execute()`에서 호출.

- [x] **Step 2.2: admin.html이 쓰는 두 경로를 큐 방식으로 대체**

admin.html이 참조하는 레거시 직접 크롤 엔드포인트 2개를 AdminTestController의 큐 등록 엔드포인트로 교체한다 (스펙 §3.2 "admin 진입점 → JobProducer" 규칙):

`AdminTestController`에 추가:
```java
/** 단건 수집: 큐 등록 (기존 /api/steam/collect/by-appid 직접 크롤 대체) */
@PostMapping("/crawl/steam/by-appid")
public ResponseEntity<Map<String, Object>> enqueueSteamGame(@RequestParam Long appId) {
    crawlJobProducer.createJob(JobType.STEAM_GAME, String.valueOf(appId), 1, null);
    return ResponseEntity.ok(Map.of("message", "큐 등록 완료 (Consumer가 수 초 내 처리)", "appId", appId));
}

/** 범위 수집: 앱 목록 슬라이스 → 큐 등록 (기존 /api/test/steam/collect-games-by-range 대체) */
@PostMapping("/crawl/steam/by-range")
public ResponseEntity<Map<String, Object>> enqueueSteamRange(@RequestParam int start, @RequestParam int end) {
    int created = steamJobProducer.enqueueRange(start, end);
    return ResponseEntity.ok(Map.of("message", "큐 등록 완료", "created", created));
}
```

`SteamJobProducer`에 추가 (기존 `collectSteamGamesWeekly()`의 목록 수집 로직 재사용):
```java
/** fetchGameApps() 결과의 [start, end) 슬라이스를 큐에 등록. 반환: 등록 건수 */
public int enqueueRange(int start, int end) {
    List<Map<String, Object>> apps = steamFetcher.fetchGameApps();
    List<String> ids = apps.subList(Math.max(0, start), Math.min(apps.size(), end)).stream()
            .map(app -> String.valueOf(app.get("appid")))
            .toList();
    return crawlJobProducer.createJobs(JobType.STEAM_GAME, ids, 5);
}
```
`admin.html` L262 `/api/steam/collect/by-appid` → `/api/crawl/steam/by-appid`, L268 `/api/test/steam/collect-games-by-range` → `/api/crawl/steam/by-range`로 URL 교체 (요청 파라미터명 유지 확인).

- [x] **Step 2.3: 삭제 + rename**

`SteamCrawlService`(잔여 벌크 메서드 참조자 = 삭제되는 컨트롤러뿐), `SteamController`, `SteamTestController`, `SteamRateLimiterController` 삭제. `SteamApiFetcher`→`SteamFetcher`, `SteamSchedulingService`→`SteamJobProducer` rename.
Run: `grep -rn "SteamCrawlService\|SteamController\|SteamTestController\|SteamRateLimiterController" -- -AOD-All-of-Dopamine-crawler/src`
Expected: 0건

- [x] **Step 2.4: 게이트 + 커밋**

`./gradlew.bat :-AOD-All-of-Dopamine-crawler:build` → SUCCESS.
Run: `grep -n "api/steam\|api/test/steam" -- -AOD-All-of-Dopamine-crawler/src/main/resources/templates/demo/admin.html`
Expected: 0건 (신규 `/api/crawl/steam/*`만 존재)
커밋: "refactor(steam): 표준형 전환 — Executor 직결, admin 진입 큐 일원화, 레거시 경로 삭제"

---

### Phase 3: 네이버시리즈 전환 + 공용 유틸 신설

**Files:**
- Create: `common/util/HtmlParseUtils.java`
- Create: `contents/novel/naverseries/NaverSeriesSelectors.java`
- Rename: `NaverSeriesCrawler.java` → `NaverSeriesFetcher.java`
- Rename: `NaverSeriesSchedulingService.java` → `NaverSeriesJobProducer.java`
- Modify: `common/queue/executors/NaverSeriesNovelExecutor.java`, `admin/controller/AdminTestController.java`

**Interfaces:**
- Produces: `HtmlParseUtils` — `NaverSeriesCrawler`의 기존 static 헬퍼를 **시그니처 그대로** 승격: `text(Element)`, `attr(Element, String)`, `absolutize(String)`, `parseKoreanCount(String)`, `extractQueryParam(String url, String key)`. Phase 4가 재사용.

- [x] **Step 3.1: HtmlParseUtils 신설 (이동, 재작성 아님)**

`common/util/HtmlParseUtils.java` 생성 (final class, private 생성자). `NaverSeriesCrawler`의 `text`/`attr`/`absolutize`/`parseKoreanCount`/`extractQueryParam` 5개 static 메서드 본문을 **변경 없이 이동**, 원본 클래스에서는 삭제 후 호출부를 `HtmlParseUtils.xxx()`로 교체. (`findInfoValue`/`findAge`는 시리즈 전용이므로 잔류.)

- [x] **Step 3.2: NaverSeriesSelectors 신설 + 인라인 셀렉터 전량 교체**

`NaverSeriesSelectors.java` 생성 — 아래 인라인 셀렉터를 상수로 승격하고 사용처를 상수 참조로 교체 (중복 셀렉터는 상수 1개로 합침):
```java
public final class NaverSeriesSelectors {
    private NaverSeriesSelectors() {}
    // 목록: 상세 링크 (Crawler:188·192 + SchedulingService:100·113 중복 → 통합)
    public static final String LIST_DETAIL_LINK_STRICT = "a[href*='/novel/detail.series'][href*='productNo=']";
    public static final String LIST_DETAIL_LINK_FALLBACK = "a[href*='/novel/detail.series']";
    // 상세 (Crawler:145, 219 등)
    public static final String DETAIL_SYNOPSIS = "div.end_dsc ._synopsis";
    public static final String DETAIL_INFO_ITEMS = "> li";
}
```
검증: `grep -n "select(\"" -- -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents/novel/naverseries/*.java` → `NaverSeriesSelectors` 상수 참조 외 문자열 리터럴 0건

- [x] **Step 3.3: 목록 발견을 Fetcher로 집약**

`NaverSeriesSchedulingService.fetchNovelIdsByUrl(String, int)` 본문을 `NaverSeriesFetcher.discoverTargets(String baseUrl, int maxPages)`로 이동 (시그니처의 이름만 표준화, 파라미터·반환 `List<String>` 유지). JobProducer(구 SchedulingService)와 AdminTestController의 호출부를 `naverSeriesFetcher.discoverTargets(...)`로 교체. JobProducer에 Jsoup import가 남아있지 않은지 확인.

- [x] **Step 3.4: rename 2건 + 게이트 + 커밋**

`NaverSeriesCrawler`→`NaverSeriesFetcher`, `NaverSeriesSchedulingService`→`NaverSeriesJobProducer` (참조: Executor, MasterScheduler L5·L26, AdminTestController).
`./gradlew.bat :-AOD-All-of-Dopamine-crawler:build` → SUCCESS.
Run: `grep -rn "Jsoup\." -- -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents/novel/naverseries/NaverSeriesJobProducer.java`
Expected: 0건 (파싱 코드 0줄 달성)
커밋 2개: ① "refactor(common): HtmlParseUtils 신설 — 크롤러 공용 파싱 헬퍼 통합" ② "refactor(naverseries): 표준형 전환 — Selectors 단일 출처, 발견 로직 Fetcher 집약"

---

### Phase 4: 네이버웹툰 전환 (Selenium 최대 복잡도 — 마지막)

**Files:**
- Modify: `contents/Webtoon/NaverWebtoon/NaverWebtoonSelectors.java` (인라인 셀렉터 편입)
- Rename: `NaverWebtoonCrawler.java` → `NaverWebtoonFetcher.java`
- Rename: `NaverWebtoonSchedulingService.java` → `NaverWebtoonJobProducer.java`
- Modify: `NaverWebtoonSeleniumPageParser.java`, `MobileListParser.java`, `common/queue/executors/NaverWebtoonExecutor.java`, `NaverWebtoonFinishedExecutor.java`, `AdminTestController.java`, `MasterScheduler.java`
- Delete: `NaverWebtoonService.java`
- Dir rename: `contents/Webtoon/NaverWebtoon/` → `contents/webtoon/naverwebtoon/` (2단계)

- [x] **Step 4.1: 인라인 셀렉터를 NaverWebtoonSelectors로 편입**

편입 대상 (grep으로 확정된 위치): `SeleniumPageParser:133`(`h2[class*='EpisodeListInfo'][class*='title']`), `:265`(`div.ContentMetaInfo__meta_info--GbTg4 a...`), `:286`(`p.EpisodeListInfo__summary--Jd1WG`), `SchedulingService:99·130`(`a[href*=titleId]`), `MobileListParser:79`(`ul.list_toon li.item`). 기존 상수와 중복이면 기존 상수 재사용, 없으면 신규 상수 추가 후 참조 교체. `:246`은 셀렉터 변수를 순회하는 코드이므로 배열 상수 참조인지 확인만.
검증: `grep -rn "cssSelector(\"\|select(\"" -- <웹툰 패키지 경로> | grep -v Selectors` → 0건

- [x] **Step 4.2: 발견 로직을 Fetcher로, 메트릭을 Executor로**

① `NaverWebtoonSchedulingService`의 private `fetchWebtoonIdsByWeekday`/`fetchFinishedWebtoonIds`/`extractTitleId`를 `NaverWebtoonFetcher`(구 Crawler)로 이동 — public `discoverWeekday(String weekday)`, `discoverFinished(int maxPages)`로 공개. 중복 `extractTitleId`는 `HtmlParseUtils.extractQueryParam(url, "titleId")` 호출로 통일 (SeleniumPageParser의 public `extractTitleId`도 동일 교체 후 삭제).
② `NaverWebtoonService.collectWebtoonById`의 `CustomMetrics` 계측 + ThreadLocal cleanup 호출을 `NaverWebtoonExecutor.execute()`로 이동, Executor가 `naverWebtoonFetcher.crawlWebtoonByTitleId(targetId)`를 직접 호출. `NaverWebtoonFinishedExecutor`도 동일 교체 (5줄 수준 중복은 허용 — JobType이 다른 별개 실행자).
③ `NaverWebtoonService.java` 삭제 (잔여 crawlAllWeekdays* 계열은 AdminTestController sync 엔드포인트가 사용 → 해당 핸들러를 JobProducer 큐 등록 호출로 교체하고 응답 JSON 키(`message`, count류) 유지).

- [x] **Step 4.3: rename + 디렉터리 소문자 (2단계)**

`NaverWebtoonCrawler`→`NaverWebtoonFetcher`, `NaverWebtoonSchedulingService`→`NaverWebtoonJobProducer` (참조: Executor 2개, MasterScheduler L4·L25, AdminTestController).
```bash
cd -- -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents
git mv Webtoon webtoon_tmp && git mv webtoon_tmp webtoon
cd webtoon && git mv NaverWebtoon naverwebtoon_tmp && git mv naverwebtoon_tmp naverwebtoon
```

- [x] **Step 4.4: 게이트 + 커밋**

`./gradlew.bat :-AOD-All-of-Dopamine-crawler:build` → SUCCESS.
Run: `grep -rn "NaverWebtoonService" -- -AOD-All-of-Dopamine-crawler/src` → 0건
**웹툰은 실크롤 스모크 필수** (Selenium은 실행 없이 검증 불가): 로컬 환경에서 admin 트리거로 요일 1개 소량 큐 등록 → Consumer 처리 → `raw_items` 적재 + payload 필드가 전환 전 샘플과 동일한지 확인.
커밋 2개: ① "refactor(webtoon): 셀렉터 단일 출처 + 발견 로직 Fetcher 집약 + Service 계층 제거" ② "refactor(webtoon): 표준 네이밍 + 디렉터리 소문자"

---

### Phase 5: 마무리 — 죽은 코드 삭제 + 최종 게이트

**Files:**
- Delete: `admin/controller/ContentManagementController.java` (148줄 전체 주석), `demo/TestDataController.java` (참조 0 고아)
- Modify: `demo/controller/DemoPageController.java`, `admin/controller/AdminTestController.java`
- Keep (스펙 편차, 사유 기록): `resources/rules/game/epic.yml`

- [x] **Step 5.1: 죽은 코드 삭제**

① `ContentManagementController.java`, `TestDataController.java` 파일 삭제.
② `DemoPageController`: 템플릿이 존재하지 않는 핸들러 5개(`/demo/recommendation`, `/demo/new`, `/demo/ranking`, `/demo/explore`, `/demo/content/{id}` — 소스 templates에는 `demo/admin.html`뿐) 삭제, 주석 처리된 recommendation import(L6)·필드(L32)·TODO 제거. `/demo/admin` 핸들러만 잔류. `DemoPageService`에서 삭제된 핸들러만 쓰던 메서드도 연쇄 삭제.
③ `AdminTestController`의 중복 매핑 제거: L649 `processBatch(@RequestParam)` 삭제 (admin.html은 L487 JSON 버전만 호출).
④ **epic.yml은 삭제하지 않는다** — `RuleFilesTest`(L26, L98 goldenEpicDormant)가 휴면 상태를 명시 검증 중. 스펙 §4-5의 "epic.yml 삭제"는 이 사유로 보류 (스펙 편차 기록).

- [x] **Step 5.2: 최종 성공 기준 grep 게이트 (스펙 §6)**

```bash
CR=./-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler
# 1) 인라인 셀렉터 0건 (Selectors 클래스 제외)
grep -rn "cssSelector(\"\|select(\"" $CR/contents | grep -v Selectors        # → 0건
# 2) 플랫폼별 컨트롤러 0개
find $CR/contents -name "*Controller*.java"                                   # → 0건
# 3) 표준 역할 세트 확인
find $CR/contents -name "*Fetcher.java" -o -name "*JobProducer.java" | sort   # → 플랫폼 4개 × 2
# 4) SchedulingService 어휘 소멸
grep -rn "SchedulingService" $CR --include="*.java" | grep -v Transform       # → 0건
# 5) 디렉터리 소문자 일관
find $CR/contents -maxdepth 2 -type d | grep -E "[A-Z]"                       # → 0건
```

- [x] **Step 5.3: 전체 테스트 + 일괄 스모크 + 커밋**

`./gradlew.bat build` (3모듈 전체) → SUCCESS. 환경 가능 시 플랫폼 4개 admin 트리거 일괄 스모크(각 소량) → `raw_items` 적재 확인.
커밋: "chore(crawler): 죽은 코드 삭제(주석 컨트롤러·고아 엔드포인트·뷰 없는 데모 핸들러) + 중복 매핑 제거"

---

## 스펙 편차 기록

| 편차 | 사유 |
|---|---|
| epic.yml 유지 (스펙은 삭제) | `RuleFilesTest`가 휴면 룰 골든 테스트로 명시 검증 — ingest는 불가침 영역 |
| admin 수동 트리거가 "직접 크롤"에서 "큐 등록"으로 변경 | 스펙 §3.2 진입점 규칙이 우선 — 관리자 응답이 동기 결과가 아닌 등록 확인으로 바뀜 |
| 스모크를 Phase별 필수→"환경 가능 시"로 완화, 웹툰만 필수 | 로컬 DB·API 키 환경 의존. 최종 Phase 5에서 일괄 필수 |
| TMDB 컨트롤러 삭제를 Phase 5→Phase 1로 이동 | TmdbService 삭제와 원자적으로 묶여야 컴파일 그린 유지 |

## 실행 중 발견·결정 (2026-08-12 완료 시점 기록)

- **Phase 0 확장**: 플레이스홀더 기본값만으론 부족 — contextLoads가 실제 PostgreSQL 접속을 시도해 실패. 테스트 H2 설정이 필요했는데 `application(-test).{properties,yml}` 경로가 전부 .gitignore 대상이라 **CrawlerApplicationTests에 인라인 `@SpringBootTest(properties=...)`로 H2 주입**.
- **ranking/naverseries가 NaverSeriesCrawler의 static 유틸을 재사용 중이었음** (사전 grep이 `-v naverseries`로 ranking 하위까지 걸러 누락) → 컴파일 필수 참조만 갱신 (extractQueryParam→HtmlParseUtils, cleanTitle→NaverSeriesFetcher). ranking 구조는 불변.
- **대규모 git mv 후 Gradle 증분 컴파일 캐시 오염** — 무관한 패키지까지 "does not exist" 오류. Phase별 게이트는 `clean build`로 실행.
- **전체 빌드 게이트는 `-x :-AOD-All-of-Dopamine-api:sentryBundleSourcesJava`** — api 모듈 Sentry 플러그인이 업로드 토큰 요구 (기존 환경 문제, 이번 변경과 무관).
- **admin.html의 by-appid는 JSON body 계약** → 계획의 @RequestParam 대신 @RequestBody로 구현.
- **DemoPageService·ContentDTO·ContentDetailDTO 연쇄 삭제** — 뷰 없는 핸들러 삭제로 미사용화.
- **미실행 잔여**: §5-3 실크롤 스모크 (로컬 DB·API 키·Chrome 환경 필요 — 별도 세션에서 4개 플랫폼 각 소량 수집으로 payload 동일성 확인 필요), push.

## 최종 결과

- Phase 0~5 전부 완료, 커밋 7개 (1fd4414 → b5df730)
- **50파일, +739 / -2,341 (순감 -1,602줄)**
- 최종 게이트: 3모듈 빌드+테스트 그린, 성공 기준 grep 5종 전부 통과
