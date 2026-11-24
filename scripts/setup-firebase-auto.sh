#!/bin/bash

echo "🔥 Firebase অটো কনফিগারেশন শুরু..."

# Determine project root - script is in dolil-onushondan/scripts/
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"  # /workspaces/codespaces-blank/dolil-onushondan

echo "📁 প্রজেক্ট ডিরেক্টরি: $PROJECT_DIR"

# ১. Firebase CLI ইনস্টল চেক
if ! command -v firebase &> /dev/null; then
  echo "📦 Firebase CLI ইনস্টল করা হচ্ছে..."
  curl -sL https://firebase.tools | bash || echo "⚠️  Firebase CLI ইনস্টল করতে সমস্যা হয়েছে, ম্যানুয়ালি ইনস্টল করুন।"
else
  echo "✅ Firebase CLI ইতিমধ্যে ইনস্টল করা আছে।"
fi

# ২. Firebase লগিন
echo "🔐 Firebase লগিন করা হচ্ছে..."
if ! firebase projects:list &> /dev/null; then
  echo "Firebase লগিন টোকেন চেক..."
  if [ -n "$FIREBASE_TOKEN" ]; then
    echo "FIREBASE_TOKEN ব্যবহার করে লগিন..."
    echo "n" | firebase login:ci --token "$FIREBASE_TOKEN" > /dev/null 2>&1 || echo "⚠️  FIREBASE_TOKEN আমলু attesté করুননি।"
  else
    echo "⚠️  Firebase লগিন নেই। ম্যানুয়ালি লগিন করুন: firebase login:ci"
    echo "   অথবা FIREBASE_TOKEN এনভায়রনমেন্ট ভেরিয়েবল সেট করুন।"
  fi
else
  echo "✅ Firebase লগিন করা আছে।"
fi

# ৩. ফায়ারবেস প্রজেক্ট সিলেক্ট বা নতুন তৈরি
PROJECT_ID="dolil-onushondan"

echo "📁 ফায়ারবেস প্রজেক্ট চেক করা হচ্ছে..."
if firebase projects:list --json 2>/dev/null | grep -q "$PROJECT_ID"; then
  echo "✅ প্রজেক্ট '$PROJECT_ID' পাওয়া গেছে।"
else
  echo "🆕 নতুন ফায়ারবেস প্রজেক্ট তৈরি করা হচ্ছে: $PROJECT_ID"
  firebase projects:create "$PROJECT_ID" --display-name "$PROJECT_ID" 2>/dev/null || echo "⚠️  প্রজেক্ট তৈরি করতে সমস্যা হয়েছে, ম্যানুয়ালি করুন।"
fi

# .firebaserc ফাইল তৈরি (প্রজেক্ট রুটে)
cat > "$PROJECT_DIR/.firebaserc" << EOF
{ "projects": { "default": "$PROJECT_ID" } }
EOF

# ৪. Android আপ রেজিস্টার (package name: com.dolil.onushodan)
APP_NAME="dolil-onushondan-android"
ANDROID_PACKAGE="com.dolil.onushodan"
echo "📱 Android আপ রেজিস্টার করা হচ্ছে..."

if firebase apps:list --project "$PROJECT_ID" --json 2>/dev/null | grep -q "$ANDROID_PACKAGE"; then
  echo "✅ Android আপ ($ANDROID_PACKAGE) ইতিমধ্যে রেজিস্টার করা আছে।"
else
  echo "🆕 Android আপ তৈরি করা হচ্ছে: $ANDROID_PACKAGE..."
  firebase apps:create android "$APP_NAME" --package-name "$ANDROID_PACKAGE" --project "$PROJECT_ID" 2>/dev/null || {
    echo "⚠️  আপ অটো সৃষ্টি করতে পারলাম না। Firebase কনসোলে ম্যানুয়ালি আপ রেজিস্টার করুন।"
    echo "   প্যাকেজ নাম: $ANDROID_PACKAGE"
    echo "   প্রজেক্ট: $PROJECT_ID"
  }
fi

# ৫. google-services.json ডাউনলোড
echo "📥 google-services.json ডাউনলোড করা হচ্ছে..."

APP_ID=$(firebase apps:list --project "$PROJECT_ID" --json 2>/dev/null | grep -o '"appId":"[^"]*"' | head -1 | cut -d'"' -f4 || true)

if [ -n "$APP_ID" ]; then
  mkdir -p "$PROJECT_DIR/app"
  firebase apps:sdkconfig android "$APP_ID" --project "$PROJECT_ID" --out "$PROJECT_DIR/app/google-services.json" 2>/dev/null || {
    echo "⚠️  google-services.json অটো ডাউনলোড করুননি। Firebase কনসোলে ম্যানুয়ালি ডাউনলোড করে রাখুন:"
    echo "   $PROJECT_DIR/app/google-services.json"
  }
else
  echo "⚠️  Android আপ আইডি পাওয়া যায়নি। Firebase কনসোলে ম্যানুয়ালি google-services.json ডাউনলোড করে রাখুন:"
  echo "   $PROJECT_DIR/app/google-services.json"
