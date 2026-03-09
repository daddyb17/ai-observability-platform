$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$outDir = Join-Path $repoRoot "docs\screenshots"
$liveDir = Join-Path $outDir "live"

$incidentUrl = if ($env:INCIDENT_URL) { $env:INCIDENT_URL } else { "http://localhost:8084" }
$aiUrl = if ($env:AI_URL) { $env:AI_URL } else { "http://localhost:8085" }
$notificationUrl = if ($env:NOTIFICATION_URL) { $env:NOTIFICATION_URL } else { "http://localhost:8086" }
$grafanaUrl = if ($env:GRAFANA_URL) { $env:GRAFANA_URL } else { "http://localhost:3000/d/platform-overview/platform-overview" }
$jaegerUrl = if ($env:JAEGER_URL) { $env:JAEGER_URL } else { "http://localhost:16686/search" }

New-Item -ItemType Directory -Force -Path $liveDir | Out-Null

function Find-Browser {
    $candidates = @("msedge", "chrome", "chromium", "google-chrome")
    foreach ($candidate in $candidates) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }
    return $null
}

function Capture-Url {
    param(
        [string]$Browser,
        [string]$Url,
        [string]$OutputPath
    )
    & $Browser --headless --disable-gpu --window-size=1600,900 "--screenshot=$OutputPath" "$Url" | Out-Null
}

function Write-JsonHtml {
    param(
        [string]$JsonFile,
        [string]$HtmlFile
    )
    $json = Get-Content -Raw -Path $JsonFile
    $escaped = $json.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;")
    $html = @"
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { font-family: Consolas, monospace; background: #f6f8fa; padding: 24px; }
    pre { background: #fff; padding: 18px; border: 1px solid #d0d7de; overflow: auto; }
  </style>
</head>
<body><pre>$escaped</pre></body>
</html>
"@
    Set-Content -Path $HtmlFile -Value $html -Encoding UTF8
}

Write-Host "Fetching latest incident..."
$incidents = Invoke-RestMethod -Method Get -Uri "$incidentUrl/api/incidents"
if (-not $incidents -or $incidents.Count -eq 0) {
    throw "No incidents available. Run outage simulation first."
}
$incidentId = $incidents[0].id
$incidents | ConvertTo-Json -Depth 20 | Set-Content -Path (Join-Path $liveDir "incidents-latest.json") -Encoding UTF8

Write-Host "Capturing live API evidence for incident $incidentId ..."
Invoke-RestMethod -Method Get -Uri "$incidentUrl/api/incidents/$incidentId" |
    ConvertTo-Json -Depth 20 |
    Set-Content -Path (Join-Path $liveDir "incident-api-response.json") -Encoding UTF8
Invoke-RestMethod -Method Get -Uri "$aiUrl/api/incidents/$incidentId/analysis" |
    ConvertTo-Json -Depth 20 |
    Set-Content -Path (Join-Path $liveDir "ai-analysis-response.json") -Encoding UTF8
Invoke-RestMethod -Method Get -Uri "$notificationUrl/api/alerts?incidentId=$incidentId&limit=10" |
    ConvertTo-Json -Depth 20 |
    Set-Content -Path (Join-Path $liveDir "alert-history-response.json") -Encoding UTF8

$browser = Find-Browser
if ($browser) {
    Write-Host "Using headless browser: $browser"
    try { Capture-Url -Browser $browser -Url $grafanaUrl -OutputPath (Join-Path $outDir "grafana-dashboard.png") } catch {}
    try { Capture-Url -Browser $browser -Url $jaegerUrl -OutputPath (Join-Path $outDir "jaeger-trace-bottleneck.png") } catch {}

    $incidentJson = Join-Path $liveDir "incident-api-response.json"
    $aiJson = Join-Path $liveDir "ai-analysis-response.json"
    $alertJson = Join-Path $liveDir "alert-history-response.json"

    $incidentHtml = Join-Path $liveDir "incident-api-response.html"
    $aiHtml = Join-Path $liveDir "ai-analysis-response.html"
    $alertHtml = Join-Path $liveDir "alert-history-response.html"

    Write-JsonHtml -JsonFile $incidentJson -HtmlFile $incidentHtml
    Write-JsonHtml -JsonFile $aiJson -HtmlFile $aiHtml
    Write-JsonHtml -JsonFile $alertJson -HtmlFile $alertHtml

    try { Capture-Url -Browser $browser -Url ("file:///" + ($incidentHtml -replace '\\', '/')) -OutputPath (Join-Path $outDir "incident-api-response.png") } catch {}
    try { Capture-Url -Browser $browser -Url ("file:///" + ($aiHtml -replace '\\', '/')) -OutputPath (Join-Path $outDir "ai-analysis-response.png") } catch {}
    try { Capture-Url -Browser $browser -Url ("file:///" + ($alertHtml -replace '\\', '/')) -OutputPath (Join-Path $outDir "alert-history-response.png") } catch {}
} else {
    Write-Host "No supported headless browser found. JSON evidence is available in $liveDir."
}

Write-Host "Live evidence capture completed."
