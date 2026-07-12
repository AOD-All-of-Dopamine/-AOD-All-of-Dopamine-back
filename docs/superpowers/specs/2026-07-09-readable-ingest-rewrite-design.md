# Ingest 파이프라인 가독성 재작성 (Readable Ingest Rewrite) — Design Spec

- 작성일: 2026-07-09
- 상태: 승인 대기
- 브랜치: `feature/readable-ingest` ← `feature/transform-rule-registry`에서 분기
- 전제: 서비스는 테스트 버전 — 동작 보존은 지키되 전면 재작성 허용

## 0. 배경과 목표

**문제**: RawItem → Transform → Upsert 구간(`TransformEngine`~`ContentMergeService`)이 추적 불가능하게 어렵다.
원인 분해 결과 대부분이 구현 선택 탓: ① fieldMappings/domainObjectMappings **2중 매핑**, ② 홉마다 이름 뒤집기(camel→snake→camel),
③ 검증 없는 리플렉션(오타=조용한 유실), ④ HashMap Doc 3종(타입 없음), ⑤ 'Upsert' 4형제 + Optimized 중복 구현 + 죽은 코드.

**목표**: 같은 동작을 **핵심 6파일 ~650줄**(+유지 1, 소폭수정 1)의 읽기 좋은 구조로 재작성. "값이 어디서 와서 어디로 가는지"가 2홉 안에 보여야 한다. (대체되는 기존 코드: 13파일 ~1,300줄)

**결정 근거 (실측)**:
- git 10개월: rules yml을 건드린 커밋 ~40개 중 **yml 단독 변경은 4개(10%)** — 룰은 크롤러/엔티티 변경과 90% 동반 이동
- 따라서 yml의 가치는 "플랫폼별 매핑 아티팩트의 균일함"이지 핫스왑이 아님 → **yml은 유지하되 엔진을 재작성** (E안)

## 1. 핵심 결정 (E안 — 깨끗한 리플렉션)

| 항목 | 결정 |
|---|---|
| 소스 매핑 | **yml 유지** — 플랫폼당 1파일, 단일 `mappings` 섹션 (domainObjectMappings **삭제**) |
| 목적지 이름 | **엔티티 프로퍼티명 그대로** (`domain.ageRating`) — 이름 뒤집기 소멸. 예외: `attr.*`는 기존 JSONB 키 리터럴(snake_case) 유지 (API 소비자 호환) |
| 도메인 바인딩 | **리플렉션(BeanWrapper) + 기동 검증** — 어댑터 5클래스 대신 `DomainCatalog` 테이블 1클래스 |
| 기동 검증 (협상 불가) | 부팅 시 모든 yml의 `domain.*` 프로퍼티 실존 + 타입 변환 가능 확인 → **오타/rename 미반영 = 앱이 안 뜸**. CI의 컨텍스트 로드 테스트로 배포 전 차단 |
| 중간 산물 | HashMap Doc 3종 → **typed `IngestDraft`**(Content + 도메인엔티티 + PlatformData) 직접 조립 |
| 파이프라인 | `IngestPipeline` 1개로 통합 (BatchTransformService/Optimized/UpsertService/ContentUpsertService/DomainCoreUpsertService 대체) |
| 병합 | **동작 그대로 보존**: author/developer 후보 → 정규화 제목 일치 → Content null-fill + PlatformData 추가 + 도메인 필드 재적용. MOVIE/TV 중복검사 미지원 유지 |
| 제목 비교 | `ContentSimilarityService` 삭제 → `Values.sameTitle()` static ~8줄로 흡수 (가짜 double API 제거) |
| 핫리로드 미래 | 비범위. 단 yml이 플랫폼 아티팩트로 남으므로 "룰을 DB에서 로드" 진화 경로는 보존됨 |

## 2. yml 스키마 v4

```yaml
platformName: NaverSeries        # RuleRegistry 인덱스 키 (RawItem.platform_name과 일치)
domain: WEBNOVEL
schemaVersion: 4

mappings:                        # 소스 payload 경로 → 목적지 (접두사가 저장 위치 결정)
  title:         master.masterTitle      # Content 프로퍼티명 그대로
  synopsis:      master.synopsis
  imageUrl:      master.posterImageUrl
  firstDate:     master.releaseDate
  author:        domain.author           # 도메인 엔티티 프로퍼티명 그대로
  publisher:     domain.publisher
  ageRating:     domain.ageRating
  genres:        domain.genres
  productUrl:    platform.url            # PlatformData 프로퍼티명 그대로
  titleId:       platform.platformSpecificId
  status:        attr.status             # JSONB 키 리터럴 (기존 키 이름 유지)
  rating:        attr.rating
  downloadCount: attr.download_count

defaults:                        # 원본 누락 시 명시적 기본값 (없으면 스킵)
  attr.download_count: 0
  attr.comment_count: 0

normalizers:                     # 목적지 필드별 정규화 파이프
  master.masterTitle: [nfkc, strip_parentheses, collapse_spaces]
```

- 소스 경로는 기존 `deepGet` 문법 유지 (`movie_details.id`, `developers[0]`)
- `domain.platforms`는 yml에 적지 않음 — 엔진이 `[platformName] + platformsFrom` 자동 주입 (기존 RF-4 동작 유지, `platformsFrom` 섹션 유지)
- 기존 yml 7개를 이 스키마로 기계적 이관 (값·키 변경 없음, 구조만)

## 3. 컴포넌트 (핵심 6파일 ~650줄)

