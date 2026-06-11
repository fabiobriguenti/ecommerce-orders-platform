# Projeto Final — Plataforma de Pedidos para E-commerce

## Contexto do Problema

Uma startup de marketplace precisa de um backend que permita a seus clientes realizarem compras de forma confiável, rastreável e segura. Hoje, o processo é manual e repleto de inconsistências: pedidos ficam em estados ambíguos, pagamentos são processados em duplicidade, e não há visibilidade sobre o que acontece após o cliente clicar em "Finalizar Compra".

O time de produto levantou os seguintes problemas recorrentes que precisam ser resolvidos:

- Pedidos são criados sem itens e nunca finalizados, ocupando espaço e gerando ruído nos relatórios.
- Um mesmo pagamento é processado mais de uma vez para o mesmo pedido quando o cliente clica repetidamente no botão.
- Não é possível rastrear em qual etapa um pedido está nem o motivo de ter sido cancelado.
- Pedidos de clientes inválidos (inexistentes ou bloqueados) chegam até o processamento de pagamento.
- Quando o gateway de pagamento está instável, toda a plataforma para.
- Não existe controle de concorrência: dois processos podem modificar o mesmo pedido simultaneamente, gerando inconsistências.

**Seu desafio é projetar e implementar o backend dessa plataforma**, decompondo o domínio em microsserviços, modelando as regras de negócio com rigor e entregando um sistema confiável, observável e seguro.

> **Importante:** A identificação dos microsserviços que compõem essa plataforma é parte do desafio. Você deve reconhecer quais serviços existiriam em uma arquitetura real e definir as responsabilidades de cada um. Porém, **apenas o serviço de pedidos (`order-service`) deve ser implementado**. Todos os demais serviços devem ser simulados via WireMock. Não existe uma única resposta correta — o que será avaliado é a **coerência das decisões de design** e sua justificativa.

---

## Escopo Funcional

O sistema deve suportar o fluxo completo de um pedido em um e-commerce:

1. Um cliente inicia um pedido.
2. Itens (produtos) são adicionados ao pedido.
3. O pedido é confirmado.
4. O pagamento é iniciado e processado.
5. O pedido avança ou retrocede de estado conforme o resultado do pagamento.

O foco está em **fazer um pedido, incluir itens e realizar o pagamento, manipulando os estados do pedido ao longo do ciclo de vida**. Não é necessário implementar entrega, logística, catálogo completo ou gestão de usuários — os serviços externos serão simulados por um servidor **WireMock** standalone.

---

## Regras de Negócio

Esta seção é de **fundamental importância**. A correta implementação das regras abaixo será o principal critério de avaliação.

### Clientes

- Um pedido só pode ser criado para um cliente **ativo e existente** no sistema. A validação deve ser feita via chamada HTTP ao **WireMock** que simula o serviço de clientes.
- Clientes bloqueados ou inexistentes devem ter sua solicitação rejeitada com erro apropriado.

### Pedidos

- Um pedido recém-criado deve conter, obrigatoriamente, o identificador do cliente.
- Um pedido só pode avançar para confirmação se possuir **ao menos um item**.
- O valor total do pedido deve ser calculado com base no **preço do produto no momento da confirmação**, não no momento da adição do item. Preços podem mudar.
- Um pedido **confirmado não pode ter itens adicionados ou removidos**.
- Um pedido **cancelado não pode ser modificado** em nenhuma hipótese.
- Um pedido só pode ser cancelado enquanto o pagamento **não tiver sido aprovado**.
- Não pode haver dois pedidos abertos para o mesmo cliente ao mesmo tempo (regra de negócio: um cliente possui no máximo um pedido ativo por vez).

### Itens

- Um item só pode ser adicionado a um pedido se o produto existir e estiver disponível. A disponibilidade deve ser verificada via chamada HTTP ao **WireMock** que simula o serviço de catálogo/estoque.
- A quantidade de um item deve ser maior que zero.
- O mesmo produto pode ser adicionado ao pedido apenas uma vez — se adicionado novamente, a quantidade deve ser **incrementada**, não duplicada.
- A remoção de um item que não existe no pedido deve retornar erro apropriado.

