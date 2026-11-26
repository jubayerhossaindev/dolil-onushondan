# Android E2E QA

The Android QA suite combines real-emulator Maestro journeys, Compose/Robolectric UI tests, and deterministic repository/domain integration tests. A tap is not considered proof of functionality unless the expected state or persisted result is asserted.

## Real-emulator Maestro journeys

The CI workflow executes every flow in `e2e/maestro/` against a fresh Android 35 Google APIs emulator:

- `guest-journey.yaml`: first-run onboarding, guest entry, seeded deed search/details, primary navigation, record tabs, secondary directories, user guide, and Add Deed wizard reachability.
- `deed-draft-persistence.yaml`: create a local guest draft, stop and relaunch the app without clearing data, and assert the draft remains in Records.
- `deed-entry-full-submission.yaml`: validate the first step, traverse all five steps, exercise party/parcel add-remove and optional fields, verify land-unit conversion, and submit a deed.
- `settings-persistence.yaml`: change guest-visible language, theme and dynamic-colour preferences, restart the process without clearing data, and verify the settings screen is still configured.
- Additional flows cover feature journeys as the inventory expands.

After the core Maestro flows pass, `scripts/run-maestro-responsive-matrix.sh` runs `e2e/responsive/responsive-ui.yaml` against compact phone, enlarged font, landscape phone and 960dp tablet emulator profiles. The script restores emulator display settings even when a test fails. Responsive screenshots and per-profile JUnit reports are included in the E2E artifact.

The CI runner creates one AVD and launches it once per job. It uses `emulator -accel-check` to verify KVM when available, explicitly configures TCG only when KVM cannot be used, and starts with `-no-snapshot` so no launcher dialogs or prior device state can be restored. It does not send a synthetic MENU key to the launcher after boot.

## Other automated application coverage

`./gradlew :app:testDebugUnitTest` runs the JVM/Robolectric suite. Coverage includes:

- Compose screen behavior for onboarding, access, dashboard, search/filter/sort, records/details, deed wizard, profile/settings, guide/tour, tablet list/detail layouts, and date picker interactions.
- Deed validation boundaries, Bangla digit normalization, duplicate detection, land-unit conversions, role/permission matrices, and record sorting/pagination.
- Room database save/update/archive/restore, audit-log and pending-sync transactions, guest isolation, persistence, and conflict handling.
- Portable JSON export/import integrity, checksum/tamper rejection, PDF generation, print adapter behavior, encryption, Drive backup/restore, account isolation, device retirement, and sync-worker authorization/error paths.

The Android CI artifact contains build/test/lint reports. The Android E2E artifact contains Maestro command output, JUnit XML, per-profile responsive reports, screenshots, emulator console and acceleration diagnostics, AVD configuration, activity/window state, logcat, preflight hierarchy, and failure hierarchy.

## Data and external-service boundaries

The app's real guest flows verify local Room persistence. Google Credential Manager/OAuth and live Google Drive operations are not treated as successful when the CI job uses a dummy Firebase configuration. A real Credential Manager UI/sign-in test requires a configured Web OAuth client, a stable registered CI signing certificate, Google Play services and a dedicated test account. Live personal-Drive round trips likewise require configured isolated credentials.

Role-based real-user flows are separate from role-permission unit tests. The current app session implementation must provide a real authorized role and admin-user directory before Office/Admin journeys can be verified end-to-end. Do not use a test-only role escalation shortcut.

The requested full UI inventory and the known configuration/product blockers are tracked in [REAL_DEVICE_QA_MATRIX.md](REAL_DEVICE_QA_MATRIX.md).

## Local execution

Build the debug APK using the same CI-only Firebase configuration and debug keystore preparation used by `.github/workflows/android-ci.yml`, install Maestro CLI 2.12.1, then run:

```bash
export MAESTRO_VERSION=2.12.1
curl -fsSL "https://get.maestro.mobile.dev" | bash
export PATH="$HOME/.maestro/bin:$PATH"
bash scripts/run-maestro-e2e.sh app/build/outputs/apk/debug/app-debug.apk
```
