/**
 * 동시 예약 부하 테스트 (Pessimistic Lock 검증)
 *
 * 시나리오: 재고 50개인 상품에 100명이 동시에 1개씩 예약
 * 기대 결과: 정확히 50건 성공, 50건 실패 (재고 초과 0건)
 *
 * 실행: k6 run k6/test-concurrent-reserve.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, TEST_PASSWORD, login, authHeaders } from './helpers.js';

const reserveSuccess = new Counter('reserve_success');
const reserveFail = new Counter('reserve_fail');
const unexpectedReserveError = new Counter('unexpected_reserve_error');

export const options = {
  scenarios: {
    concurrent_reserve: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      maxDuration: '60s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    reserve_success: ['count==50'],
    reserve_fail: ['count==50'],
    unexpected_reserve_error: ['count==0'],
  },
};

function isExpectedStockFailure(res) {
  if (res.status !== 409) {
    return false;
  }

  try {
    const body = JSON.parse(res.body);
    return body.message === '재고가 부족합니다.';
  } catch (e) {
    return false;
  }
}

export function setup() {
  // 동시 예약 대상 상품 ID 조회
  const ownerToken = login('k6_user001@test.com', TEST_PASSWORD);
  if (!ownerToken) {
    throw new Error('Owner login failed');
  }

  const productsRes = http.get(`${BASE_URL}/api/products`, {
    headers: authHeaders(ownerToken),
  });

  const products = JSON.parse(productsRes.body);
  const targetProduct = products.find(
    (p) => p.title === 'k6_test_concurrent_reserve'
  );
  if (!targetProduct) {
    throw new Error('Target product not found. Run setup-data.sql first.');
  }

  // 100명 로그인하여 토큰 수집
  const tokens = [];
  for (let i = 1; i <= 100; i++) {
    const email = `k6_user${String(i).padStart(3, '0')}@test.com`;
    const token = login(email, TEST_PASSWORD);
    if (token) {
      tokens.push(token);
    }
  }

  if (tokens.length < 100) {
    throw new Error(`Expected 100 login tokens, collected ${tokens.length}`);
  }

  console.log(
    `Setup complete: productId=${targetProduct.id}, stock=${targetProduct.availableQuantity}, users=${tokens.length}`
  );

  return {
    productId: targetProduct.id,
    tokens: tokens,
    initialStock: targetProduct.availableQuantity,
  };
}

export default function (data) {
  const vuIndex = __VU - 1;
  const token = data.tokens[vuIndex % data.tokens.length];

  const res = http.post(
    `${BASE_URL}/api/products/${data.productId}/reserve`,
    JSON.stringify({ quantity: 1 }),
    { headers: authHeaders(token) }
  );

  const expectedStockFailure = isExpectedStockFailure(res);

  check(res, {
    'status is 200 or expected stock failure': (r) =>
      r.status === 200 || expectedStockFailure,
  });

  if (res.status === 200) {
    reserveSuccess.add(1);
  } else if (expectedStockFailure) {
    reserveFail.add(1);
  } else {
    unexpectedReserveError.add(1);
  }
}

export function teardown(data) {
  // 최종 재고 확인
  const token = data.tokens[0];
  const res = http.get(`${BASE_URL}/api/products/${data.productId}`, {
    headers: authHeaders(token),
  });

  if (res.status !== 200) {
    throw new Error(`Final product fetch failed: ${res.status} ${res.body}`);
  }

  const product = JSON.parse(res.body);
  console.log(`\n========== 동시 예약 테스트 결과 ==========`);
  console.log(`초기 재고: ${data.initialStock}`);
  console.log(`최종 재고: ${product.availableQuantity}`);
  console.log(`예약된 수량: ${data.initialStock - product.availableQuantity}`);
  console.log(`상태: ${product.status}`);
  console.log(`==========================================\n`);

  if (product.availableQuantity !== 0) {
    throw new Error(
      `Expected final stock to be 0, got ${product.availableQuantity}`
    );
  }
}
