package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

enum class DriveSyncStatus(val code: String, val bnLabel: String, val enLabel: String) {
    IDLE("IDLE", "প্রস্তুত", "Idle"),
    PENDING("PENDING", "অপেক্ষমাণ", "Pending"),
    SYNCING("SYNCING", "সিঙ্ক হচ্ছে...", "Syncing..."),
    SUCCESSFUL("SUCCESSFUL", "সিঙ্ক সম্পন্ন", "Synced"),
    FAILED("FAILED", "সিঙ্ক ব্যর্থ", "Sync Failed"),
    AUTHORIZATION_REQUIRED("AUTHORIZATION_REQUIRED", "পুনরায় অনুমোদন প্রয়োজন", "Authorization Required");

    companion object {
        fun fromCode(code: String?): DriveSyncStatus =
            entries.firstOrNull { it.code == code } ?: IDLE
    }
}

data class DriveFolderIds(
    val rootFolderId: String = "",
    val accountsFolderId: String = "",
    val namespaceFolderId: String = "",
    val devicesFolderId: String = "",
    val backupsFolderId: String = "",
    val commitsFolderId: String = "",
    val manifestFileId: String = ""
)

data class DriveLinkState(
    val firebaseUid: String = "",
    val accountNamespaceId: String = "",
    val driveSubjectId: String = "",
    val driveEmail: String = "",
    val driveDisplayName: String = "",
    val folderIds: DriveFolderIds = DriveFolderIds(),
    val lastSyncedCommitIds: List<String> = emptyList(),
    val lastVerifiedBackupId: String? = null,
    val lastVerifiedSequence: Long = 0L,
    val lastSyncUtcMillis: Long = 0L,
    val syncStatus: DriveSyncStatus = DriveSyncStatus.IDLE,
    val lastErrorMessage: String? = null,
    val autoSyncEnabled: Boolean = true,
    val encryptionEnabled: Boolean = false
) {
    val isConnected: Boolean
        get() = driveSubjectId.isNotBlank() && accountNamespaceId.isNotBlank()
}

data class LocalAccountProfile(
    val firebaseUid: String = "",
    val fullName: String = "",
    val phone: String = ""
)

