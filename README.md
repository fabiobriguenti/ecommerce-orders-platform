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
./mvnw clean test          # unit + web-slice tests + JaCoCo (no Docker)
./mvnw clean verify        # + integration tests (Testcontainers: DB + WireMock, needs Docker)
./mvnw org.pitest:pitest-maven:mutationCoverage   # mutation testing (domain)
```

No Windows PowerShell use `.\mvnw.cmd` e garanta que `JAVA_HOME` aponta para o JDK 25.

### Quality gates

| Gate | Ferramenta | Mínimo | Onde |
|------|------------|--------|------|
| Cobertura de linha (domínio) | JaCoCo (`check`) | **80%** | fase `test` |
| Mutation score (domínio) | Pitest | **75%** (MSI atual ~81%) | `mutationCoverage` |

Camadas de teste: **unitários** de domínio/aplicação; **web-slice** (`@WebFluxTest`) cobrindo
controllers, autorização por scope e RFC 7807 sem Docker; **integração** (`*IT`) contra Postgres e
WireMock reais via **Testcontainers**, reutilizando os mesmos `wiremock/mappings/`. Relatórios:
`target/site/jacoco/index.html` e `target/pit-reports/index.html`.

> Os `*IT` precisam de Docker. No Docker Desktop do Windows o Testcontainers pode não achar o
> ambiente (transporte *named pipe*); rode `./mvnw test` localmente — os `*IT` rodam no CI (Linux).

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

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) em cada push/PR para `main`:

1. **build-test** — `mvnw verify` (compila → testes unitários + **gate JaCoCo** de cobertura do
   domínio → testes de integração com Testcontainers) e **Pitest** (mutation, gate MSI). Relatórios
   publicados como artefato.
2. **security-scan** — build da imagem Docker e **scan de vulnerabilidades com Trivy**: findings
   HIGH/CRITICAL vão para a aba *Security* (SARIF) e o build **falha em CRITICAL** *fixable*
   (`ignore-unfixed`).

> O gate Trivy já pegou um caso real: `CVE-2026-22732` (CRITICAL) em `spring-security-web` 6.5.1 —
> corrigido fixando a versão do Spring Security em 6.5.9 no `pom.xml`.

## Estrutura

```
order-service/            # serviço (Clean Architecture: domain / application / infrastructure)
wiremock/mappings/        # stubs dos serviços externos (a partir da Fase 5)
observability/            # prometheus.yml + provisioning do Grafana
docker-compose.yml        # orquestração local (app + DB + WireMock + observabilidade)
.github/workflows/ci.yml  # pipeline: build/test/cobertura/mutation + scan Trivy
docs/                     # architecture.md (decisões de design)
```
