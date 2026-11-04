# ⚖️ Trade-offs em sistemas distribuídos

Em um sistema distribuído **a rede falha, tem latência e os nós caem**. Não existe design perfeito: existem escolhas conscientes.

## 1. As 8 falácias da computação distribuída

```mermaid
mindmap
  root((Falácias))
    A rede é confiável
    Latência é zero
    Largura de banda é infinita
    A rede é segura
    A topologia não muda
    Existe um único administrador
    Custo de transporte é zero
    A rede é homogênea
```

## 2. CAP e PACELC

Na presença de **P**artição de rede, escolha entre **C**onsistência e **A**vailability (disponibilidade).

```mermaid
flowchart TD
    P{"Partição de rede?<br/>(order ↔ inventory inacessível)"}
    P -- Sim --> CHOICE{Prioridade?}
    CHOICE -- "CP: recusar o pedido" --> CP["503 ao cliente<br/>nunca vende sem estoque confirmado"]
    CHOICE -- "AP: aceitar" --> AP["202 PENDING_VALIDATION<br/>valida depois (consistência eventual)"]
    P -- Não --> EL{"Else: Latência × Consistência"}
    EL --> L1["Consultar sempre o inventory<br/>(mais consistente, mais lento)"]
    EL --> L2["Cachear estoque<br/>(mais rápido, pode estar desatualizado)"]
```

**No projeto** (`OrderService` + `InventoryClient`): escolhemos **AP**. Se o inventory falha, o pedido é aceito como `PENDING_VALIDATION` (HTTP 202). Risco: *overselling* → exige compensação posterior (cancelar/pedir desculpas). Para pagamentos, a escolha típica seria CP.

## 3. Resiliência: timeout → retry → circuit breaker → fallback

```mermaid
flowchart LR
    R([Requisição]) --> RT["🔁 Retry<br/>3 tentativas, backoff exp."]
    RT --> CB{"⚡ Circuit Breaker"}
    CB -- fechado --> TO["⏱️ Timeout 800ms"] --> I[inventory]
    CB -- aberto --> FB
    I -- erro/timeout --> RT
    RT -- esgotou --> FB["🛟 Fallback<br/>PENDING_VALIDATION"]
    I -- ok --> OK([CONFIRMED / REJECTED])
```

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN: falhas ≥ 50% (janela de 10, mín. 5)
    OPEN --> HALF_OPEN: após 10s
    HALF_OPEN --> CLOSED: 3 chamadas de teste OK
    HALF_OPEN --> OPEN: falhou
```

| Mecanismo | Protege contra | Risco se mal configurado |
|---|---|---|
| Timeout | Dependência lenta que prende threads | Muito curto: falsos erros; muito longo: esgota threads |
| Retry + backoff | Falhas transitórias | **Tempestade de retries** amplifica a falha (3 camadas × 3 tentativas = 27 chamadas) |
| Circuit breaker | Falha em cascata; dá folga ao downstream | Limiares ruins abrem/fecham demais |
| Fallback | Degradação elegante | Resposta "falsa" precisa ser aceitável ao negócio |

**Experimente:** `POST localhost:8081/chaos?failurePercent=100` e depois `GET localhost:8080/actuator/circuitbreakers`.

## 4. Idempotência: obrigatória quando há retry

Um retry pode duplicar um efeito (o POST chegou, mas a resposta se perdeu).

```mermaid
sequenceDiagram
    participant C as Cliente
    participant O as order-service
    C->>O: POST /orders (Idempotency-Key: k1)
    O-->>C: ⏳ resposta perdida
    C->>O: POST /orders (Idempotency-Key: k1)
    Note over O: chave já vista → devolve o MESMO pedido
    O-->>C: 201 (mesmo id)
```

> Limitação do exemplo: o mapa de chaves está em memória do pod. Com várias réplicas, deveria estar num armazenamento compartilhado (Redis/banco) com TTL.

## 5. Comunicação: síncrona vs. assíncrona

```mermaid
flowchart LR
    subgraph SYNC["Síncrona (este projeto)"]
        A1[order] -->|HTTP| B1[inventory]
    end
    subgraph ASYNC["Assíncrona (alternativa)"]
        A2[order] -->|"evento OrderCreated"| BR[(Broker<br/>Kafka/RabbitMQ)] --> B2[inventory]
    end
```

| | Síncrona (REST) | Assíncrona (eventos) |
|---|---|---|
| Acoplamento temporal | Alto (ambos precisam estar no ar) | Baixo |
| Simplicidade / debug | ✅ Simples | ❌ Mais difícil (ordem, duplicatas, DLQ) |
| Consistência | Imediata | Eventual |
| Latência percebida | Soma das chamadas | Resposta rápida, processamento depois |

## 6. Outros trade-offs que você vai encontrar

| Decisão | De um lado | Do outro |
|---|---|---|
| Monolito × microsserviços | Simplicidade, transações ACID | Deploy e escala independentes, mas rede, observabilidade e consistência distribuída |
| Transação distribuída | 2PC: forte, mas bloqueante e frágil | **Saga**: eventual, com compensações |
| Cache | Menor latência e carga | Dado obsoleto, invalidação difícil |
| Réplicas de leitura | Escala de leitura | *Replication lag* |
| Mais réplicas | Disponibilidade e vazão | Custo, mais conexões, mais coordenação |

## 7. Saga (evolução natural do exemplo)

```mermaid
sequenceDiagram
    participant O as Order
    participant I as Inventory
    participant P as Payment
    O->>I: reservar estoque
    I-->>O: reservado
    O->>P: cobrar
    P--xO: falhou
    O->>I: ⏪ compensar: liberar estoque
    O->>O: status = CANCELLED
```

## ✅ Checklist de decisão

1. Qual o custo de um dado inconsistente por alguns segundos? → define CP vs AP.
2. O que acontece se a dependência cair? → defina o fallback **antes** de ir para produção.
3. A operação é repetível sem efeitos colaterais? → idempotência.
4. Consigo ver o que acontece? → métricas, logs e traces antes de escalar.