data class ConflictAuditRecord(
    val id: String = UUID.randomUUID().toString(),
    val firebaseUid: String = "",
    val deedId: String = "",
    val deedNumber: String = "",
    val resolution: String = "",
    val localRevisionId: String = "",
    val remoteRevisionId: String = "",
    val remoteDeviceId: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

object NamespaceHelper {
    private const val NAMESPACE_PREFIX = "dolil-onsundondhan-backup-v1:"

    fun computeAccountNamespaceId(firebaseUid: String): String {
        val input = "$NAMESPACE_PREFIX${firebaseUid.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object SessionPassphraseVault {
    private val passphrases = mutableMapOf<String, CharArray>()

    @Synchronized
    fun setPassphrase(firebaseUid: String, passphrase: String) {
        clear(firebaseUid)
        if (passphrase.isNotEmpty()) {
            passphrases[firebaseUid] = passphrase.toCharArray()
        }
    }

    @Synchronized
    fun getPassphrase(firebaseUid: String): CharArray? =
        passphrases[firebaseUid]?.copyOf()

    @Synchronized
    fun hasPassphrase(firebaseUid: String): Boolean =
        passphrases[firebaseUid]?.isNotEmpty() == true

    @Synchronized
    fun clear(firebaseUid: String) {
        passphrases.remove(firebaseUid)?.fill('\u0000')
    }

    @Synchronized
    fun clearAll() {
        passphrases.values.forEach { it.fill('\u0000') }
        passphrases.clear()
    }
}

class DeviceIdentityStore(
    context: Context,
    prefsName: String = PREFS_NAME
) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        prefsName,
        Context.MODE_PRIVATE
    )

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val linkAdapter = moshi.adapter(DriveLinkState::class.java)
    private val profileAdapter = moshi.adapter(LocalAccountProfile::class.java)
    private val conflictListType = Types.newParameterizedType(List::class.java, ConflictAuditRecord::class.java)
    private val conflictListAdapter = moshi.adapter<List<ConflictAuditRecord>>(conflictListType)

    private val linkFlows = mutableMapOf<String, MutableStateFlow<DriveLinkState>>()

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun getDeviceLabel(): String {
        val saved = prefs.getString(KEY_DEVICE_LABEL, null)
        if (!saved.isNullOrBlank()) return saved
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val label = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "Android Device" }
        prefs.edit().putString(KEY_DEVICE_LABEL, label).apply()
        return label
    }

    @Synchronized
    fun nextDeviceSequence(firebaseUid: String): Long {
        val key = "$KEY_SEQ_PREFIX$firebaseUid"
        val next = prefs.getLong(key, 0L) + 1L
        prefs.edit().putLong(key, next).apply()
        return next
    }

    fun getCurrentDeviceSequence(firebaseUid: String): Long =
        prefs.getLong("$KEY_SEQ_PREFIX$firebaseUid", 0L)

    @Synchronized
    fun observeDriveLink(firebaseUid: String): Flow<DriveLinkState> {
        val flow = linkFlows.getOrPut(firebaseUid) {
            MutableStateFlow(getDriveLink(firebaseUid))
        }
        return flow.asStateFlow()
    }

    fun getDriveLink(firebaseUid: String): DriveLinkState {
        if (firebaseUid.isBlank()) return DriveLinkState()
        val raw = prefs.getString("$KEY_DRIVE_LINK_PREFIX$firebaseUid", null)
        val parsed = raw?.let { runCatching { linkAdapter.fromJson(it) }.getOrNull() }
        val ns = NamespaceHelper.computeAccountNamespaceId(firebaseUid)
        return (parsed ?: DriveLinkState(firebaseUid = firebaseUid, accountNamespaceId = ns))
            .copy(firebaseUid = firebaseUid, accountNamespaceId = ns)
    }

    @Synchronized
    fun saveDriveLink(state: DriveLinkState) {
        val uid = state.firebaseUid
        if (uid.isBlank()) return
        val normalized = state.copy(accountNamespaceId = NamespaceHelper.computeAccountNamespaceId(uid))
        prefs.edit().putString("$KEY_DRIVE_LINK_PREFIX$uid", linkAdapter.toJson(normalized)).apply()
        linkFlows.getOrPut(uid) { MutableStateFlow(normalized) }.value = normalized
    }

    @Synchronized
    fun updateDriveSyncStatus(
        firebaseUid: String,
        status: DriveSyncStatus,
        errorMessage: String? = null
    ) {
        val current = getDriveLink(firebaseUid)
        saveDriveLink(current.copy(syncStatus = status, lastErrorMessage = errorMessage))
    }

    @Synchronized
    fun disconnectDriveLocally(firebaseUid: String) {
        if (firebaseUid.isBlank()) return
        val current = getDriveLink(firebaseUid)
        val cleared = DriveLinkState(
            firebaseUid = firebaseUid,
            accountNamespaceId = NamespaceHelper.computeAccountNamespaceId(firebaseUid),
            autoSyncEnabled = current.autoSyncEnabled,
            encryptionEnabled = false
        )
        SessionPassphraseVault.clear(firebaseUid)
        saveDriveLink(cleared)
    }

    fun getAllLinkedUidsForDriveSubject(driveSubjectId: String): List<String> {
        if (driveSubjectId.isBlank()) return emptyList()
        return prefs.all.keys
            .filter { it.startsWith(KEY_DRIVE_LINK_PREFIX) }
            .mapNotNull { key ->
                val uid = key.removePrefix(KEY_DRIVE_LINK_PREFIX)
                val link = getDriveLink(uid)
                if (link.driveSubjectId == driveSubjectId) uid else null
            }
    }

    @Synchronized
    fun saveLocalProfile(profile: LocalAccountProfile) {
        if (profile.firebaseUid.isBlank()) return
        prefs.edit()
            .putString("$KEY_PROFILE_PREFIX${profile.firebaseUid}", profileAdapter.toJson(profile))
            .apply()
    }

    fun getLocalProfile(firebaseUid: String): LocalAccountProfile? {
        if (firebaseUid.isBlank()) return null
        val raw = prefs.getString("$KEY_PROFILE_PREFIX$firebaseUid", null) ?: return null
        return runCatching { profileAdapter.fromJson(raw) }.getOrNull()
    }

    @Synchronized
    fun deleteAccountLocalMetadata(firebaseUid: String) {
        if (firebaseUid.isBlank()) return
        SessionPassphraseVault.clear(firebaseUid)
        prefs.edit()
            .remove("$KEY_DRIVE_LINK_PREFIX$firebaseUid")
            .remove("$KEY_PROFILE_PREFIX$firebaseUid")
            .remove("$KEY_SEQ_PREFIX$firebaseUid")
            .remove("$KEY_CONFLICT_AUDIT_PREFIX$firebaseUid")
            .apply()
        linkFlows[firebaseUid]?.value = DriveLinkState(
            firebaseUid = firebaseUid,
            accountNamespaceId = NamespaceHelper.computeAccountNamespaceId(firebaseUid)
        )
    }

    @Synchronized
    fun appendConflictAudit(record: ConflictAuditRecord) {
        if (record.firebaseUid.isBlank()) return
        val existing = getConflictAudits(record.firebaseUid).toMutableList()
        existing.add(0, record)
        val trimmed = existing.take(100)
        prefs.edit()
            .putString("$KEY_CONFLICT_AUDIT_PREFIX${record.firebaseUid}", conflictListAdapter.toJson(trimmed))
            .apply()
    }

    fun getConflictAudits(firebaseUid: String): List<ConflictAuditRecord> {
        val raw = prefs.getString("$KEY_CONFLICT_AUDIT_PREFIX$firebaseUid", null) ?: return emptyList()
        return runCatching { conflictListAdapter.fromJson(raw) }.getOrNull().orEmpty()
    }

    private companion object {
        const val PREFS_NAME = "dolil_device_identity"
        const val KEY_DEVICE_ID = "install_device_id"
        const val KEY_DEVICE_LABEL = "install_device_label"
        const val KEY_SEQ_PREFIX = "device_seq_"
        const val KEY_DRIVE_LINK_PREFIX = "drive_link_"
        const val KEY_PROFILE_PREFIX = "local_profile_"
        const val KEY_CONFLICT_AUDIT_PREFIX = "conflict_audit_"
    }
}
