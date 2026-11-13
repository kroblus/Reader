# Web and verification implementation evidence

Final integration: final QA passed 113/113, JVM tests 115/115, macrobenchmark 2/2 and comparator cases 16/16. UI paired performance comparisons still fail the unchanged 15% gate. Earlier pending statements below are chronological handoff notes; see [verification summary](verification-summary.md) for final evidence and source identity.

Base: 700916f41a46af9a00399749b06f0e59769dad33. Branch: codex/reader-complete-improvements. No commits, pushes, tags or releases made by this agent.

## WP05 implemented and exercised; final combined regression pending

- HtmlFetcher has distinct bounded HTML/text readers, manual per-hop target validation, cancellable OkHttp body reads, bounded network/5xx retries, status/retryable/Retry-After/source metadata. Long Retry-After is returned to the worker for deferred work rather than shortened.
- RobotsPolicy caches by origin (64 entries / one hour), applies to preview, sample, chapter and redirect targets, respects specific user-agent groups, longest Allow/Disallow, wildcard/end matching and URI octet normalization. 404/410 means absent; access/network/oversize/MIME errors remain failures. Policy follows RFC 9309: https://www.rfc-editor.org/rfc/rfc9309.html.
- AI has an independent opt-in enabled preference. Saved keys stay in existing Keystore/AES-GCM store. Disabled/missing-key feedback is typed. Connection tests propagate cancellation; actual calls stop. UI distinguishes saved vs tested and states service/model, trimmed HTML transmission and provider charges. EN/ZH resources are separate web_improvement_strings.xml files.
- Tests added/expanded: HtmlFetcherTest, RobotsPolicyTest; controlled HTTPS fake-key test explicitly enables AI. No paid endpoint called.

## WP12 implemented; local verification and remaining delivery gates

- QA runner removes former USB approval writes, idle/standby changes and background unfreeze loop. Read-only install policy preflight, isolated QA launch, runId/source manifest, fresh-only results, failure logs/counts and environment restoration in finally.
- TestEvidenceOutput prefixes runId. Direct calls without runner use process-specific paths.
- Comparator rejects missing/nonfinite/zero/inconsistent metrics, insufficient samples and mismatched environments. Its final `scripts/test-performance-comparison.ps1` suite passed 16 positive/negative cases on PowerShell 5.1.
- PowerShell parser checked both runner and comparator without syntax errors. Python PyYAML BaseLoader parsed all four workflows; this is syntax verification, not remote CI execution.
- Quality workflow is reusable for precise SHA. Release depends on its verified SHA and checks tag/version/APK/signature identity; temporary signing material cleaned in finally-equivalent always step. No workflow triggered.
- Benchmark target now .benchmark with matching macrobenchmark variant. Cold/warm shelf startup distinguished.

## Performance measurement changes

Engine metrics renamed to prepared-text preview/full/two-chapter pagination and cache lookup. Removed ungrounded universal thresholds. ReaderUiPerformanceInstrumentedTest measures ten real actions per scenario to renderer frame commit; includes instrumentation overhead, warm process, separate from macrobenchmark cold startup. Large corpus samples peak PSS every 50 ms and checks exact TXT character completeness plus nested EPUB. Device runs are serialized by root. Executed historical baseline, non-final FullQA1 comparison and updated import measurements are recorded below; no physical-device product target is inferred.

## Shared integration

Root integrated AppContainer and DownloadFeatureUiMappings with AI_CONFIGURATION/AI_FAILURE and HTTP status/Retry-After/source presentation. Download agent integrated Worker retry metadata. Gradle and device ownership are explicitly handed off; this agent ran the historical baseline only during its authorized exclusive slot.

## Baseline preparation

Created detached worktree D:/Desktop/Codex/Reader-baseline-700916f at the unchanged historical product commit. Only copied identical ReaderUiPerformanceInstrumentedTest, ReaderEnginePerformanceInstrumentedTest and TestEvidenceOutput harness, plus SDK-only local.properties. Its actual completed baseline run is recorded below. Root coordinates serial Gradle/QA ownership. UI harness uses preferences.first()+save to compile against both versions.

