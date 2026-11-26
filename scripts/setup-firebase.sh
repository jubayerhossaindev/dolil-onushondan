#!/bin/bash

set -e

echo "🔥 Firebase সেটআপ শুরু..."

# ১. Firebase CLI ইনস্টল চেক
if ! command -v firebase &> /dev/null; then
  echo "📦 Firebase CLI ইনস্টল করা হচ্ছে..."
  curl -sL https://firebase.tools | bash
else
  echo "✅ Firebase CLI ইতিমধ্যে ইনস্টল করা আছে।"
fi

# ২. Firebase লগিন চেক
echo "🔐 Firebase লগিন অবস্থা চেক করা হচ্ছে..."
if ! firebase projects:list &> /dev/null; then
  echo "🌐 Firebase লগিন করুন..."
  firebase login
else
  echo "✅ Firebase লগিন করা আছে।"
fi

# ৩. ফায়ারবেস প্রজেক্ট সিলেক্ট বা তৈরি
echo "📁 ফায়ারবেস প্রজেক্ট সিলেক্ট করুন:"
firebase projects:list

echo ""
echo "প্রজেক্ট আইডি লিখুন (অথবা নতুন তৈরি করতে 'new' লিখুন):"
read -r PROJECT_ID

if [ "$PROJECT_ID" = "new" ]; then
  echo "নতুন প্রজেক্ট নাম লিখুন:"
  read -r NEW_PROJECT_NAME
  firebase projects:create "$NEW_PROJECT_NAME"
  PROJECT_ID="$NEW_PROJECT_NAME"
fi

firebase use "$PROJECT_ID"

# ৪. Android আপ রেজিস্টার (gailbangla - app package name)
echo "📱 Android আপ রেজিস্টার করা হচ্ছে..."
firebase apps:create android "$PROJECT_ID" dolil-onushodan

# ৫. google-services.json ডাউনলোড
echo "📥 google-services.json ডাউনলোড করা হচ্ছে..."
mkdir -p dolil-onushondan/app
firebase apps:sdkconfig android "$PROJECT_ID" dolil-onushodan > dolil-onushondan/app/google-services.json

echo "✅ google-services.json সেভ করা হয়েছে: dolil-onushondan/app/google-services.json"

# ৬. Firebase.gradle কনফিগারেশন নোট
echo ""
echo "📝 নিম্নলিখিত ধাপগুলো ম্যানুয়ালি সম্পন্ন করুন:"
echo "   ১. Project-level build.gradle-এ Firebase plugin যোগ করুন"
echo "   ২. App-level build.gradle-ে google-services প্রোগ্রামের প্রয়োগ করুন"
echo "   ৩. google-services.json ফাইলটি app ফোল্ডারে আছে কিনা নিশ্চিত করুন"

echo ""
echo "🎉 Firebase সেটআপ সম্পন্ন।"
