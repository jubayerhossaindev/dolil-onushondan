# Environment & Build Secrets Configuration

## Zero-Secret Native Android OAuth Design

Dolil Onusondhan uses **native Android OAuth** (`Google Play Services AuthorizationClient` + `CredentialManager`) and therefore **never embeds a Google OAuth Client Secret** inside:

- `.env` / `.env.example`
- `BuildConfig`
- `app/build.gradle.kts`
- Source code or resources

---

## Files & Usage

| File / Config                     | Purpose                                                                                                            | Git / Distribution Notes                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| `.env.example`                    | Documents configuration contract (`google-services.json`, native OAuth, ignored debug token)                       | Safe to commit; contains zero secrets.                                           |
| `.env` (optional local)           | Used by `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` if local debug overrides are ever needed | Git-ignored; never add OAuth Client Secrets here.                                |
| `app/google-services.json`        | Supplies Firebase App ID, Web API Key (public client identifier), and `default_web_client_id`                      | Obtain directly from Firebase Console for `com.aistudio.dolilonusondhan.xkqmpz`. |
| `debug.keystore` / release `.jks` | Signs APK/AAB builds so Android OAuth SHA-1/SHA-256 verification succeeds                                          | Never commit production `.jks` passwords or release keystores.                   |

---

## Gradle Configuration Highlights (`app/build.gradle.kts`)

- `applicationId = "com.aistudio.dolilonusondhan.xkqmpz"`
- `namespace = "com.example"`
- `versionCode = 1`, `versionName = "1.0.0"` (`AppVersionInfo.formattedVersion == "v1.0.0"`)
- `googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }` allows clean local unit/Robolectric compilation and tests even before `app/google-services.json` is dropped in, while `CredentialReadinessChecker` clearly surfaces configuration readiness in the app UI.
- `secrets.ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")` prevents leaking debug tokens into `BuildConfig`.
