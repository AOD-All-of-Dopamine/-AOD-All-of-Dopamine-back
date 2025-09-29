# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build

# Java 17 JDK가 설치된 경량 Alpine Linux 이미지
# "AS build"는 이 단계에 이름을 붙인 것 (멀티 스테이지 빌드)

WORKDIR /app
# 컨테이너 내부 작업 디렉토리를 /app으로 설정

# Copy Gradle wrapper and build files
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./

# Gradle Wrapper와 빌드 설정 파일 복사

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# 의존성을 미리 다운로드해서 Docker 레이어 캐싱 활용
# 소스 코드가 변경되어도 의존성은 재다운로드 안됨


# 소스 코드 복사
COPY src ./src
# 애플리케이션 소스 코드 복사


# 애플리케이션 빌드
RUN ./gradlew bootJar --no-daemon
# Spring Boot 실행 가능한 JAR 파일 생성
# --no-daemon: Gradle 데몬을 사용하지 않음 (컨테이너에서는 불필요)

# ========== Runtime Stage (실행 단계) ==========
FROM eclipse-temurin:17-jre-alpine
# Java 17 JRE만 있는 경량 이미지
# JDK가 아닌 JRE를 사용해서 이미지 크기 50% 감소

WORKDIR /app

# curl 설치 (헬스체크용)
RUN apk add --no-cache curl
# Alpine Linux 패키지 매니저로 curl 설치
# --no-cache: 패키지 캐시 저장 안함 (이미지 크기 감소)

# 빌드 단계에서 생성된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar
# build 단계에서 만든 JAR 파일을 현재 단계로 복사
# 모든 빌드 도구와 소스 코드는 버려지고 JAR만 남음

# 보안: 비root 사용자 생성
RUN addgroup -g 1001 appuser && \
    adduser -u 1001 -G appuser -s /bin/sh -D appuser && \
    chown -R appuser:appuser /app
# 1. appuser 그룹 생성 (GID 1001)
# 2. appuser 사용자 생성 (UID 1001)
# 3. /app 디렉토리 소유권을 appuser에게 부여

USER appuser
# root가 아닌 appuser로 애플리케이션 실행 (보안 강화)

# Expose port
EXPOSE 8080
# 컨테이너가 8080 포트를 사용한다고 문서화
# 실제로 포트를 여는 건 docker-compose의 ports 설정

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

  # interval: 30초마다 체크
  # timeout: 3초 내에 응답 없으면 실패
  # start-period: 시작 후 40초는 실패해도 괜찮음
  # retries: 3번 연속 실패하면 unhealthy 상태

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]