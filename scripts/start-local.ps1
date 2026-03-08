$ErrorActionPreference = "Stop"

try {
    docker info | Out-Null
} catch {
    throw "Docker daemon is not running. Start Docker Desktop and retry."
}

docker compose up -d postgres zookeeper kafka redis elasticsearch prometheus grafana jaeger kibana kafka-ui kafka-init
& "$PSScriptRoot/verify-local.ps1"

Write-Host "Infra is ready."
Write-Host "Prometheus: http://localhost:9090"
Write-Host "Grafana:    http://localhost:3000 (admin/admin)"
Write-Host "Jaeger:     http://localhost:16686"
Write-Host "Kibana:     http://localhost:5601"
Write-Host "Kafka UI:   http://localhost:8089"
