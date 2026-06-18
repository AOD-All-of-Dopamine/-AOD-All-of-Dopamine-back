# Crawler Unification — P2-NaverSeries Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose the ~600-line `NaverSeriesCrawler` (which does Jsoup fetch + parsing + payload build inline, duplicated between `collectNovelById` and `crawlToRaw`) into `NaverSeriesNovelFetcher` (Jsoup IO), `NaverSeriesNovelParser` (pure Document→payload), and `NaverSeriesNovelSource` (`ContentSource`). Route the queue executor and the admin bulk loop through the shared parser, delete the legacy single-item method and the duplicated extraction — proven behavior-identical by a golden-master test.

**Architecture:** `NaverSeriesNovelSource implements ContentSource<NaverSeriesDetail>` where `NaverSeriesDetail` bundles the detail `Document` + the first-episode date (which needs a second fetch). `fetchDetail` delegates to `NaverSeriesNovelFetcher`; `parse` (pure, no network) delegates to `NaverSeriesNovelParser`. The admin `crawlToRaw` keeps its list-scraping but reuses the same fetcher+parser (cookie preserved via a fetcher overload), removing the duplicate. The shared static helpers `cleanTitle`/`extractQueryParam` move to the parser and the two ranking callers are updated.

**Tech Stack:** Java 17, Spring Boot 3.5.x, JUnit 5 + Mockito + AssertJ, Jsoup 1.16, Jackson. Builds on Foundation + P2-Steam + P2-TMDB.

**Spec:** `docs/superpowers/specs/2026-06-17-crawler-unification-design.md` (§4 NaverSeries row — "가장 큰 정리", §5, §6).
**Prereq:** Foundation + P2-Steam + P2-TMDB complete on `refactor/crawler-unification`.

---

## Conventions

- Run from `-AOD-All-of-Dopamine-back/`. Bash: `./gradlew ...`.
- Crawler test task: `:-AOD-All-of-Dopamine-crawler:test`.
- `git add` paths start with `-` → always use `git add -- "<path>"`.
- Commit trailer (every commit):
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  ```

---

## Current behavior being preserved (read `NaverSeriesCrawler.java` fully first)

`collectNovelById(String productId)` (queue path; only caller = `NaverSeriesNovelExecutor`):
1. `detailUrl = "https://series.naver.com/novel/detail.series?productNo=" + productId`; `doc = get(detailUrl, null)`.
2. Adult check (`#adult_msg` present, or `input[name=enctp]` value `"19"`) → return false (no save).
3. Extract: `productUrl` (og:url, fallback detailUrl), `title` (cleanTitle of og:title, fallback `h2`) → if blank return false; `imageUrl` (og:image), `rating` (`div.score_area`), `downloadCount` (`a.btn_download>span`), `commentCount`, `episodeCount`, `status`/`author`/`publisher`/`ageRating`/`genres` (from `ul.end_info li.info_lst > ul`), `synopsis` (`div.end_dsc ._synopsis`), `titleId` (`extractQueryParam(productUrl,"productNo")`), `firstDate` (`extractFirstEpisodeDate(titleId, null)`, null on failure).
4. Build a `LinkedHashMap` payload with keys in this order: `title, author, publisher, status, ageRating, synopsis, imageUrl, productUrl, titleId, genres, rating, downloadCount, commentCount, episodeCount, firstDate` (string values wrapped with `nz`).
5. `collector.saveRaw("NaverSeries", "WEBNOVEL", payload, titleId, productUrl)`; return true. (exception → false)

`crawlToRaw(baseListUrl, cookieString, maxPages)` (admin path; only caller = `AdminTestController`) loops list pages, scrapes detail URLs, and for each runs the **same extraction as step 3-5** (the duplication) with the provided cookie. `crawlRecentNovels`/`crawlCompletedNovels` delegate to `crawlToRaw` and have no callers (dead).

External users of the static helpers (must keep working): `ranking/.../service/NaverSeriesRankingService.java` uses `NaverSeriesCrawler.extractQueryParam(...)`; `ranking/.../parser/NaverSeriesDetailParser.java` uses `NaverSeriesCrawler.cleanTitle(...)`.

