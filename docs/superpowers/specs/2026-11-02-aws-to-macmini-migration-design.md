# AWS → 맥미니(main-server) 백엔드 이전 설계

작성: 2026-11-02 · 상태: 사용자 승인(2026-11-02) · 레포: `-AOD-All-of-Dopamine-back` (코드 변경과 배포 자산이 여기 모임)

## 0. 배경과 목표

AWS 크레딧이 끝나 **이미 과금 중**이다. AWS에 있는 백엔드 전부(API EC2 · 크롤러 EC2 · RDS · ECR)를 사용자의 맥미니 OrbStack VM `main-server`로 옮긴다. 추천 서빙은 이미 그 VM에서 운영 중이므로 그대로 두고, API가 라우터를 Docker 네트워크로 부르게 바꾼다.

**목표**
- `api.allofdophamin.com` 호스트명 유지 → Vercel 웹 프록시(`apps/web/api/[...path].ts`)와 EAS 모바일 빌드는 재배포 없음.
- 데이터 유실 0. 전환 다운타임 15~25분 안(최종 덤프·복원·검증 시간).
- 오늘 안에 RDS 전체 덤프를 AWS 밖에 확보한다(최우선).
- 전환 뒤 AWS 자원을 전부 정리해 과금을 끊는다.

**비범위(후속 작업)**
- 모니터링 스택(Prometheus·Grafana·Loki) 이전 — prod 설정이 placeholder 상태라 지금도 미배포.
- CI/CD 2단계(GHCR + arm64 호스트드 러너 + Tailscale SSH 배포) 상세 — §9에 개요만.
- AI 배치 파이프라인(Vane·OpenAI 배치)의 DB 접속 경로 변경 — §10 운영 메모로만.

## 1. 현재 상태 (2026-11-02 확인한 사실)

| 구성요소 | 사실 | 출처 |
|---|---|---|
| API EC2 | t3.small, 인스턴스 `i-0718dec7cc7bf2037`, EIP `13.125.165.117`, Ubuntu + nginx 1.24(TLS 종단) → 컨테이너 `aod-app:8080`. Tailscale 노드 `aod-api`(100.126.116.57) | `main.yml`, `curl -I`, tailscale status, 맥 세션 |
| 크롤러 EC2 | t3.small 별도 인스턴스, 컨테이너 `aod-crawler:8081`, Chrome/Selenium 내장. Tailscale 미가입 | `main.yml`, crawler compose |
| RDS | `<RDS_HOST>`, PostgreSQL 17.9, `vector 0.8.1`, DB `postgres` 2,506 MB. 스키마 public(29 테이블, 2,487 MB) · aod_ai(9) · aod_log(30) · aod_rec(3). 모든 객체 소유자 `postgres`. 롤 `postgres`(마스터, 비superuser) · `aod_ai`(login) · `aod_public_ro`(nologin). `flyway_schema_history` baseline 3부터 V12 이상(코드에 V13 참조 있음 — 정확한 값은 verify.sql이 RDS에서 뽑는다). 이 PC에서 5432 접속 가능 | psql 읽기 조회 |
| 큰 테이블 | raw_items 1,380 MB(259k) · platform_data 532 MB · contents 275 MB(245k) · crawl_job_queue 132 MB · game_contents 79 MB · transform_runs 71 MB | psql |
| 권한 | `aod_ai`는 public·aod_ai만 읽음. **aod_log·aod_rec 33개 테이블은 못 읽음 → 전체 덤프는 마스터 `postgres` 계정 필요**. `aod_ai`는 `aod_public_ro`의 멤버. 기본 권한 3건(`postgres`가 public 테이블 SELECT→aod_public_ro, aod_ai 테이블 arwd→aod_ai, aod_ai 시퀀스 rU→aod_ai) | psql `has_table_privilege`, `pg_auth_members`, `pg_default_acl` |
| RDS 설정 | `timezone=UTC`, `max_connections=80`, 조사 시점 연결 13개 | psql `SHOW` |
| ECR | `aod-api`, `aod-crawler` (ap-northeast-2). CI가 push → EC2에서 pull | `main.yml` |
| IAM | 배포용 사용자 `claude-aod-deploy`(키가 맥 `~/.aws`와 과거 채팅에 노출) | 맥 세션 |
| 보안그룹 | `AOD_Test_database`(sg-0cb00cf8d355bf475)가 두 EC2에 붙어 있고 0.0.0.0/0 전체 허용 | 맥 세션 |
| DNS(Gabia NS) | `@ A 216.198.79.1`(Vercel) · `api A 13.125.165.117` · `MX 10 mailapp.hiworks.co.kr` · `TXT "v=spf1 include:_spf.hiworks.co.kr ~all"` · SOA 기본 TTL 86400. `www` 없음 | nslookup |
| 프론트 | 웹 Vercel(`API_ORIGIN` 기본값이 api 호스트명), 모바일 EAS(`EXPO_PUBLIC_API_BASE_URL` 동일) | front 레포 |
| 추천 서빙 | main-server의 compose 프로젝트 `aod-rec`(네트워크 `aod-rec_aod-rec`): 엔진 4개 + `rec-router`가 `100.98.241.81:8080`에 바인딩. systemd `aod-rec-fw`가 raw PREROUTING에서 EC2 IP(100.126.116.57) 외 8080 DROP. 운영 백엔드의 `REC_ROUTER_BASE_URL=http://100.98.241.81:8080` | 맥 세션, `deploy.env` |
| 맥미니 | Apple M4 Pro, 24 GB, macOS 26.4, 460 GB(317 GB 여유). OrbStack 전역 캡 20 GB/12 CPU. VM: main-server 6 GB/4 CPU/40 GiB(실사용 2.7 GB, 10 GiB), guest-a~d 합계 13 GB 캡(실사용 ~0.45 GB, 타인에게 빌려준 격리 VM) | 맥 세션 |
| main-server | Ubuntu 24.04 arm64, ufw 기본 거부 + tailscale0만 허용, `net.ipv4.ip_nonlocal_bind=1`, 시스템 TZ America/Los_Angeles. chansu-bible 스택이 Tailscale Funnel 443 사용. `cloudflared` 미설치 | 맥 세션 |
| 코드 | API 모듈은 Selenium을 **쓰지 않음**(Dockerfile의 Chrome 설치는 불필요). 크롤러는 `ChromeDriverProvider`가 WebDriverManager로 chromedriver를 내려받음 → arm64 리눅스용 공식 바이너리 없음 | grep |

