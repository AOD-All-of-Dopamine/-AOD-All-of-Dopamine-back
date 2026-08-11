# Crawler 표준 구조 수렴 (Crawler Standardization) — 설계 스펙

- 날짜: 2026-08-11
- 상태: 사용자 승인됨 (브레인스토밍 완료)
- 실행 방식: **루프 엔지니어링** (반복 단위·검증 게이트·중단 조건은 §7)

## 1. 목표

활성 플랫폼 4개(Steam, TMDB, 네이버웹툰, 네이버시리즈)의 수집 코드를 **단일 표준 구조로 수렴**시켜, 크롤러 하나를 이해하면 나머지 전부를 이해할 수 있는 상태로 만든다. **런타임 동작(크론 일정, 큐 처리, 수집 결과)은 보존한다.**

### 스코프 제외 (변경 금지)
- `contents/novel/kakaopage/` — 휴면 상태 유지, 이번 라운드 손대지 않음
- `ranking/` 전체 + `RankingScheduler` — 후속 라운드
- ingest 파이프라인 (`ingest/`) — 이미 표준화 완료됨 (RuleRegistry)
- api / shared 모듈

## 2. 배경 — 현재 문제

같은 일을 하는 크롤러 5개가 3가지 다른 어휘로 작성되어 있다.

| 플랫폼 | 구조 어휘 | 문제 |
|---|---|---|
| Steam (8파일) | ApiFetcher / PayloadProcessor / CrawlService / SchedulingService + 컨트롤러 3개 | 역할 분산, 테스트 컨트롤러 산재 |
| TMDB (6+5파일) | ApiFetcher / PayloadProcessor / Service / SchedulingService + 컨트롤러 2개 | Steam과 유사하지만 이름 다름 |
| 네이버웹툰 (7파일) | Crawler / SeleniumPageParser(698줄) / Selectors / Service / SchedulingService | 다른 어휘 세트, 거대 파서 |
| 네이버시리즈 (2파일) | Crawler(452줄) / SchedulingService | 모든 것이 한 파일에 |

내부 로직 문제:
1. **셀렉터 이원화/중복**: `NaverWebtoonSelectors` 상수 클래스가 있는데 `SeleniumPageParser:133`, `NaverWebtoonSchedulingService:99`가 인라인 하드코딩으로 우회. 네이버시리즈는 같은 셀렉터가 `NaverSeriesCrawler:188`과 `NaverSeriesSchedulingService:100`에 복붙 중복.
2. **파싱 유틸 중복**: `text()/attr()/absolutize()/parseKoreanCount()` 등이 크롤러별 자체 구현.
3. **역할 침범**: SchedulingService("큐 등록만" 담당)가 실제로는 Jsoup 크롤링+파싱 수행.
4. **디렉터리-패키지 불일치**: 디렉터리는 `TMDB`/`Webtoon` 대문자, package 선언은 소문자 (Windows 대소문자 무시로 방치됨).
5. **진입점 산재**: 플랫폼별 컨트롤러 5개(SteamController, SteamTestController, SteamRateLimiterController, TmdbController, TmdbTestController)가 수동 트리거 경로로 존재.

## 3. 표준 모양 (Target Shape)

### 3.1 플랫폼당 구조

```
contents/<platform>/               # 전부 소문자: steam, tmdb, webtoon, novel/naverseries
  ├─ XxxSelectors.java             # (스크래핑형만) 셀렉터 상수 단일 출처. 폴백 우선순위 배열 패턴
  ├─ XxxFetcher.java               # 원천 접근의 유일한 관문:
  │                                #   discoverTargets(...) : 목록 크롤 → 대상 ID/URL 목록
  │                                #   fetchDetail(id)      : 상세 크롤 → payload(Map/DTO)
  ├─ XxxListParser / XxxDetailParser  # (필요시) 페이지 단위로만 분할
  └─ dto/

common/queue/executors/
  └─ XxxExecutor.java              # 큐 소비: Fetcher.fetchDetail() → RawItem 적재
                                   # 기존 Service/CrawlService/PayloadProcessor 로직 흡수

contents/<platform>/
  └─ XxxJobProducer.java           # 기존 SchedulingService rename.
                                   # Fetcher.discoverTargets() 호출 → 큐 등록만. 파싱 코드 0줄

common/util/
  └─ HtmlParseUtils.java           # text/attr null-safe, absolutize, parseKoreanCount 등 공용 헬퍼
```

### 3.2 규칙

