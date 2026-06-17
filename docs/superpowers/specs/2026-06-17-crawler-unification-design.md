# 크롤러 통일 리팩토링 설계 (Crawler Unification)

- **작성일:** 2026-06-17
- **대상 모듈:** `-AOD-All-of-Dopamine-crawler`
- **상태:** 설계 승인 대기 (브레인스토밍 산출물)

---

## 1. 문제 정의 (Problem)

플랫폼/도메인마다 크롤러 로직의 **구조(모양) 자체가 제각각**이라 사람이 읽고 유지보수하기 어렵다. 같은 일(크롤 → `raw_items` 저장)을 하는데 클래스 생김새·계층·네이밍·타입·로깅이 전부 다르다.

| 플랫폼/도메인 | 현재 모양 | 단일 진입 메서드 |
|---|---|---|
| Steam (GAME) | `SteamCrawlService` → `SteamApiFetcher` + `SteamPayloadProcessor` (3계층) | `collectGameByAppId(Long)` |
| TMDB (MOVIE/TV) | `TmdbService` 한 클래스에 bulk·discovery·단일 전부 혼재 (~300줄) | `collectMovieById(String)` / `collectTvShowById(String)` |
| NaverWebtoon (WEBTOON) | `NaverWebtoonService`가 `NaverWebtoonCrawler`를 감싸기만 함 | `collectWebtoonById(String)` |
| NaverSeries (WEBNOVEL) | `NaverSeriesCrawler` `@Component` 하나가 Jsoup fetch + 600줄 파싱 + payload 생성을 전부 inline, `System.out.println` 디버그 | `collectNovelById(String)` |
| KakaoPage | `KakaoPageCrawler` 존재하나 scheduler/executor 미연결 (dead-in-pipeline) | — |

### 갈라짐의 구체적 증상
1. **네이밍 불일치:** `collectGameByAppId` / `collectMovieById` / `collectWebtoonById` / `collectNovelById` — 규칙 없음.
2. **ID 타입 불일치:** Steam은 `Long`, 나머지는 `String`.
3. **계층 불일치:** Steam·TMDB는 Fetcher+Processor 분리, NaverSeries는 전부 inline, NaverWebtoon은 래퍼.
4. **리터럴 하드코딩:** `"Steam"`/`"GAME"`, `"TMDB_MOVIE"`/`"MOVIE"`, URL 템플릿이 각 클래스에 흩어짐.
5. **로깅 천차만별:** 이모지(🎬📖✅❌), `System.out.println`(NaverSeries), slf4j 혼용.
6. **메트릭은 NaverWebtoon만** 기록 (그것도 legacy `CustomMetrics`).
7. **이중 진입점:** ① 스케줄러 bulk(큐 우회, 직접 fetch+save) ② 큐 단일(executor). → 같은 parse/save 로직을 **플랫폼마다 두 번씩** 작성:
   - Steam: `collectGameByAppId` vs `collectGamesFromList` 루프
   - TMDB: `collectMovieById` vs `processMovieList` 루프
   - NaverSeries: `collectNovelById`(~130줄) vs `crawlToRaw` 루프(거의 동일한 ~130줄)

---

## 2. 목표 / 비목표 (Goals / Non-Goals)

### 목표
- **읽기 일관성:** 모든 플랫폼 크롤러가 동일한 모양(`descriptor / fetchDetail / parse`)으로 읽힌다. "네이버 시리즈 크롤러를 이해하면 스팀 크롤러도 똑같이 읽힌다."
- **확장성:** 새 플랫폼 추가 = `ContentSource` 구현 1개(+Fetcher/Parser) 작성 → 레지스트리 자동 등록. Consumer·Pipeline 무수정 (OCP).
- **중복 제거:** fetch→parse→save 교차 관심사를 단 한 곳(`CrawlPipeline`)으로. 플랫폼별 이중 작성(단일/bulk) 제거.

