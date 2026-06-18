# Crawler Unification — P2-TMDB Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate TMDB movie & TV single-item crawling onto the unified framework: add `TmdbMovieSource` + `TmdbTvSource`, route `TmdbMovieExecutor`/`TmdbTvExecutor` through `CrawlPipeline`, and delete the legacy `TmdbService.collectMovieById` / `collectTvShowById` — proven behavior-identical by golden-master tests.

**Architecture:** `TmdbMovieSource` / `TmdbTvSource` implement `ContentSource<Map<String,Object>>`, delegating fetch to the existing `TmdbApiFetcher` (`getMovieDetails`/`getTvShowDetails`, language hardcoded `"ko-KR"` exactly as the legacy single-item path) and parse to the existing `TmdbPayloadProcessor`. The bulk/discovery methods (`processMovieList`, `collectAllMoviesByYear`, etc.) are intentionally left for P3 (they take a `language` parameter and become a `TmdbEnumerator`).

**Tech Stack:** Java 17, Spring Boot 3.5.x, JUnit 5 + Mockito + AssertJ, Jackson. Builds on the Foundation (`crawl` package + harness) and P2-Steam (pattern reference).

**Spec:** `docs/superpowers/specs/2026-06-17-crawler-unification-design.md` (§4 TMDB row, §5 behavior, §6 characterization).
**Prereq:** Foundation + P2-Steam complete on branch `refactor/crawler-unification`.

---

## Conventions

- Run from `-AOD-All-of-Dopamine-back/`. Bash: `./gradlew ...`; PowerShell: `.\gradlew.bat ...`.
- Crawler test task: `:-AOD-All-of-Dopamine-crawler:test`.
- `git add` paths start with `-`, so always use the `--` separator: `git add -- "<path>"`.
- Every commit message ends with the trailer:
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  ```

---

## Current behavior being preserved (read before starting)

`TmdbService.collectMovieById(String movieId)` (single-item path; only caller = `TmdbMovieExecutor`):
1. `language = "ko-KR"`, `id = Integer.parseInt(movieId)`.
2. `Map detailedData = tmdbApiFetcher.getMovieDetails(id, language)`.
3. if `detailedData == null || detailedData.isEmpty()` → return false (no save).
4. `saveRaw("TMDB_MOVIE", "MOVIE", payloadProcessor.process(detailedData), movieId, "https://www.themoviedb.org/movie/" + movieId)`.
5. return true. (any exception → return false)

`collectTvShowById(String tvId)` is identical with `getTvShowDetails`, `"TMDB_TV"`, `"TV"`, and `"https://www.themoviedb.org/tv/" + tvId`.

`TmdbPayloadProcessor.process` returns a `HashMap` with: copied fields (id, overview, release_date, first_air_date, name, title, original_name, original_title, runtime, episode_run_time, number_of_seasons — only those present), `poster_image_url` from `poster_path` (prefixed `https://image.tmdb.org/t/p/w500`), `genres` → list of names, `cast` → top-10 names, `directors`/`writers` → crew names by job (only if non-empty), `watch_providers` → KR flatrate provider names (only if non-empty). The new sources reuse this exact processor → identical output.

Signatures: `TmdbApiFetcher.getMovieDetails(int, String)`, `getTvShowDetails(int, String)` → `Map<String,Object>`. `TmdbService` constructor (Lombok `@RequiredArgsConstructor`) order: `(TmdbApiFetcher, CollectorService, TmdbPayloadProcessor)` — unchanged by this plan (bulk methods still use all three).

---

## File Structure

**New:**
| File | Responsibility |
|---|---|
| `.../contents/TMDB/source/TmdbMovieSource.java` | `ContentSource<Map>` — TMDB movie fetch+parse |
| `.../contents/TMDB/source/TmdbTvSource.java` | `ContentSource<Map>` — TMDB TV fetch+parse |
| `.../src/test/resources/fixtures/tmdb/movie-27205.json` | Movie detail fixture (genres/credits/watch providers) |
| `.../src/test/resources/fixtures/tmdb/tv-1396.json` | TV detail fixture |
| `.../src/test/resources/golden/tmdb-movie-27205.json` | Golden (bootstrapped by test) |
| `.../src/test/resources/golden/tmdb-tv-1396.json` | Golden (bootstrapped by test) |
| `.../contents/TMDB/source/TmdbMovieGoldenTest.java` | Characterization: legacy==golden, source==golden |
| `.../contents/TMDB/source/TmdbTvGoldenTest.java` | Characterization: legacy==golden, source==golden |