The old 403 UI assertion was updated from generic HTTP failure to access-restricted guidance, matching the explicitly requested new product rule. Controlled fixture robots response now uses actual text/plain rather than HTML markup. AI instrumentation additionally covers disabled/missing key without network, persisted enable state, fake-key 401 and cancellation propagation.

## Integration evidence update

- Root check5 production compilation and app unit suite succeeded. XML confirms HtmlFetcherTest 8/8, RobotsPolicyTest 5/5, JsoupWebSourceParserTest 5/5. The first bounded-503 test found OkHttp 4.12 internally retries `503 + Retry-After: 0` despite retryOnConnectionFailure=false; fixed by keeping the original Retry-After in a call-local tag and suppressing only that internal follow-up header. The outer loop now owns the unchanged three-request assertion. Upstream source reviewed: https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt.
- QA runner offline-device negative preflight was actually executed. An initial run found adb logcat waits for missing devices; collection now checks current authorization/availability first. The corrected run at build/reports/reader-user-tests/20261113T060446Z-4de207b9-700916f41a46-ed0e62cd7d20 completed with tests=0, succeeded=false and explicit failure, source and run metadata.
- Comparator now checks font scale, density and viewport dimensions too. PowerShell 5.1 compatibility fixed (no ConvertFrom-Json -AsHashtable); `powershell -NoProfile -File scripts/test-performance-comparison.ps1` passed 16 cases.
- Root's genuine process recovery test is excluded from orchestrated suites. Full QA runner and CI run its prepare/force-stop-QA/verify phases directly, validate OK(1 test) and preserve failure logs/counts. New Bash helper passes bash -n; .gitattributes limits LF enforcement to that helper. Targeted -TestClass runs do not automatically add these phases.
- Added QA-only Compose test manifest dependency after actual QA evidence showed ComponentActivity unavailable in the isolated QA variant. This is required for ReaderSettingsDraftInstrumentedTest; reader/root own its assertions.

## Historical performance baseline executed

Detached 700916f product + identical harness executed on emulator-5554 / ShengliFlowApi36 / API36: 2 tests, 0 failures, build success 3m43s. Evidence copied to build/reports/reader-performance/baseline-700916f-perf-20261113, including source.json with exact command/harness hashes, JSON samples, JUnit XML and Gradle log. Environment: 1080x2400, 420dpi, fontScale 1.0, ~60Hz, power source 0. UI ten-sample p95 (includes instrumentation): book open 810.54 ms, cached tap 232.15 ms, uncached chapter 266.87 ms, layout 165.85 ms, tools 367.23 ms. Prepared engine p95: 8KiB 110.68 ms, 256KiB 4048.67 ms, two-chapter 7213.37 ms; cache-container lookup ~0.003 ms. These are emulator observations, not physical-device targets. Final-source comparison remains pending; the earlier failed comparison below is retained.

## Actual integration and FullQA1 diagnostic result

- Root's final-static3 passed 115 JVM tests and assembled QA, QA tests, benchmark target and macrobenchmark; app lint reported zero errors, 63 warnings and one hint. Subsequent static4 reran affected app checks and benchmark successfully. These are completed earlier checkpoints, not a claim that later source changes have already passed the final gate.
- FullQA1 report: `build/reports/reader-user-tests/20261113T065411Z-25a0bb63-700916f41a46-8d13fcd762d1`. Root observed 108 tests, 107 passing, one obsolete staging-order assertion failing, zero skipped, 14m24s. It stopped before the host-controlled process recovery and fresh-install phases. Root strengthened the staging/final-order assertions; QA6 passed that revised case.
- Ran the unchanged comparator with `-MaxRegressionPercent 15` against FullQA1 and the historical baseline. Environment, corpus and p95/sample consistency checks passed. Prepared-engine results passed (exit 0); UI comparison failed (exit 1) on layout change. This remains a diagnostic result for source `700916f41a46-8d13fcd762d1`, not the final source.

