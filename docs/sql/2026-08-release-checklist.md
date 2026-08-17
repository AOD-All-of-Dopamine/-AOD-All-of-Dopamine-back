# 2026-08 릴리스 체크리스트 (crawler-standardization + collections)

main 머지 후 배포 시 순서대로 실행. 스키마 변경은 전부 "추가"(컬렉션 3테이블 신설)라
기존 데이터와 충돌 없음 - 리스크는 스키마가 아니라 아래 데이터 채움 2건.

## 1. 백업

```bash
pg_dump -h <host> -U <user> -d <db> -F c -f aod_backup_$(date +%Y%m%d).dump
```

## 2. back 배포

- 기동 시 `ddl-auto=update`가 collections / collection_items / collection_likes 생성
  (배포 properties가 update가 아니면 기동 로그에서 테이블 생성 여부 확인 후 수동 DDL)
- GIN 인덱스는 DatabaseIndexInitializer가 멱등 처리
- 확인: `GET /api/collections?size=1` -> 200

## 3. 백필 SQL (필수 - 미실행 시 탐색 필터·카드 태그 전멸)

배포 DB의 contents.genres/platforms가 빈 배열인 경우 반드시 실행.
로컬에서 이미 검증된 파일이며 멱등(여러 번 실행 안전):

- `docs/sql/2026-07-promote-genres-to-contents.sql` **§1~3만** (§4 DROP은 검증 후 별도 결정)
- `docs/sql/2026-07-promote-platforms-to-contents.sql` **§1~3만**

확인:
```sql
SELECT count(*) FILTER (WHERE genres <> '{}') AS g,
       count(*) FILTER (WHERE platforms <> '{}') AS p, count(*) FROM contents;
```
```
GET /api/works/genres?domain=GAME  -> 장르 목록 비어있지 않아야 함
```

## 4. 크롤 1회 (카드 신규 표시 데이터 채움)

- Steam: review_summary(리뷰 요약) - 이번에 수집 경로 신설, 기존 행엔 없음
- TMDB: attributes.rating(평점) - 프로세서 화이트리스트에 이번에 추가, **백필 불가**(기존 raw에 값 없음)
- 크롤 전까지 카드는 해당 요소를 생략 렌더(깨지지 않음)

## 5. front 배포 (back 이후 - 역순이면 컬렉션 페이지 API 404)

확인 시나리오: 탐색 필터(장르 1개 적용 시 결과 감소) / 게임 카드 스팀 평가 /
컬렉션 생성-담기-좋아요-발견 노출 / 모바일 390px 하단 6탭

## 참고

- 구 attr 잔재(NaverSeries rating 등)는 새 UI에 노출되지 않음(접두사 필터) - 정리 불필요
- 조회수 중복 방지 없음(상세 GET당 +1) - 문서화된 한계, 백로그
- 관련 문서: docs/troubleshooting/06, front docs/plans/2026-08-15-collections.md
