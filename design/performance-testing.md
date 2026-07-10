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
report. Never run these commands against the user's primary reader device without an explicit
backup and approval.

The normal QA suite remains `:app:connectedQaAndroidTest`; it deliberately tests the separate
`com.lightreader.app.qa` package and does not affect an installed reader's library or settings.
