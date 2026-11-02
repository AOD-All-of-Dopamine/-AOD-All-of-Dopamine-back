# AWS → 맥미니(main-server) 백엔드 이전 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AWS의 API EC2·크롤러 EC2·RDS를 맥미니 OrbStack VM `main-server`로 옮기고 `api.allofdophamin.com`을 Cloudflare Tunnel로 그쪽에 붙인 뒤 AWS 과금을 끊는다.

**Architecture:** 백엔드 레포에 `deploy/main-server/` compose 프로젝트(`aod-backend`: pgvector postgres · api · crawler · cloudflared)를 추가하고, 크롤러가 arm64에서 OS 패키지 chromium을 쓰도록 이미지와 `ChromeDriverProvider`를 고친다. 데이터는 RDS `pg_dump -Fd` → `pg_restore`로 옮기고 `verify.sql` diff로 검증한다. 옛 DNS 캐시 꼬리는 48시간 동안 EC2 API를 맥 DB로 돌리는 브리지로 흡수한다.

**Tech Stack:** Spring Boot 3.5 / Java 17 (Gradle 멀티모듈), Selenium 4.11 + WebDriverManager 6.1, Docker Compose, PostgreSQL 17 + pgvector 0.8.1, Cloudflare Tunnel, systemd timer, rclone(R2).

**Spec:** `docs/superpowers/specs/2026-11-02-aws-to-macmini-migration-design.md`

## Global Constraints

- 대상 VM: `main-server` (Ubuntu 24.04 **arm64**, Tailscale IP `100.98.241.81`), 캡 12 GB / 6 CPU / 80 GiB.
- 이미지 태그 고정: `pgvector/pgvector:0.8.1-pg17`, `cloudflare/cloudflared:2026.10.7`, `postgres:17`(덤프 클라이언트), `eclipse-temurin:17-jdk`(빌드), `eclipse-temurin:17-jre`, `debian:trixie-slim`.
- `eclipse-temurin:17-jdk-alpine` 금지 — amd64 전용.
- postgres 서버 `timezone=UTC`(RDS와 동일). api·crawler는 `TZ=Asia/Seoul` + `-Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8`.
- 호스트 publish는 `127.0.0.1`만: api `18080`, crawler `18081`, postgres `15432`. 예외는 브리지 기간 `PG_PUBLISH=100.98.241.81:5432` + iptables로 `100.126.116.57`만 허용.
- 힙 옵션은 compose `JAVA_TOOL_OPTIONS` 한 곳에서만(Dockerfile ENTRYPOINT에 `-Xmx` 금지).
- 비밀값(RDS 마스터 비번, `.env` 값, 터널 토큰, R2 키)은 채팅·커밋에 넣지 않는다. 사용자가 터미널·파일에 직접 입력한다.
- AWS 콘솔·Cloudflare·Gabia·GitHub 시크릿 작업과 OrbStack 캡 변경·재시작은 사용자가 하거나 사용자의 직접 OK 뒤에만.
- 커밋 메시지 끝: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 브랜치: `feat/macmini-migration` (origin/main에서 분기, 스펙 커밋 `156ff77` 위).
- 이 PC 명령은 레포 루트 `D:\AOD\-AOD-All-of-Dopamine-back`에서. Gradle은 PowerShell `.\gradlew.bat`(작업 트리의 `gradlew`가 CRLF라 Git Bash `./gradlew`는 안 됨).

## 실행 분담

| 트랙 | Task | 누가 |
|---|---|---|
| 0. 데이터 안전 | 0 | 사용자(이 PC 터미널에서 비번 입력) |
| A. 코드 | 1–7 | 이 세션 |
| B. 운영 | 8–13 | 맥 세션 "Main server AI backend 연동" + 사용자 (`deploy/main-server/RUNBOOK.md` 순서) |

Task 0은 다른 모든 것과 독립이며 **가장 먼저** 한다. Track A와 Task 8(호스트 준비)·Task 10의 Cloudflare 계정·NS 교체는 병렬 가능.

---

### Task 0: AWS 밖 첫 사본 — 이 PC로 RDS 전체 덤프

