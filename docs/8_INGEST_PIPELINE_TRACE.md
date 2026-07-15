# Ingest 파이프라인 트레이스: RawItem → IngestPipeline → contents

> 이 문서는 "값 하나가 어디를 거쳐 어느 테이블에 도착하는지"를 따라가는 **런타임 트레이스**다.
> yml 룰 스키마(v4)는 [3_TRANSFORM_ENGINE.md](3_TRANSFORM_ENGINE.md) §4, 클래스 관계도는 [6_CLASS_DIAGRAMS.md](6_CLASS_DIAGRAMS.md) §4 참고.
> 2026-07 typed 재작성([설계 스펙](superpowers/specs/2026-07-09-readable-ingest-rewrite-design.md), PR #113/#114) 이후 기준.
> 구 v3 엔진(TransformEngine/UpsertService 계열, Map 기반 2중 매핑 + 리플렉션)은 **삭제됨** — 이 문서의 과거 버전이 그 트레이스였다.

## 0. 한 문장 멘탈모델

**"claim하고(①) → 룰 찾고(②) → typed 초안 조립(③) → 병합 또는 신규 저장(④) → 장부 기록(⑤)"**

```
① IngestPipeline ──── SKIP LOCKED로 배치 잠금 + processed 마킹 ──▶ raw_items (claim)
② RuleRegistry ────── "누구 룰로 변환하지?" ─────────────────────▶ PlatformRule (yml 인덱스)
③ DraftAssembler ──── payload + rule → 진짜 엔티티 3종 조립 ─────▶ IngestDraft(Content, 도메인엔티티, PlatformData)
④ IngestPipeline ──── 중복병합 | 재수집 라우팅 | 신규 저장 ───────▶ contents / platform_data / *_contents
⑤ IngestPipeline ──── "장부 기록" ───────────────────────────────▶ transform_runs
```

관련 파일 (crawler 모듈, `com.example.crawler.ingest`):

| 단계 | 클래스 | 역할 한 줄 |
|---|---|---|
| ⓪ | `CollectorService` | 크롤러 payload를 SHA-256 dedup으로 `raw_items` 적재. 같은 (platform, psid)의 payload가 바뀌면 `processed=false`로 재큐잉 |
| ①④⑤ | `IngestPipeline` | 오케스트레이터. claim → item별 트랜잭션으로 변환·병합·저장 → 감사 (구 BatchTransformService+UpsertService 4형제+ContentMergeService 대체) |
| ② | `rule/RuleRegistry` | 기동 시 `classpath*:rules/**/*.yml` 전부 스캔·**검증**·platformName 인덱싱. 검증 실패 = 부팅 실패 |
| ② | `rule/PlatformRule` | yml 1개 = record 1개 (v4 스키마 파싱, 모든 에러 메시지에 파일 경로 보존) |
| ③ | `DraftAssembler` | payload+rule → `IngestDraft` 조립 (구 TransformEngine 대체). **저장은 안 함** |
| ③ | `Values` | static 순수 함수 모음: `deepGet`·`convert`·`normalize`·`sameTitle` (구 ContentSimilarityService 흡수) |
| ④ | `DomainCatalog` | 도메인별로 다른 것 전부(생성/로드/중복후보/저장)를 모은 단일 switch 테이블 — 도메인 추가 시 고칠 곳이 여기 하나 |
| ⑤ | `TransformRun` | 감사 엔티티. status = SUCCESS / SUCCESS_DUPLICATE / SKIPPED / FAILED |
| 배선 | `IngestConfig` | 유일한 Spring 접점(@Bean 4개) — 위 컴포넌트는 전부 plain 클래스 |
| 구동 | `TransformSchedulingService` | 매일 06시(배치 100) / 일요일 07시(배치 200), `processBatch`가 0을 반환할 때까지 반복 |

## 1. 값 하나의 전체 여정 (NaverSeries 웹소설 예시)

크롤러가 저장해 둔 `raw_items.source_payload`:

```json
{
  "title": "전지적 독자 시점 (외전)",
  "author": "싱숑", "publisher": "문피아", "ageRating": "전체이용가",
  "genres": ["판타지"], "firstDate": "2018-05-14",
  "productUrl": "https://series.naver.com/...", "titleId": "3293134",
  "status": "연재중", "rating": 9.9,
  "downloadCount": 123, "commentCount": 456, "episodeCount": 516,
  "synopsis": "...", "imageUrl": "https://..."
}
```

### ① claim — `IngestPipeline.processBatch`

```java
List<RawItem> batch = tx.execute(s -> {
    List<RawItem> items = rawRepo.lockNextBatch(batchSize);  // 네이티브 FOR UPDATE SKIP LOCKED
    for (RawItem raw : items) { raw.setProcessed(true); raw.setProcessedAt(now); }
    rawRepo.saveAll(items);
    return items;   // 커밋 → 락 해제
});
```

- **잠금과 동시에 processed 마킹**하고 커밋한다. 이후 어떤 item이 실패해도 재선택되지 않는다 → **독약(poison) 아이템 무한 재시도 차단**. 실패는 ⑤의 FAILED 감사 기록으로만 남는다.
- 이후 item마다 **별도 트랜잭션**(`tx.execute`)으로 처리 — 한 건의 실패가 배치 전체를 롤백하지 않는다.

### ② 룰 결정 — `RuleRegistry.resolve(domain, platformName)`

```java
PlatformRule rule = ruleRegistry.resolve(raw.getDomain(), raw.getPlatformName());
// "NaverSeries" → rules/webnovel/naverseries.yml (yml 안의 platformName이 조인 키)
// 미지 플랫폼 → IllegalArgumentException → FAILED
// rule.domain ≠ raw.domain → IllegalArgumentException → FAILED (교차검증)
run.setRulePath(ruleRegistry.pathOf(...));   // 어떤 yml로 변환했는지 감사에 기록
```

룰은 기동 시 이미 전부 로드·검증돼 있다 (§3). 런타임엔 Map 조회 한 번.

### ③ 조립 — `DraftAssembler.assemble(payload, rule)`

**mappings 우변(목적지)의 접두사가 저장 위치를 결정한다.** 목적지는 접두사 뒤에 **엔티티 프로퍼티명 그대로**를 쓴다 (이름 뒤집기 없음):

| 접두사 | 바인딩 대상 | 최종 도착지 |
|---|---|---|
| `master.*` | `Content` 프로퍼티 | `contents` |
| `domain.*` | 도메인 엔티티 프로퍼티 (`WebnovelContent` 등) | `webnovel_contents` 등 |
| `platform.*` | `PlatformData` 프로퍼티 | `platform_data` 컬럼 |
| `attr.*` | attributes Map 키 리터럴 | `platform_data.attributes` **JSONB** |

이 예시(`rules/webnovel/naverseries.yml`)의 라우팅 결과:

```
title       → master.masterTitle          Content.masterTitle = "전지적 독자 시점 (외전)"
imageUrl    → master.posterImageUrl       Content.posterImageUrl
firstDate   → master.releaseDate          Content.releaseDate = 2018-05-14  ※ 바인딩 시점에 이미 LocalDate!
author      → domain.author               WebnovelContent.author = "싱숑"
ageRating   → domain.ageRating            WebnovelContent.ageRating          ※ 프로퍼티명 그대로 (snake 변환 없음)
genres      → master.genres               Content.genres = ["판타지"]   ※ 2026-07 마스터로 승격
productUrl  → platform.url                PlatformData.url
titleId     → platform.platformSpecificId PlatformData.platformSpecificId = "3293134"
status/rating/downloadCount/... → attr.*  platform_data.attributes JSONB로 직행
```

조립 규칙 (순서대로):

1. `Values.deepGet(payload, 좌변)` — `"movie_details.id"`, `"developers[0]"` 같은 경로 지원. 값이 없으면 `defaults:` 선언값 사용, 그것도 없으면 **스킵** (해당 필드만 비어 감 — 의도된 동작, RF-3).
2. `Values.convert` — **목적지 프로퍼티의 실제 타입**에 맞춰 변환 후 바인딩. `LocalDate`면 `FlexibleDateParser`(한국어/ISO/점·슬래시/영어 다 처리), 배열→`Integer`면 첫 원소(TMDB episode_run_time), Map에 `name` 키가 있으면 name 값(String화) 등. 구 엔진과 달리 **날짜가 String으로 떠돌지 않는다.**
3. `normalizers` 적용 (master String 프로퍼티만): `strip_parentheses` + `collapse_spaces` + `nfkc` → masterTitle = **"전지적 독자 시점"**
4. `domain.platforms` 주입: `[자기 플랫폼명]` + `platformsFrom` 선언 키의 attributes 값 병합 — yml 매핑과 무관하게 **항상 덮어씀** (RF-4). platforms가 fieldMapping에 없는 이유가 이것 (원본에서 안 오고 엔진이 만듦).

반환: `IngestDraft(content, domainEntity, platformData, boundDomainProps)` — **진짜 엔티티 객체들이고, 아직 DB에 아무것도 없다.** `boundDomainProps`는 이번에 바인딩된 도메인 프로퍼티 목록(④의 병합에서 "덮어쓸 필드"가 된다).

### ④ 병합 또는 신규 저장 — `IngestPipeline.processOne`

```
4a. 제목 blank? ──────────────────────────────▶ SKIPPED ("master_title 없음") [끝]
4b. psid/url 채움: raw 컬럼 → yml 매핑값 → payload 공용 키(platformSpecificId/url) fallback
4c. 중복병합: DomainCatalog.duplicateCandidates — 같은 author(웹툰/웹소설)·developer(게임)의
      기존 작품들 중 Values.sameTitle 일치 ────▶ mergeInto(기존 작품) [contentId = 기존, 끝]
      (MOVIE/TV는 후보검색 미지원 — 구 시스템과 동일)
4d. 재수집 라우팅: 중복후보가 없어도 (platformName, psid)를 이미 아는 platform_data 행이면
      그 소유 Content로 mergeInto ────────────▶ [contentId = 기존, 끝]
      ← 신규경로가 같은 psid를 다시 insert → uk_platform_id 위반으로 재수집이
        영구 FAILED 루프에 빠지던 Critical 버그의 수정
4e. 전부 아니면 신규 저장: contents → platform_data → *_contents 순서로 이 순간에만 DB 쓰기
```

`mergeInto`(구 ContentMergeService.mergeContent 동작 보존)의 3원칙:

- **Content**: null인 필드만 채움 (기존 값 우선) — 단 genres는 이번 수집에 값이 있으면 덮어쓰기 (재수집 갱신)
- **PlatformData**: 같은 (platform, psid) 행이 있으면 attributes/lastSeenAt/url **갱신**(재수집 반영 — 최신 크롤이 진실), 없으면 새 행 추가. 기존 행의 content 포인터는 건드리지 않음(크로스링크 감지 시 warn 로그만)
- **도메인 필드**: `boundDomainProps`에 든 프로퍼티만 **덮어쓰기** — 단 `platforms`만은 **기존∪신규 합집합** (2026-07 수정: 과거에는 교체라서 크로스플랫폼 병합 시 먼저 수집된 플랫폼이 배열에서 유실됐음. 한 작품 = 여러 플랫폼 연관이 정상 상태)

제목 동일성(`Values.sameTitle`)은 소문자화 + 공백/구두점 제거 후 **정확 일치**다. 유사도 점수 아님.

### ⑤ 장부 — `TransformRun`

| status | 언제 |
|---|---|
| `SUCCESS` | 정상 저장/병합 |
| `SUCCESS_DUPLICATE` | 같은 배치 안에서 이미 본 contentId로 또 도착 |
| `SKIPPED` | masterTitle 없음/blank |
| `FAILED` | 미지 플랫폼, 도메인 불일치, 변환/저장 중 예외 (item 트랜잭션 롤백, `producedContentId=null` 보장) |

기록 내용: rawId, platformName, **rulePath**(어떤 yml이었나), producedContentId, error, finishedAt.
감사 저장 자체가 실패해도 배치는 계속된다 (error 로그만).

## 2. 왜 이제 추적이 쉬운가 (구 엔진 대비)

| 구 v3 엔진 | 현재 (typed) |
|---|---|
| Master/Platform/DomainDoc = 그냥 HashMap, 키가 문자열 | `IngestDraft`가 **진짜 엔티티**를 들고 다님 — IDE 내비게이션 가능 |
| fieldMappings→domainObjectMappings 2중 매핑, 홉마다 camel↔snake 뒤집힘 | 값 여정 **2홉**: yml 1줄(`author: domain.author`) → 엔티티 프로퍼티명 검색 끝 |
| 매핑 어긋나면 에러 없이 값 유실 (조용한 실패) | 목적지 오타·죽은 defaults·producer 없는 platformsFrom = **부팅 실패**. 제목 없음 = SKIPPED 감사 기록 |
| 'Upsert' 이름 4개 클래스 + Merge/Similarity | 오케스트레이션은 `IngestPipeline` 하나 |
| 배치 단위 트랜잭션 (한 건 실패 → 전체 영향) | **item별 트랜잭션 격리** + claim 시점 processed 마킹(독약 차단) |

남아 있는 "조용한" 동작 한 곳: **원본에 값이 없고 defaults 선언도 없으면 그 필드는 그냥 비어 간다** (스킵). 필드가 비어서 도착했다면 payload 키 이름과 yml 좌변부터 대조할 것.

**읽는 요령**: 프로덕션 코드보다 **테스트를 먼저** 읽어라 — 실제 입출력 예제가 코드로 고정돼 있다.

| 테스트 | 고정해 둔 것 |
|---|---|
| `RuleFilesTest` | 프로덕션 rules yml 7개 전부 로드 통과 + Steam 등 골든 입출력 |
| `DraftAssemblerTest` | 접두사 라우팅/defaults/normalizers/platforms 주입 |
| `IngestPipelineTest` / `IngestPipelineMergeTest` | claim·상태 어휘·격리 / 병합·재수집 라우팅 시맨틱 |
| `RuleRegistryTest` / `PlatformRuleTest` | 기동검증(부팅 실패 케이스들) / v4 파싱 에러 |
| `ValuesTest` / `DomainCatalogTest` | 타입 변환·정규화·제목 비교 / 도메인 switch 4곳 |

## 3. 새 플랫폼 추가 시 실제로 해야 하는 일 (전체 체크리스트)

"yml만 추가"는 **변환 단계에만** 해당한다. 실제 온보딩은:

1. 크롤러 서비스 작성 (Java — `CollectorService.saveRaw`로 `raw_items` 저장까지)
2. `JobType` enum 추가 + `JobExecutor` 구현체 작성 (Java)
3. 스케줄링 서비스에 producer 호출 추가 (Java)
4. **`rules/<domain>/<platform>.yml` 작성** ← 여기만 yml
   - `platformName`은 크롤러가 RawItem에 기록하는 `platform_name` 문자열과 **정확히 일치**해야 함
   - 목적지는 엔티티 **프로퍼티명 그대로** (`domain.ageRating`처럼) — 오타·rename 미반영은 부팅 실패로 잡히니 일단 띄워보면 안다
   - 구 v3의 "fieldMappings ↔ domainObjectMappings 짝 맞추기"는 **폐지됨** (섹션이 하나뿐)
5. 재빌드·재배포 (yml은 classpath 리소스라 **yml만 고쳐도 재배포 필요**)
