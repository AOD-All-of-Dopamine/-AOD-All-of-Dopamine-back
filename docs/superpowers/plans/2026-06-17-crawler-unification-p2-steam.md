# Crawler Unification — P2-Steam Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate Steam game crawling onto the unified `ContentSource`/`CrawlPipeline` framework: introduce `SteamGameSource`, route both single-item callers (queue executor + admin controller) and the bulk loop through the pipeline, and delete the legacy `SteamCrawlService.collectGameByAppId` + its duplicated save logic — proven behavior-identical by a golden-master characterization test.

**Architecture:** `SteamGameSource implements ContentSource<Map<String,Object>>` delegates fetch to the existing `SteamApiFetcher` and parse to the existing `SteamPayloadProcessor` (so the saved payload is byte-identical). `CrawlPipeline.run(source, id)` owns the save/log/result. The per-platform `SteamGameExecutor` is kept but rewired to the pipeline (it is deleted later in P4 when the consumer talks to the registry directly).

**Tech Stack:** Java 17, Spring Boot 3.5.x, JUnit 5 + Mockito + AssertJ, Jackson. Builds on the Foundation plan (`crawl` package + harness already merged on this branch).

**Spec:** `docs/superpowers/specs/2026-06-17-crawler-unification-design.md` (§4 Steam row, §5 behavior, §6 characterization).
**Prereq:** Foundation plan complete (`SourceDescriptor`, `CrawlPayload`, `CrawlResult`, `ContentSource`, `CrawlPipeline`, `ContentSourceRegistry`, `CanonicalJson`, `SaveRawCapture`, `GoldenFiles` all present on branch `refactor/crawler-unification`).

---

## Conventions

- Run from `-AOD-All-of-Dopamine-back/`. Bash: `./gradlew ...`; PowerShell: `.\gradlew.bat ...`.
- Crawler test task: `:-AOD-All-of-Dopamine-crawler:test`.
- Every commit message ends with the trailer:
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  ```
- Branch: `refactor/crawler-unification`.

---

## Current behavior being preserved (read before starting)

`SteamCrawlService.collectGameByAppId(Long appId)` (the single-item path used by the queue executor and the `/api/steam/collect/by-appid` admin endpoint) does exactly:
1. `Map<String,Object> details = steamApiFetcher.fetchGameDetails(appId)` — returns the Steam `data` map, or null.
2. if `details == null` → return false (no save).
3. if `!"game".equals(details.get("type"))` → return false (no save).
4. `collectorService.saveRaw("Steam", "GAME", payloadProcessor.process(details), String.valueOf(appId), "https://store.steampowered.com/app/" + appId)`.
5. return true.

`SteamPayloadProcessor.process` returns a `HashMap` copy with `genres` → list of `description` strings, `categories` → list of `description` strings, `release_date` → its `date` string. The unified `SteamGameSource` reuses this exact processor, so output is identical.

Callers of `collectGameByAppId`: `SteamGameExecutor.execute` and `SteamController` `/collect/by-appid`. The bulk methods (`collectAllGamesInBatches`, `collectAllGamesInRange`, private `collectGamesFromList`) are used only by admin controllers and call `fetchGameApps()` then per-app fetch+process+save — this is the duplicated logic this plan removes from `collectGamesFromList`.

---

## File Structure

**New:**
| File | Responsibility |
|---|---|
| `.../game/steam/source/SteamGameSource.java` | `ContentSource<Map<String,Object>>` — Steam game fetch+parse |
| `.../src/test/resources/fixtures/steam/app-400.json` | Representative Steam `data` payload fixture (type=game, genres/categories/release_date) |
| `.../src/test/resources/golden/steam-game-400.json` | Golden snapshot of the saveRaw record (bootstrapped by the test) |
| `.../game/steam/source/SteamGameGoldenTest.java` | Characterization: legacy output == golden, then source output == golden |

**Modified:**
| File | Change |
|---|---|
| `.../common/queue/executors/SteamGameExecutor.java` | Delegate to `CrawlPipeline.run(steamGameSource, ...)` |
| `.../game/steam/controller/SteamController.java` | `/collect/by-appid` delegates to pipeline+source |
| `.../game/steam/service/SteamCrawlService.java` | Delete `collectGameByAppId`; `collectGamesFromList` delegates to pipeline; swap deps |

Production prefix: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/`
Test prefix: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/`

---

## Task 1: Characterize current Steam output (lock the golden)

Add a fixture and a test that runs the **current** `collectGameByAppId` and snapshots the `saveRaw` record as the golden. This locks behavior before any change.

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/test/resources/fixtures/steam/app-400.json`
- Create: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/game/steam/source/SteamGameGoldenTest.java`
- Create (bootstrapped by test run): `-AOD-All-of-Dopamine-crawler/src/test/resources/golden/steam-game-400.json`

- [ ] **Step 1: Create the fixture** (`app-400.json`) — a representative Steam `data` payload exercising every processor branch:

```json
{
  "type": "game",
  "name": "Portal",
  "steam_appid": 400,
  "required_age": 0,
  "is_free": false,
  "short_description": "Portal is a new single player game from Valve.",
  "header_image": "https://cdn.akamai.steamstatic.com/steam/apps/400/header.jpg",
  "developers": ["Valve"],
  "publishers": ["Valve"],
  "genres": [
    {"id": "1", "description": "Action"},
    {"id": "23", "description": "Indie"}
  ],
  "categories": [
    {"id": 2, "description": "Single-player"},
    {"id": 22, "description": "Steam Achievements"}
  ],
  "release_date": {"coming_soon": false, "date": "2007년 10월 10일"}
}
```

- [ ] **Step 2: Write the characterization test**

```java
package com.example.crawler.game.steam.source;

