#!/bin/bash

set -e

KEY_DIR="/workspaces/codespaces-blank/signing-keys"
BASE64_FILE="$KEY_DIR/keystore-base64.txt"

if [ ! -f "$BASE64_FILE" ]; then
  echo "❌ $BASE64_FILE ফাইল পাওয়া যায়নি। önce generate-signing-key.sh রান করুন।"
  exit 1
fi

echo "🔐 GitHub Secrets সেটআপ শুরু..."

# KEYSTORE_BASE64 - কীস্টোর বেস64 কন্টেন্ট ফাইল থেকে নেওয়া হবে
echo "📥 KEYSTORE_BASE64 সেট করা হচ্ছে..."
gh secret set KEYSTORE_BASE64 < "$BASE64_FILE"

# STORE_PASSWORD
echo "📥 STORE_PASSWORD সেট করা হচ্ছে..."
gh secret set STORE_PASSWORD -b "Jubayer321###"

# KEY_PASSWORD
echo "📥 KEY_PASSWORD সেট করা হচ্ছে..."
gh secret set KEY_PASSWORD -b "Jubayer321###"

echo "✅ সব GitHub Secret সফলভাবে সেট করা সম্পন্ন।"
