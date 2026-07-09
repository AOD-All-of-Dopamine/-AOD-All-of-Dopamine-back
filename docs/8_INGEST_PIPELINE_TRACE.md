> ⚠️ 이 문서는 **구(v3) yml 엔진** 기준 트레이스다. 2026-07 typed 파이프라인 재작성(feature/readable-ingest,
> [설계 스펙](superpowers/specs/2026-07-09-readable-ingest-rewrite-design.md)) 이후의 흐름은:
> `IngestPipeline` → `DraftAssembler`(+`RuleRegistry` v4) → `DomainCatalog`. 값 여정은 "yml 1줄 → 프로퍼티명 검색" 2홉.

# Ingest 파이프라인 트레이스: RawItem → Transform → Upsert

> 이 문서는 "값 하나가 어디를 거쳐 어느 테이블에 도착하는지"를 따라가는 **런타임 트레이스**다.
> yml 룰 스키마 자체는 [3_TRANSFORM_ENGINE.md](3_TRANSFORM_ENGINE.md) §4 참고.
> 이 코드는 선언적(제네릭) 설계라 파일 하나만 읽어서는 흐름이 안 보인다 — 헤맬 때마다 이 문서로 돌아올 것.

## 0. 한 문장 멘탈모델

**"룰 찾고(①) → 세 바구니로 분류하고(②) → 조립해서 중복검사 후 저장(③) → 장부 기록(④)"**

```
① BatchTransformService ──── "누구 룰로 변환하지?" ──▶ RuleRegistry (yml 인덱스)
② TransformEngine ────────── "꺼내서 세 바구니에 분류" ─▶ Triple(master, platform, domain)  ※ 전부 Map
③ UpsertService ──────────── "조립 → 중복검사 → 저장" ──▶ contents / platform_data / *_contents
④ BatchTransformService ──── "장부 기록" ───────────────▶ TransformRun + raw.processed=true
```

관련 파일 (crawler 모듈, `com.example.crawler`):

| 박스 | 클래스 | 역할 한 줄 |
|---|---|---|
| ① | `ingest/BatchTransformService` | RawItem 배치 잡아 전체 오케스트레이션 |
| ① | `service/RuleRegistry` | 기동 시 `classpath*:rules/**/*.yml` 전부 스캔, platformName으로 인덱싱 |
| ① | `service/RuleLoader` | yml 1개 → SnakeYAML `loadAs` → `MappingRule` 객체 |
| ② | `service/TransformEngine` | raw JSON + 룰 → MasterDoc/PlatformDoc/DomainDoc (전부 HashMap) |
| ③ | `service/UpsertService` | build 3종 → 중복병합 판정 → 저장 순서 제어 |
| ③ | `service/ContentUpsertService` | master Map → `Content` 엔티티 |
| ③ | `service/DomainCoreUpsertService` | domain Map → `WebnovelContent` 등 도메인 엔티티 (@MapsId/Persistable) |
| ③ | `service/GenericDomainUpserter` | domainObjectMappings + 리플렉션으로 도메인 엔티티 필드 세팅 |
| ③ | `service/similarity/ContentMergeService` | "이미 다른 플랫폼으로 들어온 같은 작품인가?" 판정·병합 |

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

### ① 룰 결정 — `BatchTransformService.processBatch`

```java
MappingRule rule = ruleRegistry.resolve(raw.getDomain(), raw.getPlatformName());
// raw.platformName="NaverSeries" → rules/webnovel/naverseries.yml (yml 안의 platformName이 조인 키)
// rule.domain("WEBNOVEL") ≠ raw.domain 이면 예외 (교차검증)
run.setRulePath(ruleRegistry.pathOf(...));  // 어떤 yml로 변환했는지 TransformRun에 기록
```

- RawItem은 `RawItemRepository.lockNextBatch` — 네이티브 `FOR UPDATE SKIP LOCKED`로 배치 잠금.
- `platformSpecificId`는 payload의 `titleId`/`steam_appid`/`movie_details.id` 등 fallback 체인으로 추출.

### ② 변환 — `TransformEngine.transform(payload, rule)`

**fieldMappings의 우변(목적지) 접두사가 바구니를 결정한다:**

| 접두사 | 바구니 | 최종 도착지 |
|---|---|---|
| (없음) | MasterDoc | `contents` (마스터 테이블) |
| `domain.` | DomainDoc | `webnovel_contents` 등 도메인 테이블 |
| `platform.` | PlatformDoc | `platform_data` 컬럼 |
| `platform.attributes.` | PlatformDoc.attributes | `platform_data.attributes` **JSONB** |

이 예시의 라우팅 결과:

```
title       → master["master_title"]   = "전지적 독자 시점 (외전)"
imageUrl    → master["poster_image_url"]
firstDate   → master["release_date"]   = "2018-05-14"   ※ 아직 String!
author      → domain["author"]         = "싱숑"
ageRating   → domain["age_rating"]     = "전체이용가"    ※ camel→snake로 이름 바뀜
genres      → domain["genres"]         = ["판타지"]
productUrl  → platform["url"]
titleId     → platform["platform_specific_id"] = "3293134"
status/rating/downloadCount/... → platform.attributes.*   (JSONB로 직행)
```

