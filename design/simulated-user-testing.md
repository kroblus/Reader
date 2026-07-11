# LightReader simulated-user testing

Last updated: 2026-07-11

## Purpose and safety boundary

This is the executable test specification for LightReader. A complete result combines deterministic
automation on `com.lightreader.app.qa`, a local API 36 AVD run, and a physical-device acceptance run.
An emulator result must never be reported as a physical-device pass.

The connected suite uses Android Test Orchestrator with `clearPackageData=true`. It may reset only:

- `com.lightreader.app.qa`
- `com.lightreader.app.qa.test`

It must never target, clear, force-stop, or overwrite `com.lightreader.app` on a device that contains
real reading data. The QA build alone trusts the committed localhost test CA; release builds continue
to trust system certificates only and reject cleartext traffic.

## Automated layers

| Layer | Main coverage | Command/lane |
| --- | --- | --- |
| JVM | parsing, URL validation, tap policy, pagination/cache, settings fingerprints, feature state | module `testDebugUnitTest` tasks |
| QA core | Room migrations, encodings, EPUB, persistence, security, HTTPS and WorkManager | `:app:connectedQaAndroidTest` |
| Compose journeys | first launch, shelf, reader, settings, bookmarks, search, web download and errors | `app/src/androidTest/.../journey` |
| System boundary | DocumentsUI, volume key, Home/resume, process-safe state | `ReaderSystemBoundaryInstrumentedTest` |
| Stress | 2 MB paragraph, 10 MB/100 MB TXT, memory trim | weekly corpus and local full run |
| WebView | real JavaScript DOM callback | local AVD/physical device only |
| Performance | ten cold starts and import evidence | physical-device Macrobenchmark/corpus run |

The established tests remain authoritative for behavior they already cover. New journey suites add
cross-feature continuity rather than replacing lower-level assertions.

## Personas and journeys

### J01 — First-time reader

1. Launch after QA data reset.
2. Verify brand, rotating tagline, empty shelf, import action, skin action and overflow.
3. Enter app settings, web import and DeepSeek settings; use both toolbar and system back.
4. Confirm developer tools are hidden, enable them with the documented title long-press, open DOM,
   recreate the activity, and confirm the preference persists.
5. Pass when every destination has one visible root tag and navigation never produces a blank screen.

### J02 — Local-file reader

1. Cancel DocumentsUI and confirm the shelf/database remain empty.
2. Select the staged UTF-8 fixture through DocumentsUI and confirm exactly one QA book is created.
3. Import the UTF-8, UTF-16, GB18030, Big5, mixed-language and no-heading fixtures through the
   format/repository gates; all output chapters must be non-empty and bounded.
4. Import basic and nested EPUB fixtures; reject empty, encrypted, corrupt and path-traversal EPUBs.
5. Re-import identical content and exercise open-existing, import-copy and cancel in the Compose suite.

### J03 — Long-term shelf reader

1. Cover empty, one-book, many-book, long-title, missing-author and mixed-language shelves.
2. Edit title/author and clear the author; compare source chapter path and content before/after.
3. Cancel deletion, then confirm deletion. Book, chapters, progress, bookmarks, search index and private
   root files must all be removed.
4. Change every application skin and language; recreate the activity and verify persistence.

### J04 — Active reader

1. Open new and resumed books at first, middle and final chapters.
2. Exercise left/center/right taps, fullscreen-next taps, horizontal/vertical gestures, volume keys and
   auto-read across chapter boundaries.
3. Exercise NONE, HORIZONTAL, SLIDE, VERTICAL, SCROLL and SIMULATION page-turn modes.
4. Rapidly queue same-direction and opposing turns; text offsets must stay continuous with no blank page.
5. Verify chrome auto-hide, settings dock, TOC and bookmark overlays pause the hide timer.
6. Send the app Home and resume it; recreate the activity and reopen from the safe shelf fallback.

### J05 — Search and bookmark reader

1. Search empty, whitespace, absent, Chinese, English, punctuation and long queries.
2. Verify the first real search lazily creates a complete index without duplicate chunks.
3. Jump from results to the exact chapter/offset and preserve query state while the screen remains alive.
4. Add/remove the current bookmark, create cross-chapter bookmarks, jump/delete from the overlay, and
   recreate the activity to prove database persistence.

### J06 — Customization reader

1. Cover comfort, compact, immersive and custom presets.
2. Cover font size/family/weight, line/paragraph spacing, indent, margins and justification boundaries.
3. Confirm only layout fingerprints repaginate; theme, brightness, chrome and animation preserve offset.
4. Verify follow-system/custom brightness, keep-screen-on and portrait lock while reading, then verify all
   window state is restored on exit.
5. Run the manual display matrix at font scales 1.0, 1.3 and 2.0 on phone portrait/landscape and 600dp.

### J07 — Web-novel reader

1. Parse the QA HTTPS catalog through the production fetcher/parser and verify title, author, four chapter
   links and readable sample text.
2. Map HTTP, non-HTML, oversized, disconnect, browser-verification and robots failures.
3. Start the real WorkManager download, observe state changes and open exactly one completed web book.
4. Force a partial chapter failure; preserve completed chapters and reset only failed chapters on resume.
5. Cover pause, resume, cancel/delete confirmation, no-new-chapter refresh and deduplicated append refresh.

