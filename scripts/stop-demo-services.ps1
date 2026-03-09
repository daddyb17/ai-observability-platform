$ErrorActionPreference = "Stop"

$pidFile = Join-Path $PSScriptRoot ".demo-services.pids"

if (-not (Test-Path $pidFile)) {
    Write-Host "No PID file found at $pidFile. Nothing to stop."
    exit 0
}

$entries = Get-Content $pidFile | Where-Object { $_.Trim().Length -gt 0 }

foreach ($entry in $entries) {
    $parts = $entry.Split(",")
    if ($parts.Length -lt 2) {
        continue
    }

    $processId = [int]$parts[0]
    $name = $parts[1]

    try {
        $process = Get-Process -Id $processId -ErrorAction Stop
        Stop-Process -Id $processId -Force
        Write-Host "Stopped $name (PID $processId)"
    } catch {
        Write-Host "Process for $name (PID $processId) is not running."
    }
}

Remove-Item $pidFile -Force
Write-Host "Demo services stop sequence complete."
