#!/usr/bin/env bash
set -euo pipefail

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not running. Start Docker Desktop and retry." >&2
  exit 1
fi

MAX_RETRIES="${MAX_RETRIES:-40}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"

wait_for_healthcheck() {
  local service="$1"
  local retries=0

  while (( retries < MAX_RETRIES )); do
    local container_id
    container_id="$(docker compose ps -q "$service")"
    if [[ -n "$container_id" ]]; then
      local status
      status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")"
      if [[ "$status" == "healthy" || "$status" == "none" ]]; then
        return 0
      fi
    fi

    retries=$((retries + 1))
    sleep "$SLEEP_SECONDS"
  done

  echo "Timed out waiting for healthcheck for service: $service" >&2
  return 1
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local retries=0

  while (( retries < MAX_RETRIES )); do
    if curl -fsS "$url" > /dev/null 2>&1; then
      return 0
    fi
    retries=$((retries + 1))
    sleep "$SLEEP_SECONDS"
  done

  echo "Timed out waiting for HTTP endpoint: $name ($url)" >&2
  return 1
}

wait_for_healthcheck "postgres"
wait_for_healthcheck "kafka"
wait_for_healthcheck "redis"
wait_for_healthcheck "elasticsearch"

wait_for_http "Elasticsearch" "http://localhost:9200"
wait_for_http "Prometheus" "http://localhost:9090/-/ready"
wait_for_http "Grafana" "http://localhost:3000/api/health"
wait_for_http "Jaeger" "http://localhost:16686"
wait_for_http "Kibana" "http://localhost:5601/api/status"
wait_for_http "Kafka UI" "http://localhost:8089"

echo "All Phase 2 infrastructure checks passed."
