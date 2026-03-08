# AI Observability Platform

AI-assisted observability and incident diagnosis platform for distributed Java microservices.

## Why This Project Matters

This repository demonstrates production-style Java microservices, event-driven design with Kafka, and AI-assisted incident analysis in a local, reproducible environment.

## Architecture

- API access flows through `api-gateway` with JWT authentication from `auth-service`.
- Logs, metrics, and trace signals are normalized and correlated in `incident-detection-service`.
- `ai-analysis-service` builds incident evidence summaries and root-cause hypotheses.
- `notification-service` emits outbound alerts and stores alert history.

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
2. Build all modules:
   - `./mvnw clean verify` (Windows: `mvnw.cmd clean verify`)
3. Start services as needed:
   - Use module-level Maven commands or IDE run configs.

## Phase 2 Infrastructure Endpoints

- PostgreSQL: `localhost:5432`
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
2. Trigger payment timeout simulation.
3. Generate order traffic.
4. Observe logs, metrics, and traces.
5. Review generated incidents and AI analysis.
6. Confirm outbound alert delivery.

## Sample API Calls

- `POST /api/auth/login`
- `GET /api/logs/search`
- `GET /api/incidents/{id}`
- `POST /api/incidents/{id}/analyze`

## Testing

- Unit tests focus on rule logic, severity calculation, and payload builders.
- Integration tests target Kafka/PostgreSQL/Elasticsearch/Redis flows (Testcontainers planned).

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