import com.example.crawler.crawl.support.GoldenFiles;
import com.example.crawler.crawl.support.SaveRawCapture;
import com.example.crawler.game.steam.fetcher.SteamApiFetcher;
import com.example.crawler.game.steam.processor.SteamPayloadProcessor;
import com.example.crawler.game.steam.service.SteamCrawlService;
import com.example.crawler.ingest.CollectorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SteamGameGoldenTest {

    private static final long APP_ID = 400L;

    private Map<String, Object> loadFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/steam/app-400.json")) {
            return new ObjectMapper().readValue(in, new TypeReference<Map<String, Object>>() {});
        }
    }

    /**
     * Characterization of the CURRENT single-item path. Locks the saveRaw record as the golden.
     * Removed in Task 3 once collectGameByAppId is deleted — the golden then guards SteamGameSource.
     */
    @Test
    void legacyCollectGameByAppIdMatchesGolden() throws Exception {
        SteamApiFetcher fetcher = mock(SteamApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        SteamPayloadProcessor processor = new SteamPayloadProcessor();
        when(fetcher.fetchGameDetails(eq(APP_ID))).thenReturn(loadFixture());

        SteamCrawlService legacy = new SteamCrawlService(fetcher, collector, processor);
        legacy.collectGameByAppId(APP_ID);

        GoldenFiles.assertMatchesGolden("steam-game-400.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
}
```

- [ ] **Step 3: Run to bootstrap the golden (first run FAILS by design)**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.game.steam.source.SteamGameGoldenTest"`
Expected: FAIL with "Golden created at ...steam-game-400.json — review it and re-run." A golden file is now written.

- [ ] **Step 4: Verify the bootstrapped golden content**

Open `-AOD-All-of-Dopamine-crawler/src/test/resources/golden/steam-game-400.json`. Confirm it shows (keys sorted): `"platformName": "Steam"`, `"domain": "GAME"`, `"platformSpecificId": "400"`, `"url": "https://store.steampowered.com/app/400"`, and a `payload` where `genres` is `["Action","Indie"]`, `categories` is `["Single-player","Steam Achievements"]`, `release_date` is `"2007년 10월 10일"`, and the other scalar fields are unchanged. If anything differs, the legacy reading is wrong — STOP and report.

- [ ] **Step 5: Re-run to confirm PASS**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.game.steam.source.SteamGameGoldenTest"`
Expected: PASS (1 test).

- [ ] **Step 6: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/test/resources/fixtures/steam/app-400.json -AOD-All-of-Dopamine-crawler/src/test/resources/golden/steam-game-400.json -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/game/steam/source/SteamGameGoldenTest.java
git commit -m "test(crawler): characterize current Steam game saveRaw output (golden)"
```

---

## Task 2: Add `SteamGameSource` and prove it matches the golden

TDD: add a test that runs the new source through `CrawlPipeline` against the same fixture and asserts the same golden; then implement the source to pass.

**Files:**
- Modify: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/game/steam/source/SteamGameGoldenTest.java`
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/game/steam/source/SteamGameSource.java`

- [ ] **Step 1: Add the source test method** (add inside `SteamGameGoldenTest`, plus the two imports shown):

```java
// add imports at top of SteamGameGoldenTest.java:
import com.example.crawler.crawl.CrawlPipeline;
```

```java
    /**
     * The new unified path (SteamGameSource via CrawlPipeline) must produce the SAME saveRaw
     * record as the legacy path — guarded by the same golden file.
     */
    @Test
    void steamGameSourceMatchesGolden() throws Exception {
        SteamApiFetcher fetcher = mock(SteamApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        SteamPayloadProcessor processor = new SteamPayloadProcessor();
        when(fetcher.fetchGameDetails(eq(APP_ID))).thenReturn(loadFixture());

        SteamGameSource source = new SteamGameSource(fetcher, processor);
        new CrawlPipeline(collector).run(source, String.valueOf(APP_ID));

        GoldenFiles.assertMatchesGolden("steam-game-400.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
```

- [ ] **Step 2: Run to verify it fails to compile**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.game.steam.source.SteamGameGoldenTest"`
Expected: FAIL — `SteamGameSource` does not exist.

- [ ] **Step 3: Implement `SteamGameSource`**

```java
package com.example.crawler.game.steam.source;

import com.example.crawler.crawl.ContentSource;
import com.example.crawler.crawl.CrawlPayload;
import com.example.crawler.crawl.SourceDescriptor;
import com.example.crawler.game.steam.fetcher.SteamApiFetcher;
import com.example.crawler.game.steam.processor.SteamPayloadProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Steam game ContentSource. Fetch delegates to SteamApiFetcher, parse delegates to
 * SteamPayloadProcessor — identical to the legacy SteamCrawlService.collectGameByAppId path.
 */
@Component
@RequiredArgsConstructor
public class SteamGameSource implements ContentSource<Map<String, Object>> {

    private final SteamApiFetcher steamApiFetcher;
    private final SteamPayloadProcessor payloadProcessor;

    @Override
    public SourceDescriptor descriptor() {
        return SourceDescriptor.STEAM_GAME;
    }

    @Override
    public Map<String, Object> fetchDetail(String targetId) {
        return steamApiFetcher.fetchGameDetails(Long.parseLong(targetId));
    }

    @Override
    public CrawlPayload parse(String targetId, Map<String, Object> rawDetail) {
        if (!"game".equals(rawDetail.get("type"))) {
            return null; // not a game → skip (matches legacy)
        }
        return new CrawlPayload(targetId, null, payloadProcessor.process(rawDetail));
    }
}
```

- [ ] **Step 4: Run both tests — verify PASS**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.game.steam.source.SteamGameGoldenTest"`
Expected: PASS (2 tests). Both legacy and source paths match the same golden.

- [ ] **Step 5: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/game/steam/source/SteamGameSource.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/game/steam/source/SteamGameGoldenTest.java
git commit -m "feat(crawler): add SteamGameSource (unified path, matches legacy golden)"
```

---

## Task 3: Route callers through the pipeline and delete legacy single-item code

Rewire the two single-item callers and the bulk loop to use `SteamGameSource` via `CrawlPipeline`, delete `collectGameByAppId`, and delete the now-obsolete legacy characterization test (its golden lives on, guarded by `steamGameSourceMatchesGolden`).

**Files:**
- Modify: `.../common/queue/executors/SteamGameExecutor.java`
- Modify: `.../game/steam/controller/SteamController.java`
- Modify: `.../game/steam/service/SteamCrawlService.java`
- Modify: `.../src/test/java/com/example/crawler/game/steam/source/SteamGameGoldenTest.java`

- [ ] **Step 1: Rewire `SteamGameExecutor`** — replace the whole file:

```java
package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.crawl.CrawlPipeline;
import com.example.crawler.game.steam.source.SteamGameSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Steam 게임 크롤링 Executor — delegates to the unified CrawlPipeline + SteamGameSource.
 * (This thin executor is removed in P4 when the Consumer talks to ContentSourceRegistry directly.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamGameExecutor implements JobExecutor {

    private final CrawlPipeline crawlPipeline;
    private final SteamGameSource steamGameSource;

    @Override
    public JobType getJobType() {
        return JobType.STEAM_GAME;
    }

    @Override
    public boolean execute(String targetId) {
        return crawlPipeline.run(steamGameSource, targetId).isSuccess();
    }

    @Override
    public long getAverageExecutionTime() {
        return 1000; // API 기반, 평균 1초
    }
}
```

- [ ] **Step 2: Rewire the `SteamController` single-item endpoint**

In `SteamController.java`: add fields and rewire `/collect/by-appid`. Change the class fields and the `collectGameByAppId` method body.

Replace the field declaration:
```java
    private final SteamCrawlService steamCrawlService;
```
with:
```java
    private final SteamCrawlService steamCrawlService;
    private final com.example.crawler.crawl.CrawlPipeline crawlPipeline;
    private final com.example.crawler.game.steam.source.SteamGameSource steamGameSource;
```

Replace this line inside `collectGameByAppId(...)`:
```java
            boolean success = steamCrawlService.collectGameByAppId(appId);
```
with:
```java
            boolean success = crawlPipeline.run(steamGameSource, String.valueOf(appId)).isSuccess();
```

(`collectAllGames()` keeps calling `steamCrawlService.collectAllGamesInBatches()` — unchanged.)

- [ ] **Step 3: Refactor `SteamCrawlService`** — swap dependencies, delete `collectGameByAppId`, make `collectGamesFromList` delegate to the pipeline.

Replace the fields and imports block. The new field set:
```java
    private final SteamApiFetcher steamApiFetcher;
    private final com.example.crawler.crawl.CrawlPipeline crawlPipeline;
    private final com.example.crawler.game.steam.source.SteamGameSource steamGameSource;
```
Remove the now-unused fields `private final CollectorService collectorService;` and `private final SteamPayloadProcessor payloadProcessor;` and their imports (`com.example.crawler.ingest.CollectorService`, `com.example.crawler.game.steam.processor.SteamPayloadProcessor`).

Replace the entire private `collectGamesFromList(...)` method with:
```java
    private int collectGamesFromList(List<Map<String, Object>> appList) {
        int collectedCount = 0;
        for (Map<String, Object> app : appList) {
            Long appId = ((Number) app.get("appid")).longValue();
            String appName = (String) app.get("name");

            if (appName == null || appName.isBlank()) {
                continue;
            }

            // 단일 경로(CrawlPipeline + SteamGameSource)로 통일 — fetch/process/save 중복 제거
            if (crawlPipeline.run(steamGameSource, String.valueOf(appId)).isSuccess()) {
                collectedCount++;
            }
        }
        log.info("Steam 게임 데이터 수집 완료. 현재 작업에서 총 {}개의 유효한 게임을 수집했습니다.", collectedCount);
        return collectedCount;
    }
```

Delete the entire `public boolean collectGameByAppId(Long appId) { ... }` method.

(The `collectAllGamesInBatches`, `collectAllGamesInRange` methods and their use of `steamApiFetcher.fetchGameApps()` + `InterruptibleSleep` are unchanged.)

- [ ] **Step 4: Delete the obsolete legacy characterization test method**

In `SteamGameGoldenTest.java`, delete the `legacyCollectGameByAppIdMatchesGolden()` method (it constructs `SteamCrawlService` with the old constructor and calls the deleted `collectGameByAppId`). Keep `steamGameSourceMatchesGolden()` and the imports it still uses. Remove now-unused imports (`SteamCrawlService`).

- [ ] **Step 5: Verify the full module compiles and all tests pass**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test`
Expected: BUILD SUCCESSFUL. `SteamGameGoldenTest.steamGameSourceMatchesGolden` passes against the committed golden; all foundation `crawl.*` tests and `contextLoads` still pass. `ContentSourceRegistry` now registers `SteamGameSource` for `STEAM_GAME`.

- [ ] **Step 6: Confirm no remaining references to the deleted method**

Run: `git grep -n "collectGameByAppId" -- "-AOD-All-of-Dopamine-crawler/src"`
Expected: no matches (the method and all callers are gone). If any remain, fix them before committing.

- [ ] **Step 7: Commit**

```bash
git add -A -- "-AOD-All-of-Dopamine-crawler/src"
git commit -m "refactor(crawler): route Steam through CrawlPipeline+SteamGameSource, delete legacy collectGameByAppId"
```

---

## Self-Review

**1. Spec coverage:** §4 Steam row — `SteamGameSource` added, `SteamApiFetcher`/`SteamPayloadProcessor` reused, `collectGameByAppId` + duplicated `collectGamesFromList` save loop removed (Tasks 2–3). §6 characterization — golden from current code, new path proven equal (Tasks 1–2). §5 behavior — single-item saveRaw byte-identical; `SteamGameExecutor` kept-but-rewired (deleted in P4, as the spec sequences). ✅

**2. Placeholder scan:** No TBD/TODO. Fixture, test code, source code, and exact edits are fully specified. The golden file content is bootstrapped by the test and verified in Task 1 Step 4 against explicit expected values.

**3. Type consistency:** `SteamGameSource` implements `ContentSource<Map<String,Object>>` with `descriptor()/fetchDetail(String)/parse(String, Map)`. `CrawlPipeline.run(source, id).isSuccess()` used consistently in executor, controller, and bulk loop. `SteamApiFetcher.fetchGameDetails(Long)` and `SteamPayloadProcessor.process(Map)` signatures match the real classes. `SourceDescriptor.STEAM_GAME` provides "Steam"/"GAME"/url, matching the legacy literals locked by the golden.

**Out of scope (later):** `collectAllGamesInBatches`/`collectAllGamesInRange` bulk orchestration and their admin endpoints stay (their per-item save now goes through the pipeline; the scheduler already enqueues). `SteamGameExecutor` deletion is P4.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-17-crawler-unification-p2-steam.md`. Execute with subagent-driven-development: implement each task → spec review → code-quality review, sequentially on `refactor/crawler-unification`.
