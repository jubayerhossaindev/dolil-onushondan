# Google OAuth 2.0 & Google Drive API Setup Guide

Dolil Onusondhan uses **two distinct OAuth identity flows** on Android, with **no embedded OAuth Client Secret** in the APK or repository.

---

## 1. Architecture Overview

| Flow                           | Purpose                                  | SDK / API                                                                       | Credential Configuration                                                                                                      |
| ------------------------------ | ---------------------------------------- | ------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| **Firebase Google Sign-In**    | User authentication (`FirebaseUser.uid`) | Jetpack `CredentialManager` (`GetSignInWithGoogleOption` / `GetGoogleIdOption`) | Android OAuth Client (Package + SHA-1) + Web OAuth Client (`default_web_client_id` generated from `app/google-services.json`) |
| **Personal Google Drive Sync** | Personal visible backup folder access    | Google Play Services `AuthorizationClient` + Drive REST v3 + OIDC `userinfo`    | Native Android OAuth Client (`com.aistudio.dolilonusondhan.xkqmpz` + SHA-1). **Never embed Client Secret.**                   |

Important security rule implemented in code:

- The Firebase Google ID token is **never** reused as a Google Drive access token.
- The connected Google Drive account (`sub` via `https://openidconnect.googleapis.com/v1/userinfo`) is stored independently from Firebase Auth identity so users can pick any personal Google Drive account.

---

## 2. Enable Google Drive API

1. Open [Google Cloud Console](https://console.cloud.google.com/) and select the project linked to your Firebase project.
2. Go to **APIs & Services → Library**.
3. Search for **Google Drive API** and click **Enable**.

---

## 3. Configure OAuth Consent Screen

1. Go to **APIs & Services → OAuth consent screen**.
2. Choose **External** user type (for public distribution on Google Play).
3. Fill in required app metadata:
   - **App name:** `Dolil Onusondhan`
   - **User support email:** Developer support email
   - **Developer contact information:** Developer email
4. Under **Data Access / Scopes**, add these exact scopes requested by `PlayServicesDriveAuthGateway`:
   - `https://www.googleapis.com/auth/drive.file` _(non-sensitive per-file / app-created folder access)_
   - `openid`
   - `profile`
   - `email`
5. While publishing status is **Testing**, add all QA/developer Google accounts under **Test users**.
6. Before switching to **In production** on Google Play, submit Google OAuth Consent verification (privacy policy URL, branding, and scope justification video showing how `drive.file` manages `My Drive/Dolil Onusondhan Backup/`).

---

## 4. Create / Verify OAuth 2.0 Clients

Go to **APIs & Services → Credentials**:

1. **Android OAuth 2.0 Client ID (Debug):**
   - **Package name:** `com.aistudio.dolilonusondhan.xkqmpz`
   - **SHA-1 certificate fingerprint:** Get from `debug.keystore`:

     ```bash
     keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

2. **Android OAuth 2.0 Client ID (Release / Play App Signing):**
   - **Package name:** `com.aistudio.dolilonusondhan.xkqmpz`
   - **SHA-1 certificate fingerprint:** Get both from your upload keystore and from **Google Play Console → Setup → App integrity → App signing key certificate**.
3. **Web Application OAuth 2.0 Client ID:**
   - Created automatically by Firebase when **Google** sign-in is enabled in Firebase Authentication.
   - Its Client ID appears inside `app/google-services.json` under `oauth_client` (`client_type: 3`) and becomes `R.string.default_web_client_id` via the `com.google.gms.google-services` Gradle plugin.

---

## 5. How the App Handles Consent, Expiration, and Revocation

- **Interactive connect:** Uses `AuthorizationClient.authorize(...)`; if consent is needed, launches `PendingIntent` via Activity `StartIntentSenderForResult`.
- **Background WorkManager (`DriveSyncWorker`):** Never tries to launch Activity UI. If `AuthorizationClient` returns `hasResolution()` or an expired/revoked error, the worker records `DriveSyncStatus.AUTHORIZATION_REQUIRED` and pauses background sync until the user taps **Re-authorize** in Settings.
- **Disconnect vs. Revoke:**
  - _Disconnect Google Drive_ clears only local app linkage state (`DeviceIdentityStore`) and cancels scheduled workers while keeping remote Drive files intact.
  - _Revoke Google Access_ clears cached Play Services token via `AuthorizationClient.clearToken(...)` / revokes Drive grant and disconnects locally.