### 비목표
- Transform Engine / YAML 룰 / Upsert 파이프라인 변경 (별개 서브시스템, 무관).
- API·shared 모듈 변경.
- KakaoPage 실제 동작 완성(scheduler/executor 연결). → 구조만 맞추고 미연결 유지.
- 크롤링 대상·수집 데이터 자체의 변경 (저장되는 per-item payload는 동일 유지).

---

## 3. 선택한 아키텍처 (합성 + 런너 + 레지스트리, 단일 경로)

기존 `JobExecutorRegistry`의 합성·자동등록 관습과 일치하는 **composition + single runner + registry** 방식. 콘텐츠 진입점을 **하나로** 통일한다.

### 3.1 단일 경로 (Single Path)

```
스케줄러(thin) ──► ContentEnumerator        "무엇을 크롤?" → targetId 목록 산출
                        │                     (discover/목록스크랩/앱목록/요일)
                        ▼  CrawlJobProducer.createJobs(jobType, ids)   ← enqueue만
                 ┌──────────────┐
                 │  crawl_job 큐 │   (SKIP LOCKED, 백프레셔 캡)
                 └──────────────┘
                        │  Consumer (배치 claim)
                        ▼
                 CrawlPipeline.run(source, id)     "한 건을 어떻게" → 교차관심사 한 곳
                        ├─ source.fetchDetail(id)   (HTTP/Jsoup/Selenium)
                        ├─ source.parse(id, raw)    (순수함수, skip이면 null)
                        ├─ CollectorService.saveRaw(...)
                        └─ 검증·로깅·메트릭·예외·cleanup
                        ▼
                    raw_items
```

기존의 두 입구(스케줄러 직접 저장 / 큐 단일)를 폐지하고, **모든 크롤이 `스케줄러 → enumerate → enqueue → consumer → pipeline → fetch → parse → save` 한 경로**를 탄다.

### 3.2 레이어별 단일 책임

| 레이어 | 타입 | 책임 | 플랫폼별 |
|---|---|---|---|
| 열거 | `ContentEnumerator` | "무엇을 크롤?" → id 목록 → enqueue만 | discover(TMDB)/목록(NaverSeries)/앱목록(Steam)/요일(Webtoon) |
| 수집 | `ContentSource.fetchDetail` | id → raw detail (IO, rate limit, Selenium 수명) | 기존 Fetcher 위임 |
| 파싱 | `ContentSource.parse` | raw → payload (순수함수, 단위테스트) | Parser 위임 |
| 실행 | `CrawlPipeline` | 검증/로깅/메트릭/저장/예외 — **단 한 곳** | 공통(무수정) |
| 메타 | `SourceDescriptor` | platform/domain/jobType/url/튜닝값 단일출처 | enum 상수 1줄 |
| 매핑 | `ContentSourceRegistry` | jobType → source (자동등록) | 공통 |

### 3.3 핵심 타입

