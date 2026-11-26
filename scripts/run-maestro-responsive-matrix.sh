#!/usr/bin/env bash
set -Eeuo pipefail

results_dir="${1:-build/maestro-results/responsive}"
serial="${ANDROID_SERIAL:-emulator-5554}"
flow="e2e/responsive/responsive-ui.yaml"

mkdir -p "$results_dir"

restore_display() {
  adb -s "$serial" shell wm size reset >/dev/null 2>&1 || true
  adb -s "$serial" shell wm density reset >/dev/null 2>&1 || true
  adb -s "$serial" shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  adb -s "$serial" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
  adb -s "$serial" shell settings put system user_rotation 0 >/dev/null 2>&1 || true
}

trap restore_display EXIT

run_case() {
  local name="$1"
  local size="$2"
  local density="$3"
  local font_scale="$4"
  local rotation="$5"
  local case_dir="$results_dir/$name"

  mkdir -p "$case_dir"
  printf '\n=== Responsive case: %s ===\n' "$name"

  adb -s "$serial" shell wm size "$size"
  adb -s "$serial" shell wm density "$density"
  adb -s "$serial" shell settings put system font_scale "$font_scale"
  adb -s "$serial" shell settings put system accelerometer_rotation 0
  adb -s "$serial" shell settings put system user_rotation "$rotation"
  sleep 3

  adb -s "$serial" shell wm size
  adb -s "$serial" shell wm density
  adb -s "$serial" shell settings get system font_scale
  adb -s "$serial" shell settings get system user_rotation

  maestro test \
    --test-output-dir="$case_dir" \
    --format=junit \
    --output="$case_dir/report.xml" \
    "$flow"
}

# 360dp compact phone at 1.0x font scale.
run_case "phone-360dp" "1080x2400" "480" "1.0" "0"

# Approximately 411dp phone with enlarged system text (1.3x).
run_case "phone-411dp-font-1_5" "1080x2400" "420" "1.5" "0"

# Landscape phone; NavigationRail and expanded-width layouts become eligible.
run_case "landscape-phone" "2400x1080" "440" "1.0" "1"

# 960dp tablet profile for list/detail and expanded layout behavior.
run_case "tablet-960dp" "1920x1440" "320" "1.0" "0"

printf '\nAll responsive emulator profiles passed.\n'