## 2. 목표 아키텍처

```
사용자 ──HTTPS──▶ Cloudflare(DNS + 프록시 + TLS) ──Tunnel──▶ cloudflared ──▶ api:8080
                                                        (main-server, 아웃바운드만)

main-server (OrbStack VM, 캡 12 GB / 6 CPU / 80 GiB)
├─ compose 프로젝트 aod-rec (기존, 변경 없음)   네트워크 aod-rec_aod-rec
│    rec-router:8080 · rec-steam · rec-tmdb · rec-webtoon · rec-webnovel
├─ compose 프로젝트 aod-backend (신규)           네트워크 aod-backend + aod-rec_aod-rec(외부 참조)
│    postgres     pgvector/pgvector:0.8.1-pg17  /srv/aod-pg        127.0.0.1:15432 (브리지 기간만 100.98.241.81:5432)
│    api          aod-api (Temurin 17 JRE)                        127.0.0.1:18080 → REC_ROUTER_BASE_URL=http://rec-router:8080
│    crawler      aod-crawler (Debian + chromium)                  127.0.0.1:18081
│    cloudflared  cloudflare/cloudflared:2026.10.7  (profile tunnel) 포트 없음
└─ systemd aod-backup.timer  매일 03:30 KST pg_dump → /srv/aod-backups (+ rclone → Cloudflare R2)
```

**자원 배분** (OrbStack 캡은 상한이지 예약이 아니다. 캡 합계가 전역 20 GB를 넘는 과할당이 되지만 guest 실사용이 ~0.1 GB씩이라 실무상 문제없음 — 사용자 인지 완료.)

