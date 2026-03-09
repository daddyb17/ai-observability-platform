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
incident_id="$(echo "$incidents_json" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -n 1)"
if [[ -z "$incident_id" ]]; then
  echo "No incidents available. Run outage simulation first." >&2
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
