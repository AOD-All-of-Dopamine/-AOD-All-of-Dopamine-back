# 🔬 포트폴리오용 성능 측정 가이드

## 📌 개요

이 문서는 **최적화 전후 성능 차이를 정량적으로 측정**하여 포트폴리오에 활용하기 위한 가이드입니다.

---

## 🎯 측정 항목

### 1. 처리 시간 (Duration)
- 총 소요 시간 (밀리초, 초)
- 시간 단축 비율 (%)

### 2. 처리 속도 (Throughput)
- 건/초 (items per second)
- 건/분 (items per minute)
- 속도 향상 배율 (몇 배 빨라졌는지)

### 3. 메모리 사용량
- 시작/종료 메모리
- 피크 메모리
- 메모리 증가량

### 4. 처리 성공률
- 전체 항목 수
- 성공/실패 건수
- 성공률 (%)

---

## 🚀 사용 방법

### Step 1: 테스트 데이터 준비

배치 처리용 `raw_items` 테이블에 테스트 데이터를 준비합니다.

```sql
-- 현재 처리 대기 중인 항목 수 확인
SELECT COUNT(*) FROM raw_items WHERE status = 'PENDING';

-- 최소 1000개 이상 준비하는 것을 권장
```

### Step 2: 개별 테스트 실행

#### 🔴 최적화 전 (BEFORE) 테스트

```bash
# Postman 또는 curl 사용
POST http://localhost:8080/api/performance/test/before
Content-Type: application/json

{
  "batchSize": 100,      # 배치 크기 (기존 방식)
  "iterations": 10       # 반복 횟수
}
```

**파라미터 설명**:
- `batchSize`: 한 번에 처리할 항목 수 (100 권장)
- `iterations`: 테스트 반복 횟수 (10회면 총 1000건 처리)

**응답 예시**:
```json
{
  "metrics": {
    "testName": "Batch Processing",
    "version": "BEFORE",
    "durationMs": 45000,
    "successItems": 1000,
    "throughputPerSecond": 22.22,
    "startMemoryMb": 512,
    "endMemoryMb": 768,
    "peakMemoryMb": 850
  },
  "message": "최적화 전 테스트 완료"
}
```

#### 🟢 최적화 후 (AFTER) 테스트

```bash
POST http://localhost:8080/api/performance/test/after
Content-Type: application/json

{
  "batchSize": 500,      # 배치 크기 (최적화 후 증가)
  "iterations": 10       # 반복 횟수
}
```

**파라미터 설명**:
- `batchSize`: 한 번에 처리할 항목 수 (500~1000 권장)
- 최적화로 인해 더 큰 배치 처리 가능

#### 🔥 병렬 처리 테스트 (AFTER+)

```bash
POST http://localhost:8080/api/performance/test/parallel
Content-Type: application/json

{
  "totalItems": 5000,    # 전체 처리 항목 수
  "batchSize": 500,      # 배치 크기
  "numWorkers": 4        # 병렬 워커 수 (CPU 코어 수 고려)
}
```

### Step 3: 자동 비교 테스트 (권장 ⭐)

**Before와 After를 자동으로 비교**합니다:

```bash
POST http://localhost:8080/api/performance/test/compare
Content-Type: application/json

{
  "beforeBatchSize": 100,
  "afterBatchSize": 500,
  "iterations": 5
}
```

**응답 예시**:
```json
{
  "beforeMetrics": { ... },
  "afterMetrics": { ... },
  "speedImprovementFactor": 52.5,
  "timeReductionPercent": 94.3,
  "comparisonSummary": "
    ═══════════════════════════════════════════════════════
    📊 최적화 전후 비교 결과
    ═══════════════════════════════════════════════════════
    
    ⏱️  처리 시간:
       Before: 45,000 ms (45.00초)
       After:  2,560 ms (2.56초)
       개선:   94.3% 단축 ⭐
    
    🚀 처리 속도:
       Before: 22.22 건/초
       After:  1,166.67 건/초
       개선:   52.5배 향상 ⭐⭐⭐
    ...
  "
}
```

### Step 4: CSV 내보내기

측정 결과를 CSV 파일로 저장하여 엑셀로 분석 가능:

```bash
POST http://localhost:8080/api/performance/export/csv
Content-Type: application/json

[
  { 측정결과1 },
  { 측정결과2 },
  ...
]
```

---

## 📊 포트폴리오 활용 예시

### 예시 1: README에 표로 정리

```markdown
## 🚀 성능 최적화 성과

| 항목 | 최적화 전 | 최적화 후 | 개선 |
|-----|---------|---------|-----|
| 처리 시간 (10,000건) | 45.0초 | 2.6초 | **94.3% 단축** |
| 처리 속도 | 22건/초 | 1,167건/초 | **52.5배 향상** |
| 피크 메모리 | 850 MB | 620 MB | 27% 감소 |
| 배치 크기 | 100건 | 500건 | 5배 증가 |

### 주요 최적화 기법
- Hibernate Batch Insert (50건 단위)
- 규칙 캐싱 (파일 I/O 제거)
- 벌크 처리 (saveAll)
- 병렬 워커 (4 threads)
```

### 예시 2: 그래프로 시각화

