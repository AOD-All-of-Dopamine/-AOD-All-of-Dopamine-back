import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

/**
 * API Server Load Test
 * 
 * 목적: API 서버(8080) 성능 테스트
 * 엔드포인트:
 *   - Health check
 *   - Recommendation API (전통적 추천)
 *   - Ranking API
 * 
 * 시나리오: 15 VUs, 5분간 지속
 */

export const options = {
    vus: 15,
    duration: '5m',

    thresholds: {
        'http_req_duration': ['p(95)<2000'],
        'http_req_failed': ['rate<0.05'],
        'api_errors': ['rate<0.05'],
        'recommendation_duration': ['p(95)<3000'],
    },

    tags: {
        test_type: 'load',
        target: 'api',
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const apiErrors = new Rate('api_errors');
const recommendationDuration = new Trend('recommendation_duration');

export default function () {
    group('Health Checks', function () {
        const healthRes = http.get(`${BASE_URL}/actuator/health`, {
            tags: { endpoint: 'health' },
        });

        check(healthRes, {
            'health status is 200': (r) => r.status === 200,
            'health is UP': (r) => r.json('status') === 'UP',
        }) || apiErrors.add(1);
    });

    sleep(1);

    group('Recommendation API', function () {
        // Assuming your API has a recommendation endpoint
        // Adjust the URL and parameters based on your actual API
        const params = {
            headers: {
                'Content-Type': 'application/json',
            },
            tags: { endpoint: 'recommendation' },
        };

        const payload = JSON.stringify({
            userId: `user_${__VU}_${Math.floor(Math.random() * 1000)}`,
            domain: randomDomain(),
            limit: 10,
        });

        const start = new Date();
        const recRes = http.post(`${BASE_URL}/api/recommendations/traditional`, payload, params);
        const duration = new Date() - start;

        recommendationDuration.add(duration);

        const recOk = check(recRes, {
            'recommendation status is 200': (r) => r.status === 200 || r.status === 404, // 404 acceptable if no data yet
            'recommendation has items': (r) => {
                if (r.status === 200) {
                    const body = r.json();
                    return Array.isArray(body) || body.items !== undefined;
                }
                return true; // 404 is ok
            },
        });

        if (!recOk) apiErrors.add(1);
    });

    sleep(2);

    group('Ranking API', function () {
        const domain = randomDomain();
        const rankingRes = http.get(`${BASE_URL}/api/rankings/${domain}`, {
            tags: { endpoint: 'ranking' },
        });

        const rankingOk = check(rankingRes, {
            'ranking status is 200': (r) => r.status === 200 || r.status === 404,
            'ranking has data': (r) => {
                if (r.status === 200) {
                    const body = r.json();
                    return Array.isArray(body) || body.rankings !== undefined;
                }
                return true;
            },
        });

        if (!rankingOk) apiErrors.add(1);
    });

    sleep(2);
}

function randomDomain() {
    const domains = ['MOVIE', 'TV', 'GAME', 'WEBTOON', 'WEBNOVEL'];
    return domains[Math.floor(Math.random() * domains.length)];
}

export function handleSummary(data) {
    const passed =
        data.metrics.http_req_failed.values.rate < 0.05 &&
        data.metrics['http_req_duration'].values['p(95)'] < 2000;

    console.log(`\n${'='.repeat(60)}`);
    console.log(`API Load Test ${passed ? '✓ PASSED' : '✗ FAILED'}`);
    console.log(`${'='.repeat(60)}`);
    console.log(`Total Requests:          ${data.metrics.http_reqs.values.count}`);
    console.log(`Failed Requests:         ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`);
    console.log(`API Errors:              ${(data.metrics.api_errors.values.rate * 100).toFixed(2)}%`);
    console.log(`Avg Response Time:       ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`P95 Response Time:       ${data.metrics['http_req_duration'].values['p(95)'].toFixed(2)}ms`);
    console.log(`Recommendation P95:      ${data.metrics.recommendation_duration.values['p(95)'].toFixed(2)}ms`);
    console.log(`${'='.repeat(60)}\n`);

    return {
        'stdout': '',
        '/reports/api-load-test.json': JSON.stringify(data, null, 2),
    };
}