| Metric | Baseline p95 ms | FullQA1 p95 ms | Change |
| --- | ---: | ---: | ---: |
| Prepared preview pagination | 110.6811 | 102.6744 | -7.23% |
| Prepared full pagination | 4048.6735 | 3619.0919 | -10.61% |
| Prepared two-chapter pagination | 7213.3725 | 7133.7202 | -1.10% |
| Cache-container lookup | 0.0029988 | 0.0029175 | -2.71% |
| Warm book open to readable frame | 810.5367 | 762.4189 | -5.94% |
| Cached actual page tap | 232.1521 | 231.7789 | -0.16% |
| Uncached chapter action | 266.8705 | 283.4189 | +6.20% |
| Layout change | 165.8497 | 199.8385 | **+20.49%, failed** |
| TOC tool list | 367.2277 | 366.5658 | -0.18% |

Layout baseline samples (ms): `[165.8497,149.4920,150.1827,132.7171,133.9515,133.2631,133.3251,132.2080,133.7072,133.1685]`; FullQA1: `[199.4913,199.8385,181.8441,133.8473,149.7773,133.4447,133.1153,133.7783,134.1209,131.9422]`. Median moved 133.5162 to 133.9841 ms (+0.35%); two samples exceeded the fixed 190.7272 ms investigation threshold. Values cluster around 60 Hz frame intervals, but this is insufficient to dismiss the regression as noise.

Read-only comparison found a real additional work path: repaginate now went through loadChapter and regenerated an 8192-character first-page preview before full pagination at offset zero. The harness's short chapters therefore paginated twice. Root/reader removed that redundant preview when the same book/chapter already has displayed pages and added a lifecycle assertion for one layoutVersion commit and the preserved UTF-16 anchor. Initial open, empty viewport recovery and chapter changes retain preview behavior. The identical baseline/current UI harness SHA-256 at the failed run was `254D1CB8382AA5DC38EA5AF9D526687022CE5C44BC55707145D10029359AE5CA`. No thresholds or samples were discarded. QA7 passed the new lifecycle behavior but its performance comparison still failed, as recorded below; final combined QA remains pending.

## Complete library-import scope

The existing 10 MiB TXT, 100 MiB TXT and nested EPUB cases now time the production `BookRepository.import(Uri)` once, including source fingerprinting, private file writes, parsing and Room commit. Reports use `measurement_scope=library_import`, `sample_count=1`, and `pss_sampling_interval_ms=50`. Verification queries the committed book and chapters, checks keys/order/counts/totalChars and each private file, then stream-checks every TXT body character; EPUB checks its exact fixture body. Cleanup deletes only that imported QA book and controlled source. The 2 MiB single-paragraph test remains a separate format-plugin boundary case with its original corpus.

Root's QA6 report `build/reports/reader-user-tests/20261113T070934Z-8b6fcfec-700916f41a46-63244b97dc23` passed all four import cases. Its reported measurements are: 100 MiB 46402 ms / sampled peak PSS 212311 KiB / 410 chapters; approximately 10 MiB 3915 ms / 212273 KiB / 857 chapters; nested EPUB 153 ms / 170002 KiB / one chapter. They are single-run observations and are not percentile-comparator inputs. The QA6 suite itself had one gallery synchronization failure, subsequently fixed and rerun in QA7; it is not marked wholly passing here.

## Final workflow and benchmark preparation audit

