$ErrorActionPreference = 'Stop'
$baselineFile = [IO.Path]::GetTempFileName()
$currentFile = [IO.Path]::GetTempFileName()
$fixture = @{
    manufacturer='test'; model='test'; sdk=36; fingerprint='controlled-fixture'; emulator=$true
    refresh_rate_hz=60; power_state=1; corpus_sha256='deterministic'; measurement_scope='prepared_text_engine'
    source_id='source-a'; run_id='run-a'; font_scale=1.0; density_dpi=420; viewport_width_px=1080; viewport_height_px=2400
}
foreach ($metric in @('preview_pagination','pagination','cache_lookup','two_chapter_pagination')) {
    $fixture["${metric}_ms"] = @(1..10 | ForEach-Object { [double]$_ })
    $fixture["${metric}_p95_ms"] = 10.0
}
$fixture | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $baselineFile -Encoding UTF8
$passed = 0
function Expect-Rejection([string]$Name, [scriptblock]$Change) {
    $object = $fixture | ConvertTo-Json -Depth 5 | ConvertFrom-Json
    $current = @{}
    foreach ($property in $object.PSObject.Properties) { $current[$property.Name] = $property.Value }
    & $Change $current
    $current | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $currentFile -Encoding UTF8
    $rejected = $false
    try { & "$PSScriptRoot/compare-reader-performance.ps1" -Baseline $baselineFile -Current $currentFile | Out-Null }
    catch { $rejected = $true }
    if (-not $rejected) { throw "Invalid evidence was accepted: $Name" }
    $script:passed++
}
try {
    $fixture | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $currentFile -Encoding UTF8
    & "$PSScriptRoot/compare-reader-performance.ps1" -Baseline $baselineFile -Current $currentFile
    $passed++
    Expect-Rejection 'missing metric' { param($v) $v.Remove('pagination_p95_ms') }
    Expect-Rejection 'zero metric' { param($v) $v.pagination_p95_ms = 0 }
    Expect-Rejection 'NaN' { param($v) $v.pagination_p95_ms = 'NaN' }
    Expect-Rejection 'Infinity' { param($v) $v.pagination_p95_ms = 'Infinity' }
    Expect-Rejection 'missing samples' { param($v) $v.Remove('pagination_ms') }
    Expect-Rejection 'too few samples' { param($v) $v.pagination_ms = @(1, 2) }
    Expect-Rejection 'invalid sample' { param($v) $v.pagination_ms[0] = -1 }
    Expect-Rejection 'different device build' { param($v) $v.fingerprint = 'changed' }
    Expect-Rejection 'different refresh rate' { param($v) $v.refresh_rate_hz = 120 }
    Expect-Rejection 'different font scale' { param($v) $v.font_scale = 2.0 }
    Expect-Rejection 'different density' { param($v) $v.density_dpi = 480 }
    Expect-Rejection 'different orientation' { param($v) $v.viewport_width_px = 2400; $v.viewport_height_px = 1080 }
    Expect-Rejection 'missing source' { param($v) $v.Remove('source_id') }
    Expect-Rejection 'inconsistent percentile' { param($v) $v.pagination_p95_ms = 9 }
    Expect-Rejection 'actual regression' { param($v) $v.pagination_ms = @(1..10 | ForEach-Object { $_ * 2.0 }); $v.pagination_p95_ms = 20 }
    Write-Host "$passed performance comparator cases passed."
} finally {
    Remove-Item -LiteralPath $baselineFile, $currentFile -Force
}
