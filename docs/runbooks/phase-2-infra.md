# Phase 2 Infrastructure Runbook

## Start

- Bash: `./scripts/start-local.sh`
- PowerShell: `powershell -ExecutionPolicy Bypass -File .\scripts\start-local.ps1`

## Verify

- Bash: `./scripts/verify-local.sh`
- PowerShell: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1`

## Stop

- Bash: `./scripts/stop-local.sh`
- PowerShell: `powershell -ExecutionPolicy Bypass -File .\scripts\stop-local.ps1`

## Expected Endpoints

- `http://localhost:9090` Prometheus
- `http://localhost:3000` Grafana
- `http://localhost:16686` Jaeger
- `http://localhost:5601` Kibana
- `http://localhost:8089` Kafka UI
- `http://localhost:9200` Elasticsearch
- `localhost:55432` PostgreSQL (default; override with `POSTGRES_PORT`)
- `localhost:9092` Kafka
- `localhost:6379` Redis
