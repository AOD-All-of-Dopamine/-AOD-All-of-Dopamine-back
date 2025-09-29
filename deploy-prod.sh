#!/bin/bash

echo "🚀 서버 환경 배포 시작..."

# 환경 변수 로드
if [ ! -f .env.prod ]; then
    echo "❌ .env.prod 파일이 없습니다!"
    echo "💡 .env.prod 파일을 생성하고 보안 정보를 설정하세요."
    exit 1
fi

export $(cat .env.prod | grep -v '^#' | xargs)

echo "🔨 Gradle 빌드 중..."
./gradlew clean bootJar  # Maven 대신 Gradle

echo "🐳 Docker 이미지 빌드 중..."
docker-compose build

echo "🛑 기존 컨테이너 중지..."
docker-compose -f docker-compose.yml -f docker-compose.prod.yml down

echo "✅ 서버 환경 시작..."
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

echo "🏥 헬스체크 중..."
sleep 25

echo "애플리케이션 확인..."
curl -f http://localhost:8080/actuator/health 2>/dev/null && echo "✅ 애플리케이션 OK" || echo "❌ 애플리케이션 시작 실패"

echo "Prometheus 확인..."
curl -f http://localhost:9090/-/healthy 2>/dev/null && echo "✅ Prometheus OK" || echo "❌ Prometheus 시작 실패"

echo "Grafana 확인..."
curl -f http://localhost:3000/api/health 2>/dev/null && echo "✅ Grafana OK" || echo "❌ Grafana 시작 실패"

echo ""
echo "📋 컨테이너 상태:"
docker-compose -f docker-compose.yml -f docker-compose.prod.yml ps

echo ""
echo "✅ 서버 배포 완료!"
echo ""
PUBLIC_IP=$(curl -s ifconfig.me 2>/dev/null || echo "YOUR_SERVER_IP")
echo "🌐 접속 정보 (서버):"
echo "  - 애플리케이션: http://${PUBLIC_IP}:8080"
echo "  - Prometheus: http://${PUBLIC_IP}:9090"
echo "  - Grafana: http://${PUBLIC_IP}:3000 (admin/설정한비밀번호)"
echo ""
echo "⚠️  보안그룹에서 포트 오픈 필요: 8080, 9090, 3000"
echo ""
echo "📜 로그: docker-compose -f docker-compose.yml -f docker-compose.prod.yml logs -f"
echo "🛑 중지: docker-compose -f docker-compose.yml -f docker-compose.prod.yml down"