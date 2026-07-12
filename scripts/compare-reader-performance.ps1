param(
    [Parameter(Mandatory = $true)][string]$Baseline,
    [Parameter(Mandatory = $true)][string]$Current,
    [double]$MaxRegressionPercent = 15
)

$ErrorActionPreference = "Stop"
$baselineJson = Get-Content -Raw $Baseline | ConvertFrom-Json
$currentJson = Get-Content -Raw $Current | ConvertFrom-Json
if ($baselineJson.manufacturer -ne $currentJson.manufacturer -or $baselineJson.model -ne $currentJson.model) {
    throw "Performance evidence must come from the same manufacturer and model."
}

$metrics = @("first_visible_p95_ms", "pagination_p95_ms", "cache_lookup_p95_ms", "cross_chapter_ms")
$failures = @()
foreach ($metric in $metrics) {
    $before = [double]$baselineJson.$metric
    $after = [double]$currentJson.$metric
    if ($before -le 0) { throw "Baseline metric $metric must be greater than zero." }
    $regression = (($after - $before) / $before) * 100
    Write-Host ("{0}: baseline={1:N3} current={2:N3} regression={3:N2}%" -f $metric, $before, $after, $regression)
    if ($regression -gt $MaxRegressionPercent) { $failures += "$metric regressed by $([Math]::Round($regression, 2))%" }
}

if ($failures.Count -gt 0) { throw "Reader engine performance gate failed: $($failures -join '; ')" }
Write-Host "Reader engine performance gate passed."
