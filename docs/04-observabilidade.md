# 🔭 Observabilidade

> Monitoramento responde "**está quebrado?**"; observabilidade ajuda a responder "**por que** está quebrado?" sem precisar de novo deploy.

## Os 3 pilares

```mermaid
flowchart LR
    APP["Spring Boot<br/>Actuator + Micrometer"] --> M["📊 Métricas<br/>(o quê / quanto)"]
    APP --> L["📜 Logs<br/>(o que aconteceu)"]
    APP --> T["🧵 Traces<br/>(onde / quanto tempo)"]
    M --> PROM[(Prometheus)] --> GRAF[Grafana]
    T --> OTLP["OTLP"] --> JAE[(Jaeger)]
    L --> STD["stdout"] --> AGG["Agregador (Loki/ELK)"]
```

| Pilar | Implementação no projeto |
|---|---|
| Métricas | `micrometer-registry-prometheus` → `/actuator/prometheus`; métrica de negócio `orders_created_total{status}`; métricas do Resilience4j |
| Logs | Padrão com `[app,traceId,spanId]` (`application.yml`): cada linha liga ao trace |
| Traces | `micrometer-tracing-bridge-otel` + OTLP → Jaeger; `RestClient.Builder` propaga o contexto (`traceparent`) para o inventory |
| Health | `/actuator/health/liveness` e `/readiness` (usados pelas probes do K8s) |

## Fluxo de um trace

```mermaid
sequenceDiagram
    participant C as Cliente
    participant O as order-service
    participant I as inventory-service
    participant J as Jaeger
    C->>O: POST /orders
    Note over O: gera traceId=abc, spanId=1
    O->>I: GET /inventory/BOOK-1 (header traceparent: abc-1)
    Note over I: continua traceId=abc, novo span
    I-->>O: 200
    O-->>C: 201
    O->>J: exporta spans (OTLP)
```

> O `inventory-service` é deliberadamente simples (só métricas). Para ver o span dele no Jaeger, adicione as mesmas dependências de tracing do `order-service`.

## Golden Signals e como consultá-los (PromQL)

```mermaid
mindmap
  root((Golden Signals))
    Latência
      p99 por endpoint
    Tráfego
      requisições por segundo
    Erros
      taxa de 5xx
    Saturação
      CPU, heap, threads, throttling
```

| Sinal | PromQL |
|---|---|
| Tráfego | `sum(rate(http_server_requests_seconds_count{application="order-service"}[1m]))` |
| Erros | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` |
| Latência p99 | `histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{application="order-service"}[5m])))` |
| Saturação (heap) | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` |
| Circuito aberto | `resilience4j_circuitbreaker_state{name="inventory",state="open"} == 1` |
| Pedidos por status | `sum by (status) (rate(orders_created_total[1m]))` |

Alertas de exemplo em [`observability/alerts.yml`](../observability/alerts.yml).

## Roteiro de investigação

```mermaid
flowchart TD
    A["🚨 Alerta: erro alto"] --> B["Métricas: qual endpoint/pod?"]
    B --> C["Circuit breaker aberto?"]
    C -- sim --> D["Traces: inventory lento ou falhando?"]
    C -- não --> E["Traces: span lento dentro do order?"]
    D --> F["Logs filtrados pelo traceId"]
    E --> F
    F --> G["Causa raiz"]
```

## ⚖️ Trade-offs

| Decisão | Ganho | Custo |
|---|---|---|
| Amostragem de 100 % dos traces (`TRACING_SAMPLING=1.0`) | Visibilidade total | Custo/overhead alto; em produção use 1–10 % ou *tail sampling* |
| Muitas labels em métricas (ex.: `userId`) | Detalhe | **Explosão de cardinalidade** derruba o Prometheus |
| Histogramas (`percentiles-histogram`) | Percentis corretos agregados entre pods | Mais séries armazenadas |
| Logs verbosos | Diagnóstico | Custo de armazenamento, dados sensíveis |
