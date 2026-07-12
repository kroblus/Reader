param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [switch]$RequirePhysical,
    [switch]$IncludeLargeCorpus,
    [switch]$ExcludeWebView,
    [string]$TestClass,
    [string]$PerformanceBaseline
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$report = Join-Path $root "build\reports\reader-user-tests\$Serial"
New-Item -ItemType Directory -Force $report | Out-Null

function Invoke-Adb {
    & adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($args -join ' ')" }
}

function Ensure-AdbTestPackage([string]$PackageName, [string]$ApkPath) {
    & adb -s $Serial shell pm path $PackageName 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        $installOutput = (& adb -s $Serial install -r -t $ApkPath 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) {
            $installOutput | Set-Content -Encoding UTF8 (Join-Path $report "install-preflight.txt")
            if ($installOutput -match "INSTALL_FAILED_USER_RESTRICTED") {
                throw "0 tests executed: $Serial rejected USB package installation. Unlock the device and enable USB installation / USB debugging security settings, or run .\scripts\run-reader-user-tests.ps1 -Serial emulator-5554."
            }
            throw "0 tests executed: failed to install $PackageName during QA preflight. See $report\install-preflight.txt"
        }
    }
}

$deviceLine = (& adb devices -l | Select-String "^$([regex]::Escape($Serial))\s+device\b").Line
if (-not $deviceLine) { throw "Device $Serial is not online and authorized." }
$isEmulator = (Invoke-Adb shell getprop ro.kernel.qemu).Trim() -eq "1"
if ($RequirePhysical -and $isEmulator) { throw "A physical device is required, but $Serial is an emulator." }

$metadata = [ordered]@{
    serial = $Serial
    adb = $deviceLine
    manufacturer = (Invoke-Adb shell getprop ro.product.manufacturer).Trim()
    model = (Invoke-Adb shell getprop ro.product.model).Trim()
    sdk = (Invoke-Adb shell getprop ro.build.version.sdk).Trim()
    fingerprint = (Invoke-Adb shell getprop ro.build.fingerprint).Trim()
    locale = (Invoke-Adb shell getprop persist.sys.locale).Trim()
    battery = ((Invoke-Adb shell dumpsys battery) -join "`n")
    storage = ((Invoke-Adb shell df /data) -join "`n")
    physical = -not $isEmulator
}
$metadata | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $report "device.json")
"QA install preflight started for $Serial" | Set-Content -Encoding UTF8 (Join-Path $report "preflight.txt")

$excluded = @()
if (-not $IncludeLargeCorpus) { $excluded += "com.lightreader.app.LargeTxtImportInstrumentedTest" }
if ($ExcludeWebView) { $excluded += "com.lightreader.app.HtmlBridgeInstrumentedTest" }
$gradleArguments = @(
    ":app:connectedQaAndroidTest",
    "--console=plain"
)
if ($excluded.Count -gt 0) {
    $gradleArguments += "-Pandroid.testInstrumentationRunnerArguments.notClass=$($excluded -join ',')"
}
if ($TestClass) {
    $gradleArguments += "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
}

