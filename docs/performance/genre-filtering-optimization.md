# 장르 필터링 성능 최적화

## 개요

장르 필터링 로직을 메모리 기반 필터링에서 DB 레벨 필터링으로 변경하여 성능을 최적화했습니다.

## 변경 사항

### Before (메모리 필터링)
```java
// 모든 데이터를 메모리에 로드
List<Content> allContent = contentRepository.findAll(Pageable.unpaged()).getContent();

// Java Stream으로 필터링
allContent.stream()
    .filter(c -> filterByGenres(c, genres))  // 각 항목마다 DB 조회
    .collect(Collectors.toList());
```

**문제점:**
- 전체 데이터를 메모리에 로드 (데이터가 많을수록 메모리 부담)
- 각 컨텐츠마다 장르 정보를 가져오기 위해 추가 DB 조회 발생 (N+1 문제)
- DB 인덱스 활용 불가

### After (DB 레벨 필터링)
```java
// PostgreSQL text[] 배열 쿼리로 필터링
@Query(value = "SELECT m.* FROM movie_contents m " +
       "WHERE m.genres @> CAST(:genres AS text[])",
       nativeQuery = true)
Page<MovieContent> findByGenresContainingAll(@Param("genres") String[] genres, Pageable pageable);
```

**장점:**
- DB 레벨에서 필터링 (GIN 인덱스 활용)
- 필요한 데이터만 메모리에 로드
- N+1 문제 해결
- 페이징 효율성 향상

## 필터링 로직

### AND 조건
선택된 **모든 장르**를 포함하는 작품만 반환합니다.

예: `genres=["액션", "코미디"]` 선택 시
- ✅ 장르: ["액션", "코미디", "드라마"] → 포함
- ❌ 장르: ["액션", "드라마"] → 제외 (코미디 없음)
- ❌ 장르: ["코미디"] → 제외 (액션 없음)

### PostgreSQL 배열 연산자
- `@>` : 배열이 모든 요소를 포함하는지 확인 (AND 조건)
- `&&` : 배열이 하나 이상의 공통 요소를 가지는지 확인 (OR 조건)

## 인덱스 설정

### GIN 인덱스 생성

```sql
-- 모든 도메인의 장르 컬럼에 GIN 인덱스 생성
CREATE INDEX idx_movie_genres ON movie_contents USING GIN (genres);
CREATE INDEX idx_tv_genres ON tv_contents USING GIN (genres);
CREATE INDEX idx_game_genres ON game_contents USING GIN (genres);
CREATE INDEX idx_webtoon_genres ON webtoon_contents USING GIN (genres);
CREATE INDEX idx_webnovel_genres ON webnovel_contents USING GIN (genres);
```

### 인덱스 확인

```sql
-- 인덱스 생성 확인
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE indexname LIKE 'idx_%_genres';

-- 인덱스 사용 통계
SELECT 
    tablename,
    indexname,
    idx_scan as scans,
    idx_tup_read as tuples_read
FROM pg_stat_user_indexes
WHERE indexname LIKE 'idx_%_genres'
ORDER BY idx_scan DESC;
```

## 성능 테스트

### 1. API를 통한 성능 테스트

```bash
# 장르 필터링 테스트 (실제 작동하는 엔드포인트)
curl "http://localhost:8080/api/works?domain=MOVIE&genres=액션&genres=코미디&page=0&size=20"

# 응답 시간 측정
time curl "http://localhost:8080/api/works?domain=MOVIE&genres=액션&genres=SF&page=0&size=20"

# 다양한 도메인 테스트
curl "http://localhost:8080/api/works?domain=TV&genres=드라마&genres=로맨스&page=0&size=20"
curl "http://localhost:8080/api/works?domain=GAME&genres=액션&genres=RPG&page=0&size=20"
curl "http://localhost:8080/api/works?domain=WEBTOON&genres=판타지&genres=액션&page=0&size=20"
```

### 2. 직접 SQL 성능 측정

