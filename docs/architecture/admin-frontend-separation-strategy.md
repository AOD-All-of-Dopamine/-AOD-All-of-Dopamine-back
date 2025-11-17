# 🔐 관리자 페이지 분리 전략 가이드

## 🤔 핵심 질문: 유저용 프론트와 관리자 페이지를 같은 프로젝트에?

### **보안 및 성능 고려사항**

| 측면 | 단일 프론트엔드 | 분리된 Admin 프론트 |
|------|----------------|-------------------|
| **보안** | 관리자 코드 노출 가능 | 완전 격리 |
| **번들 크기** | 증가 (사용자도 다운로드) | 최적화 가능 |
| **배포** | 한 번에 배포 | 독립 배포 |
| **접근 제어** | 라우팅 기반 | 서버/도메인 기반 |
| **개발 복잡도** | 낮음 | 중간 |

---

## 📊 3가지 접근 방식 비교

### **방식 1: 단일 프론트엔드 (조건부 렌더링)** ⭐⭐⭐

```
AOD-All-of-Dopamine-front/
├── src/
│   ├── pages/
│   │   ├── user/          # 일반 사용자 페이지
│   │   └── admin/         # 관리자 페이지 (조건부)
│   └── App.tsx            # 권한 기반 라우팅
```

#### 장점
- ✅ 단일 프로젝트 관리
- ✅ 코드 공유 (공통 컴포넌트, API 클라이언트)
- ✅ 배포 간단

#### 단점
- ⚠️ **번들 크기 증가** - 일반 사용자도 관리자 코드 다운로드
- ⚠️ **보안 취약** - 관리자 코드가 클라이언트에 노출
- ⚠️ **성능 영향** - 불필요한 코드 로딩

#### 보안 개선 방법
```typescript
// React.lazy로 코드 스플리팅
const AdminDashboard = React.lazy(() => import('./pages/admin/Dashboard'));

// 권한 체크 + 동적 로딩
<Route 
  path="/admin/*" 
  element={
    <AdminRoute>
      <Suspense fallback={<Loading />}>
        <AdminDashboard />
      </Suspense>
    </AdminRoute>
  }
/>
```

**효과**: 관리자 코드는 관리자 접근 시에만 다운로드 ✅

---

### **방식 2: 별도 Admin 프론트엔드 프로젝트** ⭐⭐⭐⭐⭐ (추천)

```
프로젝트 구조:
├── AOD-All-of-Dopamine-front/        # 사용자용 (포트 3000)
│   └── 일반 사용자 페이지
│
└── AOD-All-of-Dopamine-admin/        # 관리자용 (포트 3001)
    └── 관리자 대시보드
```

