# 07. 탐색 페이지 스팀 리뷰 개수 필터가 너무 느림 (요청당 ≈17초)

- **날짜**: 2026-09-02
- **영향**: 탐색 페이지 게임 탭에서 `reviewCountMin`(스팀 리뷰 개수 하한) 필터를 걸면 응답이 수십 초. 페이지를 넘길 때마다 반복
- **대상**: `-AOD-All-of-Dopamine-shared/.../repository/ContentRepository.java`(`findWorks`, `WORKS_FILTER`), `-AOD-All-of-Dopamine-api/.../service/WorkApiService.java`(`getWorksWithDbFiltering`), 운영 RDS(PostgreSQL)
- **상태**: 🟡 **진단 완료 · 원인 확정 · ① 동적 조립 코드 적용 (PR #116, 미배포) — 배포 후 재측정 예정**

> 이 문서는 "쿼리가 왜 느린지"를 **실측(EXPLAIN ANALYZE)으로 확정**하기까지의 전 과정과, 그 과정에서 배운 진단 방법을 함께 기록한다. 같은 방식으로 다른 느린 쿼리도 진단할 수 있도록 절차를 재사용 가능한 형태로 남긴다.

---

## 1. 증상

- 탐색 페이지(`/explore`) 게임 탭에서 스팀 리뷰 개수 슬라이더로 필터 → 목록이 뜨기까지 "굉장히 오래" 걸림
- 같은 탭에서 필터 없이 진입하면 정상 속도
- 실측 결과(§4): **본 쿼리 8.57초 + count 쿼리 8.22초 = 요청 1회당 약 17초** (콜드 캐시 기준)

## 2. 호출 경로

```
브라우저 (탐색 페이지 useWorks 훅)
  → GET /api/works?domain=GAME&reviewCountMin=1000&page=0&size=20
    ① WorkController.getWorks          — 파라미터 수집, WorkFilters record로 묶음
    ② WorkApiService.getWorks           — filters.hasAny() ? 필터 경로 : 무필터 경로
    ③ getWorksWithDbFiltering           — Sort 제거(ORDER BY 고정) → contentRepository.findWorks()
    ④ buildSummaryPage → enrichSummaries — 20건에 자식 테이블·platform_data 배치 조회로 카드 정보 보강
    ⑤ PageResponse<WorkSummaryDTO> 반환
```

요청 1회당 나가는 쿼리:

| 순서 | 쿼리 | 비용 |
|---|---|---|
| 1 | `findWorks` 본 쿼리 (필터 + ORDER BY + LIMIT 20) | **병목** |
| 2 | `findWorks` count 쿼리 (같은 WHERE로 `COUNT(*)`) | **병목** |
| 3 | 도메인 자식 테이블 배치 조회 (`IN` 20건) | 가벼움 |
| 4 | `platform_data` 배치 조회 (`IN` 20건) | 가벼움 |

③ 단계의 특기 사항: 컨트롤러가 만든 `Sort`(sortBy/sortDirection)는 필터 경로에서 **버려진다**. 쿼리에 `ORDER BY release_date DESC`가 하드코딩되어 있기 때문 (05번 문서 §8-1에서 이미 지적된 잠재 회귀).

## 3. 문제의 쿼리 (Before)

`ContentRepository.findWorks` — 필터 축 8개를 **단일 네이티브 쿼리**로 처리한다. 각 축은 `(:param IS NULL OR 조건)` 스위치로 켜고 끈다.

```sql
SELECT c.* FROM contents c
WHERE c.domain = :domain
  AND c.is_adult = false
  AND (CAST(:genres AS text[]) IS NULL OR c.genres @> CAST(:genres AS text[]))           -- 장르 AND 포함
  AND (CAST(:platforms AS text[]) IS NULL OR c.platforms && CAST(:platforms AS text[]))  -- 플랫폼 OR 겹침
  AND (CAST(:keyword AS text) IS NULL OR c.master_title ILIKE ('%' || :keyword || '%')
       OR c.original_title ILIKE ('%' || :keyword || '%'))
  AND (CAST(:releaseFrom AS date) IS NULL OR c.release_date >= CAST(:releaseFrom AS date))
  AND (CAST(:releaseTo AS date) IS NULL OR c.release_date <= CAST(:releaseTo AS date))
  AND ((CAST(:status AS text) IS NULL AND CAST(:weekdays AS text[]) IS NULL AND CAST(:ageRatings AS text[]) IS NULL)
       OR EXISTS (SELECT 1 FROM webtoon_contents w WHERE w.content_id = c.content_id
           AND (CAST(:status AS text) IS NULL OR w.status = :status)
           AND (CAST(:weekdays AS text[]) IS NULL OR w.weekday = ANY(CAST(:weekdays AS text[])))
           AND (CAST(:ageRatings AS text[]) IS NULL OR w.age_rating = ANY(CAST(:ageRatings AS text[])))))
  AND (CAST(:reviewCountMin AS integer) IS NULL                                            -- ★ 문제의 축
       OR EXISTS (SELECT 1 FROM game_contents g WHERE g.content_id = c.content_id
           AND g.review_count >= CAST(:reviewCountMin AS integer)))
ORDER BY c.release_date DESC NULLS LAST, c.content_id ASC
-- Spring Data가 LIMIT 20 OFFSET n 을 붙이고, countQuery = 같은 WHERE로 SELECT COUNT(*)
```

구조 요약:
- `:param IS NULL OR …` — 필터를 안 쓰면 NULL이 바인딩되어 `IS NULL`이 참 → 그 축은 전체 통과. 자바에서 조건을 조립하지 않아도 되게 하려는 편의 설계
- `@>`(포함, AND 의미) / `&&`(겹침, OR 의미)는 PostgreSQL 배열 연산자 — `idx_contents_genres/platforms` GIN 인덱스 대상
- 웹툰 3축·게임 리뷰 축은 `contents`에 없는 자식 테이블 컬럼이라 **상관 EXISTS 서브쿼리**로 검사
- `reviewCountMin`은 2026-08 Steam 정제 때 웹툰 EXISTS 패턴을 그대로 복사해 추가됨

## 4. 진단 과정 (실측)

### 4-1. 인덱스 현황 (`pg_indexes`)

| 테이블 | 인덱스 | 정의 |
|---|---|---|
| contents | contents_pkey | btree (content_id) |
| contents | idx_contents_lookup | btree (domain, master_title, release_date) |
| contents | idx_contents_genres / platforms | gin (genres) / gin (platforms) |
| game_contents | game_contents_pkey | btree (content_id) |
| game_contents | idx_game_genres / platforms | gin |

→ **`game_contents.review_count`에는 인덱스가 없다.** `ORDER BY release_date`를 지원하는 인덱스도 없다(`idx_contents_lookup`은 domain 다음이 master_title이라 release_date 순서를 못 준다).

### 4-2. 리터럴 버전 측정 — 베스트 케이스 (113ms)

값을 직접 박은 쿼리(`review_count >= 1000`)로 `EXPLAIN (ANALYZE, BUFFERS)`:

```
Limit (actual time=69.7..112.6 rows=20)
  Buffers: shared hit=39900              ← read 0: 전부 캐시
  -> Gather Merge (Workers 2)
     -> Sort (top-N heapsort, 61kB)
        -> Nested Loop (rows=2919 ×3 loops)
           -> Parallel Seq Scan on game_contents g  (Filter: review_count >= 1000)
                 Rows Removed by Filter: 57893 (×3)  ← 인덱스 없어 18만 행 풀스캔
           -> Index Scan using contents_pkey on contents c (loops=8759)
Execution Time: 113.328 ms
```

플래너가 `1000 IS NULL`을 계획 시점에 거짓으로 접고(constant folding) EXISTS를 **세미조인으로 변환** → "리뷰 1000+ 게임 8,759건을 먼저 찾고 contents를 PK로 역조회"하는 좋은 플랜. count 쿼리도 같은 형태로 **115ms**.

**⚠ 함정**: 이 숫자는 (a) 플래너가 값을 알고 최적화한 베스트 케이스이고 (b) 캐시가 완전히 데워진 상태다. "느리다"는 제보와 안 맞으면 **측정이 실제 조건을 재현하지 못한 것**을 의심해야 한다.

### 4-3. 실제 조건 재현 시도 — 측정 사고와 검출

실제 앱은 JDBC가 값을 **바인딩 파라미터**로 보낸다. 이를 재현하려고 `PREPARE fw($1…$10) … ; SET plan_cache_mode = force_generic_plan; EXPLAIN EXECUTE fw(…)`를 실행했더니:

```
Result  One-Time Filter: false   rows=0   Execution Time: 0.037 ms
```

"조건이 항상 거짓 → 0건". 그러나 count로 8,760건 매칭을 이미 확인한 상태라 **논리적으로 불가능한 결과**. `pg_prepared_statements`로 서버에 실제 등록된 문장을 조회하니:

```
WHERE c.domain = NULL AND … CAST(NULL AS text[]) IS NULL …
```

**DB 클라이언트 툴(DBeaver)이 `$1`~`$10`을 자기 변수로 해석해 전부 `NULL`로 치환한 뒤 서버에 보낸 것.** `SHOW plan_cache_mode`도 `auto`로 나와 SET이 적용되지 않았음을 확인(문장별 커넥션 분리 의심). → 이 측정은 전부 무효.

교훈: **결과가 논리적으로 불가능하면 측정 도구부터 의심**하고, "서버가 실제로 받은 것"(`pg_prepared_statements`, `SHOW`)을 확인한다.

### 4-4. 툴 독립적 재현 — `(SELECT 값)` 트릭 (8.5초)

값을 스칼라 서브쿼리로 감싸면 플래너가 상수로 접지 못하고 런타임 값(InitPlan)으로 취급한다. `$N`·PREPARE·SET이 필요 없어 어떤 툴에서든 안전하다:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.* FROM contents c
WHERE c.domain = 'GAME' AND c.is_adult = false
  AND ((SELECT 1000) IS NULL
       OR EXISTS (SELECT 1 FROM game_contents g
                  WHERE g.content_id = c.content_id AND g.review_count >= (SELECT 1000)))
ORDER BY c.release_date DESC NULLS LAST, c.content_id ASC
LIMIT 20 OFFSET 0;
```

결과 — **이것이 실제 앱 경로의 플랜**:

```
Limit (actual time=8569.7 rows=20)
  Buffers: shared hit=10133 read=12425            ← 1.2만 페이지 디스크 읽기
  I/O Timings: shared read=8116.379               ← 전체 8573ms 중 95%
  InitPlan 1 -> Result                            ← (SELECT 1000)
  -> Sort (top-N heapsort, 59kB)
     -> Bitmap Heap Scan on contents c (rows=8758)
          Recheck Cond: domain = 'GAME'
          Filter: (NOT is_adult) AND ((InitPlan 1) IS NULL OR (ANY (content_id = (hashed SubPlan 5))))
          Rows Removed by Filter: 173680          ← 18.8만 행 읽고 17.4만 버림
          Heap Blocks: exact=15862
          -> Bitmap Index Scan on idx_contents_lookup (rows=187949)   ← GAME 인덱스 항목 18.8만
          SubPlan 5 -> Seq Scan on game_contents g (28.8ms, rows=8759)  ← 1회 실행 후 해시
Execution Time: 8572.900 ms
```

count 쿼리(같은 방식): **8215.5ms**, `read=13104` — 본 쿼리 **직후**인데도 또 디스크에서 읽음.

### 4-5. 비교표

| | 본 쿼리 | count | 합계 | 플랜 형태 | Buffers |
|---|---|---|---|---|---|
| 리터럴 (베스트) | 113ms | 115ms | ≈0.23s | game_contents → contents 역조회 (세미조인) | hit 39,900 / read 0 |
| **바인딩 재현 (실제)** | **8,573ms** | **8,215ms** | **≈17s** | contents GAME 전수 스캔 + 행별 OR 필터 | hit 10,133 / **read 12,425** |

### 4-6. 전체 쿼리 재현 — 앱이 보내는 그 SQL (2026-09-14 추가 실측)

§4-4는 리뷰 축 하나만 넣은 단순화 재현이었다. `WORKS_FILTER`의 `:param` 10개 전부를 `(SELECT 값)`으로 치환해
**스위치 8개가 모두 봉투 상태**인 실제 앱 SQL을 재현했다 (`= ANY(...)`는 서브쿼리 형태로 해석되므로 원문처럼 `ANY(CAST((SELECT …) AS text[]))`로 감싸야 한다).

| | 본 쿼리 | count |
|---|---|---|
| Execution Time | **9,872ms** | **9,962ms** |
| Buffers | hit 718,629 / read 18,035 | hit 707,221 / read 15,955 |
| I/O Timings | 8,857ms | 9,124ms |
| 출발점 | Bitmap Index Scan on idx_contents_lookup (domain = InitPlan, rows=191,067) | 동일 |
| Rows Removed by Filter | 176,797 | 176,797 |
| 플래너 예상 rows | **1** (실제 8,758) | 1 |

§4-4와 다른 점 두 가지:

1. **리뷰 EXISTS가 `hashed SubPlan`이 아니라 행별 인덱스 프로브로 실행됐다.**
   ```
   SubPlan 32 -> Index Scan using game_contents_pkey on game_contents g   loops=176292
                 Buffers: shared hit=715007 read=3662
   ```
   스위치 8개의 기본 통과율이 곱해지며 플래너가 "살아남는 행 = 1"로 추정 → "1행이면 해시보다 프로브 한 번이 싸다"고 판단 → 실제 17만 6천 번 프로브. 단순화 재현(스위치 1개, 예상 110,882행)에서는 해시가 선택돼 29ms였다. **`IS NULL OR`의 두 번째 해악 = 추정 붕괴가 세부 전략까지 잘못 고르게 한다.** 단순화한 재현은 실제보다 온화할 수 있다.
2. **캐시 잔존의 비대칭.** 본 쿼리 직후 count에서 contents는 다시 `read 15,955`(캐시에서 밀려남), game_contents는 `read 1 / hit 705,447`(잔존). GAME 구간 130MB+는 RDS 캐시에 안 담기고 40MB 남짓인 game_contents는 담긴다.

꺼진 스위치 7개는 `IS NULL`이 참이라 OR 오른쪽이 평가되지 않음(`InitPlan … (never executed)`, `SubPlan 22 (never executed)`) — **문제는 스위치 개수가 아니라 켜진 스위치 하나를 플래너가 다룰 수 없는 구조**다.

## 5. 원인 (확정)

세 요인의 연쇄다. 우선순위 순:

### 5-1. 구조: `IS NULL OR`가 플래너에게서 "적게 읽는 작전"을 빼앗는다

플래너는 실행 전에 계획을 확정해야 하는데, 바인딩 파라미터 상황에서는 `:reviewCountMin`이 NULL(전원 통과)일지 1000(8,759건만 통과)일지 **계획 시점에 모른다**. 두 경우 모두 정답인 유일한 계획은 **"GAME 행을 전부 읽고 행마다 OR을 평가"**하는 것뿐이다.

- AND 조건은 "못 만족하면 탈락"이라 읽을 범위를 미리 좁힐 수 있지만, OR 조건은 "다른 쪽으로 통과 가능"이라 어느 한쪽으로도 범위를 못 좁힌다 → EXISTS의 세미조인 변환, 인덱스 범위 축소가 전부 봉인된다.
- 서브쿼리 실행 방식은 추정치에 따라 갈린다: 단순화 재현(§4-4)에서는 `hashed SubPlan`(1회 스캔 + 해시 대조, 29ms)이었지만 **실제 앱 SQL(§4-6)에서는 추정 붕괴(예상 1행)로 행별 인덱스 프로브 176,292회**가 선택됐다(I/O ≈1초 + CPU ≈1.6초). 그래도 시간의 본체는 강제된 **contents 18.8만 행(약 180MB) 전수 읽기**(I/O 8초대)다.
- 이 패턴은 필터 축 8개 전부에 동일하게 존재한다(장르 `@>`도 generic plan에서는 GIN을 못 탄다). 리뷰 축이 제일 큰 도메인(GAME 18.8만)에서 먼저 터진 것뿐.

### 5-2. 환경: 강제된 전수조사가 캐시를 넘어 디스크를 때린다

- GAME 도메인의 heap + 인덱스 발자국 ≈ 180MB > 운영 RDS의 shared_buffers
- `read=12,425` 페이지 × ≈0.65ms = **8.1초** (전체의 95%)
- 직후 count 쿼리도 `read=13,104` → 방금 읽은 페이지가 이미 밀려남 = **매 요청이 콜드**

### 5-3. 부차 요인

- `game_contents.review_count` 인덱스 부재 (풀스캔 29ms — 현재는 작지만 좋은 플랜에서는 4,830페이지 콜드 읽기가 됨)
- `Page` 반환 → count 쿼리가 같은 비용을 매번 반복 (×2)
- `ORDER BY release_date` 지원 인덱스 부재 → 항상 Sort (LIMIT 덕에 top-N heapsort라 비용은 작음)
- 플래너 행 수 추정 오차 12배 (예상 110,882 vs 실제 8,758) — OR 필터의 선택도를 기본값으로 추정

## 6. "인덱스만 추가하면 안 되나?" — 안 된다

| 조치 | 효과 | 이유 |
|---|---|---|
| `game_contents(review_count)` 인덱스 단독 | **≈0.03초 절약** | 나쁜 플랜에서 인덱스가 건드리는 부분은 이미 29ms짜리 SubPlan뿐. 8.1초 디스크 I/O(contents 전수 스캔)는 그대로 |
| `contents(domain, release_date DESC)` 인덱스 단독 | 본 쿼리만 부분 개선, **count는 그대로 8초** | "최신순으로 걷다 20건 차면 중단"이 가능해지지만 필터가 희소하면 역효과(도박). count는 조기 종료가 없다 |
| **구조 수정 + 인덱스** | 완성형 | EXISTS가 최상위 AND가 되어 플래너가 "조건 맞는 8,759건에서 출발" 가능 → 그때 review_count 인덱스가 풀스캔을 인덱스 점프로 바꿔줌 |

인덱스는 "좋은 플랜이 선택된 다음" 각 단계를 가속하는 증폭기이지, 나쁜 플랜을 좋은 플랜으로 바꾸지 못한다. 또한 크롤러가 대량 쓰기하는 시스템이라 인덱스 유지비도 무시할 수 없다.

## 7. 동적 쿼리 도구 검토 (QueryDSL 등)

**어떤 도구든 성능 원리는 같다 — "켜진 조건만 SQL에 넣는다".** 차이는 타입 안전성·가독성·의존성 비용.

| | 타입 안전 | 배열 연산자(`@>`, `&&`) | 도입 비용 | 적합도 |
|---|---|---|---|---|
| 조건부 문자열 조립 (custom repository + native) | ✗ | ◎ 그대로 | 0 | **◎** |
| QueryDSL | ◎ | △ Hibernate 함수 등록 + `Expressions.template` 우회 | 중 (annotation processor, jakarta classifier) | ○ |
| Spring Data Specification / Criteria | △ | △ 동일 우회, 코드 장황 | 소 | ✗ |
| MyBatis 동적 XML | ✗ | ◎ | 대 (별도 영속성 스택) | ✗ |
| jOOQ | ◎ | ◎ 네이티브 지원 | 대 (코드 생성, 학습) | △ |

결정: **조건부 문자열 조립**. 이 쿼리의 정체성이 PostgreSQL 배열 연산자 + EXISTS + NULLS LAST라는 네이티브 SQL이라, QueryDSL을 들이면 핵심 조건을 전부 우회 템플릿으로 써야 해 타입 안전성 이득이 반감된다. 값은 반드시 바인딩 파라미터로만(인젝션 방지), 오타 리스크는 필터 조합별 통합 테스트로 메꾼다. 배열 연산자 없는 복잡한 동적 쿼리(관리자 검색·통계)가 늘어나면 그때 QueryDSL 도입을 재검토.

## 8. 개선안 설계 (After — 미구현)

| 축 | 조치 | 효과 | 의존 |
|---|---|---|---|
| ① 쿼리 구조 ✅ 적용 | `IS NULL OR` 제거. `WorksQueryBuilder`(순수 조립기, 단위 테스트 9건) + `ContentRepositoryCustom/Impl`(EntityManager 실행). 켜진 축만 조건부 append + 바인딩. 구 `WORKS_FILTER`/`@Query findWorks` 삭제 | 나쁜 플랜 원천 차단 (8.5s → 웜 0.1s급). 8개 축 전부 혜택 | 없음 (본체) |
| ② 데이터 모델 | `review_count`를 `contents`로 승격 + `(domain, review_count)` btree. 2026-07 genres/platforms 승격과 동일 플레이북. Ingest YAML `master.*` 한 줄 | EXISTS 소멸, 콜드에서도 후보 즉시 선별 | ① 이후 |
| ③ 정렬 인덱스 | `CREATE INDEX … ON contents (domain, release_date DESC NULLS LAST, content_id) WHERE is_adult = false` | 무필터/약필터 경로까지 "20건 차면 중단" 가능 | 독립 |
| ④ 페이징 | `Page` → `Slice` (21건 요청, 다음 페이지 유무만) | 요청당 쿼리 ×2 → ×1. CLAUDE.md "대용량 조회는 Slice" 규칙과 일치 | 프론트가 전체 건수를 쓰는지 확인 |

①의 코드 형태:

```java
StringBuilder sql = new StringBuilder(
    "SELECT c.* FROM contents c WHERE c.domain = :domain AND c.is_adult = false");
Map<String, Object> params = new HashMap<>();
params.put("domain", domain);
if (filters.genres() != null) {
    sql.append(" AND c.genres @> CAST(:genres AS text[])");
    params.put("genres", toArr(filters.genres()));
}
if (filters.reviewCountMin() != null) {           // ② 승격 후에는 " AND c.review_count >= :rcMin"
    sql.append(" AND EXISTS (SELECT 1 FROM game_contents g")
       .append("  WHERE g.content_id = c.content_id AND g.review_count >= :rcMin)");
    params.put("rcMin", filters.reviewCountMin());
}
// … 나머지 축 동일 패턴 …
sql.append(" ORDER BY c.release_date DESC NULLS LAST, c.content_id ASC");
```

원칙: "**DB에는 실제로 켜진 조건만 보낸다.**" 웹툰 3축은 행 수가 작아 EXISTS로 두어도 된다(아픈 축만 승격).

진행 순서 제안: ② 마이그레이션(컬럼 추가·백필·인덱스) → ① 리포지토리 개편 → ④ Slice → §4-4 방식으로 **EXPLAIN 재측정해 효과를 숫자로 확인**.

## 9. 재사용 진단 체크리스트

1. 파라미터 이름으로 grep → 컨트롤러부터 리포지토리까지 **실행되는 쿼리 특정**
2. `pg_indexes`로 **인덱스 현황** 확보
3. `EXPLAIN (ANALYZE, BUFFERS)` 리터럴 버전 → **베스트 케이스** 기록
4. 파라미터 쿼리면 `(SELECT 값)` 트릭으로 **실제 케이스** 측정 (툴이 `$N`을 먹는 것 주의)
5. 플랜에서 `Rows Removed` / `Buffers hit vs read` / `I/O Timings` / SubPlan·Sort 노드 확인
6. 두 플랜을 비교해 **구조 문제 vs 환경 문제 분리** → 그다음에야 수선 논의
7. 결과가 논리적으로 불가능하면 측정 도구부터 의심 (`pg_prepared_statements`, `SHOW`)

## 10. 교훈

- **베스트 케이스 측정의 함정**: 리터럴 EXPLAIN(113ms)만 믿었다면 "쿼리는 빠른데?"로 끝났을 것. 제보와 측정이 안 맞으면 측정 조건을 의심한다.
- **원인은 연쇄다**: 구조(`IS NULL OR`) → 과잉 읽기(20배) → 캐시 초과 → 디스크. 어느 한 고리만 보면 "인덱스 추가"나 "RDS 증설" 같은 반쪽 처방이 나온다.
- **편의 설계의 대가**: 단일 쿼리 + NULL 스위치는 자바를 단순하게 했지만 그 비용을 DB 플래너에 떠넘겼다. 05번 문서에서 통합 쿼리로 N+1을 잡은 것은 옳았고, 이번 문제는 그 통합 방식의 다음 단계(조건부 조립)를 요구한다.
- **측정 도구도 시스템의 일부**: 클라이언트 툴의 파라미터 치환 하나로 두 번의 측정이 무효가 됐다.
