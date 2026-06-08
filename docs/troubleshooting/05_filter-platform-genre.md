# 05. 플랫폼 + 장르 복합 필터가 너무 느림 (N+1)

- **날짜**: 2026-06-07
- **영향**: `/api/works`에서 장르+플랫폼을 같이 걸면 응답이 수 초 이상 (간헐 타임아웃)
- **대상**: `-AOD-All-of-Dopamine-api/.../api/service/WorkApiService.java`, 5개 도메인 Repository
- **상태**: 🟡 코드 적용 (미배포)

---

## 1. 증상
- 장르 **단독** → 빠름
- 플랫폼 **단독** → 빠름
- 장르 **+** 플랫폼 (특히 GAME) → **수 초**, 심하면 타임아웃

## 2. 호출 경로 (Before)
`getWorks`가 조건에 따라 분기했다. 장르가 있으면 `getWorksByGenresWithDbFiltering`로 갔고, **이 메서드가 장르+플랫폼+키워드 복합을 인메모리로 처리**했다.

```java
public PageResponse<WorkSummaryDTO> getWorks(Domain domain, String keyword,
        List<String> platforms, List<String> genres, Pageable pageable) {

    // 장르 필터링이 있는 경우 - DB 레벨에서 처리
    if (genres != null && !genres.isEmpty()) {
        return getWorksByGenresWithDbFiltering(domain, keyword, platforms, genres, pageable); // ← 복합필터가 여기로
    }
    // 장르 없고 플랫폼만 - 메모리 필터링
    if (platforms != null && !platforms.isEmpty()) {
        return getWorksByPlatforms(domain, keyword, platforms, pageable);
    }
    // 필터링 없음
    return getWorksWithoutFiltering(domain, keyword, pageable);
}
```

## 3. 문제의 핵심: N+1 (이전 코드 전체)

### 3-1. `getWorksByGenresWithDbFiltering` (Before)
장르로 DB 후보(최대 2000)를 뽑고 → Content를 로드 → **플랫폼/키워드를 메모리에서 필터** → 인메모리 페이징.

```java
private PageResponse<WorkSummaryDTO> getWorksByGenresWithDbFiltering(
        Domain domain, String keyword, List<String> platforms, List<String> genres, Pageable pageable) {

    if (domain == null) {
        return getWorksWithoutFiltering(null, keyword, pageable);
    }
    String[] genreArray = genres.toArray(new String[0]);

    // 장르만 있으면 DB 페이징 fast path
    boolean hasKeyword = keyword != null && !keyword.isBlank();
    boolean hasPlatforms = platforms != null && !platforms.isEmpty();
    if (!hasKeyword && !hasPlatforms) {
        Pageable dbPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<?> domainPage = findDomainPageByGenres(domain, genreArray, dbPageable);
        // ... domainPage -> DTO 매핑 후 반환 (이 경로는 빠름)
    }

    // ▼▼▼ 복합(장르+플랫폼/키워드) 경로 — 여기가 문제 ▼▼▼
    List<Long> filteredContentIds = new ArrayList<>();

    // 1) 장르로 DB 필터 (최대 2000건만, GENRE_CANDIDATE_PAGEABLE = PageRequest.of(0, 2000))
    switch (domain) {
        case MOVIE:
            Page<MovieContent> moviePage =
                movieContentRepository.findByGenresContainingAll(genreArray, GENRE_CANDIDATE_PAGEABLE);
            filteredContentIds = moviePage.getContent().stream()
                    .map(MovieContent::getContentId).collect(Collectors.toList());
            break;
        // case TV / GAME / WEBTOON / WEBNOVEL — 동일 패턴 (테이블만 다름)
        // ...
        default:
            return /* empty */;
    }

    if (filteredContentIds.size() >= GENRE_FILTER_CANDIDATE_CAP) {
        log.warn("Genre-filter candidate set hit cap {} ... results may be truncated", GENRE_FILTER_CANDIDATE_CAP, domain);
    }
    if (filteredContentIds.isEmpty()) {
        return /* empty */;
    }

    // 2) 후보 Content 전체 로드
    List<Content> filteredContents = contentRepository.findByContentIdIn(filteredContentIds);

    // 3) 키워드 필터 (메모리)
    if (keyword != null && !keyword.isBlank()) {
        String lowerKeyword = keyword.toLowerCase();
        filteredContents = filteredContents.stream()
                .filter(c -> c.getMasterTitle().toLowerCase().contains(lowerKeyword)
                        || (c.getOriginalTitle() != null && c.getOriginalTitle().toLowerCase().contains(lowerKeyword)))
                .collect(Collectors.toList());
    }

    // 4) 플랫폼 필터 (메모리) — MOVIE/TV는 watchProviders, 나머지는 PlatformData
    if (platforms != null && !platforms.isEmpty()) {
        if (domain == Domain.MOVIE || domain == Domain.TV) {
            List<String> lowerPlatforms = platforms.stream().map(String::toLowerCase).collect(Collectors.toList());
            List<String> ottPlatforms = lowerPlatforms.stream().filter(OTT_WATCH_PROVIDERS::contains).collect(Collectors.toList());
            if (!ottPlatforms.isEmpty()) {
                filteredContents = filteredContents.stream()
                        .filter(c -> filterByWatchProviders(c, ottPlatforms))   // ← 콘텐츠마다 DB 조회
                        .collect(Collectors.toList());
            } else {
                filteredContents = filteredContents.stream()
                        .filter(c -> filterByPlatforms(c, platforms))            // ← 콘텐츠마다 DB 조회
                        .collect(Collectors.toList());
            }
        } else {
            filteredContents = filteredContents.stream()
                    .filter(c -> filterByPlatforms(c, platforms))                // ← 콘텐츠마다 DB 조회
                    .collect(Collectors.toList());
        }
    }

    // 5) 정렬 + 페이징 (메모리)
    return applyPaginationAndMapping(filteredContents, pageable);
}
```

