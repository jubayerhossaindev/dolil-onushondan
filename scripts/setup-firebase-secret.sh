#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="/workspaces/codespaces-blank/dolil-onushondan"
CONFIG_FILE="$PROJECT_DIR/app/google-services.json"
OUTPUT_DIR="/workspaces/codespaces-blank/signing-keys"
BASE64_FILE="$OUTPUT_DIR/FIREBASE_CONFIG_BASE64.txt"
EXPECTED_PACKAGE="com.dolil.onushodan"

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

command -v base64 >/dev/null 2>&1 || fail "base64 is not installed or is not on PATH."
command -v python3 >/dev/null 2>&1 || fail "python3 is required to validate google-services.json."
command -v mktemp >/dev/null 2>&1 || fail "mktemp is not installed or is not on PATH."

[[ -d "$PROJECT_DIR" ]] || fail "Project directory not found: $PROJECT_DIR"
[[ -f "$CONFIG_FILE" ]] || fail "Firebase config file not found: $CONFIG_FILE"
[[ -s "$CONFIG_FILE" ]] || fail "Firebase config file is empty: $CONFIG_FILE"

printf '%s\n' "Validating Firebase configuration..."
python3 - "$CONFIG_FILE" "$EXPECTED_PACKAGE" <<'PY'
import json
import pathlib
import sys

config_path = pathlib.Path(sys.argv[1])
expected_package = sys.argv[2]

try:
    config = json.loads(config_path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as error:
    raise SystemExit(f"Invalid google-services.json: {error}") from error

project_id = config.get("project_info", {}).get("project_id")
clients = config.get("client", [])
package_names = {
    client.get("client_info", {}).get("android_client_info", {}).get("package_name")
    for client in clients
}

if not project_id or project_id == "dummy-project":
    raise SystemExit("Firebase config must contain a real project_id, not a dummy value.")
if expected_package not in package_names:
    found = sorted(name for name in package_names if name)
    raise SystemExit(
        "Firebase config does not contain the expected Android package "
        f"{expected_package!r}. Found: {found}"
    )

print(f"Firebase project ID: {project_id}")
print(f"Android package: {expected_package}")
PY

# Keep the encoded config outside the repository and restrict access to this user.
mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR"
umask 077

temp_file="$(mktemp "$OUTPUT_DIR/.FIREBASE_CONFIG_BASE64.XXXXXX")"
trap 'rm -f "$temp_file"' EXIT

base64 -w 0 "$CONFIG_FILE" > "$temp_file"
[[ -s "$temp_file" ]] || fail "Base64 output is empty."
chmod 600 "$temp_file"
mv -f "$temp_file" "$BASE64_FILE"
trap - EXIT

printf '\n%s\n' "SUCCESS: Base64 config file created."
printf 'Saved to: %s\n' "$BASE64_FILE"
printf 'File size: %s bytes\n' "$(wc -c < "$BASE64_FILE" | tr -d ' ')"
printf '\n%s\n' "Next: manually copy the file contents into GitHub Environment secret:"
printf '%s\n' "Environment: android-release"
printf '%s\n' "Secret name: FIREBASE_CONFIG_BASE64"
printf '%s\n' "The secret value was NOT uploaded, displayed, or printed by this script."
