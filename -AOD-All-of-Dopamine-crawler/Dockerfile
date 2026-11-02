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
