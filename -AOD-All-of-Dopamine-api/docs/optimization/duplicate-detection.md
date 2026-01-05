# 중복 작품 탐지 및 병합 시스템

## 개요

크롤링으로 수집한 raw data를 가공할 때 동일 작품을 자동으로 탐지하고 병합하는 시스템입니다.

## 주요 기능

### 1. 유사도 기반 중복 탐지
- **Levenshtein Distance 알고리즘** 사용
- 제목 정규화 (공백, 특수문자 제거, 소문자 변환)
- 기본 유사도 임계값: **85%**

### 2. 중복 후보 검색
도메인별로 author/developer가 같은 작품들을 먼저 필터링:
- **GAME**: `developer` 필드로 검색
- **WEBTOON**: `author` 필드로 검색
- **WEBNOVEL**: `author` 필드로 검색

### 3. 자동 병합
중복이 발견되면:
- ✅ 기존 작품에 **새 플랫폼 정보 추가**
- ✅ **누락된 필드만 보완** (기존 데이터 보존)
- ✅ 도메인별 상세 정보 병합 (장르, 플랫폼 등)

## 아키텍처

### 주요 컴포넌트

```
UpsertService (진입점)
    ├── ContentSimilarityService (유사도 계산)
    ├── ContentMergeService (중복 탐지 및 병합)
    ├── ContentUpsertService (Content 엔티티 관리)
    └── DomainCoreUpsertService (도메인별 데이터 관리)
```

### 처리 흐름

```
1. Raw Data 입력
   ↓
2. Content 엔티티 구성 (아직 저장 X)
   ↓
3. 도메인 데이터 구성 (아직 저장 X)
   ↓
4. 중복 후보 검색 (author/developer 기준)
   ↓
5. 유사도 검사 (Levenshtein Distance)
   ↓
6-A. 중복 발견 (유사도 >= 85%)
     → 기존 작품에 병합
     → 플랫폼 정보 추가
     → 누락 필드 보완
   
6-B. 중복 없음
     → 새 작품으로 저장
```

## 구현 파일

### 새로 추가된 파일

1. **`ContentSimilarityService.java`**
   - 제목 유사도 계산 (Levenshtein Distance)
   - 제목 정규화 로직
   - 위치: `com.example.AOD.service.similarity`

2. **`ContentMergeService.java`**
   - 중복 작품 탐지
   - 작품 병합 로직
   - 플랫폼 정보 추가
   - 위치: `com.example.AOD.service.similarity`

### 수정된 파일

1. **`UpsertService.java`**
   - 중복 체크 로직 통합
   - 저장 전 중복 검사 수행

2. **`ContentUpsertService.java`**
   - `buildContent()` 메서드 추가 (저장 없이 구성만)
   - `saveContent()` 메서드 추가

3. **`DomainCoreUpsertService.java`**
   - `buildDomainData()` 메서드 추가 (저장 없이 구성만)
   - `saveDomainData()` 메서드 추가
   - `createDomainEntity()` 메서드 추가

4. **Repository 파일들**
   - `GameContentRepository.java`: `findByDeveloper()`, `findByPublisher()` 추가
   - `WebtoonContentRepository.java`: `findByAuthor()` 추가
   - `WebnovelContentRepository.java`: `findByAuthor()` 추가

## 사용 예시

### 시나리오 1: 같은 게임이 Steam과 Epic에 모두 있는 경우

```
1. Steam에서 "Elden Ring" 수집 (developer: FromSoftware)
   → 새 작품으로 저장 (ID: 1)
   → Steam 플랫폼 정보 저장

2. Epic에서 "엘든 링" 수집 (developer: FromSoftware)
   → developer가 같은 작품 검색: "Elden Ring" 발견
   → 유사도 계산: "eldenring" vs "엘든링" = 0.89 (≥ 0.85)
   → 중복으로 판단!
   → 기존 작품(ID: 1)에 Epic 플랫폼 정보 추가
   → 누락된 필드(originalTitle: "엘든 링") 보완
```

### 시나리오 2: 같은 웹툰이 네이버와 카카오에 모두 있는 경우

```
1. 네이버에서 "나 혼자만 레벨업" 수집 (author: 추공)
   → 새 작품으로 저장 (ID: 10)
   → 네이버 플랫폼 정보 저장

2. 카카오에서 "Solo Leveling" 수집 (author: 추공)
   → author가 같은 작품 검색: "나 혼자만 레벨업" 발견
   → 유사도 계산: "나혼자만레벨업" vs "solo leveling" = 0.25 (< 0.85)
   → 중복 아님 (영문 제목이라 유사도 낮음)
   → 새 작품으로 저장 (ID: 11)
   
   ⚠️ 한계점: 영문/한글 제목이 완전히 다를 경우 탐지 실패
```

