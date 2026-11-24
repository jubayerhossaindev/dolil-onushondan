# Performance & Resource Audit — Dolil Onusondhan

## 1. Compose Rendering & Recomposition Hygiene

- **Lazy List Virtualization:** All primary scrolling surfaces (`DashboardScreen`, `SearchScreen`, `RecordsScreen`, `DeedDetailsPane`, `DeedEntryScreen`, `PersonsDirectoryScreen`, `LandMouzaDirectoryScreen`, `ProfileSettingsScreen`, `UserGuideScreen`) use `LazyColumn` / `LazyRow` with stable explicit `key = { ... }` parameters (`key = { it.id }`, `key = { it.deed.id }`, `key = { it.code }`, `key = { _, topic -> topic.titleRes }`) to minimize item re-composition and preserve scroll state across updates.
- **Derived State & Memoization:**
  - `derivedStateOf` used for `isDirty`, `stepCompletedFlags`, `activeFilterCount`, and `DolilMainScaffold` state checks so high-frequency text typing does not trigger parent layout recomposition.
  - `remember(...)` wraps expensive calculations: `BigDecimal` area totals (`totalShotok`, `totalKatha`, `totalSqFt`), directory extraction (`PersonsDirectoryScreen`, `LandMouzaDirectoryScreen`), filtered/sorted lists (`RecordsScreen`), and catalog loading (`BangladeshLocationsCatalog.ensureLoaded` uses `AtomicBoolean` idempotent guard).
- **Flow Sharing & Lifecycle:** `DolilViewModel` exposes `StateFlow`s via `stateIn(..., SharingStarted.WhileSubscribed(5000L), ...)` and collects via `collectAsStateWithLifecycle()` to pause upstream Firestore/Room subscriptions cleanly when backgrounded.

## 2. Database & Query Scaling

- **Bounded Search Tokens:** `SearchNormalizer.buildSearchTokens(..., maxTokens = 140)` caps Firestore `array-contains` token array sizes below `firestore.rules` limit (`150`) and prevents runaway document bloat.
- **SQL Indexing:** `DeedEntity` defines explicit Room `@Index` annotations on `deedNumber`, `normalizedDeedNumber`, `deedYear`, `officeId`, `status`, `visibility`, `createdBy`, `deletedAtMillis`, and `updatedAtMillis`.
- **Pagination:** Both `SearchScreen` and `RecordsScreen` paginate (`DeedSorter.paginate`, default `pageSize = 20`) with explicit `Load More` controls (`search_load_more_button`, `records_load_more_button`) instead of rendering unbounded lists.

## 3. APK & Build Optimization

- Unused optional libraries (`camera-*`, `retrofit`, `okhttp`, `coil-compose`, `accompanist-permissions`, `play-services-location`, `firebase-ai`) are cleanly commented out in `app/build.gradle.kts` to keep APK size and build overhead small.
- **Residual Measurement Gap:** Live frame timing (`JankStats` / Macrobenchmark) could not be measured without an attached Android device or emulator (`BLK-04`).
