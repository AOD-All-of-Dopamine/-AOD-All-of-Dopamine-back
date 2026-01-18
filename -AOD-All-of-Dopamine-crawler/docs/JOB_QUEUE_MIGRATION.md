# Job Queue 시스템 도입 - 크롤링 성능 개선 문서

> **작성일**: 2026-01-18  
> **작성자**: 크롤러 팀  
> **상태**: ✅ 완료

---

## 📋 목차
1. [문제 상황](#문제-상황)
2. [근본 원인 분석](#근본-원인-분석)
3. [해결 방안](#해결-방안)
4. [구현 내역](#구현-내역)
5. [마이그레이션 가이드](#마이그레이션-가이드)
6. [성능 개선 효과](#성능-개선-효과)
7. [향후 개선 방향](#향후-개선-방향)

---

## 🔥 문제 상황

### 증상
- **Steam 크롤러가 3일 동안 실행**되면서 단일 스레드를 점유
- 다른 크롤링 작업(TMDB, 웹툰, 소설)이 **대기 상태**로 블로킹됨
- 스케줄러는 정상 동작하지만 실제 크롤링은 **순차적으로만 실행**

### 영향도
- 🔴 **긴급도**: 높음 - 신규 콘텐츠 수집 지연
- 🔴 **영향 범위**: 전체 크롤링 시스템
- 🔴 **비즈니스 임팩트**: 사용자에게 최신 데이터 제공 불가

### 발생 시점
- Steam 전체 게임 크롤링 스케줄 실행 시 (매주 목요일 03:00)
- 크롤링 대상: **150,000개** Steam 게임

---

## 🔍 근본 원인 분석

### 1. 스레드 풀 구성 문제

```yaml
# application.yml (기존 설정)
task:
  execution:
    pool:
      core-size: 1        # ❌ 코어 스레드 1개
      max-size: 1         # ❌ 최대 스레드 1개
      queue-capacity: 5   # ❌ 큐 용량 5개
```

**분석**:
- 단일 스레드로 모든 크롤링 작업 처리
- Steam 작업이 스레드를 점유하면 다른 작업은 큐에서 대기
- 큐 용량 5개 초과 시 작업 거부

### 2. 동기적 대량 처리

```java
// SteamCrawlService.java (기존 코드)
@Async("crawlerTaskExecutor")
public CompletableFuture<Integer> collectAllGamesInBatches() {
    List<Map<String, Object>> gameApps = steamApiFetcher.fetchGameApps(); // 15만개
    
    for (Map<String, Object> app : gameApps) {
        // 3일 동안 루프... 🔥
        collectGameByAppId(appId);
        Thread.sleep(500); // API 제한
    }
}
```

**문제점**:
- 15만개 아이템을 하나의 메서드에서 순차 처리
- `@Async` 어노테이션은 메서드 진입만 비동기, **내부는 동기**
- 스레드 풀의 단일 스레드를 **3일 동안 독점**

### 3. 메모리 vs 스레드 점유 오해

**초기 의문**: "왜 힙 메모리가 지속 증가하는가?"

**분석 결과**:
- 15만개 리스트 = 약 **75MB** 메모리 (실제로는 부담 없음)
- 문제는 메모리가 아니라 **스레드 점유**
- 3일 동안 HTTP 요청/JSON 파싱 객체 생성/소멸로 GC 압박

**결론**: 메모리 최적화보다 **작업 분산**이 핵심

---

## 💡 해결 방안

### 선택한 패턴: Producer-Consumer with Job Queue

#### 왜 Job Queue?

| 문제 | 기존 방식 | Job Queue 방식 |
|------|----------|---------------|
| 스레드 블로킹 | 3일 동안 1개 스레드 점유 | 5초마다 작은 배치 처리 |
| 작업 분산 | 순차 처리만 가능 | 타입별 균등 분배 |
| 장애 복구 | 서버 재시작 시 처음부터 | DB 기반 체크포인트 |
| 우선순위 | 없음 | 우선순위 기반 처리 |
| 모니터링 | 로그뿐 | DB 쿼리로 진행률 확인 |

### 아키텍처 설계

```
┌─────────────────────────────────────────┐
│           MasterScheduler               │
│     (Cron 기반 스케줄 트리거)            │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│          Producer Services              │
│  - SteamSchedulingService               │
│  - TmdbSchedulingService                │
│  - NaverWebtoonSchedulingService        │
│  - NaverSeriesSchedulingService         │
│                                         │
│  역할: 크롤링 대상 목록 수집 → 작업 등록  │
└──────────────┬──────────────────────────┘
               │ createJobs()
               ▼
┌─────────────────────────────────────────┐
│       crawl_job_queue (PostgreSQL)      │
│  - 작업 상태 관리 (PENDING → COMPLETED) │
│  - 중복 방지 (UNIQUE 제약)              │
│  - 재시도 로직 (최대 3회)               │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│    CrawlJobConsumer (5초마다 실행)      │
│                                         │
│  타입별 균등 분배 (per 5초):             │
│  ┌─────────────────────────────────┐   │
│  │ Steam Game:         5개         │   │
│  │ TMDB Movie:         3개         │   │
│  │ TMDB TV Show:       2개         │   │
│  │ Naver Webtoon:      2개         │   │
│  │ Naver Series Novel: 2개         │   │
│  └─────────────────────────────────┘   │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         Crawl Services                  │
│  - 개별 아이템 크롤링 로직               │
│  - API 호출 및 데이터 저장               │
└─────────────────────────────────────────┘
```

---

## 🛠 구현 내역

### 1. 엔티티 설계

#### CrawlJob.java
```java
@Entity
@Table(name = "crawl_job_queue", indexes = {
    @Index(name = "idx_job_status_priority", columnList = "status,priority"),
    @Index(name = "idx_job_type_status", columnList = "jobType,status")
})
public class CrawlJob {
    @Id @GeneratedValue
    private Long id;
    
    @Enumerated(EnumType.STRING)
    private JobType jobType;        // STEAM_GAME, TMDB_MOVIE, etc.
    
    private String targetId;        // appId, movieId, titleId, etc.
    
    @Enumerated(EnumType.STRING)
    private JobStatus status;       // PENDING, PROCESSING, COMPLETED, FAILED
    
    private Integer priority;       // 낮을수록 우선순위 높음 (1~10)
    private Integer retryCount;     // 재시도 횟수 (최대 3회)
    
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
}
```

#### 상태 전이 다이어그램
```
PENDING ──────┐
              │
              ▼
          PROCESSING ─────┐
              │           │ (실패 시)
              │           ▼
              │         RETRY ──┐ (3회 재시도)
              │           │     │
              │ (성공)    │     ▼
              ▼           │   FAILED
          COMPLETED       │
                          ▼
                      PENDING (다시 시도)
```

### 2. Repository with Locking

```java
@Repository
public interface CrawlJobRepository extends JpaRepository<CrawlJob, Long> {
    
    // 비관적 락 + SKIP LOCKED (PostgreSQL 9.5+)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM CrawlJob j WHERE j.jobType = :jobType " +
           "AND j.status = 'PENDING' ORDER BY j.priority ASC, j.createdAt ASC")
    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "0")})
    List<CrawlJob> findPendingJobsByTypeWithLock(
        @Param("jobType") JobType jobType, 
        Pageable pageable
    );
}
```

**핵심 포인트**:
- `PESSIMISTIC_WRITE`: 동시성 제어 (여러 Consumer 인스턴스 지원)
- `SKIP LOCKED`: 락 걸린 행은 건너뛰고 다음 행 처리 (대기 시간 제로)
- 우선순위 + 생성시간 순 정렬

### 3. Producer 구현

```java
@Service
@RequiredArgsConstructor
public class SteamSchedulingService {
    
    private final SteamApiFetcher steamApiFetcher;
    private final CrawlJobProducer crawlJobProducer;
    
    public void collectSteamGamesWeekly() {
        // 1. API에서 목록만 가져오기 (빠름)
        List<Map<String, Object>> gameApps = steamApiFetcher.fetchGameApps();
        
        // 2. appId만 추출
        List<String> appIds = gameApps.stream()
            .map(app -> String.valueOf(app.get("appid")))
            .collect(Collectors.toList());
        
        // 3. Job Queue에 등록하고 즉시 반환 ✅
        int created = crawlJobProducer.createJobs(JobType.STEAM_GAME, appIds, 5);
        
        log.info("✅ Steam 게임 {} 개 작업 생성 완료", created);
        // 여기서 종료! 실제 크롤링은 Consumer가 처리
    }
}
```

**변경 전후 비교**:
| 항목 | 기존 방식 | Job Queue 방식 |
|------|----------|---------------|
| 실행 시간 | 3일 | 5초 (목록 수집만) |
| 스레드 점유 | 3일 | 5초 |
| 반환 시점 | 크롤링 완료 후 | 작업 등록 즉시 |

### 4. Consumer 구현

```java
@Service
@RequiredArgsConstructor
public class CrawlJobConsumer {
    
    private final CrawlJobRepository repository;
    private final SteamCrawlService steamCrawlService;
    private final TmdbService tmdbService;
    // ... 기타 서비스들
    
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    @Transactional
    public void processBatchBalanced() {
        // 타입별 균등 분배
        int steamProcessed = processByType(JobType.STEAM_GAME, 5);
        int tmdbMovieProcessed = processByType(JobType.TMDB_MOVIE, 3);
        int tmdbTvProcessed = processByType(JobType.TMDB_TV, 2);
        int webtoonProcessed = processByType(JobType.NAVER_WEBTOON, 2);
        int novelProcessed = processByType(JobType.NAVER_SERIES_NOVEL, 2);
        
        int total = steamProcessed + tmdbMovieProcessed + tmdbTvProcessed 
                  + webtoonProcessed + novelProcessed;
        
        if (total > 0) {
            log.info("📦 배치 처리 완료 - Steam:{}, TMDB-M:{}, TMDB-TV:{}, 웹툰:{}, 소설:{}", 
                    steamProcessed, tmdbMovieProcessed, tmdbTvProcessed, 
                    webtoonProcessed, novelProcessed);
        }
    }
    
    private int processByType(JobType jobType, int limit) {
        List<CrawlJob> jobs = repository.findPendingJobsByTypeWithLock(jobType, limit);
        
        for (CrawlJob job : jobs) {
            processJob(job); // 개별 작업 처리
        }
        
        repository.saveAll(jobs);
        return jobs.size();
    }
}
```

**핵심 설계**:
- ⏰ 5초마다 실행: 부하 분산 + 빠른 응답성
- 🎯 타입별 할당량: Steam 많이(5), TMDB 중간(3+2), 웹툰/소설 적게(2)
- 🔄 균등 분배: 모든 도메인이 공평하게 처리됨

### 5. 기존 서비스 적응

#### 단일 아이템 크롤링 메서드 추가
```java
// TmdbService.java
public boolean collectMovieById(String movieId) {
    String movieJson = tmdbApiFetcher.fetchMovieDetails(movieId, "ko-KR");
    String payload = payloadProcessor.buildPayload(movieJson, "movie");
    collectorService.sendPayload(payload);
    return true;
}

// NaverWebtoonService.java
public boolean collectWebtoonById(String titleId) {
    // 웹툰 단일 크롤링 로직
    return true;
}
```

### 6. Admin 컨트롤러 통합

```java
@RestController
@RequestMapping("/api")
public class AdminTestController {
    
    private final SteamSchedulingService steamSchedulingService;
    private final NaverWebtoonSchedulingService webtoonSchedulingService;
    
    @PostMapping("/crawl/steam/all-games")
    public Map<String, Object> crawlSteamAllGames() {
        steamSchedulingService.collectSteamGamesWeekly();
        return Map.of(
            "success", true,
            "message", "Steam 게임 크롤링 작업이 Job Queue에 등록되었습니다. " +
                      "Consumer가 5초마다 처리합니다."
        );
    }
}
```

**변경점**:
- ❌ 기존: 크롤링 완료까지 대기 → 응답 반환
- ✅ 신규: 작업 등록 → 즉시 응답 반환

---

## 📚 마이그레이션 가이드

### Step 1: DB 설정 변경

```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # none → update 변경
```

**효과**: Hibernate가 `CrawlJob` 엔티티를 보고 자동으로 테이블 생성

### Step 2: 테이블 확인

```sql
-- PostgreSQL에서 확인
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' AND table_name = 'crawl_job_queue';

-- 인덱스 확인
SELECT indexname FROM pg_indexes WHERE tablename = 'crawl_job_queue';
```

### Step 3: 애플리케이션 재시작

```bash
# Gradle 빌드
./gradlew clean build

# 애플리케이션 실행
./deploy-local.sh  # Linux/macOS
deploy-local.bat   # Windows
```

### Step 4: 동작 확인

#### 로그 모니터링
```
[Steam Producer] Steam 게임 150000 개 작업 생성 완료
[Consumer] 배치 처리 완료 - Steam:5, TMDB-M:3, TMDB-TV:2, 웹툰:2, 소설:2
```

#### DB 쿼리
```sql
-- 대기 중인 작업 수
SELECT job_type, COUNT(*) 
FROM crawl_job_queue 
WHERE status = 'PENDING' 
GROUP BY job_type;

-- 처리 중인 작업
SELECT job_type, target_id, started_at 
FROM crawl_job_queue 
WHERE status = 'PROCESSING';

-- 완료율
SELECT 
    job_type,
    COUNT(*) FILTER (WHERE status = 'COMPLETED') * 100.0 / COUNT(*) as completion_rate
FROM crawl_job_queue 
GROUP BY job_type;
```

---

## 📊 성능 개선 효과

### 1. 스레드 점유 시간

| 항목 | 기존 | 개선 | 개선률 |
|------|------|------|-------|
| Steam 크롤링 스케줄 실행 시간 | 3일 (72시간) | 5초 | **99.998%** ↓ |
| 스레드 블로킹 시간 | 3일 | 0초 | **100%** 해소 |

### 2. 동시 처리 능력

| 시나리오 | 기존 | 개선 |
|---------|------|------|
| Steam + TMDB 동시 크롤링 | ❌ 불가능 (순차) | ✅ 가능 (5초마다 5+3+2) |
| 5개 도메인 동시 크롤링 | ❌ 불가능 | ✅ 가능 (균등 분배) |

### 3. 장애 복구

| 시나리오 | 기존 | 개선 |
|---------|------|------|
| 서버 재시작 | 처음부터 다시 | DB에서 PENDING 작업 이어서 처리 |
| 부분 실패 | 전체 롤백 | 실패 작업만 RETRY 상태로 재시도 |

### 4. 실시간 처리량

**계산**:
- Consumer 주기: 5초
- Steam 처리량: 5개/배치
- **1시간**: 3,600초 ÷ 5초 × 5개 = **3,600개**
- **1일**: 3,600개 × 24시간 = **86,400개**
- **15만개 완료 시간**: 150,000 ÷ 86,400 ≈ **1.74일**

**기존 대비**:
- 기존: 3일 (72시간)
- 개선: 1.74일 (41.7시간)
- **다른 작업도 동시 처리** ✅

---

## 🚀 향후 개선 방향

### 1. 처리량 조절 (Runtime Tuning)

```java
// 피크 시간대에는 처리량 증가
@Scheduled(cron = "0 0 2-6 * * *") // 새벽 2~6시
public void processBatchHighSpeed() {
    processByType(JobType.STEAM_GAME, 20);  // 5 → 20
    processByType(JobType.TMDB_MOVIE, 10);  // 3 → 10
}

// 일반 시간대
@Scheduled(fixedDelay = 5000)
public void processBatchNormal() {
    processByType(JobType.STEAM_GAME, 5);
}
```

### 2. 우선순위 동적 조정

```java
// 신규 콘텐츠 우선순위 높이기
public void prioritizeRecentContent() {
    repository.updatePriorityByDate(
        LocalDateTime.now().minusDays(7),  // 최근 7일
        1  // 최고 우선순위
    );
}
```

### 3. 분산 처리 (Multi-Instance)

```
Consumer Instance 1     Consumer Instance 2
       ↓                        ↓
  SKIP LOCKED          SKIP LOCKED
       ↓                        ↓
   작업 A, B, C             작업 D, E, F
```

**이미 지원됨**:
- `PESSIMISTIC_WRITE + SKIP LOCKED`로 여러 인스턴스 동시 실행 가능
- 스케일 아웃으로 처리량 증대

### 4. 모니터링 대시보드

```java
@RestController
@RequestMapping("/api/queue")
public class QueueMonitorController {
    
    @GetMapping("/stats")
    public QueueStats getQueueStats() {
        return QueueStats.builder()
            .totalPending(repository.countByStatus(JobStatus.PENDING))
            .totalProcessing(repository.countByStatus(JobStatus.PROCESSING))
            .totalCompleted(repository.countByStatus(JobStatus.COMPLETED))
            .byType(repository.countGroupByType())
            .build();
    }
}
```

Grafana 대시보드:
- 큐 깊이 (Pending 작업 수)
- 처리 속도 (완료/시간)
- 실패율 (Failed / Total)
- 타입별 분포

### 5. Dead Letter Queue (DLQ)

```java
// 3회 재시도 후에도 실패한 작업
public void moveToDLQ() {
    List<CrawlJob> failedJobs = repository.findByStatusAndRetryCountGreaterThan(
        JobStatus.FAILED, 3
    );
    
    failedJobs.forEach(job -> {
        // DLQ 테이블로 이동
        dlqRepository.save(DeadLetterJob.from(job));
        repository.delete(job);
    });
}
```

### 6. Rate Limiting per Domain

```java
// 도메인별 API 제한 준수
@Component
public class RateLimiter {
    private final Map<JobType, Bucket> buckets = new ConcurrentHashMap<>();
    
    public boolean tryConsume(JobType type) {
        Bucket bucket = buckets.computeIfAbsent(type, this::createBucket);
        return bucket.tryConsume(1);
    }
    
    private Bucket createBucket(JobType type) {
        switch (type) {
            case STEAM_GAME:
                return Bucket.builder()
                    .addLimit(Bandwidth.simple(200, Duration.ofMinutes(5)))
                    .build();
            case TMDB_MOVIE:
                return Bucket.builder()
                    .addLimit(Bandwidth.simple(40, Duration.ofSeconds(10)))
                    .build();
            // ...
        }
    }
}
```

---

## 📖 관련 문서

- [Copilot Instructions](../.github/copilot-instructions.md)
- [DB Setup Guide](../DB_SETUP_GUIDE.md)
- [Architecture Overview](../docs/architecture/system-architecture.md)

---

## 🎯 결론

### 핵심 성과
1. ✅ **스레드 블로킹 해소**: 3일 → 5초
2. ✅ **동시 처리 지원**: 5개 도메인 균등 분배
3. ✅ **장애 복구**: DB 기반 체크포인트
4. ✅ **확장성**: 멀티 인스턴스 지원
5. ✅ **모니터링**: DB 쿼리로 진행률 추적

### 교훈
- 메모리 최적화보다 **작업 분산**이 더 중요
- `@Async`는 메서드 진입만 비동기, **내부 로직은 동기**
- 대량 처리는 **작은 배치로 분할** + **주기적 실행**
- DB 기반 큐는 **내구성**과 **가시성** 제공

### 다음 단계
- [ ] Grafana 대시보드 구축
- [ ] 프로덕션 환경 배포
- [ ] 1주일 모니터링 후 처리량 튜닝
- [ ] DLQ 및 알림 시스템 추가
