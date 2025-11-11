# 성능 측정 API 테스트 스크립트 (PowerShell)
# 🔥 Actuator 통합 버전 - Prometheus/Grafana 자동 수집

# 서버 URL
$baseUrl = "http://localhost:8080"

Write-Host "🔬 성능 측정 테스트 시작 (Actuator 통합)" -ForegroundColor Green
Write-Host "   ✅ Prometheus/Grafana 자동 수집" -ForegroundColor Cyan
Write-Host ""

# 1. Before 테스트
Write-Host "1️⃣  최적화 전 테스트 실행 중..." -ForegroundColor Yellow
$beforeResult = Invoke-RestMethod -Uri "$baseUrl/api/performance/test/before?batchSize=100&iterations=5" -Method Post
Write-Host "✅ 완료: $($beforeResult.metrics.successItems) 건 처리" -ForegroundColor Green
Write-Host "   처리 시간: $($beforeResult.metrics.durationMs) ms" -ForegroundColor Cyan
Write-Host "   처리 속도: $([math]::Round($beforeResult.metrics.throughputPerSecond, 2)) 건/초" -ForegroundColor Cyan
Write-Host ""

# 잠깐 대기
Start-Sleep -Seconds 2

# 2. After 테스트
Write-Host "2️⃣  최적화 후 테스트 실행 중..." -ForegroundColor Yellow
$afterResult = Invoke-RestMethod -Uri "$baseUrl/api/performance/test/after?batchSize=500&iterations=5" -Method Post
Write-Host "✅ 완료: $($afterResult.metrics.successItems) 건 처리" -ForegroundColor Green
Write-Host "   처리 시간: $($afterResult.metrics.durationMs) ms" -ForegroundColor Cyan
Write-Host "   처리 속도: $([math]::Round($afterResult.metrics.throughputPerSecond, 2)) 건/초" -ForegroundColor Cyan
Write-Host ""

# 3. 비교 결과 출력
Write-Host "📊 성능 개선 결과" -ForegroundColor Magenta
Write-Host "════════════════════════════════════════" -ForegroundColor Gray

$speedImprovement = $afterResult.metrics.throughputPerSecond / $beforeResult.metrics.throughputPerSecond
$timeReduction = (1 - ($afterResult.metrics.durationMs / $beforeResult.metrics.durationMs)) * 100

Write-Host "⏱️  처리 시간:" -ForegroundColor White
Write-Host "   Before: $($beforeResult.metrics.durationMs) ms" -ForegroundColor Gray
Write-Host "   After:  $($afterResult.metrics.durationMs) ms" -ForegroundColor Gray
Write-Host "   개선:   $([math]::Round($timeReduction, 1))% 단축 ⭐" -ForegroundColor Green

Write-Host ""
Write-Host "🚀 처리 속도:" -ForegroundColor White
Write-Host "   Before: $([math]::Round($beforeResult.metrics.throughputPerSecond, 2)) 건/초" -ForegroundColor Gray
Write-Host "   After:  $([math]::Round($afterResult.metrics.throughputPerSecond, 2)) 건/초" -ForegroundColor Gray
Write-Host "   개선:   $([math]::Round($speedImprovement, 1))배 향상 ⭐⭐⭐" -ForegroundColor Green

Write-Host ""
Write-Host "════════════════════════════════════════" -ForegroundColor Gray
Write-Host ""
Write-Host "✅ 배치 처리 테스트 완료!" -ForegroundColor Green
Write-Host ""

