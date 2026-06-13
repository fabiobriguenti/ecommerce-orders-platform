# Performance tests (k6)

On-demand load/stress tests for `order-service`. **Not** part of `mvn verify` or the CI gate — run
these manually when you want a throughput/latency number.

## What it measures

`load-test.js` hammers `GET /api/v1/orders/{id}` — a pure **app + Postgres** read (no WireMock on the
path), so the result reflects the application's own throughput. Two modes (env `MODE`):

- `ramp` (default) — *ramping-arrival-rate*: climbs the request rate in stages to find the **ceiling**.
- `const` — *constant-arrival-rate*: holds `RATE` req/s for 1 min to **verify a target**.

Thresholds (make the run pass/fail): `http_req_failed < 1%` and `http_req_duration p95 < 200ms`.

## Run a baseline

```bash
# 1. Start the stack with the rate limiter OFF (otherwise you measure the 120 req/min guard → 429).
docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml up -d --build

# 2. Mint a token and create one order to read.
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token | jq -r .accessToken)
OID=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-active"}' | jq -r .id)

# 3. Run k6 on the compose network (no host-networking headaches on Docker Desktop).
docker run --rm -i --network ecommerce-orders-platform_default \
  -e BASE_URL=http://order-service:8080 -e TOKEN="$TOKEN" -e ORDER_ID="$OID" \
  grafana/k6 run - < perf/load-test.js

# 4. Tear down.
docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml down
```

Verify a specific target instead of finding the ceiling:

```bash
docker run --rm -i --network ecommerce-orders-platform_default \
  -e BASE_URL=http://order-service:8080 -e TOKEN="$TOKEN" -e ORDER_ID="$OID" \
  -e MODE=const -e RATE=300 \
  grafana/k6 run - < perf/load-test.js
```

## Knobs (env vars)

| Var | Default | Meaning |
|-----|---------|---------|
| `MODE` | `ramp` | `ramp` (find ceiling) or `const` (hold a target) |
| `RATE` | `200` | req/s for `MODE=const` |
| `MAX` | `600` | top req/s for `MODE=ramp` |
| `DURATION` | `1m` | hold time for `MODE=const` |
| `TARGET` | `get` | `get` = `GET /orders/{id}` (app + Postgres, exercises the R2DBC pool); `create` = `POST /orders` with an unknown customer → one outbound call to the Customer WireMock, no DB (isolates the WebClient/Netty pool) |
| `CUSTOMER_ID` | `perf-unknown` | customer id used by `TARGET=create` |

The two pools are A/B-able **without rebuilding** via the perf overlay (host env → container):
`DB_POOL_MAX` (R2DBC, app default 30) and `EXTERNAL_POOL_MAX_CONN` (WebClient, app default 200). Set
them before `docker compose ... up -d order-service` to recreate the service with new sizes.

## Reading the result

- `http_reqs` — total requests and the **achieved req/s** (the headline throughput).
- `http_req_duration` — latency `avg / p(90) / p(95) / p(99)`.
- `http_req_failed` — error rate; a sustained climb here marks the saturation point.
- Watch it live in **Grafana → _Order Service_** (request rate, p95, 5xx) while it runs.

## Notes

- **Rate limiter**: always use the perf overlay (or `RATE_LIMIT_RPM` high), else the limiter caps you.
- **Token TTL** is 1h by default — re-mint for longer soak runs.
- **Co-located generator**: running k6 and the app on the same host shares CPU and understates the
  ceiling. For a trustworthy number, drive load from a separate machine.
