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
