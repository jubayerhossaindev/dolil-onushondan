# Pre-Testing & Pre-Release Configuration Checklist

## A. Local Build & Automated Verification

- [x] `AppVersionInfo` uses `BuildConfig.VERSION_NAME` (`1.0.0` -> `v1.0.0`) rendered exclusively in **Settings → Application Information**.
- [x] Per-deed **Audit Trail** preserved in `DeedDetailsScreen`; no App Changelog screen or section.
- [x] Developer Identity card present in Settings Section 6 (`JH`, `Jewel Hossain` / `জুয়েল হোসাইন`, `App Developer` / `অ্যাপ নির্মাতা`, tagline only; zero fake links/contacts/photos).
- [x] All Cloud Firestore code & dependency removed; Firebase used strictly for Authentication.
- [x] Room `v1 -> v2` migration preserves legacy local deeds under `__legacy_local__` and isolates data by `ownerUid`.
- [x] `AuthorizationClient` (`drive.file`, `openid`, `profile`, `email`) + OIDC `sub` check + `SHA-256("dolil-onsundondhan-backup-v1:" + uid)` namespace isolation + `PBKDF2WithHmacSHA256` (600,000 iterations) / `AES-256-GCM` implemented with zero embedded client secrets.

## B. External Console Provisioning (Required Before Live Device Testing)

- [ ] Create/open Firebase project and register Android app `com.aistudio.dolilonusondhan.xkqmpz`.
- [ ] Add Debug SHA-1 & SHA-256 certificate fingerprints to Firebase Project Settings.
- [ ] Enable **Email/Password** and **Google** providers in Firebase Authentication.
- [ ] Download real `app/google-services.json` (with Web OAuth `client_type: 3` -> `default_web_client_id`) and place in `app/google-services.json`.
- [ ] Enable **Google Drive API** in the linked Google Cloud Console project.
- [ ] Configure Google Cloud **OAuth Consent Screen** (`External`, scopes: `drive.file`, `openid`, `profile`, `email`) and add test users while in Testing mode.
- [ ] Verify Android OAuth 2.0 Client (`com.aistudio.dolilonusondhan.xkqmpz` + Debug SHA-1) exists in Google Cloud Credentials.

## C. Pre-Production Google Play Release Checklist

- [ ] Generate or configure production Upload Keystore and add Upload + Play App Signing SHA-1 & SHA-256 to both Firebase and Google Cloud Android OAuth Clients.
- [ ] Complete live device E2E test matrix (Email Register + Verify, Password Reset, Google Sign-In, Drive Connect, Backup/Restore, Encrypted Passphrase Roundtrip, Multi-device Conflict, Revoke Access, Account Deletion).
- [ ] Submit External OAuth Consent Screen (including `drive.file` justification & demo video + real Privacy Policy URL) for Google verification before launching to general public.
- [ ] Verify `Settings → Cloud Backup & Sync → Credential & OAuth Status` reports all live checks ready on device.