- Read-only workflow review confirms main/reusable-release quality and nightly include all large-corpus tests; PR smoke excludes the class intentionally, and performance-corpus targets that class explicitly. Release still depends on successful quality for the requested SHA, checks out verified_sha, asserts HEAD/GITHUB_SHA equality, verifies tag/versionCode/versionName/package and signing certificate before upload, and always removes its temporary keystore. No remote workflow, signing or publication was performed.
- Root authorized and this agent completed three-workflow evidence wiring: nightly/performance-corpus now pass unique runId (run/attempt plus matrix API or corpus suffix) and the actual checked-out SHA; all android.yml/nightly process-recovery calls use steps.source.outputs.sha. Artifact names for nightly/corpus include attempt and checked SHA. YAML parsing and explicit run/source contract checks passed for all four workflows; git diff --check passed.
- The emulator action's [script parser](https://raw.githubusercontent.com/ReactiveCircus/android-emulator-runner/v2/src/script-parser.ts) splits the input into individual lines, and its [executor](https://raw.githubusercontent.com/ReactiveCircus/android-emulator-runner/v2/src/main.ts) runs each with an independent sh and checks each exit code. Removed the redundant separate set/status/if lines, which shared no state. Each failing Gradle/helper invocation is still caught by the action. This is static source verification, not a remote CI result.
- Benchmark app remains isolated `com.lightreader.app.benchmark`, release-like/minified/non-debuggable/profileable and debug-signed; driver `com.lightreader.app.macrobenchmark` is self-instrumenting, targetSdk36, benchmark-only, uses release fallbacks and queries only the isolated app. Earlier built APKs passed public metadata/signature checks with matching debug certificates. They must be rebuilt and rehashed after the last product changes before measurement.
- The prepared macro command was subsequently executed successfully, as recorded below: `gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest --console=plain --no-configuration-cache "-Pkotlin.compiler.execution.strategy=in-process" "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR"`. ANDROID_SERIAL was set temporarily to emulator-5554 and restored afterward. Only the emulator warning was suppressed; all other benchmark checks remained.

## QA7 comparison after the redundant-preview fix

Report: `build/reports/reader-user-tests/20261113T072017Z-716c7a9e-700916f41a46-6df29927338c`. Root reports nine of ten tests passing (six lifecycle, two search and one UI performance collection); gallery failed and is being diagnosed separately. A passing collection test is not a passing performance gate. The original comparator with the unchanged 15% threshold returned exit 1: uncached chapter +18.53%, layout change +20.45%, and TOC list +18.02%. Book open +12.50% and cached tap +14.53% remained within that threshold.

| UI metric | QA7 p95 ms | QA7 median ms | Baseline median ms | Median change |
| --- | ---: | ---: | ---: | ---: |
| Book open | 911.8896 | 440.4904 | 357.6286 | +23.17% |
| Cached tap | 265.8852 | 199.3413 | 166.4204 | +19.78% |
| Uncached chapter | 316.3300 | 200.1116 | 175.0797 | +14.30% |
| Layout change | 199.7658 | 166.5742 | 133.5162 | +24.76% |
| TOC tool list | 433.4084 | 342.3059 | 243.4741 | +40.59% |

QA7 layout samples (ms): `[198.5322,199.7658,183.8702,150.3385,166.4781,149.5699,167.5079,164.4435,166.6702,149.7690]`. Several operations are slower throughout the distribution, not just a single outlier. Global host/rendering load or shared UI work are possible explanations, not established causes; there is no trace evidence to excuse the failure as noise. Final frozen-source same-environment comparison is still required. Neither this failure nor FullQA1's failure should be hidden by a later passing sample.

## Static5 and the complete thirty-sample ABBA execution

