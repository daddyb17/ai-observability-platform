# AI Observability Platform

AI-assisted observability and incident diagnosis platform for distributed Java microservices.

## Why This Project Matters

This repository demonstrates production-style Java microservices, event-driven design with Kafka, and AI-assisted incident analysis in a local, reproducible environment.

## Architecture

- API access flows through `api-gateway` with JWT authentication from `auth-service`.
- Logs, metrics, and trace signals are normalized and correlated in `incident-detection-service`.
- `ai-analysis-service` builds incident evidence summaries and root-cause hypotheses.
- `notification-service` emits outbound alerts and stores alert history.

Detailed architecture docs:

- `docs/architecture/system-context.md`
- `docs/architecture/container-diagram.md`
- `docs/architecture/sequence-log-ingestion.md`
- `docs/architecture/sequence-incident-analysis.md`
- `docs/architecture/api-contracts.md`

## Service Responsibilities

- `auth-service`: login/refresh/logout/me JWT flow.
- `api-gateway`: routing, auth checks, rate-limiting integration point.
- `log-ingestion-service`: structured logs to Kafka + Elasticsearch.
- `metrics-ingestion-service`: Prometheus-derived metric signals.
- `trace-ingestion-service`: trace lookup and summary generation.
- `incident-detection-service`: rules, aggregation, severity, incident lifecycle.
- `ai-analysis-service`: AI analysis pipeline with provider abstraction.
- `search-query-service`: unified read APIs for logs/incidents/analysis.
- `notification-service`: webhook/slack-style alert fan-out.
- `order-service`, `payment-service`, `notification-sample-service`: demo traffic and failure scenarios.

## Tech Stack

- Java 21
- Spring Boot 3.x
- Kafka
- PostgreSQL
- Elasticsearch
- Prometheus + Grafana
- Jaeger
- Redis
- Docker Compose

## Local Setup

1. Start infrastructure:
   - Bash: `./scripts/start-local.sh`
   - PowerShell: `powershell -ExecutionPolicy Bypass -File .\\scripts\\start-local.ps1`
   - Optional Postgres host port override: set `POSTGRES_PORT` (example: `POSTGRES_PORT=55432`)
2. Configure local environment:
   - Copy `.env.example` values into your shell/session as needed.
   - `start-demo-services` scripts auto-generate ephemeral `JWT_SECRET_BASE64` and `INTERNAL_API_TOKEN` when unset.
3. Build all modules:
   - `./mvnw clean verify` (Windows: `mvnw.cmd clean verify`)
4. Start services as needed:
   - PowerShell: `powershell -ExecutionPolicy Bypass -File .\\scripts\\start-demo-services.ps1`
   - Bash: `./scripts/start-demo-services.sh`

## Phase 2 Infrastructure Endpoints

- PostgreSQL: `localhost:55432` (default; override with `POSTGRES_PORT`)
- Kafka: `localhost:9092`
- Redis: `localhost:6379`
- Elasticsearch: `http://localhost:9200`
- Kibana: `http://localhost:5601`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin)
- Jaeger: `http://localhost:16686`
- Kafka UI: `http://localhost:8089`

## Demo Walkthrough

1. Bring up the stack.
2. Seed baseline data (`scripts/seed-demo-data.sh` or `scripts/seed-demo-data.ps1`).
3. Trigger payment timeout simulation (`scripts/simulate-payment-outage.sh` or `scripts/simulate-payment-outage.ps1`).
4. Observe logs, metrics, and traces.
5. Review generated incidents and AI analysis.
6. Confirm outbound alert delivery.
7. Simulate latency spikes (`scripts/simulate-latency-spike.sh` or `scripts/simulate-latency-spike.ps1`).
8. Stop demo services when done:
   - PowerShell: `powershell -ExecutionPolicy Bypass -File .\\scripts\\stop-demo-services.ps1`
   - Bash: `./scripts/stop-demo-services.sh`

## Sample API Calls

- `POST /api/auth/login`
- `GET /api/logs/search`
- `GET /api/incidents/{id}`
- `POST /api/incidents/{id}/analyze`

## Testing

- Unit tests focus on rule logic, severity calculation, and payload builders.
- Integration tests include Testcontainers-based checks for:
  - shared infrastructure container bootstrapping (`common-test`)
  - log indexing into Elasticsearch (`log-ingestion-service`)
  - repeated-error burst to incident persistence (`incident-detection-service`)
  - AI analysis persistence from request-event consumption (`ai-analysis-service`)
  - notification persistence lifecycle with PostgreSQL (`notification-service`)
  - optional heavier container checks can be enabled with:
    - `RUN_KAFKA_TESTCONTAINER=true`
    - `RUN_REDIS_TESTCONTAINER=true`
    - `RUN_ELASTIC_TESTCONTAINER=true`

## CI/CD

- GitHub Actions workflow: `.github/workflows/ci.yml`
- Runs on pushes/PRs, using Java 21 and Maven cache:
  - `./mvnw -B -ntp verify -DskipTests`
  - `./mvnw -B -ntp test`
  - `./mvnw -B -ntp package -DskipTests`

## Documentation and PR Workflow

- PR template: `.github/pull_request_template.md`
- Screenshot assets: `docs/screenshots/README.md`
- Demo capture runbook: `docs/runbooks/demo-capture-runbook.md`
- Post-MVP checklist: `docs/runbooks/next-steps-checklist.md`
- Provider switch runbook: `docs/runbooks/provider-switch-demo.md`
- Outage E2E runbook: `docs/runbooks/outage-e2e-test.md`

## Kubernetes Starter

- Kubernetes manifests: `k8s/`
- Apply base:
  - `kubectl apply -k k8s/base`

## Future Improvements

- Config-driven incident rules
- DLQ replay tooling
- Kubernetes manifests
- Local LLM provider integration

## Senior Engineering Concepts Demonstrated

- Event-driven design
- Distributed tracing
- Structured logging
- Incident correlation
- Resilience patterns
- AI-assisted operational diagnosis
