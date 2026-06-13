import http from 'k6/http';
import { check } from 'k6';

// Load/stress test for order-service. Targets a pure app+DB read (GET /orders/{id}) so the number
// reflects the application's throughput, not the WireMock-backed write path. Two modes:
//   MODE=ramp  (default) — ramping-arrival-rate: climb the request rate to find the ceiling.
//   MODE=const            — constant-arrival-rate: hold RATE req/s to verify a target.
//
// Run (on the compose network, rate limiter disabled via perf/docker-compose.perf.yml):
//   docker run --rm -i --network ecommerce-orders-platform_default \
//     -e BASE_URL=http://order-service:8080 -e TOKEN=$TOKEN -e ORDER_ID=$OID \
//     grafana/k6 run - < perf/load-test.js
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const ORDER_ID = __ENV.ORDER_ID || '';
const MODE = __ENV.MODE || 'ramp';
const RATE = Number(__ENV.RATE || 200);          // req/s for MODE=const
const MAX = Number(__ENV.MAX || 600);            // top req/s for MODE=ramp
const DURATION = __ENV.DURATION || '1m';         // hold time for MODE=const
// TARGET=get    -> GET /orders/{id}: app + Postgres (exercises the R2DBC pool).
// TARGET=create -> POST /orders with an unknown customer: one outbound call to the Customer WireMock
//                  and zero DB writes (404 before persistence) -> isolates the WebClient/Netty pool.
const TARGET = __ENV.TARGET || 'get';
const CUSTOMER = __ENV.CUSTOMER_ID || 'perf-unknown';

// Treat the target's expected status as success so http_req_failed/thresholds stay meaningful.
http.setResponseCallback(http.expectedStatuses(TARGET === 'create' ? 404 : 200));

const scenarios = MODE === 'const'
  ? {
      target: {
        executor: 'constant-arrival-rate',
        // VUs needed ~= rate x latency(s), not = rate; keep a base pool with generous headroom.
        rate: RATE, timeUnit: '1s', duration: DURATION,
        preAllocatedVUs: 200, maxVUs: 4000,
      },
    }
  : {
      ceiling: {
        executor: 'ramping-arrival-rate',
        startRate: 50, timeUnit: '1s',
        preAllocatedVUs: 100, maxVUs: 2000,
        stages: [
          { target: Math.round(MAX * 0.25), duration: '30s' },
          { target: Math.round(MAX * 0.5), duration: '30s' },
          { target: MAX, duration: '45s' },
          { target: MAX, duration: '30s' },
        ],
      },
    };

export const options = {
  scenarios,
  thresholds: {
    http_req_failed: ['rate<0.01'],     // < 1% failed requests
    http_req_duration: ['p(95)<200'],   // p95 latency under 200ms
  },
};

export default function () {
  if (TARGET === 'create') {
    const res = http.post(`${BASE}/api/v1/orders`, JSON.stringify({ customerId: CUSTOMER }), {
      headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
      tags: { name: 'create-order' },
    });
    check(res, { 'responded 404': (r) => r.status === 404 });
  } else {
    const res = http.get(`${BASE}/api/v1/orders/${ORDER_ID}`, {
      headers: { Authorization: `Bearer ${TOKEN}` },
      tags: { name: 'get-order' },
    });
    check(res, { 'status is 200': (r) => r.status === 200 });
  }
}