## 설정

### 유사도 임계값 조정

`ContentMergeService.java`:
```java
private static final double SIMILARITY_THRESHOLD = 0.85;  // 85%
```

- **높일수록**: 정확도 증가, 재현율 감소 (보수적)
- **낮출수록**: 재현율 증가, 오탐 가능성 증가

### 추천 설정
- 같은 언어권: **0.85 ~ 0.90**
- 다국어 혼합: **0.70 ~ 0.80** (오탐 주의)

## 로그 예시

### 중복 발견 시
```
INFO  ContentMergeService - 중복 검사: 'Elden Ring' vs '엘든 링' = 0.89 (threshold: 0.85)
INFO  ContentMergeService - 중복 작품 발견! 병합 진행: 엘든 링 -> Elden Ring
INFO  ContentMergeService - 작품 병합 시작: 기존 ID=1, 제목=Elden Ring
INFO  ContentMergeService - 새 플랫폼 정보 추가: Epic (12345)
INFO  ContentMergeService - 작품 병합 완료: ID=1
INFO  UpsertService - 중복 작품으로 병합됨: 1
```

### 중복 없을 시
```
DEBUG ContentMergeService - 중복 후보 2개 발견 (domain: GAME, title: Stardew Valley)
INFO  ContentMergeService - 중복 검사: 'Stardew Valley' vs 'Terraria' = 0.35 (threshold: 0.85)
INFO  ContentMergeService - 중복 검사: 'Stardew Valley' vs 'Minecraft' = 0.23 (threshold: 0.85)
DEBUG ContentSimilarityService - 유사도 검사: 'Stardew Valley' vs 'Terraria' = 0.35
INFO  UpsertService - 새 작품 저장: 15
```

## 테스트 방법

### 1. 중복 탐지 테스트

같은 developer로 비슷한 제목의 게임 2개 크롤링:
```json
// 1차 데이터
{
  "master_title": "The Witcher 3",
  "domain": "GAME",
  "domainDoc": {
    "developer": "CD Projekt RED"
  },
  "platform": {
    "platformName": "Steam",
    "platformSpecificId": "292030"
  }
}

// 2차 데이터 (약간 다른 제목)
{
  "master_title": "더 위쳐 3",
  "domain": "GAME",
  "domainDoc": {
    "developer": "CD Projekt RED"
  },
  "platform": {
    "platformName": "GOG",
    "platformSpecificId": "1207664663"
  }
}
```

**예상 결과**: 
- 1개 Content 생성
- 2개 PlatformData 생성 (Steam, GOG)

### 2. 유사도 임계값 테스트

```java
@Test
public void testSimilarity() {
    ContentSimilarityService service = new ContentSimilarityService();
    
    // 높은 유사도
    assertEquals(1.0, service.calculateSimilarity("Elden Ring", "Elden Ring"));
    assertEquals(0.89, service.calculateSimilarity("Elden Ring", "엘든 링"), 0.1);
    
    // 낮은 유사도
    assertTrue(service.calculateSimilarity("Elden Ring", "Dark Souls") < 0.85);
}
```

## 한계점 및 개선 방향

### 현재 한계

1. **언어 장벽**: 영문 ↔ 한글 완전히 다른 제목은 탐지 실패
   - 예: "Solo Leveling" vs "나 혼자만 레벨업"

2. **성능**: author/developer가 같은 작품이 많으면 검색 비용 증가

3. **오탐 가능성**: 같은 작가의 비슷한 제목 작품 (예: "마법사 1권", "마법사 2권")

### 개선 방안

1. **다국어 제목 매칭**
   - `originalTitle` 필드 활용
   - 영한 제목 DB 구축
   
2. **추가 메타데이터 활용**
   - 출시일(releaseDate) 비교
   - 장르 유사도 추가 검증
   
3. **성능 최적화**
   - 제목 해시값 인덱싱
   - 캐싱 도입

4. **수동 검증 UI**
   - 유사도 0.70~0.85 구간은 관리자 확인 필요
   - "이 작품들은 같은 작품인가요?" UI 제공

## 모니터링

### 중요 지표

- 중복 탐지율 (하루 몇 건 병합되는지)
- 오탐률 (잘못 병합된 케이스)
- 평균 유사도 점수
- author/developer 검색 시간

### 로그 레벨 조정

`application.properties`:
```properties
# 상세 디버그 (개발)
logging.level.com.example.AOD.service.similarity=DEBUG

# 중요 로그만 (운영)
logging.level.com.example.AOD.service.similarity=INFO
```

## 문의

중복 탐지 관련 이슈나 개선 제안은 이슈 트래커에 등록해주세요.