### 3-2. N+1의 원흉 — 콘텐츠당 DB 조회 (Before)
위 4단계의 `filterByPlatforms` / `filterByWatchProviders`는 **콘텐츠 하나마다 `platform_data`를 쿼리**한다.

```java
private boolean filterByPlatforms(Content content, List<String> platforms) {
    if (platforms == null || platforms.isEmpty()) return true;
    // ⛔ 콘텐츠 1건당 SELECT * FROM platform_data WHERE content_id = ?
    List<PlatformData> platformDataList = platformDataRepository.findByContent(content);
    return platformDataList.stream()
            .anyMatch(pd -> platforms.stream()
                    .anyMatch(platform -> pd.getPlatformName().equalsIgnoreCase(platform)));
}

@SuppressWarnings("unchecked")
private boolean filterByWatchProviders(Content content, List<String> ottPlatforms) {
    if (ottPlatforms == null || ottPlatforms.isEmpty()) return true;
    // ⛔ 역시 콘텐츠 1건당 platform_data 조회
    List<PlatformData> platformDataList = platformDataRepository.findByContent(content);
    if (platformDataList.isEmpty()) return false;
    Set<String> contentWatchProviders = new HashSet<>();
    for (PlatformData pd : platformDataList) {
        Map<String, Object> attributes = pd.getAttributes();
        if (attributes != null && attributes.containsKey("watch_providers")) {
            Object wp = attributes.get("watch_providers");
            if (wp instanceof List) ((List<String>) wp).stream().map(String::toLowerCase).forEach(contentWatchProviders::add);
        }
    }
    return ottPlatforms.stream().anyMatch(contentWatchProviders::contains);
}
```

### 3-3. 왜 느린가 (단계별 비용)
1. 장르 후보 **최대 2000건** 로드 (`GENRE_CANDIDATE_PAGEABLE`)
2. 그 2000건의 Content를 `findByContentIdIn`으로 로드
3. **플랫폼 필터에서 콘텐츠당 `platform_data` 쿼리 → 최대 2000번 순차 실행 (N+1)**
4. 정렬·페이징도 메모리(`applyPaginationAndMapping`) → 사실상 2000건을 다 만지고 자름
5. `totalElements`도 2000 cap 기준이라 **총개수/총페이지가 부정확**

