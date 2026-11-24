# Accessibility Audit Report (WCAG 2.2 AA & Android Accessibility)

## 1. Color Contrast & Semantic Independence

- **Automated Luminance Checks:** Verified in `PaperAndInkRedesignRobolectricTest`:
  - Primary/Secondary/Tertiary/Error & `onSurfaceVariant` text on `background` and `surfaceContainerHigh`: `>= 4.5:1` in both Light and Dark themes.
  - Card & input outlines against `background` and `surfaceContainerLowest`: `>= 3.0:1`.
  - Archival parchment preview (`PaperDocumentOnSurface` & `PaperDocumentOnMuted` on `PaperDocumentSurface`): `>= 4.5:1`.
  - Independent status chips (`Approved`, `Pending`, `Draft`, `Archived`, `Error`): `>= 4.5:1` in both Light and Dark modes.
- **Color-Blind Safety:** Status badges (`StatusChip`) and alerts always pair semantic colors with distinct icons (`Approve`, `Pending`, `Draft`, `Archive`) and explicit Bengali/English text labels—never color alone.

## 2. Touch Targets & Interactive Ergonomics

- `minimumInteractiveComponentSize()` (Material 3 `48.dp x 48.dp` minimum touch target) is enforced across `DolilPrimaryButton`, `DolilSecondaryButton`, `DolilOutlinedDestructiveButton`, `DolilIconButton`, `DetailRow` Copy buttons, accordion rows in `UserGuideScreen`, Wizard step pills, Onboarding skip/language controls, and `ConflictResolutionDialog` buttons.

## 3. Screen Reader Semantics (TalkBack) & Reduced Motion

- **Content Descriptions:** Meaningful `contentDescription` strings supplied on interactive icons (`Copy`, `Calendar`, `Clear`, `Help`, `Expand/Collapse`, `Delete`, `Back`) while decorative icons pass `contentDescription = null`.
- **Semantic Announcements & Headings:**
  - `semantics { heading() }` on screen & section headers (`DashboardScreen`, `RecordsScreen`, `SearchScreen`, `UserGuideScreen`).
  - `semantics { liveRegion = LiveRegionMode.Polite }` on dynamic search result counts (`SearchScreen`) and error banners (`DolilErrorBanner`).
  - `semantics { stateDescription = ... }` on expandable `UserGuideScreen` FAQ accordions.
- **Reduced Motion Compliance:** `isReducedMotionEnabled(context)` disables decorative shimmer/spring/scale animations across `Components.kt`, `OnboardingScreen`, `SearchScreen`, `RecordsScreen`, `UserGuideScreen`, and `ProductTourOverlay` when OS animator duration scale is `0`.
- **Large Font Scaling (`1.5x`) at Narrow Width (`360dp`):** Verified in `QaUiScreensAuditRobolectricTest` without text truncation crashes or clipped primary actions.

## 4. Residual Manual Verification Item

- TalkBack focus order traversal on a live Android emulator/device could not be executed in the headless container (`BLK-04`).
