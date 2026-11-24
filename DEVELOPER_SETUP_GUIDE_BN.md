# দলিল অনুসন্ধান (Dolil Onusondhan) — ডেভেলপার সেটআপ ও প্রোডাকশন গাইড (বাংলা)

**অ্যাপ্লিকেশন আইডি (Package Name):** `com.aistudio.dolilonusondhan.xkqmpz`  
**বর্তমান সংস্করণ (Settings → Application Information):** `v1.0.0` (`versionName = "1.0.0"`, `versionCode = 1`)  
**নির্মাতা (Developer Identity):** জুয়েল হোসাইন (`Jewel Hossain`)

---

## ১. আর্কিটেকচারের মূল নীতি

এই অ্যাপ্লিকেশনে **Firebase** ও **Google Drive**-এর দায়িত্ব সম্পূর্ণ আলাদা:

1. **Firebase (শুধুমাত্র Authentication):**
   - Email/Password রেজিস্ট্রেশন, লগইন, ইমেইল ভেরিফিকেশন, পাসওয়ার্ড রিসেট, Google Sign-In (`CredentialManager` + `default_web_client_id`), Re-authentication, Logout এবং Account Deletion।
   - **Firestore, Realtime Database বা Firebase Storage ব্যবহার সম্পূর্ণ নিষিদ্ধ এবং কোড থেকে অপসারিত।**
2. **Room Database (ডিভাইসের স্থানীয় এবং UID-আইসোলেটেড স্টোরেজ):**
   - প্রতিটি Firebase ব্যবহারকারীর (`ownerUid`) দলিল, অডিট লগ এবং সিঙ্ক কিউ আলাদাভাবে সংরক্ষিত থাকে।
   - Guest Mode-এর জন্য আলাদা স্যান্ডবক্স ডেটাবেস (`dolil_guest_sandbox.db`) ব্যবহৃত হয়।
   - `v1 -> v2` মাইগ্রেশনে পূর্বের সব স্থানীয় দলিল `__legacy_local__` হিসেবে সংরক্ষিত থাকে এবং লগইন করা ব্যবহারকারী চাইলে Settings থেকে সেগুলো ইম্পোর্ট করতে পারেন।
3. **ব্যক্তিগত Google Drive (ক্লাউড ব্যাকআপ ও মাল্টি-ডিভাইস সিঙ্ক):**
   - ব্যবহারকারীর নিজের অনুমোদিত Google Drive-এ দৃশ্যমান ফোল্ডারে (`My Drive/Dolil Onusondhan Backup/accounts/{accountNamespaceId}/`) সংরক্ষিত হয়।
   - `accountNamespaceId = SHA-256("dolil-onsundondhan-backup-v1:" + firebaseUid)`।
   - Google Play Services `AuthorizationClient`-এর মাধ্যমে **নেটিভ Android OAuth** ব্যবহৃত হয় — **APK বা সোর্স কোডে কোনো Google OAuth Client Secret রাখা হয়নি।**

---

## ২. Firebase Console সেটআপ (Authentication Only)