---

## File Structure

**New:**
| File | Responsibility |
|---|---|
| `.../contents/Novel/NaverSeriesNovel/NaverSeriesDetail.java` | record `(Document doc, String firstDate)` |
| `.../contents/Novel/NaverSeriesNovel/NaverSeriesNovelFetcher.java` | Jsoup IO: detail page + first-episode date |
| `.../contents/Novel/NaverSeriesNovel/NaverSeriesNovelParser.java` | pure Document→`CrawlPayload`; holds parsing helpers + `cleanTitle`/`extractQueryParam` |
| `.../contents/Novel/NaverSeriesNovel/NaverSeriesNovelSource.java` | `ContentSource<NaverSeriesDetail>` |
| `.../src/test/resources/fixtures/naverseries/novel-12345.html` | detail page fixture |
| `.../src/test/resources/golden/naverseries-novel-12345.json` | golden (bootstrapped) |
| `.../contents/Novel/NaverSeriesNovel/NaverSeriesNovelGoldenTest.java` | characterization: legacy==golden, source==golden |

**Modified:**
| File | Change |
|---|---|
| `.../contents/Novel/NaverSeriesNovel/NaverSeriesCrawler.java` | T1: `get`/`extractFirstEpisodeDate` → `protected`. T3: inject fetcher+parser, delete `collectNovelById` + dead `crawlRecentNovels`/`crawlCompletedNovels`, refactor `crawlToRaw` to reuse fetcher+parser, remove moved helpers |
| `.../common/queue/executors/NaverSeriesNovelExecutor.java` | T3: delegate to `CrawlPipeline.run(naverSeriesNovelSource, ...)` |
| `.../ranking/Webnovel/NaverSeries/service/NaverSeriesRankingService.java` | T3: `NaverSeriesNovelParser.extractQueryParam` |
| `.../ranking/Webnovel/NaverSeries/parser/NaverSeriesDetailParser.java` | T3: `NaverSeriesNovelParser.cleanTitle` |

Production prefix: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/`
Test prefix: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/`

---

## Task 1: Add a test seam + fixture, characterize current `collectNovelById` (lock golden)

Make the two network methods overridable, add a deterministic HTML fixture, and snapshot the current output via a test subclass that returns the fixture (no network, no static mocking).

**Files:**
- Modify: `.../contents/Novel/NaverSeriesNovel/NaverSeriesCrawler.java` (visibility only)
- Create: `.../src/test/resources/fixtures/naverseries/novel-12345.html`
- Create: `.../contents/Novel/NaverSeriesNovel/NaverSeriesNovelGoldenTest.java`
- Create (bootstrapped): `.../src/test/resources/golden/naverseries-novel-12345.json`

- [ ] **Step 1: Make the network methods overridable**

In `NaverSeriesCrawler.java`, change the method signatures (body unchanged):
- `private String extractFirstEpisodeDate(String productNo, String cookieString) throws Exception` → `protected String extractFirstEpisodeDate(String productNo, String cookieString) throws Exception`
- `private Document get(String url, String cookieString) throws Exception` → `protected Document get(String url, String cookieString) throws Exception`

- [ ] **Step 2: Create the fixture** (`fixtures/naverseries/novel-12345.html`) — a minimal detail page exercising every selector the parser reads:

```html
<!DOCTYPE html>
<html>
<head>
  <meta property="og:url" content="https://series.naver.com/novel/detail.series?productNo=12345">
  <meta property="og:title" content="홍길동전 [완결]">
  <meta property="og:image" content="https://series.naver.com/cover/12345.jpg">
</head>
<body>
  <div class="end_head">관심 1,234</div>
  <div class="score_area">평점 9.5</div>
  <a class="btn_download"><span>1,234</span></a>
  <span id="commentCount">56</span>
  <h5 class="end_total_episode">총 <strong>100</strong>화</h5>
  <ul class="end_info">
    <li class="info_lst">
      <ul>
        <li>연재중</li>
        <li><span>글</span><a href="#">홍길동</a></li>
        <li><span>출판사</span><a href="#">네이버출판</a></li>
        <li>전체 이용가</li>
        <li><a href="#">판타지</a></li>
        <li><a href="#">액션</a></li>
      </ul>
    </li>
  </ul>
  <div class="end_dsc"><span class="_synopsis">재미있는 이야기입니다. 접기</span></div>
</body>
</html>
```

