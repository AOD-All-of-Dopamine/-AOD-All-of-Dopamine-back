# 🎨 Admin UI 아키텍처 가이드

## 📊 권장 아키텍처: 프론트엔드 통합 방식

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (React)                         │
│                AOD-All-of-Dopamine-front                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  사용자 페이지 (기존)                                         │
│  ├── HomePage                                               │
│  ├── ExplorePage                                            │
│  ├── ProfilePage                                            │
│  └── ...                                                    │
│                                                             │
│  🆕 관리자 페이지 (추가)                                      │
│  ├── /admin/crawling         - 크롤링 관리                   │
│  ├── /admin/batch            - 배치 처리                     │
│  ├── /admin/monitoring       - 시스템 모니터링               │
│  └── /admin/statistics       - 통계 대시보드                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                          ↓ HTTP/REST API
┌─────────────────────────────────────────────────────────────┐
│                   Backend (Spring Boot)                     │
│               AOD-All-of-Dopamine-back                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  REST API 엔드포인트                                          │
│  ├── /api/crawl/**          - 크롤링 트리거                  │
│  ├── /api/batch/**          - 배치 처리                      │
│  ├── /api/health            - 헬스체크                       │
│  └── /actuator/**           - Prometheus 메트릭             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 프론트엔드 구현 예시

### 1️⃣ 크롤링 관리 페이지

```typescript
// src/pages/admin/CrawlingDashboard.tsx
import React, { useState } from 'react';
import { crawlApi } from '../../api/admin';

export const CrawlingDashboard: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any>(null);

  const handleCrawlNaverWebtoon = async () => {
    setLoading(true);
    try {
      const response = await crawlApi.naverWebtoon.allWeekdays();
      setResult(response);
      alert('크롤링 작업이 시작되었습니다!');
    } catch (error) {
      alert('오류 발생: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleCrawlSteam = async () => {
    setLoading(true);
    try {
      const response = await crawlApi.steam.collectAll();
      setResult(response);
      alert('Steam 크롤링이 시작되었습니다!');
    } catch (error) {
      alert('오류 발생: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="admin-crawling">
      <h1>크롤링 관리</h1>
      
      <div className="crawl-controls">
        <section>
          <h2>웹툰</h2>
          <button 
            onClick={handleCrawlNaverWebtoon}
            disabled={loading}
          >
            네이버 웹툰 크롤링 시작
          </button>
        </section>

        <section>
          <h2>게임</h2>
          <button 
            onClick={handleCrawlSteam}
            disabled={loading}
          >
            Steam 크롤링 시작
          </button>
        </section>
      </div>

      {result && (
        <div className="result">
          <h3>결과</h3>
          <pre>{JSON.stringify(result, null, 2)}</pre>
        </div>
      )}
    </div>
  );
};
```

---

### 2️⃣ 배치 처리 페이지

```typescript
// src/pages/admin/BatchProcessing.tsx
import React, { useState, useEffect } from 'react';
import { batchApi } from '../../api/admin';

export const BatchProcessing: React.FC = () => {
  const [batchSize, setBatchSize] = useState(500);
  const [numWorkers, setNumWorkers] = useState(4);
  const [totalItems, setTotalItems] = useState(10000);
  const [processing, setProcessing] = useState(false);
  const [stats, setStats] = useState<any>(null);
  const [pendingCount, setPendingCount] = useState(0);

  useEffect(() => {
    loadPendingCount();
  }, []);

  const loadPendingCount = async () => {
    try {
      const response = await batchApi.getPendingCount();
      setPendingCount(response.pendingRaw);
    } catch (error) {
      console.error('Failed to load pending count', error);
    }
  };

  const handleProcessBatch = async () => {
    setProcessing(true);
    try {
      const response = await batchApi.processOptimized({ batchSize });
      setStats(response);
      alert(`처리 완료: ${response.processed}건 (${response.itemsPerSecond}건/초)`);
      loadPendingCount();
    } catch (error) {
      alert('오류 발생: ' + error.message);
    } finally {
      setProcessing(false);
    }
  };

  const handleProcessParallel = async () => {
    setProcessing(true);
    try {
      const response = await batchApi.processParallel({
        totalItems,
        batchSize,
        numWorkers
      });
      setStats(response);
      alert(`병렬 처리 완료: ${response.processed}건 (${response.itemsPerSecond}건/초)`);
      loadPendingCount();
    } catch (error) {
      alert('오류 발생: ' + error.message);
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div className="admin-batch">
      <h1>배치 처리</h1>

      <div className="stats-overview">
        <div className="stat-card">
          <h3>미처리 항목</h3>
          <p className="stat-value">{pendingCount.toLocaleString()}건</p>
        </div>
      </div>

      <div className="batch-controls">
        <section>
          <h2>단일 배치 처리</h2>
          <label>
            배치 크기:
            <input 
              type="number" 
              value={batchSize}
              onChange={(e) => setBatchSize(Number(e.target.value))}
              min={100}
              max={2000}
            />
          </label>
          <button 
            onClick={handleProcessBatch}
            disabled={processing}
          >
            {processing ? '처리 중...' : '배치 처리 시작'}
          </button>
        </section>

        <section>
          <h2>병렬 배치 처리 (고속)</h2>
          <label>
            총 처리 항목:
            <input 
              type="number" 
              value={totalItems}
              onChange={(e) => setTotalItems(Number(e.target.value))}
              min={1000}
              max={1000000}
            />
          </label>
          <label>
            배치 크기:
            <input 
              type="number" 
              value={batchSize}
              onChange={(e) => setBatchSize(Number(e.target.value))}
              min={100}
              max={2000}
            />
          </label>
          <label>
            워커 수:
            <input 
              type="number" 
              value={numWorkers}
              onChange={(e) => setNumWorkers(Number(e.target.value))}
              min={1}
              max={12}
            />
          </label>
          <button 
            onClick={handleProcessParallel}
            disabled={processing}
          >
            {processing ? '처리 중...' : '병렬 처리 시작'}
          </button>
        </section>
      </div>

      {stats && (
        <div className="stats-result">
          <h3>처리 결과</h3>
          <table>
            <tbody>
              <tr>
                <td>처리 완료</td>
                <td>{stats.processed}건</td>
              </tr>
              <tr>
                <td>남은 항목</td>
                <td>{stats.pendingRaw}건</td>
              </tr>
              <tr>
                <td>소요 시간</td>
                <td>{(stats.elapsedMs / 1000).toFixed(2)}초</td>
              </tr>
              <tr>
                <td>처리 속도</td>
                <td>{stats.itemsPerSecond}건/초</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
```

---

### 3️⃣ API 클라이언트

```typescript
// src/api/admin.ts
import axios from 'axios';

const API_BASE = 'http://localhost:8080/api';

export const crawlApi = {
  naverWebtoon: {
    allWeekdays: () => 
      axios.post(`${API_BASE}/crawl/naver-webtoon/all-weekdays`),
    weekday: (weekday: string) => 
      axios.post(`${API_BASE}/crawl/naver-webtoon/weekday`, { weekday }),
  },
  steam: {
    collectAll: () => 
      axios.post(`${API_BASE}/crawl/steam/all`),
  },
  naverSeries: {
    crawl: (params: any) => 
      axios.post(`${API_BASE}/crawl/naver-series`, params),
  },
};

export const batchApi = {
  processOptimized: (params: { batchSize: number }) =>
    axios.post(`${API_BASE}/batch/process-optimized`, params)
      .then(res => res.data),
  
  processParallel: (params: { 
    totalItems: number; 
    batchSize: number; 
    numWorkers: number;
  }) =>
    axios.post(`${API_BASE}/batch/process-parallel`, params)
      .then(res => res.data),
  
  getPendingCount: () =>
    axios.get(`${API_BASE}/batch/status`)
      .then(res => res.data),
};
```

---

### 4️⃣ 라우팅 설정

```typescript
// src/App.tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { CrawlingDashboard } from './pages/admin/CrawlingDashboard';
import { BatchProcessing } from './pages/admin/BatchProcessing';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 기존 사용자 페이지 */}
        <Route path="/" element={<HomePage />} />
        <Route path="/explore" element={<ExplorePage />} />
        
        {/* 관리자 페이지 */}
        <Route path="/admin/crawling" element={<CrawlingDashboard />} />
        <Route path="/admin/batch" element={<BatchProcessing />} />
      </Routes>
    </BrowserRouter>
  );
}
```

---

## 🔒 보안 고려사항

### 1. 관리자 인증 추가

```typescript
// src/contexts/AdminAuthContext.tsx
export const AdminAuthProvider: React.FC = ({ children }) => {
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    // 관리자 권한 확인
    const checkAdmin = async () => {
      const user = await getCurrentUser();
      setIsAdmin(user.role === 'ADMIN');
    };
    checkAdmin();
  }, []);

  return (
    <AdminAuthContext.Provider value={{ isAdmin }}>
      {children}
    </AdminAuthContext.Provider>
  );
};
```

### 2. Protected Route

```typescript
// src/components/AdminRoute.tsx
export const AdminRoute: React.FC<{ element: React.ReactElement }> = 
  ({ element }) => {
  const { isAdmin } = useAdminAuth();
  
  if (!isAdmin) {
    return <Navigate to="/login" replace />;
  }
  
  return element;
};