1. [Firebase Console](https://console.firebase.google.com/)-এ একটি নতুন বা বিদ্যমান প্রজেক্ট খুলুন।
2. **Project Settings → General → Add app → Android** নির্বাচন করুন:
   - **Android package name:** `com.aistudio.dolilonusondhan.xkqmpz`
   - **Debug SHA-1 & SHA-256** যুক্ত করুন (`keytool` দিয়ে বের করুন, নিচে কমান্ড দেখুন)।
   - রিলিজের জন্য Google Play Console App Signing SHA-1 ও SHA-256 যুক্ত করুন।
3. **Authentication → Sign-in method** থেকে সক্রিয় করুন:
   - **Email/Password** (সক্রিয় করুন; Email link ছাড়াই সাধারণ Email/Password)
   - **Google** (Support email দিয়ে সক্রিয় করুন — এর মাধ্যমেই `default_web_client_id` তৈরি হয়)
4. **Firestore / Realtime Database / Storage চালু করার প্রয়োজন নেই** (এবং এই অ্যাপে এগুলোতে কোনো ডেটা পাঠানো হয় না)।
5. `google-services.json` ডাউনলোড করে `app/google-services.json` স্থানে রাখুন।

SHA-1 ও SHA-256 বের করার কমান্ড:

```bash
keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
```

(প্রোডাকশন আপলোড কী-স্টোরের জন্য আপনার `.jks` ফাইল এবং Play Console → **App Integrity → App signing** থেকে অ্যাপ সাইনিং ফিঙ্গারপ্রিন্ট নিন।)

---

## ৩. Google Cloud Console ও OAuth 2.0 সেটআপ

একই Google Cloud প্রজেক্টে (যা Firebase প্রজেক্টের সাথে যুক্ত):

1. **Google Drive API সক্রিয় করুন:**
   - **APIs & Services → Library** → `Google Drive API` → **Enable**।
2. **OAuth Consent Screen কনফিগার করুন:**
   - **User Type:** `External` (পাবলিক ইউজারদের জন্য)।
   - অ্যাপের নাম: `Dolil Onusondhan` (`দলিল অনুসন্ধান`), Support Email, Developer Contact Email দিন।
   - **Scopes যুক্ত করুন:**
     - `https://www.googleapis.com/auth/drive.file`
     - `openid`
     - `profile`
     - `email`
   - পাবলিক রিলিজের আগে টেস্টিং অবস্থায় আপনার **Test Users** ইমেইলগুলো যুক্ত করুন; পাবলিক রিলিজের জন্য **OAuth App Verification** (ডেমো ভিডিও ও প্রাইভেসি পলিসি লিংকসহ) সম্পন্ন করুন।
3. **OAuth 2.0 Client IDs যাচাই করুন:**
   - **Web Application Client:** Firebase Google Sign-In-এর জন্য (`google-services.json`-এর `oauth_client` যেখানে `client_type: 3`, সেটিই `default_web_client_id` হিসেবে ব্যবহৃত হয়)।
   - **Android Client(s):** প্যাকেজ নাম `com.aistudio.dolilonusondhan.xkqmpz` এবং আপনার Debug ও Release/Play Signing SHA-1 ফিঙ্গারপ্রিন্টসহ তৈরি রাখুন।
   - **গুরুত্বপূর্ণ:** APK বা `.env`-এ কখনোই কোনো Client Secret যোগ করবেন না।

---

## ৪. এনক্রিপশন ও সিঙ্ক আচরণ (Settings → Cloud Backup & Sync)

- ব্যবহারকারী নিজের Google অ্যাকাউন্ট থেকে যেকোনো ব্যক্তিগত Drive অ্যাকাউন্ট বেছে নিতে পারবেন (এটি Firebase লগইন ইমেইল থেকে আলাদা হতে পারে)।
- **ঐচ্ছিক পাসফ্রেজ এনক্রিপশন:** `PBKDF2WithHmacSHA256` (৬,০০,০০০ ইটারেশন) + `AES-256-GCM` (প্রতি ব্যাকআপে আলাদা Salt, 12-byte IV ও AAD)। পাসফ্রেজ ভুলে গেলে রিকভারি সম্ভব নয়, তাই ব্যবহারকারীকে সতর্কবার্তা দেখানো হয়।
- **WorkManager অটো-সিঙ্ক:** ডিবাউন্সড ও ৩০ মিনিটের পিরিয়ডিক সিঙ্ক চলে। যদি নতুন করে ইউজার কনসেন্ট প্রয়োজন হয়, ব্যাকগ্রাউন্ড ওয়ার্কার সিঙ্ক থামিয়ে `Authorization Required` স্ট্যাটাস সেট করে (ব্যাকগ্রাউন্ড থেকে কোনো UI চালু করে না)।
- **Disconnect vs Revoke Google Access:**
  - _Disconnect:_ কেবল স্থানীয় ডিভাইস ও বর্তমান অ্যাপের সাথে Drive লিংক বিচ্ছিন্ন করে (রিমোট ফাইল রেখে দেয়)।
  - _Revoke Access:_ Google থেকে অনুমোদন প্রত্যাহার এবং ক্যাশড টোকেন মুছে ফেলে।

---

## ৫. Google Play প্রোডাকশন রিলিজের ধাপসমূহ

1. `app/google-services.json` এবং `res/values/strings.xml`-এ `recaptcha_site_key` (যদি Release App Check ব্যবহার করেন) যাচাই করুন।
2. `gradle :app:testDebugUnitTest` এবং ফিজিক্যাল ডিভাইসে (Play Services সহ) ম্যানুয়াল E2E টেস্ট (Google Sign-In, Email Register/Verify, Drive Backup/Restore, 3-Way Conflict, Account Delete) সম্পন্ন করুন।
3. Google Cloud Console-এ External OAuth Consent Screen ভেরিফিকেশন অনুমোদিত হলে Play Store-এ `bundleRelease` আপলোড করুন এবং Play App Signing SHA-1/SHA-256 Firebase ও Cloud Console-এ যুক্ত করুন।