**Modified:**
| File | Change |
|---|---|
| `.../common/queue/executors/TmdbMovieExecutor.java` | Delegate to `CrawlPipeline.run(tmdbMovieSource, ...)` |
| `.../common/queue/executors/TmdbTvExecutor.java` | Delegate to `CrawlPipeline.run(tmdbTvSource, ...)` |
| `.../contents/TMDB/service/TmdbService.java` | Delete `collectMovieById` + `collectTvShowById` |

Production prefix: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/`
Test prefix: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/`

**Out of scope (P3):** `processMovieList`, `processTvShowList`, `collectMoviesForPeriod`, `collectAllMoviesByYear`, `collectPopular*`, `collect*ByYearSample`, `collectNewContentAsync`, `updatePastContentAsync` and their controllers — these become a `TmdbEnumerator` in P3.

---

## Task 1: Characterize current TMDB movie & TV output (lock goldens)

**Files:**
- Create: `.../src/test/resources/fixtures/tmdb/movie-27205.json`
- Create: `.../src/test/resources/fixtures/tmdb/tv-1396.json`
- Create: `.../src/test/java/com/example/crawler/contents/TMDB/source/TmdbMovieGoldenTest.java`
- Create: `.../src/test/java/com/example/crawler/contents/TMDB/source/TmdbTvGoldenTest.java`
- Create (bootstrapped): `.../src/test/resources/golden/tmdb-movie-27205.json`, `.../golden/tmdb-tv-1396.json`

- [ ] **Step 1: Create the movie fixture** (`fixtures/tmdb/movie-27205.json`):

```json
{
  "id": 27205,
  "title": "Inception",
  "original_title": "Inception",
  "overview": "A thief who steals corporate secrets through dream-sharing technology.",
  "release_date": "2010-07-16",
  "runtime": 148,
  "popularity": 82.5,
  "vote_count": 34000,
  "poster_path": "/inception.jpg",
  "genres": [
    {"id": 28, "name": "Action"},
    {"id": 878, "name": "Science Fiction"}
  ],
  "credits": {
    "cast": [
      {"name": "Leonardo DiCaprio"},
      {"name": "Joseph Gordon-Levitt"}
    ],
    "crew": [
      {"job": "Director", "name": "Christopher Nolan"},
      {"job": "Writer", "name": "Christopher Nolan"}
    ]
  },
  "watch/providers": {
    "results": {"KR": {"flatrate": [{"provider_name": "Netflix"}, {"provider_name": "Watcha"}]}}
  }
}
```

- [ ] **Step 2: Create the TV fixture** (`fixtures/tmdb/tv-1396.json`):

```json
{
  "id": 1396,
  "name": "Breaking Bad",
  "original_name": "Breaking Bad",
  "overview": "A high school chemistry teacher turned methamphetamine producer.",
  "first_air_date": "2008-01-20",
  "episode_run_time": [47],
  "number_of_seasons": 5,
  "popularity": 410.2,
  "vote_count": 12000,
  "poster_path": "/breakingbad.jpg",
  "genres": [
    {"id": 18, "name": "Drama"}
  ],
  "credits": {
    "cast": [
      {"name": "Bryan Cranston"},
      {"name": "Aaron Paul"}
    ],
    "crew": [
      {"job": "Writer", "name": "Vince Gilligan"}
    ]
  },
  "watch/providers": {
    "results": {"KR": {"flatrate": [{"provider_name": "Netflix"}]}}
  }
}
```

- [ ] **Step 3: Write the movie characterization test** (`TmdbMovieGoldenTest.java`):

