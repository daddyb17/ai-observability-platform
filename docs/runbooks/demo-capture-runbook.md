# Demo Capture Runbook

## Objective

Run a repeatable outage scenario and capture portfolio-ready evidence.

## Prerequisites

1. Infrastructure is running (`start-local` script).
2. Core demo services are running (see `scripts/start-demo-services.ps1` or `.sh`).
3. `POSTGRES_PORT` conflict handled (use `55432` if local Postgres occupies `5432`).

## Execution Steps

1. Seed baseline data:
   - PowerShell: `powershell -ExecutionPolicy Bypass -File .\scripts\seed-demo-data.ps1`
   - Bash: `./scripts/seed-demo-data.sh`
2. Trigger payment outage:
   - PowerShell: `powershell -ExecutionPolicy Bypass -File .\scripts\simulate-payment-outage.ps1`
   - Bash: `./scripts/simulate-payment-outage.sh`
3. Capture:
   - Grafana dashboard
   - Jaeger trace
   - Incident API output
   - AI analysis output
   - Alert history output
4. Recover payment-service:
   - `POST /payments/simulate/recover`
5. Stop demo services:
   - PowerShell: `powershell -ExecutionPolicy Bypass -File .\scripts\stop-demo-services.ps1`
   - Bash: `./scripts/stop-demo-services.sh`
6. Optionally validate latency scenario:
   - `simulate-latency-spike` script

## Suggested API Evidence Commands

1. `curl http://localhost:8084/api/incidents`
2. `curl http://localhost:8085/api/incidents/{incidentId}/analysis`
3. `curl "http://localhost:8086/api/alerts?incidentId={incidentId}&limit=10"`
