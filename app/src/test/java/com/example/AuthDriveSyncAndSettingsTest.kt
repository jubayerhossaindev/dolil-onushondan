package com.example

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.config.AppVersionInfo
import com.example.core.config.CredentialReadinessChecker
import com.example.core.designsystem.DolilTheme
import com.example.core.security.AuthValidationError
import com.example.core.security.AuthValidator
import com.example.data.drive.DriveAccountInfo
import com.example.data.drive.DriveAuthGateway
import com.example.data.drive.DriveAuthResult
import com.example.data.drive.DriveBackupEngine
import com.example.data.drive.DriveBackupEnvelope
import com.example.data.drive.DriveCommitRecord
import com.example.data.drive.DriveCryptoEngine
import com.example.data.drive.DriveFileMetadata
import com.example.data.drive.DriveHttpException
import com.example.data.drive.DriveManifest
import com.example.data.drive.DriveRemoteStorage
import com.example.data.drive.DriveSchemaConstants
import com.example.data.drive.DriveSyncOutcome
import com.example.data.local.DeviceIdentityStore
import com.example.data.local.DolilDao
import com.example.data.local.DolilDatabase
import com.example.data.local.DriveFolderIds
import com.example.data.local.DriveLinkState
import com.example.data.local.DriveSyncStatus
import com.example.data.local.LEGACY_LOCAL_OWNER_UID
import com.example.data.local.LocalUserPreferences
import com.example.data.local.NamespaceHelper
import com.example.data.repository.DolilSyncRepository
import com.example.data.sync.DriveSyncWorker
import com.example.data.sync.DriveSyncWorkerDependencies
import com.example.domain.model.AppLanguage
import com.example.domain.model.AuditAction
import com.example.domain.model.DeedConflict
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedType
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandParcel
import com.example.domain.model.OfficeStats
import com.example.domain.model.PartyInfo
import com.example.domain.model.SyncState
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.screens.AccessSelectionScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeedDetailsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthDriveSyncAndSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dolil_identity_store", Context.MODE_PRIVATE)
            .edit().clear().commit()
        DriveSyncWorkerDependencies.resetToDefaults()
    }

    @After
    fun tearDown() {
        DriveSyncWorkerDependencies.resetToDefaults()
    }

    // -------------------------------------------------------------------------
    // 1. Version Exclusivity & Audit Trail UI Removal
    // -------------------------------------------------------------------------

    @Test
    fun `version number matches BuildConfig and renders ONLY in Settings section 5`() {
        assertEquals("1.0.1", AppVersionInfo.versionName)
        assertEquals("v1.0.1", AppVersionInfo.formattedVersion)

        var screenMode by mutableIntStateOf(0)
        val sampleDeed = DeedRecord(
            id = "ver-deed-1",
            deedNumber = "1200/2026",
            deedYear = 2026,
            mouzaName = "Fatulla",
            grantors = listOf(PartyInfo(fullName = "Grantor One")),
            grantees = listOf(PartyInfo(fullName = "Grantee One")),
            parcels = listOf(LandParcel(khatianNumber = "10", dagNumber = "20", landAreaDecimal = "5.0")),
            status = DeedStatus.APPROVED,
            visibility = DeedVisibility.PUBLIC
        )
        val user = UserProfile(
            userId = "uid-ver-1",
            fullName = "Rahim Uddin",
            email = "rahim@example.com",
            role = UserRole.PUBLIC_USER
        )

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                when (screenMode) {
                    0 -> ProfileSettingsScreen(
                        language = AppLanguage.EN,
                        preferences = LocalUserPreferences(language = AppLanguage.EN, themeMode = ThemeMode.LIGHT),
                        userProfile = user,
                        pendingSyncCount = 0,
                        adminUsers = emptyList(),
                        onUpdateProfileNamePhone = { _, _ -> },
                        onLanguageChange = {},
                        onThemeChange = {},
                        onSyncNow = {},
                        onAssignUserRole = { _, _, _, _ -> },
                        onSignOut = {}
                    )
                    1 -> DashboardScreen(
                        language = AppLanguage.EN,
                        userProfile = user,
                        deedsState = UiState.Success(listOf(sampleDeed)),
                        officeStats = OfficeStats(totalDeeds = 1),
                        onQuickSearchSubmit = {},
                        onNavigateAllRecords = {},
                        onNavigatePersons = {},
                        onNavigateLand = {},
                        onNavigateProfile = {},
                        onOpenDeedDetail = {}
                    )
                    2 -> OnboardingScreen(
                        language = AppLanguage.EN,
                        onLanguageChange = {},
                        onSkipOrFinishToAccess = {},
                        onLoginOrRegisterFromOnboarding = {},
                        onExploreAsGuest = {}
                    )
                    3 -> AccessSelectionScreen(
                        language = AppLanguage.EN,
                        onLanguageChange = {},
                        onEnterGuestMode = {},
                        onViewOnboardingAgain = {},
                        onAuthSuccess = {}
                    )
                    else -> DeedDetailsScreen(
                        language = AppLanguage.EN,
                        userProfile = user,
                        deed = sampleDeed,
                        onEditDeed = {},
                        onApproveDeed = {},
                        onSoftDeleteDeed = {},
                        onRestoreDeed = {},
                        onBack = {}
                    )
                }
            }
        }

        // Settings Screen MUST display v1.0.1 in Section 5
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("section_app_info"))
        composeRule.onNodeWithTag("section_app_info").assertIsDisplayed()
        composeRule.onAllNodesWithText("v1.0.1", substring = true).assertCountEquals(1)

        // Dashboard MUST NOT display version text
        screenMode = 1
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("v1.0", substring = true).assertCountEquals(0)

        // Onboarding MUST NOT display version text
        screenMode = 2
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("v1.0", substring = true).assertCountEquals(0)

        // Access Selection MUST NOT display version text
        screenMode = 3
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("v1.0", substring = true).assertCountEquals(0)

        // DeedDetails MUST NOT display app version text or any Audit Trail / Change History UI section
        screenMode = 4
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("v1.0", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Audit Trail", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("পরিবর্তন ইতিহাস", substring = true).assertCountEquals(0)
    }

    // -------------------------------------------------------------------------
    // 2. Settings 6 Sections, Developer Identity & Cloud Dialogs
    // -------------------------------------------------------------------------

    @Test
    fun `settings displays exact 6 sections, developer identity card without fake info, and drive dialogs`() {
        var lang by mutableStateOf(AppLanguage.EN)
        var disconnectCalled = false
        var revokeCalled = false
        val connectedLink = DriveLinkState(
            firebaseUid = "uid-dev-1",
            accountNamespaceId = NamespaceHelper.computeAccountNamespaceId("uid-dev-1"),
            driveSubjectId = "sub-123",
            driveEmail = "jewel.personal@gmail.com",
            driveDisplayName = "Personal Drive",
            syncStatus = DriveSyncStatus.SUCCESSFUL,
            autoSyncEnabled = true,
            encryptionEnabled = false
        )

        composeRule.setContent {
            DolilTheme(language = lang) {
                ProfileSettingsScreen(
                    language = lang,
                    preferences = LocalUserPreferences(language = lang, themeMode = ThemeMode.LIGHT),
                    userProfile = UserProfile(
                        userId = "uid-dev-1",
                        fullName = "User One",
                        email = "user1@example.com",
                        role = UserRole.PUBLIC_USER,
                        authProviderIds = listOf("password")
                    ),
                    pendingSyncCount = 0,
                    adminUsers = emptyList(),
                    onUpdateProfileNamePhone = { _, _ -> },
                    onLanguageChange = { lang = it },
                    onThemeChange = {},
                    onSyncNow = {},
                    onAssignUserRole = { _, _, _, _ -> },
                    onSignOut = {},
                    driveLinkState = connectedLink,
                    onDisconnectDrive = { disconnectCalled = true },
                    onRevokeDriveAccess = { revokeCalled = true },
                    readinessReport = CredentialReadinessChecker.inspect(context, "uid-dev-1")
                )
            }
        }

        composeRule.waitForIdle()
        listOf(
            "section_appearance",
            "section_language_guide",
            "section_account",
            "section_cloud_sync",
            "section_app_info",
            "section_developer_identity"
        ).forEach { tag ->
            composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        // Verify Developer Identity exact text & zero fabricated contact info in EN
        composeRule.onNodeWithText("6. Meet the Developer").assertIsDisplayed()
        composeRule.onNodeWithText("JH").assertIsDisplayed()
        composeRule.onNodeWithText("Jewel Hossain").assertIsDisplayed()
        composeRule.onNodeWithText("App Developer").assertIsDisplayed()

        // Verify Disconnect vs Revoke Access actions in Cloud section
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_disconnect_drive_button"))
        composeRule.onNodeWithTag("profile_disconnect_drive_button").performClick()
        composeRule.waitForIdle()
        assertTrue(disconnectCalled)

        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_revoke_drive_button"))
        composeRule.onNodeWithTag("profile_revoke_drive_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Revoke Google Drive Access?").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_dismiss_button").performClick()
        composeRule.waitForIdle()
        assertFalse(revokeCalled)

        // Switch to Bengali and verify Developer Identity localization
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_lang_bn"))
        composeRule.onNodeWithTag("profile_lang_bn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("section_developer_identity"))
        composeRule.onNodeWithText("৬. নির্মাতার পরিচয়").assertIsDisplayed()
        composeRule.onNodeWithText("জুয়েল হোসাইন").assertIsDisplayed()
        composeRule.onNodeWithText("অ্যাপ নির্মাতা").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 3. AuthValidator & AccessSelection Email/Password UI
    // -------------------------------------------------------------------------

    @Test
    fun `auth validator enforces strong registration rules and UI switches cleanly`() {
        val invalid = AuthValidator.validateRegistration(
            fullName = "A",
            email = "bad-email",
            password = "short",
            confirmPassword = "other"
        )
        assertTrue(AuthValidationError.INVALID_FULL_NAME in invalid)
        assertTrue(AuthValidationError.INVALID_EMAIL in invalid)
        assertTrue(AuthValidationError.PASSWORD_TOO_SHORT in invalid)
        assertTrue(AuthValidationError.PASSWORDS_DO_NOT_MATCH in invalid)

        val missingDigit = AuthValidator.validateRegistration(
            fullName = "Jewel Hossain",
            email = "jewel@example.com",
            password = "onlyletters",
            confirmPassword = "onlyletters"
        )
        assertEquals(listOf(AuthValidationError.PASSWORD_NEEDS_LETTER_AND_DIGIT), missingDigit)

        val valid = AuthValidator.validateRegistration(
            fullName = "Jewel Hossain",
            email = "jewel@example.com",
            password = "StrongPass1",
            confirmPassword = "StrongPass1"
        )
        assertTrue(valid.isEmpty())

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                AccessSelectionScreen(
                    language = AppLanguage.EN,
                    onLanguageChange = {},
                    onEnterGuestMode = {},
                    onViewOnboardingAgain = {},
                    onAuthSuccess = {}
                )
            }
        }

        composeRule.waitForIdle()
        // These controls live in a vertically scrollable screen; bring each into the viewport
        // before asserting visibility or interacting with it.
        composeRule.onNodeWithTag("access_login_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("access_guest_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("access_mode_register").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Keep the test selectors aligned with the production test tags on AccessSelectionScreen.
        composeRule.onNodeWithTag("access_fullname_input").performScrollTo().assertIsDisplayed()
            .performTextInput("Test User")
        composeRule.onNodeWithTag("access_email_input").performScrollTo().assertIsDisplayed()
            .performTextInput("invalid")
        composeRule.onNodeWithTag("access_password_input").performScrollTo().assertIsDisplayed()
            .performTextInput("abc")
        composeRule.onNodeWithTag("access_confirm_password_input").performScrollTo().assertIsDisplayed()
            .performTextInput("xyz")
        composeRule.onNodeWithTag("access_email_submit_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("error_banner").performScrollTo().assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 4. Room v1 -> v2 Migration & Strict UID Partitioning
    // -------------------------------------------------------------------------

    @Test
    fun `room migration v1 to v2 preserves pre-existing deeds under legacy local uid and isolates multiple users`() {
        val dbName = "migration_test_${UUID.randomUUID()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper: SupportSQLiteOpenHelper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS deeds (
                                id TEXT NOT NULL PRIMARY KEY,
                                createdBy TEXT NOT NULL,
                                updatedBy TEXT NOT NULL,
                                organizationId TEXT NOT NULL,
                                officeId TEXT NOT NULL,
                                officeName TEXT NOT NULL,
                                deedType TEXT NOT NULL,
                                deedNumber TEXT NOT NULL,
                                normalizedDeedNumber TEXT NOT NULL,
                                deedYear INTEGER NOT NULL,
                                registrationDateMillis INTEGER NOT NULL,
                                districtName TEXT NOT NULL,
                                upazilaName TEXT NOT NULL,
                                unionName TEXT NOT NULL,
                                mouzaName TEXT NOT NULL,
                                jlNumber TEXT NOT NULL,
                                status TEXT NOT NULL,
                                visibility TEXT NOT NULL,
                                rowVersion INTEGER NOT NULL,
                                primaryGrantorName TEXT NOT NULL,
                                primaryGranteeName TEXT NOT NULL,
                                totalAreaDecimalText TEXT NOT NULL,
                                grantorsJson TEXT NOT NULL,
                                granteesJson TEXT NOT NULL,
                                parcelsJson TEXT NOT NULL,
                                searchTokensJoined TEXT NOT NULL,
                                notes TEXT NOT NULL,
                                syncState TEXT NOT NULL,
                                createdAtMillis INTEGER NOT NULL,
                                updatedAtMillis INTEGER NOT NULL,
                                deletedAtMillis INTEGER
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_deeds_normalizedDeedNumber ON deeds(normalizedDeedNumber)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_deeds_deedYear ON deeds(deedYear)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_deeds_officeId_deedYear_normalizedDeedNumber ON deeds(officeId, deedYear, normalizedDeedNumber)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_deeds_status ON deeds(status)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_deeds_visibility ON deeds(visibility)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_deeds_registrationDateMillis ON deeds(registrationDateMillis)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_deeds_mouzaName ON deeds(mouzaName)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_deeds_updatedAtMillis ON deeds(updatedAtMillis)")
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS audit_logs (
                                id TEXT NOT NULL PRIMARY KEY,
                                actorId TEXT NOT NULL,
                                actorName TEXT NOT NULL,
                                organizationId TEXT NOT NULL,
                                officeId TEXT NOT NULL,
                                action TEXT NOT NULL,
                                entityType TEXT NOT NULL,
                                entityId TEXT NOT NULL,
                                previousVersion INTEGER NOT NULL,
                                newVersion INTEGER NOT NULL,
                                summary TEXT NOT NULL,
                                createdAtMillis INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_entityId ON audit_logs(entityId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_createdAtMillis ON audit_logs(createdAtMillis)")
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS pending_sync (
                                deedId TEXT NOT NULL PRIMARY KEY,
                                operation TEXT NOT NULL,
                                queuedAtMillis INTEGER NOT NULL,
                                retryCount INTEGER NOT NULL,
                                lastError TEXT
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO deeds (
                                id, createdBy, updatedBy, organizationId, officeId, officeName,
                                deedType, deedNumber, normalizedDeedNumber, deedYear, registrationDateMillis,
                                districtName, upazilaName, unionName, mouzaName, jlNumber, status, visibility,
                                rowVersion, primaryGrantorName, primaryGranteeName, totalAreaDecimalText,
                                grantorsJson, granteesJson, parcelsJson, searchTokensJoined, notes, syncState,
                                createdAtMillis, updatedAtMillis, deletedAtMillis
                            ) VALUES (
                                'legacy-1', 'old-user', 'old-user', 'org', 'off', 'Office',
                                'SAF_KABALA', '777/2020', '777/2020', 2020, 1000,
                                'Narayanganj', 'Fatulla', '', 'Fatulla', '140', 'APPROVED', 'PUBLIC',
                                1, 'Karim', 'Rahim', '10.0', '[]', '[]', '[]', '777/2020 karim', '', 'SYNCED',
                                1000, 1000, NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, DolilDatabase::class.java, dbName)
            .addMigrations(DolilDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = migratedDb.dolilDao()
            runBlocking {
                val legacyRow = dao.getDeedById("legacy-1")
                assertNotNull(legacyRow)
                assertEquals(LEGACY_LOCAL_OWNER_UID, legacyRow?.ownerUid)
                assertEquals(1, dao.countDeedsByOwner(LEGACY_LOCAL_OWNER_UID))

                val repoA = DolilSyncRepository(dao = dao, ownerUid = "uid_alpha", enableCloudSync = true)
                val repoB = DolilSyncRepository(dao = dao, ownerUid = "uid_beta", enableCloudSync = true)

                // Neither uid_alpha nor uid_beta automatically steals legacy data or sees each other's deeds
                assertEquals(0, repoA.observeLocalDeeds().first().size)
                assertEquals(0, repoB.observeLocalDeeds().first().size)

                val userA = UserProfile(userId = "uid_alpha", fullName = "Alpha", role = UserRole.PUBLIC_USER)
                repoA.saveDeedLocallyAndTrySync(
                    DeedRecord(id = "alpha-1", deedNumber = "111/2026", mouzaName = "A"),
                    userA,
                    AuditAction.CREATE
                )
                assertEquals(1, repoA.observeLocalDeeds().first().size)
                assertEquals(0, repoB.observeLocalDeeds().first().size)

                // Explicit migration moves legacy deeds into uid_alpha cleanly
                repoA.migrateLegacyLocalDataToCurrentOwner()
                assertEquals(2, repoA.observeLocalDeeds().first().size)
                assertEquals(0, dao.countDeedsByOwner(LEGACY_LOCAL_OWNER_UID))
                assertEquals(0, repoB.observeLocalDeeds().first().size)
            }
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    // -------------------------------------------------------------------------
    // 5. Namespace Isolation & PBKDF2 + AES-256-GCM Crypto Engine
    // -------------------------------------------------------------------------

    @Test
    fun `namespace sha256 matches contract and crypto engine enforces aes-256-gcm aad and sha256`() = runBlocking {
        val uid1 = "firebase_uid_1"
        val uid2 = "firebase_uid_2"
        val expectedDigest = MessageDigest.getInstance("SHA-256")
            .digest("dolil-onsundondhan-backup-v1:$uid1".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val ns1 = NamespaceHelper.computeAccountNamespaceId(uid1)
        val ns2 = NamespaceHelper.computeAccountNamespaceId(uid2)
        assertEquals(expectedDigest, ns1)
        assertNotEquals(ns1, ns2)
        assertEquals(64, ns1.length)

        val crypto = DriveCryptoEngine(defaultIterations = DriveCryptoEngine.MIN_PBKDF2_ITERATIONS)
        val sampleBytes = "Dolil Onusondhan Confidential Deed Backup Payload".toByteArray(Charsets.UTF_8)

        val env1 = crypto.sealPayload(
            plaintextBytes = sampleBytes,
            backupId = "backup-1",
            sequenceNumber = 1L,
            timestampUtc = "2026-11-24T00:00:00Z",
            deviceId = "dev-a",
            accountNamespaceId = ns1,
            driveSubjectId = "sub-1",
            schemaVersion = 1,
            passphrase = "MyStrongPassphrase!99".toCharArray()
        )
        val env2 = crypto.sealPayload(
            plaintextBytes = sampleBytes,
            backupId = "backup-1",
            sequenceNumber = 1L,
            timestampUtc = "2026-11-24T00:00:00Z",
            deviceId = "dev-a",
            accountNamespaceId = ns1,
            driveSubjectId = "sub-1",
            schemaVersion = 1,
            passphrase = "MyStrongPassphrase!99".toCharArray()
        )

        assertTrue(env1.encrypted)
        assertEquals("AES-256-GCM", env1.encryptionMetadata?.algorithm)
        assertEquals("PBKDF2WithHmacSHA256", env1.encryptionMetadata?.kdf)
        assertEquals(600_000, env1.encryptionMetadata?.iterations)
        assertNotEquals(env1.encryptionMetadata?.saltBase64, env2.encryptionMetadata?.saltBase64)
        assertNotEquals(env1.encryptionMetadata?.ivBase64, env2.encryptionMetadata?.ivBase64)

        val decrypted = crypto.unsealPayload(env1, "MyStrongPassphrase!99".toCharArray())
        assertTrue(decrypted.isSuccess)
        assertArrayEquals(sampleBytes, decrypted.getOrThrow())

        // Wrong passphrase fails
        assertTrue(crypto.unsealPayload(env1, "WrongPass!".toCharArray()).isFailure)

        // Tampered AAD fails
        val tamperedAad = env1.copy(backupId = "backup-other")
        assertTrue(crypto.unsealPayload(tamperedAad, "MyStrongPassphrase!99".toCharArray()).isFailure)

        // Plaintext SHA-256 mismatch fails
        val plainEnv = crypto.sealPayload(
            plaintextBytes = sampleBytes,
            backupId = "backup-plain",
            sequenceNumber = 2L,
            timestampUtc = "2026-11-24T00:00:00Z",
            deviceId = "dev-a",
            accountNamespaceId = ns1,
            driveSubjectId = "sub-1",
            schemaVersion = 1,
            passphrase = null
        )
        assertTrue(crypto.unsealPayload(plainEnv.copy(payloadSha256 = "0".repeat(64)), null).isFailure)
    }

    // -------------------------------------------------------------------------
    // 6. DriveBackupEngine: Namespace Isolation, Orphan Recovery, Conflicts,
    //    Tombstones, Device Retirement & Portable Export/Import
    // -------------------------------------------------------------------------

    @Test
    fun `drive engine isolates uids on same drive account and rejects drive mismatch`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.dolilDao()
            val store = DeviceIdentityStore(context)
            val fakeDrive = FakeDriveRemoteStorage()
            val engine = DriveBackupEngine(fakeDrive, dao, store, DriveCryptoEngine(defaultIterations = 10_000))
            val sharedDrive = DriveAccountInfo(subjectId = "drive-shared-sub", email = "shared@gmail.com")

            // User 1 saves a deed and syncs
            val repo1 = DolilSyncRepository(dao, ownerUid = "firebase_uid_1", enableCloudSync = true)
            repo1.saveDeedLocallyAndTrySync(
                DeedRecord(id = "u1-deed", deedNumber = "100/2026", mouzaName = "Fatulla"),
                UserProfile(userId = "firebase_uid_1", fullName = "User1"),
                AuditAction.CREATE
            )
            val res1 = engine.syncNow("token-1", sharedDrive, "firebase_uid_1", null)
            assertTrue(res1 is DriveSyncOutcome.Synced)

            // User 2 on same shared Drive account does NOT see User 1's deed
            val res2 = engine.syncNow("token-1", sharedDrive, "firebase_uid_2", null, forceUploadSnapshot = true)
            assertTrue(res2 is DriveSyncOutcome.Synced)
            assertEquals(0, dao.getActiveDeedsForOwnerOnce("firebase_uid_2").size)

            // Different Drive sub for firebase_uid_1 triggers AccountMismatch
            val wrongDrive = DriveAccountInfo(subjectId = "drive-other-sub", email = "other@gmail.com")
            val mismatch = engine.syncNow("token-2", wrongDrive, "firebase_uid_1", null)
            assertTrue(mismatch is DriveSyncOutcome.AccountMismatch)
        } finally {
            db.close()
        }
    }

    @Test
    fun `drive engine recovers orphaned backup and handles multi-device conflict resolution`() = runBlocking {
        val dbA = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java).allowMainThreadQueries().build()
        val dbB = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java).allowMainThreadQueries().build()
        try {
            val fakeDrive = FakeDriveRemoteStorage()
            val storeA = DeviceIdentityStore(context, "dolil_identity_store_a_${UUID.randomUUID()}")
            val storeB = DeviceIdentityStore(context, "dolil_identity_store_b_${UUID.randomUUID()}")
            val uid = "uid-multi-1"
            val driveAccount = DriveAccountInfo(subjectId = "drive-sub-1", email = "owner@gmail.com")
            val actor = UserProfile(userId = uid, fullName = "Jewel")

            val repoA = DolilSyncRepository(
                dao = dbA.dolilDao(),
                ownerUid = uid,
                deviceId = storeA.getOrCreateDeviceId(),
                enableCloudSync = true
            )
            val repoB = DolilSyncRepository(
                dao = dbB.dolilDao(),
                ownerUid = uid,
                deviceId = storeB.getOrCreateDeviceId(),
                enableCloudSync = true
            )

            // Device A creates initial deed and syncs
            val engineA = DriveBackupEngine(fakeDrive, dbA.dolilDao(), storeA, DriveCryptoEngine(defaultIterations = 10_000))
            repoA.saveDeedLocallyAndTrySync(
                DeedRecord(id = "deed-shared", deedNumber = "500/2026", mouzaName = "Original Mouza", rowVersion = 1L),
                actor,
                AuditAction.CREATE
            )
            val firstSync = engineA.syncNow("tok", driveAccount, uid, null)
            assertTrue(firstSync is DriveSyncOutcome.Synced)

            // Device B pulls initial backup
            val engineB = DriveBackupEngine(fakeDrive, dbB.dolilDao(), storeB, DriveCryptoEngine(defaultIterations = 10_000))
            val pullB = engineB.syncNow("tok", driveAccount, uid, null)
            assertTrue(pullB is DriveSyncOutcome.Synced)
            assertEquals("Original Mouza", dbB.dolilDao().getDeedByIdForOwner("deed-shared", uid)?.mouzaName)

            // Simulate orphaned backup in Drive (backup uploaded without commit!)
            val link = storeA.getDriveLink(uid)
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val envAdapter = moshi.adapter(DriveBackupEnvelope::class.java)
            val payloadJson = """{"schemaVersion":1,"accountNamespaceId":"${link.accountNamespaceId}","driveSubjectId":"${driveAccount.subjectId}","exportedAtUtc":"2026-11-24T01:00:00Z","deeds":[],"auditLogs":[]}"""
            val orphanEnv = DriveCryptoEngine(10_000).sealPayload(
                plaintextBytes = payloadJson.toByteArray(Charsets.UTF_8),
                backupId = "backup-20261124T010000Z-orphan",
                sequenceNumber = 99L,
                timestampUtc = "2026-11-24T01:00:00Z",
                deviceId = "device-other",
                accountNamespaceId = link.accountNamespaceId,
                driveSubjectId = driveAccount.subjectId,
                schemaVersion = 1,
                passphrase = null
            )
            fakeDrive.uploadJsonResumable(
                accessToken = "tok",
                preGeneratedOrExistingId = null,
                fileName = "backup-20261124T010000Z-orphan.json",
                parentFolderId = link.folderIds.backupsFolderId,
                jsonBytes = envAdapter.toJson(orphanEnv).toByteArray(Charsets.UTF_8),
                isUpdateExisting = false
            )

            // Next sync recovers orphaned backup and creates commit!
            val recoverSync = engineA.syncNow("tok", driveAccount, uid, null)
            assertTrue(recoverSync is DriveSyncOutcome.Synced)
            assertEquals(1, (recoverSync as DriveSyncOutcome.Synced).orphanedRecoveredCount)

            // Now simulate concurrent edit from another device vs Device B pending edit -> ConflictDetected!
            val deedOnB = repoB.observeLocalDeeds().first().first()
            repoB.saveDeedLocallyAndTrySync(
                deedOnB.copy(mouzaName = "Device B Edit", updatedAtMillis = System.currentTimeMillis() + 500),
                actor,
                AuditAction.UPDATE
            )
            // Also make a synthetic remote commit from "device-remote-X" modifying same deed-shared after lastSyncUtcMillis
            val remoteEditedDeed = deedOnB.copy(
                mouzaName = "Remote X Edit",
                deviceId = "device-remote-X",
                revisionId = "rev-remote-x",
                rowVersion = 5L,
                updatedAtMillis = System.currentTimeMillis() + 10_000
            )
            val remotePayloadJson = """{"schemaVersion":1,"accountNamespaceId":"${link.accountNamespaceId}","driveSubjectId":"${driveAccount.subjectId}","exportedAtUtc":"2026-11-24T02:00:00Z","deeds":[${moshi.adapter(DeedRecord::class.java).toJson(remoteEditedDeed)}],"auditLogs":[]}"""
            val remoteBackupId = "backup-20261124T020000Z-${UUID.randomUUID()}"
            val remoteEnv = DriveCryptoEngine(10_000).sealPayload(
                plaintextBytes = remotePayloadJson.toByteArray(Charsets.UTF_8),
                backupId = remoteBackupId,
                sequenceNumber = 100L,
                timestampUtc = "2026-11-24T02:00:00Z",
                deviceId = "device-remote-X",
                accountNamespaceId = link.accountNamespaceId,
                driveSubjectId = driveAccount.subjectId,
                schemaVersion = 1,
                passphrase = null
            )
            fakeDrive.uploadJsonResumable(
                "tok", null, "$remoteBackupId.json", link.folderIds.backupsFolderId,
                envAdapter.toJson(remoteEnv).toByteArray(Charsets.UTF_8), false
            )
            val remoteCommit = DriveCommitRecord(
                commitId = "commit-20261124T020000Z-remote",
                backupId = remoteBackupId,
                sequenceNumber = 100L,
                parentCommitIds = emptyList(),
                deviceId = "device-remote-X",
                deviceSequence = 1L,
                timestampUtc = "2026-11-24T02:00:00Z",
                accountNamespaceId = link.accountNamespaceId,
                driveSubjectId = driveAccount.subjectId,
                payloadChecksum = remoteEnv.payloadSha256
            )
            fakeDrive.uploadJsonResumable(
                "tok", null, "${remoteCommit.commitId}.json", link.folderIds.commitsFolderId,
                moshi.adapter(DriveCommitRecord::class.java).toJson(remoteCommit).toByteArray(Charsets.UTF_8), false
            )

            // Adjust Device B's lastSyncUtcMillis slightly earlier so remote edit is treated as concurrent remote change
            storeB.saveDriveLink(storeB.getDriveLink(uid).copy(lastSyncUtcMillis = System.currentTimeMillis() - 10_000))
            val conflictOutcome = engineB.syncNow("tok", driveAccount, uid, null)
            assertTrue(conflictOutcome is DriveSyncOutcome.ConflictDetected)
            val conflict = (conflictOutcome as DriveSyncOutcome.ConflictDetected).conflicts.first()

            // Test Keep Both 3-way resolution preserves both local & Drive copy cleanly
            repoB.resolveConflictKeepBoth(conflict, actor)
            val allAfterKeepBoth = repoB.observeLocalDeeds().first()
            assertEquals(2, allAfterKeepBoth.size)
            assertTrue(allAfterKeepBoth.any { it.mouzaName == "Device B Edit" })
            assertTrue(allAfterKeepBoth.any { it.mouzaName == "Remote X Edit" })
        } finally {
            dbA.close()
            dbB.close()
        }
    }

    @Test
    fun `portable export import tombstones and device retirement work reliably`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dolilDao()
            val store = DeviceIdentityStore(context)
            val fakeDrive = FakeDriveRemoteStorage()
            val engine = DriveBackupEngine(fakeDrive, dao, store, DriveCryptoEngine(10_000))
            val uid = "uid-export-1"
            val driveAcc = DriveAccountInfo(subjectId = "drive-sub-ex", email = "export@gmail.com")
            val repo = DolilSyncRepository(dao, ownerUid = uid, enableCloudSync = true)
            val actor = UserProfile(userId = uid, fullName = "Jewel")

            val deed = DeedRecord(id = "d-exp-1", deedNumber = "888/2026", mouzaName = "Chashara")
            repo.saveDeedLocallyAndTrySync(deed, actor, AuditAction.CREATE)

            // Sync & check device listing + retirement
            val synced = engine.syncNow("tok", driveAcc, uid, null)
            assertTrue(synced is DriveSyncOutcome.Synced)
            val devices = engine.listDevices("tok", driveAcc, uid).getOrThrow()
            assertEquals(1, devices.size)
            assertFalse(devices.first().retired)

            engine.retireDevice("tok", uid, devices.first().deviceId).getOrThrow()
            val retiredDevices = engine.listDevices("tok", driveAcc, uid).getOrThrow()
            assertTrue(retiredDevices.first().retired)

            // Export portable JSON -> Tombstone local -> Import JSON into fresh UID
            val portableJson = engine.exportPortableBundleJson(uid)
            repo.permanentlyDeleteWithTombstone(deed, actor)
            assertEquals(0, repo.observeLocalDeeds().first().size)
            assertTrue(dao.getAllDeedsIncludingTombstonesForOwnerOnce(uid).first().isDeleted)

            val importedCount = engine.importPortableBundleJson(portableJson, "uid-restored", "dev-2").getOrThrow()
            assertEquals(1, importedCount)
            assertEquals(1, dao.getActiveDeedsForOwnerOnce("uid-restored").size)
        } finally {
            db.close()
        }
    }

    // -------------------------------------------------------------------------
    // 7. WorkManager DriveSyncWorker Consent Gate & UID Abort
    // -------------------------------------------------------------------------

    @Test
    fun `drive sync worker sets AUTHORIZATION_REQUIRED when consent needed and aborts cleanly on uid mismatch`() = runBlocking {
        val store = DeviceIdentityStore(context)
        val uid = "worker-uid-1"
        val ns = NamespaceHelper.computeAccountNamespaceId(uid)
        store.saveDriveLink(
            DriveLinkState(
                firebaseUid = uid,
                accountNamespaceId = ns,
                driveSubjectId = "sub-w-1",
                driveEmail = "worker@gmail.com",
                autoSyncEnabled = true
            )
        )

        // Case A: PendingIntent / NeedsResolution must NOT launch UI from background; sets AUTHORIZATION_REQUIRED
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent("com.example.TEST_CONSENT"),
            PendingIntent.FLAG_IMMUTABLE
        )
        DriveSyncWorkerDependencies.currentFirebaseUidProvider = { uid }
        DriveSyncWorkerDependencies.authGatewayFactory = {
            object : DriveAuthGateway {
                override suspend fun requestInteractiveAuthorization(
                    context: Context,
                    targetAccountEmail: String?,
                    forceSelectAccount: Boolean
                ) = DriveAuthResult.NeedsResolution(pendingIntent)

                override suspend fun authorizeSilently(context: Context, targetAccountEmail: String?) =
                    DriveAuthResult.NeedsResolution(pendingIntent)

                override suspend fun handleResolutionResult(context: Context, resultData: Intent?) =
                    DriveAuthResult.Cancelled()

                override suspend fun revokeGoogleAccess(context: Context, lastAccessToken: String?) =
                    Result.success(Unit)
            }
        }

        val workerA = TestListenableWorkerBuilder<DriveSyncWorker>(context)
            .setInputData(Data.Builder().putString(DriveSyncWorker.KEY_EXPECTED_UID, uid).build())
            .build()
        val resultA = workerA.doWork()
        assertEquals(ListenableWorker.Result.failure(), resultA)
        assertEquals(DriveSyncStatus.AUTHORIZATION_REQUIRED, store.getDriveLink(uid).syncStatus)

        // Case B: UID mismatch aborts without syncing wrong account
        DriveSyncWorkerDependencies.currentFirebaseUidProvider = { "different-uid-2" }
        var silentCalls = 0
        DriveSyncWorkerDependencies.authGatewayFactory = {
            object : DriveAuthGateway {
                override suspend fun requestInteractiveAuthorization(
                    context: Context,
                    targetAccountEmail: String?,
                    forceSelectAccount: Boolean
                ) = DriveAuthResult.Cancelled()

                override suspend fun authorizeSilently(context: Context, targetAccountEmail: String?): DriveAuthResult {
                    silentCalls++
                    return DriveAuthResult.Cancelled()
                }

                override suspend fun handleResolutionResult(context: Context, resultData: Intent?) =
                    DriveAuthResult.Cancelled()

                override suspend fun revokeGoogleAccess(context: Context, lastAccessToken: String?) =
                    Result.success(Unit)
            }
        }
        val workerB = TestListenableWorkerBuilder<DriveSyncWorker>(context)
            .setInputData(Data.Builder().putString(DriveSyncWorker.KEY_EXPECTED_UID, uid).build())
            .build()
        val resultB = workerB.doWork()
        assertEquals(ListenableWorker.Result.success(), resultB)
        assertEquals(0, silentCalls)
    }

    // -------------------------------------------------------------------------
    // In-Memory Fake Drive v3 Remote Storage for Deterministic Unit Tests
    // -------------------------------------------------------------------------

    private class FakeDriveRemoteStorage : DriveRemoteStorage {
        private data class StoredFile(
            val meta: DriveFileMetadata,
            val content: ByteArray
        )

        private val files = linkedMapOf<String, StoredFile>()

        override suspend fun generateFileIds(accessToken: String, count: Int): List<String> =
            (1..count).map { "fake-id-${UUID.randomUUID()}" }

        override suspend fun listFilesInFolder(
            accessToken: String,
            parentFolderId: String?,
            nameEquals: String?,
            mimeTypeEquals: String?
        ): List<DriveFileMetadata> {
            return files.values
                .map { it.meta }
                .filter { meta ->
                    (parentFolderId == null || parentFolderId in meta.parents) &&
                        (nameEquals == null || meta.name == nameEquals) &&
                        (mimeTypeEquals == null || meta.mimeType == mimeTypeEquals)
                }
        }

        override suspend fun getFileMetadata(accessToken: String, fileId: String): DriveFileMetadata? =
            files[fileId]?.meta

        override suspend fun findOrCreateFolder(
            accessToken: String,
            folderName: String,
            parentFolderId: String?
        ): DriveFileMetadata {
            val existing = listFilesInFolder(
                accessToken = accessToken,
                parentFolderId = parentFolderId,
                nameEquals = folderName,
                mimeTypeEquals = DriveSchemaConstants.MIME_FOLDER
            ).firstOrNull()
            if (existing != null) return existing

            val id = "folder-${UUID.randomUUID()}"
            val meta = DriveFileMetadata(
                id = id,
                name = folderName,
                parents = listOfNotNull(parentFolderId),
                mimeType = DriveSchemaConstants.MIME_FOLDER
            )
            files[id] = StoredFile(meta, ByteArray(0))
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
            val id = preGeneratedOrExistingId?.takeIf { it.isNotBlank() } ?: "file-${UUID.randomUUID()}"
            if (!isUpdateExisting && files.containsKey(id)) {
                throw DriveHttpException(409, "Already exists")
            }
            val meta = DriveFileMetadata(
                id = id,
                name = fileName,
                parents = listOf(parentFolderId),
                mimeType = DriveSchemaConstants.MIME_JSON,
                sizeBytes = jsonBytes.size.toLong(),
                modifiedTimeUtc = "2026-11-24T00:00:00Z"
            )
            files[id] = StoredFile(meta, jsonBytes.copyOf())
            return meta
        }

        override suspend fun downloadFileBytes(accessToken: String, fileId: String): ByteArray =
            files[fileId]?.content ?: throw DriveHttpException(404, "Missing $fileId")

        override suspend fun deleteFile(accessToken: String, fileId: String) {
            files.remove(fileId)
        }
    }
}
