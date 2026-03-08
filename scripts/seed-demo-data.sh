#!/usr/bin/env bash
set -euo pipefail

ORDER_URL=${ORDER_URL:-http://localhost:8091}
PAYMENT_URL=${PAYMENT_URL:-http://localhost:8092}
COUNT=${COUNT:-12}

require_endpoint() {
  local name="$1"
  local url="$2"
  if ! curl -fsS "$url" >/dev/null 2>&1; then
    echo "Required endpoint is not reachable: $name ($url)" >&2
    exit 1
  fi
}

require_endpoint "order-service health" "$ORDER_URL/actuator/health"
require_endpoint "payment-service health" "$PAYMENT_URL/actuator/health"

echo "Ensuring payment-service is in recovered mode..."
curl -fsS -X POST "$PAYMENT_URL/payments/simulate/recover" >/dev/null || true

echo "Seeding baseline orders (count=$COUNT)..."
for i in $(seq 1 "$COUNT"); do
  curl -fsS -X POST "$ORDER_URL/orders" \
    -H "Content-Type: application/json" \
    -d "{\"amount\":100,\"currency\":\"USD\",\"customerEmail\":\"seed$i@example.com\"}" >/dev/null
done

echo "Seed complete."

