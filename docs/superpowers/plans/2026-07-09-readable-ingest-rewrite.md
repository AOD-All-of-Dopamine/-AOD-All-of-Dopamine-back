# Readable Ingest Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** RawItem→Transform→Upsert 구간(기존 13파일 ~1,300줄)을 스펙(E안)대로 핵심 6파일 ~650줄의 typed 파이프라인으로 재작성한다.

**Architecture:** yml은 소스 매핑만(단일 `mappings`, 목적지=엔티티 프로퍼티명), 도메인 바인딩은 BeanWrapper 리플렉션+기동검증, 도메인별 차이는 `DomainCatalog` 1클래스, 오케스트레이션은 `IngestPipeline` 1클래스(claim 후 item별 트랜잭션 격리). 새 클래스는 Spring 애노테이션 없이 plain으로 만들고(구 엔진과 공존을 위해), 마지막 컷오버 태스크에서 `IngestConfig`로 배선하며 구 코드·구 yml을 동시 교체한다.

**Tech Stack:** Java 17, Spring Boot 3 (BeanWrapper, TransactionTemplate), SnakeYAML, JUnit 5 + Mockito (spring-boot-starter-test 포함), Gradle 멀티모듈.

**Spec:** `docs/superpowers/specs/2026-07-09-readable-ingest-rewrite-design.md` — 작업 전 §1(결정)·§5(배치 개선 3개) 필독.
**브랜치:** `feature/readable-ingest` (이미 생성됨). 모든 명령은 `C:/Users/dfdfg/IdeaProjects/AOD/-AOD-All-of-Dopamine-back` 에서 실행.
**테스트 명령 형식:** `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "<FQCN>" --console=plain` (모듈명 앞 대시 때문에 따옴표 필수)

## 이관 시 보존해야 하는 기존 동작의 숨은 사실 (조사 완료 — 그대로 따를 것)

1. **normalizers는 master 필드에만 실제 적용된다.** 구 yml의 `domain.author`·`platform.attributes.*` normalizer 항목은 no-op였다(구 엔진이 master doc에만 적용). v4에서 해당 항목을 제거해도 동작 동일.
2. **epic.yml의 `data.platforms: domain.platforms`는 死매핑.** 구 엔진이 fieldMappings 처리 후 `domain.platforms`를 `[platformName]+platformsFrom`으로 무조건 덮어썼다. v4에서 제거.
3. **valueMap은 어떤 yml도 사용하지 않는다.** v4 스키마에 없음 (YAGNI).
4. **병합 시 도메인 필드는 '덮어쓰기'다** — platforms 포함. 크로스플랫폼 병합 시 기존 platforms 배열이 새 플랫폼 것으로 교체되는 기존 이슈가 있으나 **동작 보존 원칙에 따라 그대로 재현**하고, 별도 이슈로 넘긴다 (Task 7 주석에 명시).
5. **`MovieContent`/`TvContent`의 출연진 프로퍼티명은 `cast`** (DB 컬럼만 `cast_members`). yml 목적지는 `domain.cast`.
6. 구 psid fallback 체인(`raw.platformSpecificId → payload.platformSpecificId → steam_appid → movie_details.id → tv_details.id → titleId → seriesId`)은: yml이 플랫폼별 키를 명시 매핑(steam_appid/titleId/seriesId/movie_details.id/tv_details.id)하고, 파이프라인은 `raw 컬럼 우선 → draft → payload.platformSpecificId` 3단만 남긴다.

---