| 항목 | 값 | 비고 |
|---|---|---|
| main-server 메모리 캡 | 6 → **12 GB** | OrbStack 재시작 필요(guest 4개 함께 재시작) |
| main-server CPU | 4 → **6** | 같은 재시작에 묶어 1회로 |
| main-server 디스크 | 40 → **80 GiB** | 재시작 없이 즉시 적용. 복원 전에 먼저 |
| postgres | mem_limit 1.5 GB, `shared_buffers=512MB`, `effective_cache_size=1GB`, `max_connections=150` | API 풀 30 + 브리지 EC2 API 30 + 크롤러 5 + 여유 |
| api | mem_limit 1.5 GB, `-Xmx1024m` | 힙은 compose 한 곳에서만(Dockerfile ENTRYPOINT에 두지 않음) |
| crawler | mem_limit 2.5 GB, `-Xmx1280m`, `shm_size 512m` | Chromium 프로세스는 힙 밖. 기존 ENTRYPOINT의 `-Xmx2048m`은 제거 |
| cloudflared | mem_limit 128 MB | |
| 시간대 | api·crawler `TZ=Asia/Seoul` + JVM `-Duser.timezone=Asia/Seoul`. **postgres 서버는 `timezone=UTC`**(RDS와 같게 — `timestamptz` 표시·`now()` 기본값이 바뀌지 않도록) | VM 시스템 TZ가 PDT라 명시 필수(크롤러 스케줄은 `zone` 없이 JVM 기본 시간대를 씀: 01:00·04:00·06:00 등) |
| 문자셋 | JVM `-Dfile.encoding=UTF-8`, 크롤러 이미지 `LANG=C.UTF-8` | JDK 17은 로캘에서 기본 문자셋을 정한다. Debian slim은 로캘이 없어 ASCII가 된다 → 한글 깨짐 |

**호스트 포트 원칙**: Docker가 ufw를 우회하므로 모든 publish는 `127.0.0.1` 바인딩. 포트는 18080·18081·15432 — 8080은 D+2에 추천 라우터가 `127.0.0.1:8080`으로 돌아오고, 5432는 chansu 스택과 겹칠 수 있어서 피한다. 예외는 브리지 기간의 postgres `100.98.241.81:5432`(§6)뿐이며, 그 기간엔 iptables로 EC2 Tailscale IP 외 접속을 막는다.

## 3. 코드 변경 (브랜치 `feat/macmini-migration`)

### 3-1. 크롤러 이미지 — arm64에서 도는 Chromium
- 빌드 스테이지: `eclipse-temurin:17-jdk-alpine` → **`eclipse-temurin:17-jdk`**. alpine 태그는 amd64 전용이라 arm64에서 빌드가 안 된다(2026-11-02 `docker manifest inspect`로 확인). `gradlew`는 git 모드가 100644라 `chmod +x` 한 줄 추가.
- 런타임 스테이지: `debian:trixie-slim` + `chromium chromium-driver fonts-liberation tini curl ca-certificates tzdata`, JRE는 `COPY --from=eclipse-temurin:17-jre /opt/java/openjdk`로 가져온다.
  - Ubuntu의 `chromium`은 Snap 스텁이라 컨테이너에서 못 쓴다 → Temurin(Ubuntu 기반) 대신 Debian.
  - trixie에는 `openjdk-17` 패키지가 없고 bookworm은 보안 지원이 끝나 가므로 JRE는 Temurin 것을 복사한다(API와 같은 JRE).
  - 크롤러는 화면을 그리지 않고 DOM만 읽으므로 CJK 폰트는 넣지 않는다.
  - amd64에서도 같은 패키지가 있어 이미지는 멀티아치로 동작한다(브리지 기간 EC2 재배포 시에도 그대로 빌드됨).
  - `ENV LANG=C.UTF-8 LC_ALL=C.UTF-8` (§2 문자셋).
- ENTRYPOINT에서 `-Xms512m -Xmx2048m` 제거. 힙은 compose `JAVA_TOOL_OPTIONS`에서만(API와 같은 원칙). tini 유지.
- 비루트 사용자(uid 1001) 유지. `HEALTHCHECK` 유지.

### 3-2. `ChromeDriverProvider` — 경로 주입
- 환경변수 `CHROMEDRIVER_PATH`가 있으면 `System.setProperty("webdriver.chrome.driver", …)`로 지정하고 `WebDriverManager.chromedriver().setup()`을 **건너뜀**. 없으면 지금과 동일(로컬 Windows/맥 개발 영향 없음).
- 환경변수 `CHROME_BIN`이 있으면 `ChromeOptions.setBinary(...)`.
- `--headless` → `--headless=new`(Chromium 120+ 권장값).
- 테스트: 경로 해석을 순수 함수(`resolve(Map<String,String> env)` 류)로 분리해 단위 테스트 3건(둘 다 없음 · driver만 · 둘 다)으로 고정. Chrome 기동은 테스트하지 않는다.
- compose에서 `CHROME_BIN=/usr/bin/chromium`, `CHROMEDRIVER_PATH=/usr/bin/chromedriver`.

