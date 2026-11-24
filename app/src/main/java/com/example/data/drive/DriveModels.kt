package com.example.data.drive

import android.app.PendingIntent
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.DeedConflict
import com.example.domain.model.DeedRecord

object DriveSchemaConstants {
    const val CURRENT_SCHEMA_VERSION = 1
    const val ROOT_FOLDER_NAME = "Dolil Onusondhan Backup"
    const val ACCOUNTS_FOLDER_NAME = "accounts"
    const val DEVICES_FOLDER_NAME = "devices"
    const val BACKUPS_FOLDER_NAME = "backups"
    const val COMMITS_FOLDER_NAME = "commits"
    const val MANIFEST_FILE_NAME = "manifest.json"
    const val MIME_FOLDER = "application/vnd.google-apps.folder"
    const val MIME_JSON = "application/json"
}

data class DriveAccountInfo(
    val subjectId: String,
    val email: String = "",
    val displayName: String = ""
)

data class DriveFileMetadata(
    val id: String,
    val name: String,
    val parents: List<String> = emptyList(),
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val modifiedTimeUtc: String = "",
    val sha256Checksum: String? = null
)

data class DriveDeviceRecord(
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val lastSequence: Long,
    val lastCommitId: String? = null,
    val lastSeenUtc: String,
    val retired: Boolean = false,
    val retiredAtUtc: String? = null
)

data class DriveEncryptionMetadata(
    val algorithm: String = "AES-256-GCM",
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 600_000,
    val saltBase64: String,
    val ivBase64: String,
    val aadCanonical: String,
    val keyVersion: Int = 1
)

data class DriveBackupPayload(
    val schemaVersion: Int = DriveSchemaConstants.CURRENT_SCHEMA_VERSION,
    val accountNamespaceId: String,
    val driveSubjectId: String,
    val exportedAtUtc: String,
    val deeds: List<DeedRecord>,
    val auditLogs: List<AuditLogEntry>
)

data class DriveBackupEnvelope(
    val backupId: String,
    val sequenceNumber: Long,
    val schemaVersion: Int = DriveSchemaConstants.CURRENT_SCHEMA_VERSION,
    val timestampUtc: String,
    val deviceId: String,
    val accountNamespaceId: String,
    val driveSubjectId: String,
    val encrypted: Boolean,
    val encryptionMetadata: DriveEncryptionMetadata? = null,
    val payloadSha256: String,
    val payloadBase64: String
)

data class DriveCommitRecord(
    val commitId: String,
    val backupId: String,
    val sequenceNumber: Long,
    val parentCommitIds: List<String>,
    val deviceId: String,
    val deviceSequence: Long,
    val timestampUtc: String,
    val accountNamespaceId: String,
    val driveSubjectId: String,
    val payloadChecksum: String,
    val schemaVersion: Int = DriveSchemaConstants.CURRENT_SCHEMA_VERSION
)

data class DriveManifest(
    val schemaVersion: Int = DriveSchemaConstants.CURRENT_SCHEMA_VERSION,
    val accountNamespaceId: String,
    val driveSubjectId: String,
    val headCommitIds: List<String>,
    val latestCommitId: String?,
    val latestBackupId: String?,
    val latestSequence: Long,
    val rebuiltAtUtc: String,
    val knownDevices: List<DriveDeviceRecord> = emptyList()
)

data class DriveBackupSummary(
    val backupId: String,
    val commitId: String?,
    val sequenceNumber: Long,
    val timestampUtc: String,
    val deviceId: String,
    val encrypted: Boolean,
    val fileId: String,
    val payloadChecksum: String
)

data class PortableBackupBundle(
    val schemaVersion: Int = DriveSchemaConstants.CURRENT_SCHEMA_VERSION,
    val exportedAtUtc: String,
    val sourceLabel: String = "Dolil Onusondhan",
    val deeds: List<DeedRecord>,
    val auditLogs: List<AuditLogEntry>,
    val payloadSha256: String
)

sealed interface DriveAuthResult {
    data class Authorized(
        val accessToken: String,
        val accountInfo: DriveAccountInfo
    ) : DriveAuthResult

    data class NeedsResolution(
        val pendingIntent: PendingIntent
    ) : DriveAuthResult

    data class Cancelled(val reason: String = "User cancelled Drive authorization") : DriveAuthResult
    data class Error(val message: String, val isRevokedOrExpired: Boolean = false) : DriveAuthResult
}

sealed interface DriveSyncOutcome {
    data class Synced(
        val uploadedBackupId: String?,
        val commitId: String?,
        val mergedDeedCount: Int,
        val conflicts: List<DeedConflict> = emptyList(),
        val orphanedRecoveredCount: Int = 0
    ) : DriveSyncOutcome

    data class AuthorizationRequired(val message: String) : DriveSyncOutcome

    data class AccountMismatch(
        val expectedSubjectId: String,
        val expectedEmail: String,
        val actualSubjectId: String,
        val actualEmail: String
    ) : DriveSyncOutcome

    data class PassphraseRequired(val backupId: String? = null) : DriveSyncOutcome
    data class ConflictDetected(val conflicts: List<DeedConflict>) : DriveSyncOutcome
    data class Error(val message: String) : DriveSyncOutcome
}
