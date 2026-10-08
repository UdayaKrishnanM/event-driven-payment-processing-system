# Setup, Run, Troubleshoot, Validate and Understand — Complete Guide

This guide takes you from an empty machine to a fully validated, running system, and then explains everything that happens inside it so you can demo it and answer interview questions.

**Contents**

1. [What you are running](#1-what-you-are-running)
2. [Install prerequisites](#2-install-prerequisites)
3. [Start the app (Option A: everything in Docker)](#3-start-the-app--option-a-everything-in-docker-recommended)
4. [Start the app (Option B: infrastructure in Docker, services in IntelliJ)](#4-start-the-app--option-b-infrastructure-in-docker-services-in-intellij)
5. [Validate the full app](#5-validate-the-full-app)
6. [Run the tests, coverage gate and CI](#6-run-the-tests-coverage-gate-and-ci)
7. [Load test with k6](#7-load-test-with-k6)
8. [Troubleshooting — fixing issues](#8-troubleshooting--fixing-issues)
9. [Understand everything that happens](#9-understand-everything-that-happens)
10. [Code map — where each feature lives](#10-code-map--where-each-feature-lives)
11. [Useful commands cheat sheet](#11-useful-commands-cheat-sheet)
12. [Publishing to GitHub + SonarCloud](#12-publishing-to-github--sonarcloud)

---

## 1. What you are running

| Container | Port | Purpose |
|---|---|---|
| `edps-kafka` | 9094 (host), 9092 (inside Docker) | Apache Kafka 3.7 in KRaft mode (no ZooKeeper) |
| `edps-kafka-ui` | 8080 | Web UI to see topics, messages and consumer lag |
| `edps-postgres` | 5432 | PostgreSQL 16, database `payments`, schemas `payment`, `ledger`, `notification` |
| `edps-redis` | 6379 | Redis 7: idempotency keys, velocity counter, status cache |
| `edps-payment-service` | 8081 | REST API, idempotency, authorizer, settlement status |
| `edps-ledger-service` | 8082 | Double-entry ledger |
| `edps-notification-service` | 8083 | Merchant notifications + dead-letter monitor |

Kafka topics (created automatically by the services, 3 partitions each):
`payment.initiated`, `payment.authorized`, `payment.declined`, `payment.settled` and one `.DLT` topic for each (8 in total).

Database logins (created by `infra/postgres/init.sql`):

| User | Password | Can access |
|---|---|---|
| `postgres` | `postgres` | everything (admin, for you) |
| `payment_svc` | `payment_pwd` | schema `payment` only |
| `ledger_svc` | `ledger_pwd` | schema `ledger` only |
| `notification_svc` | `notification_pwd` | schema `notification` only |

---

## 2. Install prerequisites

| Tool | Version | Needed for | Check |
|---|---|---|---|
| Docker Desktop | recent (Engine 25+) | Running everything, Testcontainers | `docker version`, `docker compose version` |
| Git | any | Cloning / pushing | `git --version` |
| JDK | **17** (Temurin recommended) | Running tests / services from IntelliJ | `java -version` |
| Maven | 3.9+ | Building / tests | `mvn -version` |
| IntelliJ IDEA Community | any recent | Reading / running code | — |
| Postman | any recent | Functional validation | — |
| k6 | any recent | Load test | `k6 version` |

Windows install commands (PowerShell):

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install Apache.Maven          # or: choco install maven
winget install Docker.DockerDesktop
winget install Git.Git
winget install Postman.Postman
winget install k6 --source winget
```

Mac: `brew install --cask temurin@17 docker postman` and `brew install maven git k6`.

**Docker Desktop settings:** Settings → Resources → give Docker **at least 6 GB RAM** (8 GB is better for load testing) and 4 CPUs. On Windows, use the WSL 2 backend.

**JAVA_HOME:** Maven must use JDK 17 (21 also works). `mvn -version` prints the Java version it uses. If it shows Java 8 or 11, set `JAVA_HOME` to the JDK 17 folder and reopen the terminal.

---

## 3. Start the app — Option A: everything in Docker (recommended)

Nothing but Docker is needed for this option; the Java build happens inside Docker.

```bash
# 1. unzip the project and open a terminal inside the folder that contains docker-compose.yml
cd event-driven-payment-system

# 2. build images and start all 7 containers (first time: 3-8 minutes, it downloads Maven dependencies)
docker compose up -d --build

# 3. watch until all three services show "(healthy)"
docker compose ps
```

Expected `docker compose ps` (abbreviated):

```
edps-kafka                  Up (healthy)
edps-kafka-ui               Up
edps-postgres               Up (healthy)
edps-redis                  Up (healthy)
edps-payment-service        Up (healthy)
edps-ledger-service         Up (healthy)
edps-notification-service   Up (healthy)
```

The services wait for Kafka/Postgres/Redis to be healthy, then take ~20–40 s to start. If one stays `starting`/`unhealthy`, see [Troubleshooting](#8-troubleshooting--fixing-issues).

4. Quick check — all three should print `{"status":"UP",...}`:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

5. Open in a browser:
   * Kafka UI: http://localhost:8080 → Topics (you should see 8 payment topics, plus Kafka's internal `__consumer_offsets`)
   * Swagger UI: http://localhost:8081/swagger-ui.html (and 8082, 8083)

6. Send your first payment.

   **macOS / Linux / Git Bash:**
   ```bash
   curl -i -X POST http://localhost:8081/api/v1/payments \
     -H "Content-Type: application/json" \
     -H "Idempotency-Key: 3f0c8a52-7d1e-4e7b-9a43-1b2c3d4e5f60" \
     -d '{"merchantId":"MER-1001","cardNumber":"4111111111111111","expiryMonth":12,"expiryYear":2028,"amount":2499.00,"currency":"INR"}'
   ```

   **Windows PowerShell** (use `curl.exe`, not the `curl` alias):
   ```powershell
   $body = '{"merchantId":"MER-1001","cardNumber":"4111111111111111","expiryMonth":12,"expiryYear":2028,"amount":2499.00,"currency":"INR"}'
   Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/payments `
     -Headers @{ "Idempotency-Key" = [guid]::NewGuid().ToString() } `
     -ContentType "application/json" -Body $body
   ```

   You get `202 Accepted` with `"status":"RECEIVED"` and a `paymentId`. One or two seconds later:

   ```bash
   curl http://localhost:8081/api/v1/payments/<paymentId>     # "status":"SETTLED", "authCode":"X7K2P9"
   ```

**Stopping and resetting**

```bash
docker compose stop              # stop, keep everything
docker compose start             # start again
docker compose down              # remove containers (Postgres data is kept in a volume; Kafka topics are recreated)
docker compose down -v           # remove containers AND the Postgres volume -> completely fresh start
docker compose up -d --build payment-service   # rebuild + restart just one service after a code change
```

---

## 4. Start the app — Option B: infrastructure in Docker, services in IntelliJ

Use this while developing or debugging (breakpoints).

1. Start only the infrastructure:
   ```bash
   docker compose up -d kafka kafka-ui postgres redis
   ```
2. Build once so the `common` module is installed for the services:
   ```bash
   mvn -DskipTests install
   ```
3. In IntelliJ: **File → Open →** the folder with the root `pom.xml` (it imports all 4 modules).
   * **Settings → Build → Compiler → Annotation Processors → Enable annotation processing** (needed for Lombok). Also install the Lombok plugin if IntelliJ asks.
   * **File → Project Structure → SDK = 17.**
4. Run the three main classes (green ▶ next to `main`):
   * `payment-service/.../PaymentServiceApplication`
   * `ledger-service/.../LedgerServiceApplication`
   * `notification-service/.../NotificationServiceApplication`

   Their `application.yml` defaults already point at `localhost:9094` (Kafka), `localhost:5432` (Postgres) and `localhost:6379` (Redis).

   Alternatively from a terminal (3 terminals):
   ```bash
   mvn -pl payment-service spring-boot:run
   mvn -pl ledger-service spring-boot:run
   mvn -pl notification-service spring-boot:run
   ```

> Don't run a service both in Docker and in IntelliJ at the same time — the ports (8081–8083) clash. `docker compose stop payment-service` first.

---

## 5. Validate the full app

Work through these in order. Each one proves a feature (and a resume bullet).

### 5.1 Automated: Postman collection (recommended, ~1 minute)

1. Postman → **Import** → select both files in `postman/`:
   `Event-Driven-Payment-System.postman_collection.json` and `EDPS-Local.postman_environment.json`.
2. Select the environment **EDPS Local (docker compose)** (top right).
3. Right-click the collection → **Run collection** → keep the order → **Run**.

Expected: every test passes (39 requests, each with automated assertions). The folders:

| Folder | What it proves |
|---|---|
| 0. Health | All three services are UP |
| 1. Payment happy path + idempotency | 202 + RECEIVED; same key → same payment, same body, `Idempotent-Replayed: true`; same key + different body → 422; payment reaches SETTLED with a 6-char auth code |
| 2. Ledger | Exactly 2 entries (DEBIT CUSTOMER_CLEARING, CREDIT MERCHANT:&lt;id&gt;), merchant balance = 2499.00, whole ledger balanced |
| 3. Issuer declines | Card …0002 → INSUFFICIENT_FUNDS; 60000 → LIMIT_EXCEEDED; 6 payments on one card in 60 s → VELOCITY_CHECK_FAILED |
| 4. Validation | Missing/invalid key, bad Luhn, expired card, amount 0, > 100000, 3 decimals, bad currency, malformed JSON → 400 with `code/message/field/timestamp`; unknown payment → 404 |
| 5. Notifications | Authorized, settled and declined notifications exist |
| 6. Retries + DLT + replay | Chaos on → payment stuck AUTHORIZED, message on `payment.authorized.DLT` and in `failed_events` → chaos off → replay → SETTLED |
| 7. Observability | Prometheus metric `payments_settlement_duration_seconds` is exported |

> The polling requests ("poll until SETTLED") repeat themselves automatically in the **Collection Runner**. If you click **Send** manually, just click again after a second.
> Run from the command line: `npx newman run postman/Event-Driven-Payment-System.postman_collection.json -e postman/EDPS-Local.postman_environment.json`

### 5.2 Automated: smoke test script (macOS / Linux / Git Bash)

```bash
./scripts/smoke-test.sh
# ... Result: 17 passed, 0 failed
```

### 5.3 Manual demo checklist (what to show an interviewer)

**A. The full flow in seconds**
1. Kafka UI → Topics → `payment.initiated` → Messages. POST a payment. A message appears with key = paymentId and value = the event JSON (only `**** 1111`, never the full card).
2. Then `payment.authorized` and `payment.settled` get one message each.
3. `GET /api/v1/payments/{id}` → `SETTLED`.

**B. No double charges (API layer)** — send the exact same POST twice with the same `Idempotency-Key`:
* both return 202 with the **same** `paymentId`; the second has header `Idempotent-Replayed: true`;
* only one row exists:
  ```bash
  docker exec -it edps-postgres psql -U postgres -d payments -c \
    "SELECT id, status FROM payment.payments WHERE idempotency_key = '3f0c8a52-7d1e-4e7b-9a43-1b2c3d4e5f60';"
  ```
* change the amount but keep the key → **422** `IDEMPOTENCY_KEY_REUSED`.
* look at the Redis entry: `docker exec -it edps-redis redis-cli GET idem:3f0c8a52-7d1e-4e7b-9a43-1b2c3d4e5f60` → `DONE:<sha256>:{...response...}`, and `TTL` on it → ~86400.

**C. No double posting (consumer layer)** — in Kafka UI, open `payment.authorized`, find a message, and use **Produce Message** to send the same key + value again. The ledger logs `Skipping duplicate event ...` and the entries endpoint still shows exactly 2 entries.

**D. Retries with exponential backoff + dead-letter topic**
```bash
# 1. make the ledger fail for one merchant
curl -X PUT http://localhost:8082/api/v1/admin/chaos -H "Content-Type: application/json" -d '{"failMerchantId":"MER-DLT-DEMO"}'

# 2. watch the ledger logs in a second terminal
docker compose logs -f ledger-service

# 3. send a payment for that merchant
curl -X POST http://localhost:8081/api/v1/payments -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen || cat /proc/sys/kernel/random/uuid)" \
  -d '{"merchantId":"MER-DLT-DEMO","cardNumber":"4242424242424242","expiryMonth":12,"expiryYear":2028,"amount":77.00,"currency":"INR"}'
```
In the logs you will see the attempts 1 s, 2 s and 4 s apart:
```
WARN ... Delivery attempt 1 failed for topic=payment.authorized partition=1 offset=12 ...: SimulatedLedgerFailureException ...
WARN ... Delivery attempt 2 failed ...      (+1 s)
WARN ... Delivery attempt 3 failed ...      (+2 s)
WARN ... Delivery attempt 4 failed ...      (+4 s)  -> published to payment.authorized.DLT
```
and in `docker compose logs notification-service`: `ERROR ... DEAD LETTER stored id=... topic=payment.authorized.DLT ...`.
Then:
```bash
curl http://localhost:8083/api/v1/admin/failed-events              # the failed message, original topic, consumer group, error
curl http://localhost:8081/api/v1/payments/<paymentId>             # stays AUTHORIZED
curl -X PUT http://localhost:8082/api/v1/admin/chaos -H "Content-Type: application/json" -d '{"failMerchantId":null}'
curl -X POST http://localhost:8083/api/v1/admin/failed-events/<failedEventId>/replay
curl http://localhost:8081/api/v1/payments/<paymentId>             # now SETTLED
```
(You can also start the ledger with chaos already on by setting `LEDGER_CHAOS_FAIL_MERCHANT_ID` in `docker-compose.yml`.)

**E. Issuer rules** — card `4000000000000002` → DECLINED `INSUFFICIENT_FUNDS`; amount `60000.00` → `LIMIT_EXCEEDED`; the same card 6 times within 60 s → the 6th is `VELOCITY_CHECK_FAILED`. (`CARD_EXPIRED` is unit-tested; via the API an expired card is already rejected with 400 by validation.)

**F. Ledger always balances**
```bash
curl http://localhost:8082/api/v1/ledger/trial-balance     # "balanced": true, totalDebits == totalCredits
```

**G. Card data never stored or logged**
```bash
docker exec -it edps-postgres psql -U postgres -d payments -c "SELECT masked_card, card_fingerprint FROM payment.payments LIMIT 3;"
docker compose logs | grep 4111111111111111      # prints nothing
```

**H. Database-per-service is enforced**
```bash
docker exec -it edps-postgres psql -U payment_svc -d payments -c "SELECT * FROM ledger.ledger_entries;"
# ERROR:  permission denied for schema ledger
```

### 5.4 Useful SQL for checking state

```bash
docker exec -it edps-postgres psql -U postgres -d payments
```
```sql
SELECT status, count(*) FROM payment.payments GROUP BY status;
SELECT id, merchant_id, masked_card, amount, status, auth_code, decline_reason FROM payment.payments ORDER BY created_at DESC LIMIT 10;
SELECT topic, sent_at IS NOT NULL AS sent, count(*) FROM payment.outbox GROUP BY 1,2;
SELECT payment_id, account, direction, amount FROM ledger.ledger_entries ORDER BY created_at DESC LIMIT 10;
SELECT type, message FROM notification.notifications ORDER BY created_at DESC LIMIT 10;
SELECT topic, consumer_group, error, replay_count FROM notification.failed_events;
SELECT count(*) FROM payment.processed_events;   -- one row per event the service has handled
\q
```

---

## 6. Run the tests, coverage gate and CI

```bash
mvn test        # unit + @WebMvcTest tests only. No Docker needed. ~1 minute.
mvn verify      # + integration tests (*IT) with Testcontainers + JaCoCo 80% coverage gate. Docker must be running.
```

* Unit tests: `*Test.java` (Surefire). Integration tests: `*IT.java` (Failsafe).
* Coverage report per module: `payment-service/target/site/jacoco/index.html` (open in a browser).
* The build **fails** if any module's line coverage is below 80% (`jacoco:check` in the root `pom.xml`).
* First `mvn verify` pulls `postgres:16-alpine`, `redis:7-alpine` and `apache/kafka:3.7.0` images — allow a few minutes.

Run a single test: `mvn -pl payment-service -am test -Dtest=AuthorizationServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Run a single IT: `mvn -pl ledger-service -am verify -Dit.test=LedgerSettlementIT -Djacoco.skip=true`
Skip ITs (no Docker): `mvn verify -DskipITs -Djacoco.skip=true`

**CI (GitHub Actions)** — `.github/workflows/ci.yml` runs `mvn -B verify` on every push and PR (GitHub runners have Docker, so Testcontainers works), then SonarCloud if `SONAR_TOKEN` is configured, and uploads the JaCoCo report as an artifact. See [section 12](#12-publishing-to-github--sonarcloud).

---

## 7. Load test with k6

1. Close heavy apps. `docker compose up -d --build` and wait until healthy.
2. Start low and increase:
   ```bash
   k6 run -e RATE=50  load-test/payment-load.js
   k6 run -e RATE=100 load-test/payment-load.js
   k6 run -e RATE=200 load-test/payment-load.js   # keep going: 300, 400, 600 ...
   ```
3. A run **passes** when: both thresholds are ✓ (errors < 1%, p99 < 500 ms), `dropped_iterations` ≈ 0, and afterwards consumer lag in Kafka UI returns to 0 and there are no stuck payments:
   ```bash
   docker exec -i edps-postgres psql -U postgres -d payments < load-test/check-after-run.sql
   ```
4. Binary-search between the last pass and the first fail, run the final rate 3 times, keep the median, then save it:
   ```bash
   k6 run -e RATE=350 --summary-export=load-test/results.json load-test/payment-load.js
   ```
5. Read **TPS** from the `/s` figure on `http_reqs{phase:steady}` and **p99** from `http_req_duration{phase:steady}`. Round down. Fill in `load-test/RESULTS.md` and the README Performance table, with your machine specs.
6. End-to-end settlement p99: `curl -s http://localhost:8081/actuator/prometheus | grep payments_settlement_duration_seconds` (look at `quantile="0.99"`).

Tip: between runs, `docker compose down -v && docker compose up -d` gives a clean database so earlier runs don't skew results.

---

## 8. Troubleshooting — fixing issues

> **Honest note:** this project was written without being able to run Maven or Docker in the environment it was produced in (Maven Central and the Docker daemon were not reachable there). Every file was written carefully against Spring Boot 3.3 / Spring Kafka 3.2 APIs, but **run `mvn verify` once on your machine first** — if anything fails to compile or a test is flaky, the error message plus the section below will point you to the fix. Fixing that first build is also good, real experience to talk about.

### First steps for any problem
```bash
docker compose ps                         # which container is unhealthy / exited?
docker compose logs --tail=200 <service>  # read the first ERROR, not the last one
curl http://localhost:8081/actuator/health
```

### Docker / compose

| Symptom | Cause | Fix |
|---|---|---|
| `Cannot connect to the Docker daemon` / `error during connect` | Docker Desktop not running | Start Docker Desktop, wait for "Engine running" |
| `port is already allocated` (5432, 6379, 8080, 9094, 8081-8083) | Something else uses the port (local Postgres, another project) | Stop it (`netstat -ano \| findstr :5432` on Windows, `lsof -i :5432` on Mac), or change the **left** side of the port mapping in `docker-compose.yml`, e.g. `"15432:5432"` (then use that port from your machine / IntelliJ) |
| Build fails at `RUN --mount=type=cache` | BuildKit disabled / very old Docker | Update Docker Desktop, or `set DOCKER_BUILDKIT=1` (Windows) / `export DOCKER_BUILDKIT=1` |
| Build fails at `mvn package` with "Could not resolve dependencies" | No internet / corporate proxy inside Docker | Check internet; behind a proxy configure it in Docker Desktop → Settings → Resources → Proxies. Or build on the host (`mvn -DskipTests package`) and use Option B |
| A service exits with code 137 / `OOMKilled` | Docker has too little memory | Give Docker 6–8 GB RAM |
| Service stuck "starting" then "unhealthy" | It crashed at start-up — read its log | See the Spring Boot section below |
| `kafka` unhealthy | Slow first start, or stale state | `docker compose down` then `docker compose up -d`. Check `docker compose logs kafka` |
| Kafka UI shows no cluster / connection error | Kafka still starting | Wait 30 s and refresh |
| Changed `infra/postgres/init.sql` but nothing changed | The init script only runs on an **empty** volume | `docker compose down -v` then `up -d` |
| `FATAL: password authentication failed for user "payment_svc"` | Old Postgres volume from a different setup (roles never created) | `docker compose down -v` then `up -d --build` |

### Spring Boot start-up errors (in `docker compose logs <service>` or IntelliJ)

| Error contains | Meaning | Fix |
|---|---|---|
| `Connection to localhost:5432 refused` / `Connection refused` to Postgres | Postgres not up, or wrong host | Option A: wait for healthy Postgres. Option B: `docker compose up -d postgres`; check port 5432 is not taken by a local Postgres install |
| `permission denied for schema payment` | DB user doesn't own the schema (old volume) | `docker compose down -v`, start again |
| `FlywayException: Validate failed: Migration checksum mismatch` | You edited an already-applied `V*.sql` file | Never edit applied migrations — add `V4__...sql`. Locally you can reset: `docker compose down -v` |
| `Unsupported Database: PostgreSQL 16` | `flyway-database-postgresql` missing | It is in the service POMs; run `mvn clean install` / rebuild image |
| `Connection to node -1 (localhost/127.0.0.1:9094) could not be established` | Kafka not reachable | Option A: services use `kafka:9092` automatically. Option B: Kafka container must be running; host port 9094 |
| `UnknownTopicOrPartitionException` / `Topic payment.x not present` | Topics not created yet | Topics are created on start-up by each service (`KafkaAdmin`). Restart the service after Kafka is healthy |
| `Unable to connect to Redis` (payment-service) | Redis not running | `docker compose up -d redis` |
| `NoSuchBeanDefinitionException ... OutboxWriter` | `payments.outbox.enabled` missing | Keep `payments.outbox.enabled: true` in payment/ledger `application.yml` |
| `Could not resolve placeholder 'payments.card.fingerprint-secret'` | Property missing in a custom config | It has a default in `application.yml`; set `CARD_FINGERPRINT_SECRET` if you removed it |
| `Port 8081 was already in use` | Same service running twice (IntelliJ + Docker) | Stop one of them |

### Maven / IntelliJ / compile

| Symptom | Fix |
|---|---|
| `release version 17 not supported` / `invalid target release` | Maven is using an old JDK. Set `JAVA_HOME` to JDK 17+, reopen terminal, `mvn -version` |
| `cannot find symbol ... getX()` / `log` not found in IntelliJ | Lombok: enable annotation processing (Settings → Build → Compiler → Annotation Processors) and install the Lombok plugin. Then Build → Rebuild Project |
| `Could not find artifact com.payments:common` when running one module | Build from the root once: `mvn -DskipTests install` (or use `mvn -pl payment-service -am ...`) |
| IntelliJ red imports everywhere | Right-click root `pom.xml` → Maven → Reload project |
| A compile error that looks like an API mismatch | Run `mvn -e -X compile -pl <module>` and look at the exact line; most API names are standard Spring Boot 3.3. Fix that line, then re-run |

### Tests

| Symptom | Fix |
|---|---|
| `Could not find a valid Docker environment` | Docker Desktop must be running. On Windows: Docker Desktop → Settings → General → "Expose daemon on tcp://localhost:2375 without TLS" is **not** needed with recent versions; restart Docker Desktop if Testcontainers still can't find it |
| `client version 1.32 is too old. Minimum supported API version is 1.44` | Docker Engine 29+ vs Testcontainers. The root POM already passes `api.version=1.44` to the ITs. If it still happens, create `%USERPROFILE%\.docker-java.properties` (Windows) or `~/.docker-java.properties` with `api.version=1.44`, or upgrade `testcontainers.version` in the root `pom.xml` to the latest 1.21.x |
| `client version 1.44 is too new` | Very old Docker (< 25). Update Docker Desktop, or run with `-Ddocker.api.version=1.43` |
| ITs time out on first run | Images are being pulled. Run again, or pre-pull: `docker pull apache/kafka:3.7.0 postgres:16-alpine redis:7-alpine` |
| `Rule violated for bundle ...: lines covered ratio is 0.7x, but expected minimum is 0.80` | Coverage gate working as designed. Open `target/site/jacoco/index.html`, see which class is red, add tests for it. (When ITs are skipped, coverage comes from unit tests only.) |
| A timing-based IT is flaky on a slow laptop | Increase the `atMost(...)` durations in that IT (they are generous already). Give Docker more CPU/RAM |
| Testcontainers on Mac with Colima / Podman | Set `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` as in the Testcontainers docs |

### Runtime behaviour

| Symptom | Cause / Fix |
|---|---|
| Payment stays `RECEIVED` | The authorizer is not consuming. Check `docker compose logs payment-service` for Kafka errors. Check Kafka UI → Consumers → `payment-authorizer` lag. Check unsent outbox rows: `SELECT count(*) FROM payment.outbox WHERE sent_at IS NULL;` |
| Payment stays `AUTHORIZED` | Ledger failed (see `failed_events`), chaos mode is on (`GET :8082/api/v1/admin/chaos`), or ledger-service is down. When it comes back it catches up automatically — that's the point of Kafka |
| `409 IDEMPOTENCY_IN_PROGRESS` | You sent the same key while the first request was still running. Wait and retry |
| `422 IDEMPOTENCY_KEY_REUSED` | Same key, different body. Use a new UUID for a new payment |
| `400 INVALID_IDEMPOTENCY_KEY` | The key must be a UUID |
| `400 VALIDATION_FAILED field=expiryYear` | Card is expired (month/year in the past) |
| `503 SERVICE_UNAVAILABLE` | Redis or Postgres unreachable. Retry with the same key once it's back |
| Velocity rule declines your manual tests | More than 5 payments on the same card within 60 s. Wait a minute, use another card (e.g. `5555555555554444`, `4242424242424242`, `378282246310005`), or `docker exec edps-redis redis-cli FLUSHALL` |
| GET shows old status for a moment | Shouldn't happen (cache is evicted after commit). If you changed DB rows by hand, flush Redis: `docker exec edps-redis redis-cli FLUSHALL` |
| Messages on a DLT | `GET :8083/api/v1/admin/failed-events` shows the error and the consumer group. Fix the cause, then replay |

---

## 9. Understand everything that happens

### 9.1 The life of one payment (with the exact classes)

```
Client ──POST /api/v1/payments──▶ PaymentController
                                  │  @Valid PaymentRequest (Luhn @ValidCard, @NotExpired, BigDecimal limits, currency)
                                  ▼
                               PaymentFacade
                                  │  key must be a UUID; hash = SHA-256(canonical body)
                                  ▼
                               IdempotencyService ── Redis: SET idem:{key} IN_PROGRESS:{hash} NX EX 86400
                                  │ won the key
                                  ▼
                               PaymentService.create()   @Transactional ───────────────┐
                                  │  INSERT payment.payments (status RECEIVED, ****1111) │ one DB
                                  │  INSERT payment.outbox (payment.initiated event)     │ transaction
                                  ▼                                                      ┘
                               IdempotencyService stores DONE:{hash}:{response}
Client ◀── 202 Accepted {paymentId, status: RECEIVED} ──┘

OutboxPublisher (every 500 ms) ── SELECT … FOR UPDATE SKIP LOCKED → kafka send (key=paymentId) → mark sent
        │
        ▼  topic payment.initiated
PaymentInitiatedConsumer (group payment-authorizer) → AuthorizationProcessor.process()  @Transactional
        │  processed_events INSERT … ON CONFLICT DO NOTHING (skip duplicates)
        │  AuthorizationService: ExpiredCardRule → InsufficientFundsRule → AmountLimitRule → VelocityRule (Redis INCR)
        │  payment.authorize(authCode) or payment.decline(reason)   (PaymentStatus.transitionTo guards the move)
        │  outbox row: payment.authorized or payment.declined
        │  evict Redis status cache AFTER commit
        ▼
   ┌─────────── payment.authorized ───────────┐            payment.declined
   ▼                                          ▼                   ▼
AuthorizedPaymentConsumer (ledger-service)  PaymentOutcomeConsumer (notification-service)
LedgerService.settle() @Transactional        NotificationService.record() → notifications row
  processed_events (dedupe)
  DEBIT  CUSTOMER_CLEARING   2499.00
  CREDIT MERCHANT:MER-1001   2499.00
  outbox row: payment.settled
        ▼  topic payment.settled
   ┌────────────────┴───────────────┐
   ▼                                ▼
PaymentSettledConsumer            PaymentOutcomeConsumer → "settled" notification
SettlementProcessor: SETTLED + metric payments.settlement.duration
```

### 9.2 Why each piece exists

| Piece | Problem it solves |
|---|---|
| **Kafka between services** | Services are decoupled: the ledger can be down and catch up later; new consumers (notifications) are added without changing producers; events can be replayed |
| **paymentId as message key** | Same key → same partition → all events of one payment are processed in order |
| **3 partitions + `concurrency: 3`** | Up to 3 payments processed in parallel per consumer group; scale further by adding partitions and instances |
| **`acks=all`, `enable.idempotence=true`** | The broker never loses an acknowledged event and producer retries don't create duplicates on the topic |
| **Redis idempotency key** | A client retry (timeout, double click) never creates a second payment |
| **UNIQUE(idempotency_key)** | Backstop if Redis loses the key; `PaymentFacade` catches the violation and returns the existing payment |
| **processed_events per service** | Kafka is at-least-once; a redelivered event must not be processed twice. Same transaction as the business write, so both commit or neither does |
| **Transactional outbox** | "Save to DB" and "send to Kafka" can't be one transaction. Writing the event to a table in the same transaction and publishing it afterwards means an event is never lost and never sent for a rolled-back payment |
| **`PaymentStatus.canMoveTo`** | A late or duplicate event can never move a SETTLED payment backwards |
| **`@Version`** | Two concurrent updates can't silently overwrite each other (optimistic locking) |
| **Exponential backoff (1s, 2s, 4s)** | Temporary problems (DB blip) often fix themselves; growing waits avoid hammering a struggling dependency |
| **DLT** | A message that keeps failing must not block its partition forever. It's parked, stored in `failed_events`, and can be replayed after a fix |
| **Not-retryable exceptions** | Bad data / illegal state transitions will fail every time — send them straight to the DLT |
| **ErrorHandlingDeserializer** | A non-JSON "poison pill" doesn't crash the consumer loop; it goes to the DLT |
| **Double-entry ledger** | Every money movement has equal debits and credits; `trial-balance` proves the books balance |
| **Schema + DB user per service** | Database-per-service is enforced by PostgreSQL permissions |
| **Card masking + HMAC fingerprint** | PCI-DSS mindset: no full PAN at rest or in logs; velocity can still recognise "the same card" |

### 9.3 Delivery guarantees, in one paragraph (interview answer)
Kafka gives at-least-once delivery. The producer side is made safe with `acks=all` + idempotent producer, and the DB→Kafka hop with the transactional outbox (at-least-once: an event may be re-sent after a crash, never lost). Every consumer de-duplicates on `eventId` inside the same transaction as its business write, so the *effect* of each event happens exactly once. Ordering per payment is guaranteed by keying on `paymentId`.

### 9.4 Interview questions this project answers

| Question | Answer pointers (and where to show it) |
|---|---|
| Why Kafka instead of REST between services? | Decoupling, ledger can be down and catch up, replay, new consumers without producer changes (`docker compose stop ledger-service`, send payments, start it, watch them settle) |
| How do you guarantee no double charge? | Redis SET NX key + DB unique constraint + `processed_events` in the same transaction (`IdempotencyService`, `PaymentFacade`, `EventDeduplicator`) |
| What delivery guarantee does Kafka give here? | At-least-once; idempotent producer; outbox; consumer de-dup → effectively exactly-once (9.3) |
| Why paymentId as the key? | Ordering per payment on one partition |
| What if the ledger DB is down? | Retries 1s/2s/4s, then DLT, stored in `failed_events`, replay endpoint (chaos demo) |
| Which errors don't you retry? | Validation, illegal state, deserialization (`KafkaErrorHandlers`) |
| What is the dual-write problem? | DB + Kafka can't be one transaction → outbox (`OutboxWriter`, `OutboxPublisher`) |
| Why Postgres for the ledger and Redis for idempotency? | ACID + constraints for money; atomic SET NX + TTL for keys; Redis never the source of truth |
| How did you measure TPS/p99? | k6 constant-arrival-rate, warm-up, step up, median of 3, machine specs (section 7) |
| How would you scale it? | More partitions + instances in the same consumer group; outbox publisher already safe for multiple instances (SKIP LOCKED); Redis cluster; read replicas; the bottleneck you saw in the load test |
| How do you protect card data? | Last 4 only, HMAC fingerprint, log masking converter, BigDecimal, secrets via env vars, PCI-DSS awareness |
| SOLID? | `AuthorizationRule` interface, one class per rule, list injected (open/closed); thin listeners (single responsibility); `VelocityCounter` interface (dependency inversion) |
| Why evict the cache after commit? | Evicting before commit lets a concurrent read re-cache the old status (`PaymentStatusCache`) |
| What breaks if Redis is flushed? | Nothing important: keys/velocity reset, cache misses go to Postgres, unique constraint still blocks duplicates |

---

## 10. Code map — where each feature lives

```
event-driven-payment-system/
├── pom.xml                         parent: Java 17, Boot 3.3.5, Surefire/Failsafe, JaCoCo 80% gate, Sonar props
├── common/                         shared library (auto-configured into every service)
│   ├── events/PaymentEvent         the one event envelope (record) + EventType -> topic
│   ├── domain/PaymentStatus        state machine (canMoveTo / transitionTo) + IllegalStateTransitionException
│   ├── kafka/Topics                topic names, DLT naming
│   ├── kafka/KafkaErrorHandlers    exponential backoff + DLT recoverer + not-retryable exceptions
│   ├── kafka/KafkaSerializers      byte[] / String / JSON value serializer
│   ├── dedup/EventDeduplicator     processed_events INSERT ... ON CONFLICT DO NOTHING
│   ├── outbox/OutboxWriter|Publisher  transactional outbox
│   ├── util/CardMasker, logging/CardMaskingConverter   PAN masking in data and logs
│   ├── web/BaseApiExceptionHandler, ApiError          consistent error JSON
│   └── config/PaymentsCommonAutoConfiguration         creates 8 topics + wires all of the above
├── payment-service/  (8081)
│   ├── api/PaymentController, GlobalExceptionHandler
│   ├── dto/PaymentRequest (validation), PaymentResponse
│   ├── validation/LuhnValidator (@ValidCard), NotExpiredValidator (@NotExpired)
│   ├── idempotency/IdempotencyService (Redis), RequestHasher
│   ├── service/PaymentFacade, PaymentService, PaymentQueryService (@Cacheable), PaymentStatusCache, CardFingerprinter
│   ├── authorization/AuthorizationService + rules/ (Expired, InsufficientFunds, AmountLimit, Velocity) + RedisVelocityCounter
│   ├── messaging/PaymentInitiatedConsumer → AuthorizationProcessor; PaymentSettledConsumer → SettlementProcessor
│   ├── domain/Payment (@Version), PaymentRepository
│   └── resources/db/migration/V1..V3 (payments, processed_events, outbox)
├── ledger-service/   (8082)
│   ├── messaging/AuthorizedPaymentConsumer → service/LedgerService.settle()
│   ├── domain/LedgerEntry, Accounts, LedgerQueries (balances, trial balance)
│   ├── chaos/ChaosSettings + api/ChaosController (DLT demo)
│   └── resources/db/migration/V1..V3 (ledger_entries, processed_events, outbox)
├── notification-service/ (8083)
│   ├── messaging/PaymentOutcomeConsumer → NotificationService
│   ├── messaging/DeadLetterMonitor (topicPattern .*\.DLT) → FailedEventService (store + replay)
│   ├── config/AppConfig (String consumer factory for DLTs)
│   └── resources/db/migration/V1..V3 (notifications, processed_events, failed_events)
├── docker-compose.yml, Dockerfile, infra/postgres/init.sql
├── postman/                       collection + environment
├── scripts/smoke-test.sh          end-to-end smoke test
├── load-test/                     k6 script, post-run SQL check, RESULTS.md template
├── docs/architecture.png
└── .github/workflows/ci.yml
```

Tests mirror the same packages under `src/test/java` (`*Test` = unit/slice, `*IT` = Testcontainers).

---

## 11. Useful commands cheat sheet

```bash
# lifecycle
docker compose up -d --build
docker compose ps
docker compose logs -f payment-service ledger-service notification-service
docker compose restart ledger-service
docker compose down -v                     # full reset

# Kafka CLI inside the container
docker exec -it edps-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec -it edps-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic payment.authorized
docker exec -it edps-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups
docker exec -it edps-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic payment.settled --from-beginning --property print.key=true

# Redis
docker exec -it edps-redis redis-cli KEYS 'idem:*'
docker exec -it edps-redis redis-cli KEYS 'velocity:*'
docker exec -it edps-redis redis-cli FLUSHALL

# Postgres
docker exec -it edps-postgres psql -U postgres -d payments

# build / test
mvn -DskipTests install
mvn test
mvn verify
mvn -pl payment-service spring-boot:run

# resilience demo: stop the ledger, send payments (they stay AUTHORIZED), start it -> they settle
docker compose stop ledger-service
docker compose start ledger-service
```

---

## 12. Publishing to GitHub + SonarCloud

1. Create a **public** repo `event-driven-payment-system` on GitHub (MIT licence). Then:
   ```bash
   git init && git add . && git commit -m "Initial project structure"
   git branch -M main
   git remote add origin https://github.com/<you>/event-driven-payment-system.git
   git push -u origin main
   ```
   The guide recommends building in phases with a commit per phase — if you're learning from this code, re-type/commit it phase by phase (infrastructure → payment API → idempotency → authorizer → ledger → notifications/DLT → tests/CI/docs) so the history reflects real work you understand.
2. **SonarCloud:** sign in at sonarcloud.io with GitHub → import the repo → Administration → Analysis Method → turn **off** Automatic Analysis.
   Create a token (My Account → Security) and add it in GitHub → Settings → Secrets and variables → Actions → `SONAR_TOKEN`.
   In the root `pom.xml` set `sonar.organization` and `sonar.projectKey` to the values SonarCloud shows.
3. Replace `YOUR_GITHUB_USER` in the README badges.
4. Branch protection: Settings → Branches → add rule for `main` → require the **CI** check to pass.
5. Proof of the coverage gate: open a PR that deletes a test file, let CI fail with the JaCoCo "Rule violated" message, screenshot it, close the PR.
6. Run the k6 test on your machine and fill in `load-test/RESULTS.md`, the README Performance table, and your resume numbers — only with numbers you measured.
