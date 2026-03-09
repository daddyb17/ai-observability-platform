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

function Resolve-PostgresPort() {
    if ($env:POSTGRES_PORT) {
        return $env:POSTGRES_PORT
    }

    try {
        $dockerPort = (docker compose port postgres 5432 2>$null | Select-Object -First 1)
        if ($dockerPort -and $dockerPort.Contains(":")) {
            return $dockerPort.Split(":")[-1].Trim()
        }
    } catch {
    }

    return "55432"
}

function Wait-Tcp([string]$Name, [string]$TargetHost, [int]$Port, [int]$MaxRetries = 40, [int]$SleepSeconds = 5) {
    for ($i = 0; $i -lt $MaxRetries; $i++) {
        $client = New-Object System.Net.Sockets.TcpClient
        try {
            $iar = $client.BeginConnect($TargetHost, $Port, $null, $null)
            if ($iar.AsyncWaitHandle.WaitOne(2000, $false) -and $client.Connected) {
                $client.EndConnect($iar) | Out-Null
                return
            }
        } catch {
        } finally {
            $client.Close()
        }
        Start-Sleep -Seconds $SleepSeconds
    }
    throw "Timed out waiting for $Name at ${TargetHost}:$Port"
}

$postgresPort = [int](Resolve-PostgresPort)
Wait-Tcp -Name "PostgreSQL" -TargetHost "127.0.0.1" -Port $postgresPort
Wait-Http -Name "Elasticsearch" -Url "http://localhost:9200"
Wait-Http -Name "Prometheus" -Url "http://localhost:9090/-/ready"
Wait-Http -Name "Grafana" -Url "http://localhost:3000/api/health"
Wait-Http -Name "Jaeger" -Url "http://localhost:16686"
Wait-Http -Name "Kibana" -Url "http://localhost:5601/api/status"
Wait-Http -Name "Kafka UI" -Url "http://localhost:8089"

Write-Host "All Phase 2 infrastructure checks passed (PostgreSQL port: $postgresPort)."
