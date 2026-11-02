# main-server 백엔드 런북 (AWS → 맥미니 이전)

설계: `docs/superpowers/specs/2026-11-02-aws-to-macmini-migration-design.md`. 이 문서는 **그대로 따라 치는 순서**만 적는다.
- 〔맥〕 main-server 셸(맥 세션이 실행) · 〔macOS〕 맥 호스트 셸 · 〔사용자〕 콘솔·대시보드 · 〔사용자 터미널〕 비밀값을 직접 치는 단계(Claude 세션이 대신하지 않는다) · 〔PC〕 사용자 Windows PC
- 성공 표시가 안 보이면 다음 단계로 가지 않는다.

```bash
# 〔맥〕 매 셸에서 먼저 (1-5 에서 .env 를 만든 뒤)
cd ~/-AOD-All-of-Dopamine-back/deploy/main-server     # 아래 docker compose 명령은 전부 여기서
export RDS_HOST=$(grep '^RDS_HOST=' .env | cut -d= -f2-)   # 엔드포인트는 퍼블릭 레포에 적지 않는다 — .env 에만
```

## 1. 호스트 준비

1-1 〔macOS〕 main-server 디스크 40 → **80 GiB**(재시작 없이 적용). 예전에 `disk_bytes`를 바꾼 그 `orb config` 설정으로.
```bash
orb run -m main-server df -h /          # 성공: Size 80G 안팎
```
1-2 〔사용자 OK 뒤 macOS〕 main-server 메모리 **12 GiB** · CPU **6** 으로 바꾸고 OrbStack 재시작. **모든 VM이 재시작된다** — guest-a~d 사용자에게 미리 알린다.
```bash
orb run -m main-server sh -c 'cat /sys/fs/cgroup/memory.max /sys/fs/cgroup/cpu.max'
# 성공: 12884901888 · 600000 100000
```
```bash
# 〔맥〕 추천·chansu 스택이 다시 떴는지
docker ps --format '{{.Names}} {{.Status}}' | sort      # 성공: rec-* 5개 healthy, chansu 4개 Up
```
1-3 〔맥〕 디렉터리
```bash
sudo mkdir -p /srv/aod-pg /srv/aod-backups
sudo chown "$USER": /srv/aod-backups   # §2 의 .verify 리디렉션을 로그인 사용자가 쓴다. /srv/aod-pg 는 root 그대로 — postgres 엔트리포인트가 uid 999 로 바꾼다
```
1-4 〔맥〕 코드
```bash
cd ~ && git clone https://github.com/AOD-All-of-Dopamine/-AOD-All-of-Dopamine-back.git
cd ./-AOD-All-of-Dopamine-back && git checkout feat/macmini-migration    # './' 필수 — '-' 로 시작하는 이름
```
1-5 〔사용자 터미널, main-server〕 설정 파일과 RDS 비번 파일. 값은 채팅에 쓰지 않는다.
```bash
cd ~/-AOD-All-of-Dopamine-back/deploy/main-server
cp .env.example .env && chmod 600 .env
openssl rand -base64 24 | tr -d '/+='     # 두 번 돌려 POSTGRES_PASSWORD · AOD_AI_PASSWORD 에
nano .env                                  # 나머지 값(GitHub Actions 시크릿 원본)과 RDS_HOST(AWS 콘솔 → RDS → 엔드포인트) 입력
RDS_HOST=$(grep '^RDS_HOST=' .env | cut -d= -f2-)
read -rs -p 'RDS master password: ' PGPW; echo
printf '%s:5432:*:postgres:%s\n' "$RDS_HOST" "$PGPW" > ~/.pgpass-rds
chmod 600 ~/.pgpass-rds; unset PGPW
```
1-6 〔맥〕 확인
```bash
docker compose config --quiet && echo CONFIG-OK
docker network inspect aod-rec_aod-rec > /dev/null && echo REC-NET-OK
docker run --rm -v "$HOME/.pgpass-rds:/pgpass:ro" -e PGPASSFILE=/pgpass postgres:17 \
  psql -h "$RDS_HOST" -U postgres -d postgres -Atc 'select 1'          # 성공: 1
```

## 2. 덤프 (리허설·최종 공통)