- [ ] **Step 3: Write the characterization test** (`NaverSeriesNovelGoldenTest.java`):

```java
package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.example.crawler.crawl.support.GoldenFiles;
import com.example.crawler.crawl.support.SaveRawCapture;
import com.example.crawler.ingest.CollectorService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.mockito.Mockito.mock;

class NaverSeriesNovelGoldenTest {

    private static final String PRODUCT_ID = "12345";
    private static final String FIRST_DATE = "2020-01-01";

    private Document fixtureDoc() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/naverseries/novel-12345.html")) {
            Objects.requireNonNull(in, "fixture not found: /fixtures/naverseries/novel-12345.html");
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Jsoup.parse(html, "https://series.naver.com/");
        }
    }

    /** Characterization of CURRENT collectNovelById via a test seam (no network). Removed in Task 3. */
    @Test
    void legacyCollectNovelByIdMatchesGolden() throws Exception {
        CollectorService collector = mock(CollectorService.class);
        Document doc = fixtureDoc();

        NaverSeriesCrawler legacy = new NaverSeriesCrawler(collector) {
            @Override
            protected Document get(String url, String cookieString) {
                return doc;
            }
            @Override
            protected String extractFirstEpisodeDate(String productNo, String cookieString) {
                return FIRST_DATE;
            }
        };

        legacy.collectNovelById(PRODUCT_ID);

        GoldenFiles.assertMatchesGolden("naverseries-novel-12345.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
}
```

- [ ] **Step 4: Bootstrap the golden (first run FAILS by design)**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesNovelGoldenTest"`
Expected: FAIL with "Golden created ...".

- [ ] **Step 5: Verify the bootstrapped golden**

`golden/naverseries-novel-12345.json` must show `platformName "NaverSeries"`, `domain "WEBNOVEL"`, `platformSpecificId "12345"`, `url "https://series.naver.com/novel/detail.series?productNo=12345"`, and a `payload` with: `title "홍길동전"` (the `[완결]` tag stripped by cleanTitle), `author "홍길동"`, `publisher "네이버출판"`, `status "연재중"`, `ageRating "전체 이용가"`, `synopsis "재미있는 이야기입니다."` (trailing "접기" stripped), `imageUrl`, `productUrl` (= the og:url), `titleId "12345"`, `genres ["판타지","액션"]`, `rating 9.5`, `downloadCount 1234`, `commentCount 56`, `episodeCount 100`, `firstDate "2020-01-01"`. If anything differs, STOP and report.

- [ ] **Step 6: Re-run → PASS; then commit**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesNovelGoldenTest"` → PASS.

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents/Novel/NaverSeriesNovel/NaverSeriesCrawler.java" "-AOD-All-of-Dopamine-crawler/src/test/resources/fixtures/naverseries/novel-12345.html" "-AOD-All-of-Dopamine-crawler/src/test/resources/golden/naverseries-novel-12345.json" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/contents/Novel/NaverSeriesNovel/NaverSeriesNovelGoldenTest.java"
git commit -m "test(crawler): characterize current NaverSeries novel saveRaw output (golden)"
```

---

## Task 2: Extract `NaverSeriesDetail` + `Fetcher` + `Parser` + `Source`; prove source matches golden

Move the fetch and parse logic out of `NaverSeriesCrawler` into focused units (do NOT yet modify `NaverSeriesCrawler`/executor — that is Task 3). Move method bodies **verbatim** from `NaverSeriesCrawler` where indicated.

**Files:**
- Create: `NaverSeriesDetail.java`, `NaverSeriesNovelFetcher.java`, `NaverSeriesNovelParser.java`, `NaverSeriesNovelSource.java`
- Modify: `NaverSeriesNovelGoldenTest.java`

- [ ] **Step 1: Create `NaverSeriesDetail` record**

```java
package com.example.crawler.contents.Novel.NaverSeriesNovel;

