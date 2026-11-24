#!/bin/bash

set -e

KEY_DIR="/workspaces/codespaces-blank/signing-keys"
KEYSTORE="$KEY_DIR/dolilonushodan-release.jks"
ALIAS="dolilonushodan"

mkdir -p "$KEY_DIR"

if [ -f "$KEYSTORE" ]; then
  echo "⚠️  $KEYSTORE ইতিমধ্যে আছে। নতুন করে তৈরি করছেন না।"
  exit 0
fi

echo "🔐 Android signing key তৈরি করা হচ্ছে..."
echo "📁 ফোল্ডার: $KEY_DIR"

keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keypass "Jubayer321###" \
  -storepass "Jubayer321###" \
  -dname "CN=Dolil Onushodan, OU=Android, O=Dolil Onushodan, L=Narayanganj, S=Narayanganj, C=BD"

echo "✅ কীস্টোর তৈরি সম্পন্ন: $KEYSTORE"

BASE64_FILE="$KEY_DIR/keystore-base64.txt"
base64 -w0 "$KEYSTORE" > "$BASE64_FILE"
echo "🔐 base64 এনকোডেড কীস্টোর সেভ করা হয়েছে: $BASE64_FILE"
