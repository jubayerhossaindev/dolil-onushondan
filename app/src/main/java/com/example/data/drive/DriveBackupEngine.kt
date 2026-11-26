package com.example.data.drive

import com.example.core.config.AppVersionInfo
import com.example.data.local.DeviceIdentityStore
import com.example.data.local.DolilDao
import com.example.data.local.DriveFolderIds
import com.example.data.local.DriveLinkState
import com.example.data.local.DriveSyncStatus
import com.example.data.local.NamespaceHelper
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.AuditAction
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.DeedConflict
import com.example.domain.model.DeedRecord
import com.example.domain.model.SyncState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class DriveBackupEngine(
    private val driveApi: DriveRemoteStorage,
    private val dao: DolilDao,
    private val identityStore: DeviceIdentityStore,
    private val crypto: DriveCryptoEngine = DriveCryptoEngine()
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val payloadAdapter = moshi.adapter(DriveBackupPayload::class.java)
    private val envelopeAdapter = moshi.adapter(DriveBackupEnvelope::class.java)
    private val commitAdapter = moshi.adapter(DriveCommitRecord::class.java)
    private val deviceAdapter = moshi.adapter(DriveDeviceRecord::class.java)
    private val manifestAdapter = moshi.adapter(DriveManifest::class.java)
    private val portableAdapter = moshi.adapter(PortableBackupBundle::class.java)

    suspend fun syncNow(
        accessToken: String,
        accountInfo: DriveAccountInfo,
        firebaseUid: String,
        passphrase: CharArray?,
        forceUploadSnapshot: Boolean = false
    ): DriveSyncOutcome = withContext(Dispatchers.IO) {
        val currentLink = identityStore.getDriveLink(firebaseUid)
        val validationError = validatePreSyncIdentity(
            firebaseUid = firebaseUid,
            accountInfo = accountInfo,
            linkState = currentLink
        )
        if (validationError != null) {
            return@withContext validationError
        }

        val namespaceId = NamespaceHelper.computeAccountNamespaceId(firebaseUid)
        val deviceId = identityStore.getOrCreateDeviceId()

        identityStore.updateDriveSyncStatus(firebaseUid, DriveSyncStatus.SYNCING, null)

        try {
            val folders = ensureFolderHierarchy(
                accessToken = accessToken,
                firebaseUid = firebaseUid,
                existingIds = currentLink.folderIds
            )
            val linkWithFolders = currentLink.copy(
                firebaseUid = firebaseUid,
                accountNamespaceId = namespaceId,
                driveSubjectId = accountInfo.subjectId,
                driveEmail = accountInfo.email,
                driveDisplayName = accountInfo.displayName,
                folderIds = folders
            )
            identityStore.saveDriveLink(linkWithFolders)

            // 1. Read commits, backups, and devices (all paginated inside listFilesInFolder)
            val deviceFiles = driveApi.listFilesInFolder(accessToken, folders.devicesFolderId)
            val commitFiles = driveApi.listFilesInFolder(accessToken, folders.commitsFolderId)
            val backupFiles = driveApi.listFilesInFolder(accessToken, folders.backupsFolderId)

            val commits = mutableListOf<DriveCommitRecord>()
            for (file in commitFiles.filter { it.name.endsWith(".json") }) {
                val parsed = runCatching {
                    val raw = driveApi.downloadFileBytes(accessToken, file.id).toString(Charsets.UTF_8)
                    commitAdapter.fromJson(raw)
                }.getOrNull()
                if (parsed != null &&
                    parsed.accountNamespaceId == namespaceId &&
                    parsed.driveSubjectId == accountInfo.subjectId
                ) {
                    commits.add(parsed)
                }
            }

            // 2. Recover any verified orphaned backups (e.g., interrupted after backup upload, before commit write)
            val committedBackupIds = commits.map { it.backupId }.toSet()
            var orphanedRecovered = 0
            val backupMetaByBackupId = mutableMapOf<String, DriveFileMetadata>()
            for (bFile in backupFiles.filter { it.name.startsWith("backup-") && it.name.endsWith(".json") }) {
                val backupKey = bFile.name.removeSuffix(".json")
                backupMetaByBackupId[backupKey] = bFile
            }

            val currentHeadsBeforeRecovery = computeHeadCommitIds(commits)
            for ((backupKey, bFile) in backupMetaByBackupId) {
                if (backupKey !in committedBackupIds) {
                    val envelope = runCatching {
                        val raw = driveApi.downloadFileBytes(accessToken, bFile.id).toString(Charsets.UTF_8)
                        envelopeAdapter.fromJson(raw)
                    }.getOrNull()
                    if (envelope != null &&
                        envelope.backupId == backupKey &&
                        envelope.accountNamespaceId == namespaceId &&
                        envelope.driveSubjectId == accountInfo.subjectId &&
                        envelope.payloadBase64.isNotBlank() &&
                        envelope.payloadSha256.isNotBlank()
                    ) {
                        val canTrustEnvelope = if (!envelope.encrypted) {
                            crypto.unsealPayload(envelope, null).isSuccess
                        } else {
                            envelope.encryptionMetadata != null
                        }
                        if (canTrustEnvelope) {
                            val recoveryCommit = DriveCommitRecord(
                                commitId = "commit-${utcTimestampCompact()}-${UUID.randomUUID()}",
                                backupId = envelope.backupId,
                                sequenceNumber = envelope.sequenceNumber,
                                parentCommitIds = currentHeadsBeforeRecovery,
                                deviceId = envelope.deviceId,
                                deviceSequence = envelope.sequenceNumber,
                                timestampUtc = nowIsoUtc(),
                                accountNamespaceId = namespaceId,
                                driveSubjectId = accountInfo.subjectId,
                                payloadChecksum = envelope.payloadSha256
                            )
                            val commitBytes = commitAdapter.toJson(recoveryCommit).toByteArray(Charsets.UTF_8)
                            val genId = runCatching { driveApi.generateFileIds(accessToken, 1).firstOrNull() }.getOrNull()
                            driveApi.uploadJsonResumable(
                                accessToken = accessToken,
                                preGeneratedOrExistingId = genId,
                                fileName = "${recoveryCommit.commitId}.json",
                                parentFolderId = folders.commitsFolderId,
                                jsonBytes = commitBytes,
                                isUpdateExisting = false
                            )
                            commits.add(recoveryCommit)
                            orphanedRecovered++
                        }
                    }
                }
            }

            // 3. Identify unseen remote head commits and merge their backups
            val remoteHeads = computeHeadCommitIds(commits)
            val unseenHeadCommits = commits
                .filter { it.commitId in remoteHeads && it.commitId !in linkWithFolders.lastSyncedCommitIds }
                .sortedBy { it.sequenceNumber }

            val downloadedPayloads = mutableListOf<Pair<DriveCommitRecord, DriveBackupPayload>>()
            for (headCommit in unseenHeadCommits) {
                val backupFile = backupMetaByBackupId[headCommit.backupId]
                    ?: backupFiles.firstOrNull { it.name == "${headCommit.backupId}.json" }
                    ?: continue
                val envelopeRaw = driveApi.downloadFileBytes(accessToken, backupFile.id).toString(Charsets.UTF_8)
                val envelope = envelopeAdapter.fromJson(envelopeRaw)
                    ?: throw IOException("Corrupt backup envelope ${headCommit.backupId}")

                if (envelope.accountNamespaceId != namespaceId || envelope.driveSubjectId != accountInfo.subjectId) {
                    throw SecurityException("Remote backup repository identity mismatch for ${headCommit.backupId}")
                }
                if (envelope.payloadSha256 != headCommit.payloadChecksum) {
                    throw SecurityException("Commit checksum mismatch for ${headCommit.backupId}")
                }

                val plaintextResult = crypto.unsealPayload(envelope, passphrase)
                val plaintext = plaintextResult.getOrElse { err ->
                    if (err is PassphraseMissingException) {
                        identityStore.updateDriveSyncStatus(
                            firebaseUid,
                            DriveSyncStatus.FAILED,
                            "Encryption passphrase required to decrypt remote backup ${envelope.backupId}"
                        )
                        return@withContext DriveSyncOutcome.PassphraseRequired(envelope.backupId)
                    }
                    throw err
                }
                val payload = payloadAdapter.fromJson(plaintext.toString(Charsets.UTF_8))
                    ?: throw IOException("Empty backup payload JSON")

                if (payload.accountNamespaceId != namespaceId || payload.driveSubjectId != accountInfo.subjectId) {
                    throw SecurityException("Decrypted payload repository identity mismatch")
                }
                downloadedPayloads.add(headCommit to payload)
            }

            val detectedConflicts = mutableListOf<DeedConflict>()
            var mergedRemoteCount = 0
            for ((commit, payload) in downloadedPayloads) {
                val mergeRes = mergeRemotePayloadIntoLocal(
                    firebaseUid = firebaseUid,
                    localDeviceId = deviceId,
                    lastSyncUtcMillis = linkWithFolders.lastSyncUtcMillis,
                    commit = commit,
                    payload = payload
                )
                mergedRemoteCount += mergeRes.mergedCount
                detectedConflicts.addAll(mergeRes.conflicts)
            }

            // 4. Upload local snapshot if local pending changes exist, forced, or remote has no commits yet
            val pendingLocal = dao.getPendingSyncForOwnerOnce(firebaseUid)
            val allLocalDeeds = dao.getAllDeedsIncludingTombstonesForOwnerOnce(firebaseUid).map { it.toDomain() }
            val hasUnresolvedConflicts = detectedConflicts.isNotEmpty() ||
                allLocalDeeds.any { it.syncState == SyncState.CONFLICT }

            val needsUpload = !hasUnresolvedConflicts && (
                forceUploadSnapshot ||
                    pendingLocal.isNotEmpty() ||
                    commits.isEmpty() ||
                    unseenHeadCommits.size > 1 // create merge commit when combining concurrent branches
                )

            var uploadedBackupId: String? = null
            var uploadedCommitId: String? = null
            var finalHeads = computeHeadCommitIds(commits)
            var latestSeq = (commits.maxOfOrNull { it.sequenceNumber } ?: 0L)
                .coerceAtLeast(linkWithFolders.lastVerifiedSequence)

            if (needsUpload && (allLocalDeeds.isNotEmpty() || commits.isEmpty() || forceUploadSnapshot)) {
                val allAuditLogs = dao.getAuditLogsForOwnerOnce(firebaseUid).map { it.toDomain() }
                val deviceSeq = identityStore.nextDeviceSequence(firebaseUid)
                val nextGlobalSeq = latestSeq + 1L
                val backupId = "backup-${utcTimestampCompact()}-${UUID.randomUUID()}"
                val commitId = "commit-${utcTimestampCompact()}-${UUID.randomUUID()}"
                val nowIso = nowIsoUtc()

                val payload = DriveBackupPayload(
                    accountNamespaceId = namespaceId,
                    driveSubjectId = accountInfo.subjectId,
                    exportedAtUtc = nowIso,
                    deeds = allLocalDeeds,
                    auditLogs = allAuditLogs
                )
                val plaintextBytes = payloadAdapter.toJson(payload).toByteArray(Charsets.UTF_8)
                val activePassphrase = if (linkWithFolders.encryptionEnabled) passphrase else null
                if (linkWithFolders.encryptionEnabled && (activePassphrase == null || activePassphrase.isEmpty())) {
                    identityStore.updateDriveSyncStatus(
                        firebaseUid,
                        DriveSyncStatus.FAILED,
                        "Passphrase required to upload encrypted backup"
                    )
                    return@withContext DriveSyncOutcome.PassphraseRequired(null)
                }

                val envelope = crypto.sealPayload(
                    plaintextBytes = plaintextBytes,
                    backupId = backupId,
                    sequenceNumber = nextGlobalSeq,
                    timestampUtc = nowIso,
                    deviceId = deviceId,
                    accountNamespaceId = namespaceId,
                    driveSubjectId = accountInfo.subjectId,
                    schemaVersion = DriveSchemaConstants.CURRENT_SCHEMA_VERSION,
                    passphrase = activePassphrase
                )
                val envelopeBytes = envelopeAdapter.toJson(envelope).toByteArray(Charsets.UTF_8)
                val envelopeSha256 = crypto.sha256Hex(envelopeBytes)

                val preIds = runCatching { driveApi.generateFileIds(accessToken, 2) }.getOrDefault(emptyList())
                val backupDriveId = preIds.getOrNull(0)
                val commitDriveId = preIds.getOrNull(1)

                val uploadedBackupMeta = driveApi.uploadJsonResumable(
                    accessToken = accessToken,
                    preGeneratedOrExistingId = backupDriveId,
                    fileName = "$backupId.json",
                    parentFolderId = folders.backupsFolderId,
                    jsonBytes = envelopeBytes,
                    isUpdateExisting = false
                )

                // Verify upload size & checksum integrity before committing!
                val verifiedMeta = driveApi.getFileMetadata(accessToken, uploadedBackupMeta.id)
                    ?: uploadedBackupMeta
                if (verifiedMeta.sizeBytes > 0L && verifiedMeta.sizeBytes != envelopeBytes.size.toLong()) {
                    throw IOException("Uploaded backup size verification failed (${verifiedMeta.sizeBytes} != ${envelopeBytes.size})")
                }
                if (!verifiedMeta.sha256Checksum.isNullOrBlank() &&
                    !verifiedMeta.sha256Checksum.equals(envelopeSha256, ignoreCase = true)
                ) {
                    throw IOException("Uploaded backup SHA-256 verification failed")
                }

                val commitRecord = DriveCommitRecord(
                    commitId = commitId,
                    backupId = backupId,
                    sequenceNumber = nextGlobalSeq,
                    parentCommitIds = finalHeads,
                    deviceId = deviceId,
                    deviceSequence = deviceSeq,
                    timestampUtc = nowIso,
                    accountNamespaceId = namespaceId,
                    driveSubjectId = accountInfo.subjectId,
                    payloadChecksum = envelope.payloadSha256
                )
                val commitBytes = commitAdapter.toJson(commitRecord).toByteArray(Charsets.UTF_8)
                driveApi.uploadJsonResumable(
                    accessToken = accessToken,
                    preGeneratedOrExistingId = commitDriveId,
                    fileName = "$commitId.json",
                    parentFolderId = folders.commitsFolderId,
                    jsonBytes = commitBytes,
                    isUpdateExisting = false
                )

                commits.add(commitRecord)
                uploadedBackupId = backupId
                uploadedCommitId = commitId
                finalHeads = computeHeadCommitIds(commits)
                latestSeq = nextGlobalSeq

                // Mark non-conflicting deeds synced and clear pending queue
                val conflictIds = detectedConflicts.map { it.deedId }.toSet()
                val syncedDeeds = allLocalDeeds
                    .filter { it.id !in conflictIds && it.syncState != SyncState.CONFLICT }
                    .map { it.copy(syncState = SyncState.SYNCED).toEntity() }
                if (syncedDeeds.isNotEmpty()) {
                    dao.upsertDeeds(syncedDeeds)
                }
                for (item in pendingLocal) {
                    if (item.deedId !in conflictIds) {
                        dao.removePendingSyncForOwner(firebaseUid, item.deedId)
                    }
                }
            }

            // 5. Upsert current device checkpoint
            val remoteDevices = loadDeviceRecords(accessToken, deviceFiles).toMutableList()
            val myDeviceRecord = DriveDeviceRecord(
                deviceId = deviceId,
                deviceName = identityStore.getDeviceLabel(),
                appVersion = AppVersionInfo.formattedVersion,
                lastSequence = latestSeq,
                lastCommitId = finalHeads.lastOrNull(),
                lastSeenUtc = nowIsoUtc(),
                retired = false
            )
            val existingDeviceFile = deviceFiles.firstOrNull { it.name == "device-$deviceId.json" }
            val deviceBytes = deviceAdapter.toJson(myDeviceRecord).toByteArray(Charsets.UTF_8)
            driveApi.uploadJsonResumable(
                accessToken = accessToken,
                preGeneratedOrExistingId = existingDeviceFile?.id,
                fileName = "device-$deviceId.json",
                parentFolderId = folders.devicesFolderId,
                jsonBytes = deviceBytes,
                isUpdateExisting = existingDeviceFile != null
            )
            remoteDevices.removeAll { it.deviceId == deviceId }
            remoteDevices.add(myDeviceRecord)

            // 6. Rebuild manifest.json as derived index
            val manifest = DriveManifest(
                accountNamespaceId = namespaceId,
                driveSubjectId = accountInfo.subjectId,
                headCommitIds = finalHeads,
                latestCommitId = commits.maxByOrNull { it.sequenceNumber }?.commitId,
                latestBackupId = commits.maxByOrNull { it.sequenceNumber }?.backupId,
                latestSequence = latestSeq,
                rebuiltAtUtc = nowIsoUtc(),
                knownDevices = remoteDevices.sortedBy { it.deviceName }
            )
            val manifestBytes = manifestAdapter.toJson(manifest).toByteArray(Charsets.UTF_8)
            val existingManifestId = folders.manifestFileId.takeIf { it.isNotBlank() }
                ?.takeIf { driveApi.getFileMetadata(accessToken, it) != null }
                ?: driveApi.listFilesInFolder(
                    accessToken = accessToken,
                    parentFolderId = folders.namespaceFolderId,
                    nameEquals = DriveSchemaConstants.MANIFEST_FILE_NAME
                ).firstOrNull()?.id

            val savedManifest = driveApi.uploadJsonResumable(
                accessToken = accessToken,
                preGeneratedOrExistingId = existingManifestId,
                fileName = DriveSchemaConstants.MANIFEST_FILE_NAME,
                parentFolderId = folders.namespaceFolderId,
                jsonBytes = manifestBytes,
                isUpdateExisting = existingManifestId != null
            )

            // 7. Checkpoint-gated tombstone GC
            runCheckpointGatedTombstoneGc(
                firebaseUid = firebaseUid,
                activeDevices = remoteDevices.filter { !it.retired }
            )

            val updatedFolders = folders.copy(manifestFileId = savedManifest.id)
            identityStore.saveDriveLink(
                linkWithFolders.copy(
                    folderIds = updatedFolders,
                    lastSyncedCommitIds = finalHeads,
                    lastVerifiedBackupId = uploadedBackupId ?: manifest.latestBackupId,
                    lastVerifiedSequence = latestSeq,
                    lastSyncUtcMillis = System.currentTimeMillis(),
                    syncStatus = if (detectedConflicts.isNotEmpty()) DriveSyncStatus.FAILED else DriveSyncStatus.SUCCESSFUL,
                    lastErrorMessage = if (detectedConflicts.isNotEmpty()) {
                        "${detectedConflicts.size} sync conflict(s) require resolution"
                    } else null
                )
            )

            return@withContext if (detectedConflicts.isNotEmpty()) {
                DriveSyncOutcome.ConflictDetected(detectedConflicts)
            } else {
                DriveSyncOutcome.Synced(
                    uploadedBackupId = uploadedBackupId,
                    commitId = uploadedCommitId,
                    mergedDeedCount = mergedRemoteCount,
                    conflicts = emptyList(),
                    orphanedRecoveredCount = orphanedRecovered
                )
            }
        } catch (e: DriveHttpException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                identityStore.updateDriveSyncStatus(
                    firebaseUid,
                    DriveSyncStatus.AUTHORIZATION_REQUIRED,
                    "Drive authorization expired or revoked (${e.statusCode})"
                )
                return@withContext DriveSyncOutcome.AuthorizationRequired(e.message)
            }
            identityStore.updateDriveSyncStatus(firebaseUid, DriveSyncStatus.FAILED, e.message)
            return@withContext DriveSyncOutcome.Error(e.message)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Drive sync failed"
            identityStore.updateDriveSyncStatus(firebaseUid, DriveSyncStatus.FAILED, msg)
            return@withContext DriveSyncOutcome.Error(msg)
        }
    }

    suspend fun listAvailableBackups(
        accessToken: String,
        accountInfo: DriveAccountInfo,
        firebaseUid: String
    ): Result<List<DriveBackupSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val link = identityStore.getDriveLink(firebaseUid)
            val validation = validatePreSyncIdentity(firebaseUid, accountInfo, link)
            if (validation is DriveSyncOutcome.AccountMismatch) {
                throw SecurityException("Google Drive account mismatch: expected ${validation.expectedEmail}, got ${validation.actualEmail}")
            }
            if (validation is DriveSyncOutcome.Error) {
                throw SecurityException(validation.message)
            }

            val namespaceId = NamespaceHelper.computeAccountNamespaceId(firebaseUid)
            val folders = ensureFolderHierarchy(accessToken, firebaseUid, link.folderIds)
            val commitFiles = driveApi.listFilesInFolder(accessToken, folders.commitsFolderId)
            val backupFiles = driveApi.listFilesInFolder(accessToken, folders.backupsFolderId)

            val commitsByBackupId = commitFiles.mapNotNull { file ->
                runCatching {
                    val raw = driveApi.downloadFileBytes(accessToken, file.id).toString(Charsets.UTF_8)
                    commitAdapter.fromJson(raw)
                }.getOrNull()
            }.filter {
                it.accountNamespaceId == namespaceId && it.driveSubjectId == accountInfo.subjectId
            }.associateBy { it.backupId }

            backupFiles
                .filter { it.name.startsWith("backup-") && it.name.endsWith(".json") }
                .mapNotNull { file ->
                    val env = runCatching {
                        val raw = driveApi.downloadFileBytes(accessToken, file.id).toString(Charsets.UTF_8)
                        envelopeAdapter.fromJson(raw)
                    }.getOrNull() ?: return@mapNotNull null

                    if (env.accountNamespaceId != namespaceId || env.driveSubjectId != accountInfo.subjectId) {
                        return@mapNotNull null
                    }
                    val commit = commitsByBackupId[env.backupId]
                    DriveBackupSummary(
                        backupId = env.backupId,
                        commitId = commit?.commitId,
                        sequenceNumber = env.sequenceNumber,
                        timestampUtc = env.timestampUtc,
                        deviceId = env.deviceId,
                        encrypted = env.encrypted,
                        fileId = file.id,
                        payloadChecksum = env.payloadSha256
                    )
                }
                .sortedByDescending { it.sequenceNumber }
        }
    }

    suspend fun restoreFromBackup(
        accessToken: String,
        accountInfo: DriveAccountInfo,
        firebaseUid: String,
        backupFileId: String,
        passphrase: CharArray?
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val link = identityStore.getDriveLink(firebaseUid)
            val validation = validatePreSyncIdentity(firebaseUid, accountInfo, link)
            if (validation != null) {
                throw SecurityException("Repository identity validation failed before restore")
            }
            val namespaceId = NamespaceHelper.computeAccountNamespaceId(firebaseUid)
            val raw = driveApi.downloadFileBytes(accessToken, backupFileId).toString(Charsets.UTF_8)
            val envelope = envelopeAdapter.fromJson(raw)
                ?: throw IOException("Invalid backup file format")

            if (envelope.accountNamespaceId != namespaceId || envelope.driveSubjectId != accountInfo.subjectId) {
                throw SecurityException("Backup file belongs to a different account namespace or Google Drive identity")
            }

            val plaintext = crypto.unsealPayload(envelope, passphrase).getOrThrow()
            val payload = payloadAdapter.fromJson(plaintext.toString(Charsets.UTF_8))
                ?: throw IOException("Empty backup payload")

            if (payload.accountNamespaceId != namespaceId || payload.driveSubjectId != accountInfo.subjectId) {
                throw SecurityException("Payload repository identity mismatch")
            }

            val restoredDeeds = payload.deeds.map { deed ->
                deed.copy(
                    ownerUid = firebaseUid,
                    syncState = SyncState.SYNCED
                ).toEntity()
            }
            val restoredAudits = payload.auditLogs.map { log ->
                log.toEntity(ownerUid = firebaseUid)
            }
            if (restoredDeeds.isNotEmpty()) {
                dao.upsertDeeds(restoredDeeds)
            }
            if (restoredAudits.isNotEmpty()) {
                dao.insertAuditLogs(restoredAudits)
            }
            dao.clearPendingSyncForOwner(firebaseUid)
            restoredDeeds.size
        }
    }

    suspend fun listDevices(
        accessToken: String,
        accountInfo: DriveAccountInfo,
        firebaseUid: String
    ): Result<List<DriveDeviceRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            val link = identityStore.getDriveLink(firebaseUid)
            validatePreSyncIdentity(firebaseUid, accountInfo, link)?.let { failure ->
                val message = when (failure) {
                    is DriveSyncOutcome.Error -> failure.message
                    is DriveSyncOutcome.AccountMismatch ->
                        "Connected Google Drive account (${failure.actualEmail}) does not match linked account (${failure.expectedEmail})."
                    else -> "Identity verification failed."
                }
                throw SecurityException(message)
            }
            val folders = ensureFolderHierarchy(accessToken, firebaseUid, link.folderIds)
            val files = driveApi.listFilesInFolder(accessToken, folders.devicesFolderId)
            loadDeviceRecords(accessToken, files).sortedByDescending { it.lastSeenUtc }
        }
    }

    suspend fun retireDevice(
        accessToken: String,
        firebaseUid: String,
        targetDeviceId: String,
        accountInfo: DriveAccountInfo? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(firebaseUid.isNotBlank()) { "Authenticated Firebase UID is required." }
            val cleanDeviceId = targetDeviceId.trim()
            require(
                cleanDeviceId.isNotEmpty() &&
                    !cleanDeviceId.contains('/') &&
                    !cleanDeviceId.contains('\\') &&
                    !cleanDeviceId.contains("..")
            ) { "Invalid device ID." }

            val link = identityStore.getDriveLink(firebaseUid)
            val expectedNamespace = NamespaceHelper.computeAccountNamespaceId(firebaseUid)
            if (link.accountNamespaceId.isNotBlank() && link.accountNamespaceId != expectedNamespace) {
                throw SecurityException("Local account namespace validation failed.")
            }
            if (accountInfo != null) {
                validatePreSyncIdentity(firebaseUid, accountInfo, link)?.let { failure ->
                    val message = when (failure) {
                        is DriveSyncOutcome.Error -> failure.message
                        is DriveSyncOutcome.AccountMismatch ->
                            "Connected Google Drive account (${failure.actualEmail}) does not match linked account (${failure.expectedEmail})."
                        else -> "Identity verification failed."
                    }
                    throw SecurityException(message)
                }
            }

            val folders = ensureFolderHierarchy(accessToken, firebaseUid, link.folderIds)
            val files = driveApi.listFilesInFolder(
                accessToken = accessToken,
                parentFolderId = folders.devicesFolderId,
                nameEquals = "device-$cleanDeviceId.json"
            )
            val existing = files.firstOrNull() ?: return@runCatching Unit
            val raw = driveApi.downloadFileBytes(accessToken, existing.id).toString(Charsets.UTF_8)
            val record = deviceAdapter.fromJson(raw) ?: return@runCatching Unit
            val retired = record.copy(retired = true, retiredAtUtc = nowIsoUtc())
            val bytes = deviceAdapter.toJson(retired).toByteArray(Charsets.UTF_8)
            driveApi.uploadJsonResumable(
                accessToken = accessToken,
                preGeneratedOrExistingId = existing.id,
                fileName = existing.name,
                parentFolderId = folders.devicesFolderId,
                jsonBytes = bytes,
                isUpdateExisting = true
            )
            Unit
        }
    }

    suspend fun deleteAllRemoteBackupsForUid(
        accessToken: String,
        firebaseUid: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val link = identityStore.getDriveLink(firebaseUid)
            val nsId = NamespaceHelper.computeAccountNamespaceId(firebaseUid)
            val folderId = link.folderIds.namespaceFolderId.takeIf { it.isNotBlank() } ?: run {
                val root = driveApi.listFilesInFolder(
                    accessToken, null, DriveSchemaConstants.ROOT_FOLDER_NAME, DriveSchemaConstants.MIME_FOLDER
                ).firstOrNull() ?: return@runCatching Unit
                val accounts = driveApi.listFilesInFolder(
                    accessToken, root.id, DriveSchemaConstants.ACCOUNTS_FOLDER_NAME, DriveSchemaConstants.MIME_FOLDER
                ).firstOrNull() ?: return@runCatching Unit
                driveApi.listFilesInFolder(
                    accessToken, accounts.id, nsId, DriveSchemaConstants.MIME_FOLDER
                ).firstOrNull()?.id.orEmpty()
            }
            if (folderId.isNotBlank()) {
                driveApi.deleteFile(accessToken, folderId)
            }
            Unit
        }
    }

    suspend fun exportPortableBundleJson(firebaseUid: String): String = withContext(Dispatchers.IO) {
        val deeds = dao.getActiveDeedsForOwnerOnce(firebaseUid).map { it.toDomain() }
        val audits = dao.getAuditLogsForOwnerOnce(firebaseUid).map { it.toDomain() }
        val tempPayload = DriveBackupPayload(
            accountNamespaceId = "portable",
            driveSubjectId = "portable",
            exportedAtUtc = nowIsoUtc(),
            deeds = deeds,
            auditLogs = audits
        )
        val sha = crypto.sha256Hex(payloadAdapter.toJson(tempPayload).toByteArray(Charsets.UTF_8))
        val bundle = PortableBackupBundle(
            exportedAtUtc = tempPayload.exportedAtUtc,
            deeds = deeds,
            auditLogs = audits,
            payloadSha256 = sha
        )
        portableAdapter.toJson(bundle)
    }

    suspend fun importPortableBundleJson(
        rawJson: String,
        targetOwnerUid: String,
        deviceId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(targetOwnerUid.isNotBlank()) { "Authenticated user ID is required for import." }
            require(rawJson.length <= 10 * 1024 * 1024) { "Backup file exceeds maximum supported size (10 MB)." }
            val bundle = portableAdapter.fromJson(rawJson)
                ?: throw IllegalArgumentException("Invalid portable backup JSON")
            val providedSha = bundle.payloadSha256.trim().lowercase()
            require(providedSha.matches(Regex("^[0-9a-f]{64}$"))) {
                "Portable backup checksum is missing or invalid."
            }
            val canonicalPayload = DriveBackupPayload(
                accountNamespaceId = "portable",
                driveSubjectId = "portable",
                exportedAtUtc = bundle.exportedAtUtc,
                deeds = bundle.deeds,
                auditLogs = bundle.auditLogs
            )
            val computedSha = crypto.sha256Hex(
                payloadAdapter.toJson(canonicalPayload).toByteArray(Charsets.UTF_8)
            ).lowercase()
            if (computedSha != providedSha) {
                throw SecurityException("Portable backup integrity check failed (SHA-256 mismatch).")
            }

            val now = System.currentTimeMillis()
            val importedDeeds = bundle.deeds.map { deed ->
                val safeId = deed.id.trim().ifBlank { UUID.randomUUID().toString() }
                deed.copy(
                    id = safeId,
                    ownerUid = targetOwnerUid,
                    createdBy = targetOwnerUid,
                    updatedBy = targetOwnerUid,
                    deviceId = deviceId,
                    revisionId = UUID.randomUUID().toString(),
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPLOAD,
                    isDeleted = false
                ).toEntity()
            }
            val importedAudits = bundle.auditLogs.map { log ->
                log.toEntity(ownerUid = targetOwnerUid)
            }
            if (importedDeeds.isNotEmpty()) {
                dao.upsertDeeds(importedDeeds)
                importedDeeds.forEach { entity ->
                    dao.enqueuePendingSync(
                        com.example.data.local.PendingSyncEntity(
                            deedId = entity.id,
                            ownerUid = targetOwnerUid,
                            operation = "UPSERT",
                            queuedAtMillis = now
                        )
                    )
                }
            }
            if (importedAudits.isNotEmpty()) {
                dao.insertAuditLogs(importedAudits)
            }
            importedDeeds.size
        }
    }

    fun validatePreSyncIdentity(
        firebaseUid: String,
        accountInfo: DriveAccountInfo,
        linkState: DriveLinkState
    ): DriveSyncOutcome? {
        if (firebaseUid.isBlank()) {
            return DriveSyncOutcome.Error("Authenticated Firebase UID is required before syncing.")
        }
        if (accountInfo.subjectId.isBlank()) {
            return DriveSyncOutcome.Error("Verified Google Drive subject ID (sub) is required.")
        }
        val expectedNamespace = NamespaceHelper.computeAccountNamespaceId(firebaseUid)
        if (linkState.accountNamespaceId.isNotBlank() && linkState.accountNamespaceId != expectedNamespace) {
            return DriveSyncOutcome.Error("Local account namespace validation failed.")
        }
        if (linkState.driveSubjectId.isNotBlank() && linkState.driveSubjectId != accountInfo.subjectId) {
            return DriveSyncOutcome.AccountMismatch(
                expectedSubjectId = linkState.driveSubjectId,
                expectedEmail = linkState.driveEmail,
                actualSubjectId = accountInfo.subjectId,
                actualEmail = accountInfo.email
            )
        }
        if (identityStore.getOrCreateDeviceId().isBlank()) {
            return DriveSyncOutcome.Error("Device identity is unavailable.")
        }
        return null
    }

    suspend fun ensureFolderHierarchy(
        accessToken: String,
        firebaseUid: String,
        existingIds: DriveFolderIds
    ): DriveFolderIds {
        val namespaceId = NamespaceHelper.computeAccountNamespaceId(firebaseUid)

        suspend fun validOrFind(persistedId: String, name: String, parentId: String?): String {
            if (persistedId.isNotBlank()) {
                val meta = driveApi.getFileMetadata(accessToken, persistedId)
                if (meta != null && meta.name == name &&
                    (parentId == null || parentId in meta.parents)
                ) {
                    return meta.id
                }
            }
            return driveApi.findOrCreateFolder(accessToken, name, parentId).id
        }

        val rootId = validOrFind(existingIds.rootFolderId, DriveSchemaConstants.ROOT_FOLDER_NAME, null)
        val accountsId = validOrFind(existingIds.accountsFolderId, DriveSchemaConstants.ACCOUNTS_FOLDER_NAME, rootId)
        val nsFolderId = validOrFind(existingIds.namespaceFolderId, namespaceId, accountsId)
        val devicesId = validOrFind(existingIds.devicesFolderId, DriveSchemaConstants.DEVICES_FOLDER_NAME, nsFolderId)
        val backupsId = validOrFind(existingIds.backupsFolderId, DriveSchemaConstants.BACKUPS_FOLDER_NAME, nsFolderId)
        val commitsId = validOrFind(existingIds.commitsFolderId, DriveSchemaConstants.COMMITS_FOLDER_NAME, nsFolderId)

        return existingIds.copy(
            rootFolderId = rootId,
            accountsFolderId = accountsId,
            namespaceFolderId = nsFolderId,
            devicesFolderId = devicesId,
            backupsFolderId = backupsId,
            commitsFolderId = commitsId
        )
    }

    private suspend fun mergeRemotePayloadIntoLocal(
        firebaseUid: String,
        localDeviceId: String,
        lastSyncUtcMillis: Long,
        commit: DriveCommitRecord,
        payload: DriveBackupPayload
    ): MergeResult {
        val localDeedsMap = dao.getAllDeedsIncludingTombstonesForOwnerOnce(firebaseUid)
            .associate { it.id to it.toDomain() }
        val pendingIds = dao.getPendingSyncForOwnerOnce(firebaseUid).map { it.deedId }.toSet()

        val deedsToUpsert = mutableListOf<com.example.data.local.DeedEntity>()
        val conflicts = mutableListOf<DeedConflict>()
        var mergedCount = 0

        for (remoteRaw in payload.deeds) {
            val remote = remoteRaw.copy(
                ownerUid = firebaseUid,
                syncState = SyncState.SYNCED
            )
            val local = localDeedsMap[remote.id]

            when {
                local == null -> {
                    deedsToUpsert.add(remote.toEntity())
                    mergedCount++
                }
                local.revisionId.isNotBlank() && local.revisionId == remote.revisionId -> {
                    // Identical revision, nothing to merge
                }
                local.id in pendingIds -> {
                    // Local has unsynced mutations. Determine whether remote actually changed on another device!
                    val remoteChangedOnOtherDevice = remote.deviceId != localDeviceId &&
                        (lastSyncUtcMillis == 0L || remote.updatedAtMillis > lastSyncUtcMillis)
                    if (remoteChangedOnOtherDevice) {
                        val conflictDeed = local.copy(syncState = SyncState.CONFLICT)
                        deedsToUpsert.add(conflictDeed.toEntity())
                        conflicts.add(
                            DeedConflict(
                                deedId = local.id,
                                localDeed = conflictDeed,
                                serverDeed = remote,
                                remoteDeviceId = remote.deviceId,
                                remoteCommitId = commit.commitId
                            )
                        )
                    }
                }
                remote.updatedAtMillis >= local.updatedAtMillis || remote.rowVersion > local.rowVersion -> {
                    deedsToUpsert.add(remote.toEntity())
                    mergedCount++
                }
            }
        }

        if (deedsToUpsert.isNotEmpty()) {
            dao.upsertDeeds(deedsToUpsert)
        }

        val existingLogIds = dao.getAuditLogsForOwnerOnce(firebaseUid).map { it.id }.toSet()
        val newLogs = payload.auditLogs
            .filter { it.id !in existingLogIds }
            .map { it.toEntity(ownerUid = firebaseUid) }
        if (newLogs.isNotEmpty()) {
            dao.insertAuditLogs(newLogs)
        }

        return MergeResult(mergedCount = mergedCount, conflicts = conflicts)
    }

    private suspend fun loadDeviceRecords(
        accessToken: String,
        deviceFiles: List<DriveFileMetadata>
    ): List<DriveDeviceRecord> = deviceFiles
        .filter { it.name.startsWith("device-") && it.name.endsWith(".json") }
        .mapNotNull { file ->
            runCatching {
                val raw = driveApi.downloadFileBytes(accessToken, file.id).toString(Charsets.UTF_8)
                deviceAdapter.fromJson(raw)
            }.getOrNull()
        }

    private suspend fun runCheckpointGatedTombstoneGc(
        firebaseUid: String,
        activeDevices: List<DriveDeviceRecord>,
        retentionMillis: Long = TOMBSTONE_RETENTION_MILLIS
    ) {
        if (activeDevices.isEmpty()) return
        val earliestDeviceCheckpointMillis = activeDevices
            .mapNotNull { parseIsoUtcMillis(it.lastSeenUtc) }
            .minOrNull() ?: return
        val ageCutoff = System.currentTimeMillis() - retentionMillis
        val safeCutoff = minOf(ageCutoff, earliestDeviceCheckpointMillis)
        if (safeCutoff > 0L) {
            dao.purgeTombstonesBefore(firebaseUid, safeCutoff)
        }
    }

    private fun computeHeadCommitIds(commits: List<DriveCommitRecord>): List<String> {
        if (commits.isEmpty()) return emptyList()
        val referencedParents = commits.flatMap { it.parentCommitIds }.toSet()
        return commits
            .filter { it.commitId !in referencedParents }
            .sortedBy { it.sequenceNumber }
            .map { it.commitId }
    }

    private fun utcTimestampCompact(): String =
        SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun nowIsoUtc(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun parseIsoUtcMillis(iso: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(iso)?.time
    }.getOrNull()

    private data class MergeResult(
        val mergedCount: Int,
        val conflicts: List<DeedConflict>
    )

    companion object {
        const val TOMBSTONE_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
