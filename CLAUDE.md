# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

`order-service` is **implemented and green**: build, unit/slice/integration/acceptance tests, the JaCoCo (≥80%) and Pitest (≥75% MSI) gates, the GitHub Actions pipeline, and the Docker Compose stack all pass. `desafio.md` remains the authoritative source of requirements (in Portuguese) — treat its **Regras de Negócio** (business rules) section as the primary acceptance criteria, and the **Non-negotiable constraints** below as the cross-cutting checklist to verify any change against.

Deadline: **2026-06-19**. Delivery is a public GitHub repo.

## What is being built

The backend for an e-commerce order platform, decomposed into microservices — but **only `order-service` is implemented**. All other services (Customer, Catalog, Payment Gateway, Notification) are simulated by a standalone **WireMock** server. The order service calls them over real HTTP; **no stub or mock logic may live in production code** — service isolation is WireMock's job exclusively.

Identifying which microservices exist and their responsibilities is part of the challenge and must be documented in `docs/architecture.md` (bounded contexts, API/event contracts, design trade-offs). The WireMock mappings are evidence of that domain analysis.

## Repository layout

```
docs/architecture.md        # domain decomposition, bounded contexts, ADRs
order-service/              # the only real service; src/ + Dockerfile
wiremock/mappings/          # JSON stub mappings for simulated services
docker-compose.yml          # all services + DB + observability + WireMock
README.md                   # local run instructions (docker compose up)
.github/workflows/ci.yml    # build → unit → integration → mutation → vuln scan (Trivy)
```

## Stack

Implemented in **Java 25 / Spring Boot 3.5 (WebFlux, reactive)**, **R2DBC + PostgreSQL**, **Flyway** migrations (run over JDBC), built with **Maven** via the wrapper (no local Maven needed). Testcontainers **2.x** drives the `*IT` suite. (The spec also permits Kotlin/Python/Go — see the table in `desafio.md`.) Mandatory and in place: Clean/Hexagonal Architecture, DDD, idempotency, observability, security.

Build/test/run — from `order-service/` (use `.\mvnw.cmd` on Windows; native commands need `JAVA_HOME` on JDK 25):
- `./mvnw test` — unit + web-slice tests + JaCoCo coverage gate. **No Docker.**
- `./mvnw verify` — the above plus the Testcontainers integration + acceptance `*IT` (Postgres + WireMock). **Needs Docker** (any modern engine; Testcontainers 2.x negotiates current Docker APIs).
- `./mvnw org.pitest:pitest-maven:mutationCoverage` — domain mutation gate (Pitest).
- `docker compose up --build` — from the repo root; full stack (app + Postgres + WireMock + observability).

`order-service` uses **Clean Architecture / Hexagonal** with strict layering:
- **Domain** — entities, value objects, business rules, ports
- **Application** — use cases
- **Infrastructure** — adapters: HTTP in, persistence, HTTP clients to external services

## Non-negotiable constraints (verify any change against these)

These are the most error-prone rules from the spec — they cut across many files and are the main grading criteria.

**Order lifecycle / state machine** (you design the states & transitions):
- An order always carries a customer id; customer must be **active and existing** (validated via HTTP to the Customer WireMock, not locally).
- Confirmation requires **≥1 item**. A confirmed order cannot have items added/removed. A cancelled order cannot be modified at all.
- Order total is computed from the product price **at confirmation time**, not at item-add time.
- An order can be cancelled only while payment is **not yet approved**.
- At most **one active order per customer** at any time.

**Items:**
- Item added only if product exists and is available (validated via Catalog WireMock).
- Quantity > 0. Adding the same product again **increments quantity** (no duplicate line). Removing a non-existent item is an error.

**Payment:**
- Only a **confirmed** order can start payment; starting it twice is a no-op (idempotent).
- Rejected payment returns the order to a retryable state; after **3 rejections** the order is auto-cancelled.
- The payment callback/webhook must be idempotent — reprocessing the same event has no side effects.

**Cross-cutting:**
- Concurrent requests on the same order must be handled correctly — pick and **justify** a concurrency strategy (optimistic/pessimistic locking) in `docs/architecture.md`.
- Confirm and payment operations must be idempotent.

## API requirements

- Versioned routes (e.g. `/api/v1/...`).
- Error responses follow **RFC 7807 (Problem Details)**.
- Mutating endpoints (`POST`/`DELETE`) accept an `Idempotency-Key` header.
- Documented via **OpenAPI 3.1**, Swagger UI at `/swagger-ui.html`.
- Required endpoints are listed in `desafio.md` (orders + payments). Endpoint names may be adapted to the domain, but semantics must be preserved.

## Quality gates (graded with rigor)

- Domain unit tests, **≥80% coverage**.
- Integration tests against a real DB via **Testcontainers**.
- HTTP-client integration tests via **WireMock through Testcontainers**, reusing the same `wiremock/mappings/` files (do not duplicate stubs).
- Mutation testing (**Pitest** or equivalent), **MSI ≥75%** on the domain module.

## Other mandatory requirements

- **Persistence:** relational DB (e.g. PostgreSQL) with versioned migrations (Flyway/Liquibase). NoSQL is optional and must be justified.
- **Observability:** structured JSON logs with a propagated `CorrelationID`; Micrometer + Prometheus metrics; OpenTelemetry tracing; docker-compose includes Prometheus + Grafana.
- **Security:** JWT (OAuth2 Bearer) with scope-based access (e.g. `orders:write`, `payments:read`); relevant OWASP Top 10 controls (input validation, rate limiting, security headers). Auth may be mocked or use local Keycloak.
- **Resilience:** the payment gateway can be unstable (503) and must not take down the platform — use a circuit breaker (Resilience4j or equivalent).

## WireMock minimum scenarios

Mappings live in `wiremock/mappings/` and load on container start. Minimum required scenarios:
- Customer: active (200), blocked (422), not found (404)
- Catalog: available with price (200), unavailable (422), not found (404)
- Payment: approved (200), rejected (200 with REJECTED status), gateway unstable (503)
- Notification: accepted (202)
