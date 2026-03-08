$ErrorActionPreference = "Stop"

$orderUrl = if ($env:ORDER_URL) { $env:ORDER_URL } else { "http://localhost:8091" }
$paymentUrl = if ($env:PAYMENT_URL) { $env:PAYMENT_URL } else { "http://localhost:8092" }
$count = if ($env:COUNT) { [int]$env:COUNT } else { 12 }

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

Require-Endpoint -Name "order-service health" -Url "$orderUrl/actuator/health"
Require-Endpoint -Name "payment-service health" -Url "$paymentUrl/actuator/health"

Write-Host "Ensuring payment-service is in recovered mode..."
try {
    Invoke-WebRequest -Method Post -Uri "$paymentUrl/payments/simulate/recover" -UseBasicParsing | Out-Null
} catch {
}

Write-Host "Seeding baseline orders (count=$count)..."
for ($i = 1; $i -le $count; $i++) {
    $body = @{
        amount = 100
        currency = "USD"
        customerEmail = "seed$i@example.com"
    } | ConvertTo-Json
    Invoke-WebRequest -Method Post -Uri "$orderUrl/orders" -ContentType "application/json" -Body $body -UseBasicParsing | Out-Null
}

Write-Host "Seed complete."
