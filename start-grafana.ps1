# Grafana 빠른 시작 스크립트

Write-Host "🚀 Grafana 대시보드 빠른 시작" -ForegroundColor Green
Write-Host ""

# 1. Monitoring 스택 시작
Write-Host "1️⃣  Monitoring 스택 시작 중..." -ForegroundColor Yellow
cd monitoring
docker-compose -f monitoring-compose.local.yml up -d

if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ Prometheus, Grafana 시작 완료" -ForegroundColor Green
} else {
    Write-Host "   ❌ 시작 실패" -ForegroundColor Red
    exit 1
}

Write-Host ""
Start-Sleep -Seconds 5

# 2. 서비스 상태 확인
Write-Host "2️⃣  서비스 상태 확인 중..." -ForegroundColor Yellow

try {
    $prometheus = Invoke-WebRequest -Uri "http://localhost:9090/-/healthy" -UseBasicParsing -TimeoutSec 5
    Write-Host "   ✅ Prometheus: http://localhost:9090" -ForegroundColor Green
} catch {
    Write-Host "   ⚠️  Prometheus 응답 없음" -ForegroundColor Yellow
}

try {
    $grafana = Invoke-WebRequest -Uri "http://localhost:3000/api/health" -UseBasicParsing -TimeoutSec 5
    Write-Host "   ✅ Grafana: http://localhost:3000" -ForegroundColor Green
} catch {
    Write-Host "   ⚠️  Grafana 응답 없음 (시작 중일 수 있음)" -ForegroundColor Yellow
}

Write-Host ""

# 3. 안내 메시지
Write-Host "════════════════════════════════════════" -ForegroundColor Gray
Write-Host "✅ Grafana 준비 완료!" -ForegroundColor Green
Write-Host ""
Write-Host "📊 접속 정보:" -ForegroundColor Cyan
Write-Host "   URL: http://localhost:3000" -ForegroundColor Gray
Write-Host "   ID:  admin" -ForegroundColor Gray
Write-Host "   PW:  admin" -ForegroundColor Gray
Write-Host ""
Write-Host "📂 대시보드 Import 방법:" -ForegroundColor Cyan
Write-Host ""
Write-Host "   [방법 1] 커뮤니티 대시보드 (추천)" -ForegroundColor White
Write-Host "   1. Grafana 접속" -ForegroundColor Gray
Write-Host "   2. 좌측 메뉴 → Dashboards → Import" -ForegroundColor Gray
Write-Host "   3. ID 입력: 11378 (Spring Boot Monitor)" -ForegroundColor Gray
Write-Host "   4. Load → Prometheus 선택 → Import" -ForegroundColor Gray
Write-Host ""
Write-Host "   [방법 2] 커스텀 대시보드 (프로젝트 전용)" -ForegroundColor White
Write-Host "   1. Grafana 접속" -ForegroundColor Gray
Write-Host "   2. 좌측 메뉴 → Dashboards → Import" -ForegroundColor Gray
Write-Host "   3. Upload JSON file 클릭" -ForegroundColor Gray
Write-Host "   4. 파일 선택: monitoring/grafana/dashboards/aod-performance-dashboard.json" -ForegroundColor Gray
Write-Host "   5. Prometheus 선택 → Import" -ForegroundColor Gray
Write-Host ""
Write-Host "🎯 다음 단계:" -ForegroundColor Cyan
Write-Host "   1. 백엔드 서버 시작: .\gradlew.bat bootRun" -ForegroundColor Gray
Write-Host "   2. 성능 테스트 실행: .\test-performance.ps1" -ForegroundColor Gray
Write-Host "   3. Grafana에서 실시간 그래프 확인!" -ForegroundColor Gray
Write-Host ""
Write-Host "════════════════════════════════════════" -ForegroundColor Gray
Write-Host ""
Write-Host "💡 팁: 브라우저에서 Grafana를 열려면:" -ForegroundColor Yellow
Write-Host "   Start-Process http://localhost:3000" -ForegroundColor Gray
Write-Host ""

# 선택: 자동으로 브라우저 열기
$openBrowser = Read-Host "브라우저에서 Grafana를 여시겠습니까? (Y/N)"
if ($openBrowser -eq "Y" -or $openBrowser -eq "y") {
    Start-Process "http://localhost:3000"
    Write-Host "✅ 브라우저에서 Grafana 열림" -ForegroundColor Green
}
