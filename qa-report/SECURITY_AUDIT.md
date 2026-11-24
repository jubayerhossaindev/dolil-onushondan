# Security & Google Play Policy Audit — Dolil Onusondhan

## 1. Authentication & Session Security

- **Google Sign-In Only (No Anonymous Auth):** `AuthManager` uses Jetpack `CredentialManager` (`GetGoogleIdOption` / `GetSignInWithGoogleOption`) exchanged via `Firebase.auth.signInWithCredential`. Anonymous login is absent and disabled by design.
- **Clean Sign-Out:** `AuthManager.signOut` runs inside `NonCancellable` context, clears Credential Manager state, signs out `FirebaseAuth`, and relies on `authStateFlow()` to immediately tear down signed-in ViewModels (`AppNavigation.kt` keys `DolilViewModel` by `uid` / `"guest_sandbox"`).
- **Guest Sandbox Isolation:** Guest mode uses a separate Room database file (`dolil_guest_sandbox.db`), sets `remote = null` and `enableCloudSync = false`, and blocks cloud sync / profile writes cleanly.

## 2. Authorization & Data Access Control (RBAC + Zero-Trust Rules)

- **Client UI & ViewModel Enforcement:** `RolePermissions.kt` restricts:
  - Office stats (`canViewOfficeStats`), Pending queue (`canApproveDeed`), Role assignment (`canManageRoles`), Record viewing (`canViewDeed`), Editing (`canEditDeed`), Soft delete (`canSoftDeleteDeed`), and Restore (`canRestoreDeed`).
  - Suspended user profiles (`status != "ACTIVE"`) are denied all operations (`QaWorkflowAndEdgeCaseRobolectricTest` verified).
- **Server-Side Firestore Rules Inspection (`firestore.rules`):**
  - Default-deny root catch-all (`allow read, write: if false`).
  - No `get()`/`exists()` inside `allow list` queries — `FirestoreRepository` uses 3 rules-aligned queries (`PUBLIC`+`APPROVED`, `createdBy==uid`, `officeViewerUids array-contains uid`).
  - Users cannot escalate `role`, `status`, `officeId`, or `organizationId` during creation or self-update; only `PLATFORM_ADMIN` / `ORGANIZATION_ADMIN` (or verified bootstrapped admin email) can update roles.
  - Hard deletion (`allow delete: if false`) is prohibited on `users`, `deeds`, and `audit_logs`; optimistic locking checks `incoming().rowVersion == existing().rowVersion + 1`.
  - **Deployment Status (`BLK-02`):** `DeployRules` RPC failed due to caller permission in the container, and `firestore.test.js` requires offline npm install (`@firebase/rules-unit-testing`) + Firestore emulator runtime. Deploy `firestore.rules` via an authorized Firebase CLI before go-live.

## 3. Secrets, Local Storage, Input Safety & Play Policy

- **No Hardcoded Secrets:** `.env.example` keeps secret keys parameterized via Secrets Gradle Plugin; `google-services.json` and `firebase_applet_config.xml` hold standard public Firebase client configuration only. `FIREBASE_APPCHECK_DEBUG_TOKEN` is ignored cleanly in `secrets` block and debug-gated via `BuildConfig.DEBUG`.
- **Android Auto Backup Hardening (`BUG-005` Fixed):** Configured `backup_rules.xml` and `data_extraction_rules.xml` to exclude `dolil_onusondhan.db` (`-wal`/`-shm`), `dolil_guest_sandbox.db` (`-wal`/`-shm`), and `dolil_user_preferences.preferences_pb` from unencrypted OS cloud backups and device transfers.
- **Parameterized Queries & Input Safety:** Room DAO uses typed Room `@Query` bindings (`:normalizedQuery`, etc.) — no raw string-concatenated SQL. Injection-like literals (`' OR '1'='1`, `<script>alert(1)</script>`) are handled safely as plain strings (`QaWorkflowAndEdgeCaseRobolectricTest` verified).
- **Google Play Policy Compliance:**
  - `AndroidManifest.xml` requests **only** normal install-time permissions (`INTERNET`, `ACCESS_NETWORK_STATE`). Zero broad storage, camera, location, or media permissions requested.
  - PDF export uses Android `PrintManager` / Storage Access Framework cleanly without requesting storage permissions.
  - App title (`Dolil Onusondhan`) is 16 characters (<= 30 char limit), free of emojis or promotional buzzwords.
  - `dependenciesInfo { includeInApk = false; includeInBundle = true }` complies cleanly with modern Play signing/dependency metadata guidance.
