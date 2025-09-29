# AOD Crawler - 모니터링 시스템

콘텐츠 수집 및 추천 시스템 with Prometheus & Grafana 모니터링

## 🚀 빠른 시작

### 로컬 환경
```bash
# 1. 의존성 설치 확인
docker --version
docker-compose --version

# 2. 로컬 배포
./deploy-local.sh

# Windows
deploy-local.bat

# 3. 접속
# - 애플리케이션: http://localhost:8080
# - Prometheus: http://localhost:9090
# - Grafana: http://localhost:3000 (admin/admin)