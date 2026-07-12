# LightReader performance testing

## Macrobenchmark baseline

The `:macrobenchmark` module contains the cold-start baseline. Build it together with the
release-like, debug-signed app variant:

```powershell
.\gradlew.bat :app:assembleBenchmark :macrobenchmark:assemble
```

Do not use the emulator's timing as a product performance number. Use an unlocked, isolated
physical device with no valuable LightReader data: the benchmark variant has the production package
name and will replace an installed `com.lightreader.app` build. Run:

```powershell
$serial = "<physical-device-serial>"
adb -s $serial install -r app\build\outputs\apk\benchmark\app-benchmark.apk
adb -s $serial install -r macrobenchmark\build\outputs\apk\debug\macrobenchmark-debug.apk
adb -s $serial shell am instrument -w `
  -e class com.lightreader.app.macrobenchmark.StartupBenchmark#coldStartup `
  com.lightreader.app.macrobenchmark/androidx.test.runner.AndroidJUnitRunner
```

The benchmark produces JSON and Perfetto traces that can be stored with a release-performance
report. It runs ten cold starts. Establish the first result as a device-specific baseline; two
consecutive runs on that same device fail the release performance gate when median cold start
regresses by more than 15%. Never run these commands against the user's primary reader device
without an explicit backup and approval.

The normal QA suite remains `:app:connectedQaAndroidTest`; it deliberately tests the separate
`com.lightreader.app.qa` package and does not affect an installed reader's library or settings.

The 2 MB single-paragraph and 100 MB TXT import corpus is intentionally isolated in the
**Android large TXT corpus** GitHub workflow. It runs weekly and on demand with its own 60-minute
budget, while pull-request QA continues to cover every other instrumented scenario. This keeps
normal feedback predictable without removing the large-file regression gate.

The corpus also records a 10 MB import. Both 10 MB and 100 MB runs write elapsed time, before/after
PSS and chapter count JSON under the test package's `qa-evidence/performance` directory. Their hard
gate is correctness and bounded memory behavior: no OOM/ANR, bounded chapter files and complete
character coverage. Timing is retained for trend analysis rather than compared across different
device models.

`HtmlBridgeInstrumentedTest` is also kept out of the generic hosted-emulator lane. It needs a
working WebView JavaScript callback, which GitHub's Android images do not provide reliably. Run
the complete QA suite on the local WebView-enabled AVD or a disposable physical device; the
`HtmlBridgeTest` JVM suite continues to cover bridge parsing on every CI run.

The hosted workflows enable KVM group permissions before creating an AVD. Without that setup the
emulator falls back to software emulation, which can take several minutes to boot and fails before
the test runner starts.

## Reader engine timing baseline

`ReaderEnginePerformanceInstrumentedTest` records full 256 KiB chapter pagination, cached page
lookup, cross-chapter pagination and PSS delta under `qa-evidence/performance`. Emulator output is
correctness and trend evidence only. On an unlocked physical reference device, the test enforces a
300 ms p95 first-visible-page target and a 50 ms cached lookup target. Full-chapter pagination is
recorded as a same-device regression metric rather than compared to the first-page UX target.

Capture the first completed physical-device JSON as that device's baseline. Compare later runs on
the same manufacturer and model with:

```powershell
.\scripts\compare-reader-performance.ps1 `
  -Baseline .\baseline\reader-engine-benchmark.json `
  -Current .\build\reports\reader-user-tests\<serial>\evidence\reader-engine-benchmark.json
```

The gate fails when pagination p95, cached lookup p95, or cross-chapter time regresses by more than
15 percent. The QA runner also accepts `-PerformanceBaseline <path>` to run this comparison as part
of a physical-device evidence pass.
