[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$RabbitMqUrl = "http://localhost:15672",
    [string]$RabbitMqUser = "egaming",
    [string]$RabbitMqPassword = "egaming",
    [string]$DataFile = "$PSScriptRoot\..\data\comments-300.ndjson",
    [int]$Count = 0,
    [int]$ReadyTimeoutSeconds = 120,
    [int]$DrainTimeoutSeconds = 3600
)

$ErrorActionPreference = "Stop"

function Wait-ForAppReady {
    param([int]$TimeoutSeconds)
    Write-Host "Waiting for the app to report healthy at $BaseUrl/actuator/health ..."
    $elapsed = 0
    while ($elapsed -lt $TimeoutSeconds) {
        try {
            $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -UseBasicParsing
            if ($health.status -eq "UP") {
                Write-Host "App is healthy."
                return
            }
        } catch {
            # not ready yet, keep polling
        }
        Start-Sleep -Seconds 3
        $elapsed += 3
    }
    throw "App did not report healthy within $TimeoutSeconds seconds. Is 'docker compose up -d' running and is the app started?"
}

function Get-QueueDepth {
    $cred = [System.Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("$($RabbitMqUser):$($RabbitMqPassword)"))
    $headers = @{ Authorization = "Basic $cred" }
    $queue = Invoke-RestMethod -Uri "$RabbitMqUrl/api/queues/%2f/comments.analysis.q" -Headers $headers -Method Get
    return [int]$queue.messages_ready + [int]$queue.messages_unacknowledged
}

function Wait-ForQueueDrain {
    param([int]$TimeoutSeconds)
    Write-Host "Waiting for the analysis pipeline to drain (LLM classification is the slow step - this can take a while on CPU-only inference)..."
    # RabbitMQ's management API refreshes its stats on an interval, not in real time,
    # so a check made immediately after posting can read a stale "0" - settle first,
    # then require two consecutive zero readings before trusting it.
    Start-Sleep -Seconds 5
    $elapsed = 5
    $lastPrint = -1
    $consecutiveZero = 0
    while ($elapsed -lt $TimeoutSeconds) {
        $depth = Get-QueueDepth
        if ($depth -eq 0) {
            $consecutiveZero++
            if ($consecutiveZero -ge 2) {
                Write-Host "Queue drained after $elapsed s."
                return
            }
        } else {
            $consecutiveZero = 0
        }
        if ($elapsed - $lastPrint -ge 15) {
            Write-Host "  ... still processing, $depth message(s) remaining ($elapsed s elapsed)"
            $lastPrint = $elapsed
        }
        Start-Sleep -Seconds 5
        $elapsed += 5
    }
    Write-Warning "Queue did not fully drain within $TimeoutSeconds s - continuing anyway, some insight answers may reflect partial data."
}

function Invoke-InsightQuery {
    param(
        [string]$Question,
        [hashtable]$Filters
    )

    $requestBody = @{ question = $Question }
    if ($Filters) { $requestBody.filters = $Filters }
    $json = $requestBody | ConvertTo-Json -Depth 6

    Write-Host ""
    Write-Host "======================================================================"
    Write-Host "Q: $Question"
    Write-Host "======================================================================"

    $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/insights/query" -Method Post -ContentType "application/json" -Body $json

    Write-Host ""
    Write-Host "Answer:"
    Write-Host "  $($response.answer)"

    Write-Host ""
    $negPct = [math]::Round($response.aggregates.negativeShare * 100, 1)
    $topAspectsText = ($response.aggregates.topAspects | ForEach-Object { "$($_.aspect)=$($_.count)" }) -join ", "
    Write-Host "Aggregates: totalComments=$($response.aggregates.totalComments) negativeShare=$negPct% topAspects=[$topAspectsText]"

    if ($response.citations.Count -gt 0) {
        Write-Host ""
        Write-Host "Citations:"
        foreach ($citation in $response.citations) {
            Write-Host "  - [$($citation.source)] `"$($citation.excerpt)`" (id=$($citation.id))"
        }
    }
}

# 1. Readiness
Wait-ForAppReady -TimeoutSeconds $ReadyTimeoutSeconds

# 2. Load and post the demo corpus
if (-not (Test-Path $DataFile)) {
    throw "Demo data file not found: $DataFile"
}
$lines = Get-Content $DataFile
if ($Count -gt 0) {
    $lines = $lines | Select-Object -First $Count
}

Write-Host ""
Write-Host "Posting $($lines.Count) comment(s) to $BaseUrl/api/v1/comments ..."
$accepted = 0
$duplicates = 0
foreach ($line in $lines) {
    try {
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/comments" -Method Post -ContentType "application/json" -Body $line -UseBasicParsing | Out-Null
        $accepted++
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 409) {
            $duplicates++
        } else {
            throw
        }
    }
}
Write-Host "Posted: $accepted accepted, $duplicates duplicate(s) (already ingested from a previous run)."

# 3. Wait for the async pipeline (classification + embedding) to finish
Wait-ForQueueDrain -TimeoutSeconds $DrainTimeoutSeconds

# 4. Run insight queries that exploit the planted patterns in the corpus
Invoke-InsightQuery -Question "Why are players unhappy, and what should we do about it?"

Invoke-InsightQuery -Question "What are players saying about withdrawal delays recently?" -Filters @{
    aspects = @("WITHDRAWAL_DELAY")
    from    = "2026-07-20"
    to      = "2026-08-03"
}

Invoke-InsightQuery -Question "What do players think about the bonus wagering requirements?" -Filters @{
    aspects = @("BONUS_WAGERING")
}

Write-Host ""
Write-Host "Demo complete."
