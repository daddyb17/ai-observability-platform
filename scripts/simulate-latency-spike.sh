#!/usr/bin/env bash
set -euo pipefail

PAYMENT_URL=${PAYMENT_URL:-http://localhost:8092}
ORDER_URL=${ORDER_URL:-http://localhost:8091}
INCIDENT_URL=${INCIDENT_URL:-http://localhost:8084}
LOAD_COUNT=${LOAD_COUNT:-30}
WAIT_SECONDS=${WAIT_SECONDS:-25}

require_endpoint() {
  local name="$1"
  local url="$2"
  if ! curl -fsS "$url" >/dev/null 2>&1; then
    echo "Required endpoint is not reachable: $name ($url)" >&2
    exit 1
  fi
}

require_endpoint "payment-service health" "$PAYMENT_URL/actuator/health"
require_endpoint "order-service health" "$ORDER_URL/actuator/health"
require_endpoint "incident-detection-service health" "$INCIDENT_URL/actuator/health"

echo "Enabling payment latency simulation..."
curl -fsS -X POST "$PAYMENT_URL/payments/simulate/latency" >/dev/null

echo "Generating load to surface latency signal (count=$LOAD_COUNT)..."
curl -fsS -X POST "$ORDER_URL/orders/load-test" \
  -H "Content-Type: application/json" \
  -d "{\"count\":$LOAD_COUNT}" >/dev/null

echo "Waiting $WAIT_SECONDS seconds for metrics/incident processing..."
sleep "$WAIT_SECONDS"

echo "Current incidents:"
curl -fsS "$INCIDENT_URL/api/incidents" || true
echo

echo "Latency spike simulation complete."

