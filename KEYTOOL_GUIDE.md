# Android signing key (keytool) ব্যবহার গাইড

এই গাইডে ব্যাখ্যা করা হয়েছে কিভাবে Android 앱 সাইনিং কি কীস্টোর তৈরি করতে `keytool` ব্যবহার করুন।

## ১. keytool কী?

`keytool` হলো Java Development Kit (JDK)-এর সাথে বিল্ট-ইন একটি কমান্ড লাইন টুল। এর মাধ্যমে আপনি প্রাইভেট কি, পাবলিক সার্টিফিকেট এবং কীস্টোর তৈরি ও পরিচালনা করতে পারেন।

Android রিলিজ বিল্ডের জন্য একটি সাইনিং কীস্টোর প্রয়োজন।

---

## ২. কীস্টোর তৈরি করার কমান্ড

নিচের কমান্ডটি রান করুন:

```bash
keytool -genkeypair \
  -v \
  -keystore dolilonushodan-release.jks \
  -alias dolilonushodan \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keypass <আপনার_কি_পাসওয়ার্ড> \
  -storepass <আপনার_কীস্টোর_পাসওয়ার্ড> \
  -dname "CN=Dolil Onushodan, OU=Android, O=Dolil Onushodan, L=Dhaka, S=Dhaka, C=BD"
```

### প্যারামিটার ব্যাখ্যা:

| প্যারামিটার    | ব্যাখ্যা                                       |
| ------------- | -------------------------------------------- |
| `-genkeypair` | নতুন কি জেনারেট করে                           |
| `-v`          | ভার্বোজ আউটপুট                                 |
| `-keystore`   | কীস্টোর ফাইলের নাম                            |
| `-alias`      | কি অ্যালিয়াস (নাম)                            |
| `-keyalg`     | অ্যালগরিদম (RSA / EC)                         |
| `-keysize`    | কি সাইজ বিটে                                 |
| `-validity`   | কিটির স truthiness দিনে                      |
| `-keypass`    | প্রাইভেট কির পাসওয়ার্ড                         |
| `-storepass`  | কীস্টোর ফাইলের পাসওয়ার্ড                       |
| `-dname`      | ডিসটিন saiuশ নেম (অ ব্যাক্তিগত/ সংগঠনিক বিবরণ) |

---

## ৩. কীস্টোর ফাইল বackup নিতে হবে

কীস্টোর ফাইলটি (`dolilonushodan-release.jks`) খুব গুরুত্বপূর্ণ। একবার হারালে আপনি আর গুগল প্লে স্টোরে 앱 আপডেট পusher করতে পারবেন না। তাই এটি নিরাপদ জায়গায় সংরক্ষণ করুন।

গোপন পাসওয়ার্ড গুলো গিট রেপোতে কমিট করবেন না। GitHub Secrets ব্যবহার করুন।

---

## ৪. কীস্টোর তালিকা দেখতে

কীস্টোরের কন্টেন্ট চেক করতে:

```bash
keytool -list -v -keystore dolilonushodan-release.jks
```

নominated পাসওয়ার্ড দিলে কি অ্যালিয়াস, সার্টিফিকেটের ফিঙ্গারপ্রিন্ট (MD5, SHA1, SHA256) প্রিন্ট করবে।

---

## ৫. Android App সাইনিং গ্রেডেলে কনফিগার করুন

`app/build.gradle.kts` (অথবা `build.gradle`) ফাইলে নিচের ব্লক যোগ করুন:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("dolilonushodan-release.jks")
            storePassword = System.getenv("STORE_PASSWORD") ?: ""
            keyAlias = "dolilonushodan"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

গোপন ভ্যালুগুলো এনভায়রনমেন্ট ভেরিয়েবল থেকে নেওয়া উচিত (CI/CD এর জন্য GitHub Secrets ব্যবহার করুন)।

---

## ৬. CI/CD এ secrets সেট আপ করুন

GitHub রেপো সেটিংস থেকে **Settings → Secrets and variables → Actions** পেজে নিচের secretoগুলো যোগ করুন:

| Secret নাম        | মান                                |
| ----------------- | ---------------------------------- |
| `KEYSTORE_BASE64` | কীস্টোর ফাইলটিকে base64 এ এনকোড করে |
| `STORE_PASSWORD`  | কীস্টোর পাসওয়ার্ড                    |
| `KEY_PASSWORD`    | প্রাইভেট কির পাসওয়ার্ড               |

কীস্টোর ফাইল base64 এ এনকোড করতে:

```bash
base64 -w0 dolilonushodan-release.jks
```

রিলিজ ওয়ার্কফ্লোতে এটি ডিকোড করে ব্যবহার করা হয়:

```yaml
- name: Decode Keystore
  run: |
    echo "$KEYSTORE_BASE64" | base64 --decode > dolilonushodan-release.jks
```

---

## ৭. কীস্টোর ডিলিট / রিমুভ করতে

পুরনো কীস্টোর রিমুভ করতে:

```bash
rm dolilonushodan-release.jks
```

নতুন তৈরি করতে Setup গাইডের ২ নম্বর ধাপ ফলো করুন।

---

## ৮. সতর্কতা (Best Practices)

1. কীস্টোর ফাইলটি গিটে কমিট করবেন না (`*.jks` ফাইল `.gitignore`-এ রাখুন)।
2. পাসওয়ার্ড প্লেইন টেক্সটে সংরক্ষণ করবেন না।
3. কীস্টোর ব্যাকাপ একাধিক নিরাপদ জায়গায় রাখুন।
4. রিলিজ বিল্ডের জন্য সব সময় একই কীסטোর ব্যবহার করুন (অন্যথায় প্লে স্টোর আপডেট নাকবেনা)।

---

## ৯. উদাহরণ: মোট সিকোয়েন্স

```bash
# ১. কীস্টোর তৈরি
keytool -genkeypair \
  -v \
  -keystore dolilonushodan-release.jks \
  -alias dolilonushodan \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keypass <keypass> \
  -storepass <storepass> \
  -dname "CN=Dolil Onushodan, OU=Android, O=Dolil Onushodan, L=Dhaka, S=Dhaka, C=BD"

# ২. কীস্টোর তালিকা চেক
keytool -list -v -keystore dolilonushodan-release.jks

# ৩. CI এর জন্য base64 এনকোড
base64 -w0 dolilonushodan-release.jks
```

এই গাইড ফলো করে আপনি নিরাপদে Android রিলিজ সাইনিং কি তৈরি এবং ব্যবহার করতে পারবেন।
