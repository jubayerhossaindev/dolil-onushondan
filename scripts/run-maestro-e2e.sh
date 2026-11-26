#!/usr/bin/env bash
set -Eeuo pipefail

app_id="com.dolil.onushodan"
apk_path="${1:-app/build/outputs/apk/debug/app-debug.apk}"
results_dir="${RESULTS_DIR:-build/maestro-results}"
api_level="${ANDROID_API_LEVEL:-35}"
target="${ANDROID_TARGET:-google_apis}"
arch="${ANDROID_ARCH:-x86_64}"
device_profile="${ANDROID_DEVICE_PROFILE:-pixel_5}"
avd_name="maestro-api-${api_level}"
emulator_port="${EMULATOR_PORT:-5554}"
boot_timeout_seconds="${EMULATOR_BOOT_TIMEOUT_SECONDS:-1200}"
first_boot_settle_seconds="${ANDROID_FIRST_BOOT_SETTLE_SECONDS:-75}"

mkdir -p "$results_dir"

emulator_pid=""
evidence_captured="false"
test_status=0

log() {
  printf '[%s] %s\n' "$(date --iso-8601=seconds)" "$*"
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

resolve_sdk_tool() {
  local tool_name="$1"
  local candidate=""

  if command -v "$tool_name" >/dev/null 2>&1; then
    command -v "$tool_name"
    return 0
  fi

  if [[ -n "${ANDROID_HOME:-}" ]]; then
    for candidate in "$ANDROID_HOME"/cmdline-tools/*/bin/"$tool_name"; do
      if [[ -x "$candidate" ]]; then
        printf '%s\n' "$candidate"
        return 0
      fi
    done
  fi

  return 1
}

capture_evidence() {
  local serial="emulator-$emulator_port"

  if ! adb devices | grep -Fq "$serial"; then
    return 0
  fi

  adb -s "$serial" exec-out screencap -p > "$results_dir/final-screen.png" 2>/dev/null || true
  adb -s "$serial" shell dumpsys activity activities > "$results_dir/activities.txt" 2>&1 || true
  adb -s "$serial" shell dumpsys window windows > "$results_dir/window-state.txt" 2>&1 || true
  adb -s "$serial" shell dumpsys activity lastanr > "$results_dir/last-anr.txt" 2>&1 || true
  adb -s "$serial" logcat -b all -d -t 30000 > "$results_dir/logcat.txt" 2>&1 || true
  adb -s "$serial" logcat -b events -d -t 12000 > "$results_dir/events-logcat.txt" 2>&1 || true

  evidence_captured="true"
}

cleanup() {
  local status="$?"
  trap - EXIT

  if [[ "$evidence_captured" != "true" ]]; then
    capture_evidence
  fi

  if [[ -n "$emulator_pid" ]] && kill -0 "$emulator_pid" 2>/dev/null; then
    adb -s "emulator-$emulator_port" emu kill >/dev/null 2>&1 || kill "$emulator_pid" 2>/dev/null || true
    wait "$emulator_pid" 2>/dev/null || true
  fi

  exit "$status"
}
trap cleanup EXIT

[[ -f "$apk_path" ]] || fail "APK not found: $apk_path"
[[ -n "${ANDROID_HOME:-}" ]] || fail "ANDROID_HOME is not set."

sdkmanager="$(resolve_sdk_tool sdkmanager)" || fail "Android sdkmanager was not found."
avdmanager="$(resolve_sdk_tool avdmanager)" || fail "Android avdmanager was not found."
emulator_bin="$ANDROID_HOME/emulator/emulator"
[[ -x "$emulator_bin" ]] || fail "Android Emulator binary not found: $emulator_bin"

log "Android SDK: $ANDROID_HOME"
log "Emulator version: $("$emulator_bin" -version 2>&1 | head -n 1)"
log "SDK Manager: $sdkmanager"
log "AVD Manager: $avdmanager"

# Install only the SDK packages required by this job; sdkmanager leaves already
# installed packages untouched. The API/system image remain pinned by workflow env.
system_image="system-images;android-$api_level;$target;$arch"
installed_packages="$("$sdkmanager" --list_installed 2>&1)"
missing_packages=()
for package in platform-tools emulator "$system_image"; do
  if ! grep -Fq "$package" <<< "$installed_packages"; then
    missing_packages+=("$package")
  fi
done

if (("${#missing_packages[@]}" > 0)); then
  log "Installing missing Android SDK packages: ${missing_packages[*]}"
  "$sdkmanager" --install "${missing_packages[@]}"
fi

# Configure KVM permissions when the hosted runner exposes /dev/kvm.
if [[ -e /dev/kvm ]]; then
  echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
    | sudo tee /etc/udev/rules.d/99-kvm4all.rules >/dev/null
  sudo udevadm control --reload-rules
  sudo udevadm trigger --name-match=kvm
  sleep 1
fi

accel_status=0
"$emulator_bin" -accel-check > "$results_dir/acceleration-check.txt" 2>&1 || accel_status="$?"
cat "$results_dir/acceleration-check.txt"

if [[ -r /dev/kvm && -w /dev/kvm ]]; then
  if [[ "$accel_status" -ne 0 ]] || ! grep -Eqi 'KVM.*installed and usable' "$results_dir/acceleration-check.txt"; then
    fail "The runner exposes /dev/kvm but Android Emulator could not verify KVM acceleration."
  fi
  accel_mode="auto"
  printf 'KVM acceleration verified by emulator -accel-check.\n' > "$results_dir/acceleration-mode.txt"
else
  export ANDROID_I_WANT_MY_TCG=yes
  accel_mode="off"
  printf 'KVM unavailable; emulator will use explicit TCG software emulation.\n' > "$results_dir/acceleration-mode.txt"
fi

export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
mkdir -p "$ANDROID_AVD_HOME"

# A fresh AVD plus -no-snapshot prevents Quick Boot from restoring stale system UI,
# launcher ANR dialogs, app state, or user data from a previous emulator session.
"$avdmanager" delete avd -n "$avd_name" >/dev/null 2>&1 || true
printf 'no\n' | "$avdmanager" create avd \
  --force \
  --name "$avd_name" \
  --package "$system_image" \
  --device "$device_profile"

avd_config="$ANDROID_AVD_HOME/$avd_name.avd/config.ini"
[[ -f "$avd_config" ]] || fail "AVD configuration was not created: $avd_config"
set_avd_property() {
  local property="$1"
  local value="$2"

  if grep -q "^$property=" "$avd_config"; then
    sed -i "s|^$property=.*|$property=$value|" "$avd_config"
  else
    printf '\n%s=%s\n' "$property" "$value" >> "$avd_config"
  fi
}

set_avd_property "hw.cpu.ncore" "2"
# Keep the guest ADB transport closed until Android has completed its boot.
# This avoids exposing the host to the early boot-time adbd restart window.
set_avd_property "test.delayAdbTillBootComplete" "1"
printf '%s\n' "$avd_config" > "$results_dir/avd-config-path.txt"
cp "$avd_config" "$results_dir/avd-config.ini"

adb start-server
emulator_args=(
  -port "$emulator_port"
  -avd "$avd_name"
  -accel "$accel_mode"
  -no-window
  -gpu swiftshader_indirect
  # Disable snapshot storage itself, not just automatic Quick Boot save/load.
  -no-snapstorage
  -noaudio
  -no-boot-anim
  -camera-back none
  -camera-front none
)

log "Starting the only emulator session for this job."
printf '%q ' "$emulator_bin" "${emulator_args[@]}" > "$results_dir/emulator-command.txt"
printf '\n' >> "$results_dir/emulator-command.txt"
"$emulator_bin" "${emulator_args[@]}" > "$results_dir/emulator-console.log" 2>&1 &
emulator_pid="$!"

serial="emulator-$emulator_port"
timeout 180s adb -s "$serial" wait-for-device || fail "ADB did not connect to $serial."

boot_completed=""
attempt=0
while ((attempt < boot_timeout_seconds)); do
  boot_completed="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "$boot_completed" == "1" ]]; then
    break
  fi
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    fail "Android Emulator exited before boot completed. See emulator-console.log."
  fi
  sleep 2
  attempt=$((attempt + 2))
done

[[ "$boot_completed" == "1" ]] || fail "Android Emulator did not complete boot within ${boot_timeout_seconds}s."
log "Android Emulator boot completed."
adb -s "$serial" shell getprop > "$results_dir/boot-properties.txt"
adb -s "$serial" shell settings put system screen_off_timeout 214783647
adb -s "$serial" shell settings put global window_animation_scale 0.0
adb -s "$serial" shell settings put global transition_animation_scale 0.0
adb -s "$serial" shell settings put global animator_duration_scale 0.0

# Google documents this permission revocation as a way to reduce idle hotword CPU
# work on Google APIs emulator images. It does not interact with the app under test.
if adb -s "$serial" shell pm list packages com.google.android.googlequicksearchbox \
  | grep -q 'package:com.google.android.googlequicksearchbox'; then
  adb -s "$serial" shell pm revoke \
    com.google.android.googlequicksearchbox android.permission.RECORD_AUDIO \
    > "$results_dir/google-app-audio-permission.txt" 2>&1 || {
      log "Google Search microphone permission was not granted/revocable; see diagnostic file."
    }
fi

# Google APIs images continue first-boot package and Play Services work after
# sys.boot_completed becomes 1. Allow that first-boot workload to settle before
# opening Maestro's driver connection. No key events are injected into the
# launcher; Maestro launches the target app directly.
log "Allowing ${first_boot_settle_seconds}s for first-boot Android services to settle."
sleep "$first_boot_settle_seconds"
adb -s "$serial" logcat -b events -d -t 12000 > "$results_dir/preflight-events-logcat.txt" 2>&1 || true
adb -s "$serial" shell uiautomator dump /sdcard/preflight-hierarchy.xml \
  > "$results_dir/preflight-hierarchy-dump.txt" 2>&1 || true
adb -s "$serial" pull /sdcard/preflight-hierarchy.xml \
  "$results_dir/preflight-hierarchy.xml" >/dev/null 2>&1 || true

if grep -Eqi 'am_anr.*com\.google\.android\.apps\.nexuslauncher|ANR in com\.google\.android\.apps\.nexuslauncher' \
  "$results_dir/preflight-events-logcat.txt"; then
  fail "Pixel Launcher ANR was reported during emulator boot, before Maestro started. See preflight-events-logcat.txt."
fi
if grep -Eqi "Pixel Launcher isn't responding|android:id/aerr_wait" \
  "$results_dir/preflight-hierarchy.xml"; then
  fail "Android system ANR dialog is visible before app launch. See preflight-hierarchy.xml."
fi

adb -s "$serial" install -r "$apk_path"
adb -s "$serial" shell pm path "$app_id" > "$results_dir/installed-package.txt"
maestro --version > "$results_dir/maestro-version.txt"

debug_dir="$results_dir/debug-output"
mkdir -p "$debug_dir"

# A transient ADB disconnect can poison a Maestro session (Maestro issue
# #3720). Run each flow in a fresh CLI process. Before opening a driver session,
# require both the ADB transport and Android's package manager to answer
# continuously; get-state alone can report "device" during a short-lived
# reconnect immediately before the next shell command fails.
wait_for_adb_stable() {
  local timeout_seconds="${1:-240}"
  local required_seconds="${2:-30}"
  local probe_interval_seconds=2
  local consecutive_seconds=0
  local elapsed_seconds=0
  local device_state=""
  local boot_completed=""
  local package_path=""

  while ((elapsed_seconds < timeout_seconds)); do
    device_state="$(adb -s "$serial" get-state 2>/dev/null || true)"
    boot_completed=""
    package_path=""

    if [[ "$device_state" == "device" ]]; then
      boot_completed="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      package_path="$(adb -s "$serial" shell pm path "$app_id" 2>/dev/null | tr -d '\r' || true)"
    fi

    if [[ "$device_state" == "device" && "$boot_completed" == "1" && "$package_path" == package:* ]]; then
      consecutive_seconds=$((consecutive_seconds + probe_interval_seconds))
      if ((consecutive_seconds >= required_seconds)); then
        log "ADB, Android boot state, and installed package remained responsive for ${required_seconds}s."
        return 0
      fi
    else
      if ((consecutive_seconds > 0)); then
        log "ADB readiness probe reset (state=${device_state:-missing}, boot=${boot_completed:-missing}, package=${package_path:-missing})."
      fi
      consecutive_seconds=0
    fi

    sleep "$probe_interval_seconds"
    elapsed_seconds=$((elapsed_seconds + probe_interval_seconds))
  done

  log "ADB/package readiness was not stable for ${required_seconds}s within ${timeout_seconds}s."
  return 1
}

restart_emulator_after_transport_loss() {
  local child_pids=""
  local child_pid=""
  local boot_completed=""
  local elapsed_seconds=0

  log "Capturing ADB state before a full emulator restart."
  adb devices -l > "$results_dir/adb-devices-before-emulator-restart.txt" 2>&1 || true

  # Prefer the emulator console's graceful shutdown while the transport still
  # responds. If it is offline, terminate the emulator launcher and its QEMU
  # child directly; restarting only the host ADB server did not restore the
  # guest adbd / Maestro instrumentation transport in earlier runs.
  if ! adb -s "$serial" emu kill > "$results_dir/emulator-stop.txt" 2>&1; then
    log "Emulator console was unreachable; requesting termination of the emulator process tree."
    child_pids="$(pgrep -P "$emulator_pid" 2>/dev/null || true)"
    if [[ -n "$child_pids" ]]; then
      while IFS= read -r child_pid; do
        [[ -n "$child_pid" ]] || continue
        kill -TERM "$child_pid" 2>/dev/null || true
      done <<< "$child_pids"
    fi
    kill -TERM "$emulator_pid" 2>/dev/null || true
  fi

  # Bound the graceful-shutdown path too. Never block the CI job forever on a
  # QEMU process whose ADB transport has already died.
  for ((elapsed_seconds = 0; elapsed_seconds < 20; elapsed_seconds += 1)); do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
      break
    fi
    sleep 1
  done

  if kill -0 "$emulator_pid" 2>/dev/null; then
    log "Emulator did not exit gracefully; force-stopping its QEMU child and launcher."
    child_pids="$(pgrep -P "$emulator_pid" 2>/dev/null || true)"
    if [[ -n "$child_pids" ]]; then
      while IFS= read -r child_pid; do
        [[ -n "$child_pid" ]] || continue
        kill -KILL "$child_pid" 2>/dev/null || true
      done <<< "$child_pids"
    fi
    kill -KILL "$emulator_pid" 2>/dev/null || true
  fi

  wait "$emulator_pid" 2>/dev/null || true
  emulator_pid=""

  log "Restarting the host ADB server after stopping the emulator."
  adb kill-server > "$results_dir/adb-server-restart.txt" 2>&1 || true
  adb start-server >> "$results_dir/adb-server-restart.txt" 2>&1 || {
    log "Host ADB server did not restart."
    return 1
  }

  # Reboot the same AVD without wiping userdata. The app and its database stay
  # installed; Maestro's next fresh CLI session reinstalls its own driver APKs.
  log "Starting a fresh Android emulator process for the single transport retry."
  "$emulator_bin" "${emulator_args[@]}" >> "$results_dir/emulator-console.log" 2>&1 &
  emulator_pid="$!"

  timeout 180s adb -s "$serial" wait-for-device || {
    adb devices -l > "$results_dir/adb-devices-after-emulator-restart-failure.txt" 2>&1 || true
    log "ADB did not reconnect to $serial after the full emulator restart."
    return 1
  }

  elapsed_seconds=0
  boot_completed=""
  while ((elapsed_seconds < boot_timeout_seconds)); do
    boot_completed="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot_completed" == "1" ]]; then
      break
    fi
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
      log "Android Emulator exited before completing its recovery boot."
      return 1
    fi
    sleep 2
    elapsed_seconds=$((elapsed_seconds + 2))
  done

  if [[ "$boot_completed" != "1" ]]; then
    log "Android Emulator did not complete recovery boot within ${boot_timeout_seconds}s."
    return 1
  fi

  adb -s "$serial" shell settings put system screen_off_timeout 214783647
  adb -s "$serial" shell settings put global window_animation_scale 0.0
  adb -s "$serial" shell settings put global transition_animation_scale 0.0
  adb -s "$serial" shell settings put global animator_duration_scale 0.0

  if ! adb -s "$serial" shell pm path "$app_id" 2>/dev/null | grep -q '^package:'; then
    log "Application package was absent after emulator restart; reinstalling the already-built APK."
    adb -s "$serial" install -r "$apk_path" || return 1
  fi

  adb devices -l > "$results_dir/adb-devices-after-emulator-restart.txt" 2>&1 || true
  wait_for_adb_stable 300 45
}
# Do not start Maestro during the first-boot ADB instability window. Require
# continuous shell-level responsiveness, not just an online transport state.
if ! wait_for_adb_stable 240 30; then
  fail "Android emulator ADB/package readiness did not stabilize before Maestro startup."
fi

flow_status=0
if wait_for_adb_stable 240 30; then
  for flow in e2e/maestro/*.yaml; do
    [[ -f "$flow" ]] || continue
    flow_name="$(basename "$flow" .yaml)"
    log "Running Maestro flow: $flow"

    flow_rc=1
    flow_report=""
    attempt=1

    while ((attempt <= 2)); do
      attempt_dir="$results_dir/flow-runs/$flow_name/attempt-$attempt"
      attempt_debug_dir="$debug_dir/$flow_name/attempt-$attempt"
      mkdir -p "$attempt_dir/test-output" "$attempt_debug_dir"

      log "Starting $flow_name in a fresh Maestro process (attempt $attempt)."
      set +e
      maestro test \
        --test-output-dir="$attempt_dir/test-output" \
        --debug-output="$attempt_debug_dir" \
        --format=junit \
        --output="$attempt_dir/report.xml" \
        "$flow" 2>&1 | tee "$attempt_dir/console.log"
      flow_rc=${PIPESTATUS[0]}
      set -e

      printf '%s\n' "$flow_rc" > "$attempt_dir/exit-code.txt"
      flow_report="$attempt_dir/report.xml"

      if [[ "$flow_rc" -eq 0 ]]; then
        break
      fi

      # Retry only when logs prove an ADB transport loss. Do not retry real
      # app assertions, validation failures, or other deterministic UI defects.
      if [[ "$attempt" -eq 1 ]] && grep -RIEq \
        'DeviceServerDiedException|StatusRuntimeException: UNAVAILABLE|device[[:space:]].*(offline|not found)|host:transport:.*(offline|not found)' \
        "$attempt_dir" "$attempt_debug_dir" 2>/dev/null; then
        log "Detected an ADB transport failure in $flow_name; reconnecting and requiring 30 seconds of responsive ADB/package-manager checks before one isolated retry."
        if restart_emulator_after_transport_loss; then
          attempt=2
          continue
        fi
        log "ADB did not recover to a stable state; $flow_name cannot be retried safely."
      fi

      break
    done

    if [[ -f "$flow_report" ]]; then
      mkdir -p "$results_dir/reports"
      cp "$flow_report" "$results_dir/reports/$flow_name.xml"
    fi

    if [[ "$flow_rc" -ne 0 ]]; then
      flow_status=1
      log "Maestro flow failed: $flow_name (exit $flow_rc)."
    else
      log "Maestro flow passed: $flow_name."
    fi

    if ! wait_for_adb_stable 180 15; then
      log "ADB became unstable after $flow_name; attempting explicit offline-device recovery."
      if ! restart_emulator_after_transport_loss; then
        flow_status=1
        log "Stopping the remaining flows because Android ADB/package readiness did not recover."
        break
      fi
    fi
  done
else
  flow_status=1
  log "Skipping Maestro flows because the emulator ADB connection was not stable after boot."
fi

# Keep one aggregate JUnit report for CI while preserving each final per-flow
# report and both attempts (when a transport retry was needed) as artifacts.
python3 - "$results_dir/report.xml" "$results_dir/reports" <<'PY'
import glob
import os
import sys
import xml.etree.ElementTree as ET

output_path, report_dir = sys.argv[1:]
aggregate = ET.Element("testsuites")
report_paths = sorted(glob.glob(os.path.join(report_dir, "*.xml")))

for report_path in report_paths:
    try:
        source_root = ET.parse(report_path).getroot()
    except (ET.ParseError, OSError) as error:
        print(f"Unable to read per-flow JUnit report {report_path}: {error}", file=sys.stderr)
        continue

    suites = [source_root] if source_root.tag == "testsuite" else [
        child for child in list(source_root) if child.tag == "testsuite"
    ]
    for suite in suites:
        aggregate.append(suite)

if not list(aggregate):
    suite = ET.SubElement(
        aggregate,
        "testsuite",
        name="Maestro runner",
        tests="1",
        failures="1",
        errors="0",
        time="0",
    )
    testcase = ET.SubElement(
        suite,
        "testcase",
        classname="maestro.runner",
        name="no-junit-report",
    )
    ET.SubElement(testcase, "failure", message="No per-flow JUnit report was generated")

total_tests = 0
total_failures = 0
total_errors = 0
total_time = 0.0

for suite in aggregate.findall("testsuite"):
    testcases = suite.findall(".//testcase")
    failures = sum(testcase.find("failure") is not None for testcase in testcases)
    errors = sum(testcase.find("error") is not None for testcase in testcases)
    suite.set("tests", str(len(testcases)))
    suite.set("failures", str(failures))
    suite.set("errors", str(errors))
    total_tests += len(testcases)
    total_failures += failures
    total_errors += errors
    try:
        total_time += float(suite.get("time", "0"))
    except ValueError:
        pass

aggregate.set("tests", str(total_tests))
aggregate.set("failures", str(total_failures))
aggregate.set("errors", str(total_errors))
aggregate.set("time", f"{total_time:.3f}")
ET.indent(aggregate, space="  ")
ET.ElementTree(aggregate).write(output_path, encoding="utf-8", xml_declaration=True)
print(
    f"Aggregated JUnit: {total_tests} tests, "
    f"{total_failures} failures, {total_errors} errors."
)
PY

test_status="$flow_status"

# 125 means the responsive matrix was intentionally not run; do not report a
# default zero as if responsive checks had passed after a core-suite failure.
responsive_status=125
if [[ "$test_status" -eq 0 ]]; then
  set +e
  ANDROID_SERIAL="$serial" bash scripts/run-maestro-responsive-matrix.sh "$results_dir/responsive"
  responsive_status="$?"
  set -e
else
  log "Responsive matrix skipped because one or more core Maestro flows failed."
fi

capture_evidence

if [[ "$test_status" -ne 0 || "$responsive_status" -ne 0 ]]; then
  maestro hierarchy > "$results_dir/failure-hierarchy.json" \
    2> "$results_dir/failure-hierarchy.stderr" || {
      echo "WARNING: maestro hierarchy failed; falling back to Android UIAutomator." >&2
      adb -s "$serial" shell uiautomator dump /sdcard/maestro-failure-hierarchy.xml \
        > "$results_dir/failure-hierarchy-dump.txt" 2>&1 || true
      adb -s "$serial" pull /sdcard/maestro-failure-hierarchy.xml \
        "$results_dir/failure-hierarchy.xml" >/dev/null 2>&1 || true
    }
fi

printf '%s\n' "$test_status" > "$results_dir/exit-code.txt"
printf '%s\n' "$responsive_status" > "$results_dir/responsive-exit-code.txt"
if [[ "$test_status" -ne 0 || "$responsive_status" -ne 0 ]]; then
  log "Maestro suite exit code: $test_status; responsive matrix exit code: $responsive_status."
  exit 1
fi

log "All Maestro feature flows and responsive emulator profiles completed successfully."
