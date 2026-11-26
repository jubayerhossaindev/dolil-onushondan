# Real-device UI QA coverage matrix

This document separates verified behavior from real-device work still blocked by application configuration or product functionality. A green workflow is not treated as proof that every UI control has been exercised.

## Current baseline from the checked-in QA audits

| Inventory | Recorded status |
|---|---:|
| Screens | 14 total: 12 PASS, 2 PARTIAL |
| Button/action groups | 21 total: 19 PASS, 1 PARTIAL, 1 BLOCKED |
| Navigation paths | 16 total: 15 PASS, 1 BLOCKED |
| Function groups | 13 total: 11 PASS, 1 PARTIAL, 1 BLOCKED |
| Real-emulator Maestro flows before this expansion | 2 |

These are audit-row counts, not a percentage of all possible UI states. Some audit rows group multiple controls and some results are Robolectric/Compose tests rather than real Android system-UI interactions.

## Six requested real-device areas

| Area | Existing evidence | Real-device work | Current limitation |
|---|---|---|---|
| Google Sign-In / Credential Manager | Auth exception mapping and auth logic reviewed; no live credential picker run | Exercise the actual Credential Manager sheet, dismissal, account selection, and successful Firebase sign-in | The CI run currently creates a dummy `google-services.json` because `GOOGLE_SERVICES_JSON` is not configured. That file has no Web OAuth client, and the CI debug keystore is generated per run. A real picker/sign-in test needs Firebase configuration with a matching Web client and a stable signing certificate; a successful auth journey also needs a dedicated test Google account on the emulator. |
| PDF / Print / SAF | PDF bytes, pagination, and PrintDocumentAdapter behavior are covered by Robolectric tests | Drive `ACTION_CREATE_DOCUMENT` through the Android DocumentsUI picker and open the actual PrintManager UI | The system picker and print preview are separate system apps. They need cross-app UI automation and evidence from the Android 35 emulator; a Robolectric callback test is not equivalent. |
| Deed Entry, all five steps | Compose/Robolectric tests cover required-field validation, party add/remove, location entry, land-unit conversion, duplicate handling, and save callbacks | `e2e/maestro/deed-entry-full-submission.yaml` exercises the wizard on a real emulator, including validation, optional party/parcel details, add/remove, calculation and submission | Newly added flow requires a green Android E2E run before it can be marked verified. |
| Role-based UI | Domain permission matrix and isolated Compose screen tests use representative role fixtures | Run actual app sessions for Public, Office Viewer, Data Entry, Supervisor, Organization Admin and Platform Admin | This is currently blocked by production session wiring, not merely missing tests: signed-in `DolilViewModel.userProfile` is constructed with `PUBLIC_USER`; `observeAdminUsers()` returns an empty list; `adminAssignRole()` reports server role changes are disabled. The existing screen-level role fixtures do not prove that real signed-in sessions receive those roles. |
| Settings and persistence | Compose/Robolectric tests cover settings sections and DataStore functions | `e2e/maestro/settings-persistence.yaml` checks guest-visible language/theme/dynamic-colour settings after app process restart | Account, Google Drive, encryption, sync and account-deletion UI requires a valid signed-in Firebase/Drive test environment; guest mode intentionally does not expose all those controls. |
| Responsive / accessibility | Robolectric tests cover compact/expanded widths and enlarged font sizes | `scripts/run-maestro-responsive-matrix.sh` runs a real emulator smoke journey at compact phone, enlarged font, landscape phone and 960dp tablet profiles | This is a targeted geometry/font matrix, not a full TalkBack, switch-access, colour-contrast or every-device certification. |

## Real-emulator profiles

The responsive matrix configures the same fresh Android 35 emulator with these profiles, runs a UI journey, and restores its display settings afterward:

- 360dp compact portrait phone at 1.0x font scale.
- Approximately 411dp portrait phone at 1.3x font scale.
- Landscape phone profile, activating expanded-width navigation where applicable.
- 960dp tablet profile.

Each profile asserts onboarding/guest entry, Dashboard, Records, Search, Profile and Deed Entry reachability, and records screenshots.

## Requirements before the remaining blocked areas can be called PASS

### Credential Manager

1. Configure `GOOGLE_SERVICES_JSON` with the Android client and a generated Web OAuth client (`default_web_client_id`).
2. Use a stable CI debug keystore whose SHA-1 is registered in Firebase/Google Cloud. A new randomly generated debug keystore on every job will not provide a stable certificate fingerprint.
3. Use an isolated test Google account and a Google APIs/Play-services emulator that has the test account provisioned. Do not place account passwords or OAuth secrets in source files or chat.
4. Automate cancellation/dismissal and successful sign-in as separate scenarios. Do not mark successful sign-in PASS based only on the picker opening.

### PDF / Print system UI

Use a cross-app UI test (Maestro or Android UI Automator) to select a DocumentsUI destination, verify the generated PDF is present and non-empty, open PrintManager preview, verify the adapter's document/page information, and cancel cleanly. Android's Storage Access Framework uses the system picker for `ACTION_CREATE_DOCUMENT`; PrintManager delegates layout/write to `PrintDocumentAdapter`.

### Role-based sessions

The app must expose a real, authorized role source and an actual admin-user directory/update implementation before full-role emulator journeys are meaningful. Do not add a test-only shortcut that lets a user self-assign a privileged role. Keep the server authorization boundary authoritative.

## Pass criteria

A scenario is PASS only when:
- it runs on the intended real emulator profile;
- every important control action has an asserted visible or persisted outcome;
- no hidden setup state is mistaken for a user journey;
- screenshots, Maestro/JUnit output and relevant device logs are retained;
- the same flow passes on a clean run and after relevant restart/regression checks.

No overall “100% UI coverage” percentage is claimed until a complete control inventory has been mapped to executed tests and the blocked product/configuration areas above are resolved.