fi

if [ -f "$PROJECT_DIR/app/google-services.json" ]; then
  echo "✅ google-services.json পথ: $PROJECT_DIR/app/google-services.json"
else
  echo "⚠️  google-services.json ফাইল পাওয়া যায়নি। ম্যানুয়ালি যোগ করতে হবে।"
fi

# ৬. Project-level build.gradle.kts কনফিগার
PROJECT_BUILD="$PROJECT_DIR/build.gradle.kts"
echo "📝 Project-level build.gradle.kts কনফিগার করা হচ্ছে..."
if [ -f "$PROJECT_BUILD" ]; then
  if ! grep -q "com.google.gms.google-services" "$PROJECT_BUILD"; then
    sed -i '/dependencies {/a\        classpath("com.google.gms.google-services:google-services-plugin:4.4.2")' "$PROJECT_BUILD"
    echo "✅ google-services ক্লাসপ্লাগ যোগ করা হলো।"
  else
    echo "✅ google-services ক্লাসপ্লাগ ইতিমধ্যে আছে।"
  fi
else
  echo "⚠️  Project-level build.gradle.kts পাওয়া যায়নি: $PROJECT_BUILD"
fi

# ৭. App-level build.gradle.kts কনফিগার
APP_BUILD="$PROJECT_DIR/app/build.gradle.kts"
echo "📝 App-level build.gradle.kts কনফিগার করা হচ্ছে..."
if [ -f "$APP_BUILD" ]; then
  if ! grep -q "google.services" "$APP_BUILD"; then
    echo "⚠️  App-level build.gradle-এ google.services প্লাগইন ম্যানুয়ালি যোগ করতে হবে।"
  fi

  if ! grep -q "firebase-bom" "$APP_BUILD"; then
    echo "⚠️  App-level build.gradle-ে Firebase BOM যোগ করতে হবে।"
  fi

  echo "✅ App-level build.gradle.kts চেক সম্পন্ন।"
else
  echo "⚠️  App-level build.gradle.kts পাওয়া যায়নি: $APP_BUILD"
fi

# ৮. GitHub Secrets সেটআপ (CI/CD এর জন্য)
if command -v gh &> /dev/null && gh auth status &> /dev/null; then
  echo "🔐 GitHub Secrets সেট করা হচ্ছে..."

  # FIREBASE_TOKEN সেট
  FIREBASE_CI_TOKEN=$(firebase login:ci 2>/dev/null | tail -1 || echo "")
  if [ -n "$FIREBASE_CI_TOKEN" ]; then
    gh secret set FIREBASE_TOKEN -b "$FIREBASE_CI_TOKEN" --repo "$(git remote get-url origin 2>/dev/null | sed 's/.*github.com[:/]\(.*\)\.git/\1/' || echo '')" 2>/dev/null || echo "⚠️  FIREBASE_TOKEN সেট করতে সমস্যা হয়েছে, ম্যানুয়ালি সেট করুন।"
  else
    echo "⚠️  FIREBASE_TOKEN জেনারেট করতে পারলাম না। ম্যানুয়ালি সেট করুন: firebase login:ci"
  fi

  # google-services.json base64 এনকোড করে সিক্রেটে সেট
  if [ -f "$PROJECT_DIR/app/google-services.json" ]; then
    FIREBASE_CONFIG_B64=$(base64 -w0 "$PROJECT_DIR/app/google-services.json")
    gh secret set FIREBASE_CONFIG_BASE64 -b "$FIREBASE_CONFIG_B64" --repo "$(git remote get-url origin 2>/dev/null | sed 's/.*github.com[:/]\(.*\)\.git/\1/' || echo '')" 2>/dev/null || echo "⚠️  FIREBASE_CONFIG_BASE64 সেট করতে সমস্যা হয়েছে, ম্যানুয়ালি সেট করুন।"
    echo "✅ FIREBASE_CONFIG_BASE64 GitHub সিক্রেটে সেভ করা হলো।"
  else
    echo "⚠️ google-services.json পাওয়া যায়নি। FIREBASE_CONFIG_BASE64 সেট করার আগে ফাইলটি যোগ করুন।"
  fi
else
  echo "⚠️  GitHub CLI (`gh`) ইনস্টল বা লগিন করা নেই। সিক্রেটগুলো ম্যানুয়ালি সেট করুন।"
fi

echo ""
echo "🎉 Firebase অটো কনফিগারেশন সম্পন্ন!"
echo ""
echo "📋 পরবর্তী ধাপসমূহ:"
echo "   ১. App-level build.gradle.kts-এ google.services প্লাগইন apply করুন (plugins ব্লকে): alias(libs.plugins.google.services)"
echo "   ২. Firebase BOM ও প্রয়োজনীয় ডিপেন্ডেন্সি যোগ করুন (implementation(platform(libs.firebase.bom)))"
echo "   ৩. GitHub রেপো সেটিংস থেকে FIREBASE_TOKEN ও FIREBASE_CONFIG_BASE64 সিক্রেট যোগ করুন"
echo "   ৪. কোড গিটহাবে পুশ করুন: git push origin main"
