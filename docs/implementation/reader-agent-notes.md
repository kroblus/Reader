# Reader work packages 01 / 06 / 07 / 08

Final integration: all reader changes are covered by the 113/113 final QA run. Earlier pending statements below are chronological handoff notes; see [verification summary](verification-summary.md) for the final source identity and the unresolved UI performance gate.

Starting SHA: `700916f41a46af9a00399749b06f0e59769dad33`; branch `codex/reader-complete-improvements`.

## Implementation

- 01: confirmed static defect in `loadChapter`: independently evicted body with retained pages previously skipped `setLoadedChapterContent`. The controller now resolves content independently of the page hit. One `(bookId, chapterId, generation)` identity guards commits, including loading/error/preview/full pages and prefetch. Explicit cancellation propagation. Final serialized progress save and reopen barrier retained. Repagination follows the requested chapter rather than the previously displayed one during a chapter load.
- 06: `ReaderLayoutSnapshot` contains typography only. `reader_custom_layout_v1` stores it separately. Legacy CUSTOM synthesizes its snapshot from its existing values; legacy presets retain effective display without pretending to recover lost history. Preset selection preserves snapshot. Editing preset typography creates CUSTOM from effective layout. Field edits compare against the UI baseline and merge into current preferences under the existing mutex. A single panel draft previews with 70 ms coalescing, writes on release, and flushes on disposal. Reset description explicitly retains library/progress/bookmarks/language/app colors.
- 07: platform `TypedValue.applyDimension` independently converts body/title/auxiliary sp. Actual pixel metrics are captured in viewport/cache key and retained on each ReaderPage, then reused by Canvas; title size no longer changes with header visibility. Synthetic legacy engine callers keep linear fallback. Quick tools prioritize text/background/brightness, support scrolling on short screens, and keep presets accessible. Pointer handling observes consumption after child controls, preventing tool clicks from reaching body navigation.
- 08: reader tool surfaces opaque with independent scrim; preset palettes use darker auxiliary text (night uses lighter), custom text automatically increases contrast to 4.5. Compact cover omits tiny author/footer duplicates. Bookmark deletion keeps original object and offers Undo; repository restore preserves ID/offset/time. Search return callbacks added to ReaderScreen; root owns navigation wiring.

## Tests added / adjusted

- `ReaderSessionControllerTest`: deterministic body-only eviction with retained page cache, UTF-16 offset, and stale A/B commits rejected after C/new book.
- `ReaderPreferenceEditsTest`: custom A -> preset -> serialization -> custom A; preset edit becomes custom; simultaneous field edits preserve both and current language; colors do not invalidate layout/snapshot.
- `ReaderLifecycleInstrumentedTest`: real ViewModel independent eviction, cached chapter switch, reflow body/anchor, A->B->C, Activity recreate; repository reload custom/preset compatibility.
- `ReaderFontMetricsInstrumentedTest`: 1.0/1.3/1.5/2.0 font scales x portrait/landscape/wide, Chinese/English/emoji/combining text/long title, exact body coverage and measured width agreement with retained pixel snapshot.
- `ReaderSettingsDraftInstrumentedTest`: no writes during drag, preview, close commits last value once; release commits once.
- `ReaderUiInstrumentedTest`: old no-scroll/no-brightness assertions replaced with the newly specified scrollable quick panel, reachable brightness/presets and unchanged reading progress. Other UI assertions retained.

## Verification status

Parent owns serialized Gradle and device runs. Do not infer a pass from this note. Initial controller XML showed 9 tests / 0 failures, including 2 added regressions, but later changes require parent integrated rerun. Other new tests were not yet executed when this note was written.

Built-in text contrast calculated from opaque sRGB colors; initial calculation caught two tool-surface failures (warm brown 4.47 and night 4.29), then those palette values were corrected. Parent should capture screenshot/interaction proof and run final contrast calculation; no pixel-only unit tests were added.

## Remaining evidence

True process restart remains distinct from Activity recreate and is owned by root/scripts. Physical device performance and TalkBack interaction require current environment evidence; no pass claimed here.

## QA4 corrections

- Actual failure through shelf UI: book/chapters resolved, loading true, zero pages. Static trace identifies first-measure race: `loadChapter` captured 0x0 viewport, body I/O suspended, `setViewport` received valid dimensions but skipped restart because `loadedChapterId` was still null; resumed request returned early from preview/pagination with its old zero viewport and left loading true. `setViewport` now restarts whenever book/chapter metadata exists, using the current request's chapter/anchor. Mounting ReaderScreen no longer starts a redundant pagination request for persisted preferences; only an actual local preview does.
- Large-font short-landscape assertion failed at pause button (`ReaderLargeFontLandscapeInstrumentedTest.kt:94`): measured 46.476196 dp. Pause TextButton now has explicit 48 dp min height/width from the shared token; existing 48 dp assertion remains unchanged.
- Both fixes require affected QA reruns; no device/build command was run by this agent.
