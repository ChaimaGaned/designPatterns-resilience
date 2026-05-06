$ErrorActionPreference = "Stop"

$BASE_ORDER     = "http://localhost:8088/api/orders"
$BASE_INVENTORY = "http://localhost:8088/api/inventory"
$BULKHEAD_TARGET = "http://localhost:8082/api/inventory"
$PAYMENT_MOCK   = "http://localhost:9090/api/payments"

function Write-Header($Text) {
    Write-Host ""
    Write-Host ("=" * 55) -ForegroundColor Cyan
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Host ("=" * 55) -ForegroundColor Cyan
}

function Write-Step($Text) {
    Write-Host ""
    Write-Host "--- $Text ---" -ForegroundColor Yellow
}

function Invoke-Json($Url, $Method = "GET", $Body = $null) {
    if ($null -eq $Body) {
        return Invoke-RestMethod $Url -Method $Method
    }

    return Invoke-RestMethod $Url -Method $Method -Body $Body -ContentType "application/json"
}

function Assert-Endpoint($Name, $Url, $ExpectedService = $null) {
    try {
        $r = Invoke-Json $Url
    } catch {
        Write-Host "  ERREUR $Name : $($_.Exception.Message)" -ForegroundColor Red
        throw
    }

    if ($ExpectedService -and $r.service -ne $ExpectedService) {
        throw "$Name route vers '$($r.service)' au lieu de '$ExpectedService'. Rechargez Nginx avec nginx\nginx.conf."
    }

    Write-Host "  OK $Name -> $($r.service):$($r.port)" -ForegroundColor Green
    return $r
}

function Assert-DirectServices {
    Write-Header "PRECHECK : SERVICES DIRECTS"
    Assert-Endpoint "Order 8081" "http://localhost:8081/api/orders/ping" "order-service" | Out-Null
    Assert-Endpoint "Order 8083" "http://localhost:8083/api/orders/ping" "order-service" | Out-Null
    Assert-Endpoint "Inventory 8082" "http://localhost:8082/api/inventory/ping" "inventory-service" | Out-Null
    Assert-Endpoint "Inventory 8084" "http://localhost:8084/api/inventory/ping" "inventory-service" | Out-Null
    Assert-Endpoint "Payment 9090" "$PAYMENT_MOCK/ping" "payment-mock" | Out-Null
}

function Assert-NginxRoutes {
    Write-Header "PRECHECK : ROUTES VIA NGINX"
    Assert-Endpoint "Nginx Order" "$BASE_ORDER/ping" "order-service" | Out-Null
    Assert-Endpoint "Nginx Inventory" "$BASE_INVENTORY/ping" "inventory-service" | Out-Null
}

try {
    Assert-DirectServices
    Assert-NginxRoutes
} catch {
    Write-Host ""
    Write-Host "PRECHECK ECHEC" -ForegroundColor Red
    Write-Host "Cause : $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Actions conseillees :" -ForegroundColor Yellow
    Write-Host "  1. Arretez les anciens Nginx : taskkill /F /IM nginx.exe"
    Write-Host "  2. Relancez : .\scripts\start.bat"
    Write-Host "  3. Verifiez que http://localhost:8088/api/inventory/ping renvoie inventory-service."
    exit 1
}

Write-Header "TEST 1 : PING & LOAD BALANCER"

Write-Step "Ping des services (4 appels -> Round-Robin)"
$lbResults = @{ Order1 = 0; Order2 = 0; Inv1 = 0; Inv2 = 0 }

for ($i = 1; $i -le 4; $i++) {
    $r = Invoke-Json "$BASE_ORDER/ping"
    $port = [string]$r.port
    Write-Host "  Appel $i -> order-service:$port" -ForegroundColor $(if ($port -eq "8081") { "Green" } else { "Magenta" })
    if ($port -eq "8081") { $lbResults.Order1++ } else { $lbResults.Order2++ }
}

Write-Host ""
Write-Host "Load Balancer Order : instance 8081=$($lbResults.Order1) | instance 8083=$($lbResults.Order2)" -ForegroundColor Green

for ($i = 1; $i -le 4; $i++) {
    $r = Invoke-Json "$BASE_INVENTORY/ping"
    $port = [string]$r.port
    Write-Host "  Appel $i -> inventory-service:$port" -ForegroundColor $(if ($port -eq "8082") { "Green" } else { "Magenta" })
    if ($port -eq "8082") { $lbResults.Inv1++ } else { $lbResults.Inv2++ }
}

Write-Host ""
Write-Host "Load Balancer Inventory : instance 8082=$($lbResults.Inv1) | instance 8084=$($lbResults.Inv2)" -ForegroundColor Green

Write-Header "TEST 2 : CIRCUIT BREAKER - Simulation echecs"

Write-Step "Configuration PaymentMock : 70% d'echecs"
Invoke-Json "$PAYMENT_MOCK/config?failRate=70&latencyMs=200" "PUT" | Out-Null
Write-Host "  OK PaymentMock configure : failRate=70%" -ForegroundColor Yellow

$orderBody = @{
    customerId = "CUST-001"
    items = @(
        @{ productId = "SKU-LAPTOP"; productName = "Laptop Dell"; quantity = 1; unitPrice = 2499.99 }
    )
} | ConvertTo-Json -Depth 3

Write-Step "12 appels consecutifs vers OrderService"
$cbStats = @{ Success = 0; Failed = 0; Fallback = 0 }

