# Copilot Instructions for AOD Crawler Backend

## Project Overview
- **Purpose:** Content aggregation, normalization, and recommendation system with monitoring (Prometheus, Grafana).
- **Stack:** Java 17+, Spring Boot 3, JPA, Security, Thymeleaf, WebFlux (for OpenAI API), PostgreSQL, Selenium, Docker Compose.
- **Entry Point:** `AodApplication.java` (enables async & scheduling).

## Architecture & Key Patterns
- **Domain Structure:**
  - Main logic in `src/main/java/com/example/AOD/` (subfolders: `service/`, `rules/`, `domain/`, etc.).
  - Data transformation: see `TransformEngine.java` for mapping/normalization logic (uses rules from `rules/`).
  - Rules for content mapping in `resources/rules/` (YAML files, per platform/domain).
- **Service Boundaries:**
  - `service/` contains core upsert, rule loading, and transformation logic.
  - `repo/` for persistence.
  - `security/` for authentication/authorization.
- **Monitoring:**
  - Prometheus & Grafana integrated via Spring Actuator & Micrometer.
  - Monitoring config in `monitoring/` and `docker-compose.yml`.

## Developer Workflows
- **Build:**
  - `./gradlew build` (or `gradlew.bat build` on Windows)
- **Run (Local):**
  - `./deploy-local.sh` (Linux/macOS) or `deploy-local.bat` (Windows)
  - App: http://localhost:8080
  - Prometheus: http://localhost:9090
  - Grafana: http://localhost:3000 (admin/admin)
- **Test:**
  - `./gradlew test` (JUnit, Testcontainers for DB)
- **Docker Compose:**
  - `docker-compose up` (uses env vars for DB, Selenium, etc.)

## Conventions & Patterns
- **Rule-Driven Mapping:**
  - Data normalization/mapping is rule-based (see `TransformEngine`, YAML rules).
  - Extend by adding new YAML files in `resources/rules/` and updating mapping logic if needed.
- **Profiles:**
  - Use `SPRING_PROFILES_ACTIVE` env var (`local`, `prod`, etc.).
  - Config files: `application-*.properties` in `resources/`.
- **Sensitive Data:**
  - Secrets (DB, API keys) are injected via environment variables (see `docker-compose.yml`).
- **Scheduling/Async:**
  - Background jobs enabled via `@EnableScheduling`/`@EnableAsync` in main app.

## Integration Points
- **External APIs:**
  - OpenAI, TMDB, Naver, Netflix, Selenium (see env vars in compose file).
- **Monitoring:**
  - Prometheus scrapes `/actuator/prometheus`.
  - Grafana dashboards provisioned in `monitoring/grafana/provisioning/`.

## Examples
- **Add new platform mapping:**
  1. Add YAML rule in `resources/rules/<platform>/`.
  2. Update `MappingRule`/`TransformEngine` if new logic needed.
- **Run with custom DB:**
  - Set `POSTGRES_*` env vars in compose or override at runtime.

---
For more, see `README.md`, `docker-compose.yml`, and key classes in `src/main/java/com/example/AOD/`.