### 3-3. API 이미지 — Chrome 제거
- `-AOD-All-of-Dopamine-api/Dockerfile` 런타임을 `eclipse-temurin:17-jre` + `curl`만으로. Selenium 사용 코드가 없음을 grep으로 확인했다(`build.gradle`의 `selenium-java` 의존성은 이번엔 두고, 후속에서 제거 검토). 빌드 스테이지는 크롤러와 같은 이유로 `eclipse-temurin:17-jdk` + `chmod +x gradlew`.
- prod 프로필의 `logging.file.name=/var/log/aod/application.log`이 비루트 사용자로 쓰이도록 이미지에서 `/var/log/aod`를 만들고 `appuser` 소유로 둔다(named volume이 첫 마운트 때 이 소유권을 물려받는다).
- 이미지가 가벼워지고(Chrome 약 400 MB 절감) arm64 빌드가 Temurin 공식 arm64 태그로 바로 된다.

### 3-4. `deploy/main-server/` 신설
```
deploy/main-server/
├─ docker-compose.yml        postgres · api · crawler · cloudflared (§2 값), 네트워크 aod-backend + external aod-rec_aod-rec
├─ .env.example              POSTGRES_PASSWORD(새 마스터) · AOD_AI_PASSWORD(새) · TMDB_API_KEY · STEAM_API_KEY · OPENAI_API_KEY
│                            · NAVER_ID · NAVER_PW · SENTRY_DSN · REC_ENABLED · REC_ALLOWED_USERS · CF_TUNNEL_TOKEN
│                            · COMPOSE_PROFILES(터널 켤 때 tunnel) · PG_PUBLISH(기본 127.0.0.1:15432)
├─ init/01-roles.sh          첫 초기화에서만 실행: aod_ai(LOGIN) · aod_public_ro(NOLOGIN) 생성, GRANT aod_public_ro TO aod_ai
├─ verify.sql                이전 검증 질의 — RDS와 대상에서 같은 파일을 돌려 diff (§4)
├─ backup/aod-backup.sh      docker exec pg_dump -Fc → /srv/aod-backups/aod-YYYYMMDD.dump, 14일 보관, rclone copy r2:aod-backups
├─ backup/aod-backup.service · aod-backup.timer   03:30 Asia/Seoul
└─ RUNBOOK.md                §5~§8의 명령어 순서 그대로(맥 세션이 실행)
```
- compose의 api/crawler는 `build:`(컨텍스트 레포 루트, 기존 Dockerfile) — 1단계는 **main-server에서 직접 빌드**한다(추천 서빙 `deploy.sh`와 같은 방식, M4 Pro에서 수 분). 레지스트리는 2단계(§9).
- postgres 서비스 `ports`는 `${PG_PUBLISH:-127.0.0.1:15432}:5432` 한 줄. 브리지 기간만 `.env`에서 `PG_PUBLISH=100.98.241.81:5432`로 바꾸고 `up -d postgres`, 끝나면 줄을 지워 되돌림.
- cloudflared는 compose `profiles: [tunnel]` — 토큰이 준비되기 전엔 안 뜬다. 준비되면 `.env`에 `COMPOSE_PROFILES=tunnel`.
- api 컨테이너에 named volume `aod-api-logs:/var/log/aod`(prod 프로필의 `logging.file.name` 경로). 크롤러는 `./logs:/app/logs` 유지.

### 3-5. `.github/workflows/main.yml` — 1단계
- 트리거를 `workflow_dispatch`만 남긴다(`push: main/develop` 제거). 이 브랜치가 main에 머지돼도 EC2 배포가 돌지 않게 하고, 브리지(§6)에서 **수동 실행**으로만 쓴다. 잡 내용은 바꾸지 않는다.
- 2단계(§9)에서 AWS 잡을 통째로 교체한다.

## 4. 데이터 이전

