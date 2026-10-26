import http from 'k6/http';
import { check, sleep } from 'k6';
export const options = {
  vus: Number(__ENV.VUS || 5), duration: __ENV.DURATION || '30s',
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1000'] }
};
export default function () {
  const base = __ENV.BASE_URL || 'http://localhost:8080';
  const region = ['서울', '경기', '부산', '대전'][__ITER % 4];
  const r = http.get(`${base}/api/trades?region=${encodeURIComponent(region)}&sort=recent`);
  check(r, {'HTTP 200': x => x.status === 200, 'valid result': x => {try {return Array.isArray(x.json('items'));}catch {return false;}}});
  sleep(0.2);
}
