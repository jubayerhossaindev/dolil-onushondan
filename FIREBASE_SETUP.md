# Firebase Setup Guide (Authentication Only)

## Policy & Architectural Boundary

Dolil Onusondhan uses **Firebase strictly for Authentication and Auth lifecycle management**.

**Allowed Firebase functionality in this app:**

- Email/Password registration & sign-in
- Google Sign-In (`CredentialManager` + `FirebaseAuth` via `GoogleAuthProvider`)
- Password reset email (`sendPasswordResetEmail`)
- Email verification (`sendEmailVerification` & status reload)
- Session state (`FirebaseAuth.AuthStateListener`) & `FirebaseUser.uid`
- Re-authentication & account deletion
- Sign-out

**Prohibited & Removed:**

- **Cloud Firestore**, **Firebase Realtime Database**, and **Firebase Storage** are **not** used. `libs.firebase.firestore` is disabled in `app/build.gradle.kts`, and all Firestore repository/DTO classes have been removed.
- All user deed records live in UID-partitioned Room SQLite (`dolil_onusondhan.db`) and synchronize only to the user's independently authorized personal Google Drive (`drive.file`).

---

## Step-by-Step Firebase Console Provisioning

1. **Create or select a Firebase project** in [Firebase Console](https://console.firebase.google.com/).
2. **Register Android App:**
   - **Android package name:** `com.dolil.onushodan`
   - **App nickname:** `Dolil Onusondhan Android`
   - **Debug signing certificate SHA-1 & SHA-256:** Obtain via:

     ```bash
     keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

   - **Release signing SHA-1 & SHA-256:** Add upload key + Google Play App Signing fingerprints in **Project Settings → General → Your apps → Add fingerprint**.
3. **Enable Authentication Providers:**
   - Go to **Authentication → Sign-in method**:
     - Enable **Email/Password**.
     - Enable **Google** (select a project support email). Enabling Google automatically provisions the Web OAuth Client (`client_type: 3`) required for `default_web_client_id`.
4. **Download `google-services.json`:**
   - Download `google-services.json` from **Project Settings → General** and save it at:
     `app/google-services.json`
   - Verify the downloaded JSON contains:
     - `client_info.android_client_info.package_name == "com.dolil.onushodan"`
     - Android OAuth client (`client_type: 1`) matching your SHA-1
     - Web OAuth client (`client_type: 3`) so `default_web_client_id` is generated at build time
5. **App Check (Optional / Recommended for Auth protection):**
   - **Debug builds:** `AppCheckConfig.kt` installs `DebugAppCheckProviderFactory` automatically when `FirebaseApp` is initialized.
   - **Release builds:** Provide `recaptcha_site_key` in string resources if enabling reCAPTCHA App Check, or configure Play Integrity if adopting App Check enforcement in Firebase Console.

---

## Do Not Re-enable Unused Firebase Databases

Even if old files such as `firebase-blueprint.json`, `firestore.rules`, or `firebase_applet_config.xml` exist from the original template/repository history, `app/build.gradle.kts` explicitly omits `firebase-firestore`, `firebase-database`, and `firebase-storage`, and the app enforces a strict Room + Personal Google Drive architecture.