import org.jsoup.nodes.Document;

/** Bundle of the two fetches a NaverSeries novel detail needs: the detail page + first-episode date. */
public record NaverSeriesDetail(Document doc, String firstDate) {
}
```

- [ ] **Step 2: Create `NaverSeriesNovelFetcher`** — move `get(...)` and `extractFirstEpisodeDate(...)` **verbatim** from `NaverSeriesCrawler` (as `public`/`private` shown), and add the bundling methods:

```java
package com.example.crawler.contents.Novel.NaverSeriesNovel;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/** Jsoup IO for NaverSeries novels: fetch the detail page and the first-episode date. */
@Slf4j
@Component
public class NaverSeriesNovelFetcher {

    private static final String DETAIL_URL = "https://series.naver.com/novel/detail.series?productNo=";

    /** Fetch a novel detail (detail page + first-episode date). cookieString may be null. */
    public NaverSeriesDetail fetchDetail(String productId, String cookieString) throws Exception {
        Document doc = get(DETAIL_URL + productId, cookieString);
        String firstDate = null;
        try {
            firstDate = extractFirstEpisodeDate(productId, cookieString);
        } catch (Exception e) {
            log.warn("1화 날짜 추출 실패 for {}: {}", productId, e.getMessage());
        }
        return new NaverSeriesDetail(doc, firstDate);
    }

    /** Convenience for the queue path (no cookie), matching the legacy collectNovelById behavior. */
    public NaverSeriesDetail fetchDetail(String productId) throws Exception {
        return fetchDetail(productId, null);
    }

    /** Fetch a detail Document for an already-resolved URL (used by the admin list crawl). */
    public Document getByUrl(String detailUrl, String cookieString) throws Exception {
        return get(detailUrl, cookieString);
    }

    // --- moved VERBATIM from NaverSeriesCrawler (body unchanged) ---
    // private Document get(String url, String cookieString) throws Exception { ... }
    // private String extractFirstEpisodeDate(String productNo, String cookieString) throws Exception { ... }
}
```

> Move the **exact bodies** of `get(...)` and `extractFirstEpisodeDate(...)` from the current `NaverSeriesCrawler` into this class as `private` methods (replace the `// moved VERBATIM` comment). Keep the System.out debug lines in `extractFirstEpisodeDate` exactly as-is for now (a later cleanup pass converts them); do NOT change behavior.

- [ ] **Step 3: Create `NaverSeriesNovelParser`** — the pure Document→payload logic. Move the extraction from `collectNovelById` into `parse`, and move the helper methods **verbatim** from `NaverSeriesCrawler`.