```bash
# 〔맥〕
export STAMP=$(TZ=Asia/Seoul date +%Y%m%d-%H%M)
docker run --rm -v "$HOME/.pgpass-rds:/pgpass:ro" -e PGPASSFILE=/pgpass -v /srv/aod-backups:/out postgres:17 \
  pg_dump -h "$RDS_HOST" -U postgres -d postgres -Fd -j4 -Z1 -f "/out/rds-$STAMP"
docker run --rm -v "$HOME/.pgpass-rds:/pgpass:ro" -e PGPASSFILE=/pgpass -v "$PWD/verify.sql:/verify.sql:ro" postgres:17 \
  psql -h "$RDS_HOST" -U postgres -d postgres -X -q -At -F ' ' -f /verify.sql > "/srv/aod-backups/rds-$STAMP.verify"
sudo du -sh "/srv/aod-backups/rds-$STAMP"; wc -l < "/srv/aod-backups/rds-$STAMP.verify"
```
성공: 수백 MB 이상, verify 18줄.

참고 — 2026-11-02 이 PC에서 뽑은 RDS 기준값(리허설 때 크기 감각용, 실제 비교는 매번 새로 뽑은 값으로):
`flyway.max_version 14` · `flyway.rows 12` · `rows.public.contents 246663` · `rows.public.raw_items 249911` · `rows.public.users 14` · `tables.public 29` · `tables.aod_log 30`.

## 3. 복원 (리허설·최종 공통) — `aod_new`로 받고 이름을 바꾼다

3-1 〔맥〕 postgres 기동(처음 한 번 — 빈 데이터 디렉터리면 롤 초기화가 돈다)
```bash
docker compose up -d postgres && docker compose ps postgres                # 성공: healthy
docker compose exec -T postgres psql -U postgres -Atc "select rolname from pg_roles where rolname like 'aod%' order by 1"
# 성공: aod_ai / aod_public_ro
```
3-2 〔맥〕 새 DB와 복원
```bash
docker compose exec -T postgres psql -U postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS aod_new" -c "CREATE DATABASE aod_new"
docker compose exec -T postgres psql -U postgres -d aod_new -c "CREATE EXTENSION IF NOT EXISTS vector"
docker compose exec -T postgres pg_restore -U postgres -d aod_new -j4 "/backups/rds-$STAMP" 2> "/tmp/restore-$STAMP.log"; echo "exit=$?"
grep -c 'error:' "/tmp/restore-$STAMP.log"; grep 'error:' "/tmp/restore-$STAMP.log" | head -20
```
성공: `exit=0`. `exit=1`이면 에러 줄을 본다 — `rds_`로 시작하는 롤·`COMMENT ON EXTENSION`·`already exists` 만이면 계속. 테이블·인덱스·데이터 에러면 멈춘다.

3-3 〔맥〕 검증
```bash
docker compose exec -T postgres psql -U postgres -d aod_new -X -q -At -F ' ' -f - < verify.sql > "/tmp/mac-$STAMP.verify"
diff "/srv/aod-backups/rds-$STAMP.verify" "/tmp/mac-$STAMP.verify" && echo IDENTICAL
AI_PW=$(grep '^AOD_AI_PASSWORD=' .env | cut -d= -f2-)
docker compose exec -T -e PGPASSWORD="$AI_PW" postgres psql -h 127.0.0.1 -U aod_ai -d aod_new -Atc "select count(*) > 0 from public.contents"; unset AI_PW
```
성공: **최종**은 `IDENTICAL`. **리허설**은 크롤 계열(raw_items·crawl_job_queue·transform_runs·platform_data·contents) 몇 건 차이만 허용. 마지막 줄 `t`.

3-4 〔맥〕 이름 교체(api·crawler 정지 상태)
```bash
docker compose stop api crawler 2> /dev/null || true
docker compose exec -T postgres psql -U postgres -d template1 -v ON_ERROR_STOP=1 \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname IN ('postgres','aod_new') AND pid <> pg_backend_pid()" \
  -c "DROP DATABASE IF EXISTS postgres_old" \
  -c "ALTER DATABASE postgres RENAME TO postgres_old" \
  -c "ALTER DATABASE aod_new RENAME TO postgres"
docker compose exec -T postgres psql -U postgres -d template1 -Atc "select datname from pg_database order by 1"
```
성공: `postgres` · `postgres_old` · `template0` · `template1`.

## 4. 빌드 · 기동 · 스모크

