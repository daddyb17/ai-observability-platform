# Phase 10 Polish Runbook

## Goals

- Provide reproducible end-to-end simulation scripts.
- Add integration coverage with Testcontainers.
- Add CI automation in GitHub Actions.

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
- `services/notification-service` includes PostgreSQL Testcontainers test coverage for alert status transitions:
  - `PENDING -> RETRYING -> SENT`
  - `PENDING -> FAILED`

## CI Workflow

- File: `.github/workflows/ci.yml`
- Trigger: push to `main/master`, pull requests
- Steps:
  - setup Java 21
  - run `./mvnw -B -ntp test`
  - run `./mvnw -B -ntp package -DskipTests`
