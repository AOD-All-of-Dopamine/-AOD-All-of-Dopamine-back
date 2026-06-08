# 02. "CORS 에러"의 정체 = API 502 (genres-with-count 과부하)

- **날짜**: 2026-06-05
- **영향**: 프론트(`allofdophamin.com`)에서 페이지 전환 시 전 API가 간헐적으로 실패. 브라우저엔 CORS 에러로 표시.
- **상태**: ✅ 수정·배포됨 (commit `9665d37`)

## 증상
```
Access to XMLHttpRequest at 'https://api.allofdophamin.com/api/works/genres-with-count?domain=GAME'
  ... has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header ...
GET ... net::ERR_FAILED
```
모든 엔드포인트가 동시에 `ERR_FAILED` + "No ACAO header".

## 진단 (증상 ≠ 원인)
1. 라이브 서버를 직접 curl → **CORS 헤더는 정상**으로 내려옴(`Access-Control-Allow-Origin: https://allofdophamin.com`). 즉 CORS 설정 문제 아님.
2. 부하 재현 — `genres-with-count?domain=GAME`를 연속 호출하니 **20초 타임아웃 → 502**, 그 직후 **rankings 포함 전 엔드포인트가 502**로 전염.
3. 결론: **API가 과부하로 다운 → nginx가 502(ACAO 헤더 없음) 반환 → 브라우저가 CORS로 오표시.**

## 원인
[`WorkApiService.getGenresWithCount`](../../-AOD-All-of-Dopamine-api/src/main/java/com/example/AOD/api/service/WorkApiService.java) → `addGenreCountsForDomain`이 **도메인 테이블 전체를 `findAll()`로 메모리 적재 후 Java에서 장르 카운트**. GAME은 Steam 게임이 최대 15만 건 → 요청당 13~20초 + 힙/스레드 고갈 → 페이지 전환 burst 시 API 전체 다운.

## 이전 (Before)
```java
case GAME:
    contents = gameContentRepository.findAll();   // 테이블 전체를 JVM 힙으로
    ...
for (Object obj : contents) { /* 장르 세기 */ }
```

## 이후 (After)
DB 집계로 전환 — 각 도메인 Repo에 `UNNEST + GROUP BY` 추가:
```java
@Query(value = "SELECT g AS genre, COUNT(*) AS cnt FROM game_contents, UNNEST(genres) AS g " +
       "WHERE g IS NOT NULL AND g <> '' GROUP BY g ORDER BY cnt DESC", nativeQuery = true)
List<Object[]> countByGenre();
```
```java
// 서비스: findAll() 대신 집계 결과를 합산
List<Object[]> rows = gameContentRepository.countByGenre();
for (Object[] r : rows) genreCounts.merge((String) r[0], ((Number) r[1]).longValue(), Long::sum);
```
추가로:
- `@EnableCaching` + `@Cacheable("genresWithCount"/"availableGenres")` + 30분마다 `@CacheEvict`
- `getAvailableGenres`도 `findDistinctGenres()`(UNNEST DISTINCT)로 전환
- 같은 파일의 `getWorksByGenresWithDbFiltering`의 `Pageable.unpaged()` → 상한/페이징 처리(이후 #05에서 완전 통합)

## 왜 이렇게 고쳤나
- 집계는 DB가 인덱스로 처리하는 게 정석. 15만 행을 앱 힙으로 올릴 이유가 없음 → t3.small에서 치명적.
- 장르 분포는 자주 안 바뀌므로 캐싱이 큰 효과.
- 프로젝트 자체 규칙(CLAUDE.md "대용량 조회 `Pageable.unpaged()` 금지")과도 일치.

## 개선 효과
- `genres-with-count` 응답 **20초 → 수십 ms**, 메모리 폭발 제거 → **API가 더 이상 다운되지 않음 → "CORS 에러"도 사라짐.**

## 교훈
- 모든 엔드포인트가 동시에 `ERR_FAILED`면 CORS가 아니라 **업스트림 다운(502)**을 의심. `curl -H "Origin: ..."`로 ACAO·응답시간·502를 직접 확인.
- 프론트 `client.ts`의 자동 2회 재시도가 과부하를 증폭시킴(참고).