```java
package com.example.crawler.contents.TMDB.source;

import com.example.crawler.crawl.support.GoldenFiles;
import com.example.crawler.crawl.support.SaveRawCapture;
import com.example.crawler.contents.TMDB.fetcher.TmdbApiFetcher;
import com.example.crawler.contents.TMDB.processor.TmdbPayloadProcessor;
import com.example.crawler.contents.TMDB.service.TmdbService;
import com.example.crawler.ingest.CollectorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TmdbMovieGoldenTest {

    private static final int MOVIE_ID = 27205;

    private Map<String, Object> loadFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/tmdb/movie-27205.json")) {
            Objects.requireNonNull(in, "fixture not found: /fixtures/tmdb/movie-27205.json");
            return new ObjectMapper().readValue(in, new TypeReference<Map<String, Object>>() {});
        }
    }

    /** Characterization of the CURRENT single-item movie path. Removed in Task 3. */
    @Test
    void legacyCollectMovieByIdMatchesGolden() throws Exception {
        TmdbApiFetcher fetcher = mock(TmdbApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        TmdbPayloadProcessor processor = new TmdbPayloadProcessor();
        when(fetcher.getMovieDetails(eq(MOVIE_ID), eq("ko-KR"))).thenReturn(loadFixture());

        TmdbService legacy = new TmdbService(fetcher, collector, processor);
        legacy.collectMovieById(String.valueOf(MOVIE_ID));

        GoldenFiles.assertMatchesGolden("tmdb-movie-27205.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
}
```

- [ ] **Step 4: Write the TV characterization test** (`TmdbTvGoldenTest.java`):

```java
package com.example.crawler.contents.TMDB.source;

import com.example.crawler.crawl.support.GoldenFiles;
import com.example.crawler.crawl.support.SaveRawCapture;
import com.example.crawler.contents.TMDB.fetcher.TmdbApiFetcher;
import com.example.crawler.contents.TMDB.processor.TmdbPayloadProcessor;
import com.example.crawler.contents.TMDB.service.TmdbService;
import com.example.crawler.ingest.CollectorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TmdbTvGoldenTest {

    private static final int TV_ID = 1396;

    private Map<String, Object> loadFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/tmdb/tv-1396.json")) {
            Objects.requireNonNull(in, "fixture not found: /fixtures/tmdb/tv-1396.json");
            return new ObjectMapper().readValue(in, new TypeReference<Map<String, Object>>() {});
        }
    }

    /** Characterization of the CURRENT single-item TV path. Removed in Task 3. */
    @Test
    void legacyCollectTvShowByIdMatchesGolden() throws Exception {
        TmdbApiFetcher fetcher = mock(TmdbApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        TmdbPayloadProcessor processor = new TmdbPayloadProcessor();
        when(fetcher.getTvShowDetails(eq(TV_ID), eq("ko-KR"))).thenReturn(loadFixture());

        TmdbService legacy = new TmdbService(fetcher, collector, processor);
        legacy.collectTvShowById(String.valueOf(TV_ID));

        GoldenFiles.assertMatchesGolden("tmdb-tv-1396.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
}
```

- [ ] **Step 5: Run to bootstrap goldens (first run FAILS by design)**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.contents.TMDB.source.TmdbMovieGoldenTest" --tests "com.example.crawler.contents.TMDB.source.TmdbTvGoldenTest"`
Expected: FAIL with "Golden created ..." for both. Two golden files written.

- [ ] **Step 6: Verify the bootstrapped goldens**

`golden/tmdb-movie-27205.json` must show `platformName "TMDB_MOVIE"`, `domain "MOVIE"`, `platformSpecificId "27205"`, `url "https://www.themoviedb.org/movie/27205"`, and a `payload` containing `genres ["Action","Science Fiction"]`, `cast ["Leonardo DiCaprio","Joseph Gordon-Levitt"]`, `directors ["Christopher Nolan"]`, `writers ["Christopher Nolan"]`, `watch_providers ["Netflix","Watcha"]`, `poster_image_url "https://image.tmdb.org/t/p/w500/inception.jpg"`, plus `id`, `title`, `original_title`, `overview`, `release_date`, `runtime`. (Note: `popularity`/`vote_count` are dropped by the processor — it only copies its whitelist.)

`golden/tmdb-tv-1396.json` must show `platformName "TMDB_TV"`, `domain "TV"`, `platformSpecificId "1396"`, `url "https://www.themoviedb.org/tv/1396"`, payload with `genres ["Drama"]`, `cast ["Bryan Cranston","Aaron Paul"]`, `writers ["Vince Gilligan"]` (NO `directors` key — no Director crew), `watch_providers ["Netflix"]`, `poster_image_url`, `name`, `original_name`, `overview`, `first_air_date`, `episode_run_time`, `number_of_seasons`.

