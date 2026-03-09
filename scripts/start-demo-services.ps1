$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
$pidFile = Join-Path $PSScriptRoot ".demo-services.pids"
$logDir = Join-Path $repoRoot "logs\demo-services"

if (-not (Test-Path $mavenWrapper)) {
    throw "Maven wrapper not found at $mavenWrapper"
}

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

if (Test-Path $pidFile) {
    Remove-Item $pidFile -Force
}

$postgresPort = $env:POSTGRES_PORT
if (-not $postgresPort) {
    try {
        $dockerPort = (docker compose port postgres 5432 2>$null | Select-Object -First 1)
        if ($dockerPort -and $dockerPort.Contains(":")) {
            $postgresPort = $dockerPort.Split(":")[-1].Trim()
        }
    } catch {
    }
}
if (-not $postgresPort) {
    $postgresPort = "55432"
}

if (-not $env:POSTGRES_URL) {
    $env:POSTGRES_URL = "jdbc:postgresql://localhost:$postgresPort/aiobs"
}
Write-Host "Using POSTGRES_URL=$($env:POSTGRES_URL)"

$services = @(
    @{ Module = "services/auth-service"; Port = 8081; Name = "auth-service" },
    @{ Module = "services/log-ingestion-service"; Port = 8082; Name = "log-ingestion-service" },
    @{ Module = "services/search-query-service"; Port = 8083; Name = "search-query-service" },
    @{ Module = "services/incident-detection-service"; Port = 8084; Name = "incident-detection-service" },
    @{ Module = "services/ai-analysis-service"; Port = 8085; Name = "ai-analysis-service" },
    @{ Module = "services/notification-service"; Port = 8086; Name = "notification-service" },
    @{ Module = "services/metrics-ingestion-service"; Port = 8087; Name = "metrics-ingestion-service" },
    @{ Module = "services/trace-ingestion-service"; Port = 8088; Name = "trace-ingestion-service" },
    @{ Module = "sample-apps/order-service"; Port = 8091; Name = "order-service" },
    @{ Module = "sample-apps/payment-service"; Port = 8092; Name = "payment-service" },
    @{ Module = "sample-apps/notification-sample-service"; Port = 8093; Name = "notification-sample-service" },
    @{ Module = "services/api-gateway"; Port = 8080; Name = "api-gateway" }
)

function Wait-Http {
    param(
        [string]$Name,
        [string]$Url,
        [int]$MaxRetries = 120,
        [int]$SleepSeconds = 5
    )

    for ($attempt = 1; $attempt -le $MaxRetries; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                Write-Host "Ready: $Name"
                return
            }
        } catch {
            # Keep waiting until timeout.
        }
        Start-Sleep -Seconds $SleepSeconds
    }

    throw "Timed out waiting for $Name at $Url"
}

foreach ($service in $services) {
    $modulePath = Join-Path $repoRoot $service.Module
    if (-not (Test-Path (Join-Path $modulePath "pom.xml"))) {
        throw "pom.xml not found for module: $($service.Module)"
    }

    $logPath = Join-Path $logDir "$($service.Name).log"
    $modulePom = Join-Path $modulePath "pom.xml"
    $command = "& ""$mavenWrapper"" -f ""$modulePom"" spring-boot:run *> ""$logPath"" 2>&1"
    $process = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoLogo", "-NoProfile", "-Command", $command -WorkingDirectory $repoRoot -PassThru

    Add-Content -Path $pidFile -Value "$($process.Id),$($service.Name),$($service.Port),$($service.Module)"
    Write-Host "Started $($service.Name) (PID $($process.Id)); log: $logPath"
}

if ($env:SKIP_DEMO_HEALTHCHECK -eq "true") {
    Write-Host "SKIP_DEMO_HEALTHCHECK=true, skipping health checks."
    exit 0
}

foreach ($service in $services) {
    Wait-Http -Name $service.Name -Url "http://localhost:$($service.Port)/actuator/health"
}

Write-Host "All demo services are running."
