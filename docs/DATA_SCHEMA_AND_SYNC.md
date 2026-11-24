# Data Schema, Offline Persistence & Cloud Sync

## 1. Local Room Database (`dolil_onusondhan.db`)

1. `deeds` (`DeedEntity`):
   - Primary Key: `id` (UUID string)
   - Indexed Columns: `officeId`, `deedYear`, `normalizedDeedNumber`, `status`, `registrationDateMillis`, `updatedAtMillis`
   - Search Index Column: `searchTokensJoined` (bounded normalized Bengali + English tokens & prefixes)
   - Embedded/JSON Columns (via `RoomConverters` + `org.json`): `grantorsJson`, `granteesJson`, `parcelsJson`
   - Optimistic Concurrency & Sync: `rowVersion` (`Long`), `syncState` (`SYNCED`, `PENDING_UPLOAD`, `CONFLICT`, `LOCAL_ONLY_DRAFT`)
2. `audit_logs` (`AuditLogEntity`):
   - Immutable action records (`CREATE`, `UPDATE`, `SUBMIT_DRAFT`, `APPROVE`, `SOFT_DELETE`, `RESTORE`) linked by `entityId` with `previousVersion -> newVersion` and timestamp.
3. `pending_sync` (`PendingSyncEntity`):
   - Tracks unsynced deed IDs, operation type, retry count, and last error.
   - `DolilDao.saveDeedWithAuditAndSync` writes deed + audit log + pending sync queue entry in a single `@Transaction`.

## 2. Cloud Firestore (`ai-studio-android-fad5db55-43a4-4349-9875-8dfa6c7bb51e`)

- Always accessed via `FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))`.
- Collections:
  - `users/{userId}`: Profile, role (`PUBLIC_USER` .. `PLATFORM_ADMIN`), assigned `officeId` / `organizationId`, `preferredLanguage`, `themeMode`.
  - `deeds/{deedId}`: Deed metadata, `grantors`, `grantees`, `parcels`, `searchTokens`, `officeViewerUids`, `visibility` (`PUBLIC`, `OFFICE_ONLY`, `PRIVATE_DRAFT`), `status` (`DRAFT`, `PENDING_REVIEW`, `APPROVED`, `SOFT_DELETED`), `rowVersion`, and `serverTimestamp` fields.
  - `audit_logs/{logId}`: Append-only audit logs (`allow update, delete: if false` in `firestore.rules`).

> **Backend Architecture Note**: This container provisions **Cloud Firestore** + **Firebase AI Logic** + **Room** rather than a standalone PostgreSQL server.

## 3. Offline Sync & Optimistic Concurrency

1. All deed edits and drafts save locally to Room first so offline work is never lost.
2. `cacheRemoteDeeds()` skips any `deedId` currently in `pending_sync`, preventing stale snapshots from overwriting local unsynced changes.
3. On cloud upload, `FirestoreRepository.saveDeedWithOptimisticLock` checks `serverDeed.rowVersion > localDeed.rowVersion`.
   - If a newer server version exists, it returns `SaveDeedOutcome.Conflict` and sets `SyncState.CONFLICT`.
   - The UI presents `ConflictResolutionDialog` letting the user choose **Keep Local** (rebases `rowVersion` and saves) or **Accept Server** (adopts server copy and clears pending sync).