# 4. Actuator 메트릭 확인
Write-Host "📊 Actuator 메트릭 확인 중..." -ForegroundColor Yellow
try {
    # Before 메트릭
    Write-Host ""
    Write-Host "   [BEFORE 버전 메트릭]" -ForegroundColor Cyan
    $beforeMetric = Invoke-RestMethod -Uri "$baseUrl/actuator/metrics/performance.test.duration?tag=version:BEFORE" -Method Get
    if ($beforeMetric.measurements) {
        $count = ($beforeMetric.measurements | Where-Object { $_.statistic -eq "COUNT" }).value
        $total = ($beforeMetric.measurements | Where-Object { $_.statistic -eq "TOTAL_TIME" }).value
        $max = ($beforeMetric.measurements | Where-Object { $_.statistic -eq "MAX" }).value
        if ($count -gt 0) {
            $avg = $total / $count
            Write-Host "      실행 횟수: $count" -ForegroundColor Gray
            Write-Host "      총 시간: $([math]::Round($total, 2))초" -ForegroundColor Gray
            Write-Host "      평균 시간: $([math]::Round($avg, 2))초" -ForegroundColor Gray
            Write-Host "      최대 시간: $([math]::Round($max, 2))초" -ForegroundColor Gray
        }
    }
    
    # After 메트릭
    Write-Host ""
    Write-Host "   [AFTER 버전 메트릭]" -ForegroundColor Cyan
    $afterMetric = Invoke-RestMethod -Uri "$baseUrl/actuator/metrics/performance.test.duration?tag=version:AFTER" -Method Get
    if ($afterMetric.measurements) {
        $count = ($afterMetric.measurements | Where-Object { $_.statistic -eq "COUNT" }).value
        $total = ($afterMetric.measurements | Where-Object { $_.statistic -eq "TOTAL_TIME" }).value
        $max = ($afterMetric.measurements | Where-Object { $_.statistic -eq "MAX" }).value
        if ($count -gt 0) {
            $avg = $total / $count
            Write-Host "      실행 횟수: $count" -ForegroundColor Gray
            Write-Host "      총 시간: $([math]::Round($total, 2))초" -ForegroundColor Gray
            Write-Host "      평균 시간: $([math]::Round($avg, 2))초" -ForegroundColor Gray
            Write-Host "      최대 시간: $([math]::Round($max, 2))초" -ForegroundColor Gray
        }
    }
    
    Write-Host ""
    Write-Host "   ✅ Prometheus에 메트릭 저장됨" -ForegroundColor Green
    Write-Host "      → $baseUrl/actuator/prometheus" -ForegroundColor Gray
    
} catch {
    Write-Host "   ⚠️  Actuator 메트릭 조회 실패" -ForegroundColor Yellow
}
Write-Host ""

# 5. 스레드풀 상태 조회
Write-Host "🧵 스레드풀 상태 조회 중..." -ForegroundColor Yellow
try {
    $threadPoolStatus = Invoke-RestMethod -Uri "$baseUrl/api/performance/threadpool/status" -Method Get
    if ($threadPoolStatus.available) {
        Write-Host "✅ 스레드풀 정상 동작 중" -ForegroundColor Green
        Write-Host "   활성 스레드: $($threadPoolStatus.metrics.activeThreadCount) / $($threadPoolStatus.metrics.maxPoolSize)" -ForegroundColor Cyan
        Write-Host "   큐 대기: $($threadPoolStatus.metrics.queueSize) / $($threadPoolStatus.metrics.queueCapacity)" -ForegroundColor Cyan
        Write-Host "   건강 상태: $($threadPoolStatus.healthLabel)" -ForegroundColor Cyan
    } else {
        Write-Host "⚠️  스레드풀을 사용할 수 없습니다." -ForegroundColor Yellow
    }
} catch {
    Write-Host "⚠️  스레드풀 상태 조회 실패" -ForegroundColor Yellow
}
Write-Host ""

# 5. 스레드풀 부하 테스트 (선택)
Write-Host "🧵 스레드풀 부하 테스트 실행 여부를 선택하세요:" -ForegroundColor Yellow
Write-Host "   이 테스트는 약 10초 소요됩니다." -ForegroundColor Gray
$response = Read-Host "실행하시겠습니까? (Y/N)"

if ($response -eq "Y" -or $response -eq "y") {
    Write-Host ""
    Write-Host "🔥 스레드풀 부하 테스트 시작..." -ForegroundColor Yellow
    $threadPoolTest = Invoke-RestMethod -Uri "$baseUrl/api/performance/threadpool/load-test?taskCount=50&taskDurationMs=1000" -Method Post
    Write-Host "✅ 완료!" -ForegroundColor Green
    Write-Host "   작업 수: $($threadPoolTest.taskCount)" -ForegroundColor Cyan
    Write-Host "   소요 시간: $($threadPoolTest.totalDurationMs) ms" -ForegroundColor Cyan
    Write-Host "   처리 속도: $([math]::Round($threadPoolTest.tasksPerSecond, 2)) 작업/초" -ForegroundColor Cyan
    Write-Host "   최대 활성 스레드: $($threadPoolTest.afterMetrics.poolSize)" -ForegroundColor Cyan
    Write-Host ""
} else {
    Write-Host "   건너뜀" -ForegroundColor Gray
    Write-Host ""
}

Write-Host "════════════════════════════════════════" -ForegroundColor Gray
Write-Host ""
Write-Host "✅ 모든 테스트 완료!" -ForegroundColor Green
Write-Host ""
Write-Host "📊 결과 확인 방법:" -ForegroundColor Cyan
Write-Host "   1. Actuator 메트릭: $baseUrl/actuator/metrics/performance.test.duration" -ForegroundColor Gray
Write-Host "   2. Prometheus: $baseUrl/actuator/prometheus" -ForegroundColor Gray
Write-Host "   3. Grafana: http://localhost:3000 (monitoring 폴더에서 docker-compose up)" -ForegroundColor Gray
Write-Host ""
Write-Host "🎯 포트폴리오에 이 결과를 활용하세요!" -ForegroundColor Green
