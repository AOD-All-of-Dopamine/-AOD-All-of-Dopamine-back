# 플랫폼 필터링 메모리 최적화

## 📋 문제 발견

### Sentry를 통한 프로덕션 에러 포착
- **일시**: 2024년 12월 16일 오전 12:52
- **환경**: Production
- **에러**: `OutOfMemoryError: Java heap space`
- **요청**: `GET /api/works?domain=GAME&platforms[]=steam&page=0&size=20`
- **소요 시간**: 82초 후 서버 크래시

### Sentry 에러 상세
```
ServletException: Handler dispatch failed
  ↳ OutOfMemoryError: Java heap space
  
Stack Trace:
  at JwtAuthenticationFilter.doFilterInternal()
  at DispatcherServlet.doDispatch()
  at WorkApiService.getWorksByPlatforms()
```

---

## 🔍 근본 원인 분석

### 1. 문제 코드 위치
**파일**: `WorkApiService.java` (147-165줄)

```java
private PageResponse<WorkSummaryDTO> getWorksByPlatforms(
    Domain domain, String keyword, List<String> platforms, Pageable pageable
) {
    // ❌ 문제: 전체 데이터를 메모리에 로드
    List<Content> allContent;
    if (keyword != null && !keyword.isBlank()) {
        if (domain != null) {
            allContent = contentRepository.searchByDomainAndKeyword(
                domain, keyword, Pageable.unpaged()  // 🔴 문제!
            ).getContent();
        } else {
            allContent = contentRepository.searchByKeyword(
                keyword, Pageable.unpaged()  // 🔴 문제!
            ).getContent();
        }
    } else if (domain != null) {
        allContent = contentRepository.findByDomain(
            domain, Pageable.unpaged()  // 🔴 문제!
        ).getContent();
    } else {
        allContent = contentRepository.findAll(
            Pageable.unpaged()  // 🔴 문제!
        ).getContent();
    }
    
    // 메모리에서 플랫폼 필터링
    List<Content> filtered = allContent.stream()
        .filter(c -> filterByPlatforms(c, platforms))
        .collect(Collectors.toList());
    
    return applyPaginationAndMapping(filtered, pageable);
}
```

### 2. 왜 문제가 발생했는가?

#### 메모리 낭비 구조
```
DB (PostgreSQL)
   ↓
   ↓ SELECT * FROM contents WHERE domain = 'GAME'  (50,000+ rows)
   ↓
JVM Heap Memory
   ├─ List<Content> allContent [50,000+ objects]
   ├─ Each Content object: ~2KB
   ├─ Total: ~100MB+ for one request
   ↓
Stream Filter (In-Memory)
   ├─ PlatformData JOIN in Java code
   ├─ Additional memory for filtering
   ↓
Result: 20 items (요청한 페이지 크기)
```

#### 구체적 문제점
1. **`Pageable.unpaged()` 사용**
   - 페이징 없이 모든 레코드를 조회
   - Steam 게임 데이터만 **50,000개 이상**
   
2. **메모리 기반 필터링**
   - DB에서 모든 데이터를 가져온 후
   - Java Stream으로 플랫폼 필터링
   - `filterByPlatforms()` 메서드가 추가로 DB 쿼리 실행 (N+1 유사)
   
3. **비효율적 데이터 처리**
   - 필요: 20개 작품
   - 로드: 50,000개 작품
   - **낭비율: 99.96%**

---

## ✅ 해결 방법

### 1단계: DB 레벨 플랫폼 필터링 쿼리 추가

#### ContentRepository.java에 새로운 메서드 추가

```java
// 도메인 + 플랫폼 필터링
@Query("SELECT DISTINCT c FROM Content c " +
       "JOIN PlatformData pd ON pd.content = c " +
       "WHERE c.domain = :domain AND LOWER(pd.platformName) IN :platforms")
Page<Content> findByDomainAndPlatforms(
    @Param("domain") Domain domain,
    @Param("platforms") List<String> platforms,
    Pageable pageable
);

// 플랫폼 필터링만 (도메인 무관)
@Query("SELECT DISTINCT c FROM Content c " +
       "JOIN PlatformData pd ON pd.content = c " +
       "WHERE LOWER(pd.platformName) IN :platforms")
Page<Content> findByPlatforms(
    @Param("platforms") List<String> platforms,
    Pageable pageable
);

// 도메인 + 키워드 + 플랫폼 필터링
@Query("SELECT DISTINCT c FROM Content c " +
       "JOIN PlatformData pd ON pd.content = c " +
       "WHERE c.domain = :domain " +
       "AND (LOWER(c.masterTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
       "     LOWER(c.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
       "AND LOWER(pd.platformName) IN :platforms")
Page<Content> findByDomainAndKeywordAndPlatforms(
    @Param("domain") Domain domain,
    @Param("keyword") String keyword,
    @Param("platforms") List<String> platforms,
    Pageable pageable
);

// 키워드 + 플랫폼 필터링 (도메인 무관)
@Query("SELECT DISTINCT c FROM Content c " +
       "JOIN PlatformData pd ON pd.content = c " +
       "WHERE (LOWER(c.masterTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
       "       LOWER(c.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
       "AND LOWER(pd.platformName) IN :platforms")
Page<Content> findByKeywordAndPlatforms(
    @Param("keyword") String keyword,
    @Param("platforms") List<String> platforms,
    Pageable pageable
);
```

