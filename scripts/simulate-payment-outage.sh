#!/usr/bin/env bash
set -euo pipefail

PAYMENT_URL=${PAYMENT_URL:-http://localhost:8092}
ORDER_URL=${ORDER_URL:-http://localhost:8091}
INCIDENT_URL=${INCIDENT_URL:-http://localhost:8084}
AI_URL=${AI_URL:-http://localhost:8085}
NOTIFICATION_URL=${NOTIFICATION_URL:-http://localhost:8086}
LOAD_COUNT=${LOAD_COUNT:-40}
WAIT_SECONDS=${WAIT_SECONDS:-35}

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

echo "Enabling payment timeout simulation..."
curl -fsS -X POST "$PAYMENT_URL/payments/simulate/timeout" >/dev/null

echo "Generating order load test traffic (count=$LOAD_COUNT)..."
curl -fsS -X POST "$ORDER_URL/orders/load-test" \
  -H "Content-Type: application/json" \
  -d "{\"count\":$LOAD_COUNT}" >/dev/null

echo "Waiting $WAIT_SECONDS seconds for incident correlation..."
sleep "$WAIT_SECONDS"

LATEST_INCIDENT_JSON="$(curl -fsS "$INCIDENT_URL/api/incidents" | tr -d '\n')"
INCIDENT_ID="$(echo "$LATEST_INCIDENT_JSON" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -n 1)"

if [[ -z "${INCIDENT_ID}" ]]; then
  echo "No incident detected yet."
  exit 0
fi

echo "Latest incident: $INCIDENT_ID"
echo "Triggering analysis for incident..."
curl -fsS -X POST "$INCIDENT_URL/api/incidents/$INCIDENT_ID/analyze" >/dev/null || true
sleep 5

echo "Incident snapshot:"
curl -fsS "$INCIDENT_URL/api/incidents/$INCIDENT_ID"
echo

echo "AI analysis snapshot:"
curl -fsS "$AI_URL/api/incidents/$INCIDENT_ID/analysis" || true
echo

echo "Recent alerts for incident:"
curl -fsS "$NOTIFICATION_URL/api/alerts?incidentId=$INCIDENT_ID&limit=10" || true
echo

echo "Simulation complete. Recover with: curl -X POST $PAYMENT_URL/payments/simulate/recover"

