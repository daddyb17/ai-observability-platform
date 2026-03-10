$ErrorActionPreference = "Stop"

$paymentUrl = if ($env:PAYMENT_URL) { $env:PAYMENT_URL } else { "http://localhost:8092" }
$orderUrl = if ($env:ORDER_URL) { $env:ORDER_URL } else { "http://localhost:8091" }
$incidentUrl = if ($env:INCIDENT_URL) { $env:INCIDENT_URL } else { "http://localhost:8084" }
$aiUrl = if ($env:AI_URL) { $env:AI_URL } else { "http://localhost:8085" }
$notificationUrl = if ($env:NOTIFICATION_URL) { $env:NOTIFICATION_URL } else { "http://localhost:8086" }
$loadCount = if ($env:LOAD_COUNT) { [int]$env:LOAD_COUNT } else { 120 }
$waitSeconds = if ($env:WAIT_SECONDS) { [int]$env:WAIT_SECONDS } else { 60 }
$scenarioStart = if ($env:SCENARIO_START_UTC) { [DateTimeOffset]::Parse($env:SCENARIO_START_UTC).ToUniversalTime() } else { [DateTimeOffset]::UtcNow }

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

Write-Host "Enabling payment timeout simulation..."
Invoke-WebRequest -Method Post -Uri "$paymentUrl/payments/simulate/timeout" -UseBasicParsing | Out-Null

Write-Host "Generating order load test traffic (count=$loadCount)..."
$body = @{ count = $loadCount } | ConvertTo-Json
Invoke-WebRequest -Method Post -Uri "$orderUrl/orders/load-test" -ContentType "application/json" -Body $body -UseBasicParsing | Out-Null

Write-Host "Waiting $waitSeconds seconds for incident correlation..."
Start-Sleep -Seconds $waitSeconds

$incidents = Invoke-RestMethod -Method Get -Uri "$incidentUrl/api/incidents"
$recentIncidents = @()
if ($incidents) {
    $recentIncidents = @($incidents | Where-Object {
            try {
                $_.createdAt -and ([DateTimeOffset]::Parse($_.createdAt).ToUniversalTime() -ge $scenarioStart)
            } catch {
                $false
            }
        })
}
if (-not $recentIncidents -or $recentIncidents.Count -eq 0) {
    Write-Host "No incident detected yet for this scenario run."
    exit 0
}

$incidentId = ($recentIncidents | Sort-Object createdAt -Descending | Select-Object -First 1).id
Write-Host "Latest incident: $incidentId"
Write-Host "Triggering analysis for incident..."
try {
    Invoke-WebRequest -Method Post -Uri "$incidentUrl/api/incidents/$incidentId/analyze" -UseBasicParsing | Out-Null
} catch {
}
Start-Sleep -Seconds 5

Write-Host "Incident snapshot:"
Invoke-WebRequest -Method Get -Uri "$incidentUrl/api/incidents/$incidentId" -UseBasicParsing | Select-Object -ExpandProperty Content
Write-Host ""

Write-Host "AI analysis snapshot:"
try {
    Invoke-WebRequest -Method Get -Uri "$aiUrl/api/incidents/$incidentId/analysis" -UseBasicParsing | Select-Object -ExpandProperty Content
} catch {
    Write-Host "AI analysis not available yet."
}
Write-Host ""

Write-Host "Recent alerts for incident:"
try {
    Invoke-WebRequest -Method Get -Uri "$notificationUrl/api/alerts?incidentId=$incidentId&limit=10" -UseBasicParsing | Select-Object -ExpandProperty Content
} catch {
    Write-Host "Notification service not available."
}
Write-Host ""

Write-Host "Simulation complete. Recover with: Invoke-WebRequest -Method Post -Uri $paymentUrl/payments/simulate/recover"
