/**
 * 상품 목록 조회 부하 테스트 (N+1 최적화 검증)
 *
 * 시나리오: 상품 100개 + 해시태그 + 팔로우 관계가 있는 상태에서
 *          30 VU가 30초간 지속적으로 상품 목록을 조회
 * 검증: JOIN FETCH + 배치 쿼리 최적화로 안정적인 응답시간 유지
 *
 * 실행: k6 run k6/test-product-listing.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, TEST_PASSWORD, login, authHeaders } from './helpers.js';

export const options = {
  scenarios: {
    product_listing: {
      executor: 'constant-vus',
      vus: 30,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const token = login('k6_user001@test.com', TEST_PASSWORD);
  if (!token) {
    throw new Error('Login failed');
  }

  // 워밍업 요청
  http.get(`${BASE_URL}/api/products`, { headers: authHeaders(token) });

  return { token };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/api/products`, {
    headers: authHeaders(data.token),
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'returns array': (r) => {
      try {
        return Array.isArray(JSON.parse(r.body));
      } catch {
        return false;
      }
    },
    'has products': (r) => {
      try {
        return JSON.parse(r.body).length > 0;
      } catch {
        return false;
      }
    },
  });

  sleep(0.1);
}
