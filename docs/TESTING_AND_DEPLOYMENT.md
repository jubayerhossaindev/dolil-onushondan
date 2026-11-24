# Automated Testing & Deployment Guide

## 1. Automated Test Suite

1. **Firestore Security Rules (`firestore.test.js`)**:
   - Executed against the local Firebase Firestore Emulator (`FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 node --test firestore.test.js`).
   - Covers: unauthenticated denial, public role escalation denial, `PUBLIC_USER` office-restricted read denial, `OFFICE_SUPERVISOR` approval + soft delete + audit log creation, and audit log immutability.
2. **Domain Unit Tests (`app/src/test/java/com/example/`)**:
   - `SearchNormalizerTest`: Bengali digit conversion, Unicode NFC, slash collapsing, token capping, and ranked `MatchReason` precedence.
   - `DeedValidatorTest`: All 14 `ValidationErrorCode`s, draft vs submit validation, Bengali/ASCII duplicate deed detection, and `BigDecimal` unit conversions.
   - `DeedSorterAndRoleTest`: All 10 deterministic `SortOption`s with UUID tie-breaker, bounded pagination, and complete 6-role RBAC permission matrix.
3. **Room Database & Sync Repository Tests (`DolilDatabaseAndSyncTest`)**:
   - Runs via Robolectric against an in-memory `DolilDatabase`, testing atomic `saveDeedWithAuditAndSync`, indexed search, soft-delete/restore visibility, pending draft preservation during remote snapshot caching, and conflict resolution.
4. **Compose UI E2E Tests (`DolilEndToEndRobolectricTest`)**:
   - Runs via Robolectric `createComposeRule()`, testing role-gated Dashboard stats, Search & Sort sheet, Deed Entry validation & live `BigDecimal` area display, Deed Details lifecycle confirmation dialogs, and Profile Admin panel / Sign Out confirmation.

## 2. Environment & Release Signing Constraints

- **Container Environment**: No Android emulator or `adb` daemon runs inside the build container; all Android UI and Room tests are verified via Robolectric on the JVM (`gradle :app:testDebugUnitTest`).
- **Release APK / AAB Signing**: Debug builds use `debug.keystore`. Release builds (`assembleRelease` / `bundleRelease`) require external CI/CD environment variables `KEYSTORE_PATH` (or `my-upload-key.jks`), `STORE_PASSWORD`, and `KEY_PASSWORD` which are intentionally not embedded in the repository.
