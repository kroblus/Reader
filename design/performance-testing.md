# Reader performance evidence

Performance numbers describe the measured scope. Engine timings operate on already prepared text; they are not click-to-readable-frame latency. A cache container lookup is not a page turn. Do not assign an absolute device target until a real reference-device baseline exists.

## Isolated packages and builds

- QA: `com.lightreader.app.qa`; only QA tests may clear their isolated data.
- Benchmark target: `com.lightreader.app.benchmark`, release-like, profileable, signed with the local debug key.
- Benchmark driver: `com.lightreader.app.macrobenchmark`; its benchmark variant targets the matching app variant. It never replaces `com.lightreader.app`.

```powershell
.\gradlew.bat :app:assembleBenchmark :macrobenchmark:assemble --console=plain
adb -s <isolated-serial> install -r app\build\outputs\apk\benchmark\app-benchmark.apk
adb -s <isolated-serial> install -r macrobenchmark\build\outputs\apk\benchmark\macrobenchmark-benchmark.apk
adb -s <isolated-serial> shell am instrument -w com.lightreader.app.macrobenchmark/androidx.test.runner.AndroidJUnitRunner
```

`StartupBenchmark` samples ten cold and ten warm activity launches, opening the shelf. Its AndroidX JSON/Perfetto traces measure activity startup, not reading a specific book. Emulators validate wiring and provide explicitly labelled local observations only. Do not use emulator scores as physical-device product targets.

## Reading flows

`ReaderUiPerformanceInstrumentedTest` generates the same 32-chapter mixed Chinese/English book and records thirty samples per operation: shelf click to readable frame, actual cached page tap, non-adjacent uncached chapter, layout adjustment, and TOC tool list. The target chapter is `3 + (iteration % 10) * 2`, so all three ten-iteration cycles use the same chapter sequence. Each iteration reopens the book and clears the reader's session caches; all thirty samples, including the first iteration, are retained. After UI readiness it waits for a renderer frame commit (API 29+; two animation callbacks on earlier versions). These upper-bound measurements include instrumentation synchronization overhead. Book opening is warm-process; cold activity launch is measured separately above.

`ReaderEnginePerformanceInstrumentedTest` keeps ten prepared-text pagination samples (8 KiB preview and full 256 KiB), ten two-chapter pagination samples, and twenty amortized cache-container lookup samples. JSON explicitly identifies `prepared_text_engine`; it has no fabricated universal 300 ms / 50 ms gate.

```powershell
.\scripts\run-reader-user-tests.ps1 -Serial emulator-5554 -TestClass com.lightreader.app.journey.ReaderUiPerformanceInstrumentedTest
.\scripts\run-reader-user-tests.ps1 -Serial emulator-5554 -TestClass com.lightreader.app.journey.ReaderEnginePerformanceInstrumentedTest
.\scripts\compare-reader-performance.ps1 -Baseline <same-device-baseline.json> -Current <this-run.json>
.\scripts\compare-reader-performance.ps1 -Baseline <30-sample-ui-baseline.json> -Current <30-sample-ui-current.json> -MinimumSamples 30 -MaxRegressionPercent 15
.\scripts\test-performance-comparison.ps1
```

Comparisons require matching manufacturer, model, SDK/build fingerprint, refresh rate, font scale, density, viewport dimensions, power source, corpus checksum, emulator classification and measurement scope. Each metric needs at least ten finite positive samples and a matching p95. Missing metrics never become zero. Record a baseline and changed-build sample on the same isolated device, with the same power/refresh settings, before enabling regression interpretation. An isolated detached worktree at the historical product commit is prepared with the identical measurement harness for a same-device baseline. Record actual run IDs before claiming a comparison.

The current UI protocol uses a minimum of thirty samples in both baseline and current runs; the engine protocol retains its original counts. Earlier ten-sample UI reports and failed comparisons are retained as historical diagnostics and are not mixed into the thirty-sample comparison. The final full QA suite uses the same thirty-sample harness as the paired measurement.

For the authorized paired UI check, use an A-B-B-A order (A: historical product, B: current product). Verify identical harness hashes in both worktrees, build and snapshot both app/test APK pairs with source manifests before measuring, then run only the fixed snapshots on the same isolated device. Keep the same test entry point and clean QA data between runs. Allow the same thirty-second idle period after installation before each measurement, with no concurrent Gradle build or other device tests. Record host CPU/memory and device thermal/battery/display state before and after. Compare A1/B1 and A2/B2 at the unchanged 15% investigation threshold, report within-version differences, and retain all four runs rather than choosing the fastest pair. These controls reduce order and load ambiguity; they do not turn emulator observations into reference-device targets.

After all timed actions, the UI harness emits its complete JSON as Base64 with run/source identity and a SHA-256 digest through an in-progress instrumentation status and numbered Logcat chunks. Orchestrator can filter custom status streams; the host then assembles the Logcat chunks only if their run/source, count, indexes and digest agree. Every metric must contain all thirty finite positive samples with the correctly calculated p95. Preserve the original instrumentation and Logcat files alongside the decoded JSON. The filesystem copy is optional for this harness and must agree with canonical data if present. `TestEvidenceOutput` verifies the fallback file by reading its SHA-256 before reporting a successful write; an unverified write fails explicitly.