### Pagamento

- O pagamento só pode ser iniciado para um pedido **confirmado**.
- Uma vez iniciado, o pagamento não pode ser iniciado novamente para o mesmo pedido (idempotência).
- O gateway de pagamento é externo e deve ser simulado pelo **WireMock**. Os mapeamentos devem cobrir cenários de **aprovação** e **rejeição**.
- Se o pagamento for **aprovado**, o pedido avança para o próximo estado.
- Se o pagamento for **rejeitado**, o pedido retorna a um estado que permita nova tentativa de pagamento, com limite máximo de **3 tentativas**. Após 3 rejeições, o pedido é automaticamente cancelado.
- O callback de confirmação do pagamento (webhook) deve ser idempotente: processar o mesmo evento múltiplas vezes não deve gerar efeitos colaterais.

### Estados do Pedido

O pedido possui um ciclo de vida com estados bem definidos. Cabe a você modelar esses estados e as transições válidas entre eles. Leve em conta as regras descritas acima para definir o ciclo de vida completo.

> **Dica de domínio:** pense nas transições de estado como eventos de negócio (ex: `OrderConfirmed`, `PaymentApproved`, `OrderCancelled`). Quem deve publicar e consumir esses eventos?

### Concorrência e Consistência

- O sistema deve lidar corretamente com requisições concorrentes sobre o mesmo pedido. Escolha e justifique a estratégia de controle de concorrência adotada (optimistic locking, pessimistic locking ou outra abordagem).
- Operações de confirmação e pagamento devem ser **idempotentes**: realizar a mesma operação duas vezes não deve gerar efeitos duplicados.

---

## Serviços Externos (WireMock)

A plataforma de e-commerce é composta por múltiplos serviços. **Cabe a você identificar quais serviços existem, quais são suas responsabilidades e como o `order-service` se comunica com eles.** Essa análise deve estar documentada em `docs/architecture.md`.

Apenas o `order-service` deve ser implementado de verdade. Todos os demais serviços identificados devem ser simulados por um **servidor WireMock standalone**, subido via `docker-compose`. O `order-service` deve consumi-los como chamadas HTTP reais — sem qualquer lógica de stub ou mock embutida no código de produção.

A tabela abaixo apresenta os **cenários mínimos** que o WireMock deve cobrir. Se você identificar outros serviços além dos listados, inclua os respectivos mapeamentos também.

| Serviço                  | URL base sugerida (WireMock)       | Cenários mínimos obrigatórios                                                  |
|--------------------------|-------------------------------------|--------------------------------------------------------------------------------|
| **Customer Service**     | `http://wiremock:8080/customers`    | Cliente ativo (200), cliente bloqueado (422), cliente não encontrado (404)     |
| **Catalog Service**      | `http://wiremock:8080/products`     | Produto disponível com preço (200), produto indisponível (422), não encontrado (404) |
| **Payment Gateway**      | `http://wiremock:8080/payments`     | Pagamento aprovado (200), pagamento rejeitado (200 com status REJECTED), gateway instável (503) |
| **Notification Service** | `http://wiremock:8080/notifications`| Aceita notificação (202)                                                       |

### Mapeamentos WireMock

Os mapeamentos devem ser definidos como arquivos JSON no diretório `wiremock/mappings/` do repositório e carregados automaticamente na inicialização do container. Exemplo de estrutura:

```
wiremock/
├── mappings/
│   ├── customers-active.json
│   ├── customers-blocked.json
│   ├── products-available.json
│   ├── products-unavailable.json
│   ├── payments-approved.json
│   ├── payments-rejected.json
│   └── notifications.json
└── __files/
    └── (response bodies, se necessário)
```

> **Importante:** o código de produção **não deve conter** nenhum stub ou bean de mock. O isolamento dos serviços externos é responsabilidade exclusiva do WireMock. Nos testes de integração, o WireMock deve ser inicializado via **Testcontainers** apontando para os mesmos mapeamentos do diretório `wiremock/mappings/`.