### 2단계: WorkApiService 리팩토링

#### 개선된 코드

```java
/**
 * 플랫폼 필터링만 있는 경우
 * ⚠️ 개선: DB 레벨에서 플랫폼 필터링 (메모리 부하 해결)
 */
private PageResponse<WorkSummaryDTO> getWorksByPlatforms(
    Domain domain, String keyword, List<String> platforms, Pageable pageable
) {
    // 플랫폼 이름을 소문자로 변환 (쿼리에서 LOWER 사용)
    List<String> lowerPlatforms = platforms.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toList());
    
    Page<Content> contentPage;
    
    // ✅ DB 레벨에서 플랫폼 필터링
    if (keyword != null && !keyword.isBlank()) {
        if (domain != null) {
            contentPage = contentRepository.findByDomainAndKeywordAndPlatforms(
                domain, keyword, lowerPlatforms, pageable
            );
        } else {
            contentPage = contentRepository.findByKeywordAndPlatforms(
                keyword, lowerPlatforms, pageable
            );
        }
    } else if (domain != null) {
        contentPage = contentRepository.findByDomainAndPlatforms(
            domain, lowerPlatforms, pageable
        );
    } else {
        contentPage = contentRepository.findByPlatforms(
            lowerPlatforms, pageable
        );
    }
    
    // ✅ 이미 페이징된 결과를 DTO로 변환
    return PageResponse.<WorkSummaryDTO>builder()
            .content(contentPage.getContent().stream()
                    .map(this::toWorkSummary)
                    .collect(Collectors.toList()))
            .page(contentPage.getNumber())
            .size(contentPage.getSize())
            .totalElements(contentPage.getTotalElements())
            .totalPages(contentPage.getTotalPages())
            .first(contentPage.isFirst())
            .last(contentPage.isLast())
            .build();
}
```

### 3단계: 기존 메서드 Deprecated 처리

```java
/**
 * 플랫폼 필터링 헬퍼 메서드 (복수 플랫폼 지원)
 * @deprecated DB 레벨 필터링 사용 - findByPlatforms in ContentRepository
 * 메모리 필터링이 필요한 경우에만 사용
 */
@Deprecated
private boolean filterByPlatforms(Content content, List<String> platforms) {
    if (platforms == null || platforms.isEmpty()) {
        return true;
    }
    List<PlatformData> platformDataList = platformDataRepository.findByContent(content);
    return platformDataList.stream()
            .anyMatch(pd -> platforms.stream()
                    .anyMatch(platform -> pd.getPlatformName().equalsIgnoreCase(platform)));
}
```

---

## 📊 성능 개선 효과

### 메모리 사용량 비교

| 항목 | 개선 전 | 개선 후 | 개선율 |
|------|---------|---------|--------|
| **조회 레코드 수** | 50,000개 | 20개 | **99.96% 감소** |
| **메모리 사용량** | ~100MB+ | ~40KB | **99.96% 감소** |
| **응답 시간** | 82초 (OOM) | ~1-2초 예상 | **97% 개선** |
| **DB 쿼리 횟수** | 1 + N (필터링) | 1 (JOIN) | **N회 감소** |

### 쿼리 실행 계획 비교

#### 개선 전
```sql
-- 1단계: 전체 데이터 조회
SELECT * FROM contents WHERE domain = 'GAME';  -- 50,000 rows

-- 2단계: Java 코드에서 각 Content마다 PlatformData 조회 (Lazy Loading)
SELECT * FROM platform_data WHERE content_id = ?;  -- N회 실행
```

#### 개선 후
```sql
-- 한 번의 JOIN 쿼리로 해결
SELECT DISTINCT c.* 
FROM contents c
JOIN platform_data pd ON pd.content_id = c.content_id
WHERE c.domain = 'GAME' 
  AND LOWER(pd.platform_name) IN ('steam')
LIMIT 20 OFFSET 0;  -- 페이징 적용
```

---

## 🎯 API 호출 흐름

### 프론트엔드 → 백엔드

