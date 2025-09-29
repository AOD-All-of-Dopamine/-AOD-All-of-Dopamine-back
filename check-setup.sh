#!/bin/bash

echo "🔍 AOD Crawler 모니터링 설정 체크..."
echo ""

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✅${NC} $1"
        return 0
    else
        echo -e "${RED}❌${NC} $1 (없음)"
        return 1
    fi
}

check_dir() {
    if [ -d "$1" ]; then
        echo -e "${GREEN}✅${NC} $1/"
        return 0
    else
        echo -e "${RED}❌${NC} $1/ (없음)"
        return 1
    fi
}

echo "📁 필수 디렉토리:"
check_dir "monitoring"
check_dir "monitoring/grafana"
check_dir "monitoring/grafana/provisioning"
check_dir "monitoring/grafana/provisioning/datasources"
check_dir "monitoring/grafana/provisioning/dashboards"
check_dir "src/main/java/com/example/AOD/monitoring"

echo ""
echo "📄 필수 파일:"
check_file "build.gradle"  # Maven 대신 Gradle
check_file "Dockerfile"
check_file "docker-compose.yml"
check_file "docker-compose.local.yml"
check_file "docker-compose.prod.yml"
check_file ".env.local"
check_file ".env.prod"
check_file "deploy-local.sh"
check_file "deploy-prod.sh"

echo ""
echo "⚙️  설정 파일:"
check_file "src/main/resources/application.properties"
check_file "src/main/resources/application-local.properties"
check_file "src/main/resources/application-prod.properties"
check_file "monitoring/prometheus.yml"
check_file "monitoring/alerts.yml"
check_file "monitoring/alertmanager.yml"
check_file "monitoring/grafana/provisioning/datasources/prometheus.yml"
check_file "monitoring/grafana/provisioning/dashboards/dashboard.yml"

echo ""
echo "💻 Java 파일:"
check_file "src/main/java/com/example/AOD/monitoring/CustomMetrics.java"
check_file "src/main/java/com/example/AOD/Webtoon/NaverWebtoon/NaverWebtoonService.java"

echo ""
echo "🐳 Docker 확인:"
if command -v docker &> /dev/null; then
    echo -e "${GREEN}✅${NC} Docker 설치됨: $(docker --version)"
else
    echo -e "${RED}❌${NC} Docker 설치 안됨"
fi

if command -v docker-compose &> /dev/null; then
    echo -e "${GREEN}✅${NC} Docker Compose 설치됨: $(docker-compose --version)"
else
    echo -e "${RED}❌${NC} Docker Compose 설치 안됨"
fi

echo ""
echo "☕ Java 확인:"
if command -v java &> /dev/null; then
    echo -e "${GREEN}✅${NC} Java 설치됨: $(java -version 2>&1 | head -n 1)"
else
    echo -e "${RED}❌${NC} Java 설치 안됨"
fi

echo ""
echo "🔨 Gradle 확인:"  # Maven 대신 Gradle
if [ -f "gradlew" ]; then
    echo -e "${GREEN}✅${NC} Gradle Wrapper 있음"
else
    echo -e "${RED}❌${NC} Gradle Wrapper 없음"
fi

echo ""
echo "📊 build.gradle 의존성 확인:"  # pom.xml 대신
if grep -q "spring-boot-starter-actuator" build.gradle 2>/dev/null; then
    echo -e "${GREEN}✅${NC} Actuator 의존성 있음"
else
    echo -e "${RED}❌${NC} Actuator 의존성 없음"
fi

if grep -q "micrometer-registry-prometheus" build.gradle 2>/dev/null; then
    echo -e "${GREEN}✅${NC} Prometheus 의존성 있음"
else
    echo -e "${RED}❌${NC} Prometheus 의존성 없음"
fi

echo ""
echo "🔑 환경 변수 파일 확인:"
if [ -f ".env.prod" ]; then
    if grep -q "your_secure_password_here" .env.prod 2>/dev/null; then
        echo -e "${YELLOW}⚠️${NC}  .env.prod에 기본 비밀번호가 있습니다. 변경하세요!"
    else
        echo -e "${GREEN}✅${NC} .env.prod 비밀번호 설정됨"
    fi
fi

echo ""
echo "✅ 체크 완료!"
echo ""
echo "다음 단계:"
echo "1. 누락된 파일이 있다면 생성하세요"
echo "2. .env.prod의 비밀번호를 변경하세요"
echo "3. 로컬 테스트: ./deploy-local.sh"
echo "4. 서버 배포: ./deploy-prod.sh"