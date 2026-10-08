# Load test results

> Fill this in from YOUR machine. Never copy numbers from anywhere else — every value must come from a k6 run
> you can show (`load-test/results.json`).

## Machine

| Item | Value |
|---|---|
| CPU | e.g. Intel Core i7-1165G7, 4 cores / 8 threads |
| RAM | e.g. 16 GB |
| OS | e.g. Windows 11 + Docker Desktop (WSL2), 8 GB given to Docker |
| Stack | `docker compose up -d --build` (single Kafka broker, Postgres 16, Redis 7, 3 services) |

## Runs (steady phase: 2 minutes after a 30 s warm-up)

| Target RATE | http_reqs/s (steady) | p50 | p95 | p99 | Error rate | dropped_iterations | Lag back to 0? | Pass? |
|---|---|---|---|---|---|---|---|---|
| 50  | | | | | | | | |
| 100 | | | | | | | | |
| 200 | | | | | | | | |
| ... | | | | | | | | |

## Final result (median of 3 runs at the highest passing rate)

| Metric | Value |
|---|---|
| Sustained throughput | ___ TPS |
| p50 / p95 / p99 latency (POST, 202 Accepted) | ___ / ___ / ___ ms |
| Error rate | ___ % |
| End-to-end RECEIVED -> SETTLED p99 (from `/actuator/prometheus`, `payments_settlement_duration_seconds`) | ___ ms |

Resume line: *Load-tested with k6 at ___ transactions/sec with p99 latency of ___ ms on a single Docker Compose stack.*
