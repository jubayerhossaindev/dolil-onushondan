# Security, Authentication & RBAC

## 1. Authentication Flow

- Uses **Google Sign-In via Jetpack Credential Manager** (`GetSignInWithGoogleOption` for interactive sign-in and `GetGoogleIdOption` for silent auto sign-in) paired with `Firebase.auth.signInWithCredential(...)`.
- Anonymous authentication (`signInAnonymously()`) and email/password sign-in are disabled and unused.
- Sign-out runs inside `withContext(NonCancellable)` clearing Credential Manager state and signing out of Firebase Auth; `key(user.uid)` cleanly tears down user-scoped `DolilViewModel` instances on account switches.

## 2. Firebase App Check

- `configureAppCheck(this, intent)` executes at the very top of `MainActivity.onCreate()` before `enableEdgeToEdge()`, `setContent`, or any Firebase SDK calls.
- Debug builds install `DebugAppCheckProviderFactory` (synchronously committing `FIREBASE_APPCHECK_DEBUG_TOKEN` from launch intent if present).
- Release builds install `RecaptchaAppCheckProviderFactory` with `R.string.recaptcha_site_key`.

## 3. Six-Role RBAC Matrix (`RolePermissions.kt` & `firestore.rules`)

| Role                 | View Public Approved | View Office Records | View Office Stats | Create Draft | Submit/Edit Office Pending | Approve / Soft Delete / Restore | Manage Roles |
| :------------------- | :------------------: | :-----------------: | :---------------: | :----------: | :------------------------: | :-----------------------------: | :----------: |
| `PUBLIC_USER`        |         Yes          |   Own drafts only   |      **No**       |     Yes      |             No             |         Own drafts only         |      No      |
| `OFFICE_VIEWER`      |         Yes          |     Own office      |        Yes        |     Yes      |             No             |               No                |      No      |
| `OFFICE_DATA_ENTRY`  |         Yes          |     Own office      |        Yes        |     Yes      | Own office (non-approved)  |         Own drafts only         |      No      |
| `OFFICE_SUPERVISOR`  |         Yes          |     Own office      |        Yes        |     Yes      |         Own office         |           Own office            |      No      |
| `ORGANIZATION_ADMIN` |         Yes          |    Organization     |        Yes        |     Yes      |        Organization        |          Organization           |     Yes      |
| `PLATFORM_ADMIN`     |         Yes          |         All         |        Yes        |     Yes      |            All             |               All               |     Yes      |

- Bootstrapped platform admin email: `jubayerhossaingalaxy@gmail.com` (verified email check enforced both client-side and in `firestore.rules`).
- Zero hardcoded API keys or client secrets exist in source files.
