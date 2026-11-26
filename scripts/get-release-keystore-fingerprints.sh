#!/usr/bin/env bash
set -euo pipefail

# This script is located at: dolil-onushondan/scripts/
# The keystore is located at: signing-keys/ in the workspace root.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
KEYSTORE="$WORKSPACE_DIR/signing-keys/dolilonushodan-release.jks"
OUTPUT="$WORKSPACE_DIR/signing-keys/release-keystore-fingerprints.txt"

if ! command -v keytool >/dev/null 2>&1; then
    echo "ERROR: keytool is not installed or not in PATH." >&2
    exit 1
fi

if [[ ! -f "$KEYSTORE" ]]; then
    echo "ERROR: Keystore not found: $KEYSTORE" >&2
    exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"
umask 077

echo "Enter your release keystore password when prompted."
keytool -list -v -keystore "$KEYSTORE" \
    | grep -Ei 'Alias name:|SHA1:|SHA-1:|SHA256:|SHA-256:' \
    > "$OUTPUT"

if [[ ! -s "$OUTPUT" ]]; then
    echo "ERROR: No alias or SHA fingerprints were found." >&2
    rm -f "$OUTPUT"
    exit 1
fi

printf '\nSUCCESS: SHA fingerprints saved to:\n%s\n\n' "$OUTPUT"
cat "$OUTPUT"
