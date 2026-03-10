#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PID_FILE="${SCRIPT_DIR}/.demo-services.pids"
LOG_DIR="${REPO_ROOT}/logs/demo-services"

mkdir -p "${LOG_DIR}"
rm -f "${PID_FILE}"

detect_postgres_port() {
  if [[ -n "${POSTGRES_PORT:-}" ]]; then
    echo "${POSTGRES_PORT}"
    return
  fi

  local compose_port
  compose_port="$(docker compose port postgres 5432 2>/dev/null | head -n 1 || true)"
  if [[ -n "${compose_port}" ]]; then
    echo "${compose_port##*:}"
    return
  fi

  echo "55432"
}

POSTGRES_PORT_RESOLVED="$(detect_postgres_port)"
export POSTGRES_URL="${POSTGRES_URL:-jdbc:postgresql://localhost:${POSTGRES_PORT_RESOLVED}/aiobs}"
echo "Using POSTGRES_URL=${POSTGRES_URL}"

if [[ -z "${JWT_SECRET_BASE64:-}" ]]; then
  JWT_SECRET_BASE64="$(python - <<'PY'
import base64
import secrets
print(base64.b64encode(secrets.token_bytes(32)).decode())
PY
)"
  export JWT_SECRET_BASE64
  echo "Generated ephemeral JWT_SECRET_BASE64 for this local run."
fi

if [[ -z "${INTERNAL_API_TOKEN:-}" ]]; then
  INTERNAL_API_TOKEN="$(python - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
)"
  export INTERNAL_API_TOKEN
  echo "Generated INTERNAL_API_TOKEN for /internal endpoints."
fi

SERVICES=(
  "services/auth-service:8081:auth-service"
  "services/log-ingestion-service:8082:log-ingestion-service"
  "services/search-query-service:8083:search-query-service"
  "services/incident-detection-service:8084:incident-detection-service"
  "services/ai-analysis-service:8085:ai-analysis-service"
  "services/notification-service:8086:notification-service"
  "services/metrics-ingestion-service:8087:metrics-ingestion-service"
  "services/trace-ingestion-service:8088:trace-ingestion-service"
  "sample-apps/order-service:8091:order-service"
  "sample-apps/payment-service:8092:payment-service"
  "sample-apps/notification-sample-service:8093:notification-sample-service"
  "services/api-gateway:8080:api-gateway"
)

wait_for_http() {
  local name="$1"
  local url="$2"
  local max_retries="${3:-120}"
  local sleep_seconds="${4:-5}"
  local retries=0

  while (( retries < max_retries )); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      echo "Ready: ${name}"
      return 0
    fi
    retries=$((retries + 1))
    sleep "${sleep_seconds}"
  done

  echo "Timed out waiting for ${name} at ${url}" >&2
  return 1
}

for entry in "${SERVICES[@]}"; do
  IFS=":" read -r module_path port service_name <<<"${entry}"
  if [[ ! -f "${REPO_ROOT}/${module_path}/pom.xml" ]]; then
    echo "pom.xml not found for module: ${module_path}" >&2
    exit 1
  fi

  log_path="${LOG_DIR}/${service_name}.log"
  (
    cd "${REPO_ROOT}"
    ./mvnw -f "${module_path}/pom.xml" spring-boot:run >"${log_path}" 2>&1
  ) &
  pid=$!

  echo "${pid},${service_name},${port},${module_path}" >> "${PID_FILE}"
  echo "Started ${service_name} (PID ${pid}); log: ${log_path}"
done

if [[ "${SKIP_DEMO_HEALTHCHECK:-false}" == "true" ]]; then
  echo "SKIP_DEMO_HEALTHCHECK=true, skipping health checks."
  exit 0
fi

for entry in "${SERVICES[@]}"; do
  IFS=":" read -r _module_path port service_name <<<"${entry}"
  wait_for_http "${service_name}" "http://localhost:${port}/actuator/health"
done

echo "All demo services are running."