---

## Arquitetura

### Decomposição do Domínio

A identificação dos microsserviços que compõem a plataforma é responsabilidade sua. Entregue um documento (`docs/architecture.md`) contendo:

- Quais serviços você identificou e qual a responsabilidade de cada um.
- Quais são os Bounded Contexts e como se relacionam.
- Como o `order-service` se comunica com os demais (contratos de API, eventos, etc.).
- Justificativa para as decisões de design (trade-offs considerados).

> O WireMock deve representar fielmente os contratos que você definiu para os serviços não implementados. Os mapeamentos são evidência da sua análise de domínio.

### Implementação

Apenas o **`order-service`** deve ser implementado. Ele deve adotar **Clean Architecture / Arquitetura Hexagonal**, com separação clara entre:

- **Domínio** (entidades, value objects, regras de negócio, portas)
- **Aplicação** (casos de uso / use cases)
- **Infraestrutura** (adaptadores: HTTP, banco de dados, clientes HTTP para os demais serviços)

---

## Endpoints Obrigatórios

A seguir, os endpoints mínimos que a API deve expor. A nomenclatura pode ser adaptada para refletir o contexto do seu domínio, mas a semântica deve ser preservada.

### Pedidos

| Método   | Endpoint                          | Descrição                                              |
|----------|-----------------------------------|--------------------------------------------------------|
| `POST`   | `/orders`                         | Cria um novo pedido para um cliente                    |
| `GET`    | `/orders/{orderId}`               | Retorna os detalhes de um pedido                       |
| `GET`    | `/orders?customerId={id}`         | Lista pedidos de um cliente                            |
| `POST`   | `/orders/{orderId}/items`         | Adiciona um item ao pedido                             |
| `DELETE` | `/orders/{orderId}/items/{itemId}`| Remove um item do pedido                               |
| `POST`   | `/orders/{orderId}/confirm`       | Confirma o pedido (idempotente)                        |
| `DELETE` | `/orders/{orderId}`               | Cancela o pedido                                       |

### Pagamentos

| Método   | Endpoint                          | Descrição                                              |
|----------|-----------------------------------|--------------------------------------------------------|
| `POST`   | `/payments`                       | Inicia o pagamento de um pedido confirmado             |
| `GET`    | `/payments/{paymentId}`           | Retorna o status de um pagamento                       |
| `POST`   | `/payments/{paymentId}/callback`  | Recebe o resultado do gateway (webhook) — idempotente  |

### Requisitos de API

- Todas as respostas de erro devem seguir o padrão **RFC 7807 (Problem Details)**.
- Os endpoints de mutação (`POST`, `DELETE`) devem suportar o header `Idempotency-Key`.
- A API deve ser documentada via **OpenAPI 3.1** (Swagger UI disponível em `/swagger-ui.html`).
- A API deve ser **versionada** (ex: `/api/v1/...`).

---

## Requisitos Técnicos

### Persistência

- Utilize um banco de dados relacional (ex: PostgreSQL) para os dados transacionais do domínio.
- O uso de banco NoSQL (ex: MongoDB) para casos específicos (ex: histórico de eventos, catálogo) é opcional, mas deve ser justificado.
- Inclua **migrations** versionadas (ex: Flyway ou Liquibase).

### Testes

A suíte de testes é requisito de entrega e será avaliada com rigor:

- **Testes unitários** das regras de negócio do domínio (cobertura mínima de 80%).
- **Testes de integração** com banco real usando **Testcontainers**.
- **Testes de integração** dos clientes HTTP dos serviços externos usando **WireMock via Testcontainers** (reaproveitando os mapeamentos de `wiremock/mappings/`).
- Mutation Testing com **Pitest** (ou equivalente): MSI mínimo de 75% no módulo de domínio.

### Observabilidade

