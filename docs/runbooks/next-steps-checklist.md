# Next Steps Checklist (Post-MVP)

## Immediate Enhancements

- [x] Add incident timeline endpoint (`GET /api/incidents/{id}/timeline`).
- [x] Add deduplication/suppression window for repeated alert noise.
- [x] Add replayable DLQ flow for failed events (`POST /internal/alerts/dlq/replay`).
- [x] Expand config-driven rules (runtime editable thresholds via internal API).

## Platform Hardening

- [x] Add Resilience4j policies for AI, Elasticsearch, and webhooks.
- [x] Add stronger integration tests for full end-to-end outage scenario.
- [x] Add CI stage for Docker-backed integration tests.

## Portfolio Upgrades

- [x] Replace screenshot placeholders with live captures from running stack (automation scripts added).
- [x] Add Kubernetes manifests (or Helm chart) for core services.
- [x] Add provider switch demo (Mock/OpenAI/Ollama) in runbook.

## Portfolio Hardening (2026-03-10)

- [x] Externalize JWT secrets (`JWT_SECRET_BASE64`) and fail fast when missing.
- [x] Add deterministic outage/evidence scripts using run-specific incident selection.
- [x] Implement Redis-backed gateway rate limiting with in-memory fallback.
- [x] Secure `/internal/**` endpoints with `X-Internal-Auth-Token`.
- [x] Add CI verify gate (`mvn verify -DskipTests`) and Maven enforcer rules.
