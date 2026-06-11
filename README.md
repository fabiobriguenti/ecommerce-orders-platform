# Plataforma de Pedidos — `order-service`

Backend de pedidos de e-commerce. Apenas o **`order-service`** é implementado; os demais serviços
(Customer, Catalog, Payment Gateway, Notification) são simulados por **WireMock** standalone e
consumidos por HTTP real. Veja o desafio em [`desafio.md`](desafio.md) e as decisões de arquitetura
em [`docs/architecture.md`](docs/architecture.md).

## Stack

- Java 25 + Spring Boot 3.5 (WebFlux, reativo)
- R2DBC + PostgreSQL, migrations com Flyway (via JDBC)
- Maven (via Maven Wrapper — **não precisa de Maven instalado**)

## Pré-requisitos

- **Docker + Docker Compose** — única dependência para subir e exercitar a aplicação completa.
- **JDK 25** (Temurin 25) — apenas se for buildar/testar localmente fora do Docker (`JAVA_HOME`
  apontando para ele). O Maven vem pelo wrapper, não precisa instalar.

## Como rodar

### 1. Subir tudo com Docker Compose (recomendado — autocontido)

```bash
docker compose up --build
```

Sobe `order-service` + `postgres` + `wiremock` + observabilidade (`jaeger`, `prometheus`, `grafana`).
Só precisa de Docker — nada mais.

| Ponto de entrada | URL |
|------------------|-----|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

**Teste rápido** (fluxo mínimo ponta a ponta; `cust-active` e `prod-available` já existem nos
mappings do WireMock):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token | jq -r .accessToken)

OID=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-active"}' | jq -r .id)

curl -s -X POST "http://localhost:8080/api/v1/orders/$OID/items" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productId":"prod-available","quantity":2}'

curl -s -X POST "http://localhost:8080/api/v1/orders/$OID/confirm" -H "Authorization: Bearer $TOKEN"
```

Sem `curl`/`jq`? Faça o mesmo fluxo pelo **Swagger UI** (botão *Authorize* com o token).

### 2. Build e testes locais (sem Docker)

```bash
cd order-service
./mvnw test                                       # unitários + web-slice + gate de cobertura JaCoCo
./mvnw org.pitest:pitest-maven:mutationCoverage   # mutation testing (domínio)
```

No Windows PowerShell use `.\mvnw.cmd` e garanta `JAVA_HOME` no JDK 25. Relatórios em
`target/site/jacoco/index.html` e `target/pit-reports/index.html`. **Estes comandos não precisam de
Docker e rodam de forma autocontida.**

| Gate de qualidade | Ferramenta | Mínimo | Comando |
|-------------------|------------|--------|---------|
| Cobertura de linha (domínio) | JaCoCo (`check`) | **80%** | `./mvnw test` |
| Mutation score (domínio) | Pitest | **75%** (MSI atual ~81%) | `mutationCoverage` |

### Testes de integração (`*IT`) — rodam no CI

Os `*IT` exercitam os adaptadores contra **Postgres real e WireMock real** via **Testcontainers**,
reutilizando os mesmos `wiremock/mappings/`. O lar deles é o **pipeline de CI (Linux)**, onde
`./mvnw verify` executa a suíte completa (veja [CI/CD](#cicd)).

> Não estão no fluxo local padrão de propósito: o Testcontainers precisa de um daemon Docker que o
> seu cliente consiga acessar, e alguns Docker Desktop no Windows expõem a API por *named pipe* de um
> jeito que o cliente (docker-java) não negocia (`/info` → HTTP 400), fazendo a detecção do ambiente
> falhar. Em Docker compatível (CI Linux, WSL2, Colima/Rancher Desktop, ou daemon TCP), `./mvnw verify`
> roda tudo, incluindo os `*IT`, sem nenhuma alteração no projeto.

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