→ t3.small + HikariCP(api pool ~20)에서 2000회 순차 왕복 = 수 초. GAME처럼 장르 후보가 큰 도메인에서 특히 심함.

---

## 4. 핵심 발견 — 비정규화가 이미 돼 있었다
"플랫폼을 한 테이블 GIN 쿼리로 거를 토대"가 **이미 존재**했고, 서비스만 그걸 안 쓰고 있었다.

- **V1** `V1__create_genre_gin_indexes.sql` — 5개 도메인 `genres`에 GIN 인덱스
- **V3** `V3__add_platforms_to_domain_tables.sql` — 5개 테이블에 `platforms text[]` 추가 + **movie/tv는 OTT(`attributes->'watch_providers'`)를 platforms 배열에 UNION 병합** 백필 + GIN 인덱스
- **TransformEngine** (124~142줄) — 매 ingest마다 `platforms = [rule.platformName] + watch_providers`를 도메인 테이블에 기록 (대소문자 원형 유지: `Netflix`, `Disney Plus`)

즉 **OTT가 이미 `platforms` 배열의 원소**라서, `platform_data` 조인/JSONB 없이 `platforms @>`로 OTT까지 한 번에 거를 수 있었다.

---

## 5. 이후 (After)

### 5-1. Repository — 통합 단일 쿼리 (5개 도메인 동일 템플릿)
null이면 해당 조건을 스킵하는 "null-skip" 패턴. 장르·플랫폼 `@>`(AND), 키워드 ILIKE, ORDER BY 고정, **countQuery로 총개수까지 DB 처리.**
```java
@Query(value =
    "SELECT d.* FROM movie_contents d JOIN contents c ON d.content_id = c.content_id " +
    "WHERE (CAST(:genres AS text[]) IS NULL OR d.genres @> CAST(:genres AS text[])) " +
    "AND (CAST(:platforms AS text[]) IS NULL OR d.platforms @> CAST(:platforms AS text[])) " +
    "AND (CAST(:keyword AS text) IS NULL OR c.master_title ILIKE ('%' || :keyword || '%') " +
        "OR c.original_title ILIKE ('%' || :keyword || '%')) " +
    "ORDER BY c.release_date DESC NULLS LAST, d.content_id ASC",
    countQuery =
    "SELECT COUNT(*) FROM movie_contents d JOIN contents c ON d.content_id = c.content_id " +
    "WHERE (CAST(:genres AS text[]) IS NULL OR d.genres @> CAST(:genres AS text[])) " +
    "AND (CAST(:platforms AS text[]) IS NULL OR d.platforms @> CAST(:platforms AS text[])) " +
    "AND (CAST(:keyword AS text) IS NULL OR c.master_title ILIKE ('%' || :keyword || '%') " +
        "OR c.original_title ILIKE ('%' || :keyword || '%'))",
    nativeQuery = true)
Page<MovieContent> findWorks(@Param("genres") String[] genres,
                             @Param("platforms") String[] platforms,
                             @Param("keyword") String keyword,
                             Pageable pageable);
```

