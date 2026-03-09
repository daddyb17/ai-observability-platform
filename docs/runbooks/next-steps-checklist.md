# Next Steps Checklist (Post-MVP)

Based on `AGENTS.md` roadmap items after MVP/Phase 10.

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