Actual paired evidence is in `build/reports/reader-performance/abba-20261113T075248Z-225614a5`: all four tests and 600 action samples were collected with verified Logcat payloads and matching file copies. Both fixed-threshold comparisons failed: A1/B1 cached tap +17.93% and layout +18.47%; A2/B2 layout +20.11% and TOC +19.84%. Layout p95 repeated closely within each version (baseline 168.5149/167.0816 ms, current 199.6353/200.6824 ms). Host load and available memory varied despite serialized builds and fixed idle periods, so these remain emulator investigation evidence, not proof of a physical-device target. All failures remain visible.

The preceding attempt `abba-20261113T074215Z-99ab1525` retained passing instrumentation logs for A1/B1/B2 but no recoverable sample arrays because Test Storage refused writes and the old fallback did not verify success. A2 was not measured. That attempt is an evidence-collection failure, not valid performance data; its logs were retained, and no samples were reconstructed or combined with the later complete run.

For diagnosis, the same thirty-sample harness additionally records action, ready-observation wait, Compose idle wait and requested-frame wait. Their sum must match each original start-to-frame total; the original actions, readiness predicates and total remain unchanged. After all measurements it snapshots the existing QA engine JSONL without claiming asynchronous writes have flushed. A host Logcat collector starts before the fixed idle period and captures both ReaderUiEvidence and ReaderEngineMetric in real time; current-run records are isolated by the canonical run ID's instrumentation PID. Neither the log buffer nor its size is changed.

The single diagnostic A/B run `phase-diagnostic-20261113T081008Z-e3d55fa3` retained 300 complete action samples and 454 synchronous engine records per version, matching both file-snapshot counts. All four-phase sums passed. Its unchanged threshold comparison also failed and remains separate from ABBA. At each version's layout p95 sample, total time increased 182.3492 to 233.5134 ms: action 19.9407 to 41.5756, ready wait 26.5759 to 41.1124, Compose idle wait 116.6101 to 131.3676, and frame wait 19.2225 to 19.4578 ms. The overlapping actual pagination was only 17.5463 versus 22.6275 ms. Both versions recorded sixty foreground previews and ninety complete foreground paginations. Prefetch pagination has no separate engine timing, so these counts cannot prove identical total background work. Tail latency spans action/readiness/Compose synchronization; action includes preference reads, persistence and scheduling, while idle includes test synchronization. It is not established to be entirely framework noise, and no failed comparison is waived. There is no evidence-backed redundant production operation to remove at this point; further attribution needs a system trace of Main/Default/IO scheduling, DataStore, GC/JIT and frame work under controlled reference-device conditions.

## Import corpus

`LargeTxtImportInstrumentedTest` generates 10 MB Chinese TXT, 100 MB ASCII TXT and a 2 MiB uninterrupted paragraph. It also imports a reproducible nested-navigation EPUB. The 10/100 MB and EPUB measurements cover complete `BookRepository.import` execution, including source fingerprinting, body-file preparation and the Room library commit (`measurement_scope=library_import`). They verify actual book/chapter identities, persisted order, statistics and paths; the large TXT cases stream-check every body character and chapter length. The 2 MiB case separately exercises the format parser's long-paragraph boundary. Peak process PSS is sampled every 50 ms during import. Reports retain source checksums, elapsed import time, peak/before/after PSS and chapter count. Peak sampling is an observation, not an exact allocation peak, and import timing currently has one sample per corpus and is not accepted by the percentile comparator.

```powershell
.\scripts\run-reader-user-tests.ps1 -Serial emulator-5554 -IncludeLargeCorpus -TestClass com.lightreader.app.LargeTxtImportInstrumentedTest
```

## Run evidence and CI

The QA runner creates `build/reports/reader-user-tests/<runId>-<HEAD>-<source-tree-hash>`. Source manifests include new source files. It passes run/source IDs to instrumentation, collects only fresh results and its own QA fallback directory, and writes test/failure/error/skipped counts even on failure. It makes no USB installation approval, verifier, device-idle or OEM security changes. Installation restrictions are explicit environment blockers.

Android quality is reusable and checks out the exact requested SHA. The release job depends on that result, verifies its own checkout, tag/version metadata and APK package/version/signing certificate before any upload. This task only edits these gates; it does not trigger, sign or publish a release.

Full QA runs additionally execute the two-phase `ReaderProcessRecoveryInstrumentedTest` outside Orchestrator (which intentionally clears QA data between individual tests). Preparation and verification use separate instrumentation processes with a QA-only force-stop between them. The runner and CI require a successful one-test result for both phases and retain logs/counts on failure. No production package is stopped or cleared.

They then run `scripts/run-backup-fresh-install.py`: export synthetic TXT/EPUB/web books, hold the archive on the host, uninstall only QA and QA.test, verify they are absent, install the same verified APKs, and restore into the empty library. The receipt includes APK/archive checksums, source identity, installed-package identity, fresh-state checks, reopened anchors/bookmarks, duplicate restore and AI-unconfigured results. Each phase must report one successful instrumentation test. This is distinct from Activity recreation or clearing a fixture's database.
