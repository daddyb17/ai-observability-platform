# Phase 10 Polish Runbook

## Goals

- Provide reproducible end-to-end simulation scripts.
- Add integration coverage with Testcontainers.
- Add CI automation in GitHub Actions.
- Add architecture diagrams and screenshot assets for portfolio presentation.

## Simulation Scripts

- Bash:
  - `scripts/seed-demo-data.sh`
  - `scripts/simulate-payment-outage.sh`
  - `scripts/simulate-latency-spike.sh`
- PowerShell:
  - `scripts/seed-demo-data.ps1`
  - `scripts/simulate-payment-outage.ps1`
  - `scripts/simulate-latency-spike.ps1`

### Typical flow

1. Start infra (`start-local.sh` or `start-local.ps1`).
2. Start services required for the demo:
   - sample apps
   - log-ingestion-service
   - metrics-ingestion-service
   - trace-ingestion-service
   - incident-detection-service
   - ai-analysis-service
   - notification-service
3. Seed baseline data.
4. Run payment outage simulation.
5. Inspect incidents, analysis, and alert history.
6. Recover payment-service (`POST /payments/simulate/recover`).

Scripts now perform endpoint preflight checks and fail fast with explicit messages when required services are not running.

## Integration Tests

- `shared/common-test` includes a container startup smoke test for:
  - PostgreSQL
  - Optional Kafka (`RUN_KAFKA_TESTCONTAINER=true`)
  - Optional Redis (`RUN_REDIS_TESTCONTAINER=true`)
  - Optional Elasticsearch (`RUN_ELASTIC_TESTCONTAINER=true`)
- `services/log-ingestion-service` includes Elasticsearch Testcontainers coverage:
  - log document indexing and retrieval by event id.
- `services/incident-detection-service` includes PostgreSQL Testcontainers coverage:
  - repeated exception burst creates and persists an incident + signal.
- `services/ai-analysis-service` includes PostgreSQL Testcontainers coverage:
  - `ai.analysis.request` envelope consumption persists analysis output.
- `services/notification-service` includes PostgreSQL Testcontainers test coverage for alert status transitions:
  - `PENDING -> RETRYING -> SENT`
  - `PENDING -> FAILED`

## Architecture and Screenshot Assets

- Architecture docs:
  - `docs/architecture/system-context.md`
  - `docs/architecture/container-diagram.md`
  - `docs/architecture/sequence-log-ingestion.md`
  - `docs/architecture/sequence-incident-analysis.md`
- Screenshot folder:
  - `docs/screenshots/architecture-overview.png`
  - `docs/screenshots/sequence-log-ingestion.png`
  - `docs/screenshots/grafana-dashboard.png`
  - `docs/screenshots/jaeger-trace-bottleneck.png`
  - `docs/screenshots/incident-api-response.png`
  - `docs/screenshots/ai-analysis-response.png`
  - `docs/screenshots/alert-history-response.png`

## CI Workflow

- File: `.github/workflows/ci.yml`
- Trigger: push to `main/master`, pull requests
- Steps:
  - setup Java 21
  - run `./mvnw -B -ntp test`
  - run `./mvnw -B -ntp package -DskipTests`
