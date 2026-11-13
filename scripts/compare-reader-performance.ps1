param(
    [Parameter(Mandatory = $true)][string]$Baseline,
    [Parameter(Mandatory = $true)][string]$Current,
    [ValidateRange(0, 100)][double]$MaxRegressionPercent = 15,
    [ValidateRange(3, 1000)][int]$MinimumSamples = 10
)

$ErrorActionPreference = "Stop"
$baselineJson = Get-Content -LiteralPath $Baseline -Raw | ConvertFrom-Json
$currentJson = Get-Content -LiteralPath $Current -Raw | ConvertFrom-Json
function Require-Field($Data, [string]$Name) {
    if ($null -eq $Data.PSObject.Properties[$Name] -or $null -eq $Data.$Name -or [string]::IsNullOrWhiteSpace([string]$Data.$Name)) {
        throw "Missing performance field: $Name"
    }
}
function Require-PositiveNumber($Value, [string]$Name) {
    $number = 0.0
    if (-not [double]::TryParse([string]$Value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$number) -or
        [double]::IsNaN($number) -or [double]::IsInfinity($number) -or $number -le 0) { throw "Invalid positive finite number: $Name" }
    return $number
}
$environment = @('manufacturer','model','sdk','fingerprint','emulator','refresh_rate_hz','power_state','corpus_sha256','measurement_scope','font_scale','density_dpi','viewport_width_px','viewport_height_px')
foreach ($field in $environment + @('source_id','run_id')) {
    Require-Field $baselineJson $field; Require-Field $currentJson $field
    if ($field -in $environment -and $baselineJson.$field -ne $currentJson.$field) { throw "Incompatible measurement environment: $field" }
}
foreach ($field in @('refresh_rate_hz','font_scale','density_dpi','viewport_width_px','viewport_height_px')) {
    Require-PositiveNumber $baselineJson.$field $field | Out-Null
}
if ([int]$baselineJson.power_state -lt 0) { throw 'Power state was not measured.' }
if ($baselineJson.measurement_scope -eq 'prepared_text_engine') {
    $metrics = @('preview_pagination','pagination','cache_lookup','two_chapter_pagination')
} elseif ($baselineJson.measurement_scope -eq 'warm_ui_readable_frame') {
    $metrics = @('book_open','cached_turn','uncached_chapter','layout_change','tools_list')
} else { throw 'Unsupported measurement scope.' }
$failures = @()
foreach ($metric in $metrics) {
    foreach ($data in @($baselineJson, $currentJson)) {
        $sampleField = "${metric}_ms"; $p95Field = "${metric}_p95_ms"
        Require-Field $data $sampleField; Require-Field $data $p95Field
        $samples = @($data.$sampleField)
        if ($samples.Count -lt $MinimumSamples) { throw "$metric needs at least $MinimumSamples samples." }
        $values = @($samples | ForEach-Object { Require-PositiveNumber $_ $sampleField } | Sort-Object)
        $expected = $values[[Math]::Ceiling($values.Count * 0.95) - 1]
        $actual = Require-PositiveNumber $data.$p95Field $p95Field
        if ([Math]::Abs($actual - $expected) -gt [Math]::Max(0.000000001, [Math]::Abs($expected) * 0.000001)) { throw "p95 does not agree with samples: $metric" }
    }
    $before = [double]$baselineJson."${metric}_p95_ms"; $after = [double]$currentJson."${metric}_p95_ms"
    $regression = (($after - $before) / $before) * 100
    Write-Host ("{0}: baseline={1:N3} current={2:N3} regression={3:N2}%" -f $metric, $before, $after, $regression)
    if ($regression -gt $MaxRegressionPercent) { $failures += "$metric regressed by $([Math]::Round($regression, 2))%" }
}
if ($failures.Count) { throw "Performance comparison failed: $($failures -join '; ')" }
Write-Host 'Same-environment performance comparison passed; emulator results are not physical-device targets.'