- **진입점 2개 고정**: `MasterScheduler`(크론) → JobProducer, admin 컨트롤러(수동) → JobProducer. 그 외 삭제.
- **셀렉터 단일 출처**: `select("...")`/`By.cssSelector("...")` 인라인 호출 금지 → `XxxSelectors` 상수 참조로 전량 교체.
- **API형 플랫폼(Steam/TMDB)은 Selectors 없음** — 역할 세트는 "필요한 것만, 이름은 동일하게".
- **플랫폼 고유 협력자 유지**: `SteamRateLimiter`, Selenium 파서 등은 Fetcher 옆에 그대로 (표준화는 역할 어휘의 통일이지 파일 수 강제가 아님).
- **예외 일관화**: Fetcher는 실패 시 예외 throw, 재시도 판단은 Executor→Job Queue(RETRY, maxRetries=3)에만 위임. 크롤러 내부 개별 재시도 제거.
- **네이밍**: `JobProducer`는 기존 큐 어휘(CrawlJobProducer/Consumer)와 정렬.

## 4. 실행 순서 (플랫폼 1개씩, 항상 그린)

| 단계 | 내용 | 완료 판정 |
|---|---|---|
| 0 | 안전망: contextLoads 복구(SENTRY_DSN 기본값 처리), 빌드 그린 확인 | `gradlew :crawler:test` 그린 |
| 1 | TMDB 전환 (표준에 가장 가까움 — 여기서 표준 확립) | 게이트 통과 |
| 2 | Steam 전환 | 게이트 통과 |
| 3 | 네이버시리즈 전환 (셀렉터 상수화 + 유틸 추출 포함) | 게이트 통과 |
| 4 | 네이버웹툰 전환 (Selenium 복잡도 최대 — 마지막) | 게이트 통과 |
| 5 | 마무리: 플랫폼 컨트롤러 5개 → admin 통합, 죽은 코드 삭제(demo 추천 스캐폴딩, epic.yml 휴면 룰), 디렉터리 소문자 rename(git 2단계 rename) | 게이트 통과 + 전체 스모크 |

## 5. 검증 게이트 (매 단계 공통)

1. `gradlew build` 그린 (crawler 모듈 컴파일 + 테스트)
2. contextLoads 그린 (0단계 이후 상시)
3. 스모크: admin 수동 트리거로 해당 플랫폼 소량 수집 → `raw_items` 적재 확인 (payload 구조가 전환 전과 동일한지 샘플 비교)
4. 빈 참조 전수 확인: rename된 빈 이름을 참조하는 @Qualifier/설정/Thymeleaf 템플릿 grep

## 6. 성공 기준

- [ ] 4개 플랫폼이 동일 역할 세트(Selectors?/Fetcher/JobProducer/Executor)로만 구성
- [ ] 인라인 셀렉터 0건 (`grep "select(\"" contents/` 가 Selectors 참조 외 미검출)
- [ ] 수집 시작점 2곳(MasterScheduler, admin)뿐, 플랫폼별 컨트롤러 0개
- [ ] 공용 파싱 헬퍼 중복 0건 (HtmlParseUtils로 통합)
- [ ] 패키지·디렉터리 소문자 일관
- [ ] 전 단계 빌드·contextLoads 그린 + 플랫폼별 스모크 수집 성공

## 7. 루프 엔지니어링 실행 규약

- **반복 단위 = §4의 단계 1개** (한 플랫폼 = 한 반복). 반복 안에서: 구현 → §5 게이트 → 커밋 → 계획 체크오프.
- **중단 조건**: 게이트 실패 시 같은 단계에서 수정 반복, 2회 연속 실패 시 루프 중단하고 사용자 보고. 다음 단계로 넘어가며 실패를 미루는 것 금지.
- **커밋 규율**: 단계당 1~2 커밋 (기계적 이동/rename 커밋과 로직 이동 커밋 분리 권장). 브랜치: `feature/crawler-standardization`.
- **컨텍스트 원장**: 진행 상황은 implementation plan의 체크박스가 단일 진실 — 루프 각 회차는 계획 파일을 읽고 다음 미완 단계를 집는다.

## 8. 리스크와 대응

| 리스크 | 대응 |
|---|---|
| 테스트 부재 (crawler 테스트 1파일) | 단계별 스모크 수집 + payload 샘플 비교로 보완 |
| 빈 rename으로 숨은 참조 깨짐 | §5-4 전수 grep 게이트 |
| Selenium 로직은 실행 없이 검증 불가 | 웹툰 단계는 실제 소량 크롤 필수 (Docker/로컬 크롬) |
| 디렉터리 대소문자 rename이 Windows git에서 유실 | 2단계 rename (`git mv TMDB tmp && git mv tmp tmdb`) |
