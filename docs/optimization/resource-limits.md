# 리소스 제한 설정 (Resource Limits Configuration)

## 📌 개요

서버 안정성을 위해 설정된 모든 리소스 제한값입니다. 이 설정들은 서버가 "터지지 않도록" 동시 실행 가능한 작업량을 제한합니다.

---

## 🎯 핵심 리소스 제한

### 1. 크롤링 스레드 풀 (Crawler Thread Pool)
**파일**: `src/main/java/com/example/AOD/config/AsyncConfig.java`

```properties
Core Pool Size: 5
Maximum Pool Size: 10
Queue Capacity: 200
```

**설명**:
- 동시에 실행 가능한 크롤링 작업: **최대 10개**
- 대기열에 쌓일 수 있는 작업: **최대 200개**
- 대기열 초과 시: CallerRunsPolicy (요청한 스레드에서 직접 실행)

**메모리 영향**:
- Selenium WebDriver 크롤링: 200-400MB/작업
- 10개 동시 실행 시: 2-4GB 메모리 사용

---

### 2. 데이터베이스 연결 풀 (HikariCP)

#### Development 환경 (application.properties)
```properties
maximum-pool-size: 20
minimum-idle: 5
connection-timeout: 30000ms
idle-timeout: 600000ms (10분)
max-lifetime: 1800000ms (30분)
leak-detection-threshold: 60000ms (1분)
```

#### Local 환경 (application-local.properties)
```properties
maximum-pool-size: 15
minimum-idle: 5
```

#### Production 환경 (application-prod.properties)
```properties
maximum-pool-size: 30
minimum-idle: 10
```

**설명**:
- 동시 DB 연결 수 제한으로 PostgreSQL 부하 방지
- Connection Leak 감지 (60초 이상 반환되지 않는 연결 경고)
- 최소 유휴 연결 유지로 성능 최적화

---

### 3. Tomcat 스레드 풀 (HTTP Request Handler)

#### Development 환경
```properties
max-threads: 200
min-spare-threads: 10
max-connections: 10000
accept-count: 100
connection-timeout: 20000ms
```

#### Local 환경
```properties
max-threads: 150
min-spare-threads: 10
max-connections: 5000
accept-count: 100
```

#### Production 환경
```properties
max-threads: 300
min-spare-threads: 20
max-connections: 10000
accept-count: 150
```

**설명**:
- 동시 HTTP 요청 처리 수 제한
- accept-count: 큐가 가득 찬 후 거부되기 전 대기 가능한 연결 수
- connection-timeout: 유휴 연결 타임아웃

---

### 4. WebDriver 인스턴스 제한

**파일**: `src/main/java/com/example/AOD/webtoon/naver/parser/NaverWebtoonSeleniumPageParser.java`

```java
ThreadLocal<WebDriver> + 최대 50회 재사용
```

**설명**:
- 스레드당 1개의 WebDriver 인스턴스 (ThreadLocal)
- 크롤링 스레드 풀이 10개이므로 → **최대 10개의 WebDriver**
- 50회 사용 후 자동 재생성 (메모리 누수 방지)

---

## 🔍 모니터링 지표

### Prometheus Metrics

1. **크롤링 스레드 풀**
   ```
   executor.pool.size{name="crawlerTaskExecutor"}
   executor.active{name="crawlerTaskExecutor"}
   executor.queued{name="crawlerTaskExecutor"}
   ```

2. **HikariCP**
   ```
   hikaricp.connections.active
   hikaricp.connections.idle
   hikaricp.connections.pending
   ```

3. **Tomcat**
   ```
   tomcat.threads.busy
   tomcat.threads.current
   tomcat.sessions.active
   ```

4. **JVM**
   ```
   jvm.memory.used
   jvm.memory.max
   jvm.threads.live
   jvm.gc.pause
   ```

---

## ⚠️ 주의사항

### 1. 스레드 수 관계
```
Total JVM Threads ≠ Crawler Threads

Total JVM Threads = Tomcat Threads (200)
                  + Crawler Threads (10)
                  + HikariCP Threads (20)
                  + GC Threads (4~8)
                  + Spring Internal (~10-20)
                  ≈ 250-270개
```

### 2. 메모리 계산
```
예상 메모리 사용량 = Base JVM (500MB)
                  + WebDriver 10개 (2-4GB)
                  + DB Connections (100MB)
                  + Tomcat Threads (200MB)
                  ≈ 3-5GB
```

### 3. 병목 지점 확인
- **Crawler Queue 가득 참** → 크롤링 속도 < 요청 속도
- **DB Connection Timeout** → DB 쿼리가 너무 느림
- **Tomcat accept-count 초과** → HTTP 요청이 너무 많음

---

## 🛠️ 튜닝 가이드

### 크롤링이 느릴 때
```properties
# AsyncConfig.java에서 수정
maxPoolSize = 15 (10 → 15)
queueCapacity = 300 (200 → 300)
```
**주의**: WebDriver 메모리 사용량 증가!

### DB 연결 부족할 때
```properties
# application.properties
spring.datasource.hikari.maximum-pool-size=30 (20 → 30)
```
**주의**: PostgreSQL의 max_connections 설정 확인 필요!

### HTTP 요청이 많을 때
```properties
# application.properties
server.tomcat.threads.max=300 (200 → 300)
server.tomcat.accept-count=200 (100 → 200)
```
**주의**: CPU 코어 수와 메모리 고려!

---

## 📊 실제 운영 예시

### 정상 상태 (Grafana)
```
Crawler Threads Active: 3-5개
Crawler Queue Size: 0-10개
HikariCP Active: 5-10개
Tomcat Busy Threads: 10-30개
JVM Memory: 2-3GB
```

### 피크 시간 (크롤링 대량 실행)
```
Crawler Threads Active: 10개 (MAX)
Crawler Queue Size: 50-150개
HikariCP Active: 15-18개
Tomcat Busy Threads: 50-100개
JVM Memory: 4-5GB
```

### 위험 상태 (즉시 조치 필요)
```
Crawler Queue Size: 190+ (거의 가득 참)
HikariCP Pending > 5 (연결 대기 중)
Tomcat Busy = Max (요청 처리 불가)
JVM Memory > 90% (GC 과부하)
```

---

## 🚨 알람 설정 (Prometheus Alerts)

**파일**: `monitoring/alerts.yml`

```yaml
- alert: CrawlerQueueAlmostFull
  expr: executor_queued{name="crawlerTaskExecutor"} > 180
  
- alert: DatabaseConnectionHigh
  expr: hikaricp_connections_active > 18
  
- alert: TomcatThreadsExhausted
  expr: tomcat_threads_busy / tomcat_threads_current > 0.9
```

---

## 📝 변경 이력

- **2025-11-03**: 초기 리소스 제한 설정
  - Crawler Thread Pool: max 10
  - HikariCP: max 20 (dev), 15 (local), 30 (prod)
  - Tomcat: max 200 (dev), 150 (local), 300 (prod)