**덤프(맥 main-server에서, 마스터 계정)**
```bash
# 비번은 채팅에 쓰지 않는다 — 터미널 프롬프트 입력 또는 ~/.pgpass
docker run --rm -it -v /srv/aod-backups:/out postgres:17 \
  pg_dump -h <RDS_HOST> -U postgres -d postgres \
          -Fd -j4 -Z1 -f /out/rds-YYYYMMDD-HHMM
```
- 롤 속성·멤버십은 §1에서 이미 조회했으므로 globals 덤프는 받지 않는다(RDS는 `pg_dumpall --globals-only`가 `rds_*` 롤 때문에 지저분하다).
- **AWS 밖 첫 사본은 이 PC에서 직접 받는다**(이 PC에 pg_dump 17.5가 있고 RDS 5432가 열려 있다) → `D:\AOD\backup\rds-YYYYMMDD-HHMM`. 맥 쪽 사본과 독립된 두 번째 사본.
- **리허설 덤프**(오늘)와 **최종 덤프**(전환 직전, 크롤러·API 정지 후) 두 번 받는다. 2.5 GB라 각 5~10분.

**복원(대상 컨테이너 postgres, superuser `postgres`) — 리허설과 최종이 같은 절차**
1. `init/01-roles.sh`가 첫 초기화에서 `aod_ai`·`aod_public_ro`를 **새 비번**으로 만들어 둔 상태(과거 채팅에 노출된 비번 교체 겸). 롤 멤버십(`GRANT aod_public_ro TO aod_ai`)은 `pg_dump`에 안 들어가므로 여기서 만든다. 기본 권한(`ALTER DEFAULT PRIVILEGES`)과 객체 GRANT는 덤프에 들어 있다.
2. 새 DB를 만든다: `CREATE DATABASE aod_new;` → 그 안에서 `CREATE EXTENSION IF NOT EXISTS vector;` (pgvector 이미지에 포함, 0.8.x).
3. `pg_restore -j4 -d aod_new <덤프 디렉터리>` — `--no-owner`를 쓰지 않는다. 모든 객체 소유자가 `postgres`라 그대로 맞고, 롤이 미리 있어 GRANT도 그대로 복원된다. `rds_*` 롤 관련 경고는 무시.
4. §"검증"을 `aod_new`에서 통과시킨다.
5. **이름 교체**(API·크롤러 정지 상태, `template1`에 접속해서): `ALTER DATABASE postgres RENAME TO postgres_old; ALTER DATABASE aod_new RENAME TO postgres;` → 서비스 기동. 리허설에서는 교체까지 똑같이 하고, 최종 전에 `DROP DATABASE postgres_old`로 비운다. 최종 뒤 `postgres_old`는 D+2까지 두고 지운다.

**검증(리허설·최종 공통)** — `deploy/main-server/verify.sql` 하나를 RDS와 대상에서 돌려 출력을 `diff`한다. 값은 하드코딩하지 않는다(RDS 쪽 출력이 기준).
- 주요 테이블 행 수: raw_items · platform_data · contents · crawl_job_queue · game_contents · transform_runs · movie_contents · tv_contents · webtoon_contents · users · `aod_ai.content_embedding`
- `flyway_schema_history` 행 수와 최대 버전
- 스키마별 테이블 수(public·aod_ai·aod_log·aod_rec), `vector` 확장 존재
- **리허설**: RDS가 살아 있어 크롤 계열(raw_items·crawl_job_queue·transform_runs·platform_data·contents) 몇 건 차이는 허용. **최종**: 쓰기 동결 뒤라 diff가 비어야 한다.
- 추가로 `aod_ai`로 접속해 `select 1 from public.contents limit 1` 성공(롤 멤버십·기본 권한 복원 확인)

## 5. 네트워크 · DNS · Cloudflare

1. 사용자: Cloudflare 무료 계정 → 존 `allofdophamin.com` 추가. Cloudflare가 Gabia 레코드를 가져오지만 아래를 눈으로 맞춘다.

