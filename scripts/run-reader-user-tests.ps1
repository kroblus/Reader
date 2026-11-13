param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.:-]+$')][string]$Serial,
    [switch]$RequirePhysical,
    [switch]$IncludeLargeCorpus,
    [switch]$ExcludeWebView,
    [string]$TestClass,
    [string]$PerformanceBaseline
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$started = [DateTime]::UtcNow
$runId = $started.ToString("yyyyMMddTHHmmssZ") + "-" + [guid]::NewGuid().ToString('N').Substring(0, 8)
$head = (& git -C $root rev-parse HEAD).Trim()
$sourceFiles = @(& git -C $root ls-files --cached --others --exclude-standard -- '*.kt' '*.kts' '*.toml' '*.xml' '*.ps1' '*.py' '*.sh' '*.yml' 'gradle.properties' '.gitattributes') | Sort-Object -Unique
$sourceEntries = foreach ($relative in $sourceFiles) {
    $path = Join-Path $root $relative
    if (Test-Path -LiteralPath $path -PathType Leaf) { "$relative $((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash)" }
}
$sha = [System.Security.Cryptography.SHA256]::Create()
try { $treeHash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($sourceEntries -join "`n"))))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
$sourceId = "$($head.Substring(0,12))-$($treeHash.Substring(0,12))"
$report = Join-Path $root "build\reports\reader-user-tests\$runId-$sourceId"
New-Item -ItemType Directory -Force $report | Out-Null
$sourceEntries | Set-Content -Encoding UTF8 (Join-Path $report 'source-files.sha256')
@{ runId=$runId; head=$head; sourceId=$sourceId; sourceTreeSha256=$treeHash; startedUtc=$started.ToString('o'); serial=$Serial } |
    ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $report 'run.json')
$qaPackage = 'com.lightreader.app.qa'
$previousSerial = $env:ANDROID_SERIAL
$failure = $null
$tests = 0; $failed = 0; $errors = 0; $skipped = 0
$animationSettings = @{}

function Invoke-Adb {
    $output = & adb -s $Serial @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb failed ($($args -join ' ')): $($output -join ' ')" }
    $output
}

