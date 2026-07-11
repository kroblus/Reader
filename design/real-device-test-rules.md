# LightReader Real-Device Test Rules

Last updated: 2026-07-11

## Goal

This rule set defines the minimum complete real-device verification for LightReader before reporting a build as device-tested.

## Device Rules

1. `adb devices -l` must show at least one online physical device with state `device`.
2. An Android emulator is allowed only as a fallback smoke environment. It does not count as a completed real-device test.
3. Before running device tests, record:
   - device serial
   - manufacturer and model
   - Android SDK version
   - build fingerprint
   - current locale
   - battery level
   - free internal storage
4. USB debugging must be trusted and the screen must stay unlocked during Compose UI tests.
5. If more than one device is online, set `ANDROID_SERIAL` explicitly before running Gradle or ADB commands.

## Build Gate

Run these checks before any real-device result is accepted:

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintDebug --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:assembleRelease --console=plain
git diff --check
```

The build gate fails if any command exits non-zero. Warnings are allowed only when the command exits zero and the warning is understood.

## APK Gate

Verify the signed release APK before manual installation. A release build without the four
`RELEASE_*` signing values is intentionally unsigned and must not be distributed:

```powershell
$env:ANDROID_HOME = (Get-Content local.properties | Select-String '^sdk.dir=').ToString().Split('=',2)[1]
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --verbose app\build\outputs\apk\release\app-release.apk
& "$env:ANDROID_HOME\build-tools\36.0.0\aapt.exe" dump badging app\build\outputs\apk\release\app-release.apk
```

Pass criteria:

1. Signature verification succeeds.
2. `package` reports `com.lightreader.app`.
3. `versionName` and `versionCode` match `app/build.gradle.kts`.
4. `minSdkVersion`, `targetSdkVersion`, and launchable activity are present.

## Automated Real-Device Gate

Run:

```powershell
.\gradlew.bat :app:connectedQaAndroidTest --console=plain
```

This installs and resets only `com.lightreader.app.qa`; it must never be changed back to the
production `debug` package for a device containing real reading data. The gate must cover these
existing instrumented areas:

1. Room migrations and core persistence.
2. TXT import, including legacy Chinese encoding normalization and lazy search indexing.
3. Large TXT import pressure path.
4. EPUB import basics.
5. Reader pagination and page-boundary continuity.
6. Reader controls, settings dock, auto-hide behavior, bookmarks, TOC, and search.
7. Library empty state, skin switch, book edit/delete confirmations, app settings, language switch, and API key delete confirmation.
8. Web import task controls and WebView DOM bridge behavior.

The automated gate fails if any test fails, times out, or leaves the target app in a crash loop.

## Launch Smoke Gate

After automated tests, install and launch the isolated QA APK on the same physical device. This must not
force-stop, clear, or overwrite the production package:

```powershell
adb install -r app\build\outputs\apk\qa\app-qa.apk
adb shell am force-stop com.lightreader.app.qa
adb shell am start -W -n com.lightreader.app.qa/com.lightreader.app.MainActivity
adb shell pidof com.lightreader.app.qa
adb logcat -d -t 300
adb shell screencap -p /sdcard/lightreader-qa-home.png
adb pull /sdcard/lightreader-qa-home.png build\reports\real-device-home.png
adb shell rm /sdcard/lightreader-qa-home.png
```

Pass criteria:

1. `am start -W` exits successfully and reports the main activity.
2. `pidof` returns a process id.
3. Recent `logcat` contains no app crash, fatal exception, ANR, Room migration failure, or strict security error from `com.lightreader.app.qa`.
4. The screenshot shows a non-blank LightReader screen.

## Manual Real-Device Checklist

If a user-facing release is being validated, complete this checklist after the automated gate:

Installing a production release on a device that already contains `com.lightreader.app` requires an
explicit backup and approval. The automated checklist never clears production data.

1. Cold launch from a clean install and verify the empty shelf, file import entry, app settings, web import, DeepSeek settings, and DOM bridge entry are reachable.
2. Import a UTF-8 TXT and a GB18030 or GBK Chinese TXT; verify chapter splitting, readable Chinese text, search results, and persisted reading progress.
3. Import a non-DRM EPUB and verify title, chapter, and body text.
4. Open a book, wait for reader chrome to auto-hide, tap to restore it, open TOC, bookmarks, search, and settings.
5. In reader settings, change font size, line spacing, paragraph spacing, margins, background, brightness, layout preset, and page-turn mode; verify only layout-affecting settings repaginate.
6. Verify left, center, and right tap zones in the default reader mode; then enable fullscreen-tap-next and verify side taps advance while the center still opens controls.
7. Rapidly turn pages across chapter boundaries using tap, swipe, volume key, and auto-read; verify there is no blank page, skipped chapter, or wrong progress.
8. Add, jump to, and delete bookmarks.
9. Run in-book search from an empty query, a no-result query, and a real result; jump back to the reader from a result.
10. Create or inspect a web import task from a public HTTPS catalog page; verify preview, task status, retry/cancel/delete confirmations, and web-book refresh controls.
11. Save a test API key value, verify configured state, then delete it through the confirmation dialog. Do not use or record a real secret in test evidence.
12. Open the DOM bridge page with a public HTTPS page and verify DOM HTML and body text preview.

## Evidence Rules

Every complete run must report:

1. command list and pass/fail result
2. device metadata
3. APK path and version
4. connected test summary
5. launch timing from `am start -W`
6. logcat crash scan result
7. screenshot path
8. skipped manual items, with reason

## Stop Conditions

Stop and report blocked if:

1. no physical device is online
2. the device is unauthorized or offline after reconnect attempts
3. Gradle cannot install the test APK
4. the app crashes on launch
5. a failing test indicates possible data loss, wrong text decoding, unreadable reader text, broken progress persistence, or broken secret handling