#### 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│               사용자 프론트 (포트 3000)                   │
│           https://aod.example.com                        │
├─────────────────────────────────────────────────────────┤
│ HomePage, ExplorePage, ProfilePage, ...                │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                백엔드 API (포트 8080)                     │
│              https://api.aod.example.com                 │
├─────────────────────────────────────────────────────────┤
│ /api/contents/** - 사용자 API                           │
│ /api/admin/**    - 관리자 API (토큰 검증)               │
└─────────────────────────────────────────────────────────┘
                          ↑
┌─────────────────────────────────────────────────────────┐
│              관리자 프론트 (포트 3001)                    │
│          https://admin.aod.example.com                   │
├─────────────────────────────────────────────────────────┤
│ Crawling Dashboard, Batch Processing, Monitoring       │
└─────────────────────────────────────────────────────────┘
```

#### 장점
- ✅ **완전한 격리** - 사용자는 관리자 코드 접근 불가
- ✅ **보안 강화** - 관리자 페이지 URL 자체가 노출 안 됨
- ✅ **최적화된 번들** - 각 프론트엔드 용도에 맞게 최적화
- ✅ **독립 배포** - 관리자 페이지 업데이트해도 사용자 영향 없음
- ✅ **네트워크 격리** - VPN/IP 제한 가능

#### 단점
- ⚠️ 2개 프로젝트 관리
- ⚠️ 공통 코드 중복 가능 (해결 가능)

#### 프로젝트 생성

```bash
# 관리자 전용 프론트엔드 생성
cd D:\AOD
npm create vite@latest AOD-All-of-Dopamine-admin -- --template react-ts

cd AOD-All-of-Dopamine-admin
npm install
npm install axios react-router-dom recharts @tremor/react
```

#### 공통 코드 공유 방법

**옵션 A: NPM Private Package**
```bash
# 공통 라이브러리 생성
AOD-shared-lib/
├── src/
│   ├── api/          # API 클라이언트
│   ├── types/        # TypeScript 타입
│   └── utils/        # 유틸리티
└── package.json
```

**옵션 B: Git Submodule**
```bash
# 공통 코드 저장소
AOD-shared/
├── api/
├── types/
└── utils/

# 각 프로젝트에서 참조
git submodule add https://github.com/AOD/shared.git src/shared
```

**옵션 C: 심볼릭 링크 (개발 환경)**
```bash
# 공통 폴더 생성
mkdir D:\AOD\shared

# 심볼릭 링크 생성
mklink /D "D:\AOD\AOD-All-of-Dopamine-front\src\shared" "D:\AOD\shared"
mklink /D "D:\AOD\AOD-All-of-Dopamine-admin\src\shared" "D:\AOD\shared"
```

---

### **방식 3: 모노레포 (Monorepo)** ⭐⭐⭐⭐

```
AOD-All-of-Dopamine/
├── packages/
│   ├── user-frontend/      # 사용자 프론트
│   ├── admin-frontend/     # 관리자 프론트
│   ├── shared/             # 공통 코드
│   └── backend/            # 백엔드
├── package.json
└── pnpm-workspace.yaml
```

#### 장점
- ✅ 모든 코드 한 곳에서 관리
- ✅ 공통 코드 쉽게 공유
- ✅ 일관된 의존성 관리
- ✅ 통합 CI/CD

#### 단점
- ⚠️ 초기 설정 복잡
- ⚠️ 러닝 커브

#### 설정 예시 (pnpm)

```yaml
# pnpm-workspace.yaml
packages:
  - 'packages/*'
```

```json
// packages/admin-frontend/package.json
{
  "name": "@aod/admin-frontend",
  "dependencies": {
    "@aod/shared": "workspace:*"  // 로컬 패키지 참조
  }
}
```

---

## 🎯 현재 프로젝트 추천 방안

### **추천: 방식 2 (별도 Admin 프론트엔드)** ⭐⭐⭐⭐⭐

#### 이유

1. **보안이 중요한 관리자 기능**
   - 크롤링 제어
   - 배치 처리
   - 시스템 모니터링
   → 일반 사용자에게 노출되면 안 됨

2. **번들 크기 최적화**
   - 사용자 앱: 가볍고 빠르게
   - 관리자 앱: 기능 풍부하게

3. **관리 복잡도 적절**
   - 모노레포만큼 복잡하지 않음
   - 단일 프론트보다 명확한 분리

---

## 🛠️ 구현 단계

### Step 1: 관리자 프론트엔드 생성

```bash
cd D:\AOD
npm create vite@latest AOD-All-of-Dopamine-admin -- --template react-ts
cd AOD-All-of-Dopamine-admin
npm install axios react-router-dom @tanstack/react-query recharts
```

### Step 2: 기본 구조 생성

```typescript
// src/App.tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AdminLayout } from './layouts/AdminLayout';
import { CrawlingPage } from './pages/CrawlingPage';
import { BatchPage } from './pages/BatchPage';
import { MonitoringPage } from './pages/MonitoringPage';
import { LoginPage } from './pages/LoginPage';

const queryClient = new QueryClient();

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<AdminLayout />}>
            <Route index element={<Navigate to="/crawling" replace />} />
            <Route path="crawling" element={<CrawlingPage />} />
            <Route path="batch" element={<BatchPage />} />
            <Route path="monitoring" element={<MonitoringPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
```

### Step 3: API 클라이언트 설정

```typescript
// src/api/client.ts
import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api';

export const apiClient = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 인증 토큰 자동 추가
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('adminToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 401 에러 시 로그인 페이지로 리다이렉트
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('adminToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

### Step 4: 환경 변수 설정

```bash
# .env.development
VITE_API_BASE=http://localhost:8080/api

# .env.production
VITE_API_BASE=https://api.aod.example.com/api
```

---

## 🔒 백엔드 보안 강화

### 1. 관리자 API 보호

```java
// SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 사용자 API - 인증 선택
                .requestMatchers("/api/contents/**").permitAll()
                .requestMatchers("/api/user/**").authenticated()
                
                // 관리자 API - ADMIN 권한 필수
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/crawl/**").hasRole("ADMIN")
                .requestMatchers("/api/batch/**").hasRole("ADMIN")
                
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        
        return http.build();
    }
}
```

### 2. IP 화이트리스트 (선택사항)

```java
@Component
public class AdminIpFilter implements Filter {
    
    private static final List<String> ALLOWED_IPS = List.of(
        "127.0.0.1",
        "192.168.1.0/24",  // 내부 네트워크
        "your-office-ip"
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        
        if (path.startsWith("/api/admin/") || 
            path.startsWith("/api/crawl/") || 
            path.startsWith("/api/batch/")) {
            
            String clientIp = getClientIp(httpRequest);
            if (!isAllowedIp(clientIp)) {
                ((HttpServletResponse) response).sendError(403, "Access Denied");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }
}
```

---

## 🚀 배포 전략

### 개발 환경
```
사용자 프론트: http://localhost:3000
관리자 프론트: http://localhost:3001
백엔드 API:    http://localhost:8080
```

### 프로덕션 환경

**옵션 A: 서브도메인**
```
사용자: https://aod.example.com
관리자: https://admin.aod.example.com (VPN 필수)
API:    https://api.aod.example.com
```

**옵션 B: 다른 포트**
```
사용자: https://aod.example.com
관리자: https://aod.example.com:3001 (방화벽 제한)
API:    https://api.aod.example.com
```

---

## 📦 번들 크기 비교

### 단일 프론트엔드 (코드 스플리팅 없을 때)
```
사용자 페이지 로딩:
- user code: 500KB
- admin code: 300KB  ❌ 불필요
- shared: 200KB
Total: 1000KB
```

### 분리된 프론트엔드
```
사용자 페이지 로딩:
- user code: 500KB
- shared: 200KB
Total: 700KB  ✅ 30% 감소

관리자 페이지 로딩:
- admin code: 300KB
- shared: 200KB
Total: 500KB
```

---

## ✅ 최종 권장사항

### **별도 Admin 프론트엔드 프로젝트 생성** 🎯

**이유:**
1. ✅ **보안** - 관리자 코드 완전 격리
2. ✅ **성능** - 사용자 앱 번들 크기 최적화
3. ✅ **관리** - 명확한 책임 분리
4. ✅ **배포** - 독립적 배포 가능
5. ✅ **접근 제어** - 서브도메인/VPN으로 물리적 격리

**구현 복잡도:** 중간 (모노레포보다 쉬움)  
**보안 수준:** 높음  
**유지보수:** 용이  

---

## 🔄 마이그레이션 계획

### Phase 1: 관리자 프론트 생성 (1-2일)
- [ ] Vite 프로젝트 생성
- [ ] 기본 레이아웃 구성
- [ ] API 클라이언트 설정

### Phase 2: 핵심 기능 구현 (3-5일)
- [ ] 크롤링 대시보드
- [ ] 배치 처리 페이지
- [ ] 모니터링 페이지

### Phase 3: 보안 및 배포 (2-3일)
- [ ] 인증/권한 구현
- [ ] 백엔드 API 보호
- [ ] 프로덕션 배포 설정

**총 예상 시간: 1-2주**

---

**결론**: 보안과 성능을 고려하면 **별도 관리자 프론트엔드 프로젝트**가 최선의 선택입니다! 🎯
