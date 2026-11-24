package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.domain.model.AuditAction
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedType
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandParcel
import com.example.domain.model.PartyInfo
import com.example.domain.model.SyncState
import com.example.domain.search.SearchNormalizer
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

const val LEGACY_LOCAL_OWNER_UID = "__legacy_local__"

@Entity(
    tableName = "deeds",
    indices = [
        Index(value = ["normalizedDeedNumber"]),
        Index(value = ["deedYear"]),
        Index(value = ["officeId", "deedYear", "normalizedDeedNumber"]),
        Index(value = ["status"]),
        Index(value = ["visibility"]),
        Index(value = ["registrationDateMillis"]),
        Index(value = ["mouzaName"]),
        Index(value = ["updatedAtMillis"]),
        Index(value = ["ownerUid"])
    ]
)
data class DeedEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = LEGACY_LOCAL_OWNER_UID)
    val ownerUid: String = LEGACY_LOCAL_OWNER_UID,
    @ColumnInfo(defaultValue = "")
    val deviceId: String = "",
    @ColumnInfo(defaultValue = "")
    val revisionId: String = "",
    val createdBy: String,
    val updatedBy: String,
    val organizationId: String,
    val officeId: String,
    val officeName: String,
    val deedType: String,
    val deedNumber: String,
    val normalizedDeedNumber: String,
    val deedYear: Int,
    val registrationDateMillis: Long,
    val districtName: String,
    val upazilaName: String,
    val unionName: String,
    val mouzaName: String,
    val jlNumber: String,
    val status: String,
    val visibility: String,
    val rowVersion: Long,
    val primaryGrantorName: String,
    val primaryGranteeName: String,
    val totalAreaDecimalText: String,
    val grantorsJson: String,
    val granteesJson: String,
    val parcelsJson: String,
    val searchTokensJoined: String,
    val notes: String,
    val syncState: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long?,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedByDeviceId: String? = null
)

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["entityId"]),
        Index(value = ["createdAtMillis"]),
        Index(value = ["ownerUid"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = LEGACY_LOCAL_OWNER_UID)
    val ownerUid: String = LEGACY_LOCAL_OWNER_UID,
    val actorId: String,
    val actorName: String,
    val organizationId: String,
    val officeId: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val previousVersion: Long,
    val newVersion: Long,
    val summary: String,
    val createdAtMillis: Long
)

@Entity(
    tableName = "pending_sync",
    indices = [
        Index(value = ["ownerUid"])
    ]
)
data class PendingSyncEntity(
    @PrimaryKey val deedId: String,
    @ColumnInfo(defaultValue = LEGACY_LOCAL_OWNER_UID)
    val ownerUid: String = LEGACY_LOCAL_OWNER_UID,
    val operation: String, // UPSERT, SOFT_DELETE, RESTORE, TOMBSTONE
    val queuedAtMillis: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
)

object DeedRoomJsonCodec {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val partyListType = Types.newParameterizedType(List::class.java, PartyInfo::class.java)
    private val parcelListType = Types.newParameterizedType(List::class.java, LandParcel::class.java)
    private val partyAdapter = moshi.adapter<List<PartyInfo>>(partyListType)
    private val parcelAdapter = moshi.adapter<List<LandParcel>>(parcelListType)

    fun encodeParties(parties: List<PartyInfo>): String = partyAdapter.toJson(parties)
    fun decodeParties(json: String): List<PartyInfo> =
        runCatching { partyAdapter.fromJson(json) }.getOrNull().orEmpty()

    fun encodeParcels(parcels: List<LandParcel>): String = parcelAdapter.toJson(parcels)
    fun decodeParcels(json: String): List<LandParcel> =
        runCatching { parcelAdapter.fromJson(json) }.getOrNull().orEmpty()
}

class RoomConverters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String = value?.joinToString("\n").orEmpty()

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
}