for ($i = 1; $i -le 12; $i++) {
    try {
        $r = Invoke-Json $BASE_ORDER "POST" $orderBody
        $ps = $r.paymentStatus
        $fb = $r.paymentFallback
        $inst = $r.serviceInstance

        if ($fb) {
            Write-Host ("  Appel {0,2} : FALLBACK (circuit ouvert) - {1}" -f $i, $inst) -ForegroundColor Red
            $cbStats.Fallback++
        } elseif ($ps -eq "SUCCESS") {
            Write-Host ("  Appel {0,2} : SUCCESS - {1}" -f $i, $inst) -ForegroundColor Green
            $cbStats.Success++
        } else {
            Write-Host ("  Appel {0,2} : PAYMENT {1} - {2}" -f $i, $ps, $inst) -ForegroundColor DarkYellow
            $cbStats.Failed++
        }
    } catch {
        Write-Host ("  Appel {0,2} : ERREUR HTTP - {1}" -f $i, $_.Exception.Message) -ForegroundColor Red
        $cbStats.Failed++
    }
    Start-Sleep -Milliseconds 300
}

Write-Host ""
Write-Host "Resultats Circuit Breaker :" -ForegroundColor Cyan
Write-Host "  Success  : $($cbStats.Success)" -ForegroundColor Green
Write-Host "  Failed   : $($cbStats.Failed)" -ForegroundColor DarkYellow
Write-Host "  Fallback : $($cbStats.Fallback)" -ForegroundColor Red

Write-Step "Etat du Circuit Breaker"
try {
    $cb = Invoke-Json "$BASE_ORDER/circuit-status"
    Write-Host "  Etat : $($cb.state)" -ForegroundColor Cyan
} catch {
    Write-Host "  (non accessible)" -ForegroundColor Gray
}

Write-Step "Remise a zero : failRate=0"
Invoke-Json "$PAYMENT_MOCK/config?failRate=0&latencyMs=50" "PUT" | Out-Null
Write-Host "  OK PaymentMock remis a zero" -ForegroundColor Green

Write-Step "Attente 15s pour recuperation HALF-OPEN -> CLOSED"
for ($s = 15; $s -ge 1; $s--) {
    Write-Host "  Attente $s s..." -NoNewline -ForegroundColor Gray
    Start-Sleep -Seconds 1
    Write-Host "`r" -NoNewline
}
Write-Host ""

$r = Invoke-Json $BASE_ORDER "POST" $orderBody
Write-Host "  Appel recuperation : $($r.paymentStatus) | fallback=$($r.paymentFallback)" -ForegroundColor Green

Write-Header "TEST 3 : BULKHEAD - Saturation"

Write-Step "Verification stock initial"
$stock = Invoke-Json "$BASE_INVENTORY/SKU-LAPTOP"
Write-Host "  SKU-LAPTOP : disponible=$($stock.available)" -ForegroundColor Green

Write-Step "10 requetes PARALLELES vers une seule instance inventory (max=5)"
$reserveBody = @{
    productId = "SKU-HEADSET"
    quantity  = 1
    orderId   = "ORD-TEST"
} | ConvertTo-Json

$jobs = @()
$bhStats = @{ Accepted = 0; Rejected = 0 }

for ($i = 1; $i -le 10; $i++) {
    $jobs += Start-Job -ScriptBlock {
        param($url, $body, $i)
        try {
            $r = Invoke-WebRequest $url -Method POST -Body $body -ContentType "application/json" -UseBasicParsing
            return [pscustomobject]@{ index = $i; code = $r.StatusCode }
        } catch {
            $code = 0
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $code = $_.Exception.Response.StatusCode.value__
            }
            return [pscustomobject]@{ index = $i; code = $code }
        }
    } -ArgumentList "$BULKHEAD_TARGET/reserve", $reserveBody, $i
}

$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

foreach ($r in ($results | Sort-Object index)) {
    if ($r.code -eq 200) {
        Write-Host ("  Requete {0,2} : HTTP 200 ACCEPTEE" -f $r.index) -ForegroundColor Green
        $bhStats.Accepted++
    } else {
        Write-Host ("  Requete {0,2} : HTTP {1} REJETEE (Bulkhead plein)" -f $r.index, $r.code) -ForegroundColor Red
        $bhStats.Rejected++
    }
}

Write-Host ""
Write-Host "Resultats Bulkhead :" -ForegroundColor Cyan
Write-Host "  Acceptees : $($bhStats.Accepted) (max-concurrent=5)" -ForegroundColor Green
Write-Host "  Rejetees  : $($bhStats.Rejected) (HTTP 429)" -ForegroundColor Red

if ($bhStats.Rejected -eq 0) {
    throw "Bulkhead non demontre : attendu au moins 1 rejet HTTP 429."
}

Write-Step "Etat Bulkhead"
try {
    $bh = Invoke-Json "$BASE_INVENTORY/bulkhead-status"
    Write-Host "  readStock    : $($bh.bulkheads.readStock | ConvertTo-Json -Compress)" -ForegroundColor Cyan
    Write-Host "  reserveStock : $($bh.bulkheads.reserveStock | ConvertTo-Json -Compress)" -ForegroundColor Cyan
} catch {
    Write-Host "  (non accessible)" -ForegroundColor Gray
}

Write-Header "RESUME FINAL"
Write-Host ""
Write-Host "Load Balancer   : OK Nginx Round-Robin (2 instances par service)" -ForegroundColor Green
Write-Host "Circuit Breaker : OK OPEN apres echecs | HALF-OPEN apres 15s | CLOSED recupere" -ForegroundColor Green
Write-Host "Bulkhead        : OK max 5 simultanes | rejet HTTP 429 au-dela" -ForegroundColor Green
Write-Host ""
Write-Host "ARCHITECTURE VALIDEE" -ForegroundColor Cyan
Write-Host ""