| 이름 | 타입 | 값 | 프록시 |
|---|---|---|---|
| `@` | A | 216.198.79.1 (Vercel) | **끔(DNS only)** — Vercel TLS를 Cloudflare가 가로채지 않게 |
| `api` | A | 13.125.165.117 (EC2, **전환 전까지 그대로**) | **끔(DNS only)** — EC2 nginx 인증서가 그대로 TLS 처리 |
| `api-mac` | CNAME | 터널 (Public Hostname 만들 때 자동) | 켬 — 전환 전 Cloudflare 경유 시험용, D+2 삭제 |
| `@` | MX 10 | mailapp.hiworks.co.kr | — |
| `@` | TXT | `v=spf1 include:_spf.hiworks.co.kr ~all` | — |

   `api`를 처음부터 터널로 두면 **존이 Active가 되는 순간이 곧 전환**이 된다(통제 불가, 리졸버마다 제각각). 그래서 `api`는 EC2 A 레코드로 옮겨 두고, 전환 시각에 터널로 바꾼다(§6 11번).
2. 사용자: Gabia에서 먼저 `api` A 레코드 TTL을 300으로 낮추고, 그다음 네임서버를 Cloudflare가 준 2개로 교체한다.
3. Cloudflare Zero Trust → Networks → Tunnels → `aod-main-server` 생성(커넥터 토큰 → main-server `.env`의 `CF_TUNNEL_TOKEN`) → Public Hostname `api-mac.allofdophamin.com` → `http://api:8080`.
4. 존이 Cloudflare에서 **Active**로 바뀐 뒤에만 §6 전환을 시작한다(보통 1시간 안, 최대 24~48시간은 일부 리졸버가 Gabia를 봄 → 그래서 §6 브리지).
5. Cloudflare SSL/TLS 모드 **Full**(터널은 Cloudflare↔커넥터 구간이 이미 암호화). HTTPS 강제 켬.
6. 백엔드가 보는 클라이언트 IP는 Cloudflare 것이 된다. 접속 IP를 쓰는 곳(rec-events 로그 등)이 있으면 `CF-Connecting-IP` 헤더를 읽도록 후속 수정. 이번 전환의 차단 조건은 아님.

## 6. 전환 순서

### D0 (오늘)
| # | 누가 | 할 일 | 성공 표시 |
|---|---|---|---|
| 1 | 사용자 | Cloudflare 존 생성, Gabia NS 교체, `api` TTL 300 | Cloudflare 대시보드 "Pending → Active" |
| 2 | 맥 세션 | main-server 디스크 80 GiB | `orb` 설정 반영, `df -h /` |
| 3 | 사용자+맥 세션 | 캡 12 GB/6 CPU 변경 + OrbStack 재시작(guest 사용자 공지 후) | 5개 VM 모두 running, `aod-rec` 컨테이너 healthy |
| 4 | 맥 세션 | 리허설 덤프(§4) + 이 PC 복제본 | `/srv/aod-backups/rds-…` 존재, 크기 수백 MB |
| 5 | 이 세션 | §3 코드 변경 → 브랜치 push → PR | 크롤러 테스트 그린, 이미지 빌드 OK |
| 6 | 맥 세션 | 브랜치 체크아웃 → `deploy/main-server` compose 빌드·기동(postgres+api+crawler), 리허설 DB로 복원 | `curl 127.0.0.1:18080/actuator/health` UP, `:18081` UP |
| 7 | 맥 세션 | 스모크: `/api/works?domain=MOVIE&size=1`, `/api/works/{id}`, api 컨테이너에서 `rec-router:8080/health`, 크롤러 **`POST /api/crawler/rankings/steam`**(`SteamRankingFetcher`가 `ChromeDriverProvider`로 Chromium을 띄움 → arm64 Chromium 검증) | 전부 200, `external_ranking`의 `platform='Steam'` 행 `max(fetched_at)`이 스모크 시각 이후 |
| 8 | 사용자 | Cloudflare 터널 생성 → 토큰은 main-server `.env`에 직접(채팅 금지) → `cloudflared` 기동 | 터널 Healthy, 존 Active 뒤 `curl https://api-mac.allofdophamin.com/actuator/health` UP |

