@echo off
chcp 65001 > nul
echo 🖥️ 로컬 환경 배포 시작...

echo 🔨 Gradle 빌드 중...
call gradlew.bat clean bootJar

if errorlevel 1 (
    echo ❌ Gradle 빌드 실패
    pause
    exit
)

echo 🐳 Docker 이미지 빌드 중...
docker-compose build

echo 🛑 기존 컨테이너 중지...
docker-compose -f docker-compose.yml -f docker-compose.local.yml down

echo ✅ 로컬 환경 시작...
docker-compose -f docker-compose.yml -f docker-compose.local.yml up -d

echo 🏥 헬스체크 중...
timeout /t 20 /nobreak >nul

echo 애플리케이션 확인...
curl -f http://localhost:8080/actuator/health >nul 2>&1 && echo ✅ 애플리케이션 OK || echo ❌ 애플리케이션 시작 실패

echo Prometheus 확인...
curl -f http://localhost:9090/-/healthy >nul 2>&1 && echo ✅ Prometheus OK || echo ❌ Prometheus 시작 실패

echo Grafana 확인...
curl -f http://localhost:3000/api/health >nul 2>&1 && echo ✅ Grafana OK || echo ❌ Grafana 시작 실패

echo.
echo 📋 컨테이너 상태:
docker-compose -f docker-compose.yml -f docker-compose.local.yml ps

echo.
echo ✅ 로컬 배포 완료!
echo.
echo 🌐 접속 정보 (로컬):
echo   - 애플리케이션: http://localhost:8080
echo   - Actuator: http://localhost:8080/actuator
echo   - Prometheus: http://localhost:9090
echo   - Grafana: http://localhost:3000 (admin/admin)
echo   - 디버그 포트: 5005
echo.
echo 📊 Grafana 대시보드 Import ID: 4701, 11378, 12900
echo.
echo 📜 로그: docker-compose -f docker-compose.yml -f docker-compose.local.yml logs -f
echo 🛑 중지: docker-compose -f docker-compose.yml -f docker-compose.local.yml down
echo.
pause