```java
// (1) 메타데이터 단일 출처 — 흩어진 리터럴 + JobType 결합 흡수
public enum SourceDescriptor {
    STEAM_GAME    ("Steam",       "GAME",     JobType.STEAM_GAME,         false, 1000, id -> "https://store.steampowered.com/app/" + id),
    TMDB_MOVIE    ("TMDB_MOVIE",  "MOVIE",    JobType.TMDB_MOVIE,         false,  800, id -> "https://www.themoviedb.org/movie/" + id),
    TMDB_TV       ("TMDB_TV",     "TV",       JobType.TMDB_TV,            false,  800, id -> "https://www.themoviedb.org/tv/" + id),
    NAVER_WEBTOON ("NaverWebtoon","WEBTOON",  JobType.NAVER_WEBTOON,      true,  5000, id -> "https://comic.naver.com/webtoon/list?titleId=" + id),
    NAVER_SERIES  ("NaverSeries", "WEBNOVEL", JobType.NAVER_SERIES_NOVEL, false, 2000, id -> "https://series.naver.com/novel/detail.series?productNo=" + id);
    // fields: platformName, domain, jobType, usesSelenium, avgExecMillis, urlFn
    // accessors: platformName(), domain(), jobType(), usesSelenium(), avgExecMillis(), urlOf(id), recommendedBatchSize()
}

// (2) parse 결과 = 저장에 필요한 3요소
public record CrawlPayload(String platformSpecificId, String url, Map<String,Object> payload) {
    // url == null 이면 descriptor.urlOf(platformSpecificId) 사용
}

// (3) 플랫폼 행위 — 작고 똑같은 모양
public interface ContentSource<D> {
    SourceDescriptor descriptor();
    D            fetchDetail(String targetId);      // HTTP / Jsoup / Selenium
    CrawlPayload parse(String targetId, D raw);     // raw → payload (skip 대상이면 null)
    default void cleanup() {}                        // Selenium ThreadLocal 정리 훅
}

// (4) 열거 — "무엇을 크롤할지" (스케줄러가 사용)
public interface ContentEnumerator {
    JobType jobType();
    List<String> enumerate(EnumerationRequest req); // discover/목록/앱목록 → targetId 목록
}

// (5) 단일 런너 — 검증·로깅·메트릭·저장·예외가 여기 한 곳에만 (중복 0)
@Component
public class CrawlPipeline {
    private final CollectorService collector;
    private final CrawlMetrics metrics;

    public <D> CrawlResult run(ContentSource<D> source, String targetId) {
        SourceDescriptor d = source.descriptor();
        var timer = metrics.start();
        try {
            if (isBlank(targetId)) return CrawlResult.skipped("blank id");
            D raw = source.fetchDetail(targetId);
            if (raw == null) return CrawlResult.skipped("no detail");
            CrawlPayload p = source.parse(targetId, raw);
            if (p == null) return CrawlResult.skipped("filtered");      // 성인물/비게임 등
            String url = p.url() != null ? p.url() : d.urlOf(p.platformSpecificId());
            collector.saveRaw(d.platformName(), d.domain(), p.payload(), p.platformSpecificId(), url);
            metrics.success(d);
            return CrawlResult.success();
        } catch (Exception e) {
            metrics.failure(d, e);
            log.error("[{}/{}] id={} 크롤 실패: {}", d.platformName(), d.domain(), targetId, e.getMessage(), e);
            return CrawlResult.failed(e);
        } finally {
            metrics.stop(timer, d);
            source.cleanup();
        }
    }
}

// (6) jobType → source (JobExecutorRegistry와 동일한 자동등록 관습)
@Component
public class ContentSourceRegistry {
    private final Map<JobType, ContentSource<?>> byJobType;  // 생성자에서 List<ContentSource<?>> 주입, 중복 시 실패
    public ContentSource<?> get(JobType t) { ... }
}

// (7) 결과 모델
public record CrawlResult(Status status, String reason, Throwable error) {
    public enum Status { SUCCESS, SKIPPED, FAILED }
    static CrawlResult success();            // → 잡 COMPLETED
    static CrawlResult skipped(String why);  // → 잡 COMPLETED (재시도 안 함)
    static CrawlResult failed(Throwable e);  // → 잡 FAILED (재시도)
}
```

---

## 4. 플랫폼별 마이그레이션 매핑

모든 플랫폼이 **Fetcher + Parser + Source(=descriptor)** 동일 3종 세트로 정리된다.

