
#!/bin/bash

set -e

echo "🔐 GitHub Secrets সেটআপ শুরু..."

KEY_DIR="/workspaces/codespaces-blank/signing-keys"
BASE64_FILE="$KEY_DIR/keystore-base64.txt"
FIREBASE_BASE64_FILE="$KEY_DIR/FIREBASE_CONFIG_BASE64.txt"

REPO="jubayerhossaindev/dolil-onushondan"
ENVIRONMENT="android-release"

if [ ! -f "$BASE64_FILE" ]; then
  echo "❌ Keystore Base64 file পাওয়া যায়নি: $BASE64_FILE"
  exit 1
fi

if [ ! -f "$FIREBASE_BASE64_FILE" ]; then
  echo "❌ Firebase Base64 file পাওয়া যায়নি: $FIREBASE_BASE64_FILE"
  exit 1
fi

# Password input
read -rsp "STORE_PASSWORD লিখুন: " STORE_PASSWORD
echo

read -rsp "KEY_PASSWORD লিখুন: " KEY_PASSWORD
echo

# KEYSTORE_BASE64
echo "📥 KEYSTORE_BASE64 সেট করা হচ্ছে..."
gh secret set KEYSTORE_BASE64 --repo "$REPO" < "$BASE64_FILE"

# STORE_PASSWORD
echo "📥 STORE_PASSWORD সেট করা হচ্ছে..."
printf '%s' "$STORE_PASSWORD" | gh secret set STORE_PASSWORD --repo "$REPO"

# KEY_PASSWORD
echo "📥 KEY_PASSWORD সেট করা হচ্ছে..."
printf '%s' "$KEY_PASSWORD" | gh secret set KEY_PASSWORD --repo "$REPO"

# FIREBASE_CONFIG_BASE64
echo "📥 FIREBASE_CONFIG_BASE64 সেট করা হচ্ছে..."
gh secret set FIREBASE_CONFIG_BASE64 --repo "$REPO" < "$FIREBASE_BASE64_FILE"

# Release Environment Firebase Secret
echo "📥 android-release Environment-এ FIREBASE_CONFIG_BASE64 সেট করা হচ্ছে..."
gh secret set FIREBASE_CONFIG_BASE64 \
  --repo "$REPO" \
  --env "$ENVIRONMENT" \
  < "$FIREBASE_BASE64_FILE"

echo "✅ সব GitHub Secret সফলভাবে সেট করা সম্পন্ন।"
