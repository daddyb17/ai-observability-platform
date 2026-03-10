#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_DIR="${REPO_ROOT}/docs/screenshots"
LIVE_DIR="${OUT_DIR}/live"

INCIDENT_URL=${INCIDENT_URL:-http://localhost:8084}
AI_URL=${AI_URL:-http://localhost:8085}
NOTIFICATION_URL=${NOTIFICATION_URL:-http://localhost:8086}
GRAFANA_URL=${GRAFANA_URL:-http://localhost:3000/d/platform-overview/platform-overview}
JAEGER_URL=${JAEGER_URL:-http://localhost:16686/search}
SCENARIO_START_EPOCH=${SCENARIO_START_EPOCH:-0}

mkdir -p "$LIVE_DIR"

find_browser() {
  local candidates=(msedge microsoft-edge google-chrome chromium chromium-browser)
  for cmd in "${candidates[@]}"; do
    if command -v "$cmd" >/dev/null 2>&1; then
      echo "$cmd"
      return 0
    fi
  done
  return 1
}

capture_url() {
  local browser="$1"
  local url="$2"
  local out="$3"
  "$browser" --headless --disable-gpu --window-size=1600,900 --screenshot="$out" "$url" >/dev/null 2>&1
}

pretty_json_to_html() {
  local json_file="$1"
  local html_file="$2"
  {
    echo "<!doctype html><html><head><meta charset='utf-8'><style>body{font-family:Consolas,monospace;background:#f6f8fa;padding:24px}pre{background:#fff;padding:18px;border:1px solid #d0d7de;overflow:auto}</style></head><body><pre>"
    sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g' "$json_file"
    echo "</pre></body></html>"
  } > "$html_file"
}

echo "Fetching latest incident id..."
incidents_json="$(curl -fsS "$INCIDENT_URL/api/incidents")"
echo "$incidents_json" > "${LIVE_DIR}/incidents-latest.json"
incident_id="${INCIDENT_ID:-}"
if [[ -z "$incident_id" ]]; then
  incident_id="$(SCENARIO_START_EPOCH="$SCENARIO_START_EPOCH" INCIDENTS_JSON="$incidents_json" python - <<'PY'
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

filtered = []
for incident in incidents:
    epoch = created_epoch(incident.get("createdAt"))
    if epoch is not None and epoch >= start_epoch:
        filtered.append(incident)

filtered.sort(key=lambda item: item.get("createdAt", ""), reverse=True)
print(filtered[0].get("id", "") if filtered else "")
PY
)"
fi
if [[ -z "$incident_id" ]]; then
  echo "No incidents available for the requested run. Set INCIDENT_ID or run outage simulation first." >&2
  exit 1
fi

echo "Capturing live API payloads for incident $incident_id..."
curl -fsS "$INCIDENT_URL/api/incidents/$incident_id" | python -m json.tool > "${LIVE_DIR}/incident-api-response.json"
curl -fsS "$AI_URL/api/incidents/$incident_id/analysis" | python -m json.tool > "${LIVE_DIR}/ai-analysis-response.json"
curl -fsS "$NOTIFICATION_URL/api/alerts?incidentId=$incident_id&limit=10" | python -m json.tool > "${LIVE_DIR}/alert-history-response.json"

browser=""
if browser="$(find_browser)"; then
  echo "Using headless browser: $browser"
  capture_url "$browser" "$GRAFANA_URL" "${OUT_DIR}/grafana-dashboard.png" || true
  capture_url "$browser" "$JAEGER_URL" "${OUT_DIR}/jaeger-trace-bottleneck.png" || true

  pretty_json_to_html "${LIVE_DIR}/incident-api-response.json" "${LIVE_DIR}/incident-api-response.html"
  pretty_json_to_html "${LIVE_DIR}/ai-analysis-response.json" "${LIVE_DIR}/ai-analysis-response.html"
  pretty_json_to_html "${LIVE_DIR}/alert-history-response.json" "${LIVE_DIR}/alert-history-response.html"

  capture_url "$browser" "file://${LIVE_DIR}/incident-api-response.html" "${OUT_DIR}/incident-api-response.png" || true
  capture_url "$browser" "file://${LIVE_DIR}/ai-analysis-response.html" "${OUT_DIR}/ai-analysis-response.png" || true
  capture_url "$browser" "file://${LIVE_DIR}/alert-history-response.html" "${OUT_DIR}/alert-history-response.png" || true
else
  echo "No headless browser detected. JSON evidence saved to ${LIVE_DIR}."
fi

echo "Live evidence capture completed."