```java
package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.example.crawler.crawl.CrawlPayload;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parser: NaverSeries novel detail Document → CrawlPayload. Returns null to SKIP (adult / no title). */
@Slf4j
@Component
public class NaverSeriesNovelParser {

    /**
     * @param productId the productNo (used only for logging context)
     * @param detail    the fetched detail page + first-episode date
     * @return CrawlPayload, or null if the novel should be skipped (adult content or missing title)
     */
    public CrawlPayload parse(String productId, NaverSeriesDetail detail) {
        Document doc = detail.doc();

        // 19금 작품 체크
        Element adultMsg = doc.selectFirst("#adult_msg");
        Element enctp = doc.selectFirst("input[name=enctp]");
        boolean isAdultContent = (adultMsg != null) || (enctp != null && "19".equals(enctp.attr("value")));
        if (isAdultContent) {
            log.info("19금 작품으로 스킵: productNo={}", productId);
            return null;
        }

        String productUrl = attr(doc.selectFirst("meta[property=og:url]"), "content");
        if (productUrl == null || productUrl.isBlank()) {
            productUrl = "https://series.naver.com/novel/detail.series?productNo=" + productId;
        }

        String rawTitle = attr(doc.selectFirst("meta[property=og:title]"), "content");
        String title = cleanTitle(rawTitle != null ? rawTitle : text(doc.selectFirst("h2")));
        if (title == null || title.isBlank()) {
            log.warn("제목을 찾을 수 없는 작품 스킵: productNo={}", productId);
            return null;
        }

        String imageUrl = attr(doc.selectFirst("meta[property=og:image]"), "content");
        Element head = doc.selectFirst("div.end_head");
        BigDecimal rating = extractRating(doc);

        Long downloadCount = null;
        Element downloadBtnSpan = doc.selectFirst("a.btn_download > span");
        if (downloadBtnSpan != null) {
            downloadCount = parseKoreanCount(downloadBtnSpan.text());
        }

        Long commentCount = extractCommentCount(doc, head);
        Long episodeCount = extractEpisodeCount(doc);

        Element infoUl = doc.selectFirst("ul.end_info li.info_lst > ul");
        String status = null;
        if (infoUl != null) {
            Element statusLi = infoUl.selectFirst("> li");
            if (statusLi != null) {
                String statusText = statusLi.text().trim();
                if ("연재중".equals(statusText) || "완결".equals(statusText)) {
                    status = statusText;
                }
            }
        }

        String author = findInfoValue(infoUl, "글");
        String publisher = findInfoValue(infoUl, "출판사");
        String ageRating = findAge(infoUl);

        List<String> genres = new ArrayList<>();
        if (infoUl != null) {
            for (Element li : infoUl.select("> li")) {
                String label = text(li.selectFirst("> span"));
                if ("연재중".equals(li.text()) || "완결".equals(li.text()) ||
                        "글".equals(label) || "출판사".equals(label) || "이용가".equals(label)) {
                    continue;
                }
                Element a = li.selectFirst("a");
                if (a != null) {
                    String g = a.text().trim();
                    if (!g.isEmpty() && !genres.contains(g)) {
                        genres.add(g);
                    }
                }
            }
        }

        String synopsis = "";
        Elements synopsisElements = doc.select("div.end_dsc ._synopsis");
        if (!synopsisElements.isEmpty()) {
            synopsis = text(synopsisElements.last()).replaceAll("\\s*접기$", "").trim();
        }

        String titleId = extractQueryParam(productUrl, "productNo");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", nz(title));
        payload.put("author", nz(author));
        payload.put("publisher", nz(publisher));
        payload.put("status", nz(status));
        payload.put("ageRating", nz(ageRating));
        payload.put("synopsis", nz(synopsis));
        payload.put("imageUrl", nz(imageUrl));
        payload.put("productUrl", nz(productUrl));
        payload.put("titleId", nz(titleId));
        payload.put("genres", genres);
        payload.put("rating", rating);
        payload.put("downloadCount", downloadCount);
        payload.put("commentCount", commentCount);
        payload.put("episodeCount", episodeCount);
        payload.put("firstDate", detail.firstDate());

        return new CrawlPayload(titleId, productUrl, payload);
    }

    // --- helpers moved VERBATIM from NaverSeriesCrawler ---
    // private static String text(Element e) { ... }
    // private static String attr(Element e, String name) { ... }
    // private static String findInfoValue(Element infoUl, String label) { ... }
    // private static String findAge(Element infoUl) { ... }
    // private static BigDecimal extractRating(Document doc) { ... }
    // private static Long extractCommentCount(Document doc, Element head) { ... }
    // private static Long extractEpisodeCount(Document doc) { ... }
    // private static Long parseKoreanCount(String s) { ... }
    // private static String nz(String s) { ... }

    // --- public static helpers (also used by the ranking module) — moved VERBATIM ---
    // public static String extractQueryParam(String url, String key) { ... }
    // public static String cleanTitle(String raw) { ... }
}
```

> Replace each `// moved VERBATIM` comment by copying the corresponding method **body unchanged** from the current `NaverSeriesCrawler`. `extractQueryParam` and `cleanTitle` stay `public static` (the ranking module calls them — repointed in Task 3). `parseKoreanCount`, `extractRating`, `extractCommentCount`, `extractEpisodeCount`, `findInfoValue`, `findAge`, `text`, `attr`, `nz` become `private static` here.

