# Bug Report — Dolil Onusondhan QA Audit

All defects listed below were discovered through real baseline test execution (`gradle :app:testDebugUnitTest`), full source inspection, and static security/accessibility review.

---

## BUG-001 (High / Test Automation Failure & Scroll Locator Defect)

- **Area:** `DeedEntryScreen` / `DolilEndToEndRobolectricTest`
- **File:** `/app/src/test/java/com/example/DolilEndToEndRobolectricTest.kt` (lines 178–190)
- **Symptom:** Baseline test `deed entry screen adds dynamic grantor and parcel and computes live Shotok total` failed with:  
  `java.lang.AssertionError: Action performScrollTo() failed. Reason: Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'wizard_next_button')`.
- **Root Cause:** After converting `DeedEntryScreen` into a 5-step wizard inside `LazyColumn("deed_entry_screen")`, footer items (`wizard_next_button`, `save_draft_button`) and below-fold items are virtualized by `LazyColumn` until scrolled via `performScrollToNode(hasTestTag(...))`. `NarayanganjCatalogRobolectricTest` used `onNodeWithTag("deed_entry_screen").performScrollToNode(...)`, whereas `DolilEndToEndRobolectricTest` still called `.performScrollTo()` directly on uncomposed child tags.
- **Severity:** High (blocked regression test pass)
- **Status:** Fixed & pending regression confirmation.

---

## BUG-002 (Low / Design System Token Consistency)

- **Area:** Shared UI Component Library (`DolilSettingsRow`)
- **File:** `/app/src/main/java/com/example/core/designsystem/Components.kt` (lines 33–34, 800)
- **Symptom:** `DolilSettingsRow` bypassed the centralized `DolilIcons` registry and directly imported `androidx.compose.material.icons.Icons` + `Icons.Filled.ChevronRight`.
- **Root Cause:** Leftover Material icon direct reference during prior icon-system consolidation.
- **Severity:** Low
- **Status:** Fixed (`DolilIcons.ChevronRight`).

---

## BUG-003 (Medium / Concurrency & Loading State Recovery in `DolilViewModel.saveDeed`)

- **Area:** `DolilViewModel` Deed Save & Draft Flow
- **File:** `/app/src/main/java/com/example/ui/DolilViewModel.kt` (lines 410–477)
- **Symptom:** Rapid double-tap before coroutine dispatch could trigger concurrent `saveDeed` executions creating duplicate audit entries; an unexpected runtime exception inside `syncRepo.saveDeedLocallyAndTrySync` could leave `_isSaving.value = true` indefinitely.
- **Root Cause:** Missing early guard `if (_isSaving.value) return` at method entry and missing `try / finally` reset around `_isSaving` / `_savingAsDraft`.
- **Severity:** Medium
- **Status:** Fixed.

---

## BUG-004 (Medium / Functional Localization — Bengali Digit Year Parsing)

- **Area:** `DeedEntryScreen` & `SearchScreen` Year Fields
- **Files:**
  - `/app/src/main/java/com/example/ui/screens/DeedEntryScreen.kt` (lines 168, 389, 425)
  - `/app/src/main/java/com/example/ui/screens/SearchScreen.kt` (line 771)
- **Symptom:** `deedYearText.trim().toIntOrNull()` and `yearText.trim().toIntOrNull()` did not explicitly pass through `SearchNormalizer.normalizeCode(...)` before integer parsing, risking inconsistent behavior across locales/IME Bengali digit entry (`"২০২৬"`), unlike `deedNumber` and `landAreaDecimal` which already normalized Bengali digits.
- **Root Cause:** Inconsistent normalization pipeline between text/decimal fields and year fields.
- **Severity:** Medium
- **Status:** Fixed (`SearchNormalizer.normalizeCode(...)` applied before `.toIntOrNull()` in validation, record building, step completion indicator, and search filter sheet).

---

## BUG-005 (Medium / Security & Privacy — Local Database Backup Exclusion)

- **Area:** Android Auto Backup & Device-to-Device Transfer Rules
- **Files:**
  - `/app/src/main/res/xml/backup_rules.xml`
  - `/app/src/main/res/xml/data_extraction_rules.xml`
- **Symptom:** `AndroidManifest.xml` specifies `android:allowBackup="true"`, while both backup rules XML files contained only commented-out template stubs. Consequently, local Room databases (`dolil_onusondhan.db`, `dolil_guest_sandbox.db`) containing personal deed records, party names, and office drafts would be included in unencrypted OS cloud backups and transfers.
- **Root Cause:** Default Android project template backup XML files had never been populated.
- **Severity:** Medium
- **Status:** Fixed (`<exclude>` entries added for both Room DBs with `-wal`/`-shm` journals and `dolil_user_preferences.preferences_pb` in both `cloud-backup` and `device-transfer`).
