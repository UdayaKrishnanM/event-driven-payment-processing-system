// k6 load test for POST /api/v1/payments
//   k6 run -e RATE=100 load-test/payment-load.js
//   k6 run -e RATE=350 --summary-export=load-test/results.json load-test/payment-load.js
// Optional: -e BASE_URL=http://localhost:8081
//
// Each request uses a fresh Idempotency-Key and a random Luhn-valid card, so neither idempotency
// nor the velocity rule (max 5 payments / card / 60 s) blocks the test.
import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const RATE = Number(__ENV.RATE || 100);
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup: { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s',
              duration: '30s', preAllocatedVUs: 20, maxVUs: 50, tags: { phase: 'warmup' } },
    steady: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s',
              duration: '2m', startTime: '30s', preAllocatedVUs: 100, maxVUs: 500,
              tags: { phase: 'steady' } },
  },
  thresholds: {
    'http_req_failed{phase:steady}': ['rate<0.01'],   // under 1% errors
    'http_req_duration{phase:steady}': ['p(99)<500'],  // p99 under 500 ms
    'http_reqs{phase:steady}': [],                     // shows steady-phase TPS in the summary
  },
};

function luhnCard() {
  const body = '4' + Array.from({ length: 14 }, () => Math.floor(Math.random() * 10)).join('');
  let sum = 0;
  for (let i = 0; i < body.length; i++) {
    let d = Number(body[body.length - 1 - i]);
    if (i % 2 === 0) { d *= 2; if (d > 9) d -= 9; }
    sum += d;
  }
  return body + ((10 - (sum % 10)) % 10);
}

export default function () {
  const payload = JSON.stringify({
    merchantId: `MER-${1000 + Math.floor(Math.random() * 20)}`,
    cardNumber: luhnCard(),
    expiryMonth: 12, expiryYear: 2029,
    amount: (100 + Math.random() * 4900).toFixed(2),
    currency: 'INR',
  });
  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': uuidv4() },
  });
  check(res, { 'status is 202': (r) => r.status === 202 });
}