- [ ] **Step 4: Create `NaverSeriesNovelSource`**

```java
package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.example.crawler.crawl.ContentSource;
import com.example.crawler.crawl.CrawlPayload;
import com.example.crawler.crawl.SourceDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** NaverSeries webnovel ContentSource. Fetch via Jsoup (no cookie, like legacy collectNovelById), parse pure. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverSeriesNovelSource implements ContentSource<NaverSeriesDetail> {

    private final NaverSeriesNovelFetcher fetcher;
    private final NaverSeriesNovelParser parser;

    @Override
    public SourceDescriptor descriptor() {
        return SourceDescriptor.NAVER_SERIES;
    }

    @Override
    public NaverSeriesDetail fetchDetail(String targetId) {
        try {
            return fetcher.fetchDetail(targetId);
        } catch (Exception e) {
            throw new RuntimeException("NaverSeries fetch failed for " + targetId, e);
        }
    }

    @Override
    public CrawlPayload parse(String targetId, NaverSeriesDetail rawDetail) {
        return parser.parse(targetId, rawDetail);
    }
}
```

- [ ] **Step 5: Add the source-match test** to `NaverSeriesNovelGoldenTest` (add imports + method):

```java
// add imports:
import com.example.crawler.crawl.CrawlPipeline;
import static org.mockito.Mockito.when;
```

```java
    @Test
    void naverSeriesNovelSourceMatchesGolden() throws Exception {
        CollectorService collector = mock(CollectorService.class);
        NaverSeriesNovelFetcher fetcher = mock(NaverSeriesNovelFetcher.class);
        when(fetcher.fetchDetail(PRODUCT_ID)).thenReturn(new NaverSeriesDetail(fixtureDoc(), FIRST_DATE));

        NaverSeriesNovelSource source = new NaverSeriesNovelSource(fetcher, new NaverSeriesNovelParser());
        new CrawlPipeline(collector).run(source, PRODUCT_ID);

        GoldenFiles.assertMatchesGolden("naverseries-novel-12345.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
```

- [ ] **Step 6: Run both tests — verify PASS**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesNovelGoldenTest"`
Expected: PASS (2 tests). The extracted parser reproduces the legacy golden exactly.