fun DeedRecord.toEntity(): DeedEntity {
    val normNumber = SearchNormalizer.normalizeCode(deedNumber)
    val tokens = SearchNormalizer.buildSearchTokens(this).joinToString(" | ")
    val resolvedOwner = ownerUid.ifBlank { createdBy.ifBlank { LEGACY_LOCAL_OWNER_UID } }
    return DeedEntity(
        id = id,
        ownerUid = resolvedOwner,
        deviceId = deviceId,
        revisionId = revisionId.ifBlank { UUID.randomUUID().toString() },
        createdBy = createdBy.ifBlank { resolvedOwner },
        updatedBy = updatedBy.ifBlank { resolvedOwner },
        organizationId = organizationId,
        officeId = officeId,
        officeName = officeName,
        deedType = deedType.firestoreValue,
        deedNumber = deedNumber,
        normalizedDeedNumber = normNumber,
        deedYear = deedYear,
        registrationDateMillis = registrationDateMillis,
        districtName = districtName,
        upazilaName = upazilaName,
        unionName = unionName,
        mouzaName = mouzaName,
        jlNumber = jlNumber,
        status = status.firestoreValue,
        visibility = visibility.firestoreValue,
        rowVersion = rowVersion,
        primaryGrantorName = primaryGrantorName,
        primaryGranteeName = primaryGranteeName,
        totalAreaDecimalText = totalAreaDecimalText,
        grantorsJson = DeedRoomJsonCodec.encodeParties(grantors),
        granteesJson = DeedRoomJsonCodec.encodeParties(grantees),
        parcelsJson = DeedRoomJsonCodec.encodeParcels(parcels),
        searchTokensJoined = tokens,
        notes = notes,
        syncState = syncState.name,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        deletedAtMillis = deletedAtMillis,
        isDeleted = isDeleted,
        deletedByDeviceId = deletedByDeviceId
    )
}

fun DeedEntity.toDomain(): DeedRecord = DeedRecord(
    id = id,
    ownerUid = ownerUid,
    deviceId = deviceId,
    revisionId = revisionId.ifBlank { id },
    createdBy = createdBy,
    updatedBy = updatedBy,
    organizationId = organizationId,
    officeId = officeId,
    officeName = officeName,
    deedType = DeedType.fromRaw(deedType),
    deedNumber = deedNumber,
    normalizedDeedNumber = normalizedDeedNumber,
    deedYear = deedYear,
    registrationDateMillis = registrationDateMillis,
    districtName = districtName,
    upazilaName = upazilaName,
    unionName = unionName,
    mouzaName = mouzaName,
    jlNumber = jlNumber,
    status = DeedStatus.fromRaw(status),
    visibility = DeedVisibility.fromRaw(visibility),
    rowVersion = rowVersion,
    grantors = DeedRoomJsonCodec.decodeParties(grantorsJson).ifEmpty { listOf(PartyInfo()) },
    grantees = DeedRoomJsonCodec.decodeParties(granteesJson).ifEmpty { listOf(PartyInfo()) },
    parcels = DeedRoomJsonCodec.decodeParcels(parcelsJson).ifEmpty { listOf(LandParcel()) },
    notes = notes,
    syncState = enumValues<SyncState>().firstOrNull { it.name == syncState } ?: SyncState.SYNCED,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    deletedAtMillis = deletedAtMillis,
    isDeleted = isDeleted,
    deletedByDeviceId = deletedByDeviceId
)

fun AuditLogEntry.toEntity(ownerUid: String = actorId.ifBlank { LEGACY_LOCAL_OWNER_UID }): AuditLogEntity = AuditLogEntity(
    id = id,
    ownerUid = ownerUid,
    actorId = actorId,
    actorName = actorName,
    organizationId = organizationId,
    officeId = officeId,
    action = action.code,
    entityType = entityType,
    entityId = entityId,
    previousVersion = previousVersion,
    newVersion = newVersion,
    summary = summary,
    createdAtMillis = createdAtMillis
)

fun AuditLogEntity.toDomain(): AuditLogEntry = AuditLogEntry(
    id = id,
    actorId = actorId,
    actorName = actorName,
    organizationId = organizationId,
    officeId = officeId,
    action = AuditAction.fromRaw(action),
    entityType = entityType,
    entityId = entityId,
    previousVersion = previousVersion,
    newVersion = newVersion,
    summary = summary,
    createdAtMillis = createdAtMillis
)
