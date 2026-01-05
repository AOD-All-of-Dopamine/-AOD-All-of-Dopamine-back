# 📚 AOD 프로젝트 문서

이 폴더에는 AOD(All of Dopamine) 프로젝트의 모든 기술 문서가 카테고리별로 정리되어 있습니다.

## 📂 문서 구조

### 🎯 [performance/](./performance/) - 성능 측정
프로젝트의 성능 측정 및 비교 관련 문서

- **[performance-measurement-guide.md](./performance/performance-measurement-guide.md)** - 성능 측정 전체 가이드
- **[QUICK_START_PERFORMANCE_TEST.md](./performance/QUICK_START_PERFORMANCE_TEST.md)** - 빠른 시작 가이드
- **[threadpool-performance-guide.md](./performance/threadpool-performance-guide.md)** - 스레드풀 성능 측정
- **[INTEGRATION_BEFORE_AFTER_COMPARISON.md](./performance/INTEGRATION_BEFORE_AFTER_COMPARISON.md)** - Before/After 비교 방법

### ⚡ [optimization/](./optimization/) - 최적화
배치 처리, 크롤링, 리소스 최적화 관련 문서

- **[batch-performance-optimization.md](./optimization/batch-performance-optimization.md)** - 배치 성능 최적화
- **[batch-performance-configuration.md](./optimization/batch-performance-configuration.md)** - 배치 설정 가이드
- **[crawling-improvements.md](./optimization/crawling-improvements.md)** - 크롤링 개선 사항
- **[duplicate-detection.md](./optimization/duplicate-detection.md)** - 중복 감지 로직
- **[resource-limits.md](./optimization/resource-limits.md)** - 리소스 제한 설정
- **[thread-resource-issues.md](./optimization/thread-resource-issues.md)** - 스레드 리소스 이슈

### 📊 [monitoring/](./monitoring/) - 모니터링
Actuator, Prometheus, Grafana를 이용한 모니터링

- **[ACTUATOR_INTEGRATION_QUICK_START.md](./monitoring/ACTUATOR_INTEGRATION_QUICK_START.md)** - Actuator 빠른 시작
- **[ACTUATOR_INTEGRATION_COMPLETE.md](./monitoring/ACTUATOR_INTEGRATION_COMPLETE.md)** - Actuator 통합 완료
- **[CUSTOM_VS_ACTUATOR_INTEGRATION.md](./monitoring/CUSTOM_VS_ACTUATOR_INTEGRATION.md)** - Custom vs Actuator 비교
- **[GRAFANA_DASHBOARD_GUIDE.md](./monitoring/GRAFANA_DASHBOARD_GUIDE.md)** - Grafana 대시보드 가이드

### 🏗️ [architecture/](./architecture/) - 아키텍처
시스템 설계 및 아키텍처 관련 문서

- **[system-architecture.md](./architecture/system-architecture.md)** - 전체 시스템 아키텍처
- **[admin-ui-architecture.md](./architecture/admin-ui-architecture.md)** - 관리자 UI 아키텍처
- **[admin-frontend-separation-strategy.md](./architecture/admin-frontend-separation-strategy.md)** - 프론트엔드 분리 전략

---

## 🚀 빠른 시작

### 성능 측정하기
```bash
# 1. 성능 테스트 실행
.\test-performance.ps1

# 2. Grafana 대시보드 시작
.\start-grafana.ps1
```

자세한 내용은 [performance/QUICK_START_PERFORMANCE_TEST.md](./performance/QUICK_START_PERFORMANCE_TEST.md)를 참고하세요.

### 모니터링 시작하기
```bash
# Grafana 대시보드 시작
.\start-grafana.ps1
```

자세한 내용은 [monitoring/GRAFANA_DASHBOARD_GUIDE.md](./monitoring/GRAFANA_DASHBOARD_GUIDE.md)를 참고하세요.

---

## 📝 문서 작성 가이드

새로운 문서를 작성할 때는 적절한 카테고리 폴더에 배치해주세요:
- 성능 측정 관련 → `performance/`
- 최적화 작업 → `optimization/`
- 모니터링/메트릭 → `monitoring/`
- 시스템 설계 → `architecture/`

---

## 💼 포트폴리오 활용

이 문서들은 포트폴리오 작성에 유용합니다:
1. **문제 인식**: optimization/ 폴더의 이슈 문서
2. **해결 과정**: optimization/ 폴더의 개선 문서
3. **성과 측정**: performance/ 폴더의 측정 결과
4. **모니터링**: monitoring/ 폴더의 대시보드 스크린샷

---

**Last Updated**: 2025-11-11
