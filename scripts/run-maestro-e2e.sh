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

# Apply Android Emulator's documented cold-boot CPU mitigations before UI testing.
# A longer screen timeout keeps the display available while avoiding aggressive
# background work associated with an AC-powered, idle emulator.
adb shell settings put system screen_off_timeout 214783647

# Google documents this permission revocation to reduce launcher/home-screen
# CPU usage from hotword detection on Google APIs system images.
if adb shell pm list packages com.google.android.googlequicksearchbox | grep -q "package:com.google.android.googlequicksearchbox"; then
  adb shell pm revoke com.google.android.googlequicksearchbox android.permission.RECORD_AUDIO || {
    echo "WARNING: Could not revoke Google app microphone permission; continuing." >&2
  }
fi

# Let first-boot system components settle before installing and launching the app.
sleep 15

adb install -r "$apk_path"
adb shell pm path "$app_id" > "$results_dir/installed-package.txt"
maestro --version > "$results_dir/maestro-version.txt"

debug_dir="$results_dir/debug-output"
mkdir -p "$debug_dir"

set +e
maestro test \
  --test-output-dir="$results_dir" \
  --debug-output="$debug_dir" \
  --format=junit \
  --output="$results_dir/report.xml" \
  e2e/maestro
test_status=$?
set -e

# Keep device evidence before any diagnostic command can alter the visible state.
adb exec-out screencap -p > "$results_dir/final-screen.png" 2>/dev/null || true
adb shell dumpsys activity activities > "$results_dir/activities.txt" 2>&1 || true
adb logcat -b all -d -t 20000 > "$results_dir/logcat.txt" 2>&1 || true

if [[ "$test_status" -ne 0 ]]; then
  # Maestro's hierarchy command records the same accessibility tree used by selectors.
  maestro hierarchy > "$results_dir/failure-hierarchy.json" 2> "$results_dir/failure-hierarchy.stderr" || {
    echo "WARNING: maestro hierarchy failed; falling back to Android UIAutomator." >&2
    adb shell uiautomator dump /sdcard/maestro-failure-hierarchy.xml >/dev/null 2>&1 || true
    adb pull /sdcard/maestro-failure-hierarchy.xml "$results_dir/failure-hierarchy.xml" >/dev/null 2>&1 || true
  }
fi

printf '%s\n' "$test_status" > "$results_dir/exit-code.txt"

if [[ "$test_status" -ne 0 ]]; then
  echo "Maestro E2E failed with exit code $test_status." >&2
  exit "$test_status"
fi

echo "Maestro E2E completed successfully."