- After root's Activity-preserving AppLocalization fix and exclusive device/build handoff, `build/reader-final-static5.log` completed successfully in 3m13s. The five JVM suites total 115 tests (18/8/12/5/72), zero failures/errors/skips. App lint has zero errors, 63 warnings and one hint. QA app/tests, minified benchmark app and macrobenchmark assembled successfully. Historical-product QA app/test build then passed in 15 seconds. No additional production source was changed by this agent.
- Both worktrees changed the UI harness to thirty iterations with chapter target `3 + (iteration % 10) * 2`. The 32-chapter corpus, actual actions and renderer synchronization remain unchanged; all samples including startup iterations are retained. FullQA2 must use this same thirty-sample protocol. The fixed comparator remains 15%, with `-MinimumSamples 30` for these UI comparisons.
- First attempt `build/reports/reader-performance/abba-20261113T074215Z-99ab1525` passed the actual A1/B1/B2 instrumentation tests, but Test Storage refused output with Operation not permitted and the former fallback reported success without persisting files. No sample arrays were recoverable. The attempt is explicitly an evidence-collection failure, A2 was not measured, logs are retained and animations were restored. No values were reconstructed or mixed into subsequent data.
- Root authorized a test-only evidence correction in both worktrees: after every timed action finishes, UI sends an in-progress status 2 and 2800-character numbered Logcat chunks containing the same Base64 JSON/run/source/SHA-256. The host verifies complete chunk indexes/count, identity, decoded hash, all thirty finite positive samples per metric, and p95 consistency. Orchestrator filtered the custom stream in these runs, so verified Logcat is the canonical source. `TestEvidenceOutput` now invokes an actual sh program and verifies the fallback file's SHA-256 before reporting it written; failure throws. UI's optional filesystem channel may fail explicitly without losing canonical samples. This fix does not alter timed operations or thresholds.
- Rebuilt both affected test APKs (main 38s, historical 6s) and verified matching harness SHA-256 `09220c37d114e4bc687a529ed5651e7b07b16166a4d24d114bbd055b96b90162` and helper SHA-256 `97bd9fa3d030a001aced7e127855b6e0a2d1c2700307733e8e0173764c612e9e`. No production JVM/lint rerun was needed for those test-only changes.
- Complete new run: `build/reports/reader-performance/abba-20261113T075248Z-225614a5`. Historical source ID `700916f41a46-27500e73db85`, current source ID `700916f41a46-5c63c59c8995`. Both app/test APK pairs and source manifests were snapshotted before measurements; Gradle daemon was stopped. Each run used the same Orchestrator entry, fresh isolated QA installation, thirty-second idle and preserved/restored animation settings. A1 canonical data was verified before proceeding. All four tests, 600 total action samples and matching optional file copies were collected successfully.

| UI p95 ms | A1 historical | B1 current | B2 current | A2 historical |
| --- | ---: | ---: | ---: | ---: |
| Book open | 515.0393 | 566.0495 | 531.1095 | 497.8530 |
| Cached tap | 197.4057 | 232.8103 | 216.3609 | 233.2763 |
| Uncached chapter | 199.8179 | 216.7851 | 216.8558 | 216.2966 |
| Layout change | 168.5149 | 199.6353 | 200.6824 | 167.0816 |
| TOC tool list | 319.7819 | 367.3732 | 400.2109 | 333.9639 |

Both comparator invocations returned exit 1, preserved in their comparison logs and result.json. A1/B1 exceeded 15% for cached tap (+17.93%) and layout (+18.47%); A2/B2 exceeded it for layout (+20.11%) and TOC (+19.84%). No pair or sample was selected away. Layout's within-version p95 differences were small (historical -0.85%, current +0.52%), while its medians remained near 133 ms; the repeated difference is in the tail. Cached-tap historical p95 itself varied +18.17%, showing why other metrics require cautious interpretation.

Host conditions were measured rather than presumed constant: before-test CPU observations A1/B1/B2/A2 were 16.47/23.85/50.39/44.14%, available RAM 5.57/2.79/0.63/9.37 GiB. No other agent ran builds/device tests, but external load and memory pressure still varied. This limits attribution and physical-device generalization; it does not erase failed emulator comparisons. `paired-analysis.json` preserves both p95 and medians plus within-version changes. Three animation values were restored successfully to 1.0.

## Actual macrobenchmark run and handoff

`build/reports/reader-performance/macro-20261113T075929Z-a5ba6f48` contains source manifest, immutable APK copies/hashes, public package/signature evidence, Gradle log, result.json and the flattened artifacts/source mapping. Build and instrumentation succeeded in 2m49s: two tests, zero failures/errors/skips. Both cold and warm startup have ten samples, and twenty actual Perfetto traces total 119041032 bytes. The default `CompilationMode.Partial()` / required baseline profile remained enabled and passed; the benchmark APK contains merged baseline.prof and baseline.profm assets.

| Activity time to initial display | Median ms | p95 ms | Range ms |
| --- | ---: | ---: | ---: |
| Cold, ten launches | 948.1458 | 1116.9103 | 876.5391–1116.9103 |
| Warm, ten launches | 305.0984 | 390.2930 | 170.9726–390.2930 |

