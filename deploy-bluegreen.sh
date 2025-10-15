#!/bin/bash

set -e

echo "🚀 Blue-Green 배포 시작..."

# 환경 변수 로드
if [ ! -f .env.prod ]; then
    echo "❌ .env.prod 파일이 없습니다!"
    exit 1
fi

source .env.prod

# ECR 정보 설정
ECR_REGISTRY=$(aws ecr describe-repositories --repository-names aod-app --region ap-northeast-2 --query 'repositories[0].repositoryUri' --output text | cut -d'/' -f1)
ECR_REPOSITORY="aod-app"
IMAGE_TAG=${IMAGE_TAG:-latest}

export ECR_REGISTRY
export ECR_REPOSITORY
export IMAGE_TAG

echo "📦 이미지: $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG"

# ECR 로그인
echo "🔐 ECR 로그인..."
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin $ECR_REGISTRY

# 이미지 Pull
echo "⬇️  이미지 다운로드..."
docker pull $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

# 현재 실행 중인 앱 확인
echo "🔍 현재 실행 중인 환경 확인..."
if docker ps | grep -q "aod-app-blue"; then
    CURRENT_COLOR="blue"
    NEW_COLOR="green"
    CURRENT_PORT=8080
    NEW_PORT=8081
else
    CURRENT_COLOR="green"
    NEW_COLOR="blue"
    CURRENT_PORT=8081
    NEW_PORT=8080
fi

echo "현재: $CURRENT_COLOR (포트 $CURRENT_PORT)"
echo "배포: $NEW_COLOR (포트 $NEW_PORT)"

# monitoring과 DB 서비스가 없으면 시작
echo "🗄️  인프라 서비스 확인 및 시작..."
docker-compose -f docker-compose.bluegreen.yml up -d postgres prometheus grafana alertmanager

# 새로운 앱 컨테이너 시작
echo "🚀 $NEW_COLOR 환경 시작 중..."
docker-compose -f docker-compose.bluegreen.yml up -d app-$NEW_COLOR

# 헬스체크
echo "🏥 헬스체크 중..."
MAX_RETRIES=30
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if docker exec aod-app-$NEW_COLOR curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✅ $NEW_COLOR 환경 정상 작동"

        # Nginx 설정 업데이트 (있는 경우)
        if [ -f /etc/nginx/sites-available/default ]; then
            echo "🔄 Nginx 프록시 전환..."
            sudo sed -i "s/proxy_pass http:\/\/localhost:[0-9]*;/proxy_pass http:\/\/localhost:$NEW_PORT;/" /etc/nginx/sites-available/default
            sudo nginx -t && sudo systemctl reload nginx
            echo "✅ Nginx 전환 완료"
        fi

        # 30초 대기 후 이전 버전 정리
        echo "⏳ 30초 대기 중..."
        sleep 30

        echo "🛑 $CURRENT_COLOR 환경 정리..."
        docker-compose -f docker-compose.bluegreen.yml stop app-$CURRENT_COLOR
        docker-compose -f docker-compose.bluegreen.yml rm -f app-$CURRENT_COLOR

        echo ""
        echo "✅ 배포 성공!"
        echo "🌐 활성 환경: $NEW_COLOR (포트 $NEW_PORT)"
        echo ""
        docker-compose -f docker-compose.bluegreen.yml ps
        exit 0
    fi

    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "대기 중... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 10
done

# 헬스체크 실패 시 롤백
echo "❌ 헬스체크 실패 - 롤백"
docker-compose -f docker-compose.bluegreen.yml logs app-$NEW_COLOR
docker-compose -f docker-compose.bluegreen.yml stop app-$NEW_COLOR
docker-compose -f docker-compose.bluegreen.yml rm -f app-$NEW_COLOR
echo "✅ 롤백 완료 - $CURRENT_COLOR 환경 유지"
exit 1