package com.example.data.repository

import com.example.data.local.AuditLogEntity
import com.example.data.local.DolilDao
import com.example.data.local.GuestSandboxSeeder
import com.example.data.local.LEGACY_LOCAL_OWNER_UID
import com.example.data.local.PendingSyncEntity
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.AuditAction
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.DeedConflict
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedVisibility
import com.example.domain.model.SyncState
import com.example.domain.model.UserProfile
import com.example.domain.search.DefaultIndexedSearchStrategy
import com.example.domain.search.SearchNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

sealed interface SaveDeedOutcome {
    data class Saved(val deed: DeedRecord) : SaveDeedOutcome
    data class Conflict(val conflict: DeedConflict) : SaveDeedOutcome
}

data class SyncReport(
    val syncedCount: Int,
    val failedCount: Int,
    val conflicts: List<DeedConflict>
)

class DolilSyncRepository(
    private val dao: DolilDao,
    private val ownerUid: String = LEGACY_LOCAL_OWNER_UID,
    private val deviceId: String = "local-device",
    private val enableCloudSync: Boolean = true,
    private val isDriveConnectedProvider: () -> Boolean = { false }
) {
    private val searchStrategy = DefaultIndexedSearchStrategy()

    fun observeLocalDeeds(): Flow<List<DeedRecord>> =
        dao.observeDeedsForOwner(ownerUid).map { list -> list.map { it.toDomain() } }

    fun observePendingSyncCount(): Flow<Int> =
        dao.observePendingSyncForOwner(ownerUid).map { it.size }

    fun observeAuditLogs(deedId: String): Flow<List<AuditLogEntry>> =
        dao.observeAuditLogsForOwnerAndDeed(ownerUid, deedId).map { list -> list.map { it.toDomain() } }

    fun observeLegacyLocalDeedCount(): Flow<Int> =
        dao.observeDeedCountByOwner(LEGACY_LOCAL_OWNER_UID)

    suspend fun cacheRemoteDeeds(remoteDeeds: List<DeedRecord>) {
        if (remoteDeeds.isEmpty()) return
        val pendingIds = dao.getPendingSyncForOwnerOnce(ownerUid).map { it.deedId }.toSet()
        val safeToCache = remoteDeeds.filterNot { it.id in pendingIds }.map {
            it.copy(
                ownerUid = ownerUid,
                syncState = SyncState.SYNCED
            ).toEntity()
        }
        if (safeToCache.isNotEmpty()) {
            dao.upsertDeeds(safeToCache)
        }
    }

    suspend fun seedGuestSandboxIfEmpty() {
        GuestSandboxSeeder.seedIfEmpty(dao)
    }

    suspend fun saveDeedLocallyAndTrySync(
        deed: DeedRecord,
        actor: UserProfile,
        action: AuditAction
    ): Result<SaveDeedOutcome> {
        val now = System.currentTimeMillis()
        val nextSyncState = if (enableCloudSync && isDriveConnectedProvider()) {
            SyncState.PENDING_UPLOAD
        } else {
            SyncState.LOCAL_ONLY_DRAFT
        }
        val prepared = deed.copy(
            ownerUid = ownerUid,
            createdBy = deed.createdBy.ifBlank { ownerUid },
            updatedBy = ownerUid,
            deviceId = deviceId,
            revisionId = UUID.randomUUID().toString(),
            normalizedDeedNumber = SearchNormalizer.normalizeCode(deed.deedNumber),
            updatedAtMillis = now,
            syncState = nextSyncState
        )
        val audit = AuditLogEntry(
            id = UUID.randomUUID().toString(),
            actorId = ownerUid,
            actorName = actor.fullName.ifBlank { actor.email },
            organizationId = prepared.organizationId,
            officeId = prepared.officeId,
            action = action,
            entityType = "DEED",
            entityId = prepared.id,
            previousVersion = (prepared.rowVersion - 1).coerceAtLeast(0L),
            newVersion = prepared.rowVersion,
            summary = "${action.enLabel}: Deed ${prepared.deedNumber} (${prepared.deedYear})",
            createdAtMillis = now
        )
        val pending = if (enableCloudSync) {
            PendingSyncEntity(
                deedId = prepared.id,
                ownerUid = ownerUid,
                operation = if (prepared.isDeleted) "TOMBSTONE" else "UPSERT",
                queuedAtMillis = now
            )
        } else {
            null
        }
        dao.saveDeedWithAuditAndSync(
            deed = prepared.toEntity(),
            audit = audit.toEntity(ownerUid = ownerUid),
            pendingSync = pending
        )
        return Result.success(SaveDeedOutcome.Saved(prepared))
    }

    suspend fun softDeleteDeed(deed: DeedRecord, actor: UserProfile): Result<SaveDeedOutcome> {
        val updated = deed.copy(
            status = DeedStatus.SOFT_DELETED,
            visibility = if (deed.visibility == DeedVisibility.PUBLIC) DeedVisibility.OFFICE_ONLY else deed.visibility,
            deletedAtMillis = System.currentTimeMillis()
        )
        return saveDeedLocallyAndTrySync(updated, actor, AuditAction.SOFT_DELETE)
    }

    suspend fun permanentlyDeleteWithTombstone(deed: DeedRecord, actor: UserProfile): Result<SaveDeedOutcome> {
        val now = System.currentTimeMillis()
        val tombstone = deed.copy(
            isDeleted = true,
            deletedAtMillis = now,
            deletedByDeviceId = deviceId,
            status = DeedStatus.SOFT_DELETED,
            updatedAtMillis = now
        )
        return saveDeedLocallyAndTrySync(tombstone, actor, AuditAction.SOFT_DELETE)
    }

    suspend fun restoreDeed(
        deed: DeedRecord,
        actor: UserProfile,
        targetStatus: DeedStatus = DeedStatus.APPROVED
    ): Result<SaveDeedOutcome> {
        val restoredVisibility = if (deed.visibility == DeedVisibility.PRIVATE_DRAFT) {
            DeedVisibility.PRIVATE_DRAFT
        } else if (targetStatus == DeedStatus.APPROVED) {
            DeedVisibility.PUBLIC
        } else {
            DeedVisibility.OFFICE_ONLY
        }
        val updated = deed.copy(
            status = targetStatus,
            visibility = restoredVisibility,
            isDeleted = false,
            deletedAtMillis = null,
            deletedByDeviceId = null
        )
        return saveDeedLocallyAndTrySync(updated, actor, AuditAction.RESTORE)
    }

    suspend fun resolveConflictKeepLocal(
        conflict: DeedConflict,
        actor: UserProfile
    ): Result<SaveDeedOutcome> {
        val rebased = conflict.localDeed.copy(
            ownerUid = ownerUid,
            rowVersion = maxOf(conflict.localDeed.rowVersion, conflict.serverDeed.rowVersion) + 1L
        )
        return saveDeedLocallyAndTrySync(rebased, actor, AuditAction.UPDATE)
    }

    suspend fun resolveConflictAcceptServer(conflict: DeedConflict) {
        val accepted = conflict.serverDeed.copy(
            ownerUid = ownerUid,
            syncState = SyncState.SYNCED
        )
        dao.upsertDeed(accepted.toEntity())
        dao.removePendingSyncForOwner(ownerUid, accepted.id)
    }

    suspend fun resolveConflictKeepBoth(
        conflict: DeedConflict,
        actor: UserProfile
    ): Result<SaveDeedOutcome> {
        // Keep remote record under original deed ID
        val acceptedRemote = conflict.serverDeed.copy(
            ownerUid = ownerUid,
            syncState = SyncState.SYNCED
        )
        dao.upsertDeed(acceptedRemote.toEntity())
        dao.removePendingSyncForOwner(ownerUid, acceptedRemote.id)

        // Save local version as a new cloned deed so neither edit is lost
        val duplicateLocal = conflict.localDeed.copy(
            id = UUID.randomUUID().toString(),
            ownerUid = ownerUid,
            deedNumber = "${conflict.localDeed.deedNumber} (Copy)",
            rowVersion = 1L,
            revisionId = UUID.randomUUID().toString(),
            syncState = SyncState.PENDING_UPLOAD
        )
        return saveDeedLocallyAndTrySync(duplicateLocal, actor, AuditAction.CREATE)
    }

    suspend fun migrateLegacyLocalDataToCurrentOwner() {
        if (ownerUid == LEGACY_LOCAL_OWNER_UID || ownerUid == GuestSandboxSeeder.GUEST_USER_ID) return
        dao.migrateLegacyOwnerData(
            sourceUid = LEGACY_LOCAL_OWNER_UID,
            targetUid = ownerUid,
            deviceId = deviceId
        )
    }

    suspend fun importDeedsIntoCurrentOwner(deeds: List<DeedRecord>, actor: UserProfile): Int {
        var count = 0
        for (deed in deeds) {
            val copy = deed.copy(
                id = UUID.randomUUID().toString(),
                ownerUid = ownerUid,
                createdBy = ownerUid,
                updatedBy = ownerUid,
                deviceId = deviceId,
                rowVersion = 1L,
                isDeleted = false,
                deletedAtMillis = null
            )
            saveDeedLocallyAndTrySync(copy, actor, AuditAction.CREATE)
            count++
        }
        return count
    }

    suspend fun deleteAllCurrentOwnerData() {
        dao.deleteAllDataForOwner(ownerUid)
    }

    suspend fun searchLocalIndexed(query: String, limit: Int = 50): List<DeedRecord> {
        val norm = SearchNormalizer.normalizeText(query)
        if (norm.isBlank()) {
            return dao.getActiveDeedsForOwnerOnce(ownerUid).map { it.toDomain() }.take(limit)
        }
        val compact = norm.replace(" ", "")
        val entities = dao.searchIndexedDeedsForOwner(
            ownerUid = ownerUid,
            normalizedQuery = compact,
            prefixQuery = "$compact%",
            containsQuery = "%$norm%",
            limit = limit
        )
        return searchStrategy.rank(entities.map { it.toDomain() }, query).map { it.deed }
    }
}
