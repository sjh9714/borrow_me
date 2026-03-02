import http from 'k6/http';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:5000';
export const TEST_PASSWORD = 'TestPass123';

export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (res.status !== 200) {
    console.error(`Login failed for ${email}: ${res.status} ${res.body}`);
    return null;
  }

  const body = JSON.parse(res.body);
  return body.token;
}

export function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}