- [ ] **Step 7: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents/Novel/NaverSeriesNovel/NaverSeriesDetail.java" "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents/Novel/NaverSeriesNovel/NaverSeriesNovelFetcher.java" "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents/Novel/NaverSeriesNovel/NaverSeriesNovelParser.java" "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/contents/Novel/NaverSeriesNovel/NaverSeriesNovelSource.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/contents/Novel/NaverSeriesNovel/NaverSeriesNovelGoldenTest.java"
git commit -m "feat(crawler): extract NaverSeries fetcher/parser/source (match legacy golden)"
```

---

## Task 3: Migrate executor + ranking callers, refactor `NaverSeriesCrawler`, delete legacy

**Files:**
- Modify: `NaverSeriesNovelExecutor.java`, `NaverSeriesRankingService.java`, `NaverSeriesDetailParser.java` (ranking), `NaverSeriesCrawler.java`, `NaverSeriesNovelGoldenTest.java`

- [ ] **Step 1: Rewire `NaverSeriesNovelExecutor`** — replace the whole file:

```java
package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesNovelSource;
import com.example.crawler.crawl.CrawlPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 네이버 시리즈 소설 크롤링 Executor — delegates to the unified CrawlPipeline + NaverSeriesNovelSource.
 * (Removed in P4 when the Consumer talks to ContentSourceRegistry directly.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverSeriesNovelExecutor implements JobExecutor {

    private final CrawlPipeline crawlPipeline;
    private final NaverSeriesNovelSource naverSeriesNovelSource;

    @Override
    public JobType getJobType() {
        return JobType.NAVER_SERIES_NOVEL;
    }

    @Override
    public boolean execute(String targetId) {
        return crawlPipeline.run(naverSeriesNovelSource, targetId).isSuccess();
    }

    @Override
    public long getAverageExecutionTime() {
        return 2000;
    }

    @Override
    public int getRecommendedBatchSize() {
        return 3;
    }
}
```

- [ ] **Step 2: Repoint the ranking callers to `NaverSeriesNovelParser`**

In `ranking/Webnovel/NaverSeries/service/NaverSeriesRankingService.java`, change `NaverSeriesCrawler.extractQueryParam(` to `NaverSeriesNovelParser.extractQueryParam(` and update the import from `...NaverSeriesNovel.NaverSeriesCrawler` to `...NaverSeriesNovel.NaverSeriesNovelParser` (add the import if `NaverSeriesCrawler` is otherwise unused; keep it if still used elsewhere in the file).

In `ranking/Webnovel/NaverSeries/parser/NaverSeriesDetailParser.java`, change `NaverSeriesCrawler.cleanTitle(` to `NaverSeriesNovelParser.cleanTitle(` and fix the import likewise.

- [ ] **Step 3: Refactor `NaverSeriesCrawler`** — it becomes a thin admin-bulk crawler that reuses the fetcher+parser.

Replace the field/constructor with injected collaborators:
```java
    private final CollectorService collector;
    private final NaverSeriesNovelFetcher fetcher;
    private final NaverSeriesNovelParser parser;

    public NaverSeriesCrawler(CollectorService collector,
                              NaverSeriesNovelFetcher fetcher,
                              NaverSeriesNovelParser parser) {
        this.collector = collector;
        this.fetcher = fetcher;
        this.parser = parser;
    }
```

Delete these methods entirely: `collectNovelById`, `crawlRecentNovels`, `crawlCompletedNovels` (the latter two are dead), and all the helper methods now living in the fetcher/parser: `get`, `extractFirstEpisodeDate`, `text`, `attr`, `findInfoValue`, `findAge`, `extractRating`, `extractCommentCount`, `extractEpisodeCount`, `parseKoreanCount`, `extractQueryParam`, `cleanTitle`, `nz`.

Replace `crawlToRaw` with a version that scrapes the list and delegates each detail to the fetcher+parser (no duplicated extraction). The list-scrape block (collect `detailUrls`) is unchanged; the per-detail block becomes:

```java
    public int crawlToRaw(String baseListUrl, String cookieString, int maxPages) throws Exception {
        int saved = 0;
        int page = 1;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("작업 인터럽트 감지, 크롤링 중단 (현재까지 {}개 저장)", saved);
                return saved;
            }
            if (maxPages > 0 && page > maxPages) break;

            org.jsoup.nodes.Document listDoc = fetcher.getByUrl(baseListUrl + page, cookieString);

            java.util.Set<String> productNos = new java.util.LinkedHashSet<>();
            for (org.jsoup.nodes.Element a : listDoc.select("a[href*='/novel/detail.series'][href*='productNo=']")) {
                String pn = NaverSeriesNovelParser.extractQueryParam(absUrl(a.attr("href")), "productNo");
                if (pn != null) productNos.add(pn);
            }
            if (productNos.isEmpty()) {
                for (org.jsoup.nodes.Element a : listDoc.select("a[href*='/novel/detail.series']")) {
                    String pn = NaverSeriesNovelParser.extractQueryParam(absUrl(a.attr("href")), "productNo");
                    if (pn != null) productNos.add(pn);
                }
            }
            if (productNos.isEmpty()) break;

            for (String productNo : productNos) {
                NaverSeriesDetail detail = fetcher.fetchDetail(productNo, cookieString);
                CrawlPayload cp = parser.parse(productNo, detail);
                if (cp == null) continue; // adult / no title
                collector.saveRaw(SourceDescriptor.NAVER_SERIES.platformName(), SourceDescriptor.NAVER_SERIES.domain(),
                        cp.payload(), cp.platformSpecificId(), cp.url());
                saved++;
            }
            page++;
        }
        return saved;
    }

    private static String absUrl(String href) {
        if (href == null) return null;
        return href.startsWith("http") ? href : "https://series.naver.com" + href;
    }
```

Add imports as needed: `com.example.crawler.crawl.CrawlPayload`, `com.example.crawler.crawl.SourceDescriptor`, `com.example.crawler.ingest.CollectorService`. Remove now-unused imports (e.g. `org.jsoup.Jsoup`, `BigDecimal`, regex, URLDecoder, etc. that were only used by the moved helpers). Keep `@Component` and `@Slf4j`.

- [ ] **Step 4: Remove the obsolete legacy test method**

In `NaverSeriesNovelGoldenTest.java`, delete `legacyCollectNovelByIdMatchesGolden()` (it relies on overriding the now-removed `get`/`extractFirstEpisodeDate` and calls the deleted `collectNovelById`). Keep `naverSeriesNovelSourceMatchesGolden()`. Remove imports that become unused (`org.jsoup.Jsoup` and `Document` are still used by `fixtureDoc()`, so keep those).

- [ ] **Step 5: Verify the full module compiles and all tests pass**

Run: `./gradlew :-AOD-All-of-Dopamine-crawler:test`
Expected: BUILD SUCCESSFUL. `naverSeriesNovelSourceMatchesGolden` passes; Steam/TMDB/foundation tests and `contextLoads` still pass. `ContentSourceRegistry` now also registers `NAVER_SERIES_NOVEL`.

- [ ] **Step 6: Confirm cleanups**

Run: `git grep -n "collectNovelById\|crawlRecentNovels\|crawlCompletedNovels" -- "-AOD-All-of-Dopamine-crawler/src"` → no matches in src.
Run: `git grep -n "NaverSeriesCrawler.extractQueryParam\|NaverSeriesCrawler.cleanTitle" -- "-AOD-All-of-Dopamine-crawler/src"` → no matches (ranking now uses `NaverSeriesNovelParser`).
If any remain, fix before committing.

- [ ] **Step 7: Commit**

```bash
git add -A -- "-AOD-All-of-Dopamine-crawler/src"
git commit -m "refactor(crawler): route NaverSeries through pipeline+source, slim NaverSeriesCrawler, delete legacy collectNovelById"
```

---

## Self-Review

**1. Spec coverage:** §4 NaverSeries — 600-line crawler split into `NaverSeriesNovelFetcher` (Jsoup) + `NaverSeriesNovelParser` (pure) + `NaverSeriesNovelSource`; `crawlToRaw`/`collectNovelById` extraction de-duplicated (both now use parser); executor rewired; dead `crawlRecentNovels`/`crawlCompletedNovels` removed. §6 — golden from current code (test seam), source proven equal. §5 — single-item saveRaw byte-identical. ✅ (System.out→slf4j in the moved `extractFirstEpisodeDate` deferred to a cleanup pass to keep this migration behavior-neutral — noted.)

**2. Placeholder scan:** The only intentional "fill-ins" are the explicit **move-verbatim** instructions for existing method bodies (Task 2 Steps 2-3) — these reference concrete, named methods in the current `NaverSeriesCrawler`, not vague TODOs. All new glue code, the fixture, and the tests are fully specified. The golden is verified against explicit expected values (Task 1 Step 5).

**3. Type consistency:** `NaverSeriesNovelSource implements ContentSource<NaverSeriesDetail>`; `fetchDetail(String)→NaverSeriesDetail`, `parse(String, NaverSeriesDetail)→CrawlPayload`. `NaverSeriesNovelFetcher.fetchDetail(String[,String])`, `getByUrl(String,String)`. `NaverSeriesNovelParser.parse(String, NaverSeriesDetail)` + public static `cleanTitle(String)`/`extractQueryParam(String,String)`. `CrawlPayload(titleId, productUrl, payload)` → pipeline uses the explicit `productUrl`, matching the legacy saveRaw url. `SourceDescriptor.NAVER_SERIES` = "NaverSeries"/"WEBNOVEL", matching the golden.

---

## Execution Handoff

Plan complete. Execute with subagent-driven-development on `refactor/crawler-unification`. Note: Tasks 2-3 involve verbatim code moves and touch the ranking module — the golden cross-check (legacy==golden in Task 1, source==golden in Task 2) is the safety net that catches any extraction error.
