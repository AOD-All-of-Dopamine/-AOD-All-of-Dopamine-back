# 06. 탐색 필터 전멸 — "필터가 없고, 걸어도 0건" (승격 잔재 2건)

- **날짜**: 2026-08-14
- **영향**: 프론트 탐색 페이지에서 장르 필터 그룹이 아예 안 뜨고, 장르로 필터하면 결과 0건
- **대상**: 로컬 DB(contents 테이블), `-AOD-All-of-Dopamine-api/.../service/WorkApiService.java`(@Cacheable), `application.properties`(spring.cache), 신설 `config/CacheConfig.java`
- **상태**: ✅ 커밋됨 (`3d280ac`, feature/crawler-standardization) + 로컬 DB 백필 완료. **배포 DB에도 백필 SQL 실행 필요**

---

## 1. 증상

프론트 라이트 리디자인(explore 이식) 후 로컬에서 확인하니:

- 필터 레일에 **장르 그룹이 아예 안 보임** (플랫폼만 표시)
- URL로 직접 `genres=RPG`를 걸어도 **결과 0건**
- 브라우저 콘솔: `GET /api/works/genres 500`

프론트 버그처럼 보였지만 프론트는 정상 — `useGenres(domain)`가 500을 받아 옵션이 비어 그룹을 렌더하지 않은 것.

## 2. 원인 — 서로 다른 두 개가 겹침 (둘 다 2026-07 genres/platforms 승격의 잔재)

### 원인 A: 승격 백필 SQL이 로컬 DB에 미실행 → 필터 결과 0건

genres/platforms를 도메인 테이블에서 `contents`로 승격할 때, **기존 행 백필은 수동 SQL**(`docs/sql/2026-07-promote-*.sql`)로 남겨뒀다. 배포 전 실행 항목이었는데 로컬 DB에도 실행된 적이 없었다.

```sql
-- 확인 쿼리: 1,062행 전부 비어 있었음
SELECT count(*) FILTER (WHERE array_length(genres,1) > 0) FROM contents;  -- 0
```

`ddl-auto=update`가 **컬럼은 자동으로 만들어주지만 데이터는 채워주지 않으므로**, API는 정상 기동·정상 응답하면서 필터만 조용히 0건을 반환했다.

### 원인 B: @Cacheable 캐시 미등록 → /genres 계열 500

`application.properties`(gitignore 대상)에:

```properties
spring.cache.type=simple
spring.cache.cache-names=traditional-recommendations,llm-recommendations
```

`cache-names`를 **고정 목록**으로 주면 동적 캐시 생성이 꺼진다. 승격 때 추가한 `@Cacheable("availableGenres")`/`@Cacheable("genresWithCount")`를 이 목록에 등록하지 않아:

```
IllegalArgumentException: Cannot find cache named 'availableGenres' for Builder[...getAvailableGenres...]
```

`/api/works/genres`, `/api/works/genres-with-count`가 전부 500. **설정 파일이 gitignore 대상이라 코드 리뷰에서 걸릴 수 없는 위치**에 있던 것이 근본 문제.

## 3. 왜 승격 시점(7월)에 못 잡았나

당시 게이트가 **빌드 + contextLoads + 골든 테스트**뿐이었다. `@Cacheable`은 프록시 기반이라 **첫 실제 HTTP 호출에서만** 캐시 조회가 발생 — contextLoads는 통과한다. 백필도 코드가 아닌 데이터 문제라 테스트로 안 잡힌다. 두 잔재 모두 "런타임 첫 사용"까지 잠복했고, 리디자인된 프론트가 이 API들을 처음 실사용하면서 드러났다.

## 4. 조치

| 원인 | 조치 |
|---|---|
| A. 백필 미실행 | 로컬 DB에 `2026-07-promote-{genres,platforms}-to-contents.sql` §1~3 실행 → genres 1,056/1,062·platforms 1,062/1,062 채움, `genres=RPG` 실조회 확인. **배포 DB에도 동일 실행 필요 (§4 DROP은 검증 후 별도)** |
| B. 캐시 미등록 | `config/CacheConfig.java` 신설 — `ConcurrentMapCacheManager`에 4개 캐시를 **코드로 고정** (`3d280ac`). 이 빈이 있으면 spring.cache.* 자동 설정이 물러나므로 로컬 properties 드리프트가 원천 차단됨. 새 @Cacheable 추가 시 이 클래스에 등록 |

## 5. 교훈

- **수동 마이그레이션은 "실행됐는지"가 상태다**: 백필 SQL을 문서에 남기는 것으로는 부족 — 로컬/스테이징/프로덕션 각각의 실행 여부를 추적해야 한다. 데이터가 비어 보이면 코드보다 먼저 "마이그레이션이 이 DB에 돌았나"부터 확인 (03의 stale 데이터 교훈과 같은 계열).
- **gitignore된 설정에 기능 동작을 의존시키지 말 것**: cache-names처럼 코드(@Cacheable)와 짝을 이루는 설정이 로컬별 파일에 있으면, 코드 변경 시 등록 누락이 리뷰로 걸리지 않는다. 짝이 있는 설정은 코드(@Configuration)로 소유.
- **@Cacheable·프록시류는 contextLoads로 검증되지 않는다**: 부팅 성공 ≠ 첫 호출 성공. 엔드포인트 스모크(curl 한 줄)를 게이트에 포함할 것.
