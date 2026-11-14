# Action windows and portrait shelf cards

## Report and reproduction

The reported entry is Library → More → Change skin on a MEIZU 20 Pro, Android 16 / API 36. The previous fix covered the More popup and navigation to App settings, but the theme sheet creates a separate Dialog window.

`ActionSurfaceImeInstrumentedTest` observes all attached app windows through `WindowInspector` on real Choreographer frames. It keeps normal system animations, does not replace inset listeners, and samples opening, selecting, closing and returning to the library. The theme test disables UiAutomator's implicit idle wait so a second tap can occur while the first popup is still animating.

The v0.3.6 QA baseline reproduced three IME frames after the keyboard had already hidden during the visible-search → More → skin transition. Baseline sorting also left the search keyboard open. Evidence: `build/ime-overlays/ime-surfaces-fast-baseline-20261114T135813Z`.

## Fix

- Dedicated action popups, sheets and confirmation dialogs retain window focus for Back and key navigation, but use `FLAG_ALT_FOCUSABLE_IM` so they cannot become input-method targets.
- Dialog policy is applied from the dialog content before its first focus, and remains set through teardown. It is not temporarily cleared during exit animation.
- Sort, book actions, import, URL parsing and confirmation entry points end the previous input session. The search field cannot regain focus behind a library modal.
- Editable book metadata dialogs continue to use a normal Material dialog. Actual keyboard input is checked both there and in library search.
- The common policy covers theme, language, import source, deletion/duplicate confirmation, license and busy windows. No manifest-wide keyboard suppression is added.

The implementation follows Android's [window flag contract](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_ALT_FOCUSABLE_IM) and the project's actual Material 3 1.4.0 dialog lifecycle. The first fixed physical-phone run passed the rapid theme regression with 1,404 transition frames and zero unexpected IME frames; its observed popup and dialog flags both contained `ALT_FOCUSABLE_IM` (`ime-surfaces-fix-20261114T140038Z`). That intermediate run also exposed test navigation issues: the import FAB is covered while the keyboard is visible, and scrolling/IME exit can move subsequent tap targets. Those paths now wait for reachable, settled controls; the rapid theme path retains immediate taps.

## Shelf presentation

At the user's request, read and unread books now share the full portrait pastel cover in the main grid. The separate wide recent-reading card is removed. Current chapter, progress bar/percentage, continue-reading label and last-read date remain visible on the relevant card. Search, sort and saved reading state retain their existing data rules; no database migration or position reset is introduced.

`LibraryDiscoveryInstrumentedTest` checks that the continue-reading card retains its seeded chapter and 3% progress (100 of 2,972 characters), including library navigation and filtering behavior.

## Verification

- 72 app JVM tests passed. Final QA build and Lint passed (zero errors; 64 warnings and one hint).
- Android 15 / API 35 emulator with normal animations: all four new action-surface tests, all three existing settings IME tests, and the API/book deletion confirmation journeys passed. The new tests observed 2,911 transition frames across 18 action-window checks with zero unexpected IME frames. Real keyboard input in search and the editable metadata dialog remained functional. Evidence: `ime-final-api35-20261114T140913Z`.
- The shelf discovery test passed after correcting its new fixture-percentage expectation to the calculated 3%; it verifies retained chapter/progress, search, sorting and navigation. Evidence: `ime-library-api35-20261114T141214Z`, screenshots under `build/ime-overlays/library-gallery`.
- After the phone reconnected, the final QA application passed the theme/settings/editable-dialog checks, the three prior IME checks, shelf discovery and API/book confirmation journeys. One sorting tap encountered a stale UiAutomator accessibility node; the test now re-queries only on `StaleObjectException`, without restarting observation or dropping frames. The full sorting/book-menu/import/deletion test then passed. The 18 completed action-window checks captured 5,258 transition frames with zero unexpected IME frames. Evidence: `ime-final-reconnected-phone-20261114T144346Z` and `ime-final-phone-shelf-actions-20261114T144822Z`. The QA application APK remained byte-identical (`d25778a4f75c3eb6de4d275d653e797374b863e04c61ac87a691272836b0b0e4`); the follow-up changes only test synchronization and this verification record.
- [GitHub release run 43603799092](https://github.com/kroblus/Reader/actions/runs/43603799092) passed all 124 device tests (zero failures/skips), two process-recovery stages, two fresh-install backup checks, unit/lint/build gates and signing verification for source `95475319dbc64cab76acc37d15008c8c39cf8f28`.
- The official v0.3.7 / code 21 APK was downloaded, verified and installed over the phone's v0.3.6. The original install timestamp and displayed current chapter/progress were unchanged; the portrait shelf launched successfully. APK SHA-256: `10702ebcb241dfec0fa8470107c08ef70676b59df83392ed6071ab0f45a74554`; release certificate: `f1a230a1f20d3041c808fabd8c150985949e6d830ce53b96cab00aa5e1efa5a5`; size: 17,207,784 bytes.
- Archived and hash-checked 51 test files (22,878,733 bytes) before deleting only the enumerated QA evidence/media and our screenshot/XML files. Removed `com.lightreader.app.qa` and `.qa.test`; retained the formal app and its library. Local evidence: `build/ime-overlays/phone-cleanup-archive/manifest.json`, `cleanup-result.json`, `phone-update-verified.json`, and `release/verified.json`.