### J08 — DeepSeek and DOM reader

1. Store a fake key through Android Keystore and point the advanced HTTPS base URL at the QA server.
2. Cover success, invalid key, HTTP failure and timeout/malformed-response behavior.
3. Inspect captured requests: authorization is present, scripts and the secret marker are absent, and only
   the cleaned catalog sample is sent.
4. Delete the key through confirmation and scan small app files/evidence for plaintext leakage.
5. On WebView-capable devices load DOM fixtures and verify outer HTML/body callbacks and SSL cancellation.

### J09 — Constrained/security reader

1. Run migrations 1→2 through 6→7 and validate preserved fields/defaults.
2. Deliver trim-memory events while a reader session exists and verify bounded caches recover.
3. Verify backup and cleartext flags are disabled, unsafe EPUB paths cannot escape, and logs contain no key.
4. Run low-storage and network-loss cases manually where changing global device state is required.

### J10 — Accessibility/performance reader

1. Use TalkBack to traverse shelf cards, menus, settings, reader toolbar, TOC, bookmarks, search results
   and download tasks in visual order.
2. Verify icon descriptions, switch/slider state descriptions and usable 48dp touch targets.
3. Run ten physical-device cold starts and retain median/P90/P95 plus Perfetto traces.
4. Retain 10 MB/100 MB import elapsed time, before/after PSS and chapter count JSON.
5. Run a 30-minute mixed reading soak and a 200-chapter throttled web download before a release candidate.

## Fixtures

`FixtureCatalog` generates deterministic text and EPUB fixtures inside the isolated test cache. Binary
fixtures are generated at runtime so their encodings and unsafe ZIP paths cannot be damaged by Git or
editor conversion. `reader-qa.txt` is also staged in `/sdcard/Download` for the actual system picker.

`QaHttpsFixtureServer` uses a test-only PKCS#12 key and a localhost certificate with SANs for
`localhost` and `127.0.0.1`. It serves catalog/chapter responses, redirects, HTTP errors, access
restrictions, disconnects, oversized bodies and DeepSeek-compatible JSON. The key and certificate are
not production credentials and are never used outside the QA source sets.

## Running locally

Fast local gate:

```powershell
.\gradlew.bat :core-reader:testDebugUnitTest :core-formats:testDebugUnitTest `
  :feature-reader:testDebugUnitTest :feature-download:testDebugUnitTest :app:testDebugUnitTest `
  :app:lintDebug :app:assembleDebug :app:assembleQaAndroidTest --console=plain
```

Local API 36 AVD, including WebView but excluding the weekly 100 MB corpus:

```powershell
.\scripts\run-reader-user-tests.ps1 -Serial emulator-5554
```

Complete disposable physical-device QA run:

```powershell
.\scripts\run-reader-user-tests.ps1 -Serial <serial> -RequirePhysical -IncludeLargeCorpus
```

If the device lacks a working WebView provider, add `-ExcludeWebView` and record that the DOM integration
item is skipped. Do not silently call such a run complete.

## CI tiers

- Pull requests: all JVM/lint/build gates plus API 35 deterministic journeys; large corpus, WebView and
  DocumentsUI/system-boundary class are excluded for fast feedback.
- Pushes to `main`: API 35 deterministic suite including system boundaries; large corpus and hosted
  WebView remain isolated.
- Nightly: API 26 and API 35 full deterministic matrix.
- Weekly/manual: 10 MB/100 MB TXT corpus with performance JSON.
- Local pre-release: API 36 with WebView, then a physical-device run and Macrobenchmark.

## Evidence and pass criteria

Each journey writes a JSON result through AndroidX Test Storage. Failures additionally write a screenshot,
UI hierarchy and recent logcat. Gradle collects these under
`app/build/outputs/connected_android_test_additional_output`; CI uploads them with Android XML/HTML.
The local runner also records device metadata, launch timing, PID, screenshot and crash scan.
When an OEM image does not expose the AndroidX Test Storage provider, text/JSON evidence falls back to
`/sdcard/Download/LightReader-QA-Evidence`; the runner pulls that directory into the report and removes only
that QA-owned directory before the launch smoke.

A run fails immediately for data loss, unreadable text, progress regression, plaintext key exposure,
unsafe ZIP extraction, startup crash, ANR, migration failure or an unrecoverable download state.
External live-site/DeepSeek smoke failures must be classified separately from deterministic QA failures.

No test may be made green with an unconditional retry or a fixed sleep. Use Compose `waitUntil`,
UI Automator `Until`, observable Room/Flow state, WorkManager state or bounded latches.

## Manual physical-device acceptance

After automated QA passes, complete and sign off these items on an unlocked disposable/backup device:

- file picker with UTF-8, GB18030/GBK and non-DRM EPUB
- tap/swipe/volume/auto-read feel across chapters in every page-turn mode
- brightness, keep-screen-on, portrait lock, lock-screen and background recovery
- TalkBack order, 48dp targets, font scale 2.0, portrait/landscape and long localized strings
- public HTTPS catalog preview/download/refresh and optional temporary DeepSeek key
- 30-minute reading soak, 200-chapter throttled download and ten-run Macrobenchmark

Record skipped items with a reason. A missing physical device is a blocked physical gate, not a pass.