- **Logs estruturados** em JSON com `CorrelationID` propagado entre serviços.
- **Métricas** expostas via Micrometer + Prometheus (`/actuator/metrics` ou equivalente).
- **Tracing distribuído** com OpenTelemetry (exportação para Jaeger ou console).
- Docker Compose com Prometheus + Grafana deve ser incluído para visualização local.

### Segurança

- Os endpoints devem ser protegidos com **JWT (OAuth2 / Bearer Token)**.
- Implemente controle de acesso baseado em escopo (ex: `orders:write`, `payments:read`).
- Aplique os controles do **OWASP Top 10** relevantes para a API (validação de entrada, rate limiting, headers de segurança).
- O serviço de autenticação pode ser mockado ou utilizar Keycloak local via Docker.

### Containerização e CI/CD

- Inclua um **`docker-compose.yml`** na raiz do repositório que suba todos os serviços, banco de dados, infraestrutura de observabilidade e o **servidor WireMock** com os mapeamentos em `wiremock/mappings/`.
- Configure um pipeline **GitHub Actions** que execute: build → testes unitários → testes de integração → análise de vulnerabilidades (Trivy ou equivalente).

---

## Linguagem e Frameworks

O projeto pode ser desenvolvido nas seguintes linguagens:

- **Java** (recomendado: Java 21+)
- **Kotlin**
- **Python**
- **Go**

Durante o curso, utilizamos **Java + Spring Boot** como referência, pois oferece um ecossistema maduro para os requisitos deste projeto (Spring Data, Spring Security, Spring WebFlux, Resilience4j, Micrometer, etc.). Caso opte por outra linguagem, utilize frameworks compatíveis e com suporte equivalente às funcionalidades exigidas:

| Requisito               | Java/Kotlin (referência)       | Python (alternativa)         | Go (alternativa)              |
|-------------------------|-------------------------------|------------------------------|-------------------------------|
| REST API                | Spring MVC / WebFlux           | FastAPI / Django REST        | Gin / Echo / Fiber            |
| ORM / Persistência      | Spring Data JPA / Hibernate    | SQLAlchemy / Tortoise ORM    | GORM / sqlx                   |
| Segurança / JWT         | Spring Security + OAuth2       | python-jose / FastAPI OAuth2 | golang-jwt                    |
| Testes de integração    | Testcontainers (Java)          | Testcontainers (Python)      | Testcontainers (Go)           |
| Observabilidade         | Micrometer + OpenTelemetry     | opentelemetry-python         | opentelemetry-go              |
| Resiliência             | Resilience4j                   | tenacity / circuit-breaker   | sony/gobreaker                |

> Independente da linguagem, os princípios arquiteturais (Clean Architecture, DDD, idempotência, observabilidade e segurança) são obrigatórios.

---

## Entrega

- **Data limite:** 19/06/2026 às 23:55
- **Canal de entrega:** LMS da plataforma
- **O que entregar:** link do repositório GitHub contendo o projeto completo.

### Estrutura esperada do repositório

```
/
├── docs/
│   └── architecture.md        # Decomposição do domínio, Bounded Contexts, ADRs
├── order-service/              # Único serviço implementado
│   ├── src/
│   └── Dockerfile
├── wiremock/
│   ├── mappings/               # Mapeamentos dos demais serviços simulados
│   └── __files/                # Response bodies (se necessário)
├── docker-compose.yml
├── README.md                   # Instruções para rodar o projeto localmente
└── .github/
    └── workflows/
        └── ci.yml              # Pipeline GitHub Actions
```

### Checklist de entrega

- [ ] Repositório público no GitHub com código completo
- [ ] README com instruções de execução local (`docker-compose up`)
- [ ] Todos os endpoints obrigatórios implementados e documentados (Swagger/OpenAPI)
- [ ] Suíte de testes passando (unitários + integração)
- [ ] Pipeline CI/CD configurado e verde
- [ ] `docs/architecture.md` com as decisões de design
- [ ] Observabilidade funcional (logs estruturados + métricas expostas)
- [ ] Segurança implementada (JWT + controles OWASP)