측정 데이터를 엑셀/구글 시트로 가져와서:
- 막대 그래프: Before vs After 처리 속도
- 꺾은선 그래프: 배치 크기에 따른 성능 변화
- 파이 차트: 메모리 사용량 비교

### 예시 3: 상세 기술 설명

```markdown
## 기술적 개선 사항

### 1. 데이터베이스 최적화
- **문제**: N+1 쿼리로 인한 DB 부하
- **해결**: Hibernate Batch Insert (50건 단위)
- **결과**: DB 호출 횟수 50배 감소

### 2. 메모리 관리
- **문제**: OutOfMemoryError 위험
- **해결**: 주기적 flush/clear (100건마다)
- **결과**: 피크 메모리 27% 감소

### 3. 병렬 처리
- **문제**: 단일 스레드 처리로 CPU 유휴
- **해결**: 멀티 워커 병렬 처리
- **결과**: 4코어 환경에서 3.8배 추가 향상
```

---

## 🎯 권장 테스트 시나리오

### 시나리오 1: 소규모 (빠른 테스트)
```json
{
  "beforeBatchSize": 100,
  "afterBatchSize": 500,
  "iterations": 5
}
```
- 총 처리량: ~2,500건
- 소요 시간: ~5분
- 용도: 빠른 검증

### 시나리오 2: 중규모 (포트폴리오용 권장 ⭐)
```json
{
  "beforeBatchSize": 100,
  "afterBatchSize": 500,
  "iterations": 20
}
```
- 총 처리량: ~10,000건
- 소요 시간: ~15분
- 용도: 포트폴리오 자료

### 시나리오 3: 대규모 (실전 시뮬레이션)
```json
{
  "beforeBatchSize": 100,
  "afterBatchSize": 1000,
  "iterations": 100
}
```
- 총 처리량: ~100,000건
- 소요 시간: ~1시간
- 용도: 실제 운영 환경 검증

---

## 📝 측정 시 주의사항

### 1. 공정한 비교를 위해

✅ **권장사항**:
- 같은 양의 데이터로 테스트
- 테스트 간 서버 재시작 (JVM 워밍업 효과 제거)
- 최소 3회 이상 반복 측정 후 평균 사용
- 다른 부하가 없는 환경에서 측정

❌ **피해야 할 것**:
- Before와 After에 다른 데이터 사용
- 단 1회 측정 결과 사용
- 다른 프로세스가 돌아가는 상태에서 측정

### 2. 로그 확인

테스트 중 콘솔 로그를 확인하여:
```
🔬 성능 측정 시작: Batch Processing (BEFORE)
   시작 시간: 2025-11-11T10:30:00
   시작 메모리: 512 MB

📦 배치 처리 시작: 100 건
...

═══════════════════════════════════════════════════════
📊 성능 측정 결과: Batch Processing
═══════════════════════════════════════════════════════
```

### 3. 데이터베이스 상태 확인

```sql
-- 처리 전 확인
SELECT status, COUNT(*) FROM raw_items GROUP BY status;

-- 처리 후 확인
SELECT status, COUNT(*) FROM raw_items GROUP BY status;
```

---

## 🎨 결과 시각화 도구 추천

1. **Excel / Google Sheets**
   - CSV 내보내기 → 차트 생성
   - 막대, 꺾은선, 파이 차트

2. **Grafana (이미 설정됨)**
   - 실시간 모니터링
   - 메모리, 스레드 사용량 그래프

3. **JMeter / Gatling**
   - 부하 테스트 시뮬레이션
   - HTML 리포트 자동 생성

---

## 🔍 트러블슈팅

### Q1: "처리할 데이터가 없습니다" 에러

```sql
-- raw_items에 PENDING 데이터 추가 필요
INSERT INTO raw_items (status, domain, platform_name, source_payload)
VALUES ('PENDING', 'WEBTOON', 'NAVER', '{"title":"테스트"}');
```

### Q2: OutOfMemoryError 발생

```bash
# JVM 힙 메모리 증가
JAVA_OPTS="-Xmx4g -Xms2g"
./gradlew bootRun
```

### Q3: 테스트가 너무 느림

- `batchSize` 줄이기 (500 → 100)
- `iterations` 줄이기 (10 → 5)
- 데이터양 확인 (너무 많으면 일부만 테스트)

---

## 📚 추가 자료

- [batch-performance-optimization.md](./batch-performance-optimization.md) - 최적화 상세 설명
- [resource-limits.md](./resource-limits.md) - 리소스 제한 설정
- [monitoring/README.md](../monitoring/README.md) - 모니터링 대시보드

---

## ✅ 체크리스트

포트폴리오 자료 준비를 위한 체크리스트:

- [ ] 테스트 데이터 준비 (최소 5,000건)
- [ ] Before 테스트 실행 및 결과 저장
- [ ] After 테스트 실행 및 결과 저장
- [ ] 비교 테스트 실행
- [ ] CSV 내보내기
- [ ] 그래프/차트 생성
- [ ] README 또는 포트폴리오 문서 작성
- [ ] 스크린샷 캡처 (로그, 그래프)
- [ ] 코드 diff 정리 (주요 변경사항)