### 5-2. Service — 한 경로로 통합
`getWorksByGenresWithDbFiltering` 본문을 `findWorks` 호출로 교체. (이름은 유지했으나 이제 장르·플랫폼·키워드 모든 조합을 처리)
```java
private PageResponse<WorkSummaryDTO> getWorksByGenresWithDbFiltering(
        Domain domain, String keyword, List<String> platforms, List<String> genres, Pageable pageable) {
    if (domain == null) {
        return getWorksWithoutFiltering(null, keyword, pageable);
    }
    String[] genreArr    = (genres == null || genres.isEmpty())       ? null : genres.toArray(new String[0]);
    String[] platformArr = (platforms == null || platforms.isEmpty()) ? null : platforms.toArray(new String[0]);
    String kw            = (keyword == null || keyword.isBlank())     ? null : keyword;
    Pageable pageReq = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()); // Sort 제거(쿼리에 ORDER BY 고정)

    Page<?> page;
    switch (domain) {
        case MOVIE:    page = movieContentRepository.findWorks(genreArr, platformArr, kw, pageReq); break;
        case TV:       page = tvContentRepository.findWorks(genreArr, platformArr, kw, pageReq); break;
        case GAME:     page = gameContentRepository.findWorks(genreArr, platformArr, kw, pageReq); break;
        case WEBTOON:  page = webtoonContentRepository.findWorks(genreArr, platformArr, kw, pageReq); break;
        case WEBNOVEL: page = webnovelContentRepository.findWorks(genreArr, platformArr, kw, pageReq); break;
        default:       return emptyResponse();
    }
    List<WorkSummaryDTO> dtos = page.getContent().stream()
            .map(d -> domainToContent(domain, d)).filter(Objects::nonNull)
            .map(this::toWorkSummary).collect(Collectors.toList());
    return PageResponse.<WorkSummaryDTO>builder()
            .content(dtos)
            .page(page.getNumber()).size(page.getSize())
            .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
            .first(page.isFirst()).last(page.isLast())
            .build();
}
```

### 5-3. dispatch — 플랫폼 단독도 동일 경로로
```java
// Before: return getWorksByPlatforms(domain, keyword, platforms, pageable);
// After:
if (platforms != null && !platforms.isEmpty()) {
    return getWorksByGenresWithDbFiltering(domain, keyword, platforms, genres, pageable);
}
```

### 5-4. 제거된 것
- 인메모리 페이징 `applyPaginationAndMapping`(필터 경로), 후보 상한 `GENRE_FILTER_CANDIDATE_CAP` / `GENRE_CANDIDATE_PAGEABLE`, `findDomainPageByGenres`
- N+1의 `filterByPlatforms`/`filterByWatchProviders` 호출(필터 경로에서) — 두 메서드 자체는 `filterContentByPlatforms`(신작/예정 등)에서 아직 쓰여 유지
- `getWorksByPlatforms`는 이제 미사용(무해, 후속 정리)

---

## 6. 설계 결정 (왜 이 안인가)
독립적으로 받은 3가지 설계안(① 동적쿼리 빌더 ② id-셋 교집합 ③ 비정규화 단일쿼리)을 비교한 결과 **③**을 채택:
- 비정규화(V1/V3 + TransformEngine)가 이미 돼 있어 **대부분 코드를 삭제**하게 됨 (가장 단순/안전)
- JPA Repository + Spring `Page`(+countQuery) 유지 → 문자열 SQL 인젝션 표면(①)이나 앱-측 교집합 플러밍(②) 없음
- **장르·플랫폼 둘 다 AND(`@>`)** 로 통일 — 단일 연산자/단일 GIN 경로, 기존 단독 경로와 일관. (OR 필요 시 `&&`로 한 글자 교체)
- **OTT는 platforms 배열에 이미 포함** → platform_data 조인/JSONB 불필요 = N+1 원천 제거
- 페이징/총개수는 **DB countQuery**가 계산 → 인메모리 cap/subList 제거, 총개수 정확

## 7. 개선 효과
| 항목 | Before | After |
|------|--------|-------|
| 복합필터 DB 왕복 | 장르쿼리 + Content로드 + **최대 2000회 platform_data 쿼리** | **쿼리 1 + count 1** |
| 페이징/총개수 | 인메모리, 2000 cap, 총개수 부정확 | DB LIMIT/OFFSET + 정확한 count |
| OTT 필터 | 콘텐츠당 JSONB 파싱 | `platforms @>` (GIN) |
| 응답시간(복합, GAME) | 수 초~타임아웃 | 수십 ms (기대) |

## 8. 조회 커버리지와 한계 — findWorks로 "모두" 되는가?

**필터 조합은 다 된다.** `domain` 지정 시 `/api/works` 목록에서 장르·플랫폼·키워드의 모든 조합(OTT 포함)을 단일 findWorks 쿼리로 조회 가능. 그러나 아래 경계가 있다.

