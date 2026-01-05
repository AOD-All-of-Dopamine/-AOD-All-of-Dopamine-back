# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy root Gradle wrapper and build files
COPY gradle gradle
COPY gradlew .
COPY gradlew.bat .
COPY settings.gradle .
COPY build.gradle .

# Copy shared module
COPY -AOD-All-of-Dopamine-shared -AOD-All-of-Dopamine-shared

# Copy Crawler module
COPY -AOD-All-of-Dopamine-crawler -AOD-All-of-Dopamine-crawler

# Download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Build only Crawler module
RUN ./gradlew :-AOD-All-of-Dopamine-crawler:bootJar -x test --no-daemon

# Runtime stage - Debian based for Selenium/Chrome support
FROM eclipse-temurin:17-jre
WORKDIR /app

# Install Chrome and dependencies for Selenium
RUN apt-get update && apt-get install -y \
    wget \
    gnupg \
    curl \
    unzip \
    # Chrome dependencies
    fonts-liberation \
    libasound2t64 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libatspi2.0-0 \
    libcups2t64 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libgtk-3-0t64 \
    libnspr4 \
    libnss3 \
    libwayland-client0 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxkbcommon0 \
    libxrandr2 \
    xdg-utils \
    # Chrome installation
    && wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y google-chrome-stable \
    # Cleanup
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Verify Chrome installation
RUN google-chrome --version

# Copy jar from build stage
COPY --from=build /app/-AOD-All-of-Dopamine-crawler/build/libs/*.jar app.jar

# Create non-root user
RUN groupadd -g 1001 appuser && \
    useradd -u 1001 -g appuser -s /bin/bash -d /home/appuser -m appuser && \
    chown -R appuser:appuser /app

USER appuser

# Expose port for admin/health checks
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", \
    "-Xms512m", \
    "-Xmx2048m", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/heapdump.hprof", \
    "-jar", "app.jar"]