```
1. 프론트엔드 (explore-page.tsx)
   ↓
   useWorks({
     domain: "GAME",
     platforms: ["steam"],
     page: 0,
     size: 20
   })

2. API Client (workApi.ts)
   ↓
   GET /api/works?domain=GAME&platforms[]=steam&page=0&size=20

3. Controller (WorkController.java)
   ↓
   @GetMapping
   getWorks(@RequestParam List<String> platforms, ...)

4. Service (WorkApiService.java)
   ↓
   getWorksByPlatforms(...)

5. Repository (ContentRepository.java)
   ↓
   findByDomainAndPlatforms(domain, platforms, pageable)
   
6. Database (PostgreSQL)
   ↓
   SELECT DISTINCT c.* FROM contents c
   JOIN platform_data pd ON pd.content_id = c.content_id
   WHERE c.domain = 'GAME' AND LOWER(pd.platform_name) IN ('steam')
   LIMIT 20;
```

---

## 🛡️ 추가 안전 장치 (임시 완충)

### Dockerfile JVM 힙 설정 추가

```dockerfile
# 기존
ENTRYPOINT ["java", "-jar", "app.jar"]

# 개선 (임시 완충책)
ENTRYPOINT ["java", \
    "-Xms512m", \
    "-Xmx2048m", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/heapdump.hprof", \
    "-jar", "app.jar"]
```

**목적**: 
- 근본 원인 해결 전 임시 완충
- OOM 발생 시 힙 덤프 생성으로 디버깅 지원
- 최대 힙 2GB로 제한하여 컨테이너 안정성 확보

---

## 📝 교훈 및 Best Practices

### 1. 페이징 필수 적용
- ❌ `Pageable.unpaged()` 사용 금지
- ✅ 항상 `PageRequest.of(page, size)` 사용

### 2. 필터링은 DB에서
- ❌ Java Stream으로 대량 데이터 필터링
- ✅ SQL WHERE, JOIN으로 DB 레벨 필터링

### 3. N+1 문제 주의
- ❌ Lazy Loading으로 반복 쿼리
- ✅ JOIN FETCH 또는 DTO Projection

### 4. 모니터링 중요성
- ✅ Sentry를 통해 프로덕션 에러 실시간 포착
- ✅ 에러 발생 시 즉시 근본 원인 분석 가능
- ✅ Stack Trace와 Request 정보로 빠른 문제 해결

### 5. 성능 테스트
```java
// 로컬 개발 시 데이터 볼륨 테스트
@Test
void testPlatformFilteringPerformance() {
    // 50,000개 데이터로 테스트
    List<String> platforms = List.of("steam");
    Pageable pageable = PageRequest.of(0, 20);
    
    long startTime = System.currentTimeMillis();
    Page<Content> result = contentRepository.findByDomainAndPlatforms(
        Domain.GAME, platforms, pageable
    );
    long duration = System.currentTimeMillis() - startTime;
    
    assertThat(duration).isLessThan(1000); // 1초 이내
    assertThat(result.getContent()).hasSize(20);
}
```

---

## 🔄 향후 개선 사항

### 1. 인덱스 최적화
```sql
-- platform_data 테이블에 복합 인덱스 추가
CREATE INDEX idx_platform_data_platform_content 
ON platform_data(platform_name, content_id);

-- contents 테이블 기존 인덱스 확인
-- (domain, masterTitle, releaseDate) 인덱스 이미 존재
```

### 2. 캐싱 전략
```java
@Cacheable(value = "works", key = "#domain + '_' + #platforms + '_' + #pageable")
public Page<Content> findByDomainAndPlatforms(
    Domain domain, List<String> platforms, Pageable pageable
) {
    // ...
}
```

### 3. 쿼리 성능 모니터링
- Hibernate 쿼리 로깅 활성화
- Slow Query 모니터링 추가
- APM (Application Performance Monitoring) 도입 검토

---

## 📅 타임라인

- **2024-12-16 00:52**: Sentry가 프로덕션 OutOfMemoryError 포착
- **2024-12-16 09:00**: 근본 원인 분석 (Pageable.unpaged() 발견)
- **2024-12-16 09:30**: DB 레벨 필터링 쿼리 구현
- **2024-12-16 10:00**: WorkApiService 리팩토링 완료
- **2024-12-16 10:15**: 로컬 테스트 성공
- **배포 대기**: 프로덕션 배포 및 모니터링

---

## ✅ 체크리스트

- [x] 근본 원인 식별 (Pageable.unpaged())
- [x] DB 레벨 필터링 쿼리 구현
- [x] WorkApiService 리팩토링
- [x] 기존 메서드 Deprecated 처리
- [x] Dockerfile JVM 힙 설정 (임시 완충)
- [x] 문서화
- [ ] 프로덕션 배포
- [ ] Sentry 모니터링으로 검증
- [ ] 성능 테스트 결과 기록

---

## 🔗 관련 문서

- [Batch Performance Optimization](./batch-performance-optimization.md)
- [Thread Resource Issues](../optimization/thread-resource-issues.md)
- [Sentry Integration](../monitoring/ACTUATOR_INTEGRATION_COMPLETE.md)