### 전환 (존 Active + 7·8번 통과 직후, 다운타임 15~25분)
| # | 누가 | 할 일 |
|---|---|---|
| 9 | 사용자 | AWS 콘솔에서 크롤러 EC2 **중지**, API EC2 **중지** (쓰기 동결 — 다운타임 시작) |
| 10 | 맥 세션 | 맥의 리허설 api·crawler 정지 → 브리지 노출 준비(§6 13번을 여기서 — postgres 재생성을 다운타임 안에 끝냄) → 최종 덤프 → `aod_new`에 복원 → §4 검증(diff 비어야 함) → DB 이름 교체 → api·crawler 기동 → 스모크 |
| 11 | 사용자 | Cloudflare에서 `api` A 레코드 삭제 → 터널 Public Hostname `api.allofdophamin.com` → `http://api:8080` 추가(프록시 CNAME 자동 생성) — 다운타임 끝 |
| 12 | 둘 다 | 웹(allofdophamin.com)에서 로그인·탐색·좋아요 1회, 모바일 앱 1회 |

### 브리지 (전환 직후 ~ 48시간)
| # | 누가 | 할 일 |
|---|---|---|
| 13 | 맥 세션 | (10번 안에서 실행) iptables raw PREROUTING에 `-i tailscale0 -p tcp -d 100.98.241.81 --dport 5432 ! -s 100.126.116.57 -j DROP` (aod-rec-fw와 같은 방식, 비영속) → `.env` `PG_PUBLISH=100.98.241.81:5432` → `docker compose up -d postgres` |
| 14 | 사용자 | GitHub 시크릿 `POSTGRES_HOST=100.98.241.81`, `POSTGRES_PASSWORD=<새 마스터>` 로 변경 → `main.yml` 수동 실행(API EC2 다시 켠 뒤). `deploy-crawler` 잡은 크롤러 EC2가 꺼져 실패하는 게 정상 |
| 15 | 사용자 | 48시간 뒤 API EC2 중지. 맥 세션은 `PG_PUBLISH` 줄 삭제 → `up -d postgres` → 13번 iptables 규칙 삭제 |

브리지를 두는 이유: Gabia `api` TTL 86400과 `.com` 위임 캐시(최대 48h) 때문에 일부 리졸버가 며칠 EC2로 보낸다. EC2 API가 맥 DB를 보게 하면 그 트래픽도 **같은 DB에 쓴다** → 데이터 분기 없음. 브리지 중 EC2 쪽 `REC_ROUTER_BASE_URL`은 기존 값(100.98.241.81:8080)이 그대로 동작한다(`aod-rec-fw`가 EC2 IP를 허용 중).

### D+2 정리 (사용자, AWS 콘솔)
1. EC2 2대 **종료(terminate)**, 연결된 EBS 볼륨 삭제 확인
2. RDS `aod-database` 삭제 — 최종 스냅샷 **생략**(맥 복원 검증 + 이 PC 덤프 복제본으로 대체). 자동 스냅샷 잔존 여부 확인
3. EIP `13.125.165.117` 릴리스
4. ECR `aod-api`·`aod-crawler` 삭제
5. IAM 사용자 `claude-aod-deploy` 삭제(맥 `~/.aws`의 키도 제거)
6. 보안그룹 `AOD_Test_database` 등 남은 SG·키페어 정리, Cost Explorer로 다음 날 과금 0 확인
7. GitHub 시크릿 `AWS_*`, `*_EC2_HOST`, `EC2_*`, `POSTGRES_*` 삭제
8. Tailscale 어드민에서 `aod-api` 노드 삭제. Cloudflare에서 `api-mac` 호스트명 삭제
9. 맥 세션: 추천 `deploy.env` `BIND_IP=127.0.0.1` → `deploy.sh up`, `aod-rec-fw` 비활성(라우터의 tailnet 노출 제거)

## 7. 롤백

| 시점 | 방법 | 결과 |
|---|---|---|
| 9번 전 | 아무것도 바꾸지 않았음 | 영향 없음 |
| 10 실패 | `api`는 아직 EC2 A 레코드 → EC2 API·크롤러 다시 시작만 하면 끝 | RDS는 그대로이므로 즉시 복귀. 동결 중 쓰기는 없었음 |
| 11~12 실패 | Cloudflare에서 `api` 터널 호스트명 삭제 → `A 13.125.165.117`(프록시 끔) 다시 추가 → EC2 API·크롤러 다시 시작 | 같음. 맥 DB에 들어간 쓰기(수 분)는 유실 — 그래서 12번 확인은 짧게 |
| 브리지 중 맥 장애 | 같은 방법 + EC2 API 시크릿 `POSTGRES_HOST`를 RDS로 되돌려 재배포 | 맥 DB에 들어온 쓰기만 유실 → 그래서 RDS 삭제는 D+2 이후 |

