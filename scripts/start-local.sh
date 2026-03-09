#!/usr/bin/env bash
set -euo pipefail

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not running. Start Docker Desktop and retry." >&2
  exit 1
fi

echo "Starting Phase 2 infrastructure..."
docker compose up -d postgres zookeeper kafka redis elasticsearch prometheus grafana jaeger kibana kafka-ui kafka-init

echo "Waiting for infrastructure readiness..."
"$(dirname "$0")/verify-local.sh"

POSTGRES_PORT_RESOLVED="${POSTGRES_PORT:-}"
if [[ -z "${POSTGRES_PORT_RESOLVED}" ]]; then
  compose_port="$(docker compose port postgres 5432 2>/dev/null | head -n 1 || true)"
  if [[ -n "${compose_port}" ]]; then
    POSTGRES_PORT_RESOLVED="${compose_port##*:}"
  fi
fi
POSTGRES_PORT_RESOLVED="${POSTGRES_PORT_RESOLVED:-55432}"

echo "Infra is ready."
echo "Postgres:   localhost:${POSTGRES_PORT_RESOLVED}"
echo "Prometheus: http://localhost:9090"
echo "Grafana:    http://localhost:3000 (admin/admin)"
echo "Jaeger:     http://localhost:16686"
echo "Kibana:     http://localhost:5601"
echo "Kafka UI:   http://localhost:8089"