이어서:
- 원본에 값이 없으면 `defaults:` 선언에서 채움 (선언 없으면 스킵 — 조용히 사라짐 주의)
- `normalizers` 적용: `strip_parentheses` + `collapse_spaces` → master_title = **"전지적 독자 시점"**
- 엔진이 직접 주입: `domain["platforms"] = ["NaverSeries"]` (+ `platformsFrom` 선언 시 watch_providers 등 병합)
  — **platforms는 fieldMappings에 없는데 domainObjectMappings에 있는 이유가 이것** (원본에서 안 오고 엔진이 만듦)

반환: `Triple(master, platform, domain)` — **아직 전부 Map이고 DB에 아무것도 없음.**

### ③ 조립·중복검사·저장 — `UpsertService.upsert`

```
3a. contentUpsertService.buildContent(master)      → Content 객체        (저장 X)
       release_date "2018-05-14" → FlexibleDateParser → LocalDate 로 여기서 변환
3b. domainCoreUpsert.buildDomainData(domainDoc)    → WebnovelContent 객체 (저장 X)
       └ GenericDomainUpserter: domainObjectMappings + 리플렉션으로 필드 세팅
         domain["age_rating"] ─(targetField: ageRating, type: string)→ .setAgeRating("전체이용가")
         domain["genres"]     ─(type: list)→ .setGenres([판타지])
         valueMap 선언 있으면 값 치환 (예: "true"→완결)
3c. buildPlatformData(platform)                    → PlatformData 객체    (저장 X)
3d. contentMergeService.findAndMergeDuplicate(3a, 3b, 3c, domainDoc, mappings)
       ├─ 중복 발견 → 기존 작품에 병합, 기존 contentId 반환  [여기서 끝]
       └─ 중복 아님 ↓
3e. saveContent → savePlatformData → saveDomainData        ← 이 순간에만 DB 쓰기
       contents     platform_data      webnovel_contents
       (platform_data는 (platformName, platformSpecificId) 유니크로 조회 후 upsert)
```

**왜 build와 save를 쪼갰나?** — 저장 **전에** "이미 다른 플랫폼으로 들어온 같은 작품인가"를
Content+도메인엔티티+PlatformData 3종 세트로 비교해야 하기 때문 (`ContentMergeService`).
이 요구사항이 체인을 한 단계 깊게 만든 주범이다.

### ④ 장부 — `BatchTransformService`

- `TransformRun` 저장: rawId, platformName, **rulePath**, status(SUCCESS/SUCCESS_DUPLICATE/FAILED), producedContentId
- `raw.processed=true, processedAt=now`

## 2. fieldMappings vs domainObjectMappings 요약

같은 값이 **두 매핑을 차례로** 통과한다. 단계·소비자·입력이 전부 다르다:

| | `fieldMappings` (②) | `domainObjectMappings` (③) |
|---|---|---|
| 소비자 | `TransformEngine` | `GenericDomainUpserter` |
| 입력 | 원본 JSON | DomainDoc (②의 산물) |
| 좌변 | 원본 JSON 경로 (`movie_details.id`, `developers[0]` 가능) | DomainDoc 키 (②의 `domain.*` 우변들 + `platforms`) |
| 하는 일 | **추출 + 분류** | **타입 변환 + 엔티티 주입** (리플렉션) |

주의: `fieldMappings`의 `domain.*` 우변 키 집합과 `domainObjectMappings` 좌변 키 집합이 어긋나면
**에러 없이 값이 유실**된다 (매핑 없는 키는 upsert 루프에서 스킵). yml 수정 시 반드시 두 섹션을 짝으로 볼 것.

## 3. 왜 이 코드는 읽기 어려운가 (당신 탓이 아님)

1. **데이터가 타입에 없다** — Master/Platform/DomainDoc은 그냥 HashMap. 키가 문자열이라 IDE 내비게이션 불능.
2. **진실이 두 언어에 분산** — 흐름 절반은 Java, 절반은 yml. 홉마다 네이밍 뒤집힘(camel→snake→camel).
3. **리플렉션** — `targetField`와 엔티티 필드 사이 컴파일타임 연결 없음.
4. **'Upsert' 이름이 4개 클래스** — UpsertService / ContentUpsertService / DomainCoreUpsertService / GenericDomainUpserter.
5. **조용한 실패** — 매핑 누락 = 스킵. 잘못 읽어도 에러가 안 알려줌.

이는 "새 플랫폼 = yml 1개" (OCP)를 사기 위해 지불한 **추적 가능성 비용**이다.

**읽는 요령**: 프로덕션 코드보다 **characterization 테스트를 먼저** 읽어라 —
`TransformEngineTest` / `RuleRegistryTest` / `GenericDomainUpserterTest` 에 실제 입출력 예제가 코드로 고정돼 있다.

## 4. 새 플랫폼 추가 시 실제로 해야 하는 일 (전체 체크리스트)

"yml만 추가"는 **변환 단계에만** 해당한다. 실제 온보딩은:

1. 크롤러 서비스 작성 (Java — `raw_items`에 payload 저장까지)
2. `JobType` enum 추가 + `JobExecutor` 구현체 작성 (Java)
3. 스케줄링 서비스에 producer 호출 추가 (Java)
4. **`rules/<domain>/<platform>.yml` 작성** ← 여기만 yml
   - `platformName`은 크롤러가 RawItem에 기록하는 `platform_name` 문자열과 **정확히 일치**해야 함
   - `fieldMappings`의 `domain.*` ↔ `domainObjectMappings` 키 짝 맞추기
5. 재빌드·재배포 (yml은 classpath 리소스라 **yml만 고쳐도 재배포 필요**)