If anything differs, STOP and report.

- [ ] **Step 7: Re-run to confirm PASS**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.contents.TMDB.source.TmdbMovieGoldenTest" --tests "com.example.crawler.contents.TMDB.source.TmdbTvGoldenTest"`
Expected: PASS (2 tests).

- [ ] **Step 8: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/test/resources/fixtures/tmdb/movie-27205.json" "-AOD-All-of-Dopamine-crawler/src/test/resources/fixtures/tmdb/tv-1396.json" "-AOD-All-of-Dopamine-crawler/src/test/resources/golden/tmdb-movie-27205.json" "-AOD-All-of-Dopamine-crawler/src/test/resources/golden/tmdb-tv-1396.json" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/contents/TMDB/source/TmdbMovieGoldenTest.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/contents/TMDB/source/TmdbTvGoldenTest.java"
git commit -m "test(crawler): characterize current TMDB movie/TV saveRaw output (goldens)"
```

---

## Task 2: Add `TmdbMovieSource` + `TmdbTvSource` and prove they match the goldens

**Files:**
- Create: `.../contents/TMDB/source/TmdbMovieSource.java`, `.../contents/TMDB/source/TmdbTvSource.java`
- Modify: `TmdbMovieGoldenTest.java`, `TmdbTvGoldenTest.java`

- [ ] **Step 1: Add the source-match test methods** (add to each test class, plus the import):

In `TmdbMovieGoldenTest.java` add import `import com.example.crawler.crawl.CrawlPipeline;` and method:

```java
    @Test
    void tmdbMovieSourceMatchesGolden() throws Exception {
        TmdbApiFetcher fetcher = mock(TmdbApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        TmdbPayloadProcessor processor = new TmdbPayloadProcessor();
        when(fetcher.getMovieDetails(eq(MOVIE_ID), eq("ko-KR"))).thenReturn(loadFixture());

        TmdbMovieSource source = new TmdbMovieSource(fetcher, processor);
        new CrawlPipeline(collector).run(source, String.valueOf(MOVIE_ID));

        GoldenFiles.assertMatchesGolden("tmdb-movie-27205.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
```

In `TmdbTvGoldenTest.java` add import `import com.example.crawler.crawl.CrawlPipeline;` and method:

```java
    @Test
    void tmdbTvSourceMatchesGolden() throws Exception {
        TmdbApiFetcher fetcher = mock(TmdbApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        TmdbPayloadProcessor processor = new TmdbPayloadProcessor();
        when(fetcher.getTvShowDetails(eq(TV_ID), eq("ko-KR"))).thenReturn(loadFixture());

        TmdbTvSource source = new TmdbTvSource(fetcher, processor);
        new CrawlPipeline(collector).run(source, String.valueOf(TV_ID));

        GoldenFiles.assertMatchesGolden("tmdb-tv-1396.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
```

- [ ] **Step 2: Run to verify it fails to compile**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.contents.TMDB.source.Tmdb*GoldenTest"`
Expected: FAIL — `TmdbMovieSource` / `TmdbTvSource` do not exist.

- [ ] **Step 3: Implement `TmdbMovieSource`**

```java
package com.example.crawler.contents.TMDB.source;

import com.example.crawler.contents.TMDB.fetcher.TmdbApiFetcher;
import com.example.crawler.contents.TMDB.processor.TmdbPayloadProcessor;
import com.example.crawler.crawl.ContentSource;
import com.example.crawler.crawl.CrawlPayload;
import com.example.crawler.crawl.SourceDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TMDB movie ContentSource. Fetch delegates to TmdbApiFetcher.getMovieDetails (language ko-KR,
 * matching the legacy single-item path), parse delegates to TmdbPayloadProcessor.
 */
@Component
@RequiredArgsConstructor
public class TmdbMovieSource implements ContentSource<Map<String, Object>> {

    private static final String LANGUAGE = "ko-KR";

    private final TmdbApiFetcher tmdbApiFetcher;
    private final TmdbPayloadProcessor payloadProcessor;

    @Override
    public SourceDescriptor descriptor() {
        return SourceDescriptor.TMDB_MOVIE;
    }

    @Override
    public Map<String, Object> fetchDetail(String targetId) {
        return tmdbApiFetcher.getMovieDetails(Integer.parseInt(targetId), LANGUAGE);
    }

    @Override
    public CrawlPayload parse(String targetId, Map<String, Object> rawDetail) {
        if (rawDetail.isEmpty()) {
            return null; // no data → skip (matches legacy null/empty guard)
        }
        return new CrawlPayload(targetId, null, payloadProcessor.process(rawDetail));
    }
}
```

- [ ] **Step 4: Implement `TmdbTvSource`**

```java
package com.example.crawler.contents.TMDB.source;

import com.example.crawler.contents.TMDB.fetcher.TmdbApiFetcher;
import com.example.crawler.contents.TMDB.processor.TmdbPayloadProcessor;
import com.example.crawler.crawl.ContentSource;
import com.example.crawler.crawl.CrawlPayload;
import com.example.crawler.crawl.SourceDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TMDB TV ContentSource. Fetch delegates to TmdbApiFetcher.getTvShowDetails (language ko-KR,
 * matching the legacy single-item path), parse delegates to TmdbPayloadProcessor.
 */
@Component
@RequiredArgsConstructor
public class TmdbTvSource implements ContentSource<Map<String, Object>> {

    private static final String LANGUAGE = "ko-KR";

    private final TmdbApiFetcher tmdbApiFetcher;
    private final TmdbPayloadProcessor payloadProcessor;

    @Override
    public SourceDescriptor descriptor() {
        return SourceDescriptor.TMDB_TV;
    }

    @Override
    public Map<String, Object> fetchDetail(String targetId) {
        return tmdbApiFetcher.getTvShowDetails(Integer.parseInt(targetId), LANGUAGE);
    }

    @Override
    public CrawlPayload parse(String targetId, Map<String, Object> rawDetail) {
        if (rawDetail.isEmpty()) {
            return null; // no data → skip (matches legacy null/empty guard)
        }
        return new CrawlPayload(targetId, null, payloadProcessor.process(rawDetail));
    }
}
```

> Note: the legacy guard is `detailedData == null || detailedData.isEmpty()`. The pipeline already treats a null `fetchDetail` result as SKIPPED before calling `parse`, so `parse` only needs the `isEmpty()` check.

- [ ] **Step 5: Run both test classes — verify PASS**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.contents.TMDB.source.Tmdb*GoldenTest"`
Expected: PASS (4 tests: legacy + source for each of movie and TV, all against the same goldens).

- [ ] **Step 6: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents/TMDB/source/TmdbMovieSource.java" "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents/TMDB/source/TmdbTvSource.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/contents/TMDB/source/TmdbMovieGoldenTest.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/contents/TMDB/source/TmdbTvGoldenTest.java"
git commit -m "feat(crawler): add TmdbMovieSource + TmdbTvSource (match legacy goldens)"
```

---

## Task 3: Route executors through the pipeline and delete legacy single-item code

**Files:**
- Modify: `.../common/queue/executors/TmdbMovieExecutor.java`, `.../common/queue/executors/TmdbTvExecutor.java`
- Modify: `.../contents/TMDB/service/TmdbService.java`
- Modify: `TmdbMovieGoldenTest.java`, `TmdbTvGoldenTest.java`

- [ ] **Step 1: Rewire `TmdbMovieExecutor`** — replace the whole file:

```java
package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.TMDB.source.TmdbMovieSource;
import com.example.crawler.crawl.CrawlPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TMDB 영화 크롤링 Executor — delegates to the unified CrawlPipeline + TmdbMovieSource.
 * (Removed in P4 when the Consumer talks to ContentSourceRegistry directly.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbMovieExecutor implements JobExecutor {

    private final CrawlPipeline crawlPipeline;
    private final TmdbMovieSource tmdbMovieSource;

    @Override
    public JobType getJobType() {
        return JobType.TMDB_MOVIE;
    }

    @Override
    public boolean execute(String targetId) {
        return crawlPipeline.run(tmdbMovieSource, targetId).isSuccess();
    }

    @Override
    public long getAverageExecutionTime() {
        return 800; // API 기반, 평균 800ms
    }
}
```

- [ ] **Step 2: Rewire `TmdbTvExecutor`** — replace the whole file:

```java
package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.TMDB.source.TmdbTvSource;
import com.example.crawler.crawl.CrawlPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TMDB TV 크롤링 Executor — delegates to the unified CrawlPipeline + TmdbTvSource.
 * (Removed in P4 when the Consumer talks to ContentSourceRegistry directly.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbTvExecutor implements JobExecutor {

    private final CrawlPipeline crawlPipeline;
    private final TmdbTvSource tmdbTvSource;

    @Override
    public JobType getJobType() {
        return JobType.TMDB_TV;
    }

    @Override
    public boolean execute(String targetId) {
        return crawlPipeline.run(tmdbTvSource, targetId).isSuccess();
    }

    @Override
    public long getAverageExecutionTime() {
        return 800; // API 기반, 평균 800ms
    }
}
```

- [ ] **Step 3: Delete the legacy single-item methods from `TmdbService`**

In `TmdbService.java`, delete the entire `public boolean collectMovieById(String movieId) { ... }` method and the entire `public boolean collectTvShowById(String tvId) { ... }` method. Leave everything else (the bulk/discovery methods, `processMovieList`, `processTvShowList`, the `@Async` entry points) and the constructor/fields unchanged — they still use `tmdbApiFetcher`, `collectorService`, and `payloadProcessor`.

- [ ] **Step 4: Remove the obsolete legacy test methods**

In `TmdbMovieGoldenTest.java`, delete `legacyCollectMovieByIdMatchesGolden()` and remove the now-unused `TmdbService` import. Keep `tmdbMovieSourceMatchesGolden()`.
In `TmdbTvGoldenTest.java`, delete `legacyCollectTvShowByIdMatchesGolden()` and remove the now-unused `TmdbService` import. Keep `tmdbTvSourceMatchesGolden()`.

- [ ] **Step 5: Verify the full module compiles and all tests pass**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test`
Expected: BUILD SUCCESSFUL. `Tmdb*GoldenTest.*SourceMatchesGolden` pass against the committed goldens; all other tests (Steam, foundation, `contextLoads`) still pass. `ContentSourceRegistry` now also registers `TMDB_MOVIE` and `TMDB_TV`.

- [ ] **Step 6: Confirm no remaining references to the deleted methods**

Run: `git grep -n "collectMovieById\|collectTvShowById" -- "-AOD-All-of-Dopamine-crawler/src"`
Expected: no matches in `src` (docs may still mention them). If any remain, fix before committing.

- [ ] **Step 7: Commit**

```bash
git add -A -- "-AOD-All-of-Dopamine-crawler/src"
git commit -m "refactor(crawler): route TMDB movie/TV through CrawlPipeline+sources, delete legacy collect*ById"
```

---

## Self-Review

**1. Spec coverage:** §4 TMDB row — `TmdbMovieSource`+`TmdbTvSource` added, reusing `TmdbApiFetcher`/`TmdbPayloadProcessor`; single-item `collectMovieById`/`collectTvShowById` deleted; executors rewired (deleted in P4). §6 — goldens from current code, sources proven equal. §5 — single-item saveRaw byte-identical. Bulk discovery decomposition deferred to P3 (noted). ✅

**2. Placeholder scan:** No TBD/TODO. Fixtures, test code, source code, exact edits all specified. Golden contents verified against explicit expected values in Task 1 Step 6.

**3. Type consistency:** `TmdbMovieSource`/`TmdbTvSource` implement `ContentSource<Map<String,Object>>` with `descriptor()/fetchDetail(String)/parse(String,Map)`. `CrawlPipeline.run(source,id).isSuccess()` used in both executors. `TmdbApiFetcher.getMovieDetails(int,String)`/`getTvShowDetails(int,String)` and `TmdbPayloadProcessor.process(Map)` match the real classes. `SourceDescriptor.TMDB_MOVIE`/`TMDB_TV` provide the "TMDB_MOVIE"/"MOVIE" and "TMDB_TV"/"TV" literals + URLs, matching the goldens. `TmdbService` constructor `(TmdbApiFetcher, CollectorService, TmdbPayloadProcessor)` unchanged.

---

## Execution Handoff

Plan complete. Execute with subagent-driven-development: implement each task → spec review → code-quality review, sequentially on `refactor/crawler-unification`.
