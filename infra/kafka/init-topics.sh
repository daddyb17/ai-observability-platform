#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER:-kafka:29092}"

create_topic() {
  local name="$1"
  local partitions="$2"
  kafka-topics \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --if-not-exists \
    --topic "$name" \
    --partitions "$partitions" \
    --replication-factor 1
}

echo "Creating Kafka topics on $BOOTSTRAP_SERVER ..."
create_topic "logs.raw" 3
create_topic "metrics.raw" 2
create_topic "traces.raw" 2
create_topic "incidents.detected" 2
create_topic "incidents.updated" 1
create_topic "ai.analysis.request" 1
create_topic "ai.analysis.result" 1
create_topic "alerts.outbound" 1
create_topic "deadletter.logs" 1
create_topic "deadletter.metrics" 1
create_topic "deadletter.traces" 1
create_topic "deadletter.incidents" 1
create_topic "deadletter.ai" 1
create_topic "deadletter.alerts" 1

echo "Topic creation completed."
kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --list | sort
