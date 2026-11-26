# Android E2E QA

This suite runs the Android app on a GitHub-hosted Android emulator and drives the rendered UI with Maestro.

## Current automated journey

The initial guest-mode journey validates:

- First-run onboarding and access selection.
- Local guest sandbox entry and the first-run product-tour dismissal.
- Search for a known seeded demo deed and opening its details.
- Primary navigation: Home, Search, Records, and Profile.
- Records tabs available to the guest role: All, Drafts, and Archive. The approval-only Pending tab is intentionally absent for `PUBLIC_USER` guest accounts.
- Secondary destinations: Persons, Land, and User Guide.
- Opening the Add Deed wizard.

The flow uses stable Compose test tags exposed as Android resource IDs. The UI labels and interactions remain the source of truth; tags are selectors, not proof that the underlying business action succeeded.

## Local execution

Build the debug APK with the same CI-only Firebase config and debug keystore preparation used by `.github/workflows/android-ci.yml`, start an Android emulator, then run:

```bash
export MAESTRO_VERSION=2.12.1
curl -fsSL "https://get.maestro.mobile.dev" | bash
export PATH="$HOME/.maestro/bin:$PATH"
bash scripts/run-maestro-e2e.sh app/build/outputs/apk/debug/app-debug.apk
```

## CI evidence

The `Android E2E` workflow uploads the Maestro output directory, emulator logcat, activity dump, final screenshot, and the CLI version for both passing and failing runs.

## Coverage boundaries

This is an initial deterministic end-to-end baseline, not a claim of exhaustive coverage. It currently exercises the local guest sandbox. Live Google OAuth, Firebase account creation, Google Drive OAuth/backup/restore, real printer hardware, and physical-device/OEM-specific behaviour require isolated test credentials, service fixtures, and/or physical-device infrastructure. Add explicit assertions and flows for each additional workflow before treating it as release-covered.

A control is not considered tested merely because it was tapped: critical actions must assert their expected result and relevant persisted state.
