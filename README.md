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

No Windows PowerShell use `.\mvnw.cmd` e garanta que `JAVA_HOME` aponta para o JDK 25
(ex.: `E:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`).

> **Nota (Docker Desktop no Windows):** os testes de integração usam **Testcontainers**.
> O cliente docker-java embutido tem um problema de transporte via *named pipe* com o
> Docker Desktop 29, que faz o `/info` retornar HTTP 400 e o Testcontainers não encontrar o
> ambiente Docker. Em **CI Linux** (socket Unix) e em ambientes com o endpoint **TCP** do Docker
> exposto, os `*IT` rodam normalmente. Localmente no Windows, rode `./mvnw test` (unitários) — os
> `*IT` são executados no pipeline.

### Subir tudo com Docker Compose

```bash
docker compose up --build
```

Sobe `postgres`, `wiremock` e `order-service`. A API fica em `http://localhost:8080`.

## Endpoints (Fase 1 — walking skeleton)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/v1/orders` | Cria um pedido (`{ "customerId": "..." }`) → `201` |
| `GET`  | `/api/v1/orders/{orderId}` | Retorna um pedido → `200`/`404` |
| `GET`  | `/actuator/health` | Health check |

> O roadmap completo (estados, itens, pagamento, segurança, observabilidade, testes, CI) está em
> implementação por fases.

## Estrutura

```
order-service/      # serviço (Clean Architecture: domain / application / infrastructure)
wiremock/mappings/  # stubs dos serviços externos (a partir da Fase 5)
docker-compose.yml  # orquestração local
docs/               # architecture.md (decisões de design)
```
