# Crawler Unification — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the test safety-net (characterization harness) and the unified crawl framework types (`SourceDescriptor`, `CrawlPayload`, `CrawlResult`, `ContentSource`, `CrawlPipeline`, `ContentSourceRegistry`) — with zero behavior change — so per-platform crawlers can later be migrated onto a single uniform path.

**Architecture:** Composition + single runner + registry. Each platform will (in later plans) implement `ContentSource<D>` exposing only `descriptor / fetchDetail / parse`; the cross-cutting flow (validate → fetch → parse → saveRaw → log → result) lives once in `CrawlPipeline`. This plan adds those types **unwired** (no platform implements `ContentSource` yet), plus a golden-master characterization harness. Mirrors the existing `JobExecutorRegistry` auto-registration idiom.

**Tech Stack:** Java 17, Spring Boot 3.5.x, JUnit 5 + Mockito + AssertJ (all via `spring-boot-starter-test`, already on the classpath), Jackson (already present). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-06-17-crawler-unification-design.md` (§3 핵심 타입, §6 특성화 테스트).

---

## Conventions for every task

- **Run all commands from** `-AOD-All-of-Dopamine-back/` (the Gradle root). Bash tool: `./gradlew ...`. PowerShell: use `.\gradlew.bat` in place of `./gradlew`.
- The crawler Gradle test task is `:-AOD-All-of-Dopamine-crawler:test`. Target a single class with `--tests "<fqcn>"`.
- Tests in this plan are **plain unit tests** (no `@SpringBootTest`, no DB) so they run offline and fast.
- **Every commit message must end with this trailer** (shown once; include it on each commit):
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  ```
- Work on branch `refactor/crawler-unification` (already created and checked out).

---

## File Structure

**New production files** (all in new package `com.example.crawler.crawl`):

| File | Responsibility |
|---|---|
| `.../crawl/SourceDescriptor.java` | Enum: single source of truth for platformName, domain, JobType, usesSelenium, avg exec time, batch size, URL template |
| `.../crawl/CrawlPayload.java` | Record: parse output = `{platformSpecificId, url?, payload}` |
| `.../crawl/CrawlResult.java` | Record: `{status(SUCCESS/SKIPPED/FAILED), reason?, error?}` |
| `.../crawl/ContentSource.java` | Interface `<D>`: `descriptor / fetchDetail / parse / cleanup` |
| `.../crawl/CrawlPipeline.java` | `@Component`: the single runner (validate/fetch/parse/save/log/result) |
| `.../crawl/ContentSourceRegistry.java` | `@Component`: `JobType → ContentSource` map (auto-registered) |

Production path prefix: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/`

**New test files**:

| File | Responsibility |
|---|---|
| `.../crawl/support/CanonicalJson.java` | Test util: deterministic, key-sorted JSON for golden comparison |
| `.../crawl/support/SaveRawCapture.java` | Test util: capture `CollectorService.saveRaw(...)` args → canonical JSON |
| `.../crawl/support/GoldenFiles.java` | Test util: assert-against-golden (bootstrap: write-then-fail on first run) |
| `.../crawl/support/CanonicalJsonTest.java` | Verifies CanonicalJson determinism |
| `.../crawl/support/SaveRawCaptureTest.java` | Verifies capture util |
| `.../crawl/support/GoldenFilesTest.java` | Verifies golden compare/bootstrap |
| `.../crawl/SourceDescriptorTest.java` | Verifies descriptor constants |
| `.../crawl/CrawlResultTest.java` | Verifies result factories |
| `.../crawl/CrawlPipelineTest.java` | Verifies the runner flow (success/skip/fail/cleanup) |
| `.../crawl/ContentSourceRegistryTest.java` | Verifies registration/lookup/duplicate |

Test path prefix: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/`

**Target structure (later plans, for context — NOT built here):** per-platform `XxxSource`/`XxxFetcher`/`XxxParser`, `ContentEnumerator` (+ per-platform enumerators), `CrawlMetrics`, and the `CrawlJobConsumer` rewire that deletes the 6 `*Executor` classes. See "Next Plans" at the end.

---

## Task 1: `CanonicalJson` test util

