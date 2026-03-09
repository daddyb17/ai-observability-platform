$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$paymentUrl = if ($env:PAYMENT_URL) { $env:PAYMENT_URL } else { "http://localhost:8092" }
$incidentUrl = if ($env:INCIDENT_URL) { $env:INCIDENT_URL } else { "http://localhost:8084" }
$aiUrl = if ($env:AI_URL) { $env:AI_URL } else { "http://localhost:8085" }
$notificationUrl = if ($env:NOTIFICATION_URL) { $env:NOTIFICATION_URL } else { "http://localhost:8086" }
$waitSeconds = if ($env:WAIT_SECONDS) { [int]$env:WAIT_SECONDS } else { 40 }
$loadCount = if ($env:LOAD_COUNT) { [int]$env:LOAD_COUNT } else { 50 }
$analysisWaitSeconds = if ($env:ANALYSIS_WAIT_SECONDS) { [int]$env:ANALYSIS_WAIT_SECONDS } else { 60 }
$alertWaitSeconds = if ($env:ALERT_WAIT_SECONDS) { [int]$env:ALERT_WAIT_SECONDS } else { 45 }

function Require-Endpoint {
    param(
        [string]$Name,
        [string]$Url,
        [int]$MaxRetries = 24,
        [int]$SleepSeconds = 5
    )

    for ($attempt = 1; $attempt -le $MaxRetries; $attempt++) {
        try {
            $response = Invoke-WebRequest -Method Get -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        } catch {
            if ($attempt -eq $MaxRetries) {
                throw "Required endpoint is not reachable: $Name ($Url)"
            }
        }
        Start-Sleep -Seconds $SleepSeconds
    }
}

Require-Endpoint -Name "payment-service health" -Url "$paymentUrl/actuator/health"
Require-Endpoint -Name "incident-detection-service health" -Url "$incidentUrl/actuator/health"
Require-Endpoint -Name "ai-analysis-service health" -Url "$aiUrl/actuator/health"
Require-Endpoint -Name "notification-service health" -Url "$notificationUrl/actuator/health"

Write-Host "Running outage simulation..."
$env:WAIT_SECONDS = $waitSeconds
$env:LOAD_COUNT = $loadCount
powershell -ExecutionPolicy Bypass -File "$scriptDir\simulate-payment-outage.ps1"

$incidents = Invoke-RestMethod -Method Get -Uri "$incidentUrl/api/incidents"
if (-not $incidents -or $incidents.Count -eq 0) {
    throw "No incident was created during outage simulation."
}
$incidentId = $incidents[0].id
Write-Host "Incident detected: $incidentId"

Write-Host "Validating AI analysis generation..."
$analysisDeadline = (Get-Date).AddSeconds($analysisWaitSeconds)
$analysisOk = $false
while ((Get-Date) -lt $analysisDeadline) {
    try {
        $analysis = Invoke-RestMethod -Method Get -Uri "$aiUrl/api/incidents/$incidentId/analysis"
        if ($analysis.summary -and $analysis.status -ne "PENDING") {
            $analysisOk = $true
            break
        }
    } catch {
    }
    Start-Sleep -Seconds 5
}
if (-not $analysisOk) {
    throw "AI analysis was not generated within timeout for incident $incidentId."
}

Write-Host "Validating alert history generation..."
$alertDeadline = (Get-Date).AddSeconds($alertWaitSeconds)
$alertsOk = $false
while ((Get-Date) -lt $alertDeadline) {
    try {
        $alerts = Invoke-RestMethod -Method Get -Uri "$notificationUrl/api/alerts?incidentId=$incidentId&limit=10"
        if ($alerts -and $alerts.Count -gt 0) {
            $alertsOk = $true
            break
        }
    } catch {
    }
    Start-Sleep -Seconds 5
}
if (-not $alertsOk) {
    throw "No alert history found for incident $incidentId."
}

Write-Host "Recovering payment-service..."
Invoke-WebRequest -Method Post -Uri "$paymentUrl/payments/simulate/recover" -UseBasicParsing | Out-Null

Write-Host "E2E outage scenario passed for incident $incidentId."