### Task 1: `Values` — 값 유틸 (deepGet·타입변환·정규화·제목비교)

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/Values.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/ValuesTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.example.crawler.ingest;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValuesTest {

    @Test
    void deepGetSupportsNestedPathsAndBracketIndex() {
        Map<String, Object> raw = Map.of(
                "a", Map.of("b", "v"),
                "list", List.of(Map.of("name", "first"), Map.of("name", "second")));
        assertEquals("v", Values.deepGet(raw, "a.b"));
        assertEquals("first", Values.deepGet(raw, "list[0].name"));
        assertNull(Values.deepGet(raw, "a.missing"));
        assertNull(Values.deepGet(raw, "list[9].name"));
    }

    @Test
    void convertHandlesCoreTypes() {
        assertEquals("4.5", Values.convert(4.5, String.class));
        assertEquals(3, Values.convert("3", Integer.class));
        assertEquals(45, Values.convert(List.of(45, 60), Integer.class)); // TMDB episode_run_time 배열 → 첫 값
        assertNull(Values.convert(List.of(), Integer.class));
        assertEquals(LocalDate.of(2018, 5, 14), Values.convert("2018-05-14", LocalDate.class));
        assertEquals(List.of("판타지"), Values.convert(List.of("판타지"), List.class));
        assertEquals(List.of("단일값"), Values.convert("단일값", List.class)); // 비리스트 → 단일 원소 리스트 (구 동작)
        assertEquals("이름", Values.convert(Map.of("name", "이름"), String.class)); // {name:...} → name (구 동작)
        Map<String, Object> os = Map.of("windows", true);
        assertEquals(os, Values.convert(os, Map.class)); // jsonb 통과
        assertNull(Values.convert(null, String.class));
    }

    @Test
    void normalizeAppliesStepsInOrder() {
        assertEquals("제목", Values.normalize("제목  (개정판)  ", List.of("strip_parentheses", "collapse_spaces")));
        assertEquals("전지적 독자 시점", Values.normalize("[신작] 전지적 독자 시점 외전", List.of("strip_brackets", "strip_series_qualifiers", "collapse_spaces")));
        assertThrows(IllegalArgumentException.class, () -> Values.normalize("x", List.of("no_such_step")));
    }

    @Test
    void sameTitleNormalizesThenComparesExactly() {
        assertTrue(Values.sameTitle("전지적 독자 시점", "전지적  독자-시점!"));
        assertFalse(Values.sameTitle("전지적 독자 시점", "전지적 독자 시점 2"));
        assertFalse(Values.sameTitle(null, "x"));
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.ValuesTest" --console=plain`
Expected: `BUILD FAILED` — `cannot find symbol: class Values` (컴파일 에러 = RED)

- [ ] **Step 3: 최소 구현**

```java
package com.example.crawler.ingest;

import com.example.crawler.util.FlexibleDateParser;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ingest 전용 값 유틸 — 전부 static 순수 함수.
 * payload 접근(deepGet) · 타입 변환(convert) · 제목 정규화(normalize) · 병합용 제목 비교(sameTitle).
 * (구 TransformEngine.deepGet + GenericDomainUpserter.convertType + ContentSimilarityService 흡수)
 */
public final class Values {

    private Values() {}

    /** "a.b[0].c" 경로로 중첩 Map/List에서 값 추출. 없으면 null. */
    public static Object deepGet(Object obj, String path) {
        if (obj == null || path == null) return null;
        Object cur = obj;
        for (String rawPart : path.split("\\.")) {
            String part = rawPart;
            Integer idx = null;
            if (part.contains("[") && part.endsWith("]")) {
                int i = part.indexOf('[');
                try { idx = Integer.parseInt(part.substring(i + 1, part.length() - 1)); } catch (NumberFormatException ignored) {}
                part = part.substring(0, i);
            }
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = m.get(part);
            if (idx != null) {
                if (!(cur instanceof List<?> list) || idx < 0 || idx >= list.size()) return null;
                cur = list.get(idx);
            }
        }
        return cur;
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 목적지 프로퍼티 타입에 맞춰 변환. 모르는 타입은 원본 유지(리플렉션 바인딩이 실패 시 예외). */
    public static Object convert(Object value, Class<?> targetType) {
        if (value == null || targetType == null) return value;
        if (targetType != String.class && targetType.isInstance(value)) return value;
        if (targetType == String.class) return stringify(value);
        if (targetType == Integer.class || targetType == int.class) return toInteger(value);
        if (targetType == Long.class || targetType == long.class)
            return (value instanceof Number n) ? n.longValue() : Long.parseLong(value.toString());
        if (targetType == Double.class || targetType == double.class)
            return (value instanceof Number n) ? n.doubleValue() : Double.parseDouble(value.toString());
        if (targetType == LocalDate.class) return FlexibleDateParser.parse(value);
        if (List.class.isAssignableFrom(targetType)) return (value instanceof List<?> l) ? l : List.of(value);
        return value;
    }

    /** 구 GenericDomainUpserter 호환: Map에 name 키가 있으면 name 값 사용. */
    private static String stringify(Object v) {
        if (v instanceof Map<?, ?> m && m.containsKey("name")) return String.valueOf(m.get("name"));
        return String.valueOf(v);
    }

    /** 배열이면 첫 값 사용 (TMDB episode_run_time). 파싱 불가 → null (구 동작). */
    private static Integer toInteger(Object v) {
        if (v instanceof List<?> l) {
            if (l.isEmpty()) return null;
            v = l.get(0);
        }
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    /** yml normalizers 어휘 (RuleRegistry 기동검증이 참조). */
    public static final Set<String> NORMALIZERS = Set.of(
            "lowercase", "strip_parentheses", "collapse_spaces", "nfkc", "strip_brackets", "strip_series_qualifiers");

    /** normalizer 스텝들을 순서대로 적용 (구 TransformEngine.applyNormalizers 이동). */
    public static String normalize(String value, List<String> steps) {
        String s = value;
        for (String step : steps) {
            s = switch (step) {
                case "lowercase" -> s.toLowerCase();
                case "strip_parentheses" -> s.replaceAll("\\([^)]*\\)", "");
                case "collapse_spaces" -> s.replaceAll("\\s+", " ").trim();
                case "nfkc" -> Normalizer.normalize(s, Normalizer.Form.NFKC);
                case "strip_brackets" -> s.replaceAll("\\[[^\\]]*\\]", "");
                case "strip_series_qualifiers" -> s.replaceAll("(시즌\\s*\\d+|외전|스페셜)$", "").trim();
                default -> throw new IllegalArgumentException("unknown normalizer: " + step);
            };
        }
        return s;
    }

    /** 병합용 제목 동일성: 소문자화+공백/구두점 제거 후 정확 일치 (구 ContentSimilarityService 흡수). */
    public static boolean sameTitle(String a, String b) {
        return a != null && b != null && titleKey(a).equals(titleKey(b));
    }

    private static String titleKey(String t) {
        return t.toLowerCase().replaceAll("[\\s\\-_:;,.'\"!?()\\[\\]{}]", "");
    }
}
```

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.ValuesTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/Values.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/ValuesTest.java"
git commit -m "feat(ingest): Values util — deepGet, convert, normalize, sameTitle" -m "Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: `PlatformRule` — v4 yml 스키마 record + 명시적 파서

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/rule/PlatformRule.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/rule/PlatformRuleTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.example.crawler.ingest.rule;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlatformRuleTest {

    private Map<String, Object> load(String yml) {
        return new Yaml().load(yml);
    }

    @Test
    void parsesFullV4Rule() {
        PlatformRule r = PlatformRule.parse("rules/test.yml", load("""
                platformName: NaverSeries
                domain: WEBNOVEL
                schemaVersion: 4
                mappings:
                  title: master.masterTitle
                  author: domain.author
                  titleId: platform.platformSpecificId
                  rating: attr.rating
                defaults:
                  attr.rating: 0
                normalizers:
                  master.masterTitle: [nfkc, collapse_spaces]
                platformsFrom: [watch_providers]
                """));
        assertEquals("NaverSeries", r.platformName());
        assertEquals("WEBNOVEL", r.domain());
        assertEquals("master.masterTitle", r.mappings().get("title"));
        assertEquals(0, r.defaults().get("attr.rating"));
        assertEquals(List.of("nfkc", "collapse_spaces"), r.normalizers().get("master.masterTitle"));
        assertEquals(List.of("watch_providers"), r.platformsFrom());
    }

    @Test
    void optionalSectionsDefaultToEmpty() {
        PlatformRule r = PlatformRule.parse("rules/min.yml", load("""
                platformName: X
                domain: GAME
                mappings:
                  name: master.masterTitle
                """));
        assertTrue(r.defaults().isEmpty());
        assertTrue(r.normalizers().isEmpty());
        assertTrue(r.platformsFrom().isEmpty());
    }

    @Test
    void rejectsUnknownTopLevelKeyAndBadPrefixAndMissingRequired() {
        assertThrows(IllegalStateException.class, () -> PlatformRule.parse("p", load("""
                platformName: X
                domain: GAME
                fieldMappings: {a: master.masterTitle}
                """)), "구 v3 키(fieldMappings)는 거부되어야 한다");
        assertThrows(IllegalStateException.class, () -> PlatformRule.parse("p", load("""
                platformName: X
                domain: GAME
                mappings: {a: masterTitle}
                """)), "접두사 없는 목적지는 거부");
        assertThrows(IllegalStateException.class, () -> PlatformRule.parse("p", load("""
                domain: GAME
                mappings: {a: master.masterTitle}
                """)), "platformName 누락 거부");
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.rule.PlatformRuleTest" --console=plain`
Expected: `BUILD FAILED` — `cannot find symbol: class PlatformRule`

- [ ] **Step 3: 최소 구현**

```java
package com.example.crawler.ingest.rule;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * yml 룰 1개 = 이 record 1개 (스키마 v4).
 * mappings: 소스 payload 경로 → 목적지. 목적지 접두사가 저장 위치를 결정:
 *   master.*  → Content 프로퍼티 / domain.* → 도메인 엔티티 프로퍼티
 *   platform.* → PlatformData 프로퍼티 / attr.* → platform_data.attributes JSONB 키 리터럴
 * SnakeYAML 빈 바인딩 대신 명시적 Map 파싱 — 에러 메시지에 파일 경로를 담기 위함.
 */
public record PlatformRule(
        String platformName,
        String domain,
        int schemaVersion,
        Map<String, String> mappings,
        Map<String, Object> defaults,
        Map<String, List<String>> normalizers,
        List<String> platformsFrom) {

    private static final Set<String> TOP_KEYS =
            Set.of("platformName", "domain", "schemaVersion", "mappings", "defaults", "normalizers", "platformsFrom");
    private static final List<String> PREFIXES = List.of("master.", "domain.", "platform.", "attr.");

    @SuppressWarnings("unchecked")
    public static PlatformRule parse(String path, Map<String, Object> yaml) {
        Set<String> unknown = new HashSet<>(yaml.keySet());
        unknown.removeAll(TOP_KEYS);
        if (!unknown.isEmpty()) throw new IllegalStateException(path + ": 알 수 없는 최상위 키 " + unknown);

        String platformName = (String) yaml.get("platformName");
        String domain = (String) yaml.get("domain");
        if (platformName == null || domain == null)
            throw new IllegalStateException(path + ": platformName/domain 필수");

        Map<String, String> mappings = (Map<String, String>) yaml.getOrDefault("mappings", Map.of());
        Map<String, Object> defaults = (Map<String, Object>) yaml.getOrDefault("defaults", Map.of());
        for (String dst : mappings.values()) requirePrefix(path, dst);
        for (String dst : defaults.keySet()) requirePrefix(path, dst);

        return new PlatformRule(
                platformName, domain,
                ((Number) yaml.getOrDefault("schemaVersion", 4)).intValue(),
                mappings, defaults,
                (Map<String, List<String>>) yaml.getOrDefault("normalizers", Map.of()),
                (List<String>) yaml.getOrDefault("platformsFrom", List.of()));
    }

    private static void requirePrefix(String path, String dst) {
        if (PREFIXES.stream().noneMatch(dst::startsWith))
            throw new IllegalStateException(path + ": 목적지 접두사 오류 '" + dst + "' (master./domain./platform./attr. 중 하나)");
    }
}
```

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.rule.PlatformRuleTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/rule/PlatformRule.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/rule/PlatformRuleTest.java"
git commit -m "feat(ingest): PlatformRule v4 schema record with explicit strict parser" -m "Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: `DomainCatalog` — 도메인별 차이의 단일 테이블

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/DomainCatalog.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/DomainCatalogTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.example.crawler.ingest;

import com.example.shared.entity.*;
import com.example.shared.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DomainCatalogTest {

    private MovieContentRepository movieRepo;
    private TvContentRepository tvRepo;
    private GameContentRepository gameRepo;
    private WebtoonContentRepository webtoonRepo;
    private WebnovelContentRepository webnovelRepo;
    private DomainCatalog catalog;

    @BeforeEach
    void setUp() {
        movieRepo = mock(MovieContentRepository.class);
        tvRepo = mock(TvContentRepository.class);
        gameRepo = mock(GameContentRepository.class);
        webtoonRepo = mock(WebtoonContentRepository.class);
        webnovelRepo = mock(WebnovelContentRepository.class);
        catalog = new DomainCatalog(movieRepo, tvRepo, gameRepo, webtoonRepo, webnovelRepo);
    }

    @Test
    void createReturnsMatchingEntityTypeLinkedToContent() {
        Content c = new Content();
        assertInstanceOf(MovieContent.class, catalog.create(Domain.MOVIE, c));
        assertInstanceOf(TvContent.class, catalog.create(Domain.TV, c));
        assertInstanceOf(GameContent.class, catalog.create(Domain.GAME, c));
        assertInstanceOf(WebtoonContent.class, catalog.create(Domain.WEBTOON, c));
        WebnovelContent w = (WebnovelContent) catalog.create(Domain.WEBNOVEL, c);
        assertSame(c, w.getContent());
    }

    @Test
    void duplicateCandidatesByAuthorOrDeveloperAndUnsupportedDomainsReturnEmpty() {
        Content owner = new Content();
        WebnovelContent existing = new WebnovelContent(owner);
        WebnovelContent probe = new WebnovelContent(new Content());
        probe.setAuthor("싱숑");
        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existing));
        assertEquals(List.of(owner), catalog.duplicateCandidates(Domain.WEBNOVEL, probe));

        GameContent game = new GameContent(new Content());
        game.setDeveloper("밸브");
        when(gameRepo.findByDeveloper("밸브")).thenReturn(List.of());
        assertEquals(List.of(), catalog.duplicateCandidates(Domain.GAME, game));

        // author/developer 없으면 검색 안 함 (기존 동작)
        assertEquals(List.of(), catalog.duplicateCandidates(Domain.WEBNOVEL, new WebnovelContent(new Content())));
        verifyNoMoreInteractions(webnovelRepo);

        // MOVIE/TV는 중복검사 미지원 유지
        assertEquals(List.of(), catalog.duplicateCandidates(Domain.MOVIE, new MovieContent(new Content())));
        assertEquals(List.of(), catalog.duplicateCandidates(Domain.TV, new TvContent(new Content())));
    }

    @Test
    void saveDelegatesToDomainRepositoryAndFindByContentIdLoads() {
        WebnovelContent w = new WebnovelContent(new Content());
        catalog.save(Domain.WEBNOVEL, w);
        verify(webnovelRepo).save(w);

        when(gameRepo.findById(7L)).thenReturn(Optional.of(new GameContent(new Content())));
        assertTrue(catalog.findByContentId(Domain.GAME, 7L).isPresent());
        when(movieRepo.findById(9L)).thenReturn(Optional.empty());
        assertTrue(catalog.findByContentId(Domain.MOVIE, 9L).isEmpty());
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.DomainCatalogTest" --console=plain`
Expected: `BUILD FAILED` — `cannot find symbol: class DomainCatalog`

- [ ] **Step 3: 최소 구현**

```java
package com.example.crawler.ingest;

import com.example.shared.entity.*;
import com.example.shared.repository.*;

import java.util.List;
import java.util.Optional;

/**
 * 도메인별로 다른 것 전부를 모은 테이블 — 이 파일 하나가 "도메인 추가 시 고칠 곳"이다.
 * (구 DomainCoreUpsertService의 switch 3개 + ContentMergeService의 후보검색 switch 흡수)
 * 새 도메인 추가 = Domain enum + 엔티티/리포지토리 + 아래 switch 4곳에 한 줄씩.
 */
public class DomainCatalog {

    private final MovieContentRepository movieRepo;
    private final TvContentRepository tvRepo;
    private final GameContentRepository gameRepo;
    private final WebtoonContentRepository webtoonRepo;
    private final WebnovelContentRepository webnovelRepo;

    public DomainCatalog(MovieContentRepository movieRepo, TvContentRepository tvRepo,
                         GameContentRepository gameRepo, WebtoonContentRepository webtoonRepo,
                         WebnovelContentRepository webnovelRepo) {
        this.movieRepo = movieRepo;
        this.tvRepo = tvRepo;
        this.gameRepo = gameRepo;
        this.webtoonRepo = webtoonRepo;
        this.webnovelRepo = webnovelRepo;
    }

    /** 도메인 엔티티 생성 (Content 연결, 저장 전). */
    public Object create(Domain domain, Content content) {
        return switch (domain) {
            case MOVIE -> new MovieContent(content);
            case TV -> new TvContent(content);
            case GAME -> new GameContent(content);
            case WEBTOON -> new WebtoonContent(content);
            case WEBNOVEL -> new WebnovelContent(content);
        };
    }

    /** 병합 시 기존 도메인 엔티티 로드. */
    public Optional<Object> findByContentId(Domain domain, Long contentId) {
        return switch (domain) {
            case MOVIE -> movieRepo.findById(contentId).map(e -> e);
            case TV -> tvRepo.findById(contentId).map(e -> e);
            case GAME -> gameRepo.findById(contentId).map(e -> e);
            case WEBTOON -> webtoonRepo.findById(contentId).map(e -> e);
            case WEBNOVEL -> webnovelRepo.findById(contentId).map(e -> e);
        };
    }

    /**
     * 중복 후보: 같은 author(웹툰/웹소설)·developer(게임)의 기존 작품들.
     * MOVIE/TV는 기존 시스템과 동일하게 미지원(빈 목록). author/developer 없으면 검색하지 않는다.
     */
    public List<Content> duplicateCandidates(Domain domain, Object entity) {
        return switch (domain) {
            case GAME -> {
                String dev = ((GameContent) entity).getDeveloper();
                yield isBlank(dev) ? List.of()
                        : gameRepo.findByDeveloper(dev).stream().map(GameContent::getContent).toList();
            }
            case WEBTOON -> {
                String author = ((WebtoonContent) entity).getAuthor();
                yield isBlank(author) ? List.of()
                        : webtoonRepo.findByAuthor(author).stream().map(WebtoonContent::getContent).toList();
            }
            case WEBNOVEL -> {
                String author = ((WebnovelContent) entity).getAuthor();
                yield isBlank(author) ? List.of()
                        : webnovelRepo.findByAuthor(author).stream().map(WebnovelContent::getContent).toList();
            }
            case MOVIE, TV -> List.of();
        };
    }

    /** 도메인 엔티티 저장 (신규·병합 공용). */
    public void save(Domain domain, Object entity) {
        switch (domain) {
            case MOVIE -> movieRepo.save((MovieContent) entity);
            case TV -> tvRepo.save((TvContent) entity);
            case GAME -> gameRepo.save((GameContent) entity);
            case WEBTOON -> webtoonRepo.save((WebtoonContent) entity);
            case WEBNOVEL -> webnovelRepo.save((WebnovelContent) entity);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.DomainCatalogTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/DomainCatalog.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/DomainCatalogTest.java"
git commit -m "feat(ingest): DomainCatalog — per-domain create/find/dupCandidates/save in one table class" -m "Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: `DraftAssembler` — payload+rule → typed IngestDraft (골든 패리티: NaverSeries·TMDB Movie)

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/DraftAssembler.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/DraftAssemblerTest.java`

- [ ] **Step 1: 실패 테스트 작성** (골든 값 = 구 엔진 코드·yml 의미 분석으로 고정한 기대값)

```java
package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.PlatformRule;
import com.example.shared.entity.*;
import com.example.shared.repository.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DraftAssemblerTest {

    private final DomainCatalog catalog = new DomainCatalog(
            mock(MovieContentRepository.class), mock(TvContentRepository.class),
            mock(GameContentRepository.class), mock(WebtoonContentRepository.class),
            mock(WebnovelContentRepository.class));
    private final DraftAssembler assembler = new DraftAssembler(catalog);

    private PlatformRule rule(String yml) {
        return PlatformRule.parse("test", new Yaml().load(yml));
    }

    private static final String NAVER_SERIES_V4 = """
            platformName: NaverSeries
            domain: WEBNOVEL
            mappings:
              title: master.masterTitle
              imageUrl: master.posterImageUrl
              synopsis: master.synopsis
              firstDate: master.releaseDate
              author: domain.author
              publisher: domain.publisher
              ageRating: domain.ageRating
              genres: domain.genres
              productUrl: platform.url
              titleId: platform.platformSpecificId
              status: attr.status
              rating: attr.rating
              downloadCount: attr.download_count
            defaults:
              attr.download_count: 0
            normalizers:
              master.masterTitle: [strip_parentheses, collapse_spaces, nfkc]
            """;

    @Test
    void goldenNaverSeries() {
        Map<String, Object> payload = Map.of(
                "title", "전지적 독자 시점 (외전)",
                "imageUrl", "http://img", "synopsis", "줄거리",
                "firstDate", "2018-05-14",
                "author", "싱숑", "publisher", "문피아", "ageRating", "전체이용가",
                "genres", List.of("판타지"),
                "productUrl", "http://series", "titleId", "3293134",
                "status", "연재중", "rating", 9.9);

        DraftAssembler.IngestDraft d = assembler.assemble(payload, rule(NAVER_SERIES_V4));

        Content c = d.content();
        assertEquals(Domain.WEBNOVEL, c.getDomain());
        assertEquals("전지적 독자 시점", c.getMasterTitle());          // 괄호제거+공백정리 (골든)
        assertEquals(LocalDate.of(2018, 5, 14), c.getReleaseDate());   // 바인딩 시점에 typed 변환
        assertEquals("줄거리", c.getSynopsis());

        WebnovelContent w = (WebnovelContent) d.domainEntity();
        assertEquals("싱숑", w.getAuthor());
        assertEquals("전체이용가", w.getAgeRating());
        assertEquals(List.of("판타지"), w.getGenres());
        assertEquals(List.of("NaverSeries"), w.getPlatforms());        // 엔진 주입 (RF-4)

        PlatformData pd = d.platformData();
        assertEquals("NaverSeries", pd.getPlatformName());
        assertEquals("3293134", pd.getPlatformSpecificId());
        assertEquals("연재중", pd.getAttributes().get("status"));
        assertEquals(9.9, pd.getAttributes().get("rating"));
        assertEquals(0, pd.getAttributes().get("download_count"));     // default 채움 (RF-3)

        assertTrue(d.boundDomainProps().containsAll(List.of("author", "publisher", "ageRating", "genres", "platforms")));
    }

    private static final String TMDB_MOVIE_V4 = """
            platformName: TMDB_MOVIE
            domain: MOVIE
            mappings:
              title: master.masterTitle
              original_title: master.originalTitle
              overview: master.synopsis
              release_date: master.releaseDate
              movie_details.id: platform.platformSpecificId
              genres: domain.genres
              runtime: domain.runtime
              directors: domain.directors
              cast: domain.cast
              watch_providers: attr.watch_providers
            normalizers:
              master.masterTitle: [collapse_spaces, nfkc]
              master.originalTitle: [collapse_spaces, nfkc]
            platformsFrom: [watch_providers]
            """;

    @Test
    void goldenTmdbMovieWithPlatformsFromMerge() {
        Map<String, Object> payload = Map.of(
                "title", "듄:  파트2", "original_title", "Dune: Part Two",
                "overview", "사막 행성", "release_date", "2024-02-28",
                "movie_details", Map.of("id", 693134),
                "genres", List.of("SF"), "runtime", 166,
                "directors", List.of("드니 빌뇌브"), "cast", List.of("티모시 샬라메"),
                "watch_providers", List.of("Netflix", "Disney Plus"));

        DraftAssembler.IngestDraft d = assembler.assemble(payload, rule(TMDB_MOVIE_V4));

        assertEquals("듄: 파트2", d.content().getMasterTitle());
        MovieContent m = (MovieContent) d.domainEntity();
        assertEquals(166, m.getRuntime());
        assertEquals(List.of("티모시 샬라메"), m.getCast());
        // platformsFrom: 자기 플랫폼명 + watch_providers 병합, attributes에도 유지 (구 RF-4 골든)
        assertEquals(List.of("TMDB_MOVIE", "Netflix", "Disney Plus"), m.getPlatforms());
        assertEquals(List.of("Netflix", "Disney Plus"), d.platformData().getAttributes().get("watch_providers"));
        assertEquals("693134", d.platformData().getPlatformSpecificId()); // 중첩경로 + String 변환
    }

    @Test
    void missingSourceWithoutDefaultIsSkippedEntirely() {
        DraftAssembler.IngestDraft d = assembler.assemble(Map.of(), rule(NAVER_SERIES_V4));
        assertNull(d.content().getMasterTitle());
        assertNull(((WebnovelContent) d.domainEntity()).getAuthor());
        assertFalse(d.platformData().getAttributes().containsKey("status"));
        assertEquals(0, d.platformData().getAttributes().get("download_count")); // default만 채워짐
        assertEquals(List.of("NaverSeries"), ((WebnovelContent) d.domainEntity()).getPlatforms()); // platforms는 항상 주입
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.DraftAssemblerTest" --console=plain`
Expected: `BUILD FAILED` — `cannot find symbol: class DraftAssembler`

- [ ] **Step 3: 최소 구현**

```java
package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.PlatformRule;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * payload + rule → typed IngestDraft 조립 (구 TransformEngine.transform 대체).
 * 목적지 접두사: master.*=Content / domain.*=도메인 엔티티(리플렉션, 프로퍼티명 그대로)
 *              / platform.*=PlatformData / attr.*=attributes JSONB 키 리터럴.
 * 저장은 하지 않는다 — 그건 IngestPipeline의 일.
 */
public class DraftAssembler {

    private final DomainCatalog catalog;

    public DraftAssembler(DomainCatalog catalog) {
        this.catalog = catalog;
    }

    /** 조립 산물. boundDomainProps = 병합 시 덮어쓸 도메인 프로퍼티 목록. */
    public record IngestDraft(Content content, Object domainEntity, PlatformData platformData,
                              List<String> boundDomainProps) {}

    public IngestDraft assemble(Map<String, Object> payload, PlatformRule rule) {
        Domain domain = Domain.valueOf(rule.domain());
        Content content = new Content();
        content.setDomain(domain);
        Object domainEntity = catalog.create(domain, content);
        PlatformData pd = new PlatformData();
        pd.setPlatformName(rule.platformName());
        pd.setAttributes(new HashMap<>());
        pd.setLastSeenAt(Instant.now());

        BeanWrapper master = PropertyAccessorFactory.forBeanPropertyAccess(content);
        BeanWrapper dom = PropertyAccessorFactory.forBeanPropertyAccess(domainEntity);
        BeanWrapper platform = PropertyAccessorFactory.forBeanPropertyAccess(pd);
        List<String> boundDomainProps = new ArrayList<>();

        for (Map.Entry<String, String> e : rule.mappings().entrySet()) {
            Object value = Values.deepGet(payload, e.getKey());
            String dst = e.getValue();
            if (value == null) value = rule.defaults().get(dst);
            if (value == null) continue;                                  // 선언된 default 없으면 스킵 (RF-3)
            if (dst.startsWith("attr.")) {
                pd.getAttributes().put(dst.substring(5), value);           // JSONB 키 리터럴 그대로
            } else if (dst.startsWith("master.")) {
                bind(master, dst.substring(7), value);
            } else if (dst.startsWith("platform.")) {
                bind(platform, dst.substring(9), value);
            } else {                                                       // domain.
                String prop = dst.substring(7);
                bind(dom, prop, value);
                boundDomainProps.add(prop);
            }
        }

        // normalizers — master 필드에만 적용 (구 엔진과 동일)
        for (Map.Entry<String, List<String>> n : rule.normalizers().entrySet()) {
            String prop = n.getKey().substring(7);
            if (master.getPropertyValue(prop) instanceof String s)
                master.setPropertyValue(prop, Values.normalize(s, n.getValue()));
        }

        // domain.platforms 주입: [자기 플랫폼명] + platformsFrom 병합 — yml 매핑과 무관하게 항상 덮어씀 (RF-4)
        List<String> platforms = new ArrayList<>();
        platforms.add(rule.platformName());
        for (String attrKey : rule.platformsFrom())
            if (pd.getAttributes().get(attrKey) instanceof List<?> extra)
                for (Object v : extra) if (v instanceof String s) platforms.add(s);
        dom.setPropertyValue("platforms", platforms);
        if (!boundDomainProps.contains("platforms")) boundDomainProps.add("platforms");

        return new IngestDraft(content, domainEntity, pd, boundDomainProps);
    }

    /** 목적지 프로퍼티 타입에 맞춰 변환 후 세팅. 프로퍼티 부재는 RuleRegistry 기동검증이 사전 차단. */
    private void bind(BeanWrapper accessor, String property, Object value) {
        accessor.setPropertyValue(property, Values.convert(value, accessor.getPropertyType(property)));
    }
}
```

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.DraftAssemblerTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/DraftAssembler.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/DraftAssemblerTest.java"
git commit -m "feat(ingest): DraftAssembler — typed draft assembly with golden parity for NaverSeries/TMDB" -m "Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: `RuleRegistry`(신) — 스캔·인덱싱·기동 검증

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/rule/RuleRegistry.java`
- Create: `-AOD-All-of-Dopamine-crawler/src/test/resources/rules_v4_good/naverseries.yml` (아래 내용)
- Create: `-AOD-All-of-Dopamine-crawler/src/test/resources/rules_v4_bad/badprop.yml` (아래 내용)
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/rule/RuleRegistryTest.java`

- [ ] **Step 1: 테스트 리소스 2개 작성**

`src/test/resources/rules_v4_good/naverseries.yml`:
```yaml
platformName: NaverSeries
domain: WEBNOVEL
mappings:
  title: master.masterTitle
  author: domain.author
  titleId: platform.platformSpecificId
  rating: attr.rating
normalizers:
  master.masterTitle: [nfkc]
```

`src/test/resources/rules_v4_bad/badprop.yml`:
```yaml
platformName: Bad
domain: WEBNOVEL
mappings:
  title: master.masterTitle
  writer: domain.wrtier    # 오타 프로퍼티 — 부팅 실패해야 함
```

- [ ] **Step 2: 실패 테스트 작성**

```java
package com.example.crawler.ingest.rule;

import com.example.crawler.ingest.DomainCatalog;
import com.example.shared.repository.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RuleRegistryTest {

    private DomainCatalog catalog() {
        return new DomainCatalog(
                mock(MovieContentRepository.class), mock(TvContentRepository.class),
                mock(GameContentRepository.class), mock(WebtoonContentRepository.class),
                mock(WebnovelContentRepository.class));
    }

    @Test
    void loadsAndResolvesValidRules() {
        RuleRegistry reg = new RuleRegistry("classpath*:rules_v4_good/*.yml", catalog());
        PlatformRule r = reg.resolve("WEBNOVEL", "NaverSeries");
        assertEquals("NaverSeries", r.platformName());
        assertTrue(reg.pathOf("NaverSeries").endsWith("naverseries.yml"));
    }

    @Test
    void resolveRejectsUnknownPlatformAndDomainMismatch() {
        RuleRegistry reg = new RuleRegistry("classpath*:rules_v4_good/*.yml", catalog());
        assertThrows(IllegalArgumentException.class, () -> reg.resolve("WEBNOVEL", "NoSuch"));
        assertThrows(IllegalArgumentException.class, () -> reg.resolve("GAME", "NaverSeries"));
    }

    @Test
    void bootFailsOnTypoDestinationProperty() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new RuleRegistry("classpath*:rules_v4_bad/*.yml", catalog()));
        assertTrue(e.getMessage().contains("wrtier"), "오타 프로퍼티명이 에러 메시지에 있어야: " + e.getMessage());
    }

    @Test
    void bootFailsWhenNoRulesFound() {
        assertThrows(IllegalStateException.class,
                () -> new RuleRegistry("classpath*:rules_v4_none/*.yml", catalog()));
    }
}
```

- [ ] **Step 3: 실행 → 실패 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.rule.RuleRegistryTest" --console=plain`
Expected: `BUILD FAILED` — `cannot find symbol: class RuleRegistry` (ingest.rule 패키지)

- [ ] **Step 4: 최소 구현**

```java
package com.example.crawler.ingest.rule;

import com.example.crawler.ingest.DomainCatalog;
import com.example.crawler.ingest.Values;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기동 시 룰 yml 전부 스캔 → 파싱 → 검증 → platformName으로 인덱싱.
 * (구 service.RuleRegistry의 스캔/인덱싱 + RuleLoader 흡수 + 목적지 프로퍼티 기동검증 추가)
 * 검증 실패 = 부팅 실패: yml 오타·엔티티 rename 미반영이 배포 전에 잡힌다 (조용한 유실 차단).
 */
public class RuleRegistry {

    private final Map<String, PlatformRule> byPlatform = new LinkedHashMap<>();
    private final Map<String, String> paths = new LinkedHashMap<>();

    public RuleRegistry(String locationPattern, DomainCatalog catalog) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
            for (Resource res : resources) {
                String url = res.getURL().toString();
                Map<String, Object> yaml;
                try (InputStream in = res.getInputStream()) {
                    yaml = new Yaml().load(in);
                }
                PlatformRule rule = PlatformRule.parse(url, yaml);
                validate(url, rule, catalog);
                if (byPlatform.put(rule.platformName(), rule) != null)
                    throw new IllegalStateException("platformName 중복: " + rule.platformName() + " (" + url + ")");
                paths.put(rule.platformName(), shortPath(url));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("rule yml 스캔 실패: " + locationPattern, e);
        }
        if (byPlatform.isEmpty())
            throw new IllegalStateException("발견된 rule yml이 없음: " + locationPattern);
    }

    /** 기동 검증: 모든 목적지 프로퍼티 실존 + normalizer 어휘 확인. */
    private void validate(String path, PlatformRule rule, DomainCatalog catalog) {
        Domain domain = Domain.valueOf(rule.domain());
        BeanWrapper master = PropertyAccessorFactory.forBeanPropertyAccess(new Content());
        BeanWrapper dom = PropertyAccessorFactory.forBeanPropertyAccess(catalog.create(domain, new Content()));
        BeanWrapper platform = PropertyAccessorFactory.forBeanPropertyAccess(new PlatformData());

        for (String dst : rule.mappings().values()) requireProperty(path, dst, master, dom, platform);
        for (String dst : rule.defaults().keySet()) requireProperty(path, dst, master, dom, platform);

        for (Map.Entry<String, List<String>> n : rule.normalizers().entrySet()) {
            if (!n.getKey().startsWith("master."))
                throw new IllegalStateException(path + ": normalizer는 master.*만 지원 — " + n.getKey());
            requireProperty(path, n.getKey(), master, dom, platform);
            for (String step : n.getValue())
                if (!Values.NORMALIZERS.contains(step))
                    throw new IllegalStateException(path + ": 알 수 없는 normalizer '" + step + "'");
        }
    }

    private void requireProperty(String path, String dst, BeanWrapper master, BeanWrapper dom, BeanWrapper platform) {
        if (dst.startsWith("attr.")) return;                         // JSONB — 자유 키
        BeanWrapper acc = dst.startsWith("master.") ? master : dst.startsWith("platform.") ? platform : dom;
        String prop = dst.substring(dst.indexOf('.') + 1);
        if (!acc.isWritableProperty(prop))
            throw new IllegalStateException(path + ": 존재하지 않는 프로퍼티 '" + dst + "'");
    }

    /** (domain, platform) 매칭 — 구 레지스트리와 동일 시맨틱. */
    public PlatformRule resolve(String domain, String platformName) {
        PlatformRule rule = byPlatform.get(platformName);
        if (rule == null) throw new IllegalArgumentException("No rule for platform: " + platformName);
        if (domain != null && !rule.domain().equalsIgnoreCase(domain))
            throw new IllegalArgumentException("Rule domain mismatch: platform=" + platformName
                    + " rule.domain=" + rule.domain() + " requested=" + domain);
        return rule;
    }

    /** TransformRun.rulePath 기록용. */
    public String pathOf(String platformName) {
        return paths.get(platformName);
    }

    private static String shortPath(String url) {
        int i = url.lastIndexOf("rules");
        return i >= 0 ? url.substring(i) : url;
    }
}
```

- [ ] **Step 5: 실행 → 통과 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.rule.RuleRegistryTest" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/rule/RuleRegistry.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/rule/RuleRegistryTest.java" "-AOD-All-of-Dopamine-crawler/src/test/resources/rules_v4_good" "-AOD-All-of-Dopamine-crawler/src/test/resources/rules_v4_bad"
git commit -m "feat(ingest): RuleRegistry with boot-time destination validation (typo = boot failure)" -m "Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: `IngestPipeline` ① — claim·item 트랜잭션 격리·신규 저장·상태 기록

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/IngestPipeline.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/IngestPipelineTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.PlatformRule;
import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.entity.*;
import com.example.shared.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestPipelineTest {

    private RawItemRepository rawRepo;
    private TransformRunRepository runRepo;
    private RuleRegistry registry;
    private ContentRepository contentRepo;
    private PlatformDataRepository platformRepo;
    private WebnovelContentRepository webnovelRepo;
    private DomainCatalog catalog;
    private IngestPipeline pipeline;

    private static final String NAVER_SERIES_V4 = """
            platformName: NaverSeries
            domain: WEBNOVEL
            mappings:
              title: master.masterTitle
              author: domain.author
              titleId: platform.platformSpecificId
            """;

    @BeforeEach
    void setUp() {
        rawRepo = mock(RawItemRepository.class);
        runRepo = mock(TransformRunRepository.class);
        contentRepo = mock(ContentRepository.class);
        platformRepo = mock(PlatformDataRepository.class);
        webnovelRepo = mock(WebnovelContentRepository.class);
        catalog = new DomainCatalog(
                mock(MovieContentRepository.class), mock(TvContentRepository.class),
                mock(GameContentRepository.class), mock(WebtoonContentRepository.class), webnovelRepo);
        registry = mock(RuleRegistry.class);
        PlatformRule rule = PlatformRule.parse("t", new Yaml().load(NAVER_SERIES_V4));
        when(registry.resolve("WEBNOVEL", "NaverSeries")).thenReturn(rule);
        when(registry.pathOf("NaverSeries")).thenReturn("rules/webnovel/naverseries.yml");

        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        pipeline = new IngestPipeline(rawRepo, runRepo, registry, new DraftAssembler(catalog),
                catalog, contentRepo, platformRepo, new TransactionTemplate(ptm));
    }

    private RawItem raw(long id, String platform, String domain, Map<String, Object> payload) {
        RawItem r = new RawItem();
        r.setRawId(id);
        r.setPlatformName(platform);
        r.setDomain(domain);
        r.setSourcePayload(payload);
        return r;
    }

    @Test
    void newContentPathSavesAllThreeAndRecordsSuccess() {
        RawItem r = raw(1L, "NaverSeries", "WEBNOVEL",
                Map.of("title", "신작", "author", "작가", "titleId", "42"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));
        when(webnovelRepo.findByAuthor("작가")).thenReturn(List.of());
        when(contentRepo.save(any())).thenAnswer(inv -> {
            Content c = inv.getArgument(0);
            c.setContentId(100L);
            return c;
        });
        when(platformRepo.findByPlatformNameAndPlatformSpecificId("NaverSeries", "42"))
                .thenReturn(java.util.Optional.empty());

        int processed = pipeline.processBatch(10);

        assertEquals(1, processed);                       // 반환값 = claim된 건수
        assertTrue(r.isProcessed());                      // claim 시점에 processed 마킹
        verify(contentRepo).save(any(Content.class));
        verify(webnovelRepo).save(any(WebnovelContent.class));
        verify(platformRepo).save(any(PlatformData.class));

        ArgumentCaptor<TransformRun> run = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo).save(run.capture());
        assertEquals("SUCCESS", run.getValue().getStatus());
        assertEquals(100L, run.getValue().getProducedContentId());
        assertEquals("rules/webnovel/naverseries.yml", run.getValue().getRulePath());
    }

    @Test
    void blankTitleIsSkippedExplicitly() {                // 개선 ③
        RawItem r = raw(2L, "NaverSeries", "WEBNOVEL", Map.of("author", "작가"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));

        pipeline.processBatch(10);

        ArgumentCaptor<TransformRun> run = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo).save(run.capture());
        assertEquals("SKIPPED", run.getValue().getStatus());
        verify(contentRepo, never()).save(any());
    }

    @Test
    void unknownPlatformIsFailedAndBatchContinues() {     // 개선 ①+②
        RawItem bad = raw(3L, "NoSuchPlatform", "WEBNOVEL", Map.of("title", "x"));
        RawItem good = raw(4L, "NaverSeries", "WEBNOVEL", Map.of("title", "신작2", "author", "작가2"));
        when(registry.resolve("WEBNOVEL", "NoSuchPlatform"))
                .thenThrow(new IllegalArgumentException("No rule for platform: NoSuchPlatform"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(bad, good));
        when(webnovelRepo.findByAuthor("작가2")).thenReturn(List.of());
        when(contentRepo.save(any())).thenAnswer(inv -> {
            Content c = inv.getArgument(0);
            c.setContentId(101L);
            return c;
        });

        pipeline.processBatch(10);

        ArgumentCaptor<TransformRun> runs = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo, times(2)).save(runs.capture());
        assertEquals("FAILED", runs.getAllValues().get(0).getStatus());
        assertTrue(runs.getAllValues().get(0).getError().contains("NoSuchPlatform"));
        assertEquals("SUCCESS", runs.getAllValues().get(1).getStatus()); // 실패해도 배치 계속
        assertTrue(bad.isProcessed());                     // 재시도 무의미 → processed 유지
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.IngestPipelineTest" --console=plain`
Expected: `BUILD FAILED` — `cannot find symbol: class IngestPipeline`

- [ ] **Step 3: 최소 구현** (병합 경로는 Task 7에서 — 여기선 후보가 비면 신규 저장)

```java
package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.PlatformRule;
import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import com.example.shared.entity.RawItem;
import com.example.shared.repository.ContentRepository;
import com.example.shared.repository.PlatformDataRepository;
import com.example.shared.repository.RawItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ingest 오케스트레이터 (구 BatchTransformService/Optimized + UpsertService + ContentUpsertService
 *  + DomainCoreUpsertService + ContentMergeService 대체).
 * 흐름: ① claim(잠금→processed 마킹→커밋으로 락 해제) → ② item별 트랜잭션 격리로
 *      변환→중복병합|신규저장 → ③ TransformRun 감사 기록.
 * 실패 시맨틱(스펙 §5): item 실패는 FAILED 기록 후 배치 계속 / 미지 플랫폼 FAILED / 제목 blank SKIPPED.
 */
@Slf4j
public class IngestPipeline {

    private final RawItemRepository rawRepo;
    private final TransformRunRepository runRepo;
    private final RuleRegistry ruleRegistry;
    private final DraftAssembler assembler;
    private final DomainCatalog catalog;
    private final ContentRepository contentRepo;
    private final PlatformDataRepository platformRepo;
    private final TransactionTemplate tx;

    public IngestPipeline(RawItemRepository rawRepo, TransformRunRepository runRepo,
                          RuleRegistry ruleRegistry, DraftAssembler assembler, DomainCatalog catalog,
                          ContentRepository contentRepo, PlatformDataRepository platformRepo,
                          TransactionTemplate tx) {
        this.rawRepo = rawRepo;
        this.runRepo = runRepo;
        this.ruleRegistry = ruleRegistry;
        this.assembler = assembler;
        this.catalog = catalog;
        this.contentRepo = contentRepo;
        this.platformRepo = platformRepo;
        this.tx = tx;
    }

    /** @return claim된 건수 (스케줄러가 0이 될 때까지 반복 호출) */
    public int processBatch(int batchSize) {
        List<RawItem> batch = tx.execute(s -> {
            List<RawItem> items = rawRepo.lockNextBatch(batchSize);
            for (RawItem raw : items) {                    // claim: 실패해도 재선택 안 됨 (독약 루프 방지)
                raw.setProcessed(true);
                raw.setProcessedAt(Instant.now());
            }
            rawRepo.saveAll(items);
            return items;
        });

        Set<Long> seenContentIds = new HashSet<>();        // 배치 내 중복 → SUCCESS_DUPLICATE (기존 유지)
        for (RawItem raw : batch) {
            TransformRun run = newRun(raw);
            try {
                tx.execute(s -> {
                    processOne(raw, run, seenContentIds);
                    return null;
                });
            } catch (Exception e) {                        // 개선 ①: item 격리 — 배치 계속
                run.setStatus("FAILED");
                run.setError(e.toString());
                log.warn("ingest 실패 rawId={} platform={}: {}", raw.getRawId(), raw.getPlatformName(), e.toString());
            } finally {
                run.setFinishedAt(Instant.now());
                runRepo.save(run);
            }
        }
        return batch.size();
    }

    private void processOne(RawItem raw, TransformRun run, Set<Long> seenContentIds) {
        PlatformRule rule = ruleRegistry.resolve(raw.getDomain(), raw.getPlatformName()); // 미지 플랫폼 → IAE → FAILED (②)
        run.setRulePath(ruleRegistry.pathOf(raw.getPlatformName()));
        DraftAssembler.IngestDraft draft = assembler.assemble(raw.getSourcePayload(), rule);

        String title = draft.content().getMasterTitle();
        if (title == null || title.isBlank()) {            // 개선 ③: 명시적 SKIPPED
            run.setStatus("SKIPPED");
            run.setError("master_title 없음");
            return;
        }

        fillPlatformIds(raw, draft.platformData());
        Domain domain = Domain.valueOf(rule.domain());

        Content merged = findAndMergeDuplicate(domain, draft);
        Long contentId;
        if (merged != null) {
            contentId = merged.getContentId();
        } else {
            Content saved = contentRepo.save(draft.content());
            draft.platformData().setContent(saved);
            platformRepo.save(draft.platformData());
            catalog.save(domain, draft.domainEntity());
            contentId = saved.getContentId();
        }
        run.setProducedContentId(contentId);
        run.setStatus(seenContentIds.contains(contentId) ? "SUCCESS_DUPLICATE" : "SUCCESS");
        seenContentIds.add(contentId);
    }

    /** psid/url 결정: raw 컬럼 우선 → yml 매핑(draft) → payload 공용 키 (구 fallback 체인의 축소 보존). */
    private void fillPlatformIds(RawItem raw, PlatformData pd) {
        if (notBlank(raw.getPlatformSpecificId())) pd.setPlatformSpecificId(raw.getPlatformSpecificId());
        else if (pd.getPlatformSpecificId() == null)
            pd.setPlatformSpecificId(Values.str(Values.deepGet(raw.getSourcePayload(), "platformSpecificId")));
        if (notBlank(raw.getUrl())) pd.setUrl(raw.getUrl());
        else if (pd.getUrl() == null)
            pd.setUrl(Values.str(Values.deepGet(raw.getSourcePayload(), "url")));
    }

    /** Task 7에서 병합 구현 — 지금은 후보 순회만 (빈 후보 = null). */
    private Content findAndMergeDuplicate(Domain domain, DraftAssembler.IngestDraft draft) {
        for (Content candidate : catalog.duplicateCandidates(domain, draft.domainEntity())) {
            if (Values.sameTitle(draft.content().getMasterTitle(), candidate.getMasterTitle())) {
                mergeInto(domain, candidate, draft);
                return candidate;
            }
        }
        return null;
    }

    private void mergeInto(Domain domain, Content existing, DraftAssembler.IngestDraft draft) {
        throw new UnsupportedOperationException("Task 7"); // 다음 태스크에서 구현
    }

    private TransformRun newRun(RawItem raw) {
        TransformRun run = new TransformRun();
        run.setRawId(raw.getRawId());
        run.setPlatformName(raw.getPlatformName());
        run.setDomain(raw.getDomain());
        return run;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
```

> 참고: `RawItem.isProcessed()` — RawItem은 Lombok getter라 boolean 필드 접근자가 `isProcessed()`다. 컴파일 에러 나면 `getProcessed()`로 맞출 것 (엔티티 확인: `-AOD-All-of-Dopamine-shared/src/main/java/com/example/shared/entity/RawItem.java`).

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.IngestPipelineTest" --console=plain`
Expected: `BUILD SUCCESSFUL` (3 tests)

- [ ] **Step 5: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/IngestPipeline.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/IngestPipelineTest.java"
git commit -m "feat(ingest): IngestPipeline — claim + per-item tx isolation + new-content path + run statuses" -m "Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: `IngestPipeline` ② — 중복 병합 경로 (기존 동작 보존)

**Files:**
- Modify: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/IngestPipeline.java` (mergeInto 구현)
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/IngestPipelineMergeTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.PlatformRule;
import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.entity.*;
import com.example.shared.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestPipelineMergeTest {

    private RawItemRepository rawRepo;
    private TransformRunRepository runRepo;
    private ContentRepository contentRepo;
    private PlatformDataRepository platformRepo;
    private WebnovelContentRepository webnovelRepo;
    private IngestPipeline pipeline;

    private static final String KAKAO_V4 = """
            platformName: KakaoPage
            domain: WEBNOVEL
            mappings:
              title: master.masterTitle
              synopsis: master.synopsis
              author: domain.author
              genres: domain.genres
              seriesId: platform.platformSpecificId
            """;

    @BeforeEach
    void setUp() {
        rawRepo = mock(RawItemRepository.class);
        runRepo = mock(TransformRunRepository.class);
        contentRepo = mock(ContentRepository.class);
        platformRepo = mock(PlatformDataRepository.class);
        webnovelRepo = mock(WebnovelContentRepository.class);
        DomainCatalog catalog = new DomainCatalog(
                mock(MovieContentRepository.class), mock(TvContentRepository.class),
                mock(GameContentRepository.class), mock(WebtoonContentRepository.class), webnovelRepo);
        RuleRegistry registry = mock(RuleRegistry.class);
        when(registry.resolve("WEBNOVEL", "KakaoPage"))
                .thenReturn(PlatformRule.parse("t", new Yaml().load(KAKAO_V4)));
        when(registry.pathOf("KakaoPage")).thenReturn("rules/webnovel/kakaopage.yml");
        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        pipeline = new IngestPipeline(rawRepo, runRepo, registry, new DraftAssembler(catalog),
                catalog, contentRepo, platformRepo, new TransactionTemplate(ptm));
    }

    @Test
    void mergesIntoExistingContentPreservingLegacySemantics() {
        // 기존 작품: NaverSeries로 이미 수집된 "전지적 독자 시점"
        Content existing = new Content();
        existing.setContentId(100L);
        existing.setDomain(Domain.WEBNOVEL);
        existing.setMasterTitle("전지적 독자 시점");
        existing.setSynopsis(null);                                   // null-fill 대상
        WebnovelContent existingNovel = new WebnovelContent(existing);
        existingNovel.setAuthor("싱숑");
        existingNovel.setPlatforms(List.of("NaverSeries"));

        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existingNovel));
        when(webnovelRepo.findById(100L)).thenReturn(Optional.of(existingNovel));
        when(platformRepo.findByPlatformNameAndPlatformSpecificId("KakaoPage", "K-77"))
                .thenReturn(Optional.empty());

        RawItem r = new RawItem();
        r.setRawId(5L);
        r.setPlatformName("KakaoPage");
        r.setDomain("WEBNOVEL");
        r.setSourcePayload(Map.of(
                "title", "전지적  독자 시점",                          // 정규화 후 동일 제목
                "synopsis", "카카오 시놉시스",
                "author", "싱숑",
                "genres", List.of("판타지"),
                "seriesId", "K-77"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));

        pipeline.processBatch(10);

        // 신규 Content 저장 없음 — 기존에 병합
        ArgumentCaptor<Content> savedContent = ArgumentCaptor.forClass(Content.class);
        verify(contentRepo).save(savedContent.capture());
        assertSame(existing, savedContent.getValue());
        assertEquals("카카오 시놉시스", existing.getSynopsis());        // null-fill 수행

        // PlatformData는 (platform, psid) 부재 시에만 추가되고 기존 Content에 연결
        ArgumentCaptor<PlatformData> savedPd = ArgumentCaptor.forClass(PlatformData.class);
        verify(platformRepo).save(savedPd.capture());
        assertSame(existing, savedPd.getValue().getContent());

        // 도메인 필드: 매핑된 프로퍼티 '덮어쓰기' (기존 동작 보존 — platforms 교체 이슈 포함)
        verify(webnovelRepo).save(existingNovel);
        assertEquals(List.of("판타지"), existingNovel.getGenres());
        assertEquals(List.of("KakaoPage"), existingNovel.getPlatforms()); // ⚠ 기존 이슈 그대로 (후속 이슈)

        ArgumentCaptor<TransformRun> run = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo).save(run.capture());
        assertEquals("SUCCESS", run.getValue().getStatus());
        assertEquals(100L, run.getValue().getProducedContentId());
    }

    @Test
    void existingPlatformDataIsNotDuplicated() {
        Content existing = new Content();
        existing.setContentId(100L);
        existing.setDomain(Domain.WEBNOVEL);
        existing.setMasterTitle("전지적 독자 시점");
        WebnovelContent existingNovel = new WebnovelContent(existing);
        existingNovel.setAuthor("싱숑");
        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existingNovel));
        when(webnovelRepo.findById(100L)).thenReturn(Optional.of(existingNovel));
        when(platformRepo.findByPlatformNameAndPlatformSpecificId("KakaoPage", "K-77"))
                .thenReturn(Optional.of(new PlatformData()));          // 이미 존재

        RawItem r = new RawItem();
        r.setRawId(6L);
        r.setPlatformName("KakaoPage");
        r.setDomain("WEBNOVEL");
        r.setSourcePayload(Map.of("title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-77"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));

        pipeline.processBatch(10);

        verify(platformRepo, never()).save(any());                     // 중복 추가 안 함 (기존 동작)
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.IngestPipelineMergeTest" --console=plain`
Expected: `FAILED` — `UnsupportedOperationException: Task 7`

- [ ] **Step 3: `mergeInto` 구현** (IngestPipeline의 스텁 교체)

```java
    /**
     * 기존 작품에 병합 (구 ContentMergeService.mergeContent 동작 보존):
     * ① Content는 null인 필드만 채움 ② PlatformData는 (platform,psid) 부재 시에만 추가
     * ③ 도메인 필드는 이번에 바인딩된 프로퍼티를 '덮어쓰기'.
     * ⚠ ③은 platforms 배열도 교체한다(크로스플랫폼 병합 시 기존 플랫폼 유실) — 기존 시스템과 동일한
     *   알려진 이슈로, 동작 보존 원칙에 따라 그대로 두고 별도 이슈로 다룬다.
     */
    private void mergeInto(Domain domain, Content existing, DraftAssembler.IngestDraft draft) {
        Content incoming = draft.content();
        if (existing.getOriginalTitle() == null) existing.setOriginalTitle(incoming.getOriginalTitle());
        if (existing.getReleaseDate() == null) existing.setReleaseDate(incoming.getReleaseDate());
        if (existing.getPosterImageUrl() == null) existing.setPosterImageUrl(incoming.getPosterImageUrl());
        if (existing.getSynopsis() == null) existing.setSynopsis(incoming.getSynopsis());
        contentRepo.save(existing);

        PlatformData pd = draft.platformData();
        boolean platformExists = platformRepo.findByPlatformNameAndPlatformSpecificId(
                pd.getPlatformName(), pd.getPlatformSpecificId()).isPresent();
        if (!platformExists) {
            pd.setContent(existing);
            platformRepo.save(pd);
        }

        catalog.findByContentId(domain, existing.getContentId()).ifPresent(existingEntity -> {
            BeanWrapper from = PropertyAccessorFactory.forBeanPropertyAccess(draft.domainEntity());
            BeanWrapper to = PropertyAccessorFactory.forBeanPropertyAccess(existingEntity);
            for (String prop : draft.boundDomainProps())
                to.setPropertyValue(prop, from.getPropertyValue(prop));
            catalog.save(domain, existingEntity);
        });
    }
```

- [ ] **Step 4: 실행 → 통과 확인 (Task 6 테스트 포함 회귀)**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.*" --console=plain`
Expected: `BUILD SUCCESSFUL` (Values/PlatformRule/DomainCatalog/DraftAssembler/RuleRegistry/Pipeline 전부)

- [ ] **Step 5: Commit**

```bash
git add -- "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/IngestPipeline.java" "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/IngestPipelineMergeTest.java"
git commit -m "feat(ingest): duplicate-merge path preserving legacy semantics (null-fill, platform-if-absent, domain overwrite)" -m "Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: 컷오버 — yml v4 교체 + IngestConfig 배선 + 구 코드 삭제

> 이 태스크는 원자적이다: yml을 v4로 바꾸면 구 엔진이 부팅에서 깨지므로, **yml 교체·배선·구 코드 삭제를 한 커밋**으로 처리한다.

**Files:**
- Rewrite: `-AOD-All-of-Dopamine-crawler/src/main/resources/rules/webnovel/naverseries.yml` (아래 전문)
- Rewrite: `-AOD-All-of-Dopamine-crawler/src/main/resources/rules/webnovel/kakaopage.yml`
- Rewrite: `-AOD-All-of-Dopamine-crawler/src/main/resources/rules/webtoon/naverwebtoon.yml`
- Rewrite: `-AOD-All-of-Dopamine-crawler/src/main/resources/rules/game/steam.yml`
- Rewrite: `-AOD-All-of-Dopamine-crawler/src/main/resources/rules/game/epic.yml`
- Rewrite: `-AOD-All-of-Dopamine-crawler/src/main/resources/rules/movie/tmdb_movie.yml`
- Rewrite: `-AOD-All-of-Dopamine-crawler/src/main/resources/rules/tv/tmdb_tv.yml`
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/IngestConfig.java`
- Modify: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/TransformSchedulingService.java` (18·36·68행)
- Modify: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/admin/controller/AdminTestController.java` (12–13·45–46·62–63·502·517·638·665·680행)
- Delete: 구 클래스 14개 + 구 테스트 3개 (Step 5 목록)
- Modify: `docs/3_TRANSFORM_ENGINE.md` (§4 교체)
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/ingest/RuleFilesTest.java`

- [ ] **Step 1: 프로덕션 yml 검증 테스트 먼저 작성** (7개 v4 yml이 기동검증 통과 + 5개 플랫폼 골든)

```java
package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.entity.*;
import com.example.shared.repository.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 프로덕션 rules/**.yml 7개가 v4로서 로드·검증 통과하고 골든 값을 내는지. */
class RuleFilesTest {

    private final DomainCatalog catalog = new DomainCatalog(
            mock(MovieContentRepository.class), mock(TvContentRepository.class),
            mock(GameContentRepository.class), mock(WebtoonContentRepository.class),
            mock(WebnovelContentRepository.class));
    private final RuleRegistry registry = new RuleRegistry("classpath*:rules/**/*.yml", catalog);
    private final DraftAssembler assembler = new DraftAssembler(catalog);

    @Test
    void allSevenProductionRulesLoad() {
        for (String p : List.of("NaverSeries", "KakaoPage", "NaverWebtoon", "Steam", "Epic", "TMDB_MOVIE", "TMDB_TV"))
            assertNotNull(registry.pathOf(p), p + " 룰이 로드되어야 함");
    }

    @Test
    void goldenSteam() {
        var d = assembler.assemble(Map.of(
                "name", "Half-Life (Anthology)", "steam_appid", 70,
                "developers", List.of("Valve"), "publishers", List.of("Valve"),
                "platforms", Map.of("windows", true, "mac", false),
                "genres", List.of("FPS"),
                "recommendations", Map.of("total", 12345)), registry.resolve("GAME", "Steam"));
        assertEquals("Half-Life", d.content().getMasterTitle());
        GameContent g = (GameContent) d.domainEntity();
        assertEquals("Valve", g.getDeveloper());                       // developers[0]
        assertEquals(Map.of("windows", true, "mac", false), g.getOsPlatforms());
        assertEquals("70", d.platformData().getPlatformSpecificId());
        assertEquals(12345, d.platformData().getAttributes().get("recommendation_count"));
        assertEquals(List.of("Steam"), g.getPlatforms());
    }

    @Test
    void goldenNaverWebtoon() {
        var d = assembler.assemble(Map.of(
                "title", "[단행본] 화산귀환 시즌2", "author", "비가",
                "status", "연재중", "weekday", "mon", "ageRating", "15세이용가",
                "tags", List.of("무협"), "titleId", "769209"), registry.resolve("WEBTOON", "NaverWebtoon"));
        assertEquals("화산귀환", d.content().getMasterTitle());          // 대괄호+시리즈수식어 제거
        WebtoonContent w = (WebtoonContent) d.domainEntity();
        assertEquals("비가", w.getAuthor());
        assertEquals("연재중", w.getStatus());
        assertEquals(List.of("무협"), w.getGenres());                   // tags → genres
        assertEquals(0, d.platformData().getAttributes().get("like_count")); // default
    }

    @Test
    void goldenKakaoPage() {
        var d = assembler.assemble(Map.of(
                "title", "나 혼자만 레벨업", "author", "추공",
                "seriesId", "48734983", "viewCount", 1000), registry.resolve("WEBNOVEL", "KakaoPage"));
        assertEquals("나 혼자만 레벨업", d.content().getMasterTitle());
        assertEquals("추공", ((WebnovelContent) d.domainEntity()).getAuthor());
        assertEquals(1000, d.platformData().getAttributes().get("view_count"));
        assertEquals(0, d.platformData().getAttributes().get("comment_count")); // default
    }

    @Test
    void goldenTmdbTv() {
        var d = assembler.assemble(Map.of(
                "name", "오징어 게임", "original_name", "Squid Game",
                "first_air_date", "2021-09-17",
                "number_of_seasons", 2, "episode_run_time", List.of(54, 62),
                "tv_details", Map.of("id", 93405),
                "watch_providers", List.of("Netflix")), registry.resolve("TV", "TMDB_TV"));
        TvContent t = (TvContent) d.domainEntity();
        assertEquals(2, t.getSeasonCount());
        assertEquals(54, t.getEpisodeRuntime());                       // 배열 첫 값
        assertEquals(List.of("TMDB_TV", "Netflix"), t.getPlatforms()); // platformsFrom 병합
        assertEquals("93405", d.platformData().getPlatformSpecificId());
    }

    @Test
    void goldenEpicDormant() {
        var d = assembler.assemble(Map.of(
                "data", Map.of("title", "Alan Wake 2", "developer", "Remedy",
                        "keyImages", List.of(Map.of("url", "http://img")))), registry.resolve("GAME", "Epic"));
        assertEquals("Alan Wake 2", d.content().getMasterTitle());
        assertEquals("Remedy", ((GameContent) d.domainEntity()).getDeveloper());
        assertEquals("http://img", d.content().getPosterImageUrl());   // keyImages[0].url
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인** (아직 구 v3 yml이라 `PlatformRule.parse`가 `fieldMappings` 거부)

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --tests "com.example.crawler.ingest.RuleFilesTest" --console=plain`
Expected: `FAILED` — `IllegalStateException: ... 알 수 없는 최상위 키 [fieldMappings, ...]`

- [ ] **Step 3: yml 7개를 v4 전문으로 교체**

`rules/webnovel/naverseries.yml`:
```yaml
platformName: NaverSeries
domain: WEBNOVEL
schemaVersion: 4

mappings:
  title:         master.masterTitle
  imageUrl:      master.posterImageUrl
  synopsis:      master.synopsis
  firstDate:     master.releaseDate
  author:        domain.author
  publisher:     domain.publisher
  ageRating:     domain.ageRating
  genres:        domain.genres
  productUrl:    platform.url
  titleId:       platform.platformSpecificId
  status:        attr.status
  rating:        attr.rating
  downloadCount: attr.download_count
  commentCount:  attr.comment_count
  episodeCount:  attr.episode_count

defaults:
  attr.download_count: 0
  attr.comment_count: 0
  attr.episode_count: 0

normalizers:
  master.masterTitle: [strip_parentheses, collapse_spaces, nfkc]
```

`rules/webnovel/kakaopage.yml`:
```yaml
platformName: KakaoPage
domain: WEBNOVEL
schemaVersion: 4

mappings:
  title:        master.masterTitle
  imageUrl:     master.posterImageUrl
  synopsis:     master.synopsis
  author:       domain.author
  publisher:    domain.publisher
  ageRating:    domain.ageRating
  genres:       domain.genres
  productUrl:   platform.url
  seriesId:     platform.platformSpecificId
  status:       attr.status
  keywords:     attr.keywords
  rating:       attr.rating
  viewCount:    attr.view_count
  commentCount: attr.comment_count

defaults:
  attr.view_count: 0
  attr.comment_count: 0

normalizers:
  master.masterTitle: [strip_parentheses, collapse_spaces, nfkc]
```

`rules/webtoon/naverwebtoon.yml`:
```yaml
platformName: NaverWebtoon
domain: WEBTOON
schemaVersion: 4

mappings:
  title:        master.masterTitle
  imageUrl:     master.posterImageUrl
  synopsis:     master.synopsis
  releaseDate:  master.releaseDate
  author:       domain.author
  status:       domain.status
  tags:         domain.genres
  weekday:      domain.weekday
  ageRating:    domain.ageRating
  productUrl:   platform.url
  titleId:      platform.platformSpecificId
  likeCount:    attr.like_count
  episodeCount: attr.episode_count

defaults:
  attr.like_count: 0
  attr.episode_count: 0

normalizers:
  master.masterTitle: [nfkc, strip_brackets, strip_series_qualifiers, collapse_spaces]
```

`rules/game/steam.yml`:
```yaml
platformName: Steam
domain: GAME
schemaVersion: 4

mappings:
  name:                  master.masterTitle
  header_image:          master.posterImageUrl
  short_description:     master.synopsis
  release_date:          master.releaseDate
  steam_appid:           platform.platformSpecificId
  developers[0]:         domain.developer
  publishers[0]:         domain.publisher
  platforms:             domain.osPlatforms
  genres:                domain.genres
  price_overview:        attr.price_overview
  metacritic:            attr.metacritic
  is_free:               attr.is_free
  required_age:          attr.required_age
  detailed_description:  attr.detailed_description
  categories:            attr.categories
  recommendations.total: attr.recommendation_count

defaults:
  attr.recommendation_count: 0

normalizers:
  master.masterTitle: [nfkc, strip_parentheses, collapse_spaces]
```

`rules/game/epic.yml`:
```yaml
# 휴면 룰: Epic RawItem 생산처(크롤러/executor) 없음. 수집 시작 전 매핑 재검토 필요.
# 구 v3의 data.platforms → domain.platforms 는 死매핑이라 제거 (엔진이 platforms를 항상 주입).
platformName: Epic
domain: GAME
schemaVersion: 4

mappings:
  data.title:                              master.masterTitle
  data.description:                        master.synopsis
  data.effectiveDate:                      master.releaseDate
  data.keyImages[0].url:                   master.posterImageUrl
  data.developer:                          domain.developer
  data.publisher:                          domain.publisher
  data.categories:                         domain.genres
  data.price.totalPrice.originalPrice:     attr.price_original
  data.price.totalPrice.discountPrice:     attr.price_discount
  data.price.totalPrice.currencyCode:      attr.currency

normalizers:
  master.masterTitle: [nfkc, collapse_spaces]
```

`rules/movie/tmdb_movie.yml`:
```yaml
platformName: TMDB_MOVIE
domain: MOVIE
schemaVersion: 4

mappings:
  title:            master.masterTitle
  original_title:   master.originalTitle
  poster_image_url: master.posterImageUrl
  overview:         master.synopsis
  release_date:     master.releaseDate
  movie_details.id: platform.platformSpecificId    # 구 BatchTransformService fallback 흡수
  genres:           domain.genres
  runtime:          domain.runtime
  directors:        domain.directors
  cast:             domain.cast
  watch_providers:  attr.watch_providers

normalizers:
  master.masterTitle:   [collapse_spaces, nfkc]
  master.originalTitle: [collapse_spaces, nfkc]

platformsFrom:
  - watch_providers
```

`rules/tv/tmdb_tv.yml`:
```yaml
platformName: TMDB_TV
domain: TV
schemaVersion: 4

mappings:
  name:               master.masterTitle
  original_name:      master.originalTitle
  poster_image_url:   master.posterImageUrl
  overview:           master.synopsis
  first_air_date:     master.releaseDate
  tv_details.id:      platform.platformSpecificId  # 구 BatchTransformService fallback 흡수
  genres:             domain.genres
  number_of_seasons:  domain.seasonCount
  episode_run_time:   domain.episodeRuntime
  cast:               domain.cast
  watch_providers:    attr.watch_providers

normalizers:
  master.masterTitle:   [collapse_spaces, nfkc]
  master.originalTitle: [collapse_spaces, nfkc]

platformsFrom:
  - watch_providers
```

- [ ] **Step 4: `IngestConfig` 작성 + 호출처 배선**

Create `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/IngestConfig.java`:
```java
package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.repository.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** ingest 파이프라인 배선. 컴포넌트들은 plain 클래스 — 여기서만 Spring에 연결된다. */
@Configuration
public class IngestConfig {

    @Bean
    public DomainCatalog domainCatalog(MovieContentRepository movieRepo, TvContentRepository tvRepo,
                                       GameContentRepository gameRepo, WebtoonContentRepository webtoonRepo,
                                       WebnovelContentRepository webnovelRepo) {
        return new DomainCatalog(movieRepo, tvRepo, gameRepo, webtoonRepo, webnovelRepo);
    }

    @Bean
    public DraftAssembler draftAssembler(DomainCatalog catalog) {
        return new DraftAssembler(catalog);
    }

    @Bean
    public RuleRegistry ingestRuleRegistry(DomainCatalog catalog) {
        return new RuleRegistry("classpath*:rules/**/*.yml", catalog);  // 기동 검증 = 부팅 게이트
    }

    @Bean
    public IngestPipeline ingestPipeline(RawItemRepository rawRepo, TransformRunRepository runRepo,
                                         RuleRegistry ingestRuleRegistry, DraftAssembler draftAssembler,
                                         DomainCatalog domainCatalog, ContentRepository contentRepo,
                                         PlatformDataRepository platformRepo, PlatformTransactionManager txManager) {
        return new IngestPipeline(rawRepo, runRepo, ingestRuleRegistry, draftAssembler,
                domainCatalog, contentRepo, platformRepo, new TransactionTemplate(txManager));
    }
}
```

`TransformSchedulingService.java` 수정 (3곳):
- 18행 `private final BatchTransformService batchTransformService;` → `private final IngestPipeline ingestPipeline;`
- 36행·68행 `batchTransformService.processBatch(batchSize)` → `ingestPipeline.processBatch(batchSize)`

`AdminTestController.java` 수정:
- 12–13행 import 두 줄 삭제, 45–46행 필드 두 개 → `private final IngestPipeline ingestPipeline;` 하나로, 62–63행 생성자 파라미터 동일 교체
- 502·638·665·680행 `batchService.processBatch(...)` → `ingestPipeline.processBatch(...)`
- 517행 `batchServiceOptimized.processBatchOptimized(batchSize)` → `ingestPipeline.processBatch(batchSize)`

- [ ] **Step 5: 구 코드 삭제**

```bash
git rm -- \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/service/TransformEngine.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/service/RuleRegistry.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/service/RuleLoader.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/service/GenericDomainUpserter.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/service/UpsertService.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/service/ContentUpsertService.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/service/DomainCoreUpsertService.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/service/similarity/ContentMergeService.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/service/similarity/ContentSimilarityService.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/BatchTransformService.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/BatchTransformServiceOptimized.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/rules/MappingRule.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/rules/DomainObjectMapping.java" \
  "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/rules/NormalizerStep.java" \
  "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/service/TransformEngineTest.java" \
  "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/service/RuleRegistryTest.java" \
  "-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/service/GenericDomainUpserterTest.java"
```

> `util/FlexibleDateParser.java`·`FlexibleDateParserTest.java`·`ingest/TransformRun.java`·`ingest/TransformRunRepository.java`는 **유지**.

- [ ] **Step 6: 잔여 참조 grep — 0건이어야 함**

```bash
grep -rn --include="*.java" -E "TransformEngine|GenericDomainUpserter|UpsertService|ContentUpsertService|DomainCoreUpsertService|ContentMergeService|ContentSimilarityService|BatchTransformService|MappingRule|DomainObjectMapping|NormalizerStep|RuleLoader" "-AOD-All-of-Dopamine-crawler/src" "-AOD-All-of-Dopamine-api/src" "-AOD-All-of-Dopamine-shared/src" || echo "OK: no stale references"
```
Expected: `OK: no stale references` (히트가 있으면 해당 파일도 배선/삭제 처리)

- [ ] **Step 7: `docs/3_TRANSFORM_ENGINE.md` §4 표를 v4로 교체**

기존 §4 표(fieldMappings/domainObjectMappings/valueMap 행 포함)를 다음으로 교체:

```markdown
## 4. YML 룰 스키마 v4 (선언 가능한 것들)

| 섹션 | 역할 | 예시 |
|---|---|---|
| `platformName` / `domain` | RuleRegistry 인덱스 키 (필수) | `NaverSeries` / `WEBNOVEL` |
| `mappings` | 원본 경로 → 목적지. 접두사가 저장 위치 결정: `master.*`=contents(프로퍼티명), `domain.*`=도메인 엔티티(프로퍼티명), `platform.*`=platform_data(프로퍼티명), `attr.*`=JSONB 키 리터럴 | `author: domain.author` |
| `defaults` | 원본 누락 시 채울 명시적 기본값 (key=목적지). 선언 없으면 스킵 | `attr.comment_count: 0` |
| `platformsFrom` | `platforms` 배열에 병합할 attr 키 목록 | `- watch_providers` |
| `normalizers` | master 프로퍼티별 정규화 파이프 (`nfkc`, `strip_parentheses`, `strip_brackets`, `collapse_spaces`, `strip_series_qualifiers`, `lowercase`) | `master.masterTitle: [nfkc]` |

> 목적지 프로퍼티명 오타·엔티티 rename 미반영은 **부팅 실패**로 잡힌다 (RuleRegistry 기동 검증).
> 구 v3의 `domainObjectMappings`/`valueMap`은 폐지 — 목적지가 프로퍼티명 직결이라 2중 매핑이 불필요.
> 런타임 트레이스는 [8_INGEST_PIPELINE_TRACE.md](8_INGEST_PIPELINE_TRACE.md) 참고(구 엔진 기준 — 갱신 예정).
```

- [ ] **Step 8: 크롤러 모듈 전체 테스트 → green**

Run: `./gradlew ":-AOD-All-of-Dopamine-crawler:test" --console=plain`
Expected: `BUILD SUCCESSFUL` — RuleFilesTest(골든 5) 포함 전부 통과, 컨텍스트 로드 테스트가 있다면 IngestConfig 배선·기동검증까지 검증됨

- [ ] **Step 9: Commit**

```bash
git add -A -- "-AOD-All-of-Dopamine-crawler" "docs/3_TRANSFORM_ENGINE.md"
git commit -m "refactor(ingest)!: cut over to typed pipeline — v4 rules, IngestConfig wiring, delete legacy engine (13 files)" -m "구 TransformEngine/Upsert 4형제/MergeService/BatchTransform×2 삭제. yml 7개 v4 이관. 동작 보존 + 배치 개선 3종(spec §5)." -m "Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 9: 최종 검증 — 전 모듈 빌드 + 문서/스펙 대조

**Files:**
- Modify: `docs/8_INGEST_PIPELINE_TRACE.md` (헤더에 구 엔진 기준임을 알리는 배너 1줄)

- [ ] **Step 1: 전체 빌드 (api·shared 포함 — 삭제 클래스 참조 없음 확인)**

Run: `./gradlew build -x test --console=plain && ./gradlew ":-AOD-All-of-Dopamine-crawler:test" --console=plain`
Expected: 둘 다 `BUILD SUCCESSFUL`

- [ ] **Step 2: 스펙 §9 성공 기준 체크리스트 대조**

- 패리티: `DraftAssemblerTest`(NaverSeries/TMDB_MOVIE) + `RuleFilesTest`(Steam/NaverWebtoon/KakaoPage/TMDB_TV/Epic) = 7 플랫폼 골든 ✅
- 조용한 유실 0: `RuleRegistryTest.bootFailsOnTypoDestinationProperty` ✅
- 파일/줄수: `wc -l` 로 ingest 신규 6파일 합계 ~650줄 이내 확인
```bash
wc -l "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/Values.java" "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/DomainCatalog.java" "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/DraftAssembler.java" "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/IngestPipeline.java" "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/rule/PlatformRule.java" "-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/ingest/rule/RuleRegistry.java"
```

- [ ] **Step 3: `docs/8_INGEST_PIPELINE_TRACE.md` 최상단에 배너 추가**

```markdown
> ⚠️ 이 문서는 **구(v3) yml 엔진** 기준 트레이스다. 2026-07 typed 파이프라인 재작성(feature/readable-ingest,
> [설계 스펙](superpowers/specs/2026-07-09-readable-ingest-rewrite-design.md)) 이후의 흐름은:
> `IngestPipeline` → `DraftAssembler`(+`RuleRegistry` v4) → `DomainCatalog`. 값 여정은 "yml 1줄 → 프로퍼티명 검색" 2홉.
```

- [ ] **Step 4: Commit**

```bash
git add -- "docs/8_INGEST_PIPELINE_TRACE.md"
git commit -m "docs: mark ingest trace doc as legacy-engine reference post-rewrite" -m "Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Self-Review 결과 (스펙 대조 완료)

- **스펙 커버리지**: §1 결정 전항목(Task 1~8) / §2 yml v4(Task 8 Step 3) / §3 6파일(Task 1~7) / §4 흐름(Task 6~7) / §5 에러처리 3개+기동검증(Task 5·6) / §6 테스트 5종(Task 4·5·6·7·8) / §7 삭제(Task 8 Step 5) / §9 성공기준(Task 9) — 갭 없음
- **타입 일관성**: `IngestDraft(content, domainEntity, platformData, boundDomainProps)` · `DomainCatalog.create/findByContentId/duplicateCandidates/save` · `RuleRegistry(locationPattern, catalog)` 시그니처가 Task 4~8에서 동일하게 사용됨
- **알려진 리스크 표기**: 병합 시 platforms 덮어쓰기(기존 이슈 보존, Task 7 주석) / `RawItem.isProcessed()` getter 명 확인 필요(Task 6 주석) / Gradle 데몬 락 이슈 발생 시 사용자에게 빌드 확인 위임(과거 이력)
