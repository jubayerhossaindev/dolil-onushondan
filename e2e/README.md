# Android E2E QA

The Android QA suite combines real-emulator Maestro journeys, Compose/Robolectric UI tests, and deterministic repository/domain integration tests. A tap is not considered proof of functionality unless the expected state or persisted result is asserted.

## Real-emulator Maestro journeys

The CI workflow executes every flow in `e2e/maestro/` against a fresh Android 35 Google APIs emulator:

- `guest-journey.yaml`: first-run onboarding, guest entry, seeded deed search/details, primary navigation, record tabs, secondary directories, user guide, and Add Deed wizard reachability.
- Additional feature flows are added to cover Deed Entry, validation, Draft persistence, complete submission, search/filter/sort, directory interactions, and app restart.
- Each flow starts from a deterministic app state. Flows that verify persistence intentionally restart the app without clearing application data.

The CI runner creates one AVD and launches it once per job. It uses `emulator -accel-check` to verify KVM when available, explicitly configures TCG only when KVM cannot be used, and starts with `-no-snapshot` so no launcher dialogs or prior device state can be restored. It does not send a synthetic MENU key to the launcher after boot.

## Other automated application coverage

`./gradlew :app:testDebugUnitTest` runs the JVM/Robolectric suite. Coverage includes:

- Compose screen behavior for onboarding, access, dashboard, search/filter/sort, records/details, deed wizard, profile/settings, guide/tour, tablet list/detail layouts, and date picker interactions.
- Deed validation boundaries, Bangla digit normalization, duplicate detection, land-unit conversions, role/permission matrices, and record sorting/pagination.
- Room database save/update/archive/restore, audit-log and pending-sync transactions, guest isolation, persistence, and conflict handling.
- Portable JSON export/import integrity, checksum/tamper rejection, PDF generation, print adapter behavior, encryption, Drive backup/restore, account isolation, device retirement, and sync-worker authorization/error paths.

The Android CI artifact contains build/test/lint reports. The Android E2E artifact contains Maestro command output, JUnit XML, emulator console and acceleration diagnostics, AVD configuration, screenshots, activity/window state, logcat, preflight hierarchy, and failure hierarchy.

## Data and external-service boundaries

The app uses Firebase Authentication for identity. Deed data is stored locally in Room and can be backed up to the user's personal Google Drive; the Android app does not use Firestore for deed storage. Guest mode intentionally uses a separate Room database and never queues cloud sync.

The CI guest flows validate local Room persistence and complete local deed lifecycle operations without production credentials. Google Credential Manager/OAuth and live Google Drive network operations are not faked as successful: their application logic is exercised with deterministic auth/Drive gateways and storage test doubles in JVM/Robolectric tests. A real production-account sign-in or live personal-Drive round trip requires configured, isolated test credentials and is reported separately.

## Local execution

Build the debug APK using the same CI-only Firebase configuration and debug keystore preparation used by `.github/workflows/android-ci.yml`, install Maestro CLI 2.12.1, then run:

```bash
export MAESTRO_VERSION=2.12.1
curl -fsSL "https://get.maestro.mobile.dev" | bash
export PATH="$HOME/.maestro/bin:$PATH"
bash scripts/run-maestro-e2e.sh app/build/outputs/apk/debug/app-debug.apk
```
