# Job Queue 시스템 DB 설정 가이드

## 1. DB 테이블 생성

PostgreSQL이 실행 중일 때 다음 명령어로 테이블을 생성하세요:

### Windows (PowerShell)
```powershell
# Docker로 PostgreSQL 실행 중인 경우
docker exec -i <postgres-container-name> psql -U postgres -d aodDB -f - < -AOD-All-of-Dopamine-crawler\src\main\resources\db\migration\V2__create_crawl_job_queue.sql

# 또는 로컬 PostgreSQL인 경우
$env:PGPASSWORD='password'; psql -U postgres -d aodDB -h localhost -p 5432 -f "-AOD-All-of-Dopamine-crawler\src\main\resources\db\migration\V2__create_crawl_job_queue.sql"
```

### Linux/macOS
```bash
# Docker로 PostgreSQL 실행 중인 경우
docker exec -i <postgres-container-name> psql -U postgres -d aodDB < -AOD-All-of-Dopamine-crawler/src/main/resources/db/migration/V2__create_crawl_job_queue.sql

# 또는 로컬 PostgreSQL인 경우
PGPASSWORD='password' psql -U postgres -d aodDB -h localhost -p 5432 -f -AOD-All-of-Dopamine-crawler/src/main/resources/db/migration/V2__create_crawl_job_queue.sql
```

## 2. 테이블 구조

생성되는 테이블: `crawl_job_queue`

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL | 자동 증가 PK |
| job_type | VARCHAR(50) | 작업 타입 (STEAM_GAME, TMDB_MOVIE 등) |
| target_id | VARCHAR(255) | 크롤링 대상 ID |
| status | VARCHAR(20) | 상태 (PENDING, PROCESSING, COMPLETED 등) |
| priority | INTEGER | 우선순위 (낮을수록 먼저 처리) |
| retry_count | INTEGER | 재시도 횟수 |
| created_at | TIMESTAMP | 생성 시각 |
| updated_at | TIMESTAMP | 수정 시각 |
| started_at | TIMESTAMP | 시작 시각 |
| completed_at | TIMESTAMP | 완료 시각 |
| error_message | TEXT | 에러 메시지 |

### 인덱스
- `idx_job_status_priority`: status + priority (큐 조회 최적화)
- `idx_job_type_status`: job_type + status (타입별 조회 최적화)
- `idx_job_created_at`: created_at (시간 기반 조회)

### 제약 조건
- `uk_job_type_target`: job_type + target_id UNIQUE (중복 방지)

## 3. 확인

테이블이 정상적으로 생성되었는지 확인:

```sql
-- 테이블 존재 확인
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' AND table_name = 'crawl_job_queue';

-- 컬럼 확인
\d crawl_job_queue

-- 인덱스 확인
SELECT indexname FROM pg_indexes WHERE tablename = 'crawl_job_queue';
```

## 4. 다음 단계

테이블 생성 후:
1. ✅ 애플리케이션 실행
2. ✅ MasterScheduler가 Producer를 호출하여 Job 생성
3. ✅ CrawlJobConsumer가 5초마다 작업 처리
4. ✅ 모니터링 대시보드에서 큐 상태 확인

## 5. 트러블슈팅

### PostgreSQL이 실행되지 않는 경우
```bash
# Docker Compose 사용 시
docker-compose up -d postgres

# Docker 직접 실행
docker run -d --name postgres \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=aodDB \
  -p 5432:5432 \
  postgres:15
```

### 테이블이 이미 존재하는 경우
```sql
-- 테이블 삭제 후 재생성
DROP TABLE IF EXISTS crawl_job_queue CASCADE;
```
