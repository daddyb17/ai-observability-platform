#!/usr/bin/env bash
set -euo pipefail

PAYMENT_URL=${PAYMENT_URL:-http://localhost:8092}

echo "Enabling payment latency simulation..."
curl -s -X POST "$PAYMENT_URL/payments/simulate/latency" >/dev/null || true

echo "Done."