```bash
# 〔맥〕
docker compose build                                   # 첫 빌드 수 분
docker compose up -d api crawler
docker compose ps                                      # 성공: api·crawler healthy (api 최대 3분, 크롤러 최대 4분)
curl -fsS http://127.0.0.1:18080/actuator/health | head -c 80; echo
curl -fsS 'http://127.0.0.1:18080/api/works?domain=MOVIE&page=0&size=1' | head -c 300; echo
docker compose exec -T api curl -fsS http://rec-router:8080/health | head -c 80; echo
curl -fsS http://127.0.0.1:18081/actuator/health | head -c 80; echo
docker compose exec -T api sh -c 'date; java -XshowSettings:properties -version 2>&1 | grep -E "user.timezone|file.encoding"'
```
성공: 상태 `UP` 두 번, 작품 JSON, 라우터 `{"ready":true`, KST 날짜·`Asia/Seoul`·`UTF-8`.

Selenium(arm64 Chromium) 스모크 — Steam 랭킹은 `SteamRankingFetcher`가 `ChromeDriverProvider`로 브라우저를 띄운다:
```bash
Q="select coalesce(max(fetched_at)::text,'none') from external_ranking where platform='Steam'"
BEFORE=$(docker compose exec -T postgres psql -U postgres -Atc "$Q")
curl -fsS -X POST http://127.0.0.1:18081/api/crawler/rankings/steam; echo
AFTER=$(docker compose exec -T postgres psql -U postgres -Atc "$Q")
echo "before=$BEFORE"; echo "after =$AFTER"
docker compose logs --since 10m crawler | grep -iE 'ChromeDriver|SessionNotCreated|chrom.*(error|fail)' | tail -5
```
성공: `Steam 랭킹 크롤링 완료`, `after`가 `before`보다 최근(방금 시각), 로그에 `ChromeDriver 경로 지정: /usr/bin/chromedriver`, `SessionNotCreated` 없음.

## 5. Cloudflare 〔사용자〕

5-1 Gabia → DNS 관리 → `api` A 레코드 **TTL 300** 으로 먼저 낮춘다.
5-2 dash.cloudflare.com → Add a domain → `allofdophamin.com` → Free. 가져온 레코드를 아래와 같게 맞춘다.

| 이름 | 타입 | 값 | 프록시 |
|---|---|---|---|
| `@` | A | 216.198.79.1 | **끔** |
| `api` | A | 13.125.165.117 | **끔** (전환 전까지 EC2) |
| `@` | MX 10 | mailapp.hiworks.co.kr | — |
| `@` | TXT | `v=spf1 include:_spf.hiworks.co.kr ~all` | — |

5-3 Gabia → 네임서버 → Cloudflare가 준 2개로 교체.
5-4 Zero Trust → Networks → Tunnels → Create → Cloudflared → 이름 `aod-main-server` → Docker 탭 명령의 토큰(`eyJ…`)을 **main-server `.env`의 `CF_TUNNEL_TOKEN=`에 직접** 붙이고 `COMPOSE_PROFILES=tunnel`.
5-5 같은 터널 → Public Hostname 추가: Subdomain `api-mac`, Domain `allofdophamin.com`, Service `HTTP` · `api:8080`.
5-6 〔맥〕
```bash
docker compose up -d cloudflared
docker compose logs cloudflared | grep -c 'Registered tunnel connection'     # 성공: 4
```
5-7 〔PC〕 존이 **Active**가 된 뒤(Cloudflare 메일·대시보드)
```bash
nslookup -type=NS allofdophamin.com 1.1.1.1                                   # 성공: *.ns.cloudflare.com
curl -fsS https://api-mac.allofdophamin.com/actuator/health                    # 성공: {"status":"UP"
curl -fsS https://api.allofdophamin.com/actuator/health -o /dev/null -w '%{http_code}\n'   # 성공: 200 (아직 EC2)
```

## 6. 전환 — 다운타임 15~25분

전제: §4 스모크 통과, §5-7 통과. 사용자와 맥 세션이 동시에 대기.