$serviceGuardPackages = @("androidx.test.services", "androidx.test.orchestrator")
$serviceGuardJob = $null
$qaApk = Join-Path $root "app\build\outputs\apk\qa\app-qa.apk"
$qaTestApk = Join-Path $root "app\build\outputs\apk\androidTest\qa\app-qa-androidTest.apk"
if (-not (Test-Path $qaApk)) { throw "QA APK was not produced: $qaApk" }
if (-not (Test-Path $qaTestApk)) { throw "QA test APK was not produced: $qaTestApk" }
$usbApprovalOverrides = [ordered]@{
    "usb_install_item_androidx.test.services" = "androidx.test.services:1"
    "usb_install_item_androidx.test.orchestrator" = "androidx.test.orchestrator:1"
    "usb_install_item_com.lightreader.app.qa" = "com.lightreader.app.qa:1"
    "usb_install_item_com.lightreader.app.qa.test" = "com.lightreader.app.qa.test:1"
}
$originalUsbApprovals = @{}
if (-not $isEmulator) {
    try {
        foreach ($entry in $usbApprovalOverrides.GetEnumerator()) {
            $originalUsbApprovals[$entry.Key] = (Invoke-Adb shell settings get secure $entry.Key).Trim()
            Invoke-Adb shell settings put secure $entry.Key $entry.Value
        }
        $testServicesApk = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\androidx.test.services\test-services" -Recurse -Filter "test-services-*.apk" -ErrorAction Stop |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        $orchestratorApk = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\androidx.test\orchestrator" -Recurse -Filter "orchestrator-*.apk" -ErrorAction Stop |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        Ensure-AdbTestPackage "androidx.test.services" $testServicesApk.FullName
        Ensure-AdbTestPackage "androidx.test.orchestrator" $orchestratorApk.FullName
        Ensure-AdbTestPackage "com.lightreader.app.qa" $qaApk
        Ensure-AdbTestPackage "com.lightreader.app.qa.test" $qaTestApk
        foreach ($package in $serviceGuardPackages) {
            & adb -s $Serial shell cmd deviceidle whitelist "+$package" | Out-Null
            & adb -s $Serial shell am set-inactive $package false | Out-Null
            & adb -s $Serial shell am set-standby-bucket $package active | Out-Null
        }
        # Some OEM ROMs freeze the SpeakEasy service between orchestrated tests. A short-lived
        # shell guard keeps only the AndroidX test infrastructure unfrozen during this run.
        $serviceGuardJob = Start-Job -ArgumentList $Serial -ScriptBlock {
            param($TargetSerial)
            while ($true) {
                & adb -s $TargetSerial shell cmd activity unfreeze --sticky androidx.test.services 2>$null | Out-Null
                & adb -s $TargetSerial shell cmd activity unfreeze --sticky androidx.test.orchestrator 2>$null | Out-Null
                Start-Sleep -Seconds 3
            }
        }
    } catch {
        foreach ($entry in $originalUsbApprovals.GetEnumerator()) {
            if ($entry.Value -eq "null") {
                & adb -s $Serial shell settings delete secure $entry.Key 2>$null | Out-Null
            } else {
                & adb -s $Serial shell settings put secure $entry.Key $entry.Value 2>$null | Out-Null
            }
        }
        throw
    }
}

$previousSerial = $env:ANDROID_SERIAL
try {
    $env:ANDROID_SERIAL = $Serial
    Push-Location $root
    & .\gradlew.bat @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "The connected QA user-journey suite failed." }
} finally {
    Pop-Location
    $env:ANDROID_SERIAL = $previousSerial
    if ($serviceGuardJob) {
        Stop-Job $serviceGuardJob -ErrorAction SilentlyContinue
        Remove-Job $serviceGuardJob -Force -ErrorAction SilentlyContinue
        foreach ($package in $serviceGuardPackages) {
            & adb -s $Serial shell cmd deviceidle whitelist "-$package" 2>$null | Out-Null
        }
    }
    if (-not $isEmulator) {
        foreach ($entry in $originalUsbApprovals.GetEnumerator()) {
            if ($entry.Value -eq "null") {
                & adb -s $Serial shell settings delete secure $entry.Key 2>$null | Out-Null
            } else {
                & adb -s $Serial shell settings put secure $entry.Key $entry.Value 2>$null | Out-Null
            }
        }
    }
}

$xmlResults = Join-Path $root "app\build\outputs\androidTest-results\connected\qa"
if (Test-Path $xmlResults) {
    $xmlDestination = Join-Path $report "android-test-results"
    New-Item -ItemType Directory -Force $xmlDestination | Out-Null
    Copy-Item (Join-Path $xmlResults "*") $xmlDestination -Recurse -Force
}
$htmlResults = Join-Path $root "app\build\reports\androidTests\connected\qa"
if (Test-Path $htmlResults) {
    $htmlDestination = Join-Path $report "android-test-report"
    New-Item -ItemType Directory -Force $htmlDestination | Out-Null
    Copy-Item (Join-Path $htmlResults "*") $htmlDestination -Recurse -Force
}

