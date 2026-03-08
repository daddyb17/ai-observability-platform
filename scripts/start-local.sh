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

echo "Infra is ready."
echo "Prometheus: http://localhost:9090"
echo "Grafana:    http://localhost:3000 (admin/admin)"
echo "Jaeger:     http://localhost:16686"
echo "Kibana:     http://localhost:5601"
echo "Kafka UI:   http://localhost:8089"