These measure the isolated benchmark shelf Activity's initial display, not a book's first readable content. They are emulator observations with only EMULATOR suppressed, not physical-device product targets or an old/new startup comparison. Both APK signatures were verified and share the debug certificate SHA-256 `f1a230a1f20d3041c808fabd8c150985949e6d830ce53b96cab00aa5e1efa5a5`; APK hashes remained unchanged during the run. The three animation settings were rechecked as 1.0. At 2026-11-13 08:03:52 UTC this agent released Gradle/AVD ownership back to root, with no running test/build session. Root will run final FullQA2 including host-controlled process and fresh-install phases. The failed paired performance gate remains an explicit unresolved result.

## Single paired phase diagnosis, without replacing ABBA

Root then authorized reader's test-only four-stage timestamps in both worktrees, retaining the original total, ready predicates, actions and all thirty samples. Frozen harness hash: `efa896451427809d08cc478cd75277feed2a50c788b27688be2d951d5904ab3a`. Current/historical test APK rebuilds succeeded in 25/8 seconds; both snapshots were fixed before measurement and the Gradle daemon was stopped. Only one A/B pair was measured, using the same install/idle/environment protocol.

Report: `build/reports/reader-performance/phase-diagnostic-20261113T081008Z-e3d55fa3`. A1 and B1 passed instrumentation and strict canonical validation: each operation has thirty totals and four nonnegative finite phase values, and their sums agree with each original total within 0.000001 ms. Each run contains 454 synchronous engine records, with the same count in its explicitly non-flush-guaranteed JSONL snapshot. The real-time collector started before the idle interval and retained both tags; current-run engine entries were selected using the unique PID associated with canonical run-ID chunks. No log clearing or buffer modification occurred. Per-run files are `canonical-logcat.txt`, `engine-logcat.jsonl`, `engine-file-snapshot.jsonl`, `reader-ui-benchmark.json`; `phase-analysis.json` contains stage distributions and slow-sample overlap windows.

The unchanged fifteen-percent comparator returned exit 1: book open +44.14%, cached tap +21.37%, uncached chapter +30.28%, layout +28.06%; TOC +8.82% was within the threshold. This diagnostic result is retained alongside the worse/better prior samples and does not replace the complete ABBA failure.

Both versions' layout p95 samples happened at iteration 1:

| Phase of that actual sample | Historical ms | Current ms | Difference ms |
| --- | ---: | ---: | ---: |
| Execute action | 19.9407 | 41.5756 | +21.6349 |
| Observe ready predicate | 26.5759 | 41.1124 | +14.5365 |
| Wait for Compose idle | 116.6101 | 131.3676 | +14.7575 |
| Request and await frame | 19.2225 | 19.4578 | +0.2353 |
| Original total | 182.3492 | 233.5134 | +51.1642 |

The engine pagination overlapping that window took 17.5463 versus 22.6275 ms, a much smaller difference than the overall tail. Current iteration 5 was the maximum at 470.8434 ms, dominated by action 184.3426 and idle wait 238.6921 ms while actual pagination took 18.8539 ms. Overall layout medians were approximately 142.819 versus 134.078 ms and frame medians stayed near 18–19 ms.

Both versions recorded exactly 60 previews and 90 complete paginations (plus 152 reads and 152 normalizations), with only the preview operation's truthful name differing. There is no evidence here of another duplicate-pagination path to remove. The remaining observed tail spans settings-action completion, ready observation and Compose idle synchronization; the final frame wait is nearly unchanged. It cannot be declared entirely framework noise without further evidence. A read-only settings diff also confirms the update mutex existed historically; new small custom-layout serialization is a candidate for early initialization cost, not a proven cause or justification to remove persistence/race protection.

Root received the phase statistics and raw paths, and reader is reviewing them. Gradle/device/collector ownership was released immediately after the diagnostic pair; root has started final FullQA2. This agent made no production change, threshold change or additional device run after that handoff.