$qaPackage = "com.lightreader.app.qa"
$fallbackEvidence = "/sdcard/Download/LightReader-QA-Evidence"
& adb -s $Serial shell test -d $fallbackEvidence
if ($LASTEXITCODE -eq 0) {
    Invoke-Adb pull $fallbackEvidence (Join-Path $report "oem-evidence-fallback")
    Invoke-Adb shell rm -rf $fallbackEvidence
}
# connectedAndroidTest uninstalls its target package after the run, so reinstall the
# isolated QA artifact before launch smoke. The production package is never touched.
Invoke-Adb install -r $qaApk
Invoke-Adb logcat -c
Invoke-Adb shell am force-stop $qaPackage
$launch = Invoke-Adb shell am start -W -n "$qaPackage/com.lightreader.app.MainActivity"
$launch | Set-Content -Encoding UTF8 (Join-Path $report "launch.txt")
Invoke-Adb shell pidof $qaPackage | Set-Content -Encoding UTF8 (Join-Path $report "pid.txt")
Invoke-Adb logcat -d -t 300 | Set-Content -Encoding UTF8 (Join-Path $report "logcat.txt")
$packageDump = Invoke-Adb shell dumpsys package $qaPackage
$packageDump | Select-String -Pattern "versionCode=|versionName=|SigningInfo" |
    Set-Content -Encoding UTF8 (Join-Path $report "installed-package.txt")
$apkEvidence = [ordered]@{
    path = $qaApk
    bytes = (Get-Item $qaApk).Length
    sha256 = (Get-FileHash $qaApk -Algorithm SHA256).Hash
}
$apkEvidence | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $report "apk.json")
$sdkProperty = (Get-Content (Join-Path $root "local.properties") | Where-Object { $_ -like "sdk.dir=*" } | Select-Object -First 1).Substring(8)
$sdkPath = $sdkProperty -replace '\\:', ':' -replace '\\\\', '\'
$apksigner = Get-ChildItem (Join-Path $sdkPath "build-tools") -Recurse -Filter "apksigner.bat" |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($apksigner) {
    & $apksigner.FullName verify --print-certs $qaApk |
        Set-Content -Encoding UTF8 (Join-Path $report "apk-signature.txt")
    if ($LASTEXITCODE -ne 0) { throw "QA APK signature verification failed." }
}
$remoteScreenshot = "/sdcard/lightreader-qa-$([guid]::NewGuid().ToString('N')).png"
Invoke-Adb shell screencap -p $remoteScreenshot
Invoke-Adb pull $remoteScreenshot (Join-Path $report "qa-home.png")
Invoke-Adb shell rm $remoteScreenshot

$metricOutput = Join-Path $report "reader-engine.jsonl"
$metricLines = @(& adb -s $Serial exec-out run-as $qaPackage cat files/qa-evidence/performance/reader-engine.jsonl 2>$null)
$validMetricLines = @($metricLines | Where-Object {
    try {
        $_ | ConvertFrom-Json -ErrorAction Stop | Out-Null
        $true
    } catch {
        $false
    }
})
if ($validMetricLines.Count -gt 0) {
    $validMetricLines | Set-Content -Encoding UTF8 $metricOutput
} else {
    Remove-Item $metricOutput -ErrorAction SilentlyContinue
}

$additionalOutput = Join-Path $root "app\build\outputs\connected_android_test_additional_output"
if (Test-Path $additionalOutput) {
    Copy-Item -Recurse -Force $additionalOutput (Join-Path $report "evidence")
}

if ($PerformanceBaseline) {
    $currentPerformance = Get-ChildItem $report -Recurse -Filter "reader-engine-benchmark.json" |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $currentPerformance) { throw "Reader engine benchmark evidence was not produced." }
    & (Join-Path $root "scripts\compare-reader-performance.ps1") -Baseline $PerformanceBaseline -Current $currentPerformance.FullName
    if ($LASTEXITCODE -ne 0) { throw "Reader engine performance comparison failed." }
}

$fatal = Select-String -Path (Join-Path $report "logcat.txt") -Pattern "FATAL EXCEPTION|ANR in $qaPackage|Room.*migration|Migration didn.t properly handle|StrictMode.*violation|qa-fake-key" -CaseSensitive:$false
if ($fatal) { throw "QA launch log contains a crash, ANR, migration, or security failure. See $report\logcat.txt" }

Write-Host "Reader QA user journeys passed on $Serial. Evidence: $report"