## 8. 보안

- 호스트 publish는 `127.0.0.1`만. 유일한 예외 `PG_BRIDGE_BIND`는 48시간 뒤 되돌린다.
- `cloudflared`는 아웃바운드 전용 → ufw 인바운드 규칙 추가 없음. Funnel 443은 chansu가 계속 쓴다(충돌 없음).
- DB 비번 전원 교체(마스터·aod_ai). 새 값은 맥 `.env`와 GitHub 시크릿(브리지용)에만.
- 추천 라우터는 인증이 없으므로 D+2에 tailnet 노출을 끝낸다(§6 9번).
- 2단계 CI의 `tag:ci` 노드는 Tailscale ACL로 `main-server:22`만 허용(현재 ACL은 전체 허용 + 타 계정 공유 노드 존재 → 반드시 제한).

## 9. 2단계: CI/CD (전환 뒤, 별도 계획)

- 레포 3개가 **퍼블릭** → 셀프호스트 러너 금지(포크 PR이 워크플로를 바꿔 홈서버에서 코드를 실행할 수 있음).
- `ubuntu-24.04-arm` 호스트드 러너로 Gradle 빌드 + arm64 이미지를 `ghcr.io/aod-all-of-dopamine/aod-{api,crawler}:<sha>,latest` push → `tailscale/github-action`(OAuth 클라이언트, `tag:ci`, ephemeral) → SSH로 main-server 배포 사용자 → `docker compose pull && up -d` + 헬스 루프.
- compose의 `build:`를 `image:`로 바꾸는 시점이 이때. 그 전까지는 main-server에서 `git pull && docker compose build && up -d`.

## 10. 운영 메모

- **백업**: `aod-backup.timer` 03:30 KST → `/srv/aod-backups`(14일) + `rclone copy` → Cloudflare R2 버킷 `aod-backups`(무료 10 GB, 덤프 압축 시 ~1 GB/일이면 약 열흘 보관 → 버킷 수명주기 7일). 첫 성공 뒤 **복원 리허설 1회**를 런북에 넣는다.
- **재부팅**: 모든 컨테이너 `restart: unless-stopped`. 맥은 `pmset autorestart 1`, sleep 0. OrbStack 자동 시작 확인.
- **AI 배치**(`-AOD-All-of-Dopamine-AI/.env`의 `AOD_DB_HOST`): RDS 삭제 전에 맥 DB로 바꿔야 한다. 배치는 main-server 안에서 돌리거나, 돌릴 때만 `PG_BRIDGE_BIND`를 켠다. 후속 작업.
- **모니터링**: Sentry는 DSN 그대로. Prometheus 스택은 후속.
- **문서**: `DEPLOY_VM.md`(AI 레포)의 "API 서버 EC2" 서술과 `docs/7_MONITORING.md`의 EC2 전제는 전환 뒤 갱신.

## 11. 사용자가 준비할 것 (코드 작업과 병행)

- Cloudflare 계정, Gabia 로그인
- RDS 마스터(`postgres`) 비번 — 맥 터미널에서 직접 입력
- GitHub Actions 시크릿 값 원본: `TMDB_API_KEY` `STEAM_API_KEY` `OPENAI_API_KEY` `NAVER_ID` `NAVER_PW` `SENTRY_DSN` (`REC_ALLOWED_USERS`는 현재 값 확인) — 맥 `.env`에 직접
- OrbStack 재시작 시간대(guest 사용자 공지)
- AWS 콘솔 접근(중지·종료·삭제는 전부 사용자가 직접)

## 12. 실행 분담

- **이 세션(Windows PC)**: §3 코드 변경·테스트·브랜치·PR, 런북 작성, 검증 질의(Tailscale로 main-server 접근은 Tailscale SSH ACL 허용 뒤).
- **맥 세션("Main server AI backend 연동")**: §6의 호스트 작업 전부. 캡 변경·OrbStack 재시작은 그 세션에서 사용자 직접 OK 뒤에만.
- **사용자**: Cloudflare·Gabia·AWS 콘솔·GitHub 시크릿, 비밀값 입력.
