import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * Crawler Smoke Test
 * 
 * 목적: 기본 연결 및 Health check 테스트
 * 부하: 5 VUs, 30초
 * 임계값: 95% 요청 2초 이내, 실패율 5% 미만
 */

export const options = {
  vus: 5,  // 동시 사용자 5명
  duration: '30s',
  
  thresholds: {
    'http_req_duration': ['p(95)<2000'],  // 95% 요청이 2초 이내
    'http_req_failed': ['rate<0.05'],      // 실패율 5% 미만
    'checks': ['rate>0.95'],               // 95% 이상 체크 통과
  },
  
  tags: {
    test_type: 'smoke',
    target: 'crawler',
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8081';

export default function () {
  // 1. Health check
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  check(healthRes, {
    'health check status is 200': (r) => r.status === 200,
    'health status is UP': (r) => r.json('status') === 'UP',
  });

  sleep(0.5);

  // 2. Info endpoint (기본 정보)
  const infoRes = http.get(`${BASE_URL}/actuator/info`);
  check(infoRes, {
    'info status is 200': (r) => r.status === 200,
    'has app info': (r) => r.json('app') !== undefined,
  });

  sleep(0.5);

  // 3. Prometheus metrics (가장 무거운 요청)
  const metricsRes = http.get(`${BASE_URL}/actuator/prometheus`);
  check(metricsRes, {
    'metrics status is 200': (r) => r.status === 200,
    'has jvm metrics': (r) => r.body.includes('jvm_memory_used_bytes'),
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    '/reports/crawler-smoke-test.json': JSON.stringify(data),
  };
}

function textSummary(data, options) {
  const { indent = '', enableColors = false } = options || {};
  
  return `
${indent}✓ Smoke Test Summary
${indent}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
${indent}Requests:     ${data.metrics.http_reqs.values.count}
${indent}Duration:     ${data.state.testRunDurationMs / 1000}s
${indent}Success Rate: ${(1 - data.metrics.http_req_failed.values.rate) * 100}%
${indent}Avg Response: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms
${indent}P95 Response: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms
${indent}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  `;
}
