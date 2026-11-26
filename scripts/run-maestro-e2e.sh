#!/usr/bin/env bash
set -euo pipefail

app_id="com.dolil.onushodan"
apk_path="${1:-app/build/outputs/apk/debug/app-debug.apk}"
results_dir="build/maestro-results"

mkdir -p "$results_dir"

if [[ ! -f "$apk_path" ]]; then
  echo "ERROR: APK not found: $apk_path" >&2
  exit 2
fi

adb wait-for-device

boot_completed=""
for attempt in $(seq 1 60); do
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "$boot_completed" == "1" ]]; then
    break
  fi
  sleep 2
done

if [[ "$boot_completed" != "1" ]]; then
  echo "ERROR: Android emulator did not finish booting." >&2
  exit 3
fi

adb install -r "$apk_path"
adb shell pm path "$app_id" > "$results_dir/installed-package.txt"
maestro --version > "$results_dir/maestro-version.txt"

set +e
maestro test --test-output-dir="$results_dir" e2e/maestro
test_status=$?
set -e

# Keep device evidence even when an assertion fails.
adb logcat -b all -d -t 20000 > "$results_dir/logcat.txt" 2>&1 || true
adb shell dumpsys activity activities > "$results_dir/activities.txt" 2>&1 || true
adb exec-out screencap -p > "$results_dir/final-screen.png" 2>/dev/null || true

printf '%s\n' "$test_status" > "$results_dir/exit-code.txt"

if [[ "$test_status" -ne 0 ]]; then
  echo "Maestro E2E failed with exit code $test_status." >&2
  exit "$test_status"
fi

echo "Maestro E2E completed successfully."