| 플랫폼 | 유지 | 신규 | 삭제 |
|---|---|---|---|
| Steam (GAME) | `SteamApiFetcher`, `SteamPayloadProcessor`, `SteamRateLimiter` | `SteamGameSource` (parse에서 `type!="game"` → null), `SteamEnumerator`(앱목록) | `SteamCrawlService.collectGameByAppId`, `collectGamesFromList`, `collectAllGamesInBatches/Range` |
| TMDB (MOVIE/TV) | `TmdbApiFetcher`, `TmdbPayloadProcessor` | `TmdbMovieSource`, `TmdbTvSource`(각 ~30줄), `TmdbEnumerator`(discover) | `TmdbService`의 `processMovieList`, `processTvShowList`, `collectMoviesForPeriod`, `collect*ById` 등 inline 루프 |
| NaverWebtoon (WEBTOON) | `NaverWebtoonCrawler`, `NaverWebtoonSeleniumPageParser`, selectors | `NaverWebtoonSource`(`cleanup()`=ThreadLocal 정리), `NaverWebtoonEnumerator`(요일/완결) | `NaverWebtoonService` 래퍼 메서드들 |
| NaverSeries (WEBNOVEL) ★최대 정리★ | (없음 — 전부 inline이었음) | `NaverSeriesNovelFetcher`(Jsoup get + firstDate 서브요청), `NaverSeriesNovelParser`(셀렉터/추출 전부, 순수함수), `NaverSeriesNovelSource`, `NaverSeriesEnumerator`(목록) | `NaverSeriesCrawler.crawlToRaw`, `collectNovelById` (이중 작성된 파싱) |
| KakaoPage | `KakaoPageNovelDTO` | `KakaoPageNovelSource`(+Fetcher/Parser) — 구조만, **미연결** | — |

### 삭제되는 것 (구조 단순화)
- **per-platform Executor 6개 삭제** (`SteamGameExecutor`, `TmdbMovieExecutor`, `TmdbTvExecutor`, `NaverWebtoonExecutor`, `NaverWebtoonFinishedExecutor`, `NaverSeriesNovelExecutor`) → Consumer가 `registry.get(jobType)`로 직접 호출.
- 플랫폼별 inline fetch+save 루프 전부.
- 스케줄러의 `@Async` 직접 크롤 → enumerate→enqueue 두 줄로 축소.

---

## 5. 동작 변화 (명시적)

특성화 테스트로 **per-item 저장 payload는 동일**함을 보장한다. 다음은 의도된 변화다:

1. **bulk가 동기 → 비동기(큐 경유):** 스케줄러가 즉시 전량 저장하지 않고 id를 큐에 적재 → Consumer가 10초 배치로 점진 처리. 전체 완료 시간이 늘고, bulk가 `crawl_job_*` 지표에 노출됨. 백프레셔 캡(`MAX_CONCURRENT_JOBS`, `MAX_SELENIUM_JOBS`)이 bulk에도 적용됨(현재는 우회).
2. **SKIPPED → 잡 COMPLETED (재시도 안 함):** 현재 성인물/비게임 skip이 `false`→FAILED로 무한 재시도되는 버그가 고쳐진다.
3. **`NAVER_WEBTOON_FINISHED`도 Selenium 캡에 집계:** `descriptor.usesSelenium()`로 정상 계산(현재 누락 버그 수정).
4. **전 플랫폼 메트릭 자동 기록:** 현재 웹툰만 기록하던 것이 모든 플랫폼으로 통일.
5. **로깅 통일:** 이모지·`System.out.println` 제거, `CrawlPipeline`이 성공/skip/실패를 일관 형식으로.

---

## 6. 특성화 테스트 전략 (Golden-Master)

크롤러 모듈은 현재 `CrawlerApplicationTests`(컨텍스트 로드) 하나뿐 → 광범위 리팩토링 안전망을 먼저 만든다.

1. **Fixture 고정:** 플랫폼별 실제 raw 응답을 오프라인 테스트 리소스로 저장.
   - Steam: app details JSON
   - TMDB: movie details JSON, tv details JSON
   - NaverSeries: 상세 페이지 HTML + `volumeList` JSON(firstDate용)
   - NaverWebtoon: 렌더된 상세 HTML/DTO
   - 엣지 케이스 포함: 성인물 소설, 비게임 앱, 필드 누락.
