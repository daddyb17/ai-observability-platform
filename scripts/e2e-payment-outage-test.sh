#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PAYMENT_URL=${PAYMENT_URL:-http://localhost:8092}
INCIDENT_URL=${INCIDENT_URL:-http://localhost:8084}
AI_URL=${AI_URL:-http://localhost:8085}
NOTIFICATION_URL=${NOTIFICATION_URL:-http://localhost:8086}
WAIT_SECONDS=${WAIT_SECONDS:-80}
LOAD_COUNT=${LOAD_COUNT:-200}
ANALYSIS_WAIT_SECONDS=${ANALYSIS_WAIT_SECONDS:-120}
ALERT_WAIT_SECONDS=${ALERT_WAIT_SECONDS:-90}
SCENARIO_START_EPOCH="$(date -u +%s)"

require_endpoint() {
  local name="$1"
  local url="$2"
  local max_retries="${3:-24}"
  local sleep_seconds="${4:-5}"
  local retries=0

  while (( retries < max_retries )); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    retries=$((retries + 1))
    sleep "$sleep_seconds"
  done

  echo "Required endpoint is not reachable: $name ($url)" >&2
  exit 1
}

extract_incident_id() {
  local incidents_json="$1"
  SCENARIO_START_EPOCH="$SCENARIO_START_EPOCH" INCIDENTS_JSON="$incidents_json" python - <<'PY'
import datetime
import json
import os
import sys

start_epoch = int(os.environ.get("SCENARIO_START_EPOCH", "0"))
raw = os.environ.get("INCIDENTS_JSON", "[]")
try:
    incidents = json.loads(raw)
except json.JSONDecodeError:
    print("")
    sys.exit(0)
if not isinstance(incidents, list):
    incidents = [incidents]

def created_epoch(value):
    if not value:
        return None
    try:
        return int(datetime.datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp())
    except ValueError:
        return None

recent = []
for incident in incidents:
    epoch = created_epoch(incident.get("createdAt"))
    if epoch is not None and epoch >= start_epoch:
        recent.append(incident)

recent.sort(key=lambda item: item.get("createdAt", ""), reverse=True)
print(recent[0].get("id", "") if recent else "")
PY
}

require_endpoint "payment-service health" "$PAYMENT_URL/actuator/health"
require_endpoint "incident-detection-service health" "$INCIDENT_URL/actuator/health"
require_endpoint "ai-analysis-service health" "$AI_URL/actuator/health"
require_endpoint "notification-service health" "$NOTIFICATION_URL/actuator/health"

echo "Running outage simulation..."
LOAD_COUNT="$LOAD_COUNT" WAIT_SECONDS="$WAIT_SECONDS" SCENARIO_START_EPOCH="$SCENARIO_START_EPOCH" "$SCRIPT_DIR/simulate-payment-outage.sh"

INCIDENTS_JSON="$(curl -fsS "$INCIDENT_URL/api/incidents" | tr -d '\n')"
INCIDENT_ID="$(extract_incident_id "$INCIDENTS_JSON")"
if [[ -z "$INCIDENT_ID" ]]; then
  echo "No incident was created during outage simulation for this run." >&2
  exit 1
fi

echo "Validating AI analysis generation for incident $INCIDENT_ID ..."
analysis_deadline=$(( $(date +%s) + ANALYSIS_WAIT_SECONDS ))
analysis_ok=0
while (( $(date +%s) < analysis_deadline )); do
  analysis_json="$(curl -fsS "$AI_URL/api/incidents/$INCIDENT_ID/analysis" || true)"
  if [[ "$analysis_json" == *"\"summary\""* && "$analysis_json" != *"\"status\":\"PENDING\""* ]]; then
    analysis_ok=1
    break
  fi
  sleep 5
done
if [[ "$analysis_ok" -ne 1 ]]; then
  echo "AI analysis was not generated within timeout for incident $INCIDENT_ID." >&2
  exit 1
fi

echo "Validating alert history generation ..."
alerts_deadline=$(( $(date +%s) + ALERT_WAIT_SECONDS ))
alerts_ok=0
while (( $(date +%s) < alerts_deadline )); do
  alerts_json="$(curl -fsS "$NOTIFICATION_URL/api/alerts?incidentId=$INCIDENT_ID&limit=10" || true)"
  if [[ "$alerts_json" == *"$INCIDENT_ID"* ]]; then
    alerts_ok=1
    break
  fi
  sleep 5
done
if [[ "$alerts_ok" -ne 1 ]]; then
  echo "No alert history found for incident $INCIDENT_ID." >&2
  exit 1
fi

echo "Recovering payment-service..."
curl -fsS -X POST "$PAYMENT_URL/payments/simulate/recover" >/dev/null

echo "E2E outage scenario passed for incident $INCIDENT_ID."
