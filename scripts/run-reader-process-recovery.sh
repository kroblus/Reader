#!/usr/bin/env bash
# Two direct instrumentation processes preserve only isolated QA data between phases.
set -euo pipefail
serial="${1:?An authorized QA serial is required}"
run_id="${2:?A run ID is required}"
source_id="${3:?A source ID is required}"
report="app/build/reports/process-recovery/${run_id}-${source_id}"
mkdir -p "$report"
tests=0
failures=0
errors=0
finish() {
  status=$?
  if [[ "$status" -ne 0 && "$failures" -eq 0 && "$errors" -eq 0 ]]; then errors=1; fi
  printf '{"tests":%s,"failures":%s,"errors":%s,"exit_code":%s}\n' "$tests" "$failures" "$errors" "$status" > "$report/result.json"
}
trap finish EXIT
# Validate actual APK identities before either install. Fixed filenames alone do not prove isolation.
aapt_cmd="$(command -v aapt || true)"
if [[ -z "$aapt_cmd" ]]; then
  reader_sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -n "$reader_sdk_root" && ! -d "$reader_sdk_root" ]] && command -v cygpath >/dev/null 2>&1; then
    reader_sdk_root="$(cygpath -u "$reader_sdk_root")"
  fi
  if [[ -n "$reader_sdk_root" ]]; then
    while IFS= read -r candidate; do
      if [[ -f "$candidate" && -x "$candidate" ]]; then aapt_cmd="$candidate"; fi
    done < <(printf '%s\n' "$reader_sdk_root"/build-tools/*/aapt "$reader_sdk_root"/build-tools/*/aapt.exe | sort -V)
  fi
fi
if [[ -z "$aapt_cmd" ]]; then
  echo 'aapt is required to verify QA APK identities; set ANDROID_HOME or ANDROID_SDK_ROOT.' >&2
  exit 1
fi
"$aapt_cmd" dump badging app/build/outputs/apk/qa/app-qa.apk > "$report/app-identity.txt" 2>&1
"$aapt_cmd" dump badging app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk > "$report/test-identity.txt" 2>&1
"$aapt_cmd" dump xmltree app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk AndroidManifest.xml > "$report/test-target.txt" 2>&1
if ! grep -Eq "^package: name='com[.]lightreader[.]app[.]qa'([[:space:]]|$)" "$report/app-identity.txt" ||
   ! grep -Eq "^package: name='com[.]lightreader[.]app[.]qa[.]test'([[:space:]]|$)" "$report/test-identity.txt" ||
   ! grep -Eq 'android:targetPackage[^=]*="com[.]lightreader[.]app[.]qa"' "$report/test-target.txt"; then
  echo 'Refusing installation: APK identities must be exactly QA, QA.test and instrumentation target QA.' >&2
  exit 1
fi
adb -s "$serial" install -r -t app/build/outputs/apk/qa/app-qa.apk > "$report/app-install.txt"
adb -s "$serial" install -r -t app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk > "$report/test-install.txt"
for phase in prepareSavedAnchor verifySavedAnchorInNewProcess; do
  # adb can exit zero even when instrumentation failed; validate its actual result.
  adb -s "$serial" shell am instrument -w -e clearPackageData false -e runId "$run_id" -e sourceId "$source_id" \
    -e class "com.lightreader.app.ReaderProcessRecoveryInstrumentedTest#$phase" \
    com.lightreader.app.qa.test/androidx.test.runner.AndroidJUnitRunner | tee "$report/$phase.txt"
  if grep -Eq 'OK[[:space:]]*\(1 test\)' "$report/$phase.txt" && ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED' "$report/$phase.txt"; then
    tests=$((tests + 1))
  else
    count=$(sed -n 's/.*Tests run: *\([0-9]*\), *Failures: *\([0-9]*\).*/\1 \2/p' "$report/$phase.txt" | tail -1)
    if [[ -n "$count" ]]; then
      read -r phase_tests phase_failures <<< "$count"
      tests=$((tests + phase_tests)); failures=$((failures + phase_failures))
    else errors=$((errors + 1)); fi
    exit 1
  fi
  if [[ "$phase" == prepareSavedAnchor ]]; then adb -s "$serial" shell am force-stop com.lightreader.app.qa; fi
done