2. **Golden 캡처:** **현재 코드**의 parse/process를 fixture에 돌려 `CollectorService.saveRaw(platform, domain, payload, psid, url)` 인자를 spy로 가로채 → canonical 직렬화(`sha256Canonical`과 동일한 Jackson 바이트) → golden JSON으로 저장.
3. **리팩토링 Green:** 새 `ContentSource`의 `fetchDetail`을 fixture 반환으로 모킹, `parse` 결과 `CrawlPayload`의 canonical 형태가 **golden과 byte-동일**임을 단언 → 동작 보존 증명. (`payload`는 `LinkedHashMap` 키 순서 유지)
4. **부수효과:** 이 테스트들이 영구 parser 단위테스트가 된다(합성의 보상). 성인물/비게임 → `SKIPPED` 단언.
5. **범위:** parse(결정적)만 특성화. fetch(네트워크 IO)는 모킹. Enumerator는 목록 HTML 모킹 → id 집합 단언. Pipeline은 모킹 source로 saveRaw 인자·결과 매핑 단언.
6. **위치/도구:** `crawler/src/test/java/.../crawl/`, JUnit5 + Mockito. 네트워크 없음 → CI 안전·결정적.

---

## 7. 단계별 진행 (Strangler — 항상 컴파일·green)

| 단계 | 내용 | 프로덕션 영향 |
|---|---|---|
| **P0** | 특성화 테스트 베이스라인 (5종, 현행 코드 기준 fixture+golden) | 없음 (테스트만) |
| **P1** | 핵심 타입 추가: `SourceDescriptor`, `CrawlPayload`, `ContentSource`, `ContentEnumerator`, `CrawlPipeline`, `ContentSourceRegistry`, `CrawlResult`, `CrawlMetrics` | 미배선 (추가만) |
| **P2** | 플랫폼 1개씩 이관 (Steam→TMDB→Webtoon→NaverSeries→Kakao): Source 구현(+NaverSeries Fetcher/Parser 분리), Consumer→pipeline 배선, 특성화 green 확인 후 구 단일+bulk 코드 삭제 | 플랫폼별 점진 |
| **P3** | 스케줄러 → `ContentEnumerator`로 enumerate/enqueue 전환, inline 루프 삭제 | bulk 비동기화 |
| **P4** | Executor 6개 삭제 + Consumer 정리 + 메트릭/로깅 표준화 + Selenium 캡 수정 | 구조 정리 |
| **P5** | dead code 제거, KakaoPage 구조 정렬(미연결), 문서 갱신(`docs/2_JOB_QUEUE_ARCHITECTURE.md`) | 마무리 |

각 단계는 컴파일되고 특성화 테스트를 통과한 상태를 유지한다. P2는 플랫폼 단위로 green을 확인하며 진행한다.

---

## 8. 리스크 / 완화

| 리스크 | 완화 |
|---|---|
| bulk 비동기화로 처리량/완료시간 체감 변화 | P3에서 별도 단계로 분리, 큐 배치 사이즈 튜닝값(`recommendedBatchSize`) 검토 |
| Selenium 수명/정리(ThreadLocal) 회귀 | `cleanup()` 훅을 파이프라인 `finally`에서 보장, 웹툰 이관(P2) 시 집중 검증 |
| 특성화 fixture가 실제와 다를 가능성 | 실제 응답 캡처로 fixture 생성, 엣지 케이스 명시 포함 |
| 거대 `TmdbService` 분해 중 discovery 로직 누락 | `TmdbEnumerator`로 옮길 때 페이지네이션/날짜범위/언어 파라미터 보존 |
| 큐 경로로 통일 시 `retryFailedJobs` no-op·무백오프 재시도와 상호작용 | SKIPPED→COMPLETED 매핑으로 filtered 무한재시도는 해소. 재시도 백오프 개선은 본 범위 밖(후속). |

---

## 9. 미해결 질문 (Open Questions)

- 없음. (bulk 비동기화는 P3 단계로 합의됨, KakaoPage 미연결 합의됨, 특성화 우선 합의됨.)