6-1 〔사용자〕 AWS 콘솔 → EC2 → 크롤러 인스턴스 **중지**, API 인스턴스(`i-0718dec7cc7bf2037`) **중지**. ← 다운타임 시작
6-2 〔맥〕 브리지 준비(postgres 재생성을 다운타임 안에 끝낸다)
```bash
docker compose stop api crawler
sudo iptables -t raw -I PREROUTING -i tailscale0 -p tcp -d 100.98.241.81 --dport 5432 ! -s 100.126.116.57 -j DROP
sed -i 's/^PG_PUBLISH=.*/PG_PUBLISH=100.98.241.81:5432/' .env
docker compose up -d postgres && docker compose ps postgres                   # 성공: healthy
```
6-3 〔맥〕 §2 덤프(새 `STAMP`) → §3-2 복원 → §3-3 검증(**`IDENTICAL` 필수**) → §3-4 이름 교체
6-4 〔맥〕
```bash
docker compose up -d api crawler && docker compose ps
curl -fsS http://127.0.0.1:18080/actuator/health | head -c 80; echo
curl -fsS https://api-mac.allofdophamin.com/actuator/health | head -c 80; echo
```
6-5 〔사용자〕 Cloudflare DNS에서 `api` A 레코드 **삭제** → 터널 `aod-main-server` → Public Hostname 추가: Subdomain `api`, Service `HTTP` · `api:8080`. ← 다운타임 끝
6-6 〔PC〕
```bash
nslookup api.allofdophamin.com 1.1.1.1                                         # 성공: 104.x / 172.6x (Cloudflare)
curl -fsS https://api.allofdophamin.com/actuator/health                        # 성공: UP
```
6-7 〔사용자〕 https://allofdophamin.com 에서 로그인 · 탐색 · 좋아요 1회, 모바일 앱 1회.

## 7. 브리지 — 전환 직후 ~ 48시간

7-1 〔사용자〕 GitHub(백엔드 레포) → Settings → Secrets → Actions: `POSTGRES_HOST`=`100.98.241.81`, `POSTGRES_USER`=`postgres`, `POSTGRES_PASSWORD`=main-server `.env`의 값.
7-2 〔사용자〕 AWS 콘솔에서 API 인스턴스 **시작** → Actions → "CI/CD Pipeline - API & Crawler" → Run workflow(`main`). `deploy-api` 초록이면 성공(`deploy-crawler`는 인스턴스가 꺼져 실패하는 게 정상).
7-3 〔맥〕
```bash
docker compose exec -T postgres psql -U postgres -Atc "select coalesce(host(client_addr),'local')||' '||count(*) from pg_stat_activity where datname='postgres' group by 1"
```
성공: `100.126.116.57` 행이 생김(EC2 API 연결).
```powershell
# 〔PC〕 브리지 포트가 EC2 외에는 막혔는지
Test-NetConnection 100.98.241.81 -Port 5432 | Select-Object TcpTestSucceeded   # 성공: False
```
7-4 **48시간 뒤** 〔사용자〕 API 인스턴스 **중지**. 〔맥〕
```bash
sed -i 's/^PG_PUBLISH=.*/PG_PUBLISH=127.0.0.1:15432/' .env
docker compose up -d postgres
sudo iptables -t raw -D PREROUTING -i tailscale0 -p tcp -d 100.98.241.81 --dport 5432 ! -s 100.126.116.57 -j DROP
docker compose ps                                                              # 성공: 전부 healthy
```

## 8. 백업 설치 — 전환 직후

8-1 〔맥〕
```bash
sudo install -m 755 backup/aod-backup.sh /usr/local/sbin/aod-backup.sh
sudo install -m 644 backup/aod-backup.service backup/aod-backup.timer /etc/systemd/system/
sudo systemd-analyze verify /etc/systemd/system/aod-backup.service /etc/systemd/system/aod-backup.timer && echo UNITS-OK
sudo systemctl daemon-reload && sudo systemctl enable --now aod-backup.timer
systemctl list-timers aod-backup.timer --no-pager                             # 성공: NEXT 가 03:30 KST(PDT 로 표시될 수 있음)
sudo systemctl start aod-backup.service && journalctl -u aod-backup -n 3 --no-pager   # 성공: OK /srv/aod-backups/aod-….dump
```
8-2 오프사이트(R2) — 〔사용자〕 Cloudflare R2 → 버킷 `aod-backups` 생성 → 수명주기 규칙 "7일 뒤 삭제" → R2 API 토큰(Object Read & Write, 이 버킷만). 〔사용자 터미널, main-server〕
```bash
sudo apt-get install -y rclone
sudo rclone config create r2 s3 provider Cloudflare access_key_id <키ID> secret_access_key <시크릿> endpoint https://<계정ID>.r2.cloudflarestorage.com
```
〔맥〕
```bash
sudo systemctl start aod-backup.service && journalctl -u aod-backup -n 3 --no-pager   # 성공: offsite: r2:aod-backups/…
```
8-3 〔맥〕 복원 리허설 1회
```bash
F=$(ls -t /srv/aod-backups/aod-*.dump | head -1)
docker compose exec -T postgres psql -U postgres -c "DROP DATABASE IF EXISTS restore_check" -c "CREATE DATABASE restore_check"
docker compose exec -T postgres pg_restore -U postgres -d restore_check "/backups/$(basename "$F")"; echo "exit=$?"
docker compose exec -T postgres psql -U postgres -d restore_check -X -q -At -F ' ' -f - < verify.sql > /tmp/rc.verify
docker compose exec -T postgres psql -U postgres -d postgres -X -q -At -F ' ' -f - < verify.sql > /tmp/live.verify
diff /tmp/rc.verify /tmp/live.verify; docker compose exec -T postgres psql -U postgres -c "DROP DATABASE restore_check"
```
성공: `exit=0`, diff는 크롤 계열 몇 건 차이뿐.