Deterministic JSON serialization (keys sorted, fixed `\n` newlines) so golden comparisons are stable across runs and OSes. Newlines are forced to `\n` to avoid Windows CRLF flakiness.

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/CanonicalJson.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/CanonicalJsonTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.crawler.crawl.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonTest {

    @Test
    void sortsKeysAndUsesLfNewlines() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("b", 2);          // inserted out of order on purpose
        m.put("a", 1);

        String json = CanonicalJson.serialize(m);

        assertThat(json).isEqualTo("{\n  \"a\" : 1,\n  \"b\" : 2\n}");
    }

    @Test
    void isDeterministicRegardlessOfInsertionOrder() {
        Map<String, Object> m1 = new LinkedHashMap<>();
        m1.put("x", "1");
        m1.put("y", "2");
        Map<String, Object> m2 = new LinkedHashMap<>();
        m2.put("y", "2");
        m2.put("x", "1");

        assertThat(CanonicalJson.serialize(m1)).isEqualTo(CanonicalJson.serialize(m2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.support.CanonicalJsonTest"`
Expected: FAIL — compilation error, `CanonicalJson` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.crawler.crawl.support;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Deterministic JSON for golden-master comparison: keys sorted, newlines forced to '\n'.
 * Mirrors the intent of CollectorService.sha256Canonical but optimized for human-readable diffs.
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private CanonicalJson() {
    }

    public static String serialize(Object value) {
        try {
            DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            printer.indentObjectsWith(indenter);
            printer.indentArraysWith(indenter);
            return MAPPER.writer(printer).writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("CanonicalJson serialize failed", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.support.CanonicalJsonTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/CanonicalJson.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/CanonicalJsonTest.java
git commit -m "test(crawler): add CanonicalJson util for golden-master comparison"
```

---

## Task 2: `SaveRawCapture` test util

Captures the 5 arguments passed to `CollectorService.saveRaw(...)` from a Mockito mock and renders them as canonical JSON. This is how every per-platform characterization test will snapshot "what got saved".

**`CollectorService.saveRaw` signature** (from `com.example.crawler.ingest.CollectorService`):
`Long saveRaw(String platformName, String domain, Map<String,Object> payload, String platformSpecificId, String url)`

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/SaveRawCapture.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/SaveRawCaptureTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.crawler.crawl.support;

import com.example.crawler.ingest.CollectorService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SaveRawCaptureTest {

    @Test
    void capturesAllFiveArgumentsAndRendersCanonicalJson() {
        CollectorService collector = mock(CollectorService.class);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Portal");
        collector.saveRaw("Steam", "GAME", payload, "400", "https://store.steampowered.com/app/400");

        SaveRawCapture capture = SaveRawCapture.from(collector);

        assertThat(capture.platformName).isEqualTo("Steam");
        assertThat(capture.domain).isEqualTo("GAME");
        assertThat(capture.platformSpecificId).isEqualTo("400");
        assertThat(capture.url).isEqualTo("https://store.steampowered.com/app/400");
        assertThat(capture.payload).containsEntry("title", "Portal");
        assertThat(capture.toCanonicalJson())
                .contains("\"platformName\" : \"Steam\"")
                .contains("\"title\" : \"Portal\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.support.SaveRawCaptureTest"`
Expected: FAIL — compilation error, `SaveRawCapture` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.crawler.crawl.support;

import com.example.crawler.ingest.CollectorService;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;

/**
 * Captures the arguments of a single CollectorService.saveRaw(...) call made on a Mockito mock,
 * and renders them as canonical JSON for golden-master assertions.
 */
public final class SaveRawCapture {

    public final String platformName;
    public final String domain;
    public final Map<String, Object> payload;
    public final String platformSpecificId;
    public final String url;

    private SaveRawCapture(String platformName, String domain, Map<String, Object> payload,
                           String platformSpecificId, String url) {
        this.platformName = platformName;
        this.domain = domain;
        this.payload = payload;
        this.platformSpecificId = platformSpecificId;
        this.url = url;
    }

    @SuppressWarnings("unchecked")
    public static SaveRawCapture from(CollectorService mockCollector) {
        ArgumentCaptor<String> platform = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> domain = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> psid = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);

        verify(mockCollector).saveRaw(
                platform.capture(), domain.capture(), payload.capture(), psid.capture(), url.capture());

        return new SaveRawCapture(
                platform.getValue(), domain.getValue(),
                (Map<String, Object>) payload.getValue(),
                psid.getValue(), url.getValue());
    }

    public String toCanonicalJson() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("platformName", platformName);
        record.put("domain", domain);
        record.put("platformSpecificId", platformSpecificId);
        record.put("url", url);
        record.put("payload", payload);
        return CanonicalJson.serialize(record);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.support.SaveRawCaptureTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/SaveRawCapture.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/SaveRawCaptureTest.java
git commit -m "test(crawler): add SaveRawCapture util to snapshot saveRaw args"
```

---

## Task 3: `GoldenFiles` test util

Asserts an actual canonical JSON string equals a stored golden file. On first run (golden missing) it writes the file and fails, forcing a human to review and commit the baseline. Normalizes CRLF→LF so git autocrlf cannot cause false mismatches.

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/GoldenFiles.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/GoldenFilesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.crawler.crawl.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldenFilesTest {

    @Test
    void bootstrapsWhenMissingThenFails(@TempDir Path dir) {
        assertThatThrownBy(() -> GoldenFiles.assertMatches(dir, "sample.json", "{\n  \"a\" : 1\n}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Golden created");

        assertThat(Files.exists(dir.resolve("sample.json"))).isTrue();
    }

    @Test
    void passesWhenContentMatches(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("sample.json"), "{\n  \"a\" : 1\n}");

        GoldenFiles.assertMatches(dir, "sample.json", "{\n  \"a\" : 1\n}");
    }

    @Test
    void ignoresCrlfDifferences(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("sample.json"), "{\r\n  \"a\" : 1\r\n}");

        GoldenFiles.assertMatches(dir, "sample.json", "{\n  \"a\" : 1\n}");
    }

    @Test
    void failsWhenContentDiffers(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("sample.json"), "{\n  \"a\" : 1\n}");

        assertThatThrownBy(() -> GoldenFiles.assertMatches(dir, "sample.json", "{\n  \"a\" : 2\n}"))
                .isInstanceOf(AssertionError.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.support.GoldenFilesTest"`
Expected: FAIL — compilation error, `GoldenFiles` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.crawler.crawl.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Golden-master file assertions.
 *
 * Default golden directory: src/test/resources/golden (resolved relative to the module working
 * directory, which is the Gradle project dir when run via :-AOD-All-of-Dopamine-crawler:test).
 *
 * Bootstrap: if the golden file does not exist, it is written and the test fails so a human
 * reviews and commits the baseline. CRLF is normalized to LF before comparison.
 */
public final class GoldenFiles {

    private static final Path DEFAULT_DIR = Paths.get("src", "test", "resources", "golden");

    private GoldenFiles() {
    }

    public static void assertMatchesGolden(String name, String actualCanonicalJson) {
        assertMatches(DEFAULT_DIR, name, actualCanonicalJson);
    }

    public static void assertMatches(Path dir, String name, String actualCanonicalJson) {
        Path file = dir.resolve(name);
        String actual = normalize(actualCanonicalJson);
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(dir);
                Files.writeString(file, actual);
                fail("Golden created at " + file.toAbsolutePath()
                        + " — review it and re-run the test.");
            }
            String expected = normalize(Files.readString(file));
            assertThat(actual).as("golden %s", file).isEqualTo(expected);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("GoldenFiles failed for " + file, e);
        }
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.support.GoldenFilesTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/GoldenFiles.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/support/GoldenFilesTest.java
git commit -m "test(crawler): add GoldenFiles util for characterization baselines"
```

---

## Task 4: `SourceDescriptor` enum

Single source of truth for per-source metadata. Replaces scattered `"Steam"`/`"GAME"`/URL literals and the JobType↔platform coupling.

> **Verify during webtoon migration (later plan):** `NAVER_WEBTOON`'s `platformName`/`domain`/URL below are best-effort and MUST be confirmed against the actual `NaverWebtoonCrawler.saveRaw(...)` call when that platform is characterized. The webtoon characterization test will catch any mismatch. The other four (`STEAM_GAME`, `TMDB_MOVIE`, `TMDB_TV`, `NAVER_SERIES`) are confirmed from current code.

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/SourceDescriptor.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/SourceDescriptorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.crawler.crawl;

import com.example.crawler.common.queue.JobType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceDescriptorTest {

    @Test
    void steamGameMetadataMatchesCurrentLiterals() {
        SourceDescriptor d = SourceDescriptor.STEAM_GAME;
        assertThat(d.platformName()).isEqualTo("Steam");
        assertThat(d.domain()).isEqualTo("GAME");
        assertThat(d.jobType()).isEqualTo(JobType.STEAM_GAME);
        assertThat(d.usesSelenium()).isFalse();
        assertThat(d.recommendedBatchSize()).isEqualTo(5);
        assertThat(d.urlOf("400")).isEqualTo("https://store.steampowered.com/app/400");
    }

    @Test
    void tmdbMovieAndTvMatchCurrentLiterals() {
        assertThat(SourceDescriptor.TMDB_MOVIE.platformName()).isEqualTo("TMDB_MOVIE");
        assertThat(SourceDescriptor.TMDB_MOVIE.domain()).isEqualTo("MOVIE");
        assertThat(SourceDescriptor.TMDB_MOVIE.urlOf("27205"))
                .isEqualTo("https://www.themoviedb.org/movie/27205");
        assertThat(SourceDescriptor.TMDB_TV.platformName()).isEqualTo("TMDB_TV");
        assertThat(SourceDescriptor.TMDB_TV.domain()).isEqualTo("TV");
        assertThat(SourceDescriptor.TMDB_TV.urlOf("1396"))
                .isEqualTo("https://www.themoviedb.org/tv/1396");
    }

    @Test
    void naverSeriesMatchesCurrentLiterals() {
        SourceDescriptor d = SourceDescriptor.NAVER_SERIES;
        assertThat(d.platformName()).isEqualTo("NaverSeries");
        assertThat(d.domain()).isEqualTo("WEBNOVEL");
        assertThat(d.jobType()).isEqualTo(JobType.NAVER_SERIES_NOVEL);
        assertThat(d.recommendedBatchSize()).isEqualTo(3);
        assertThat(d.urlOf("123"))
                .isEqualTo("https://series.naver.com/novel/detail.series?productNo=123");
    }

    @Test
    void naverWebtoonUsesSeleniumWithBatchSizeOne() {
        SourceDescriptor d = SourceDescriptor.NAVER_WEBTOON;
        assertThat(d.usesSelenium()).isTrue();
        assertThat(d.recommendedBatchSize()).isEqualTo(1);
        assertThat(d.jobType()).isEqualTo(JobType.NAVER_WEBTOON);
    }

    @Test
    void forJobTypeResolvesDescriptor() {
        assertThat(SourceDescriptor.forJobType(JobType.TMDB_TV)).isEqualTo(SourceDescriptor.TMDB_TV);
    }

    @Test
    void forJobTypeThrowsForUnmapped() {
        assertThatThrownBy(() -> SourceDescriptor.forJobType(JobType.KAKAO_PAGE_NOVEL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.SourceDescriptorTest"`
Expected: FAIL — compilation error, `SourceDescriptor` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.crawler.crawl;

import com.example.crawler.common.queue.JobType;

import java.util.function.Function;

/**
 * Single source of truth for per-source crawl metadata.
 * Replaces scattered platform/domain/url literals and the JobType coupling that used to live
 * inside each crawl service and executor.
 *
 * Batch sizes preserve the values previously hard-coded per executor:
 *   Steam=5, TMDB(movie/tv)=6, NaverWebtoon=1, NaverSeries=3.
 */
public enum SourceDescriptor {

    STEAM_GAME("Steam", "GAME", JobType.STEAM_GAME, false, 1000L, 5,
            id -> "https://store.steampowered.com/app/" + id),

    TMDB_MOVIE("TMDB_MOVIE", "MOVIE", JobType.TMDB_MOVIE, false, 800L, 6,
            id -> "https://www.themoviedb.org/movie/" + id),

    TMDB_TV("TMDB_TV", "TV", JobType.TMDB_TV, false, 800L, 6,
            id -> "https://www.themoviedb.org/tv/" + id),

    // NOTE: confirm platformName/domain/url against NaverWebtoonCrawler during webtoon migration.
    NAVER_WEBTOON("NaverWebtoon", "WEBTOON", JobType.NAVER_WEBTOON, true, 5000L, 1,
            id -> "https://comic.naver.com/webtoon/list?titleId=" + id),

    NAVER_SERIES("NaverSeries", "WEBNOVEL", JobType.NAVER_SERIES_NOVEL, false, 2000L, 3,
            id -> "https://series.naver.com/novel/detail.series?productNo=" + id);

    private final String platformName;
    private final String domain;
    private final JobType jobType;
    private final boolean usesSelenium;
    private final long avgExecMillis;
    private final int recommendedBatchSize;
    private final Function<String, String> urlFn;

    SourceDescriptor(String platformName, String domain, JobType jobType, boolean usesSelenium,
                     long avgExecMillis, int recommendedBatchSize, Function<String, String> urlFn) {
        this.platformName = platformName;
        this.domain = domain;
        this.jobType = jobType;
        this.usesSelenium = usesSelenium;
        this.avgExecMillis = avgExecMillis;
        this.recommendedBatchSize = recommendedBatchSize;
        this.urlFn = urlFn;
    }

    public String platformName() {
        return platformName;
    }

    public String domain() {
        return domain;
    }

    public JobType jobType() {
        return jobType;
    }

    public boolean usesSelenium() {
        return usesSelenium;
    }

    public long avgExecMillis() {
        return avgExecMillis;
    }

    public int recommendedBatchSize() {
        return recommendedBatchSize;
    }

    public String urlOf(String platformSpecificId) {
        return urlFn.apply(platformSpecificId);
    }

    public static SourceDescriptor forJobType(JobType jobType) {
        for (SourceDescriptor d : values()) {
            if (d.jobType == jobType) {
                return d;
            }
        }
        throw new IllegalArgumentException("No SourceDescriptor for jobType: " + jobType);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.SourceDescriptorTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/SourceDescriptor.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/SourceDescriptorTest.java
git commit -m "feat(crawler): add SourceDescriptor enum (single source of truth for crawl metadata)"
```

---

## Task 5: `CrawlPayload` record

The output of `ContentSource.parse(...)`: the three things `saveRaw` needs. `url` is optional — when null the pipeline falls back to `descriptor.urlOf(platformSpecificId)`.

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/CrawlPayload.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/CrawlPayloadTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.crawler.crawl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlPayloadTest {

    @Test
    void ofBuildsPayloadWithNullUrl() {
        CrawlPayload p = CrawlPayload.of("400", Map.of("title", "Portal"));
        assertThat(p.platformSpecificId()).isEqualTo("400");
        assertThat(p.url()).isNull();
        assertThat(p.payload()).containsEntry("title", "Portal");
    }

    @Test
    void rejectsBlankPlatformSpecificId() {
        assertThatThrownBy(() -> new CrawlPayload("  ", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullPayload() {
        assertThatThrownBy(() -> new CrawlPayload("400", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.CrawlPayloadTest"`
Expected: FAIL — compilation error, `CrawlPayload` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.crawler.crawl;

import java.util.Map;

/**
 * The output of ContentSource.parse(...): everything CollectorService.saveRaw needs.
 * url may be null — the pipeline then uses SourceDescriptor.urlOf(platformSpecificId).
 */
public record CrawlPayload(String platformSpecificId, String url, Map<String, Object> payload) {

    public CrawlPayload {
        if (platformSpecificId == null || platformSpecificId.isBlank()) {
            throw new IllegalArgumentException("platformSpecificId is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    /** Convenience: url derived from the descriptor at pipeline time. */
    public static CrawlPayload of(String platformSpecificId, Map<String, Object> payload) {
        return new CrawlPayload(platformSpecificId, null, payload);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.CrawlPayloadTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/CrawlPayload.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/CrawlPayloadTest.java
git commit -m "feat(crawler): add CrawlPayload record (parse output)"
```

---

## Task 6: `CrawlResult` record

The pipeline's outcome. `SUCCESS`/`SKIPPED`/`FAILED`. Later, the Consumer maps SUCCESS→COMPLETED, FAILED→FAILED(retry), SKIPPED→COMPLETED(no retry).

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/CrawlResult.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/CrawlResultTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.crawler.crawl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlResultTest {

    @Test
    void successHasNoReasonOrError() {
        CrawlResult r = CrawlResult.success();
        assertThat(r.status()).isEqualTo(CrawlResult.Status.SUCCESS);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.reason()).isNull();
        assertThat(r.error()).isNull();
    }

    @Test
    void skippedCarriesReason() {
        CrawlResult r = CrawlResult.skipped("filtered");
        assertThat(r.status()).isEqualTo(CrawlResult.Status.SKIPPED);
        assertThat(r.isSkipped()).isTrue();
        assertThat(r.reason()).isEqualTo("filtered");
    }

    @Test
    void failedCarriesErrorAndItsMessage() {
        RuntimeException boom = new RuntimeException("boom");
        CrawlResult r = CrawlResult.failed(boom);
        assertThat(r.status()).isEqualTo(CrawlResult.Status.FAILED);
        assertThat(r.isFailed()).isTrue();
        assertThat(r.error()).isSameAs(boom);
        assertThat(r.reason()).isEqualTo("boom");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.CrawlResultTest"`
Expected: FAIL — compilation error, `CrawlResult` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.crawler.crawl;

/**
 * Outcome of a single CrawlPipeline.run(...).
 * Consumer mapping (later plan): SUCCESS -> COMPLETED, FAILED -> FAILED (retry),
 * SKIPPED -> COMPLETED (no retry — e.g. adult/non-game content).
 */
public record CrawlResult(Status status, String reason, Throwable error) {

    public enum Status {
        SUCCESS, SKIPPED, FAILED
    }

    public static CrawlResult success() {
        return new CrawlResult(Status.SUCCESS, null, null);
    }

    public static CrawlResult skipped(String reason) {
        return new CrawlResult(Status.SKIPPED, reason, null);
    }

    public static CrawlResult failed(Throwable error) {
        return new CrawlResult(Status.FAILED, error == null ? null : error.getMessage(), error);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.CrawlResultTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/CrawlResult.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/CrawlResultTest.java
git commit -m "feat(crawler): add CrawlResult record"
```

---

## Task 7: `ContentSource` interface

The uniform per-platform contract. `D` is the platform's private raw-detail type (Map for HTTP, Document for Jsoup, a DTO for Selenium) — it flows only from `fetchDetail` into that same source's `parse`, so the pipeline never needs to know it.

This task has no standalone test (it is exercised by `CrawlPipelineTest` in Task 8). It is a one-file interface definition.

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/ContentSource.java`

- [ ] **Step 1: Write the interface**

```java
package com.example.crawler.crawl;

/**
 * Uniform per-platform crawl contract. One implementation per platform×domain
 * (e.g. SteamGameSource, TmdbMovieSource, NaverSeriesNovelSource).
 *
 * @param <D> the platform-private raw detail type returned by fetchDetail and consumed by parse
 *            (e.g. Map for HTTP JSON, org.jsoup.nodes.Document for Jsoup, a DTO for Selenium).
 */
public interface ContentSource<D> {

    /** Metadata: platform, domain, jobType, url template, tuning. */
    SourceDescriptor descriptor();

    /** Fetch raw detail for one target id (HTTP / Jsoup / Selenium). May return null if unavailable. */
    D fetchDetail(String targetId);

    /** Convert raw detail into a CrawlPayload. Return null to SKIP (e.g. adult content, wrong type). */
    CrawlPayload parse(String targetId, D rawDetail);

    /** Optional resource cleanup (e.g. Selenium ThreadLocal driver). Called in the pipeline's finally. */
    default void cleanup() {
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/ContentSource.java
git commit -m "feat(crawler): add ContentSource interface (uniform per-platform contract)"
```

---

## Task 8: `CrawlPipeline` (the single runner)

All cross-cutting flow in one place: validate → fetch → parse → saveRaw → uniform logging → `CrawlResult`, with `cleanup()` always run. No metrics yet (metrics consolidation is a later plan — `CrawlMetrics`).

**`CollectorService.saveRaw` returns `Long`** (ignored here).

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/CrawlPipeline.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/CrawlPipelineTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.crawler.crawl;

import com.example.crawler.ingest.CollectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CrawlPipelineTest {

    private CollectorService collector;
    private CrawlPipeline pipeline;
    private FakeSource source;

    /** Configurable fake covering every branch of the pipeline. */
    static final class FakeSource implements ContentSource<String> {
        SourceDescriptor descriptor = SourceDescriptor.STEAM_GAME;
        String rawToReturn = "RAW";
        boolean throwOnFetch = false;
        CrawlPayload parseResult;
        boolean fetchCalled = false;
        boolean cleanupCalled = false;

        FakeSource() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", "Portal");
            this.parseResult = new CrawlPayload("400", null, payload);
        }

        public SourceDescriptor descriptor() {
            return descriptor;
        }

        public String fetchDetail(String targetId) {
            fetchCalled = true;
            if (throwOnFetch) {
                throw new RuntimeException("boom");
            }
            return rawToReturn;
        }

        public CrawlPayload parse(String targetId, String rawDetail) {
            return parseResult;
        }

        public void cleanup() {
            cleanupCalled = true;
        }
    }

    @BeforeEach
    void setUp() {
        collector = mock(CollectorService.class);
        pipeline = new CrawlPipeline(collector);
        source = new FakeSource();
    }

    @Test
    void successSavesRawWithDescriptorMetadataAndDerivedUrl() {
        CrawlResult result = pipeline.run(source, "400");

        assertThat(result.isSuccess()).isTrue();
        verify(collector).saveRaw(
                eq("Steam"), eq("GAME"), eq(source.parseResult.payload()),
                eq("400"), eq("https://store.steampowered.com/app/400"));
        assertThat(source.cleanupCalled).isTrue();
    }

    @Test
    void usesExplicitUrlWhenPayloadProvidesOne() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "X");
        source.parseResult = new CrawlPayload("400", "https://explicit/url", payload);

        pipeline.run(source, "400");

        verify(collector).saveRaw(any(), any(), any(), eq("400"), eq("https://explicit/url"));
    }

    @Test
    void blankIdSkipsWithoutFetching() {
        CrawlResult result = pipeline.run(source, "   ");

        assertThat(result.isSkipped()).isTrue();
        assertThat(source.fetchCalled).isFalse();
        verify(collector, never()).saveRaw(any(), any(), any(), any(), any());
    }

    @Test
    void nullDetailSkips() {
        source.rawToReturn = null;

        CrawlResult result = pipeline.run(source, "400");

        assertThat(result.isSkipped()).isTrue();
        verify(collector, never()).saveRaw(any(), any(), any(), any(), any());
    }

    @Test
    void nullParseSkips() {
        source.parseResult = null;

        CrawlResult result = pipeline.run(source, "400");

        assertThat(result.isSkipped()).isTrue();
        verify(collector, never()).saveRaw(any(), any(), any(), any(), any());
        assertThat(source.cleanupCalled).isTrue();
    }

    @Test
    void fetchExceptionFailsAndStillCleansUp() {
        source.throwOnFetch = true;

        CrawlResult result = pipeline.run(source, "400");

        assertThat(result.isFailed()).isTrue();
        assertThat(result.error()).isInstanceOf(RuntimeException.class);
        assertThat(source.cleanupCalled).isTrue();
        verify(collector, never()).saveRaw(any(), any(), any(), any(), any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.CrawlPipelineTest"`
Expected: FAIL — compilation error, `CrawlPipeline` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.crawler.crawl;

import com.example.crawler.ingest.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The single crawl runner. All cross-cutting concerns (validation, logging, saving, exception
 * handling, cleanup) live here exactly once, so each ContentSource only implements fetch + parse.
 *
 * Metrics are intentionally NOT here yet — they are added when CrawlMetrics is introduced in a
 * later plan (consumer rewire / metrics consolidation).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlPipeline {

    private final CollectorService collector;

    public <D> CrawlResult run(ContentSource<D> source, String targetId) {
        SourceDescriptor d = source.descriptor();
        try {
            if (targetId == null || targetId.isBlank()) {
                log.warn("[{}/{}] blank targetId — skip", d.platformName(), d.domain());
                return CrawlResult.skipped("blank id");
            }

            D raw = source.fetchDetail(targetId);
            if (raw == null) {
                log.info("[{}/{}] id={} no detail — skip", d.platformName(), d.domain(), targetId);
                return CrawlResult.skipped("no detail");
            }

            CrawlPayload p = source.parse(targetId, raw);
            if (p == null) {
                log.info("[{}/{}] id={} filtered — skip", d.platformName(), d.domain(), targetId);
                return CrawlResult.skipped("filtered");
            }

            String url = (p.url() != null && !p.url().isBlank())
                    ? p.url()
                    : d.urlOf(p.platformSpecificId());

            collector.saveRaw(d.platformName(), d.domain(), p.payload(), p.platformSpecificId(), url);
            log.debug("[{}/{}] id={} saved", d.platformName(), d.domain(), targetId);
            return CrawlResult.success();

        } catch (Exception e) {
            log.error("[{}/{}] id={} crawl failed: {}",
                    d.platformName(), d.domain(), targetId, e.getMessage(), e);
            return CrawlResult.failed(e);
        } finally {
            try {
                source.cleanup();
            } catch (Exception ce) {
                log.warn("[{}/{}] cleanup failed: {}", d.platformName(), d.domain(), ce.getMessage());
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.CrawlPipelineTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/CrawlPipeline.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/CrawlPipelineTest.java
git commit -m "feat(crawler): add CrawlPipeline single runner for fetch/parse/save"
```

---

## Task 9: `ContentSourceRegistry`

Maps `JobType → ContentSource`, auto-populated from all `ContentSource` beans. Mirrors `JobExecutorRegistry`. In this plan there are zero `ContentSource` beans yet, so the Spring-built registry is empty — that is fine; the unit test constructs it directly with fakes.

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/ContentSourceRegistry.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/ContentSourceRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.crawler.crawl;

import com.example.crawler.common.queue.JobType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentSourceRegistryTest {

    /** Minimal fake whose only meaningful behavior is reporting its descriptor. */
    static final class StubSource implements ContentSource<String> {
        private final SourceDescriptor descriptor;

        StubSource(SourceDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        public SourceDescriptor descriptor() {
            return descriptor;
        }

        public String fetchDetail(String targetId) {
            return null;
        }

        public CrawlPayload parse(String targetId, String rawDetail) {
            return null;
        }
    }

    @Test
    void resolvesSourceByJobType() {
        StubSource steam = new StubSource(SourceDescriptor.STEAM_GAME);
        StubSource tmdb = new StubSource(SourceDescriptor.TMDB_MOVIE);
        ContentSourceRegistry registry = new ContentSourceRegistry(List.of(steam, tmdb));

        assertThat(registry.get(JobType.STEAM_GAME)).isSameAs(steam);
        assertThat(registry.get(JobType.TMDB_MOVIE)).isSameAs(tmdb);
    }

    @Test
    void throwsOnDuplicateJobType() {
        StubSource a = new StubSource(SourceDescriptor.STEAM_GAME);
        StubSource b = new StubSource(SourceDescriptor.STEAM_GAME);

        assertThatThrownBy(() -> new ContentSourceRegistry(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsOnUnknownJobType() {
        ContentSourceRegistry registry = new ContentSourceRegistry(List.of());

        assertThatThrownBy(() -> registry.get(JobType.STEAM_GAME))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.ContentSourceRegistryTest"`
Expected: FAIL — compilation error, `ContentSourceRegistry` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.crawler.crawl;

import com.example.crawler.common.queue.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * JobType -> ContentSource registry, auto-populated from all ContentSource beans.
 * Mirrors JobExecutorRegistry. Adding a platform = add one ContentSource bean; no change here.
 */
@Slf4j
@Component
public class ContentSourceRegistry {

    private final Map<JobType, ContentSource<?>> byJobType = new EnumMap<>(JobType.class);

    public ContentSourceRegistry(List<ContentSource<?>> sources) {
        for (ContentSource<?> source : sources) {
            JobType jobType = source.descriptor().jobType();
            ContentSource<?> previous = byJobType.put(jobType, source);
            if (previous != null) {
                throw new IllegalStateException("Duplicate ContentSource for " + jobType + ": "
                        + previous.getClass().getSimpleName() + " vs " + source.getClass().getSimpleName());
            }
            log.info("📌 [Registry] ContentSource 등록: {} -> {}",
                    jobType, source.getClass().getSimpleName());
        }
    }

    public ContentSource<?> get(JobType jobType) {
        ContentSource<?> source = byJobType.get(jobType);
        if (source == null) {
            throw new IllegalArgumentException("No ContentSource for jobType: " + jobType);
        }
        return source;
    }

    public Map<JobType, ContentSource<?>> all() {
        return new EnumMap<>(byJobType);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.crawl.ContentSourceRegistryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/crawl/ContentSourceRegistry.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/crawl/ContentSourceRegistryTest.java
git commit -m "feat(crawler): add ContentSourceRegistry (jobType -> source, auto-registered)"
```

---

## Task 10: Full module verification

Confirm the whole crawler module still compiles and all tests (new + existing `contextLoads`) pass together. The new types are unwired (no `ContentSource` beans), so the Spring context is unchanged — `CrawlerApplicationTests.contextLoads` must still pass.

- [ ] **Step 1: Run the full module test suite**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test`
Expected: BUILD SUCCESSFUL. All `com.example.crawler.crawl.*` tests pass; `CrawlerApplicationTests.contextLoads` passes (the empty `ContentSourceRegistry` bean wires with an empty list).

- [ ] **Step 2: If green, tag the foundation as complete**

```bash
git commit --allow-empty -m "chore(crawler): crawl unification foundation complete (harness + core types)"
```

---

## Self-Review

**1. Spec coverage (against `2026-06-17-crawler-unification-design.md`):**
- §3.3 핵심 타입 — `SourceDescriptor` (T4), `CrawlPayload` (T5), `CrawlResult` (T6), `ContentSource` (T7), `CrawlPipeline` (T8), `ContentSourceRegistry` (T9). ✅
- §6 특성화 테스트 하네스 — `CanonicalJson` (T1), `SaveRawCapture` (T2), `GoldenFiles` (T3). ✅ (Per-platform golden baselines are produced at the start of each platform's migration plan — see Next Plans. This is the spec's P0 realized as "characterize-then-migrate per platform".)
- `ContentEnumerator` (§3.3 (4)) — **deferred to the P3 plan** on purpose: its parameter shape depends on each platform's enumeration (date range / weekday / page), best designed against real code. Noted, not a gap.
- §4 per-platform migration, §5 behavior changes, §7 P2–P5 — **out of scope for this foundation plan**, covered by Next Plans.

**2. Placeholder scan:** No TBD/TODO in code steps; every step has complete code and an exact command. The one forward-reference note (webtoon descriptor literals) is an explicit verification instruction, not a placeholder.

**3. Type consistency:** Method names/signatures are consistent across tasks — `descriptor()`, `fetchDetail(String)`, `parse(String, D)`, `cleanup()`, `CrawlPipeline.run(ContentSource<D>, String)`, `CrawlResult.success()/skipped(String)/failed(Throwable)`, `SourceDescriptor.platformName()/domain()/jobType()/usesSelenium()/recommendedBatchSize()/urlOf(String)/forJobType(JobType)`, `ContentSourceRegistry.get(JobType)/all()`. `CollectorService.saveRaw(String,String,Map,String,String)` matches the real signature.

---

## Next Plans (roadmap — each written just-in-time against real code)

This foundation produces a complete, mergeable, behavior-neutral unit. Subsequent plans (one per file, written when started so the code is grounded in the actual platform internals):

1. **P2a — Steam migration:** characterize `SteamCrawlService.collectGameByAppId` (golden), add `SteamGameSource` (delegates to existing `SteamApiFetcher`/`SteamPayloadProcessor`, `parse` returns null when `type != "game"`), prove golden, delete the single+bulk save loops.
2. **P2b — TMDB migration:** `TmdbMovieSource` + `TmdbTvSource`; extract discovery into a producer-side helper; delete `processMovieList`/`processTvShowList` inline save loops.
3. **P2c — NaverWebtoon migration:** confirm `SourceDescriptor.NAVER_WEBTOON` literals; `NaverWebtoonSource` with `cleanup()` = ThreadLocal driver cleanup.
4. **P2d — NaverSeries migration (largest):** split the 600-line `NaverSeriesCrawler` into `NaverSeriesNovelFetcher` (Jsoup) + `NaverSeriesNovelParser` (selectors, pure) + `NaverSeriesNovelSource`; remove `crawlToRaw`/`collectNovelById` duplication; replace `System.out.println` with slf4j.
5. **P2e — KakaoPage:** conform structurally (`KakaoPageNovelSource`), leave unwired.
6. **P3 — Enumerators + scheduler flip:** add `ContentEnumerator` + per-platform enumerators; schedulers reduced to enumerate→`CrawlJobProducer.createJobs`; bulk becomes async via the queue.
7. **P4 — Consumer rewire + metrics + cleanup:** rewire `CrawlJobConsumer` to `ContentSourceRegistry` + `CrawlPipeline` (batch size / avg time / selenium flag from descriptor, fixing the `NAVER_WEBTOON_FINISHED` cap bug); add `CrawlMetrics` into the pipeline; map `CrawlResult` → job status (SKIPPED→COMPLETED); delete the 6 `*Executor` classes.
8. **P5 — Final cleanup:** dead code, doc updates (`docs/2_JOB_QUEUE_ARCHITECTURE.md`).

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-17-crawler-unification-foundation.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
