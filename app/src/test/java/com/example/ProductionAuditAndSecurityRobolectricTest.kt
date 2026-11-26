package com.example

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.designsystem.DolilTheme
import com.example.core.security.RolePermissions
import com.example.data.drive.DriveAccountInfo
import com.example.data.drive.DriveAuthGateway
import com.example.data.drive.DriveAuthResult
import com.example.data.drive.DriveBackupEngine
import com.example.data.drive.DriveCryptoEngine
import com.example.data.drive.DriveFileMetadata
import com.example.data.drive.DriveRemoteStorage
import com.example.data.drive.HttpDriveApiV3Client
import com.example.data.drive.PortableBackupBundle
import com.example.data.local.DeviceIdentityStore
import com.example.data.local.DolilDatabase
import com.example.data.local.DriveLinkState
import com.example.data.local.NamespaceHelper
import com.example.data.local.UserPreferencesDataStore
import com.example.data.repository.DolilSyncRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedType
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandParcel
import com.example.domain.model.LandUnit
import com.example.domain.model.PartyInfo
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.DolilViewModel
import com.example.ui.screens.DeedEntryScreen
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class ProductionAuditAndSecurityRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class InMemoryDriveStorage : DriveRemoteStorage {
        private val folders = linkedMapOf<String, DriveFileMetadata>()
        private val files = linkedMapOf<String, Pair<DriveFileMetadata, ByteArray>>()

        override suspend fun generateFileIds(accessToken: String, count: Int): List<String> =
            List(count) { "gen-${UUID.randomUUID()}" }

        override suspend fun listFilesInFolder(
            accessToken: String,
            parentFolderId: String?,
            nameEquals: String?,
            mimeTypeEquals: String?
        ): List<DriveFileMetadata> {
            val all = folders.values + files.values.map { it.first }
            return all.filter { meta ->
                val parentOk = if (parentFolderId == null) meta.parents.isEmpty() else parentFolderId in meta.parents
                val nameOk = nameEquals == null || meta.name == nameEquals
                val mimeOk = mimeTypeEquals == null || meta.mimeType == mimeTypeEquals
                parentOk && nameOk && mimeOk
            }
        }

        override suspend fun getFileMetadata(accessToken: String, fileId: String): DriveFileMetadata? =
            folders[fileId] ?: files[fileId]?.first

        override suspend fun findOrCreateFolder(
            accessToken: String,
            folderName: String,
            parentFolderId: String?
        ): DriveFileMetadata {
            val existing = listFilesInFolder(accessToken, parentFolderId, folderName, "application/vnd.google-apps.folder").firstOrNull()
            if (existing != null) return existing
            val id = "folder-${UUID.randomUUID()}"
            val meta = DriveFileMetadata(
                id = id,
                name = folderName,
                parents = listOfNotNull(parentFolderId),
                mimeType = "application/vnd.google-apps.folder"
            )
            folders[id] = meta
            return meta
        }

        override suspend fun uploadJsonResumable(
            accessToken: String,
            preGeneratedOrExistingId: String?,
            fileName: String,
            parentFolderId: String,
            jsonBytes: ByteArray,
            isUpdateExisting: Boolean
        ): DriveFileMetadata {
            val id = preGeneratedOrExistingId ?: "file-${UUID.randomUUID()}"
            val meta = DriveFileMetadata(
                id = id,
                name = fileName,
                parents = listOf(parentFolderId),
                mimeType = "application/json",
                sizeBytes = jsonBytes.size.toLong()
            )
            files[id] = meta to jsonBytes
            return meta
        }

        override suspend fun downloadFileBytes(accessToken: String, fileId: String): ByteArray =
            files[fileId]?.second ?: throw NoSuchElementException("Missing $fileId")

        override suspend fun deleteFile(accessToken: String, fileId: String) {
            files.remove(fileId)
            folders.remove(fileId)
        }
    }

    private class StubDriveAuthGateway(
        var nextInteractive: DriveAuthResult = DriveAuthResult.Cancelled("User cancelled"),
        var nextResolution: DriveAuthResult = DriveAuthResult.Cancelled("Denied")
    ) : DriveAuthGateway {
        override suspend fun authorizeSilently(context: Context, targetAccountEmail: String?): DriveAuthResult = nextInteractive
        override suspend fun requestInteractiveAuthorization(
            context: Context,
            targetAccountEmail: String?,
            forceSelectAccount: Boolean
        ): DriveAuthResult = nextInteractive

        override suspend fun handleResolutionResult(context: Context, resultData: Intent?): DriveAuthResult = nextResolution
        override suspend fun revokeGoogleAccess(context: Context, lastAccessToken: String?): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `drive api client rejects path traversal, query injection, and blank file ids`() = runBlocking {
        val client = HttpDriveApiV3Client()
        val unsafeIds = listOf("", "   ", "../secrets", "abc/def", "abc?alt=media", "abc#frag", "id with space")

        for (bad in unsafeIds) {
            val metaRes = runCatching { client.getFileMetadata("tok", bad) }
            assertTrue("Expected failure for metadata fileId='$bad'", metaRes.exceptionOrNull() is IllegalArgumentException)

            val dlRes = runCatching { client.downloadFileBytes("tok", bad) }
            assertTrue("Expected failure for download fileId='$bad'", dlRes.exceptionOrNull() is IllegalArgumentException)

            val delRes = runCatching { client.deleteFile("tok", bad) }
            assertTrue("Expected failure for delete fileId='$bad'", delRes.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `portable backup import verifies sha256 checksum and rejects tampered or malformed payloads`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dolilDao()
            val store = DeviceIdentityStore(context)
            val engine = DriveBackupEngine(InMemoryDriveStorage(), dao, store, DriveCryptoEngine(10_000))
            val repo = DolilSyncRepository(dao, ownerUid = "owner-1", enableCloudSync = true)
            val actor = UserProfile(userId = "owner-1", fullName = "Owner")
            repo.saveDeedLocallyAndTrySync(
                DeedRecord(id = "d-sec-1", deedNumber = "101/2026", mouzaName = "Fatulla"),
                actor,
                com.example.domain.model.AuditAction.CREATE
            )

            val exportedJson = engine.exportPortableBundleJson("owner-1")
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(PortableBackupBundle::class.java)
            val bundle = adapter.fromJson(exportedJson)!!

            // 1. Tamper with a deed field without updating SHA-256 -> must fail cleanly
            val tampered = bundle.copy(
                deeds = bundle.deeds.map { it.copy(mouzaName = "TamperedMouza") }
            )
            val tamperedRes = engine.importPortableBundleJson(adapter.toJson(tampered), "owner-2", "dev-2")
            assertTrue(tamperedRes.isFailure)
            assertTrue(tamperedRes.exceptionOrNull() is SecurityException)

            // 2. Missing checksum -> must fail cleanly
            val missingSha = bundle.copy(payloadSha256 = "")
            val missingRes = engine.importPortableBundleJson(adapter.toJson(missingSha), "owner-2", "dev-2")
            assertTrue(missingRes.isFailure)

            // 3. Valid exported bundle imports cleanly
            val validRes = engine.importPortableBundleJson(exportedJson, "owner-2", "dev-2")
            assertTrue(validRes.isSuccess)
            assertEquals(1, validRes.getOrThrow())
        } finally {
            db.close()
        }
    }

    @Test
    fun `listDevices and retireDevice enforce Drive identity matching and reject path traversal device IDs`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = DeviceIdentityStore(context)
            val uid = "uid-dev-sec"
            store.saveDriveLink(
                DriveLinkState(
                    firebaseUid = uid,
                    accountNamespaceId = NamespaceHelper.computeAccountNamespaceId(uid),
                    driveSubjectId = "expected-sub",
                    driveEmail = "owner@gmail.com"
                )
            )
            val engine = DriveBackupEngine(InMemoryDriveStorage(), db.dolilDao(), store)
            val wrongAccount = DriveAccountInfo(subjectId = "other-sub", email = "attacker@gmail.com")

            val listFail = engine.listDevices("tok", wrongAccount, uid)
            assertTrue(listFail.isFailure)

            val retireMismatch = engine.retireDevice("tok", uid, "dev-123", wrongAccount)
            assertTrue(retireMismatch.isFailure)

            val retireTraversal = engine.retireDevice("tok", uid, "../other", null)
            assertTrue(retireTraversal.isFailure)
        } finally {
            db.close()
        }
    }

    @Test
    fun `public user personal deeds save as APPROVED, respect owner lifecycle, and never receive hardcoded admin role`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java).allowMainThreadQueries().build()
        val vmStore = ViewModelStore()
        try {
            val dao = db.dolilDao()
            val uid = "ordinary-user-uid"
            val syncRepo = DolilSyncRepository(dao, ownerUid = uid, enableCloudSync = true)
            val prefsStore = UserPreferencesDataStore(context)
            val vm = ViewModelProvider(
                vmStore,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = DolilViewModel(
                        syncRepo = syncRepo,
                        prefsStore = prefsStore,
                        currentUserId = uid,
                        authEmail = "jubayerhossaingalaxy@gmail.com",
                        authDisplayName = "Citizen",
                        isGuestMode = false,
                        appContext = context,
                        dao = dao,
                        driveAuthGateway = StubDriveAuthGateway(),
                        driveStorage = InMemoryDriveStorage()
                    ) as T
                }
            )[DolilViewModel::class.java]

            composeRule.setContent {
                vm.userProfile.collectAsStateWithLifecycle()
                vm.allDeedsState.collectAsStateWithLifecycle()
            }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.allDeedsState.value is UiState.Success
            }

            // Hardcoded developer email backdoor is gone -> standard role
            assertNotEquals(UserRole.PLATFORM_ADMIN, vm.userProfile.value.role)
            assertEquals(UserRole.PUBLIC_USER, vm.userProfile.value.role)

            val validDeed = DeedRecord(
                id = "personal-deed-1",
                deedNumber = "500/2026",
                deedYear = 2026,
                deedType = DeedType.SAF_KABALA,
                registrationDateMillis = System.currentTimeMillis(),
                districtName = "Narayanganj",
                upazilaName = "Fatulla",
                mouzaName = "Fatulla",
                jlNumber = "140",
                grantors = listOf(PartyInfo(fullName = "Grantor")),
                grantees = listOf(PartyInfo(fullName = "Grantee")),
                parcels = listOf(LandParcel(khatianNumber = "10", dagNumber = "20", landAreaDecimal = "5.0", landUnit = LandUnit.DECIMAL))
            )

            var saved: DeedRecord? = null
            vm.saveDeed(validDeed, saveAsDraft = false) { saved = it }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                saved != null
            }

            assertEquals(DeedStatus.APPROVED, saved!!.status)
            assertEquals(DeedVisibility.PUBLIC, saved!!.visibility)
            assertTrue(RolePermissions.canEditDeed(vm.userProfile.value, saved!!))
            assertTrue(RolePermissions.canSoftDeleteDeed(vm.userProfile.value, saved!!))

            // Cross-owner deed must still be blocked for PUBLIC_USER
            val foreignApproved = saved!!.copy(ownerUid = "someone-else", createdBy = "someone-else")
            assertFalse(RolePermissions.canEditDeed(vm.userProfile.value, foreignApproved))
            assertFalse(RolePermissions.canSoftDeleteDeed(vm.userProfile.value, foreignApproved))
        } finally {
            vmStore.clear()
            db.close()
        }
    }

    @Test
    fun `drive loading spinners reset on auth failure and pending restore or retire resumes after consent resolution`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java).allowMainThreadQueries().build()
        val vmStore = ViewModelStore()
        try {
            val dao = db.dolilDao()
            val uid = "drive-spinner-uid"
            val store = DeviceIdentityStore(context)
            val account = DriveAccountInfo(subjectId = "sub-spin", email = "spin@gmail.com")
            store.saveDriveLink(
                DriveLinkState(
                    firebaseUid = uid,
                    accountNamespaceId = NamespaceHelper.computeAccountNamespaceId(uid),
                    driveSubjectId = account.subjectId,
                    driveEmail = account.email
                )
            )
            val gateway = StubDriveAuthGateway(
                nextInteractive = DriveAuthResult.Error("Temporary Drive failure")
            )
            val storage = InMemoryDriveStorage()
            val engine = DriveBackupEngine(storage, dao, store)
            runBlocking {
                val repo = DolilSyncRepository(dao, ownerUid = uid, enableCloudSync = true)
                repo.saveDeedLocallyAndTrySync(
                    DeedRecord(id = "d-spin-1", deedNumber = "12/2026", mouzaName = "Chashara"),
                    UserProfile(userId = uid, fullName = "User"),
                    com.example.domain.model.AuditAction.CREATE
                )
                engine.syncNow("tok", account, uid, null)
            }
            val backups = runBlocking { engine.listAvailableBackups("tok", account, uid).getOrThrow() }
            assertTrue(backups.isNotEmpty())

            val vm = ViewModelProvider(
                vmStore,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = DolilViewModel(
                        syncRepo = DolilSyncRepository(dao, ownerUid = uid, enableCloudSync = true),
                        prefsStore = UserPreferencesDataStore(context),
                        currentUserId = uid,
                        authEmail = "spin@gmail.com",
                        authDisplayName = "Spin User",
                        isGuestMode = false,
                        appContext = context,
                        dao = dao,
                        identityStore = store,
                        driveAuthGateway = gateway,
                        driveStorage = storage
                    ) as T
                }
            )[DolilViewModel::class.java]

            composeRule.setContent {
                vm.isBackupsLoading.collectAsStateWithLifecycle()
                vm.isDevicesLoading.collectAsStateWithLifecycle()
                vm.statusMessage.collectAsStateWithLifecycle()
            }

            // Error outcome resets both spinners cleanly instead of hanging forever
            vm.loadAvailableBackups()
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                !vm.isBackupsLoading.value && vm.statusMessage.value == "Temporary Drive failure"
            }

            vm.loadConnectedDevices()
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                !vm.isDevicesLoading.value
            }

            // Restore requiring consent keeps pending restore operation and runs restore after resolution
            val pi = PendingIntent.getActivity(context, 1, Intent("com.example.CONSENT"), PendingIntent.FLAG_IMMUTABLE)
            gateway.nextInteractive = DriveAuthResult.NeedsResolution(pi)
            gateway.nextResolution = DriveAuthResult.Authorized("tok-2", account)

            vm.restoreFromBackup(backups.first(), null)
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.pendingDriveConsentIntent.value != null
            }
            vm.onDriveConsentResult(Intent())
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.statusMessage.value?.contains("Restored") == true ||
                    vm.statusMessage.value?.contains("পুনরুদ্ধার") == true
            }
        } finally {
            vmStore.clear()
            db.close()
        }
    }

    @Test
    fun `backup_rules and data_extraction_rules exclude dolil_device_identity shared preferences`() {
        val backupXml = File("src/main/res/xml/backup_rules.xml").let {
            if (it.exists()) it.readText() else File("app/src/main/res/xml/backup_rules.xml").readText()
        }
        val extractXml = File("src/main/res/xml/data_extraction_rules.xml").let {
            if (it.exists()) it.readText() else File("app/src/main/res/xml/data_extraction_rules.xml").readText()
        }
        assertTrue(backupXml.contains("""domain="sharedpref" path="dolil_device_identity.xml""""))
        assertEquals(2, Regex("""domain="sharedpref" path="dolil_device_identity\.xml"""").findAll(extractXml).count())
    }

    @Test
    fun `date picker dialog opens properly on tap and renders confirm button`() {
        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                DeedEntryScreen(
                    language = AppLanguage.EN,
                    userProfile = UserProfile(userId = "u1", fullName = "User"),
                    validationErrors = emptyList(),
                    duplicateCandidateId = null,
                    isSaving = false,
                    onSaveDeed = { _, _, _ -> },
                    onViewDuplicateDeed = {},
                    onClearValidation = {},
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag("entry_reg_date_calendar_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_date_picker_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("date_picker_confirm_button").assertIsDisplayed()
    }
}
