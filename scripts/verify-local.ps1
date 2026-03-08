$ErrorActionPreference = "Stop"

try {
    docker info | Out-Null
} catch {
    throw "Docker daemon is not running. Start Docker Desktop and retry."
}

function Wait-Http([string]$Name, [string]$Url, [int]$MaxRetries = 40, [int]$SleepSeconds = 5) {
    for ($i = 0; $i -lt $MaxRetries; $i++) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
            return
        } catch {
            Start-Sleep -Seconds $SleepSeconds
        }
    }
    throw "Timed out waiting for $Name at $Url"
}

Wait-Http -Name "Elasticsearch" -Url "http://localhost:9200"
Wait-Http -Name "Prometheus" -Url "http://localhost:9090/-/ready"
Wait-Http -Name "Grafana" -Url "http://localhost:3000/api/health"
Wait-Http -Name "Jaeger" -Url "http://localhost:16686"
Wait-Http -Name "Kibana" -Url "http://localhost:5601/api/status"
Wait-Http -Name "Kafka UI" -Url "http://localhost:8089"

Write-Host "All Phase 2 infrastructure checks passed."
