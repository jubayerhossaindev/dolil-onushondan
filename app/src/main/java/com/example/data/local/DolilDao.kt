package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DolilDao {

    @Query("SELECT * FROM deeds WHERE isDeleted = 0 ORDER BY updatedAtMillis DESC")
    fun observeAllDeeds(): Flow<List<DeedEntity>>

    @Query("SELECT * FROM deeds WHERE ownerUid = :ownerUid AND isDeleted = 0 ORDER BY updatedAtMillis DESC")
    fun observeDeedsForOwner(ownerUid: String): Flow<List<DeedEntity>>

    @Query("SELECT * FROM deeds WHERE ownerUid = :ownerUid AND status = 'DRAFT' AND isDeleted = 0 ORDER BY updatedAtMillis DESC")
    fun observeDraftsForOwner(ownerUid: String): Flow<List<DeedEntity>>

    @Query("SELECT * FROM deeds WHERE id = :deedId LIMIT 1")
    suspend fun getDeedById(deedId: String): DeedEntity?

    @Query("SELECT * FROM deeds WHERE id = :deedId AND ownerUid = :ownerUid LIMIT 1")
    suspend fun getDeedByIdForOwner(deedId: String, ownerUid: String): DeedEntity?

    @Query("SELECT * FROM deeds WHERE isDeleted = 0")
    suspend fun getAllDeedsOnce(): List<DeedEntity>

    @Query("SELECT * FROM deeds WHERE ownerUid = :ownerUid")
    suspend fun getAllDeedsIncludingTombstonesForOwnerOnce(ownerUid: String): List<DeedEntity>

    @Query("SELECT * FROM deeds WHERE ownerUid = :ownerUid AND isDeleted = 0")
    suspend fun getActiveDeedsForOwnerOnce(ownerUid: String): List<DeedEntity>

    @Query(
        """
        SELECT * FROM deeds
        WHERE isDeleted = 0
          AND status != 'SOFT_DELETED'
          AND (
            normalizedDeedNumber = :normalizedQuery
            OR normalizedDeedNumber LIKE :prefixQuery
            OR searchTokensJoined LIKE :containsQuery
          )
        ORDER BY registrationDateMillis DESC, id ASC
        LIMIT :limit
        """
    )
    suspend fun searchIndexedDeeds(
        normalizedQuery: String,
        prefixQuery: String,
        containsQuery: String,
        limit: Int = 100
    ): List<DeedEntity>

    @Query(
        """
        SELECT * FROM deeds
        WHERE ownerUid = :ownerUid
          AND isDeleted = 0
          AND status != 'SOFT_DELETED'
          AND (
            normalizedDeedNumber = :normalizedQuery
            OR normalizedDeedNumber LIKE :prefixQuery
            OR searchTokensJoined LIKE :containsQuery
          )
        ORDER BY registrationDateMillis DESC, id ASC
        LIMIT :limit
        """
    )
    suspend fun searchIndexedDeedsForOwner(
        ownerUid: String,
        normalizedQuery: String,
        prefixQuery: String,
        containsQuery: String,
        limit: Int = 100
    ): List<DeedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeed(deed: DeedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeeds(deeds: List<DeedEntity>)

    @Query("SELECT * FROM audit_logs WHERE entityId = :deedId ORDER BY createdAtMillis DESC")
    fun observeAuditLogsForDeed(deedId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE ownerUid = :ownerUid AND entityId = :deedId ORDER BY createdAtMillis DESC")
    fun observeAuditLogsForOwnerAndDeed(ownerUid: String, deedId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE ownerUid = :ownerUid ORDER BY createdAtMillis DESC")
    suspend fun getAuditLogsForOwnerOnce(ownerUid: String): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(entry: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(entries: List<AuditLogEntity>)

    @Query("SELECT * FROM pending_sync ORDER BY queuedAtMillis ASC")
    fun observePendingSync(): Flow<List<PendingSyncEntity>>

    @Query("SELECT * FROM pending_sync WHERE ownerUid = :ownerUid ORDER BY queuedAtMillis ASC")
    fun observePendingSyncForOwner(ownerUid: String): Flow<List<PendingSyncEntity>>

    @Query("SELECT * FROM pending_sync ORDER BY queuedAtMillis ASC")
    suspend fun getPendingSyncOnce(): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync WHERE ownerUid = :ownerUid ORDER BY queuedAtMillis ASC")
    suspend fun getPendingSyncForOwnerOnce(ownerUid: String): List<PendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueuePendingSync(item: PendingSyncEntity)

    @Query("DELETE FROM pending_sync WHERE deedId = :deedId")
    suspend fun removePendingSync(deedId: String)

    @Query("DELETE FROM pending_sync WHERE ownerUid = :ownerUid AND deedId = :deedId")
    suspend fun removePendingSyncForOwner(ownerUid: String, deedId: String)

    @Query("DELETE FROM pending_sync WHERE ownerUid = :ownerUid")
    suspend fun clearPendingSyncForOwner(ownerUid: String)

    @Query("SELECT COUNT(*) FROM deeds WHERE ownerUid = :legacyUid")
    suspend fun countDeedsByOwner(legacyUid: String = LEGACY_LOCAL_OWNER_UID): Int

    @Query("SELECT COUNT(*) FROM deeds WHERE ownerUid = :legacyUid")
    fun observeDeedCountByOwner(legacyUid: String = LEGACY_LOCAL_OWNER_UID): Flow<Int>

    @Query("UPDATE deeds SET ownerUid = :targetUid, createdBy = :targetUid, updatedBy = :targetUid, deviceId = :deviceId, syncState = 'PENDING_UPLOAD' WHERE ownerUid = :sourceUid")
    suspend fun reassignDeedsOwner(sourceUid: String, targetUid: String, deviceId: String)

    @Query("UPDATE audit_logs SET ownerUid = :targetUid, actorId = :targetUid WHERE ownerUid = :sourceUid")
    suspend fun reassignAuditLogsOwner(sourceUid: String, targetUid: String)

    @Query("UPDATE pending_sync SET ownerUid = :targetUid WHERE ownerUid = :sourceUid")
    suspend fun reassignPendingSyncOwner(sourceUid: String, targetUid: String)

    @Transaction
    suspend fun migrateLegacyOwnerData(
        sourceUid: String = LEGACY_LOCAL_OWNER_UID,
        targetUid: String,
        deviceId: String
    ) {
        reassignDeedsOwner(sourceUid, targetUid, deviceId)
        reassignAuditLogsOwner(sourceUid, targetUid)
        reassignPendingSyncOwner(sourceUid, targetUid)
    }

    @Query("DELETE FROM deeds WHERE ownerUid = :ownerUid")
    suspend fun deleteDeedsForOwner(ownerUid: String)

    @Query("DELETE FROM audit_logs WHERE ownerUid = :ownerUid")
    suspend fun deleteAuditLogsForOwner(ownerUid: String)

    @Query("DELETE FROM pending_sync WHERE ownerUid = :ownerUid")
    suspend fun deletePendingSyncForOwner(ownerUid: String)

    @Transaction
    suspend fun deleteAllDataForOwner(ownerUid: String) {
        deleteDeedsForOwner(ownerUid)
        deleteAuditLogsForOwner(ownerUid)
        deletePendingSyncForOwner(ownerUid)
    }

    @Query("DELETE FROM deeds WHERE ownerUid = :ownerUid AND isDeleted = 1 AND deletedAtMillis IS NOT NULL AND deletedAtMillis <= :cutoffMillis")
    suspend fun purgeTombstonesBefore(ownerUid: String, cutoffMillis: Long): Int

    @Transaction
    suspend fun saveDeedWithAuditAndSync(
        deed: DeedEntity,
        audit: AuditLogEntity?,
        pendingSync: PendingSyncEntity?
    ) {
        upsertDeed(deed)
        if (audit != null) {
            insertAuditLog(audit)
        }
        if (pendingSync != null) {
            enqueuePendingSync(pendingSync)
        }
    }
}
