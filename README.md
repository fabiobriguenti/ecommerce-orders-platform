# Plataforma de Pedidos — `order-service`

Backend de pedidos de e-commerce. Apenas o **`order-service`** é implementado; os demais serviços
(Customer, Catalog, Payment Gateway, Notification) são simulados por **WireMock** standalone e
consumidos por HTTP real. Veja o desafio em [`desafio.md`](desafio.md) e as decisões de arquitetura
(em construção) em `docs/architecture.md`.

## Stack

- Java 25 + Spring Boot 3.5 (WebFlux, reativo)
- R2DBC + PostgreSQL, migrations com Flyway (via JDBC)
- Maven (via Maven Wrapper — **não precisa de Maven instalado**)

## Pré-requisitos

- **JDK 25** (Temurin 25 recomendado) — para build/execução local.
- **Docker + Docker Compose** — para subir Postgres, WireMock e (fases seguintes) a stack de
  observabilidade, além dos testes de integração com Testcontainers.

## Como rodar

### Build e testes (não requer Docker)

```bash
cd order-service
./mvnw clean test          # unit tests (no Docker)
./mvnw clean verify        # unit + integration tests (Testcontainers, needs Docker)
```

No Windows PowerShell use `.\mvnw.cmd` e garanta que `JAVA_HOME` aponta para o JDK 25.

> **Nota (Docker Desktop no Windows):** os testes de integração usam **Testcontainers**.
> Localmente no Windows, rode `./mvnw test` (unitários) — os `*IT` são executados no pipeline.

### Subir tudo com Docker Compose

```bash
docker compose up --build
```

Sobe `postgres`, `wiremock`, `order-service` e a stack de observabilidade
(`jaeger`, `prometheus`, `grafana`). A API fica em `http://localhost:8080`.

## API

Todos os endpoints de negócio ficam sob `/api/v1` (orders + payments) e seguem **RFC 7807** para
erros. A documentação interativa (**OpenAPI 3.1**) está em **`/swagger-ui.html`**; o JSON em
`/v3/api-docs`. Endpoints de mutação (`POST`/`DELETE`) aceitam o header **`Idempotency-Key`**.

## Segurança e autenticação

O serviço é um **OAuth2 Resource Server** (stateless): cada requisição precisa de um **Bearer JWT**
assinado em RSA, validado pela chave pública em `order-service/src/main/resources/keys/public.pem`.
A autorização é por **scope**:

| Scope | Libera |
|-------|--------|
| `orders:read`     | `GET /api/v1/orders/**` |
| `orders:write`    | `POST`/`DELETE /api/v1/orders/**` |
| `payments:read`   | `GET /api/v1/payments/**` |
| `payments:write`  | `POST /api/v1/payments/**` |

Rotas públicas: health/metrics, OpenAPI/Swagger e o endpoint de token abaixo.

### Obter um token (dev)

> ⚠️ As chaves em `resources/keys/` são um par RSA **descartável, apenas para desenvolvimento/avaliação**
> (o desafio permite auth mockada). O endpoint de emissão é desabilitável com
> `DEV_TOKEN_ENABLED=false` e **não deve** ser usado em produção. Em produção, troque a chave pública
> pela do seu IdP e remova a privada.

Parâmetros são query params (sem body): `scope` (repetível), `subject`, `ttl` (segundos).

```bash
# Token com todos os scopes (default), TTL 1h:
curl -s -X POST http://localhost:8080/api/v1/auth/token | jq -r .accessToken

# Token com scopes específicos:
curl -s -X POST 'http://localhost:8080/api/v1/auth/token?subject=qa&scope=orders:read&scope=orders:write'

# Usar o token:
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/orders/<id>
```

No Swagger UI, clique em **Authorize** e cole o token.

Outros controles (OWASP): validação de input (Bean Validation), **rate limiting** por IP
(`app.rate-limit.*`, 120 req/min por padrão, resposta `429` RFC 7807) e **security headers**
(CSP, `Referrer-Policy`, `X-Frame-Options: DENY`, HSTS).

## Observabilidade

- **Logs**: JSON estruturado (ECS) no console. Cada requisição gera uma linha de *access log*
  com `correlationId` (header `X-Correlation-Id` recebido ou UUID gerado, ecoado na resposta) e,
  quando dentro do escopo do trace, `traceId`/`spanId`.
- **Métricas**: Micrometer → **Prometheus** em `/actuator/prometheus` (`http_server_requests`,
  JVM, etc.).
- **Tracing**: **OpenTelemetry** (Micrometer Tracing + OTLP) exportado ao **Jaeger**; o `traceId`
  é o id de correlação propagado entre serviços via `traceparent` (W3C), inclusive nas chamadas
  HTTP ao WireMock.

| UI | URL |
|----|-----|
| Jaeger (traces)     | http://localhost:16686 |
| Prometheus (métricas) | http://localhost:9090 |
| Grafana             | http://localhost:3000 (login anônimo; datasources Prometheus + Jaeger provisionados) |

Sampling de trace: `TRACING_SAMPLING` (1.0 por padrão). Endpoint OTLP: `MANAGEMENT_OTLP_TRACING_ENDPOINT`.

> O roadmap restante (testes, CI) segue em implementação por fases.

## Estrutura

```
order-service/      # serviço (Clean Architecture: domain / application / infrastructure)
wiremock/mappings/  # stubs dos serviços externos (a partir da Fase 5)
docker-compose.yml  # orquestração local
docs/               # architecture.md (decisões de design)
```
