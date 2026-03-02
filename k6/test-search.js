/**
 * 검색 API 부하 테스트
 *
 * 시나리오: 다양한 검색어로 30 VU가 30초간 검색 요청
 * 검증: 해시태그 배치 로딩 + 검색 성능 측정
 *
 * 실행: k6 run k6/test-search.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, TEST_PASSWORD, login, authHeaders } from './helpers.js';

const SEARCH_QUERIES = [
  'sports',
  'camping',
  'popular',
  'outdoor',
];

export const options = {
  scenarios: {
    search_load: {
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

  // 워밍업
  http.get(`${BASE_URL}/api/search?query=sports&source=search`, {
    headers: authHeaders(token),
  });

  return { token };
}

export default function (data) {
  const query = SEARCH_QUERIES[Math.floor(Math.random() * SEARCH_QUERIES.length)];

  const res = http.get(
    `${BASE_URL}/api/search?query=${encodeURIComponent(query)}&source=search`,
    { headers: authHeaders(data.token) }
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has response body': (r) => r.body && r.body.length > 0,
  });

  sleep(0.1);
}
