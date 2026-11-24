package com.example.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.drive.DriveAuthGateway
import com.example.data.drive.DriveAuthResult
import com.example.data.drive.DriveBackupEngine
import com.example.data.drive.DriveRemoteStorage
import com.example.data.drive.DriveSyncOutcome
import com.example.data.drive.HttpDriveApiV3Client
import com.example.data.drive.PlayServicesDriveAuthGateway
import com.example.data.local.DeviceIdentityStore
import com.example.data.local.DolilDatabase
import com.example.data.local.DriveSyncStatus
import com.example.data.local.SessionPassphraseVault
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit

object DriveSyncWorkerDependencies {
    @Volatile
    var authGatewayFactory: () -> DriveAuthGateway = { PlayServicesDriveAuthGateway() }

    @Volatile
    var driveStorageFactory: () -> DriveRemoteStorage = { HttpDriveApiV3Client() }

    @Volatile
    var currentFirebaseUidProvider: () -> String? = {
        runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
    }

    fun resetToDefaults() {
        authGatewayFactory = { PlayServicesDriveAuthGateway() }
        driveStorageFactory = { HttpDriveApiV3Client() }
        currentFirebaseUidProvider = { runCatching { Firebase.auth.currentUser?.uid }.getOrNull() }
    }
}

class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val expectedUid = inputData.getString(KEY_EXPECTED_UID).orEmpty()
        if (expectedUid.isBlank()) {
            return Result.success()
        }

        val activeUid = DriveSyncWorkerDependencies.currentFirebaseUidProvider()
        if (activeUid != expectedUid) {
            // User logged out or switched Firebase accounts: abort without syncing wrong UID!
            return Result.success()
        }

        val identityStore = DeviceIdentityStore(applicationContext)
        val link = identityStore.getDriveLink(expectedUid)
        if (!link.isConnected || !link.autoSyncEnabled) {
            return Result.success()
        }

        val authGateway = DriveSyncWorkerDependencies.authGatewayFactory()
        val authResult = authGateway.authorizeSilently(
            context = applicationContext,
            targetAccountEmail = link.driveEmail.ifBlank { null }
        )

        val authorized = when (authResult) {
            is DriveAuthResult.Authorized -> authResult
            is DriveAuthResult.NeedsResolution -> {
                identityStore.updateDriveSyncStatus(
                    firebaseUid = expectedUid,
                    status = DriveSyncStatus.AUTHORIZATION_REQUIRED,
                    errorMessage = "Google Drive consent is required before background backup can continue."
                )
                return Result.failure()
            }
            is DriveAuthResult.Cancelled -> {
                identityStore.updateDriveSyncStatus(
                    firebaseUid = expectedUid,
                    status = DriveSyncStatus.AUTHORIZATION_REQUIRED,
                    errorMessage = authResult.reason
                )
                return Result.failure()
            }
            is DriveAuthResult.Error -> {
                if (authResult.isRevokedOrExpired) {
                    identityStore.updateDriveSyncStatus(
                        firebaseUid = expectedUid,
                        status = DriveSyncStatus.AUTHORIZATION_REQUIRED,
                        errorMessage = authResult.message
                    )
                    return Result.failure()
                }
                identityStore.updateDriveSyncStatus(
                    firebaseUid = expectedUid,
                    status = DriveSyncStatus.FAILED,
                    errorMessage = authResult.message
                )
                return Result.retry()
            }
        }

        if (authorized.accountInfo.subjectId != link.driveSubjectId) {
            identityStore.updateDriveSyncStatus(
                firebaseUid = expectedUid,
                status = DriveSyncStatus.FAILED,
                errorMessage = "Connected Google Drive account (${authorized.accountInfo.email}) does not match linked repository (${link.driveEmail})."
            )
            return Result.failure()
        }

        val passphrase = if (link.encryptionEnabled) {
            val stored = SessionPassphraseVault.getPassphrase(expectedUid)
            if (stored == null || stored.isEmpty()) {
                identityStore.updateDriveSyncStatus(
                    firebaseUid = expectedUid,
                    status = DriveSyncStatus.FAILED,
                    errorMessage = "Unlock encryption passphrase in Settings to resume background backup."
                )
                return Result.failure()
            }
            stored
        } else {
            null
        }

        return try {
            val db = DolilDatabase.getInstance(applicationContext)
            val engine = DriveBackupEngine(
                driveApi = DriveSyncWorkerDependencies.driveStorageFactory(),
                dao = db.dolilDao(),
                identityStore = identityStore
            )
            when (
                val outcome = engine.syncNow(
                    accessToken = authorized.accessToken,
                    accountInfo = authorized.accountInfo,
                    firebaseUid = expectedUid,
                    passphrase = passphrase,
                    forceUploadSnapshot = false
                )
            ) {
                is DriveSyncOutcome.Synced -> Result.success()
                is DriveSyncOutcome.AuthorizationRequired,
                is DriveSyncOutcome.AccountMismatch,
                is DriveSyncOutcome.PassphraseRequired,
                is DriveSyncOutcome.ConflictDetected -> Result.failure()
                is DriveSyncOutcome.Error -> if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } finally {
            passphrase?.fill('\u0000')
        }
    }

    companion object {
        const val KEY_EXPECTED_UID = "expected_firebase_uid"
    }
}

object DriveSyncScheduler {
    const val TAG_DRIVE_SYNC = "dolil_drive_sync"

    fun scheduleDebouncedBackup(context: Context, firebaseUid: String) {
        if (firebaseUid.isBlank()) return
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .addTag(TAG_DRIVE_SYNC)
                .addTag("uid_$firebaseUid")
                .setConstraints(constraints)
                .setInitialDelay(8L, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(DriveSyncWorker.KEY_EXPECTED_UID, firebaseUid)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    "dolil_drive_debounced_$firebaseUid",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }

    fun schedulePeriodicSync(context: Context, firebaseUid: String) {
        if (firebaseUid.isBlank()) return
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(30L, TimeUnit.MINUTES)
                .addTag(TAG_DRIVE_SYNC)
                .addTag("uid_$firebaseUid")
                .setConstraints(constraints)
                .setInputData(
                    Data.Builder()
                        .putString(DriveSyncWorker.KEY_EXPECTED_UID, firebaseUid)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    "dolil_drive_periodic_$firebaseUid",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }
    }

    fun cancelAllSync(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag(TAG_DRIVE_SYNC)
        }
    }
}