### ⚠️ 8-1. 정렬 옵션 무시 (잠재적 회귀)
`WorkController.getWorks`는 `sortBy`(기본 `masterTitle`)·`sortDirection`(기본 `asc`)으로 `Sort`를 만들어 Pageable에 담아 넘긴다:
```java
Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy)
                .and(Sort.by(Sort.Direction.ASC, "contentId"));
Pageable pageable = PageRequest.of(page, size, sort);
```
하지만 findWorks는 **`ORDER BY release_date DESC NULLS LAST, content_id ASC` 고정**이고, 서비스가 `PageRequest.of(page, size)`로 **Sort를 떼낸다** → **사용자 정렬이 무시되고 항상 출시일 내림차순.**
- 프론트가 보내는 `sortBy=releaseDate&sortDirection=desc`와는 **우연히 일치**(explore 기본 정렬은 정상 동작).
- 이름순·평점순·오름차순 등 **다른 정렬을 고르면 안 먹힘.**
- (참고: 옛 코드도 단일 fast-path는 정렬을 무시했고, 복합 인메모리 경로만 정렬을 적용 → 이미 비일관 상태였음)
- **임의 정렬이 필요하면**: findWorks에 **동적 ORDER BY(화이트리스트)** 추가 필요 — 예: `master_title` / `average_score` / `release_date` × asc/desc. (native 문자열 SQL이므로 컬럼명은 반드시 화이트리스트로만 허용해 인젝션 방지)

### 8-2. domain 필수
장르/플랫폼 배열은 도메인 자식 테이블(`*_contents`) 컬럼이라 **domain 없이는 장르/플랫폼 필터 불가**(크로스도메인 미지원). `domain=null`이면 필터를 무시하고 기본 목록을 반환.

### 8-3. `/api/works` 목록 전용
`/releases/recent`, `/releases/upcoming`, `/recent-reviews`는 **별도 서비스 경로**라 findWorks를 거치지 않고 여전히 `filterContentByPlatforms`(인메모리, 플랫폼 N+1 가능성)를 사용. **이 엔드포인트들의 플랫폼 필터는 아직 미개선.**

### 8-4. 기타
AND 의미(`@>`), OTT는 platforms 배열 신선도에 의존, 플랫폼 대소문자 정확 매칭, null-skip은 런타임 검증 필요 — §9 참고.

## 9. 주의 / 검증
- **플랫폼 대소문자**: `platforms` 배열은 원형 저장(`Netflix`/`Disney Plus`/`Steam`/`TMDB_MOVIE`). 프론트 키도 동일 형태여야 `@>` 매칭됨 → **새 쿼리에선 lowercase 금지**(기존 OTT 경로가 lowercase 하던 것 제거). 실제 저장값은 `SELECT DISTINCT unnest(platforms) FROM movie_contents` 로 한 번 확인 권장.
- **null-skip CAST 동작**: native 쿼리에 null 파라미터를 넘겨 `CAST(:x AS text[]) IS NULL`로 스킵하는 패턴은 **런타임(Hibernate 파싱)에서 확인** 필요 → 배포 후 실제 호출(예: 장르만 / 플랫폼만 / 둘 다 / +키워드)로 검증.
- **키워드** `ILIKE '%kw%'`는 인덱스 미사용 → 장르/플랫폼과 함께면 GIN이 좁힌 뒤 recheck라 OK. 키워드 단독이 무거우면 `pg_trgm` GIN 인덱스(후속 V4).
- `domain=null`(크로스도메인) 배열 필터는 미지원(기존 한계 유지).

## 10. 후속 작업
- [ ] 배포 후 조합별 동작/속도 실측
- [ ] (정렬 필요 시) findWorks에 **동적 ORDER BY 화이트리스트** 추가 — 현재 정렬 옵션 무시됨 (§8-1)
- [ ] (플랫폼 필터) `/releases/recent`·`/upcoming`·`/recent-reviews`도 동일 방식으로 이관 (§8-3)
- [ ] (선택) `getWorksByPlatforms` 등 죽은 코드 삭제 + `getWorksByGenresWithDbFiltering` 메서드명 정리(이제 장르 전용 아님)
- [ ] (선택) 키워드용 `pg_trgm` 인덱스
