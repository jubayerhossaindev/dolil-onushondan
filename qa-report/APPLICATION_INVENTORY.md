# Application Inventory — Dolil Onusondhan (দলিল অনুসন্ধান)

**Application ID:** `com.aistudio.dolilonusondhan.xkqmpz` (`namespace = "com.example"`)  
**Version:** `1.0` (`versionCode = 1`, `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36.1`)  
**Architecture:** Kotlin + Jetpack Compose (Material 3), Single-Activity (`MainActivity`), MVVM (`DolilViewModel`), Offline-First Room (`DolilDatabase`) + DataStore (`UserPreferencesDataStore`) + Firebase (`Auth`, `Firestore`, `App Check`).

---

## 1. Screens & Overlays (`PAGE-01` .. `PAGE-15`)

| ID        | Route / Identifier                      | Screen / Component                                                                                                 | File                                                                              | Role Scope                                   |
| --------- | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------- | -------------------------------------------- |
| `PAGE-01` | `splash`                                | `SplashScreen`                                                                                                     | `AuthAndSplashScreens.kt`                                                         | All (initial boot)                           |
| `PAGE-02` | `onboarding`                            | `OnboardingScreen` (4-page `HorizontalPager`)                                                                      | `OnboardingAndAccessScreens.kt`                                                   | Unauthenticated / Replay                     |
| `PAGE-03` | `access_selection`                      | `AccessSelectionScreen` (Google Sign-In / Guest / View Intro)                                                      | `OnboardingAndAccessScreens.kt`                                                   | Unauthenticated                              |
| `PAGE-04` | `home` (`DolilRoutes.HOME`)             | `DashboardScreen`                                                                                                  | `DashboardScreen.kt`                                                              | All Signed-In & Guest                        |
| `PAGE-05` | `search` (`DolilRoutes.SEARCH`)         | `SearchScreen` (+ `FilterBottomSheet` + `SortBottomSheet` + tablet `DeedDetailsPane`)                              | `SearchScreen.kt`                                                                 | All Signed-In & Guest                        |
| `PAGE-06` | `records` (`DolilRoutes.RECORDS`)       | `RecordsScreen` (Active/Drafts/Pending/Archive tabs + `SortBottomSheet` + tablet `DeedDetailsPane`)                | `RecordsAndDetailsScreens.kt`                                                     | All Signed-In & Guest                        |
| `PAGE-07` | `add_deed` (`DolilRoutes.ADD_DEED`)     | `DeedEntryScreen` (Create & Edit 5-Step Guided Wizard + DatePicker + Duplicate/Discard Dialogs)                    | `DeedEntryScreen.kt`                                                              | Authenticated & Guest                        |
| `PAGE-08` | `deed_details/{deedId}`                 | `DeedDetailsScreen` (Archival Parchment summary, Print/PDF export, Copy fields, Role actions, Audit Trail)         | `RecordsAndDetailsScreens.kt`                                                     | Authorized per `RolePermissions.canViewDeed` |
| `PAGE-09` | `persons` (`DolilRoutes.PERSONS`)       | `PersonsDirectoryScreen` (Grantors & Grantees searchable index)                                                    | `PersonsLandAndProfileScreens.kt`                                                 | All Signed-In & Guest                        |
| `PAGE-10` | `land` (`DolilRoutes.LAND`)             | `LandMouzaDirectoryScreen` (Saved Parcels tab + Narayanganj 99-Mouza Catalog tab)                                  | `PersonsLandAndProfileScreens.kt`                                                 | All Signed-In & Guest                        |
| `PAGE-11` | `profile` (`DolilRoutes.PROFILE`)       | `ProfileSettingsScreen` (Profile edit, Language, Theme, Dynamic Color, Sync, Admin Role Panel, Policies, Sign Out) | `PersonsLandAndProfileScreens.kt`                                                 | All Signed-In & Guest                        |
| `PAGE-12` | `user_guide` (`DolilRoutes.USER_GUIDE`) | `UserGuideScreen` (5 expandable FAQ accordions + Replay Tour CTA)                                                  | `UserGuideScreen.kt`                                                              | All Signed-In & Guest                        |
| `PAGE-13` | Overlay                                 | `ProductTourOverlay` (6-step spotlight & tooltip walkthrough)                                                      | `ProductTourOverlay.kt`                                                           | First launch / Replay Tour                   |
| `PAGE-14` | Modal Dialog                            | `ConflictResolutionDialog` (Keep Local / Accept Server / Cancel)                                                   | `AppNavigation.kt`                                                                | Signed-In Sync Conflicts                     |
| `PAGE-15` | System Dialogs                          | `DolilConfirmationDialog`, Privacy Policy Dialog, About v1.0 Dialog                                                | `Components.kt`, `PersonsLandAndProfileScreens.kt`, `RecordsAndDetailsScreens.kt` | Contextual                                   |