**Files:** 없음(레포 밖 `D:\AOD\backup\`, git 저장소 아님)

- [ ] **Step 1: 덤프 (사용자 — 비번 프롬프트에 RDS 마스터 비번 입력)**

PowerShell:
```powershell
New-Item -ItemType Directory -Force D:\AOD\backup | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$rdsHost = ((Get-Content D:\AOD\-AOD-All-of-Dopamine-AI\.env | Select-String '^AOD_DB_HOST=') -split '=', 2)[1].Trim()
& "C:\Program Files\PostgreSQL\17\bin\pg_dump.exe" -h $rdsHost -U postgres -d postgres -W -Fd -j4 -Z1 -f "D:\AOD\backup\rds-$stamp"
```
RDS 엔드포인트는 레포가 퍼블릭이라 문서에 적지 않는다 — AI 레포의 gitignore된 `.env`(`AOD_DB_HOST`)에서 읽는다.
Expected: 몇 분 뒤 에러 없이 프롬프트 복귀.

- [ ] **Step 2: 사본이 읽히는지 확인**

```powershell
$d = Get-ChildItem D:\AOD\backup -Directory | Sort-Object Name | Select-Object -Last 1
"{0:N0} MB" -f ((Get-ChildItem $d.FullName | Measure-Object Length -Sum).Sum / 1MB)
Test-Path (Join-Path $d.FullName "toc.dat")
& "C:\Program Files\PostgreSQL\17\bin\pg_restore.exe" -l $d.FullName | Select-String "TABLE DATA (\S+) " | ForEach-Object { $_.Matches[0].Groups[1].Value } | Group-Object | Select-Object Name, Count
```
Expected: 수백 MB 이상, `True`, 스키마 네 개(`aod_ai` · `aod_log` · `aod_rec` · `public`)가 모두 나오고 `public`이 20개 이상.

---

### Task 1: `ChromeDriverProvider` — 컨테이너가 준 Chrome·chromedriver 경로 사용

arm64 리눅스용 공식 chromedriver가 없어서, `WebDriverManager.chromedriver().setup()`이 main-server에서 기동 시점에 실패한다. 환경변수 `CHROMEDRIVER_PATH`·`CHROME_BIN`이 있으면 그 경로를 쓰고 다운로드를 건너뛴다. 없으면 지금과 동일(로컬 개발 영향 없음).

**Files:**
- Create: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/util/ChromePaths.java`
- Modify: `-AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/util/ChromeDriverProvider.java` (전체 교체)
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/util/ChromePathsTest.java`
- Test: `-AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/util/ChromeDriverProviderTest.java`

**Interfaces:**
- Produces: `record ChromePaths(String chromeBinary, String chromeDriver)` (package-private) — `static ChromePaths from(Map<String,String> env)`, `boolean hasDriver()`, 상수 `CHROME_BIN = "CHROME_BIN"`, `CHROMEDRIVER_PATH = "CHROMEDRIVER_PATH"`.
- Produces: `ChromeDriverProvider` — 공개 API 불변(`public WebDriver getDriver()`, `@PostConstruct public void setupChromeDriver()`), 추가로 package-private `ChromeDriverProvider(ChromePaths)`와 `ChromeOptions buildOptions()`.
- Task 2의 Dockerfile이 `ENV CHROME_BIN=/usr/bin/chromium CHROMEDRIVER_PATH=/usr/bin/chromedriver`를 넣는다.

- [ ] **Step 1: 실패하는 테스트 작성 — `ChromePathsTest`**

```java
package com.example.crawler.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChromePathsTest {

    @Test
    void emptyEnvLeavesBothUnsetSoWebDriverManagerDecides() {
        ChromePaths p = ChromePaths.from(Map.of());
        assertNull(p.chromeBinary());
        assertNull(p.chromeDriver());
        assertFalse(p.hasDriver());
    }

    @Test
    void driverPathAloneIsEnough() {
        ChromePaths p = ChromePaths.from(Map.of("CHROMEDRIVER_PATH", "/usr/bin/chromedriver"));
        assertEquals("/usr/bin/chromedriver", p.chromeDriver());
        assertNull(p.chromeBinary());
        assertTrue(p.hasDriver());
    }

    @Test
    void bothPathsAreReadAndTrimmed() {
        ChromePaths p = ChromePaths.from(Map.of(
                "CHROME_BIN", " /usr/bin/chromium ",
                "CHROMEDRIVER_PATH", "/usr/bin/chromedriver\n"));
        assertEquals("/usr/bin/chromium", p.chromeBinary());
        assertEquals("/usr/bin/chromedriver", p.chromeDriver());
    }

    @Test
    void blankValuesCountAsAbsent() {
        // compose 의 ${VAR:-} 처럼 빈 문자열로 들어오는 경우
        ChromePaths p = ChromePaths.from(Map.of("CHROME_BIN", "  ", "CHROMEDRIVER_PATH", ""));
        assertNull(p.chromeBinary());
        assertFalse(p.hasDriver());
    }
}
```

- [ ] **Step 2: 실패하는 테스트 작성 — `ChromeDriverProviderTest`**

```java
package com.example.crawler.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChromeDriverProviderTest {

    @AfterEach
    void clearDriverProperty() {
        System.clearProperty("webdriver.chrome.driver");
    }

    @Test
    void setupUsesGivenDriverPathInsteadOfDownloading() {
        new ChromeDriverProvider(new ChromePaths(null, "/usr/bin/chromedriver")).setupChromeDriver();

        assertEquals("/usr/bin/chromedriver", System.getProperty("webdriver.chrome.driver"));
    }

    @Test
    void optionsUseGivenChromeBinaryAndNewHeadless() {
        ChromeOptions options = new ChromeDriverProvider(
                new ChromePaths("/usr/bin/chromium", "/usr/bin/chromedriver")).buildOptions();

        Map<String, Object> chrome = chromeOptions(options);
        assertEquals("/usr/bin/chromium", chrome.get("binary"));
        List<?> args = (List<?>) chrome.get("args");
        assertTrue(args.contains("--headless=new"), args.toString());
        assertTrue(args.contains("--no-sandbox"), args.toString());
    }

    @Test
    void optionsLeaveBinaryUnsetWhenNotGiven() {
        ChromeOptions options = new ChromeDriverProvider(new ChromePaths(null, null)).buildOptions();

        assertFalse(chromeOptions(options).containsKey("binary"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> chromeOptions(ChromeOptions options) {
        return (Map<String, Object>) options.asMap().get(ChromeOptions.CAPABILITY);
    }
}
```

- [ ] **Step 3: 실패 확인**

PowerShell(레포 루트):
```powershell
.\gradlew.bat :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.util.ChromePathsTest" --tests "com.example.crawler.util.ChromeDriverProviderTest"
```
Expected: `compileTestJava` FAILED — `cannot find symbol: class ChromePaths`.

- [ ] **Step 4: `ChromePaths` 구현**

```java
package com.example.crawler.util;

import java.util.Map;

/**
 * 컨테이너가 넘겨 주는 Chrome·chromedriver 경로.
 * 비어 있으면 null — 그때는 예전처럼 WebDriverManager 가 드라이버를 정한다(로컬 개발).
 * arm64 리눅스(맥미니 main-server)에는 공식 chromedriver 배포본이 없어 OS 패키지(chromium-driver) 경로를 받는다.
 */
record ChromePaths(String chromeBinary, String chromeDriver) {

    static final String CHROME_BIN = "CHROME_BIN";
    static final String CHROMEDRIVER_PATH = "CHROMEDRIVER_PATH";

    static ChromePaths from(Map<String, String> env) {
        return new ChromePaths(blankToNull(env.get(CHROME_BIN)), blankToNull(env.get(CHROMEDRIVER_PATH)));
    }

    boolean hasDriver() {
        return chromeDriver != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

- [ ] **Step 5: `ChromeDriverProvider` 전체 교체**

주석 처리돼 있던 옛 `SELENIUM_REMOTE_URL` 블록은 죽은 코드라 지운다.

```java
package com.example.crawler.util;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class ChromeDriverProvider {

    private final ChromePaths paths;

    public ChromeDriverProvider() {
        this(ChromePaths.from(System.getenv()));
    }

    ChromeDriverProvider(ChromePaths paths) {
        this.paths = paths;
    }

    /**
     * 🚀 앱 시작 시 1회만 ChromeDriver 를 정한다.
     * - 매번 setup() 호출 시 HTTP 클라이언트 스레드 누수 발생 (75분간 462개 생성 문제 해결)
     * - CHROMEDRIVER_PATH 가 있으면(컨테이너) 그 경로를 쓰고 다운로드를 건너뛴다.
     */
    @PostConstruct
    public void setupChromeDriver() {
        if (paths.hasDriver()) {
            System.setProperty("webdriver.chrome.driver", paths.chromeDriver());
            log.info("✅ ChromeDriver 경로 지정: {} (Chrome: {})", paths.chromeDriver(),
                    paths.chromeBinary() == null ? "기본 위치" : paths.chromeBinary());
            return;
        }
        log.info("🔧 ChromeDriver 초기 설정 시작 (WebDriverManager, 1회만 실행)");
        WebDriverManager.chromedriver().setup();
        log.info("✅ ChromeDriver 설정 완료");
    }

    public WebDriver getDriver() {
        // setup 은 @PostConstruct 에서 1회만 — 여기서 다시 부르지 않는다
        ChromeDriver driver = new ChromeDriver(buildOptions());

        // 🚀 핵심: Timeout 설정 (좀비 프로세스 방지!)
        // timeout 없이 driver.get() 호출 시 무한 대기 → quit() 도달 못 함 → 좀비 프로세스
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        log.debug("ChromeDriver 생성 완료 (timeout 설정: pageLoad=30s, script=30s, implicit=10s)");
        return driver;
    }

    ChromeOptions buildOptions() {
        ChromeOptions options = new ChromeOptions();
        if (paths.chromeBinary() != null) {
            options.setBinary(paths.chromeBinary());
        }

        // 기본 옵션
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--mute-audio");
        options.addArguments("--no-zygote");  // 🚀 Chrome zygote 프로세스 생성 방지 (좀비 방지)

        // 🚀 메모리 최적화 옵션
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-images");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-software-rasterizer");

        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        return options;
    }
}
```

- [ ] **Step 6: 새 테스트 통과 확인**

```powershell
.\gradlew.bat :-AOD-All-of-Dopamine-crawler:test --tests "com.example.crawler.util.ChromePathsTest" --tests "com.example.crawler.util.ChromeDriverProviderTest"
```
Expected: `BUILD SUCCESSFUL`, 7 tests passed.

- [ ] **Step 7: 크롤러 전체 테스트(회귀)**

```powershell
.\gradlew.bat :-AOD-All-of-Dopamine-crawler:test
```
Expected: `BUILD SUCCESSFUL`. (contextLoads는 `CHROMEDRIVER_PATH`가 없으니 예전처럼 WebDriverManager를 탄다 — 이 PC에 Chrome이 있어 통과.)

- [ ] **Step 8: Commit**

```bash
git add -- -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/util/ChromePaths.java -AOD-All-of-Dopamine-crawler/src/main/java/com/example/crawler/util/ChromeDriverProvider.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/util/ChromePathsTest.java -AOD-All-of-Dopamine-crawler/src/test/java/com/example/crawler/util/ChromeDriverProviderTest.java
git commit -m "feat(crawler): CHROME_BIN·CHROMEDRIVER_PATH 로 브라우저·드라이버 경로 주입

arm64 리눅스에는 공식 chromedriver 배포본이 없어 WebDriverManager 가 기동 시 실패한다.
경로 환경변수가 있으면 그것을 쓰고 다운로드를 건너뛴다. 없으면 예전과 같다.
headless 는 --headless=new 로.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 크롤러 이미지 — arm64에서 도는 Debian + chromium

**Files:**
- Modify: `-AOD-All-of-Dopamine-crawler/Dockerfile` (전체 교체)

**Interfaces:**
- Consumes: Task 1의 `CHROME_BIN`·`CHROMEDRIVER_PATH` 환경변수 계약.
- Produces: 이미지가 `/app/logs`를 `appuser`(1001) 소유로 가진다 → Task 4 compose의 named volume `aod-crawler-logs:/app/logs`가 이 소유권을 물려받는다.

- [ ] **Step 1: 지금 이미지가 arm64에서 안 되는 이유를 확인(실패 재현)**

Git Bash:
```bash
docker manifest inspect eclipse-temurin:17-jdk-alpine | grep -c '"arm64"'
grep -n 'google-chrome\|jdk-alpine\|Xmx' -- -AOD-All-of-Dopamine-crawler/Dockerfile
```
Expected: `0`(alpine에 arm64 없음), 그리고 `jdk-alpine`·`google-chrome-stable`(amd64 저장소)·`-Xmx2048m` 줄이 보인다.

- [ ] **Step 2: Dockerfile 전체 교체**

```dockerfile
# ── Build stage ──
# eclipse-temurin:17-jdk-alpine 은 amd64 전용이라 arm64(맥미니 main-server)에서 빌드가 안 된다 → Ubuntu 기반 JDK.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew .
COPY gradlew.bat .
COPY settings.gradle .
COPY build.gradle .
COPY -AOD-All-of-Dopamine-shared -AOD-All-of-Dopamine-shared
COPY -AOD-All-of-Dopamine-crawler -AOD-All-of-Dopamine-crawler

# git 모드가 100644 이고, Windows 체크아웃에선 CRLF 일 수 있다 — 어느 쪽에서 빌드해도 돌게.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true
RUN ./gradlew :-AOD-All-of-Dopamine-crawler:bootJar -x test --no-daemon

# ── Runtime stage ──
# Google Chrome 은 리눅스 arm64 빌드가 없고, Ubuntu 의 chromium 은 Snap 스텁이라 컨테이너에서 못 쓴다
# → Debian 의 chromium·chromium-driver. trixie 에는 openjdk-17 패키지가 없어 JRE 는 Temurin 것을 복사한다.
FROM debian:trixie-slim
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    CHROME_BIN=/usr/bin/chromium \
    CHROMEDRIVER_PATH=/usr/bin/chromedriver
COPY --from=eclipse-temurin:17-jre /opt/java/openjdk /opt/java/openjdk

# tini: PID 1 로 좀비 Chrome 프로세스를 거둔다. tzdata: TZ=Asia/Seoul 이 Chromium·셸에도 먹게.
RUN apt-get update && apt-get install -y --no-install-recommends \
        chromium \
        chromium-driver \
        fonts-liberation \
        tini \
        curl \
        ca-certificates \
        tzdata \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 버전을 빌드 로그에 남긴다 (드라이버와 브라우저 메이저 버전이 같아야 한다)
RUN java -version && chromium --version && chromedriver --version

WORKDIR /app
COPY --from=build /app/-AOD-All-of-Dopamine-crawler/build/libs/*.jar app.jar

# 비루트 사용자. /app/logs 를 미리 만들어 두면 named volume 이 첫 마운트 때 이 소유권을 물려받는다.
RUN groupadd -g 1001 appuser && \
    useradd -u 1001 -g appuser -s /bin/bash -d /home/appuser -m appuser && \
    mkdir -p /app/logs && \
    chown -R appuser:appuser /app

USER appuser
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

# 힙은 여기서 정하지 않는다 — compose 의 JAVA_TOOL_OPTIONS 한 곳에서만 (명령줄 -Xmx 는 그것을 덮어쓴다).
ENTRYPOINT ["/usr/bin/tini", "--", "java", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/heapdump.hprof", \
    "-jar", "app.jar"]
```

- [ ] **Step 3: 이 PC에서 빌드(amd64 — 같은 Dockerfile이 arm64에서도 그대로 빈다; arm64 실빌드는 Task 9)**

Git Bash(레포 루트):
```bash
docker build -f -AOD-All-of-Dopamine-crawler/Dockerfile -t aod-crawler:mig .
```
Expected: 성공. 빌드 로그에 `openjdk version "17.`, `Chromium 1`, `ChromeDriver 1` 세 줄.

- [ ] **Step 4: 런타임 확인 — JRE·브라우저·드라이버·문자셋·사용자**

```bash
docker run --rm --entrypoint sh aod-crawler:mig -c '
  id; echo "LANG=$LANG CHROME_BIN=$CHROME_BIN CHROMEDRIVER_PATH=$CHROMEDRIVER_PATH";
  ls -ld /app/logs;
  java -XshowSettings:properties -version 2>&1 | grep -E "native.encoding|file.encoding|java.version ";
  chromium --version; chromedriver --version'
```
Expected: `uid=1001(appuser)`, `LANG=C.UTF-8`, `/app/logs` 소유자 `appuser`, `native.encoding = UTF-8`·`file.encoding = UTF-8`, `java.version = 17.`, Chromium·ChromeDriver 메이저 버전 동일.

- [ ] **Step 5: 헤드리스 Chromium이 실제 페이지를 연다**

```bash
docker run --rm --entrypoint sh aod-crawler:mig -c 'chromium --headless=new --no-sandbox --disable-gpu --disable-dev-shm-usage --dump-dom https://store.steampowered.com/ 2>/dev/null | head -c 120'
```
Expected: `<html` 로 시작하는 출력.

- [ ] **Step 6: Commit**

```bash
git add -- -AOD-All-of-Dopamine-crawler/Dockerfile
git commit -m "build(crawler): arm64 용 이미지 — Debian trixie + chromium, Temurin JRE 복사

alpine JDK 는 amd64 전용, Google Chrome 은 리눅스 arm64 빌드가 없다.
런타임은 Debian chromium·chromium-driver(경로는 CHROME_BIN·CHROMEDRIVER_PATH),
JRE 는 eclipse-temurin:17-jre 에서 복사, LANG=C.UTF-8. ENTRYPOINT 의 -Xmx 제거(compose 에서만).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: API 이미지 — Chrome 제거, arm64 빌드, 로그 디렉터리

**Files:**
- Modify: `-AOD-All-of-Dopamine-api/Dockerfile` (전체 교체)

**Interfaces:**
- Produces: 이미지가 `/var/log/aod`를 `appuser` 소유로 가진다 → Task 4 compose의 named volume `aod-api-logs:/var/log/aod` (prod 프로필 `logging.file.name=/var/log/aod/application.log`).

- [ ] **Step 1: API 코드가 Selenium을 안 쓰는지 다시 확인**

```bash
grep -rIln --include='*.java' -iE 'org\.openqa|webdriver|chrome' ./-AOD-All-of-Dopamine-api/src ./-AOD-All-of-Dopamine-shared/src || echo "NO SELENIUM USE"
```
Expected: `NO SELENIUM USE`. 아니면 멈추고 Chrome 제거를 다시 검토한다.

- [ ] **Step 2: Dockerfile 전체 교체**

```dockerfile
# ── Build stage ──
# eclipse-temurin:17-jdk-alpine 은 amd64 전용이라 arm64(맥미니 main-server)에서 빌드가 안 된다 → Ubuntu 기반 JDK.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew .
COPY gradlew.bat .
COPY settings.gradle .
COPY build.gradle .
COPY -AOD-All-of-Dopamine-shared -AOD-All-of-Dopamine-shared
COPY -AOD-All-of-Dopamine-api -AOD-All-of-Dopamine-api

# git 모드가 100644 이고, Windows 체크아웃에선 CRLF 일 수 있다 — 어느 쪽에서 빌드해도 돌게.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true
# SENTRY_AUTH_TOKEN 이 없으면 sentry 플러그인이 소스 번들 업로드를 건너뛴다(build.gradle 의 sentry 블록).
RUN ./gradlew :-AOD-All-of-Dopamine-api:bootJar -x test --no-daemon

# ── Runtime stage ──
# API 코드는 Selenium 을 쓰지 않는다 — 예전 이미지의 Chrome 설치(약 400MB)는 필요 없었다.
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/-AOD-All-of-Dopamine-api/build/libs/*.jar app.jar

# 비루트 사용자. prod 프로필이 /var/log/aod/application.log 에 쓴다 — 미리 만들어 두면
# named volume 이 첫 마운트 때 이 소유권을 물려받는다.
RUN groupadd -g 1001 appuser && \
    useradd -u 1001 -g appuser -s /bin/bash -d /home/appuser -m appuser && \
    mkdir -p /var/log/aod && \
    chown -R appuser:appuser /app /var/log/aod

USER appuser
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 힙 크기는 여기서 정하지 않는다 — docker-compose 의 JAVA_TOOL_OPTIONS 한 곳에서만 정한다.
ENTRYPOINT ["java", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/heapdump.hprof", \
    "-jar", "app.jar"]
```

- [ ] **Step 3: 빌드**

```bash
docker build -f -AOD-All-of-Dopamine-api/Dockerfile -t aod-api:mig .
```
Expected: 성공.

- [ ] **Step 4: 런타임 확인**

```bash
docker run --rm --entrypoint sh aod-api:mig -c 'id; ls -ld /var/log/aod; command -v google-chrome || echo "no chrome"; java -version 2>&1 | head -1; ls -la /app/app.jar'
docker image ls aod-api:mig --format '{{.Repository}}:{{.Tag}} {{.Size}}'
```
Expected: `uid=1001(appuser)`, `/var/log/aod` 소유자 `appuser`, `no chrome`, `openjdk version "17.`, `app.jar` 존재. 크기는 예전(Chrome 포함)보다 수백 MB 작다.

- [ ] **Step 5: Commit**

```bash
git add -- -AOD-All-of-Dopamine-api/Dockerfile
git commit -m "build(api): Chrome 제거·arm64 빌드·/var/log/aod 소유권

API 코드는 Selenium 을 쓰지 않는다. 빌드 스테이지는 amd64 전용 alpine 대신
eclipse-temurin:17-jdk. prod 프로필 로그 경로를 비루트 사용자가 쓸 수 있게 만든다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: `deploy/main-server` compose · 롤 초기화 · 검증 SQL

**Files:**
- Create: `deploy/main-server/docker-compose.yml`
- Create: `deploy/main-server/.env.example`
- Create: `deploy/main-server/init/01-roles.sh` (실행 비트)
- Create: `deploy/main-server/verify.sql`

**Interfaces:**
- Consumes: Task 2·3의 Dockerfile 경로와 볼륨 소유권 계약.
- Produces: 컨테이너 이름 `aod-postgres`·`aod-api`·`aod-crawler`·`aod-cloudflared`, compose 프로젝트 `aod-backend`, postgres 안의 `/backups` 마운트(=호스트 `/srv/aod-backups`). Task 5 백업 스크립트와 Task 7 런북이 이 이름을 쓴다.
- Produces: `verify.sql` — `psql -X -q -At -F ' ' -f verify.sql` 출력은 `키 값` 줄, 키 정렬. RDS와 대상 출력을 `diff`.

- [ ] **Step 1: 실패 확인 — 롤 없는 빈 pgvector 컨테이너**

Git Bash:
```bash
docker run -d --name aod-pg-test -e POSTGRES_PASSWORD=testpw pgvector/pgvector:0.8.1-pg17
until docker exec aod-pg-test pg_isready -U postgres -q; do sleep 1; done
docker exec aod-pg-test psql -U postgres -Atc "select count(*) from pg_roles where rolname in ('aod_ai','aod_public_ro')"
docker rm -f aod-pg-test
```
Expected: `0` (롤이 없어 RDS 덤프의 GRANT가 실패할 상태).

- [ ] **Step 2: `init/01-roles.sh` 작성**

```sh
#!/bin/sh
# 빈 데이터 디렉터리로 처음 뜰 때만 docker-entrypoint 가 실행한다(설계 §4).
# RDS 덤프의 GRANT·기본 권한이 가리키는 롤을 복원 전에 만들어 둔다.
# 롤 멤버십(aod_ai ∈ aod_public_ro)은 pg_dump 에 들어가지 않아서 여기서 만든다.
set -e
if [ -z "${AOD_AI_PASSWORD:-}" ]; then
  echo "AOD_AI_PASSWORD 가 필요하다" >&2
  exit 1
fi
psql -v ON_ERROR_STOP=1 -v ai_pw="$AOD_AI_PASSWORD" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE aod_public_ro NOLOGIN;
CREATE ROLE aod_ai LOGIN PASSWORD :'ai_pw';
GRANT aod_public_ro TO aod_ai;
SQL
```

실행 비트(소싱되면 `set -e`가 엔트리포인트 셸에 섞인다 → 실행되게):
```bash
git add --chmod=+x deploy/main-server/init/01-roles.sh
```

- [ ] **Step 3: 롤 생성 확인**

```bash
MSYS_NO_PATHCONV=1 docker run -d --name aod-pg-test -e POSTGRES_PASSWORD=testpw -e AOD_AI_PASSWORD=aitestpw \
  -v "$(pwd -W)/deploy/main-server/init:/docker-entrypoint-initdb.d:ro" pgvector/pgvector:0.8.1-pg17
until docker exec aod-pg-test pg_isready -U postgres -q; do sleep 1; done
docker logs aod-pg-test 2>&1 | grep -E '01-roles|GRANT ROLE|ERROR'
docker exec aod-pg-test psql -U postgres -Atc "select rolname||' login='||rolcanlogin from pg_roles where rolname like 'aod%' order by 1"
docker exec aod-pg-test psql -U postgres -Atc "select r.rolname||' in '||g.rolname from pg_auth_members m join pg_roles r on r.oid=m.member join pg_roles g on g.oid=m.roleid where r.rolname='aod_ai'"
docker exec -e PGPASSWORD=aitestpw aod-pg-test psql -h 127.0.0.1 -U aod_ai -d postgres -Atc "select current_user"
docker rm -f aod-pg-test
```
Expected: 로그에 `running /docker-entrypoint-initdb.d/01-roles.sh`와 `GRANT ROLE`, ERROR 없음 / `aod_ai login=true`, `aod_public_ro login=false` / `aod_ai in aod_public_ro` / `aod_ai`.

- [ ] **Step 4: `verify.sql` 작성**

```sql
-- 이전 검증: RDS 와 대상에서 같은 파일을 돌리고 출력을 diff 한다(설계 §4).
--   psql -X -q -At -F ' ' -f verify.sql
-- 공용 스키마·aod_ai 테이블과 시스템 카탈로그만 읽는다 → aod_ai 계정으로도 돈다.
select 'rows.' || t, n from (
              select 'public.raw_items' as t, count(*) as n from public.raw_items
    union all select 'public.platform_data',     count(*) from public.platform_data
    union all select 'public.contents',          count(*) from public.contents
    union all select 'public.crawl_job_queue',   count(*) from public.crawl_job_queue
    union all select 'public.game_contents',     count(*) from public.game_contents
    union all select 'public.transform_runs',    count(*) from public.transform_runs
    union all select 'public.movie_contents',    count(*) from public.movie_contents
    union all select 'public.tv_contents',       count(*) from public.tv_contents
    union all select 'public.webtoon_contents',  count(*) from public.webtoon_contents
    union all select 'public.users',             count(*) from public.users
    union all select 'aod_ai.content_embedding', count(*) from aod_ai.content_embedding
) x
union all
select 'flyway.rows', count(*) from public.flyway_schema_history
union all
select 'flyway.max_version', max(version::int) from public.flyway_schema_history where version is not null
union all
select 'tables.' || n.nspname, count(*)
  from pg_class c join pg_namespace n on n.oid = c.relnamespace
 where c.relkind in ('r', 'p') and n.nspname in ('public', 'aod_ai', 'aod_log', 'aod_rec')
 group by n.nspname
union all
select 'ext.vector', count(*) from pg_extension where extname = 'vector'
order by 1;
```

- [ ] **Step 5: `verify.sql`을 실제 RDS 스키마에 돌려 문법·테이블 이름 확인(읽기 전용, aod_ai 계정)**

```bash
ENVF="/d/AOD/-AOD-All-of-Dopamine-AI/.env"
H=$(grep -E '^AOD_DB_HOST=' "$ENVF" | cut -d= -f2- | tr -d '\r')
PGPASSWORD=$(grep -E '^AOD_DB_PASSWORD=' "$ENVF" | cut -d= -f2- | tr -d '\r') \
  "/c/Program Files/PostgreSQL/17/bin/psql" -h "$H" -U aod_ai -d postgres -X -q -At -F ' ' -f deploy/main-server/verify.sql
```
Expected: 에러 없이 18줄(행 수 11 + flyway 2 + 스키마별 테이블 수 4 + `ext.vector 1`). 예: `tables.aod_log 30`, `rows.public.raw_items 2…`.

- [ ] **Step 6: `.env.example` 작성**

```dotenv
# main-server aod-backend 설정 — cp .env.example .env && chmod 600 .env 후 값을 채운다.
# .env 는 커밋되지 않는다(.gitignore 의 .env 규칙). 비밀값은 채팅에 붙여 넣지 말고 여기에 직접 입력한다.

# ── DB — RDS 비번을 재사용하지 말고 새로 만든다:  openssl rand -base64 24 | tr -d '/+='
POSTGRES_PASSWORD=
AOD_AI_PASSWORD=

# ── 외부 API (GitHub Actions 시크릿과 같은 값) — 앞의 셋은 prod 프로필이 요구한다
TMDB_API_KEY=
STEAM_API_KEY=
OPENAI_API_KEY=
NAVER_ID=
NAVER_PW=
SENTRY_DSN=

# ── 추천 — REC_ALLOWED_USERS 를 비우면 전원 허용. 지금 운영값을 그대로 옮긴다.
REC_ENABLED=true
REC_ALLOWED_USERS=

# ── Cloudflare Tunnel 커넥터 토큰 (Zero Trust → Networks → Tunnels → aod-main-server)
# 토큰을 넣은 뒤 COMPOSE_PROFILES=tunnel 로 바꾸면 cloudflared 도 함께 뜬다.
CF_TUNNEL_TOKEN=
COMPOSE_PROFILES=

# ── postgres 를 호스트에 여는 주소. 평소 127.0.0.1:15432.
# 전환 후 48시간 브리지 동안만 100.98.241.81:5432 (런북 §6·§7 — iptables 로 EC2 만 허용한 뒤)
PG_PUBLISH=127.0.0.1:15432
```

- [ ] **Step 7: `docker-compose.yml` 작성**

```yaml
# AOD 백엔드 — 맥미니 OrbStack VM main-server 용
#   설계: docs/superpowers/specs/2026-11-02-aws-to-macmini-migration-design.md
#   순서: deploy/main-server/RUNBOOK.md
#   cd deploy/main-server && cp .env.example .env   (값 채우기)
#   docker compose build && docker compose up -d
# 추천 스택(compose 프로젝트 aod-rec)이 먼저 떠 있어야 한다 — api 가 그 네트워크의 rec-router 를 부른다.
name: aod-backend

x-java-common: &java-common
  restart: unless-stopped
  logging:
    driver: json-file
    options: { max-size: "10m", max-file: "3" }

services:
  postgres:
    image: pgvector/pgvector:0.8.1-pg17
    container_name: aod-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: postgres
      POSTGRES_DB: postgres
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD 를 .env 에 넣는다}
      AOD_AI_PASSWORD: ${AOD_AI_PASSWORD:?AOD_AI_PASSWORD 를 .env 에 넣는다}
    # RDS 와 같은 UTC — timestamptz 표시와 now() 기본값이 이전 전후로 바뀌지 않게. 앱 JVM 은 Asia/Seoul.
    command: >-
      postgres -c timezone=UTC -c log_timezone=UTC
      -c shared_buffers=512MB -c effective_cache_size=1GB -c max_connections=150
    volumes:
      - /srv/aod-pg:/var/lib/postgresql/data
      - /srv/aod-backups:/backups
      - ./init:/docker-entrypoint-initdb.d:ro
    ports:
      - "${PG_PUBLISH:-127.0.0.1:15432}:5432"
    shm_size: 256m
    mem_limit: 1536m
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d postgres"]
      interval: 10s
      timeout: 3s
      retries: 10
    networks: [aod-backend]

  api:
    <<: *java-common
    build:
      context: ../..
      dockerfile: -AOD-All-of-Dopamine-api/Dockerfile
    image: aod-api:local
    container_name: aod-api
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      TZ: Asia/Seoul
      # 힙은 여기 한 곳에서만. 컨테이너 한도 1.5G = 힙 1G + 비힙 0.3~0.4G.
      JAVA_TOOL_OPTIONS: "-Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8 -Xms256m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
      SPRING_PROFILES_ACTIVE: prod
      # application-prod.properties 가 ${POSTGRES_*} 를 참조하므로 둘 다 넣는다
      POSTGRES_HOST: postgres
      POSTGRES_PORT: "5432"
      POSTGRES_DB: postgres
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/postgres
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      TMDB_API_KEY: ${TMDB_API_KEY:?TMDB_API_KEY 를 .env 에 넣는다}
      STEAM_API_KEY: ${STEAM_API_KEY:?STEAM_API_KEY 를 .env 에 넣는다}
      # 운영 EC2 에서도 비어 있다(2026-11-02 확인) — 비어도 prod 프로필 자리표시자가 빈 문자열로 풀린다
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      SENTRY_DSN: ${SENTRY_DSN:-}
      # 추천 라우터는 같은 VM 의 aod-rec 스택 — Docker 네트워크로 부른다
      REC_ROUTER_BASE_URL: http://rec-router:8080
      REC_ENABLED: ${REC_ENABLED:-true}
      REC_ALLOWED_USERS: ${REC_ALLOWED_USERS:-}
    volumes:
      - aod-api-logs:/var/log/aod
    ports:
      - "127.0.0.1:18080:8080"
    mem_limit: 1536m
    cpus: 2.0
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    networks: [aod-backend, aod-rec]

  crawler:
    <<: *java-common
    build:
      context: ../..
      dockerfile: -AOD-All-of-Dopamine-crawler/Dockerfile
    image: aod-crawler:local
    container_name: aod-crawler
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      TZ: Asia/Seoul
      # 컨테이너 한도 2.5G = 힙 1.25G + 비힙 + Chromium 프로세스(힙 밖)
      JAVA_TOOL_OPTIONS: "-Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8 -Xms256m -Xmx1280m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
      SPRING_PROFILES_ACTIVE: prod
      POSTGRES_HOST: postgres
      POSTGRES_PORT: "5432"
      POSTGRES_DB: postgres
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/postgres
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      TMDB_API_KEY: ${TMDB_API_KEY}
      STEAM_API_KEY: ${STEAM_API_KEY}
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      NAVER_ID: ${NAVER_ID:-}
      NAVER_PW: ${NAVER_PW:-}
      SENTRY_DSN: ${SENTRY_DSN:-}
      HEADLESS_MODE: "true"
      # CHROME_BIN · CHROMEDRIVER_PATH 는 이미지 ENV 에 있다
    volumes:
      - aod-crawler-logs:/app/logs
    ports:
      - "127.0.0.1:18081:8081"
    shm_size: 512m
    mem_limit: 2560m
    cpus: 2.0
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 120s
    networks: [aod-backend]

  # 토큰이 준비되면 .env 에 COMPOSE_PROFILES=tunnel. 아웃바운드 전용 — 호스트 포트 없음.
  cloudflared:
    image: cloudflare/cloudflared:2026.10.7
    container_name: aod-cloudflared
    profiles: [tunnel]
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      TUNNEL_TOKEN: ${CF_TUNNEL_TOKEN:-}
    depends_on: [api]
    mem_limit: 128m
    networks: [aod-backend]

networks:
  aod-backend: {}
  aod-rec:
    external: true
    name: aod-rec_aod-rec

volumes:
  aod-api-logs: {}
  aod-crawler-logs: {}
```

- [ ] **Step 8: compose 문법·보간 확인**

```bash
SP="/c/Users/KOKYUN~1/AppData/Local/Temp/claude/C--Users-kokyungwoo--claude-spares-spare-VkYmPt/7c76e2bb-bd1e-4a8d-9bc2-0816b250033d/scratchpad"
printf 'POSTGRES_PASSWORD=x\nAOD_AI_PASSWORD=y\nTMDB_API_KEY=t\nSTEAM_API_KEY=s\nOPENAI_API_KEY=o\n' > "$SP/test.env"
docker compose -f deploy/main-server/docker-compose.yml --env-file "$SP/test.env" config --quiet && echo "CONFIG OK"
docker compose -f deploy/main-server/docker-compose.yml --env-file "$SP/test.env" config | grep -E 'published|host_ip|REC_ROUTER_BASE_URL|timezone'
docker compose -f deploy/main-server/docker-compose.yml --env-file "$SP/test.env" --profile tunnel config --services
printf 'AOD_AI_PASSWORD=y\n' > "$SP/missing.env"
docker compose -f deploy/main-server/docker-compose.yml --env-file "$SP/missing.env" config --quiet 2>&1 | head -2
```
Expected: `CONFIG OK` / host_ip `127.0.0.1`, published `15432`·`18080`·`18081`, `REC_ROUTER_BASE_URL: http://rec-router:8080`, `timezone=UTC` / 서비스 4개(api, cloudflared, crawler, postgres) / 마지막은 `POSTGRES_PASSWORD 를 .env 에 넣는다` 에러.

- [ ] **Step 9: Commit**

```bash
git add deploy/main-server/docker-compose.yml deploy/main-server/.env.example deploy/main-server/verify.sql
git add --chmod=+x deploy/main-server/init/01-roles.sh
git commit -m "feat(deploy): main-server compose — pgvector postgres·api·crawler·cloudflared

RDS 와 같은 UTC postgres, 롤 aod_ai·aod_public_ro 초기화, 추천 스택 네트워크(aod-rec_aod-rec)
외부 참조, 호스트 포트는 127.0.0.1 만(18080·18081·15432). 이전 검증용 verify.sql.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 일일 백업 — 스크립트 + systemd 타이머

**Files:**
- Create: `deploy/main-server/backup/aod-backup.sh` (실행 비트)
- Create: `deploy/main-server/backup/aod-backup.service`
- Create: `deploy/main-server/backup/aod-backup.timer`

**Interfaces:**
- Consumes: 컨테이너 이름 `aod-postgres`(Task 4).
- Produces: 환경변수 `AOD_BACKUP_DIR`(기본 `/srv/aod-backups`) · `AOD_BACKUP_KEEP_DAYS`(14) · `AOD_PG_CONTAINER`(`aod-postgres`). 출력 파일 `aod-YYYYMMDD-HHMM.dump`(pg_dump 커스텀 포맷). 성공 시 마지막 줄 `OK <파일> <크기>`.

- [ ] **Step 1: 시험용 DB 컨테이너**

Git Bash:
```bash
docker run -d --name aod-pg-bk -e POSTGRES_PASSWORD=testpw pgvector/pgvector:0.8.1-pg17
until docker exec aod-pg-bk pg_isready -U postgres -q; do sleep 1; done
docker exec aod-pg-bk psql -U postgres -c "create table t(id int); insert into t values (1),(2),(3);"
SP="/c/Users/KOKYUN~1/AppData/Local/Temp/claude/C--Users-kokyungwoo--claude-spares-spare-VkYmPt/7c76e2bb-bd1e-4a8d-9bc2-0816b250033d/scratchpad"
mkdir -p "$SP/bk"
```

- [ ] **Step 2: 스크립트가 없을 때 실패 확인**

```bash
AOD_PG_CONTAINER=aod-pg-bk AOD_BACKUP_DIR="$SP/bk" bash deploy/main-server/backup/aod-backup.sh; echo "exit=$?"
```
Expected: `No such file or directory`, `exit=127`.

- [ ] **Step 3: `aod-backup.sh` 작성**

```bash
#!/usr/bin/env bash
# AOD Postgres 일일 백업 — systemd aod-backup.timer 가 03:30 KST 에 부른다(설계 §10).
#   pg_dump(커스텀 포맷) → $AOD_BACKUP_DIR, $AOD_BACKUP_KEEP_DAYS 일 보관.
#   rclone 리모트 r2: 가 설정돼 있으면 r2:aod-backups/ 에도 복사한다(오프사이트).
set -euo pipefail

BACKUP_DIR="${AOD_BACKUP_DIR:-/srv/aod-backups}"
KEEP_DAYS="${AOD_BACKUP_KEEP_DAYS:-14}"
CONTAINER="${AOD_PG_CONTAINER:-aod-postgres}"
STAMP="$(TZ=Asia/Seoul date +%Y%m%d-%H%M)"
FILE="$BACKUP_DIR/aod-$STAMP.dump"

mkdir -p "$BACKUP_DIR"
trap 'rm -f "$FILE.partial"' EXIT

docker exec "$CONTAINER" pg_dump -U postgres -d postgres -Fc -Z6 > "$FILE.partial"
# 목차가 읽히는 덤프만 보관 목록에 올린다
docker exec -i "$CONTAINER" pg_restore -l < "$FILE.partial" > /dev/null
mv "$FILE.partial" "$FILE"

find "$BACKUP_DIR" -maxdepth 1 -name 'aod-*.dump' -mtime +"$KEEP_DAYS" -delete

if command -v rclone > /dev/null 2>&1 && rclone listremotes 2> /dev/null | grep -qx 'r2:'; then
  rclone copy "$FILE" r2:aod-backups/
  echo "offsite: r2:aod-backups/$(basename "$FILE")"
fi

echo "OK $FILE $(du -h "$FILE" | cut -f1)"
```

- [ ] **Step 4: 성공 경로**

```bash
AOD_PG_CONTAINER=aod-pg-bk AOD_BACKUP_DIR="$SP/bk" bash deploy/main-server/backup/aod-backup.sh; echo "exit=$?"
ls "$SP/bk"
F=$(ls -t "$SP"/bk/aod-*.dump | head -1)
docker exec -i aod-pg-bk pg_restore -l < "$F" | grep 'TABLE DATA public t '
```
Expected: `OK …/aod-YYYYMMDD-HHMM.dump …`, `exit=0` / `.partial` 없음 / `TABLE DATA public t postgres` 한 줄.

- [ ] **Step 5: 실패 경로 — 컨테이너가 없으면 0이 아닌 코드, 부분 파일 없음**

```bash
AOD_PG_CONTAINER=no-such-container AOD_BACKUP_DIR="$SP/bk" bash deploy/main-server/backup/aod-backup.sh; echo "exit=$?"
ls "$SP/bk" | grep -c partial
docker rm -f aod-pg-bk
```
Expected: `Error response from daemon: No such container`, `exit=1`, `0`.

- [ ] **Step 6: systemd 유닛 작성**

`deploy/main-server/backup/aod-backup.service`:
```ini
[Unit]
Description=AOD Postgres 일일 백업 (pg_dump -> /srv/aod-backups)
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/aod-backup.sh
```

`deploy/main-server/backup/aod-backup.timer`:
```ini
[Unit]
Description=AOD Postgres 일일 백업 타이머 (03:30 KST)

[Timer]
OnCalendar=*-*-* 03:30:00 Asia/Seoul
Persistent=true

[Install]
WantedBy=timers.target
```
(유닛 검증은 Linux가 필요하므로 main-server에서 — 런북 §8 `systemd-analyze verify`.)

- [ ] **Step 7: Commit**

```bash
git add --chmod=+x deploy/main-server/backup/aod-backup.sh
git add deploy/main-server/backup/aod-backup.service deploy/main-server/backup/aod-backup.timer
git commit -m "feat(deploy): main-server 일일 pg_dump 백업 + R2 오프사이트 복사

03:30 KST systemd 타이머, 14일 보관, 목차 검증 후에만 보관 목록에 올린다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: `main.yml` — push 배포 끄기(수동 실행만)

이 브랜치가 main에 머지되거나 다른 PR이 머지돼도 EC2로 자동 배포되지 않게 한다. 브리지(런북 §7)에서만 수동 실행한다.

**Files:**
- Modify: `.github/workflows/main.yml:3-8`

- [ ] **Step 1: 현재 트리거 확인**

```bash
sed -n '1,9p' .github/workflows/main.yml
```
Expected: `on:` 아래 `push: branches: [main, develop]`와 `workflow_dispatch:`.

- [ ] **Step 2: 트리거 교체**

`.github/workflows/main.yml`의
```yaml
on:
  push:
    branches:
      - main
      - develop
  workflow_dispatch:
```
를
```yaml
# 2026-11 맥미니 이전: 운영은 main-server(deploy/main-server)에서 돈다. 이 워크플로는 이전 직후
# EC2 브리지(설계 §6)에서만 수동으로 쓴다 — push 로 EC2 에 배포되지 않게 push 트리거를 뺐다.
# 2단계(GHCR + arm64 러너 + Tailscale 배포)로 교체할 때 다시 연다.
on:
  workflow_dispatch:
```
로 바꾼다.

- [ ] **Step 3: 확인**

```bash
python -I -c "import yaml; d=yaml.safe_load(open('.github/workflows/main.yml', encoding='utf-8')); print(list(d[True].keys()), list(d['jobs'].keys()))" 2>/dev/null \
  || grep -nA2 '^on:' .github/workflows/main.yml
```
Expected: `['workflow_dispatch'] ['build', 'deploy-api', 'deploy-crawler']` (PyYAML이 없으면 grep으로 `on:` 다음 줄이 `workflow_dispatch:`).

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/main.yml
git commit -m "ci: EC2 배포 워크플로를 수동 실행 전용으로 — 맥미니 이전 중 push 자동 배포 차단

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: 런북 + 브랜치 push

**Files:**
- Create: `deploy/main-server/RUNBOOK.md`

**Interfaces:**
- Consumes: Task 4·5의 이름·경로·포트 전부. Task 8–13은 이 파일의 절 번호로 진행한다.

- [ ] **Step 1: `RUNBOOK.md` 작성**

````markdown
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
````

- [ ] **Step 2: 런북 자체 점검 — 이름·포트가 compose와 맞는지**

```bash
for k in 18080 18081 15432 PG_PUBLISH aod-rec_aod-rec rec-router /backups COMPOSE_PROFILES api-mac; do
  printf '%-18s compose=%s runbook=%s\n' "$k" "$(grep -c -- "$k" deploy/main-server/docker-compose.yml deploy/main-server/.env.example | awk -F: '{s+=$2} END{print s}')" "$(grep -c -- "$k" deploy/main-server/RUNBOOK.md)"
done
```
Expected: `api-mac`만 compose=0(Cloudflare 쪽 이름이라 정상), 나머지는 양쪽 모두 1 이상.

- [ ] **Step 3: Commit + push**

```bash
git add deploy/main-server/RUNBOOK.md
git commit -m "docs(deploy): main-server 이전 런북 — 준비·리허설·Cloudflare·전환·브리지·백업·정리

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push -u origin feat/macmini-migration
```
Expected: 원격 브랜치 생성. PR은 https://github.com/AOD-All-of-Dopamine/-AOD-All-of-Dopamine-back/compare/main...feat/macmini-migration 에서 연다(이 PC에 `gh` 없음). **머지는 전환이 끝난 뒤** — 머지돼도 `main.yml`이 수동 전용이라 EC2 배포는 안 돈다.

---

### Task 8: 호스트 준비 (맥 세션 + 사용자) — 런북 §1

- [ ] **Step 1:** 〔macOS〕 디스크 80 GiB — 런북 §1-1. 성공 표시 확인.
- [ ] **Step 2:** 〔사용자 OK〕 메모리 12 GiB · CPU 6 + OrbStack 재시작 — §1-2. guest 사용자 공지 먼저.
- [ ] **Step 3:** 〔맥〕 §1-3 디렉터리, §1-4 clone + `feat/macmini-migration` 체크아웃.
- [ ] **Step 4:** 〔사용자 터미널〕 §1-5 `.env` · `~/.pgpass-rds`.
- [ ] **Step 5:** 〔맥〕 §1-6 세 줄 모두 성공.

### Task 9: 리허설 (맥 세션) — 런북 §2 → §3 → §4

- [ ] **Step 1:** §2 덤프 + RDS verify.
- [ ] **Step 2:** §3-1 ~ 3-4 복원·검증(리허설 허용 오차)·이름 교체.
- [ ] **Step 3:** §4 빌드 — **arm64 실빌드 확인**(빌드 로그의 `Chromium`/`ChromeDriver` 버전 줄).
- [ ] **Step 4:** §4 스모크 전부 성공. 특히 Steam 랭킹 `after`가 방금 시각.
- [ ] **Step 5:** `docker stats --no-stream`으로 main-server 전체 메모리 여유 확인(12 GiB 중 rec + chansu + aod-backend 합이 10 GiB 미만).

### Task 10: Cloudflare (사용자) — 런북 §5

- [ ] **Step 1:** §5-1 Gabia `api` TTL 300 → §5-2 Cloudflare 존·레코드 → §5-3 NS 교체. (Task 8과 병렬 가능 — Active까지 시간이 걸리므로 가장 먼저 시작해도 된다.)
- [ ] **Step 2:** §5-4 ~ 5-6 터널·`api-mac`·cloudflared 4연결.
- [ ] **Step 3:** §5-7 존 Active 후 `api-mac` UP, `api`는 아직 EC2 200.

### Task 11: 전환 (사용자 + 맥 세션) — 런북 §6

- [ ] **Step 1:** §6-1 EC2 두 대 중지(시각 기록).
- [ ] **Step 2:** §6-2 ~ 6-4 (verify **IDENTICAL** 아니면 §10 롤백).
- [ ] **Step 3:** §6-5 Cloudflare `api` → 터널(시각 기록 — 다운타임 = Step 3 시각 − Step 1 시각).
- [ ] **Step 4:** §6-6 · 6-7 확인.

### Task 12: 브리지 · 백업 (사용자 + 맥 세션) — 런북 §7 · §8

- [ ] **Step 1:** §7-1 ~ 7-3 EC2 API가 맥 DB에 연결, PC에서 5432 차단 확인.
- [ ] **Step 2:** §8-1 타이머 + 첫 백업, §8-2 R2, §8-3 복원 리허설.
- [ ] **Step 3:** 48시간 뒤 §7-4.

### Task 13: D+2 정리 (사용자 + 맥 세션) — 런북 §9

- [ ] **Step 1:** 〔맥〕 §9 맥 쪽 명령. 마지막 줄 `{"ready":true`.
- [ ] **Step 2:** 〔사용자〕 AWS 1~6, GitHub 시크릿, Tailscale·Cloudflare 정리.
- [ ] **Step 3:** 다음 날 AWS Billing 일별 비용 0 확인.
- [ ] **Step 4:** PR 머지(이 세션: 머지 전 `git fetch && git rebase origin/main`으로 최신화 → 사용자 승인 후 머지).
- [ ] **Step 5:** 후속 작업 등록: CI 2단계(GHCR + `ubuntu-24.04-arm` + Tailscale 배포, `tag:ci` ACL), AI 배치 DB 경로, `CF-Connecting-IP` 반영, 모니터링 스택, AI 레포 `DEPLOY_VM.md`·백엔드 `docs/7_MONITORING.md`의 EC2 서술 갱신.
