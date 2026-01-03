# Camunda 8 (Zeebe) + AxonServer + Kafka (Redpanda) — Runnable Demo (Split Gateway/Brokers)

This repo is a runnable demo of a bank-style architecture:
- **Zeebe Gateway (stateless)** exposed on `localhost:26500`
- **Zeebe Brokers (stateful)**: 3 nodes, manage BPMN + process state
- **transfer-api**: client-facing REST API to start transactions
- **orchestration-workers**: Zeebe workers that send **Axon commands**
- **AxonServer (event store only)**: stores Axon events
- **Kafka (Redpanda)**: integration events
- **Observability**: OTEL -> Tempo -> Grafana (traces) + Prometheus (metrics)

## Architecture

```
Client
  |
  |  POST /transfers  (X-API-Key, Idempotency-Key)
  v
transfer-api (8083)
  |  - auth placeholder
  |  - rate limit (Bucket4j)
  |  - idempotency store (demo in-memory)
  |  - starts Zeebe process (MoneyTransferProcess)
  v
Zeebe Gateway (stateless) (26500)
  |
  v
Zeebe Brokers (stateful) x3
  |  - stores & versions BPMN
  |  - executes process instances
  |  - creates jobs for service tasks
  v
orchestration-workers (8080)
  |  - Zeebe workers (debit, book)
  |  - correlationId = processInstanceKey
  |  - sends Axon commands via CommandGateway
  v
AxonServer (8024 UI / 8124 gRPC)
  |
  +--> account-service (8081)  Axon Aggregate -> AccountDebitedEvent -> Kafka topic account.events.v1
  |
  +--> ledger-service  (8082)  Axon Aggregate -> LedgerBookedEvent   -> Kafka topic ledger.events.v1
        |
        v
Kafka (Redpanda) (9092) + Console (8088)

Observability:
- OpenTelemetry: services -> otel-collector -> Tempo -> Grafana (Trace)
- Metrics: services -> Prometheus -> Grafana (Dashboard)
```

## Prerequisites
- Docker Desktop
- **zbctl** (Zeebe CLI) installed on your machine

### Install zbctl
**macOS (Homebrew):**
```bash
brew install camunda-cloud/zeebe/zbctl
```

Check:
```bash
zbctl version
```

## Start everything
```bash
docker compose up -d --build
```

## Deploy BPMN (this makes `MoneyTransferProcess` available in Zeebe)
From the repo root:
```bash
zbctl deploy bpmn/transfer-process.bpmn
```

If your Gateway is not the default, specify address:
```bash
zbctl --address localhost:26500 --insecure deploy bpmn/transfer-process.bpmn
```

## Create a transfer via REST (realistic client flow)
```bash
curl -i -X POST http://localhost:8083/transfers   -H 'Content-Type: application/json'   -H 'X-API-Key: demo-key'   -H 'Idempotency-Key: idem-001'   -d '{"accountId":"A-001","amount":1000}'
```

## Monitor & trace requests (Prometheus + Grafana + Tempo)

### 1) Metrics (Prometheus)

Each Spring service exposes metrics at `.../actuator/prometheus`.

- Transfer API: http://localhost:8083/actuator/prometheus
- Orchestration Workers: http://localhost:8080/actuator/prometheus
- Account Service: http://localhost:8081/actuator/prometheus
- Ledger Service: http://localhost:8082/actuator/prometheus

Prometheus is on http://localhost:9090

Quick sanity check:
1. Open Prometheus → **Status → Targets**
2. You should see the 4 jobs **UP**.

### 2) Tracing (Grafana Tempo)

Grafana is on http://localhost:3000 (admin/admin).

To find the trace for a call you just made:
1. Open **Explore**
2. Pick the **Tempo** data source
3. Use TraceQL to search by service name (resource attribute):

```
{resource.service.name="transfer-api" && .http.route="/transfers"}
```

4. Click a trace result → you should see spans across:
   - `transfer-api` (HTTP handler)
   - `orchestration-workers` (Zeebe worker span)
   - (optionally) downstream calls as they are instrumented

### 3) If Grafana shows provisioning errors or duplicate dashboard UID

This demo uses provisioning (files under `infra/grafana/...`). If you previously started Grafana and it persisted state, you can reset everything with:

```bash
docker compose down -v
docker compose up -d --build
```

## UIs
- Zeebe Gateway: localhost:26500 (gRPC, no UI)
- AxonServer UI: http://localhost:8024
- Redpanda Console: http://localhost:8088
- Grafana: http://localhost:3000  (admin/admin)
- Prometheus: http://localhost:9090
- Tempo: http://localhost:3200

## Troubleshooting `zbctl deploy`
- `connection refused`: Zeebe Gateway not ready yet → wait 10–30s, retry
- `NOT_FOUND`: wrong BPMN path or BPMN invalid
- If needed: `docker compose logs -f zeebe-gateway`
