import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

/**
 * Crawler Load Test
 * 
 * 목적: EC2 t3.small에서 안정적 처리 가능한 부하 테스트
 * 시나리오:
 *   - 1분: 0 → 10 VUs (Ramp up)
 *   - 3분: 10 VUs 유지 (Steady state)
 *   - 1분: 10 → 20 VUs (Peak)
 *   - 3분: 20 VUs 유지 (Stress)
 *   - 1분: 20 → 0 VUs (Ramp down)
 * 
 * 총 소요 시간: 9분
 */

export const options = {
    stages: [
        { duration: '1m', target: 10 },   // Warm up
        { duration: '3m', target: 10 },   // Steady state
        { duration: '1m', target: 20 },   // Peak load
        { duration: '3m', target: 20 },   // Sustained load
        { duration: '1m', target: 0 },    // Cool down
    ],

    thresholds: {
        'http_req_duration': ['p(95)<3000', 'p(99)<5000'],
        'http_req_failed': ['rate<0.10'],
        'checks': ['rate>0.90'],
        'error_rate': ['rate<0.10'],
    },

    tags: {
        test_type: 'load',
        target: 'crawler',
        environment: 't3.small',
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8081';
const errorRate = new Rate('error_rate');

export default function () {
    // 1. Health check (가벼움)
    const healthRes = http.get(`${BASE_URL}/actuator/health`, {
        tags: { endpoint: 'health' },
    });

    const healthOk = check(healthRes, {
        'health is 200': (r) => r.status === 200,
    });
    errorRate.add(!healthOk);

    sleep(1);

    // 2. Metrics endpoint (무거움 - 실제 부하)
    const metricsRes = http.get(`${BASE_URL}/actuator/prometheus`, {
        tags: { endpoint: 'metrics' },
    });

    const metricsOk = check(metricsRes, {
        'metrics is 200': (r) => r.status === 200,
        'has crawler metrics': (r) => r.body.includes('crawl_job'),
    });
    errorRate.add(!metricsOk);

    sleep(2);

    // 3. Threaddump (매우 무거움 - 스트레스 유발)
    const threaddumpRes = http.get(`${BASE_URL}/actuator/threaddump`, {
        tags: { endpoint: 'threaddump' },
    });

    const threaddumpOk = check(threaddumpRes, {
        'threaddump is 200': (r) => r.status === 200,
    });
    errorRate.add(!threaddumpOk);

    sleep(3);
}

export function handleSummary(data) {
    const passed =
        data.metrics.http_req_failed.values.rate < 0.10 &&
        data.metrics['http_req_duration'].values['p(95)'] < 3000;

    console.log(`\n${'='.repeat(60)}`);
    console.log(`Load Test ${passed ? '✓ PASSED' : '✗ FAILED'}`);
    console.log(`${'='.repeat(60)}`);
    console.log(`Total Requests:    ${data.metrics.http_reqs.values.count}`);
    console.log(`Failed Requests:   ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`);
    console.log(`Avg Response Time: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`P95 Response Time: ${data.metrics['http_req_duration'].values['p(95)'].toFixed(2)}ms`);
    console.log(`P99 Response Time: ${data.metrics['http_req_duration'].values['p(99)'].toFixed(2)}ms`);
    console.log(`${'='.repeat(60)}\n`);

    return {
        'stdout': '',
        '/reports/crawler-load-test.json': JSON.stringify(data, null, 2),
        '/reports/crawler-load-test-summary.txt': generateTextSummary(data, passed),
    };
}

function generateTextSummary(data, passed) {
    return `
Crawler Load Test Report
========================
Test Date: ${new Date().toISOString()}
Result: ${passed ? 'PASSED ✓' : 'FAILED ✗'}

Performance Metrics:
-------------------
Total Requests:        ${data.metrics.http_reqs.values.count}
Success Rate:          ${((1 - data.metrics.http_req_failed.values.rate) * 100).toFixed(2)}%
Error Rate:            ${(data.metrics.error_rate.values.rate * 100).toFixed(2)}%

Response Times:
--------------
Average:               ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms
Median (P50):          ${data.metrics['http_req_duration'].values.med.toFixed(2)}ms
P95:                   ${data.metrics['http_req_duration'].values['p(95)'].toFixed(2)}ms
P99:                   ${data.metrics['http_req_duration'].values['p(99)'].toFixed(2)}ms
Max:                   ${data.metrics.http_req_duration.values.max.toFixed(2)}ms

Test Configuration:
------------------
Max VUs:               20
Duration:              9 minutes
Target:                EC2 t3.small (2 vCPU, 2GB RAM)

Thresholds:
----------
✓ P95 < 3000ms:        ${data.metrics['http_req_duration'].values['p(95)'] < 3000 ? 'PASS' : 'FAIL'}
✓ Failure Rate < 10%:  ${data.metrics.http_req_failed.values.rate < 0.10 ? 'PASS' : 'FAIL'}
✓ Check Rate > 90%:    ${data.metrics.checks.values.rate > 0.90 ? 'PASS' : 'FAIL'}

Generated: ${new Date().toISOString()}
`;
}