function Copy-FreshEvidence([string]$Source, [string]$Destination) {
    if (-not (Test-Path -LiteralPath $Source)) { return }
    Get-ChildItem -LiteralPath $Source -Recurse -File | Where-Object { $_.LastWriteTimeUtc -ge $started } | ForEach-Object {
        $relative = $_.FullName.Substring($Source.TrimEnd('\').Length).TrimStart('\')
        $target = Join-Path $Destination $relative
        New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $target
    }
}

try {
    $deviceLine = (& adb devices -l | Select-String "^$([regex]::Escape($Serial))\s+device\b").Line
    if (-not $deviceLine) { throw "0 tests executed: $Serial is not online and authorized. Use an already authorized QA device or emulator." }
    $isEmulator = (Invoke-Adb shell getprop ro.kernel.qemu).Trim() -eq '1'
    if ($RequirePhysical -and $isEmulator) { throw "0 tests executed: $Serial is an emulator; this run requires a physical reference device." }
    @{
        serial=$Serial; physical=(-not $isEmulator)
        manufacturer=(Invoke-Adb shell getprop ro.product.manufacturer).Trim()
        model=(Invoke-Adb shell getprop ro.product.model).Trim()
        sdk=(Invoke-Adb shell getprop ro.build.version.sdk).Trim()
        fingerprint=(Invoke-Adb shell getprop ro.build.fingerprint).Trim()
        display=((Invoke-Adb shell dumpsys display) -join "`n")
        battery=((Invoke-Adb shell dumpsys battery) -join "`n")
        storage=((Invoke-Adb shell df /data) -join "`n")
    } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $report 'device.json')
    # Read-only installation policy preflight. Never change USB approvals, verifier or security settings.
    $restrictions = (Invoke-Adb shell dumpsys user) -join "`n"
    $restrictions | Set-Content -Encoding UTF8 (Join-Path $report 'install-policy.txt')
    if ($restrictions -match 'no_install_apps=true|no_install_unknown_sources=true') {
        throw '0 tests executed: device policy restricts package installation. Use an authorized isolated test environment.'
    }
    # AGP animationsDisabled may temporarily alter these non-security test settings.
    foreach ($setting in @('window_animation_scale','transition_animation_scale','animator_duration_scale')) {
        $animationSettings[$setting] = (Invoke-Adb shell settings get global $setting).Trim()
    }
    $animationSettings | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $report 'original-animation-settings.json')
    $env:ANDROID_SERIAL = $Serial
    $excluded = @('com.lightreader.app.ReaderProcessRecoveryInstrumentedTest', 'com.lightreader.app.BackupFreshInstallInstrumentedTest')
    if (-not $IncludeLargeCorpus) { $excluded += 'com.lightreader.app.LargeTxtImportInstrumentedTest' }
    if ($ExcludeWebView) { $excluded += 'com.lightreader.app.HtmlBridgeInstrumentedTest' }
    $gradleArguments = @(':app:connectedQaAndroidTest', '--console=plain',
        "-Pandroid.testInstrumentationRunnerArguments.runId=$runId", "-Pandroid.testInstrumentationRunnerArguments.sourceId=$sourceId")
    if ($excluded.Count) { $gradleArguments += "-Pandroid.testInstrumentationRunnerArguments.notClass=$($excluded -join ',')" }
    if ($TestClass) { $gradleArguments += "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass" }
    Push-Location $root
    try {
        & .\gradlew.bat @gradleArguments 2>&1 | Tee-Object -FilePath (Join-Path $report 'gradle.log')
        if ($LASTEXITCODE -ne 0) { throw 'Connected QA failed. Installation denials require an already authorized test environment; no device security settings were changed.' }
    } finally { Pop-Location }
    $qaApk = Join-Path $root 'app\build\outputs\apk\qa\app-qa.apk'
    Invoke-Adb install -r -t $qaApk | Set-Content -Encoding UTF8 (Join-Path $report 'qa-install.txt')
    if (-not $TestClass) {
        $qaTestApk = Join-Path $root 'app\build\outputs\apk\androidTest\qa\app-qa-androidTest.apk'
        Invoke-Adb install -r -t $qaTestApk | Set-Content -Encoding UTF8 (Join-Path $report 'qa-test-install.txt')
        foreach ($phase in @('prepareSavedAnchor', 'verifySavedAnchorInNewProcess')) {
            $phaseOutput = (& adb -s $Serial shell am instrument -w -e clearPackageData false -e runId $runId -e sourceId $sourceId -e class "com.lightreader.app.ReaderProcessRecoveryInstrumentedTest#$phase" 'com.lightreader.app.qa.test/androidx.test.runner.AndroidJUnitRunner' 2>&1) -join "`n"
            $phaseExit = $LASTEXITCODE
            $phaseOutput | Set-Content -Encoding UTF8 (Join-Path $report "process-recovery-$phase.txt")
            if ($phaseExit -eq 0 -and $phaseOutput -match 'OK\s*\(1 test\)' -and $phaseOutput -notmatch 'FAILURES|INSTRUMENTATION_FAILED') {
                $tests++
            } else {
                $counts = [regex]::Match($phaseOutput, 'Tests run:\s*(\d+),\s*Failures:\s*(\d+)')
                if ($counts.Success) { $tests += [int]$counts.Groups[1].Value; $failed += [int]$counts.Groups[2].Value }
                else { $errors++ }
                throw "New-process recovery phase $phase failed; see this run's phase log."
            }
            if ($phase -eq 'prepareSavedAnchor') { Invoke-Adb shell am force-stop $qaPackage | Out-Null }
        }
        $freshOutput = (& python (Join-Path $root 'scripts/run-backup-fresh-install.py') --serial $Serial --report-root (Join-Path $report 'backup-fresh-install') 2>&1) -join "`n"
        $freshExit = $LASTEXITCODE
        $freshOutput | Set-Content -Encoding UTF8 (Join-Path $report 'backup-fresh-install-summary.txt')
        $freshResult = $freshOutput | ConvertFrom-Json
        $tests += [int]$freshResult.tests; $failed += [int]$freshResult.failures; $errors += [int]$freshResult.errors
        if ($freshExit -ne 0 -or -not $freshResult.succeeded) {
            throw "Fresh-install backup recovery failed; see $($freshResult.report)."
        }
    }
    Invoke-Adb shell am force-stop $qaPackage | Out-Null
    Invoke-Adb shell am start -W -n "$qaPackage/com.lightreader.app.MainActivity" |
        Set-Content -Encoding UTF8 (Join-Path $report 'launch.txt')
    Invoke-Adb shell pidof $qaPackage | Set-Content -Encoding UTF8 (Join-Path $report 'pid.txt')
    @{path=$qaApk; sha256=(Get-FileHash -LiteralPath $qaApk -Algorithm SHA256).Hash; bytes=(Get-Item -LiteralPath $qaApk).Length} |
        ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $report 'apk.json')
    $remoteScreenshot = "/sdcard/lightreader-qa-$runId.png"
    Invoke-Adb shell screencap -p $remoteScreenshot
    Invoke-Adb pull $remoteScreenshot (Join-Path $report 'qa-home.png')
    Invoke-Adb shell rm $remoteScreenshot
} catch {
    $failure = $_
    $_.ToString() | Set-Content -Encoding UTF8 (Join-Path $report 'failure.txt')
} finally {
    $env:ANDROID_SERIAL = $previousSerial
    foreach ($entry in $animationSettings.GetEnumerator()) {
        if ($entry.Value -eq 'null') { & adb -s $Serial shell settings delete global $entry.Key 2>&1 | Out-Null }
        else { & adb -s $Serial shell settings put global $entry.Key $entry.Value 2>&1 | Out-Null }
        if ($LASTEXITCODE -ne 0) { $failure = "Could not restore non-security animation setting $($entry.Key)" }
    }
    Copy-FreshEvidence (Join-Path $root 'app\build\outputs\androidTest-results\connected\qa') (Join-Path $report 'android-test-results')
    Copy-FreshEvidence (Join-Path $root 'app\build\reports\androidTests\connected\qa') (Join-Path $report 'android-test-report')
    Copy-FreshEvidence (Join-Path $root 'app\build\outputs\connected_android_test_additional_output') (Join-Path $report 'evidence')
    # adb logcat waits indefinitely for an absent device; do not block failure reporting.
    $stillOnline = (& adb devices -l | Select-String "^$([regex]::Escape($Serial))\s+device\b").Line
    if ($stillOnline) {
        & adb -s $Serial pull "/sdcard/Download/LightReader-QA-Evidence/$runId" (Join-Path $report 'oem-evidence-fallback') 2>&1 |
            Set-Content -Encoding UTF8 (Join-Path $report 'evidence-pull.txt')
        & adb -s $Serial logcat -d -t 1500 2>&1 | Set-Content -Encoding UTF8 (Join-Path $report 'logcat.txt')
    } else {
        'Device unavailable; no device evidence could be collected.' | Set-Content -Encoding UTF8 (Join-Path $report 'device-evidence-unavailable.txt')
    }
    Get-ChildItem (Join-Path $report 'android-test-results') -Recurse -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            [xml]$result = Get-Content -LiteralPath $_.FullName -Raw
            foreach ($suite in $result.SelectNodes('//testsuite')) {
                $tests += [int]$suite.tests; $failed += [int]$suite.failures; $errors += [int]$suite.errors; $skipped += [int]$suite.skipped
            }
        } catch { $failure = $_ }
    }
    @{tests=$tests; failures=$failed; errors=$errors; skipped=$skipped; completedUtc=[DateTime]::UtcNow.ToString('o'); succeeded=($null -eq $failure -and $tests -gt 0 -and $failed -eq 0 -and $errors -eq 0)} |
        ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $report 'result.json')
}
if ($failure) { throw "$failure Evidence: $report" }
if ($tests -eq 0 -or $failed -gt 0 -or $errors -gt 0) { throw "QA did not pass: $tests tests, $failed failures, $errors errors. Evidence: $report" }
if ($PerformanceBaseline) {
    $current = Get-ChildItem -LiteralPath $report -Recurse -Filter 'reader-engine-benchmark.json' | Select-Object -First 1
    if (-not $current) { throw "This run produced no engine benchmark: $report" }
    & (Join-Path $root 'scripts\compare-reader-performance.ps1') -Baseline $PerformanceBaseline -Current $current.FullName
}
Write-Host "Reader QA passed: $tests tests. Evidence: $report"
