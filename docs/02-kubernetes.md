# ☸️ Kubernetes

Kubernetes é um **orquestrador**: você declara o *estado desejado* (ex.: "2 réplicas desta imagem") e controladores trabalham continuamente para convergir o estado real a ele.

## Arquitetura

```mermaid
flowchart TB
    kubectl([kubectl / CI]) --> API
    subgraph CP["🧠 Control plane"]
        API[API Server] <--> ETCD[(etcd)]
        API <--> SCH[Scheduler]
        API <--> CM[Controller Manager]
    end
    subgraph N1["Nó 1"]
        KL1[kubelet] --> P1["Pod order-service"]
        KL1 --> P2["Pod inventory-service"]
    end
    subgraph N2["Nó 2"]
        KL2[kubelet] --> P3["Pod order-service"]
        KL2 --> P4["Pod inventory-service"]
    end
    API <--> KL1
    API <--> KL2
```

## Objetos usados no projeto

```mermaid
flowchart LR
    ING([Tráfego]) --> SVC["Service<br/>(IP estável + balanceamento)"]
    SVC --> P1[Pod]
    SVC --> P2[Pod]
    SVC --> P3[Pod]
    DEP["Deployment<br/>(rolling update)"] --> RS[ReplicaSet] --> P1 & P2 & P3
    HPA["HPA"] -. ajusta replicas .-> DEP
    PDB["PodDisruptionBudget"] -. protege .-> RS
```

| Objeto | Arquivo | Função |
|---|---|---|
| Deployment | `k8s/10-inventory.yaml`, `k8s/20-order.yaml` | Mantém N réplicas e faz rolling update |
| Service | idem | DNS estável (`inventory-service`) + balanceamento entre pods prontos |
| HPA / VPA | `20-order.yaml`, `30-vpa-example.yaml` | Escala automática (ver [escalabilidade](03-escalabilidade.md)) |
| PodDisruptionBudget | `20-order.yaml` | Garante mínimo de pods durante manutenção de nós |

## Probes: como o K8s sabe se a app está saudável

Spring Boot expõe `/actuator/health/liveness` e `/readiness` (`management.endpoint.health.probes.enabled=true`).

```mermaid
stateDiagram-v2
    [*] --> Starting
    Starting --> Running: startupProbe OK
    Running --> Ready: readinessProbe OK
    Ready --> NotReady: readinessProbe falha
    NotReady --> Ready: readinessProbe volta
    Running --> Restart: livenessProbe falha
    Ready --> Restart: livenessProbe falha
    Restart --> Starting
    note right of NotReady: Pod sai do Service, não é reiniciado
    note right of Restart: kubelet reinicia o container
```

## Deploy sem downtime

```mermaid
sequenceDiagram
    participant K as Deployment
    participant Old as Pods v1
    participant New as Pod v2
    participant S as Service
    K->>New: cria pod (maxSurge=1)
    New-->>K: readinessProbe OK
    K->>S: adiciona v2 aos endpoints
    K->>Old: preStop sleep 5s (sai dos endpoints)
    K->>Old: SIGTERM → graceful shutdown
    Note over K: maxUnavailable=0: nunca fica abaixo da capacidade
```

## ⚖️ Trade-offs

| Ganho | Custo |
|---|---|
| Auto-recuperação, rolling updates, escala declarativa | Complexidade operacional e curva de aprendizado |
| Portabilidade entre clouds | Custo do control plane, rede (CNI), segurança e RBAC para gerir |
| Service discovery embutido | Para poucos serviços, Compose/PaaS pode ser mais simples |
