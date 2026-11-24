# UI / UX Audit Report — Dolil Onusondhan

## 1. Visual Design System & Color Consistency ("Paper & Ink" Theme)

- **Light Mode Surface Hierarchy:** Grounded warm paper palette (`PaperLightBackground = #F5F2EB`, `PaperLightSurfaceLowest = #FFFDF9`, `PaperLightSurfaceHigh = #EAE4D9`) paired with high-contrast Ink text (`#18202B`, `#4A5260`).
- **Dark Mode Surface Hierarchy:** Deep charcoal canvas (`#11151B`, `#181D25`, `#252C38`) paired with crisp off-white ink (`#EDF1F7`).
- **Archival Document Surface Independence:** Verified via `PaperAndInkRedesignRobolectricTest` that `PaperDocumentSurface` (`#F8F4EA`), `PaperDocumentOnSurface` (`#1E2022`), `AccentAntiqueGold`, and all semantic `StatusChip` containers remain preserved even when Android 12+ Material You Dynamic Color is toggled ON (`useDynamicColor = true`).
- **WCAG 2.2 AA Contrast Verification:** All light/dark primary, secondary, tertiary, error, outline, archival parchment, and semantic status foreground/background pairs pass automated luminance contrast assertions (`>= 4.5:1` for text, `>= 3.0:1` for outlines/borders).

## 2. Typography & Multilingual Support (Bangla + English)

- Bundled local `.ttf` fonts under `/res/font/`: `hind_siliguri.ttf` (headings/display), `noto_sans_bengali.ttf` (Bangla body), and `plus_jakarta_sans.ttf` (Latin body).
- Tabular & editorial numerals applied cleanly (`tabularNumerals()`, `editorialNumeric()`) on stat counters, deed numbers, land areas, and review cards to prevent digit jitter.
- **Font Scaling Verification:** Tested at `fontScale = 1.5f` on `360dp` compact width in `QaUiScreensAuditRobolectricTest`; chips, cards, and action rows wrap/ellipsize without clipping or crashing.

## 3. Navigation & Information Architecture

- **5-Step Guided Deed Entry Wizard:** Breaks dense land registry data entry into `1. General`, `2. Grantors`, `3. Grantees`, `4. Location`, `5. Land & Review`, complete with horizontal progress pills (`entry_section_pill_0..4`), step validation gating before Next, auto-jump to first server/ViewModel error step, live Shotok/Katha/SqFt converter (`live_total_area_card`), and interactive review card (`entry_review_summary_card`).
- **Responsive Breakpoints:**
  - Compact phones (`360dp`–`599dp`): Bottom `NavigationBar` + single-column `LazyColumn` pushing to full-screen `DeedDetailsScreen`.
  - Medium / Expanded (`>=600dp` Navigation Rail, `>=840dp` Dual-Pane List-Detail): Verified at `w960dp-h600dp` across `SearchScreen` and `RecordsScreen` (`deed_details_pane` updates live without route stack reset).

## 4. Feedback, Empty States & Confirmation Flows

- Explicit visual distinction between Loading (`DolilLoadingSkeleton` shimmer/cards), Error (`DolilErrorBanner`), Empty (`DolilEmptyState` with single focused CTA), and Success/Undo (`SnackbarHost` with Undo action after archive).
- Destructive and state-changing actions (`Soft Delete`, `Restore`, `Sign Out`, `Discard Unsaved Changes`) consistently require `DolilConfirmationDialog` confirmation.
- Minor consistency gap found (`BUG-002`: `Icons.Filled.ChevronRight` in `DolilSettingsRow`) was resolved using `DolilIcons.ChevronRight`.
