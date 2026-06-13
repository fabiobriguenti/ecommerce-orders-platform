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
- **JDK 25** — apenas se for buildar/testar localmente fora do Docker (`JAVA_HOME`
  apontando para ele). O Maven vem pelo wrapper, não precisa instalar.

## Como rodar

### 1. Subir tudo com Docker Compose (autocontido)

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

### Testes de integração (`*IT`)

Todos os `*IT` rodam no `verify` (Failsafe) via **Testcontainers**, reutilizando os mesmos
`wiremock/mappings/`. São duas camadas:

- **Slices de adaptador** (`infrastructure/**/*IT`) — persistência contra **Postgres real** e os HTTP
  clients contra **WireMock real**, cada camada isolada e rápida.
- **Aceitação ponta a ponta** (`acceptance/*IT`) — `@SpringBootTest` em porta aleatória exercitando o
  *stack* inteiro (HTTP → segurança JWT → use cases → R2DBC/Postgres → gateway/catálogo no WireMock):
  ciclo de vida do pedido e congelamento de preço na confirmação, idempotência (`Idempotency-Key`),
  auto-cancelamento após 3 rejeições, *circuit breaker* no gateway 503, "um pedido ativo por cliente",
  concorrência (*optimistic locking* → 409) e autorização por *scope*.

Os dois contêineres são **singletons compartilhados** (`support/TestContainers`): um único Postgres e
um único WireMock por execução, e — graças ao *cache* de contexto do Spring — um único
`ApplicationContext` para toda a suíte de aceitação. Para iteração local mais rápida, os contêineres
têm `withReuse(true)`: habilite o reuso entre execuções com
`echo testcontainers.reuse.enable=true >> ~/.testcontainers.properties` (opt-in por máquina; é inócuo
em CI, onde o arquivo não existe).

Para rodar tudo localmente basta **Docker instalado** e `cd order-service && ./mvnw verify` — o mesmo
comando do CI.

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
| Grafana             | http://localhost:3000 (login anônimo; datasources + dashboards provisionados) |

Sampling de trace: `TRACING_SAMPLING` (1.0 por padrão). Endpoint OTLP: `MANAGEMENT_OTLP_TRACING_ENDPOINT`.

### Dashboards (as code)

Datasources **e** dashboards são **provisionados a partir do repositório** — quem clonar e der
`docker compose up` já encontra tudo pronto em **Grafana → pasta _Order Service_** (sem importar nada):

```
observability/grafana/
├── provisioning/datasources/datasources.yml   # Prometheus (uid: prometheus) + Jaeger (uid: jaeger)
├── provisioning/dashboards/dashboards.yml      # provider: carrega os *.json abaixo no startup
└── dashboards/order-service.json               # dashboard versionado (HTTP, latência p95, 5xx, JVM, circuit breaker)
```

Para **criar/editar**: monte na UI do Grafana (Admin anônimo já habilitado), depois
**Dashboard settings → JSON Model**, copie e salve como um `*.json` em `observability/grafana/dashboards/`.
Referencie o datasource pelo **uid** (`prometheus`) para o JSON ser portável em qualquer clone. Como
`allowUiUpdates: true`, dá para iterar na UI; só exporte o JSON para versionar a mudança.

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
wiremock/mappings/        # stubs dos serviços externos (Customer, Catalog, Payment, Notification)
observability/            # prometheus.yml + provisioning do Grafana
docker-compose.yml        # orquestração local (app + DB + WireMock + observabilidade)
.github/workflows/ci.yml  # pipeline: build/test/cobertura/mutation + scan Trivy
docs/                     # architecture.md (decisões de design)
```
