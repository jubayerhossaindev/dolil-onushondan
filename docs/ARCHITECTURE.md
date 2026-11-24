# Architecture Overview — Dolil Onusondhan (দলিল অনুসন্ধান)

## 1. Layers & Package Structure

- `com.example.core.designsystem`: Material 3 Green color palette (`Color.kt`), local Bengali/English typography using `Noto Sans Bengali` & `Hind Siliguri` (`Type.kt`), `DolilTheme` (`Theme.kt`), bilingual strings (`Strings.kt`), and reusable M3 components (`Components.kt`).
- `com.example.core.security`:
  - `AppCheckConfig.kt`: Installs Firebase App Check (`DebugAppCheckProviderFactory` in debug with synchronous `.commit()` of `FIREBASE_APPCHECK_DEBUG_TOKEN` intent extra; `RecaptchaAppCheckProviderFactory` in release). Invoked first inside `MainActivity.onCreate()`.
  - `AuthManager.kt`: Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` + silent `GetGoogleIdOption`) and Firebase Auth session lifecycle (`authStateFlow`, `NonCancellable` sign-out).
  - `RolePermissions.kt`: 6-role RBAC authorization checks (`canViewDeed`, `canEditDeed`, `canSoftDeleteDeed`, `canRestoreDeed`, `canApproveDeed`, `canViewOfficeStats`, `canManageRoles`).
- `com.example.domain`:
  - `model/`: Immutable domain models (`DeedRecord`, `PartyInfo`, `LandParcel`, `UserProfile`, `AuditLogEntry`, `DeedConflict`, `OfficeStats`, `UiState`).
  - `search/`: `SearchNormalizer` (Unicode NFC + Bengali digit conversion + bounded search tokens), `DefaultIndexedSearchStrategy`, and `DeedSorter` (10 deterministic `SortOption`s + bounded pagination).
  - `validation/`: `DeedValidator` with `ValidationErrorCode`s, draft vs submit rules, `BigDecimal` area checks, and office/year/deed-number duplicate detection.
- `com.example.data`:
  - `local/`: `DolilDatabase` (`Room`, entities `deeds`, `audit_logs`, `pending_sync`), `BangladeshLocationsCatalog`, and `UserPreferencesDataStore`.
  - `remote/`: `FirestoreRepository` targeting the provisioned named Firestore database (`R.string.firestore_database_id`), DTOs (`FirestoreDtos.kt`), and `FirestoreErrorHandler.kt`.
  - `repository/`: `DolilSyncRepository` providing offline-first local writes, atomic audit + pending sync queueing, and optimistic concurrency conflict detection (`rowVersion`).
  - `ai/`: `DolilAiRepository` powered by Firebase AI Logic (`Firebase.ai`):
    - `gemini-4-carbon` with `ThinkingLevel.HIGH` (no `maxOutputTokens` cap) for complex land/area/registry reasoning.
    - `gemini-4-hydrogen` for streaming bilingual deed summaries.
    - `gemini-3.1-flash-lite` with `Schema.obj` structured JSON for fast Bangla/English search filter extraction.
- `com.example.ui`: `DolilViewModel`, `AppNavigation.kt` (adaptive `NavigationBar` on compact screens and `NavigationRail` on >=600dp tablets/foldables), and Compose screens under `ui/screens/`.

## 2. Version 1.0 Scope Boundaries

- Included: Bilingual UI (বাংলা + English), Google Sign-In, 6-role RBAC, sectioned Deed Entry (multi-grantor, multi-grantee, cascading Bangladesh locations, multi-parcel `BigDecimal` conversion), ranked search + 10 sort modes, offline Room drafts + optimistic sync conflict resolution, audit trail, and Gemini AI reasoning/summarization/smart-filter.
- Excluded from V1.0: Document/PDF/image uploads, OCR scanning, and Google Drive backup/restore.