| 파일 | 책임 | 예상 |
|---|---|---|
| `ingest/IngestPipeline.java` | 배치 루프 → 변환 → 배치내 dedupe → 중복병합 or 신규저장 → TransformRun 감사. `IngestDraft`는 nested record | ~170줄 |
| `ingest/DraftAssembler.java` | payload + rule → `IngestDraft`. mappings 루프, defaults, normalizers, master/platform은 typed 직접 세팅, domain은 리플렉션 bind | ~120줄 |
| `ingest/DomainCatalog.java` | 도메인별로 다른 것 전부: create(switch 5) · repo 매핑 · duplicateCandidates(switch 5, MOVIE/TV는 empty) · saveNew | ~60줄 |
| `ingest/rule/PlatformRule.java` | yml 스키마 record (platformName, domain, mappings, defaults, normalizers, platformsFrom) | ~30줄 |
| `ingest/rule/RuleRegistry.java` | 기존 스캔/인덱싱 로직 재사용 + yml 로드(`RuleLoader` 흡수) + **기동 검증**(목적지 프로퍼티 실존·타입 체크, 중복 platformName 체크) | ~150줄 |
| `ingest/Values.java` | 타입 변환(str/strList/intOr/date) + `deepGet` + normalizer 5종 + `sameTitle` | ~130줄 |
| (유지) `util/FlexibleDateParser.java` | 날짜 파싱 단일화 — 그대로 | — |
| (수정) `ingest/TransformSchedulingService.java` | 호출 대상만 `IngestPipeline`으로 변경 | 소폭 |

## 4. 파이프라인 흐름 (raw 1건)

```
rule = ruleRegistry.resolve(raw.domain, raw.platformName)
draft = draftAssembler.assemble(raw.sourcePayload, rule)     // typed IngestDraft
  ├ masterTitle blank → run=SKIPPED, processed=true          [개선 ③]
배치내 dedupe (processedContentIds — 기존 유지)
후보 = domainCatalog.duplicateCandidates(domain, draft.domainEntity)
Values.sameTitle 일치?
  ├ 예: 병합 — Content null-fill + PlatformData upsert + 도메인 필드 재적용(bind existing) → 기존 contentId
  └ 아니오: contentRepo.save → platformData upsert((platformName,psid) 조회 후) → domainCatalog.saveNew
TransformRun 기록 (rulePath 컬럼 유지 — yml 경로), raw.processed=true
```

## 5. 에러 처리 (배치 시맨틱 개선 3개 — 의도적 동작 변경)

현재 동작은 사실상 버그라 수정한다. transform 입출력·병합 로직은 동작 보존, 아래만 변경:

| # | 현재 | 변경 |
|---|---|---|
| ① | item 1개 예외 → **배치 전체 롤백**(TransformRun까지 증발, 독약 job이 배치 무한 차단) | item별 트랜잭션 격리 — 해당 건 FAILED 기록, 배치 계속 |
| ② | 미지 플랫폼 → 예외 → 배치 전체 실패 | 해당 건 FAILED + processed=true (재시도 무의미) |
| ③ | 제목 blank → null 반환 → 같은 배치 2건째부터 SUCCESS_DUPLICATE로 오기록 | 명시적 SKIPPED |

- 기동 검증 실패(yml 오타, 프로퍼티 부재, platformName 중복) → **부팅 실패** (배포 전 차단)

## 6. 테스트 전략

1. **패리티(golden) 테스트 최우선**: 플랫폼 7개 × 대표 payload 픽스처 → 기존 yml 엔진(transform-rule-registry 브랜치)의 출력값을 리터럴로 고정 → 새 `DraftAssembler` 산출이 동일한지 검증. 기존 characterization 테스트를 시드로 재사용
2. `DomainCatalog` 단위: create/duplicateCandidates/saveNew (mock repo)
3. `IngestPipeline` 통합: 신규/중복병합/실패격리/SKIPPED 4경로
4. 기동 검증 테스트: 고의 오타 yml → 컨텍스트 로드 실패 확인 (CI가 rename 회귀 차단)
5. 병합 시맨틱: null-fill·PlatformData 중복 방지·도메인 필드 재적용 — 기존 동작과 비교

## 7. 삭제 목록 (재작성 완료 시점)

`TransformEngine`, `GenericDomainUpserter`, `UpsertService`, `ContentUpsertService`(죽은 findOrCreateContent 포함),
`DomainCoreUpsertService`, `ContentMergeService`, `ContentSimilarityService`, `BatchTransformService`,
`BatchTransformServiceOptimized`, `MappingRule`, `DomainObjectMapping`, `NormalizerStep`, `RuleLoader`(Registry에 흡수), 관련 구 테스트.
`AdminTestController`의 배치 트리거는 `IngestPipeline` 호출로 수정.

## 8. 비범위

- 크롤러/잡큐(`common/queue`) — 별도 리팩토링 트랙 (SKIP LOCKED 미구현·N+1 등은 별도 이슈)
- 룰 DB 로딩/핫리로드 — 경로만 보존, 구현 안 함
- MOVIE/TV 중복검사 추가 — 기존 미지원 유지
- `docs/8_INGEST_PIPELINE_TRACE.md` 갱신은 재작성 완료 후 (현재 버전은 구 엔진 기준)

## 9. 성공 기준

- 패리티 테스트 green (7 플랫폼 대표 payload에서 구·신 출력 동일)
- "값 하나의 여정" 추적이 2홉 이내 (yml 1줄 → 프로퍼티명 검색)
- 핵심 6파일, 총 ~650줄 이내, 파일당 ~170줄 이내
- 고의 yml 오타 → 부팅 실패 (조용한 유실 0)