---

## 2. User Roles (`ROLE-01` .. `ROLE-06`)

| Role ID   | Enum                            | Permissions Summary (`RolePermissions.kt`)                                                                                                |
| --------- | ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `ROLE-01` | `PUBLIC_USER` (+ Guest Sandbox) | View `PUBLIC` + `APPROVED` deeds and own `DRAFT`s; create/edit/archive/restore own `PRIVATE_DRAFT`s; office stats & Pending queue hidden. |
| `ROLE-02` | `OFFICE_VIEWER`                 | View all records within assigned `officeId`; read-only for office records; can manage own `PRIVATE_DRAFT`.                                |
| `ROLE-03` | `OFFICE_DATA_ENTRY`             | Create/edit `DRAFT` and `PENDING_REVIEW` deeds in own `officeId`; cannot `APPROVE` or edit `APPROVED` records.                            |
| `ROLE-04` | `OFFICE_SUPERVISOR`             | Approve `PENDING_REVIEW` deeds -> `APPROVED` (`PUBLIC`), edit, soft-delete, and restore deeds in own `officeId`.                          |
| `ROLE-05` | `ORGANIZATION_ADMIN`            | Full deed lifecycle + `adminUpdateUserRole` across assigned `organizationId`.                                                             |
| `ROLE-06` | `PLATFORM_ADMIN`                | Full global deed lifecycle + role/office management across all offices (bootstrapped via verified `jubayerhossaingalaxy@gmail.com`).      |

---

## 3. Data Models, Entities & Repositories (`DB-*`, `API-*`)

| ID       | Artifact                                                                  | Responsibility                                                                                                                                                              |
| -------- | ------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DB-01`  | `deeds` (`DeedEntity` in `DolilDatabase`)                                 | Offline-first SQLite Room table storing deed metadata, normalized search tokens, JSON-serialized parties & parcels (`Moshi`), `rowVersion`, `syncState`.                    |
| `DB-02`  | `audit_logs` (`AuditLogEntity`)                                           | Local immutable audit entries per deed mutation (`CREATE`, `UPDATE`, `SUBMIT_DRAFT`, `APPROVE`, `SOFT_DELETE`, `RESTORE`).                                                  |
| `DB-03`  | `pending_sync` (`PendingSyncEntity`)                                      | Persistent upload queue tracking retry count, last error, and conflict preservation (`cacheRemoteDeeds` never overwrites pending IDs).                                      |
| `DB-04`  | `dolil_guest_sandbox.db`                                                  | Isolated Room DB instance seeded by `GuestSandboxSeeder` (`5` realistic Narayanganj demo deeds, never touching signed-in user DB).                                          |
| `DB-05`  | `dolil_user_preferences` (`UserPreferencesDataStore`)                     | Jetpack Preferences DataStore persisting `language`, `themeMode`, `useDynamicColor`, `lastSyncMillis`, `hasCompletedOnboarding`, `isGuestModeActive`, tour flags.           |
| `DB-06`  | `narayanganj_locations.json` (`BangladeshLocationsCatalog`)               | Bundled asset JSON containing `1` district, `9` subdivisions (`5` upazilas + `4` revenue circles), `4` survey tags, and `99` deduplicated mouzas with JL numbers.           |
| `API-01` | `FirestoreRepository` (`/users/{uid}`, `/deeds/{id}`, `/audit_logs/{id}`) | Optimistic lock transactions (`rowVersion == expected + 1`), batch audit creation, query alignment for rules, user preference & admin role updates.                         |
| `API-02` | `AuthManager`                                                             | Google Credential Manager (`GetGoogleIdOption`, `GetSignInWithGoogleOption`) -> `FirebaseAuth.signInWithCredential` -> `clearCredentialState` + `signOut`.                  |
| `API-03` | `AppCheckConfig`                                                          | Conditional App Check (`DebugAppCheckProviderFactory` in debug / `PlayIntegrityAppCheckProviderFactory` placeholder/configured with reCAPTCHA BOM in release/dependencies). |
| `API-04` | `DeedPdfRenderer` & `DeedPrintAdapter`                                    | Native Android `PdfDocument` paginated A4 PDF generator (`PrintManager` spooler & SAF `CreateDocument` export).                                                             |
