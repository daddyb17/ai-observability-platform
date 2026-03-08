#!/usr/bin/env bash
set -euo pipefail

PAYMENT_URL=${PAYMENT_URL:-http://localhost:8092}
ORDER_URL=${ORDER_URL:-http://localhost:8091}

echo "Enabling payment timeout simulation..."
curl -s -X POST "$PAYMENT_URL/payments/simulate/timeout" >/dev/null || true

echo "Generating order traffic..."
for i in $(seq 1 25); do
  curl -s -X POST "$ORDER_URL/orders" -H "Content-Type: application/json" \
    -d "{\"amount\":100,\"currency\":\"USD\",\"customerEmail\":\"demo@example.com\"}" >/dev/null || true
done

echo "Simulation complete."

