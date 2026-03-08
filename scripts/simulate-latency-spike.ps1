$ErrorActionPreference = "Stop"

$paymentUrl = if ($env:PAYMENT_URL) { $env:PAYMENT_URL } else { "http://localhost:8092" }
$orderUrl = if ($env:ORDER_URL) { $env:ORDER_URL } else { "http://localhost:8091" }
$incidentUrl = if ($env:INCIDENT_URL) { $env:INCIDENT_URL } else { "http://localhost:8084" }
$loadCount = if ($env:LOAD_COUNT) { [int]$env:LOAD_COUNT } else { 30 }
$waitSeconds = if ($env:WAIT_SECONDS) { [int]$env:WAIT_SECONDS } else { 25 }

function Require-Endpoint {
    param(
        [string]$Name,
        [string]$Url
    )
    try {
        Invoke-WebRequest -Method Get -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
    } catch {
        throw "Required endpoint is not reachable: $Name ($Url)"
    }
}

Require-Endpoint -Name "payment-service health" -Url "$paymentUrl/actuator/health"
Require-Endpoint -Name "order-service health" -Url "$orderUrl/actuator/health"
Require-Endpoint -Name "incident-detection-service health" -Url "$incidentUrl/actuator/health"

Write-Host "Enabling payment latency simulation..."
Invoke-WebRequest -Method Post -Uri "$paymentUrl/payments/simulate/latency" -UseBasicParsing | Out-Null

Write-Host "Generating load to surface latency signal (count=$loadCount)..."
$body = @{ count = $loadCount } | ConvertTo-Json
Invoke-WebRequest -Method Post -Uri "$orderUrl/orders/load-test" -ContentType "application/json" -Body $body -UseBasicParsing | Out-Null

Write-Host "Waiting $waitSeconds seconds for metrics/incident processing..."
Start-Sleep -Seconds $waitSeconds

Write-Host "Current incidents:"
try {
    Invoke-WebRequest -Method Get -Uri "$incidentUrl/api/incidents" -UseBasicParsing | Select-Object -ExpandProperty Content
} catch {
    Write-Host "Incident service not available."
}
Write-Host ""
Write-Host "Latency spike simulation complete."