```sql
-- 실행 계획 확인 (인덱스 사용 여부)
EXPLAIN ANALYZE 
SELECT * FROM movie_contents 
WHERE genres @> ARRAY['액션', '코미디']::text[];

-- 결과 예시 (인덱스 사용 시)
-- Bitmap Heap Scan on movie_contents  (cost=12.00..16.01 rows=1 width=...)
--   Recheck Cond: (genres @> '{액션,코미디}'::text[])
--   ->  Bitmap Index Scan on idx_movie_genres  (cost=0.00..11.99 rows=1 width=0)
--         Index Cond: (genres @> '{액션,코미디}'::text[])
```

### 3. 성능 비교 시나리오

#### 시나리오 1: 데이터 1,000개, 장르 2개 선택

**Before (메모리 필터링):**
- 전체 1,000개 로드
- 각 항목마다 장르 정보 조회 (1,000번 추가 쿼리)
- 예상 시간: 500-1000ms

**After (DB 필터링):**
- 조건에 맞는 데이터만 조회
- 단일 쿼리로 완료
- 예상 시간: 10-50ms (인덱스 사용 시)

#### 시나리오 2: 데이터 10,000개, 장르 3개 선택

**Before:**
- 전체 10,000개 로드
- 10,000번 추가 쿼리
- 예상 시간: 5,000-10,000ms (5-10초)

**After:**
- 조건에 맞는 데이터만 조회
- 단일 쿼리
- 예상 시간: 20-100ms

## 테스트 체크리스트

### ✅ 기능 테스트
- [ ] 단일 장르 필터링 동작 확인
- [ ] 복수 장르 필터링 동작 확인 (AND 조건)
- [ ] 도메인별 필터링 확인 (MOVIE, TV, GAME, WEBTOON, WEBNOVEL)
- [ ] 페이징 동작 확인
- [ ] 장르 없는 경우 처리 확인

### ✅ 성능 테스트
- [ ] 인덱스 생성 확인
- [ ] EXPLAIN ANALYZE로 인덱스 사용 확인
- [ ] 응답 시간 측정 (before/after 비교)
- [ ] 동시 요청 처리 테스트

### ✅ 엣지 케이스
- [ ] 존재하지 않는 장르 입력 시
- [ ] 빈 장르 리스트 입력 시
- [ ] 매우 많은 장르 선택 시
- [ ] 도메인 없이 장르 필터링 시 (현재는 기본 조회)

## 모니터링

### 쿼리 성능 모니터링

```sql
-- 느린 쿼리 확인
SELECT 
    query,
    calls,
    total_time,
    mean_time,
    max_time
FROM pg_stat_statements
WHERE query LIKE '%genres%@>%'
ORDER BY mean_time DESC
LIMIT 10;
```

### 인덱스 효율성 모니터링

```sql
-- 인덱스 사용률
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
WHERE indexname LIKE 'idx_%_genres';
```

## 향후 개선 방향

1. **캐싱 추가**
   - 자주 사용되는 장르 조합 결과 캐싱
   - Redis 활용

2. **전문 검색 엔진 도입**
   - Elasticsearch 도입 검토
   - 복잡한 필터링 및 검색 성능 향상

3. **추가 인덱스 최적화**
   - 복합 인덱스 검토 (domain + genres)
   - 부분 인덱스 활용

4. **쿼리 최적화**
   - 플랫폼 필터링도 DB 레벨로 이동
   - JOIN 최적화

## 롤백 계획

문제 발생 시 이전 버전으로 롤백:

1. WorkApiService.java의 filterByGenres 메서드가 @Deprecated로 남아있음
2. 필요시 getWorks 메서드를 이전 로직으로 복원
3. 인덱스는 유지 (다른 쿼리에도 도움이 될 수 있음)

## 참고 자료

- [PostgreSQL 배열 타입](https://www.postgresql.org/docs/current/arrays.html)
- [PostgreSQL 배열 함수와 연산자](https://www.postgresql.org/docs/current/functions-array.html)
- [GIN 인덱스 성능](https://www.postgresql.org/docs/current/gin-intro.html)
- [PostgreSQL 배열 인덱싱](https://www.postgresql.org/docs/current/indexes-types.html)