## 9. D+2 정리

〔맥〕
```bash
rm -f ~/.pgpass-rds
docker compose exec -T postgres psql -U postgres -d template1 -c "DROP DATABASE IF EXISTS postgres_old"
# 추천 라우터를 tailnet 에서 내린다(인증 없음). api 는 Docker 네트워크로 부르므로 영향 없다.
cd ~/-AOD-All-of-Dopamine-AI/recommendation/serving
sed -i 's/^BIND_IP=.*/BIND_IP=127.0.0.1/' deploy.env && scripts/deploy.sh up
sudo systemctl disable --now aod-rec-fw
sudo iptables -t raw -D PREROUTING -i tailscale0 -p tcp -d 100.98.241.81 --dport 8080 ! -s 100.126.116.57 -j DROP 2> /dev/null || true
docker compose -f ~/-AOD-All-of-Dopamine-back/deploy/main-server/docker-compose.yml exec -T api curl -fsS http://rec-router:8080/health | head -c 60; echo
```
〔사용자〕 AWS 콘솔(리전 ap-northeast-2)
1. EC2 2대 **종료(terminate)** — 연결 EBS 삭제 확인
2. RDS `aod-database` 삭제 — 최종 스냅샷 생략(맥 복원 검증 + PC 사본), 남은 자동·수동 스냅샷도 삭제
3. Elastic IP `13.125.165.117` 릴리스
4. ECR `aod-api` · `aod-crawler` 삭제
5. IAM 사용자 `claude-aod-deploy` 삭제(〔macOS〕 `~/.aws` 의 키도 지운다)
6. 보안 그룹 `AOD_Test_database` 등 남은 SG · 키 페어 삭제. 다음 날 Billing → 일별 비용 0 확인

〔사용자〕 GitHub 시크릿 `AWS_ACCESS_KEY_ID` · `AWS_SECRET_ACCESS_KEY` · `API_EC2_HOST` · `CRAWLER_EC2_HOST` · `EC2_SSH_PRIVATE_KEY` · `EC2_USER` · `POSTGRES_*` 삭제
〔사용자〕 Tailscale 어드민에서 `aod-api` 노드 삭제. Cloudflare 터널에서 `api-mac` 호스트명 삭제.
〔사용자〕 AI 레포 `.env`의 `AOD_DB_HOST`를 RDS에서 바꾸기 전엔 AI 배치를 돌리지 않는다(후속 작업).

## 10. 롤백

| 시점 | 할 일 |
|---|---|
| §6-1 전 | 할 일 없음 |
| §6-2 ~ 6-4 실패 | 〔사용자〕 EC2 크롤러·API 다시 **시작**. `api`가 아직 EC2 A 레코드라 그걸로 끝. 〔맥〕 `PG_PUBLISH` 되돌리고 iptables 규칙 삭제 |
| §6-5 이후 실패 | 〔사용자〕 Cloudflare 터널에서 `api` 호스트명 삭제 → DNS `api` A 13.125.165.117(프록시 끔) 추가 → EC2 다시 시작. 그 사이 맥 DB에 들어온 쓰기는 유실 |

## 11. 운영 치트시트

```bash
docker compose ps                                     # 상태
docker compose logs -f --tail 100 api                 # 로그
docker stats --no-stream                              # 메모리
git pull && docker compose build && docker compose up -d   # 코드 갱신 (2단계 CI 전까지)
ls -lt /srv/aod-backups | head                        # 최근 백업
```