// 사용
<Route 
  path="/admin/*" 
  element={<AdminRoute element={<AdminLayout />} />} 
/>
```

---

## 🎨 UI 라이브러리 추천

### 대시보드용 라이브러리
```bash
npm install @tremor/react  # 차트/대시보드 컴포넌트
npm install recharts       # 차트 라이브러리
npm install react-query    # 데이터 페칭/캐싱
```

---

## 📈 장점 요약

### ✅ 이 방식을 선택해야 하는 이유

1. **단일 프론트엔드 프로젝트**
   - 사용자 페이지 + 관리자 페이지 통합
   - 코드 공유 (컴포넌트, 유틸리티)
   - 통일된 디자인 시스템

2. **완전한 분리**
   - 백엔드는 API만 제공
   - 프론트는 UI에만 집중
   - 각자 독립 배포 가능

3. **확장성**
   - 나중에 모바일 앱도 같은 API 사용
   - 다른 서비스와 API 공유 가능
   - 마이크로서비스 전환 용이

4. **개발 편의성**
   - 프론트/백 팀 독립 작업
   - React 생태계 활용
   - 핫 리로딩, 개발 도구 사용

---

## ❌ Thymeleaf를 피해야 하는 이유

1. **이미 React 프로젝트가 있음**
   - 중복 투자 방지
   - 일관성 유지

2. **제한적인 UI**
   - 모던한 대시보드 구현 어려움
   - 실시간 업데이트 복잡

3. **혼재된 아키텍처**
   - API + View 혼용
   - 유지보수 복잡도 증가

4. **프론트엔드 기술 제약**
   - React 생태계 활용 불가
   - 컴포넌트 재사용 어려움

---

## 🚀 다음 단계

1. ✅ 백엔드 API는 이미 완성 (현재 상태 유지)
2. 🔲 프론트엔드에 `/admin` 라우트 추가
3. 🔲 관리자 인증/권한 체크 구현
4. 🔲 Prometheus 메트릭 시각화 추가
5. 🔲 실시간 로그 스트리밍 (선택사항)

---

**결론**: 현재 구조(REST API)를 유지하고, React 프론트엔드에 관리자 페이지를 추가하는 것이 **최선의 선택**입니다. 🎯
