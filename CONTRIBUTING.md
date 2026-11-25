# Contributing to Dolil Onusondhan

Thank you for considering a contribution! Bug reports, documentation improvements, focused fixes, accessibility improvements, tests, and carefully scoped features are welcome.

Before contributing, please read the [Code of Conduct](CODE_OF_CONDUCT.md) and follow the project's privacy and security requirements.

## 1. Before you start

- Search [existing issues](https://github.com/jubayerhossaindev/dolil-onushondan/issues) before opening a duplicate.
- For a substantial feature or architectural change, open an issue first so the scope and trade-offs can be discussed.
- Keep changes focused. Avoid unrelated refactoring or formatting changes.
- Do not assume that older documentation always describes the current implementation. Check the active source code and Gradle configuration when investigating behavior.

## 2. Development environment

For the Android application, use:

| Requirement | Version / configuration |
|---|---|
| JDK | 21 |
| Android Gradle Plugin | 9.1.1 |
| Gradle Wrapper | 9.3.1 |
| Kotlin | 2.4.21 |
| Android compile SDK | API 36, extension level 36.1 |
| Minimum Android API | 24 |

Use the checked-in Gradle Wrapper rather than installing a separate Gradle version.

### Clone the repository

```bash
git clone https://github.com/jubayerhossaindev/dolil-onushondan.git
cd dolil-onushondan
```

Open the project in Android Studio and allow Gradle synchronization to finish.

## 3. Project conventions

### Android and Kotlin

- Follow the existing package boundaries: `core`, `data`, `domain`, and `ui`.
- Keep UI rendering and navigation in the UI layer; keep reusable validation, search normalization, and domain rules out of composable screen code where the existing architecture provides a suitable domain component.
- Reuse the established Paper & Ink design system, Material 3 components, typography, spacing, colors, and icon registry.
- Preserve the existing offline-first behavior, account isolation, guest database isolation, and explicit Google Drive authorization boundaries.
- Keep user-facing text consistent in both বাংলা and English. Update the relevant localized resources whenever a change affects visible text.
- Follow `.editorconfig`: two-space indentation for Kotlin/Kotlin DSL, four-space continuation indentation, a 120-character line-length target, and the configured ktlint official style.
- Prefer small, readable changes that match surrounding code. Do not reformat unrelated files.

### UI and accessibility

For UI changes:

- Reuse existing shared components and design tokens before adding new ones.
- Keep light and dark theme behavior consistent.
- Preserve responsive behavior on compact screens and larger layouts.
- Maintain meaningful accessibility labels and screen-reader semantics.
- Keep interactive targets usable and respect reduced-motion preferences.
- Include before/after screenshots or a short screen recording in the pull request when practical. Do not present mockups as implementation evidence.

### Python pipeline

Changes under `narayanganj_land_pipeline/` should follow the existing module boundaries and data-policy guardrails.

- Respect robots directives, published source terms, and access restrictions.
- Stop when a source blocks access; do not bypass CAPTCHA, WAF, authentication, or other access controls.
- Do not collect owner identity data, NID numbers, phone numbers, email addresses, or other private information.
- Keep unverified records separate from verified output.
- Do not fabricate missing administrative or geographic data.

### Documentation

- Keep commands and technical claims aligned with the current implementation.
- Update the relevant guide when a setup step, feature, or supported workflow changes.
- Use relative links for repository-local documentation.
- Keep examples free of real account details, secrets, and personal land-record information.

## 4. Credentials and test data

Never commit:

- Production Firebase configuration files unless the project explicitly requires and approves their publication.
- Release keystores, signing passwords, OAuth client secrets, access tokens, or private keys.
- Real deed records, grantor/grantee personal details, NID information, or private office records.
- Unredacted logs, screenshots, or exports containing personal information.

Use synthetic fixtures and the isolated Guest Sandbox for demonstrations and tests. Keep credentials in the appropriate local or GitHub Actions secret configuration.

The Drive integration uses native Android authorization. Do not add a Google OAuth client secret to the APK, source code, or committed configuration.

## 5. Run the Android checks

Run these commands from the repository root before opening a pull request:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

This runs Android lint, JVM/Robolectric unit tests, and a debug APK build.

On Windows Command Prompt, use:

```bat
gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

Some flows require an Android device, Google Play Services, or an externally configured Firebase / Google Cloud project. Clearly identify checks that you could not run; do not report a blocked test as passed.

## 6. Run the Python pipeline tests

When changing the companion pipeline:

```bash
cd narayanganj_land_pipeline
python -m venv .venv
python -m pip install -r requirements.txt
PYTHONPATH=src python -m pytest -v
```

On Windows Command Prompt:

```bat
cd narayanganj_land_pipeline
python -m venv .venv
.venv\Scripts\activate
python -m pip install -r requirements.txt
set PYTHONPATH=src
python -m pytest -v
```

Do not run live collection against a source unless its access policy permits it. Prefer mocked or fixture-based tests for automated validation.

## 7. Markdown checks

The repository contains a markdownlint configuration and the `markdownlint-cli2` dependency. You can validate changed Markdown files with:

```bash
npx markdownlint-cli2 --config .markdownlint.yaml README.md CODE_OF_CONDUCT.md CONTRIBUTING.md SECURITY.md
```

If the command reports an issue, fix the relevant Markdown rather than disabling a rule only to silence the check.

## 8. Branch and pull request workflow

Use a descriptive branch name, for example:

```text
feat/deed-search-filter
fix/drive-sync-retry
docs/contributing-guide
test/deed-validation
```

Then:

1. Make a focused change and commit it with a clear message.
2. Run the checks relevant to the changed code.
3. Push your branch and open a pull request against `main`.
4. Explain the problem, the solution, and any user-visible behavior change.
5. Link related issues and note configuration or migration requirements.
6. Include test results and screenshots where they help reviewers.
7. Respond to review feedback and keep the pull request focused.

A useful pull request description includes:

- **What changed:** The behavior or documentation added or corrected.
- **Why:** The issue or user need addressed.
- **Validation:** Commands executed and their actual results.
- **Limitations:** Any manual, device-only, or externally configured checks that remain.
- **Data impact:** Any database, migration, backup, or compatibility implications.

## 9. Pull request checklist

Before submitting, confirm the applicable items:

- [ ] The change has a clear purpose and is limited to the intended scope.
- [ ] Existing behavior and project conventions are preserved.
- [ ] Relevant tests were added or updated.
- [ ] Android lint, unit tests, and build checks pass, or blockers are documented.
- [ ] Python pipeline tests pass when the pipeline is changed.
- [ ] বাংলা and English UI text are both updated when needed.
- [ ] Accessibility and Light / Dark theme behavior were considered for UI changes.
- [ ] Documentation and setup instructions are current.
- [ ] No credentials, private data, or real deed records were added.
- [ ] Any migration, configuration, or external service requirement is explained.

## 10. Review and release

Maintainers review contributions for correctness, privacy, maintainability, accessibility, test coverage, and consistency with the project's scope.

Submitting a pull request does not guarantee acceptance or a particular release date. Changes may be requested, deferred, or declined when they conflict with project goals or cannot be safely maintained.

Thank you for helping improve Dolil Onusondhan!
