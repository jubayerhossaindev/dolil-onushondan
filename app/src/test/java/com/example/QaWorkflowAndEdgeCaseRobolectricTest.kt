package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.RolePermissions
import com.example.data.local.DolilDao
import com.example.data.local.DolilDatabase
import com.example.data.local.UserPreferencesDataStore
import com.example.data.repository.DolilSyncRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedConflict
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedType
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandClass
import com.example.domain.model.LandParcel
import com.example.domain.model.LandUnit
import com.example.domain.model.PartyInfo
import com.example.domain.model.SortOption
import com.example.domain.model.SurveyType
import com.example.domain.model.SyncState
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.domain.search.DeedSorter
import com.example.domain.search.DefaultIndexedSearchStrategy
import com.example.domain.search.SearchNormalizer
import com.example.domain.validation.DeedValidator
import com.example.domain.validation.ValidationErrorCode
import com.example.ui.DolilViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QaWorkflowAndEdgeCaseRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: DolilDatabase
    private lateinit var dao: DolilDao
    private lateinit var syncRepo: DolilSyncRepository
    private lateinit var prefsStore: UserPreferencesDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dolilDao()
        syncRepo = DolilSyncRepository(dao = dao, ownerUid = "qa_admin_1", enableCloudSync = true)
        prefsStore = UserPreferencesDataStore(context)
    }

    @After
    fun tearDown() {
        runBlocking {
            prefsStore.setUseDynamicColor(false)
            prefsStore.setLanguage(AppLanguage.BN)
            prefsStore.setThemeMode(ThemeMode.SYSTEM)
        }
        db.close()
    }

    private fun awaitCondition(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 8_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun createVm(
        vmStore: ViewModelStore,
        userId: String,
        email: String,
        displayName: String,
        guest: Boolean
    ): DolilViewModel = ViewModelProvider(
        vmStore,
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DolilViewModel(
                syncRepo = syncRepo,
                prefsStore = prefsStore,
                currentUserId = userId,
                authEmail = email,
                authDisplayName = displayName,
                isGuestMode = guest
            ) as T
        }
    )[DolilViewModel::class.java]

    @Test
    fun `qa_test_2026 viewModel CRUD archive undo and restore lifecycle updates Room and flows`() {
        val vmStore = ViewModelStore()
        try {
            val vm = createVm(
                vmStore = vmStore,
                userId = "qa_admin_1",
                email = "jubayerhossaingalaxy@gmail.com",
                displayName = "QA Supervisor",
                guest = false
            )
            composeRule.setContent {
                vm.allDeedsState.collectAsStateWithLifecycle()
                vm.searchResults.collectAsStateWithLifecycle()
                vm.preferences.collectAsStateWithLifecycle()
                vm.validationErrors.collectAsStateWithLifecycle()
                vm.lastArchivedDeed.collectAsStateWithLifecycle()
            }
            awaitCondition { vm.allDeedsState.value is UiState.Success }

            val badDeed = DeedRecord(
                id = "QA_TEST_2026_BAD",
                deedNumber = "   ",
                deedYear = 1850,
                registrationDateMillis = 0L
            )
            var savedCallbackHit = false
            vm.saveDeed(badDeed, saveAsDraft = false) { savedCallbackHit = true }
            awaitCondition { ValidationErrorCode.REQUIRED_DEED_NUMBER in vm.validationErrors.value }
            assertFalse(savedCallbackHit)
            assertTrue(ValidationErrorCode.INVALID_DEED_YEAR in vm.validationErrors.value)

            vm.clearValidationError(ValidationErrorCode.REQUIRED_DEED_NUMBER)
            assertFalse(ValidationErrorCode.REQUIRED_DEED_NUMBER in vm.validationErrors.value)
            vm.clearValidationErrors()
            assertTrue(vm.validationErrors.value.isEmpty())

            val validDeed = DeedRecord(
                id = "QA_TEST_2026_DEED_1",
                deedNumber = "QA_TEST_2026_901",
                deedYear = 2026,
                deedType = DeedType.SAF_KABALA,
                registrationDateMillis = System.currentTimeMillis(),
                officeId = "sr_narayanganj_sadar",
                officeName = "Sub-Registry Office, Narayanganj Sadar",
                districtName = "Narayanganj",
                upazilaName = "Fatulla Revenue Circle",
                mouzaName = "Deulpara",
                jlNumber = "149",
                grantors = listOf(PartyInfo(fullName = "QA_TEST_2026_Grantor")),
                grantees = listOf(PartyInfo(fullName = "QA_TEST_2026_Grantee")),
                parcels = listOf(
                    LandParcel(
                        surveyType = SurveyType.RS,
                        khatianNumber = "777",
                        dagNumber = "888",
                        landClass = LandClass.NAL,
                        landAreaDecimal = "25.5",
                        landUnit = LandUnit.DECIMAL
                    )
                )
            )

            var createdDeed: DeedRecord? = null
            vm.saveDeed(validDeed, saveAsDraft = false) { createdDeed = it }
            awaitCondition { createdDeed != null }
            assertEquals(DeedStatus.APPROVED, createdDeed?.status)
            assertEquals(DeedVisibility.PUBLIC, createdDeed?.visibility)

            // Update existing deed
            val updatedCandidate = createdDeed!!.copy(notes = "QA_TEST_2026_UPDATED_NOTE")
            var updatedDeed: DeedRecord? = null
            vm.saveDeed(updatedCandidate, saveAsDraft = false) { updatedDeed = it }
            awaitCondition { updatedDeed?.notes == "QA_TEST_2026_UPDATED_NOTE" }

            // Search and filter by query + type + year + district + mouza
            vm.updateSearchQuery("QA_TEST_2026_901")
            vm.updateFilters(
                deedType = DeedType.SAF_KABALA,
                year = 2026,
                district = "Narayanganj",
                mouza = "Deulpara",
                sortOption = SortOption.LAND_AREA_DESC
            )
            awaitCondition { vm.searchResults.value.any { it.deed.id == "QA_TEST_2026_DEED_1" } }
            assertEquals("QA_TEST_2026_DEED_1", vm.searchResults.value.first().deed.id)

            // Soft Delete -> Undo Archive -> Soft Delete -> Explicit Restore
            vm.softDeleteDeed(updatedDeed!!)
            awaitCondition {
                runBlocking { dao.getDeedById("QA_TEST_2026_DEED_1")?.status == DeedStatus.SOFT_DELETED.name } &&
                    vm.lastArchivedDeed.value != null
            }

            vm.undoLastArchive()
            awaitCondition {
                runBlocking { dao.getDeedById("QA_TEST_2026_DEED_1")?.status == DeedStatus.APPROVED.name } &&
                    vm.lastArchivedDeed.value == null
            }

            val liveRecord = runBlocking { dao.getDeedById("QA_TEST_2026_DEED_1")!! }
            vm.softDeleteDeed(
                DeedRecord(
                    id = liveRecord.id,
                    ownerUid = liveRecord.ownerUid,
                    createdBy = liveRecord.createdBy,
                    officeId = liveRecord.officeId,
                    organizationId = liveRecord.organizationId,
                    deedNumber = liveRecord.deedNumber,
                    deedYear = liveRecord.deedYear,
                    status = DeedStatus.APPROVED,
                    visibility = DeedVisibility.PUBLIC
                )
            )
            awaitCondition {
                runBlocking { dao.getDeedById("QA_TEST_2026_DEED_1")?.status == DeedStatus.SOFT_DELETED.name }
            }

            val archivedDeed = runBlocking { dao.getDeedById("QA_TEST_2026_DEED_1")!! }
            vm.restoreDeed(
                validDeed.copy(
                    ownerUid = archivedDeed.ownerUid,
                    createdBy = archivedDeed.createdBy,
                    status = DeedStatus.SOFT_DELETED,
                    visibility = DeedVisibility.valueOf(archivedDeed.visibility)
                )
            )
            awaitCondition {
                runBlocking { dao.getDeedById("QA_TEST_2026_DEED_1")?.status == DeedStatus.APPROVED.name }
            }

            // Conflict resolution keep local vs accept server
            val conflict = DeedConflict(
                localDeed = validDeed.copy(notes = "QA_LOCAL_WINS", rowVersion = 1L),
                serverDeed = validDeed.copy(notes = "QA_SERVER_WINS", rowVersion = 3L)
            )
            vm.resolveConflictKeepLocal(conflict)
            awaitCondition {
                runBlocking { dao.getDeedById("QA_TEST_2026_DEED_1")?.notes == "QA_LOCAL_WINS" }
            }

            vm.resolveConflictAcceptServer(conflict)
            awaitCondition {
                runBlocking {
                    val entity = dao.getDeedById("QA_TEST_2026_DEED_1")
                    entity?.notes == "QA_SERVER_WINS" && entity.syncState == SyncState.SYNCED.name
                }
            }

            // Audit trail entries recorded cleanly
            val auditLogs = runBlocking { vm.observeAuditLogs("QA_TEST_2026_DEED_1").first() }
            assertTrue(auditLogs.size >= 4)

            // Preferences toggles & pagination
            vm.setLanguage(AppLanguage.EN)
            vm.setThemeMode(ThemeMode.DARK)
            vm.setUseDynamicColor(true)
            vm.loadNextPage()
            vm.clearFilters()
            awaitCondition {
                vm.preferences.value.language == AppLanguage.EN &&
                    vm.preferences.value.themeMode == ThemeMode.DARK &&
                    vm.preferences.value.useDynamicColor
            }
            assertEquals(1, vm.searchFilter.value.page)
        } finally {
            vmStore.clear()
        }
    }

    @Test
    fun `qa_test_2026 guest mode blocks cloud sync and profile save with clear status notice`() {
        val vmStore = ViewModelStore()
        try {
            val guestVm = createVm(
                vmStore = vmStore,
                userId = "guest",
                email = "",
                displayName = "Guest",
                guest = true
            )
            composeRule.setContent {
                guestVm.allDeedsState.collectAsStateWithLifecycle()
                guestVm.preferences.collectAsStateWithLifecycle()
            }
            guestVm.setLanguage(AppLanguage.EN)
            awaitCondition {
                guestVm.allDeedsState.value is UiState.Success &&
                    guestVm.preferences.value.language == AppLanguage.EN
            }

            guestVm.syncNow()
            assertTrue(
                guestVm.statusMessage.value?.contains("Demo", ignoreCase = true) == true ||
                    guestVm.statusMessage.value?.contains("Sign in", ignoreCase = true) == true
            )
            guestVm.clearStatusMessage()

            guestVm.updateProfileNamePhone("QA Guest", "01700000000")
            awaitCondition { guestVm.statusMessage.value != null }
            assertTrue(guestVm.statusMessage.value?.contains("Google", ignoreCase = true) == true)
        } finally {
            vmStore.clear()
        }
    }

    @Test
    fun `qa_test_2026 validator boundary edge cases injection strings and Bangla numerals`() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        // Bangla digits & comma decimal in land area + injection-like payload stored safely
        val specialPayloadDeed = DeedRecord(
            id = "QA_TEST_2026_EDGE",
            deedNumber = "  ১২৫৬ / ২০২৬  ",
            deedYear = currentYear + 1,
            registrationDateMillis = 1700000000000L,
            districtName = "নারায়ণগঞ্জ",
            upazilaName = "ফতুল্লা",
            mouzaName = "দেউলপাড়া",
            jlNumber = "১৪৯",
            grantors = listOf(PartyInfo(fullName = "' OR '1'='1; --")),
            grantees = listOf(PartyInfo(fullName = "<script>alert(1)</script>" + "A".repeat(300))),
            parcels = listOf(
                LandParcel(
                    khatianNumber = "১২৩",
                    dagNumber = "৪৫৬",
                    landAreaDecimal = "১২,৫০"
                )
            )
        )
        val okRes = DeedValidator.validate(specialPayloadDeed, isDraft = false, currentYear = currentYear)
        assertTrue("Expected Bangla digits & comma area to pass, got ${okRes.errors}", okRes.isValid)

        // Future year beyond currentYear+1, zero area, missing dag/khatian
        val invalidBoundary = specialPayloadDeed.copy(
            deedYear = currentYear + 2,
            parcels = listOf(LandParcel(khatianNumber = "", dagNumber = "", landAreaDecimal = "0"))
        )
        val errRes = DeedValidator.validate(invalidBoundary, isDraft = false, currentYear = currentYear)
        assertFalse(errRes.isValid)
        assertTrue(ValidationErrorCode.INVALID_DEED_YEAR in errRes.errors)
        assertTrue(ValidationErrorCode.REQUIRED_KHATIAN in errRes.errors)
        assertTrue(ValidationErrorCode.REQUIRED_DAG in errRes.errors)
        assertTrue(ValidationErrorCode.INVALID_LAND_AREA in errRes.errors)

        // Draft rejects malformed negative area while allowing missing parties/location
        val badDraft = DeedRecord(
            id = "QA_TEST_2026_BAD_DRAFT",
            deedNumber = "101",
            deedYear = 2025,
            registrationDateMillis = 1700000000000L,
            parcels = listOf(LandParcel(landAreaDecimal = "-5.2"))
        )
        val badDraftRes = DeedValidator.validate(badDraft, isDraft = true, currentYear = currentYear)
        assertFalse(badDraftRes.isValid)
        assertEquals(listOf(ValidationErrorCode.INVALID_LAND_AREA), badDraftRes.errors)

        // SearchNormalizer handles empty, whitespace, mixed Bangla/English, and injection literals
        assertEquals("", SearchNormalizer.normalizeText("   \n\t  "))
        assertEquals("1256/2026", SearchNormalizer.normalizeCode(" ১২৫৬ / ২০২৬ "))
        val strategy = DefaultIndexedSearchStrategy()
        val ranked = strategy.rank(listOf(specialPayloadDeed), "' or '1'='1")
        assertEquals(1, ranked.size)
    }

    @Test
    fun `qa_test_2026 all 10 SortOptions and pagination helpers behave deterministically`() {
        val d1 = DeedRecord(
            id = "QA_1",
            deedNumber = "10",
            deedYear = 2020,
            registrationDateMillis = 1000L,
            updatedAtMillis = 2000L,
            mouzaName = "B",
            grantors = listOf(PartyInfo(fullName = "Zebra")),
            parcels = listOf(LandParcel(landAreaDecimal = "5"))
        )
        val d2 = DeedRecord(
            id = "QA_2",
            deedNumber = "2",
            deedYear = 2025,
            registrationDateMillis = 5000L,
            updatedAtMillis = 1000L,
            mouzaName = "A",
            grantors = listOf(PartyInfo(fullName = "Alpha")),
            parcels = listOf(LandParcel(landAreaDecimal = "20"))
        )

        SortOption.entries.forEach { option ->
            val sorted = DeedSorter.sort(listOf(d1, d2), option)
            assertEquals("Sort failed for $option", 2, sorted.size)
        }
        assertEquals("QA_2", DeedSorter.sort(listOf(d1, d2), SortOption.LAND_AREA_DESC).first().id)
        assertEquals("QA_1", DeedSorter.sort(listOf(d1, d2), SortOption.DEED_YEAR_ASC).first().id)
        assertEquals("QA_2", DeedSorter.sort(listOf(d1, d2), SortOption.GRANTOR_NAME_ASC).first().id)
        assertEquals("QA_2", DeedSorter.sort(listOf(d1, d2), SortOption.MOUZA_NAME_ASC).first().id)
        assertEquals("QA_1", DeedSorter.sort(listOf(d1, d2), SortOption.UPDATED_AT_DESC).first().id)

        assertEquals(1, DeedSorter.paginate(listOf(d1, d2), page = 1, pageSize = 1).size)
        assertEquals(2, DeedSorter.paginate(listOf(d1, d2), page = 2, pageSize = 1).size)
    }

    @Test
    fun `qa_test_2026 role permissions matrix enforces strict least privilege and suspended denial`() {
        val suspendedSupervisor = UserProfile(
            userId = "sup-suspended",
            role = UserRole.OFFICE_SUPERVISOR,
            officeId = "sr_narayanganj_sadar",
            status = "SUSPENDED"
        )
        val publicUser = UserProfile(userId = "pub-1", role = UserRole.PUBLIC_USER)
        val viewer = UserProfile(userId = "view-1", role = UserRole.OFFICE_VIEWER, officeId = "sr_narayanganj_sadar")
        val entry = UserProfile(userId = "ent-1", role = UserRole.OFFICE_DATA_ENTRY, officeId = "sr_narayanganj_sadar")
        val supervisor = UserProfile(userId = "sup-1", role = UserRole.OFFICE_SUPERVISOR, officeId = "sr_narayanganj_sadar")
        val orgAdmin = UserProfile(userId = "org-1", role = UserRole.ORGANIZATION_ADMIN, organizationId = "org_a")
        val platformAdmin = UserProfile(userId = "plat-1", role = UserRole.PLATFORM_ADMIN)

        val approvedPublic = DeedRecord(id = "d-pub", status = DeedStatus.APPROVED, visibility = DeedVisibility.PUBLIC)
        val officePending = DeedRecord(
            id = "d-off",
            officeId = "sr_narayanganj_sadar",
            organizationId = "org_a",
            status = DeedStatus.PENDING_REVIEW,
            visibility = DeedVisibility.OFFICE_ONLY
        )
        val otherOfficePending = officePending.copy(id = "d-other", officeId = "sr_sonargaon", organizationId = "org_b")
        val ownDraft = DeedRecord(
            id = "d-draft",
            createdBy = "pub-1",
            status = DeedStatus.DRAFT,
            visibility = DeedVisibility.PRIVATE_DRAFT
        )

        // Suspended cannot view/edit/delete/restore even approved public deed
        assertFalse(RolePermissions.canViewDeed(suspendedSupervisor, approvedPublic))
        assertFalse(RolePermissions.canEditDeed(suspendedSupervisor, officePending))
        assertFalse(RolePermissions.canSoftDeleteDeed(suspendedSupervisor, officePending))
        assertFalse(RolePermissions.canRestoreDeed(suspendedSupervisor, officePending.copy(status = DeedStatus.SOFT_DELETED)))

        // Public user sees only public approved + own drafts
        assertTrue(RolePermissions.canViewDeed(publicUser, approvedPublic))
        assertTrue(RolePermissions.canViewDeed(publicUser, ownDraft))
        assertTrue(RolePermissions.canEditDeed(publicUser, ownDraft))
        assertFalse(RolePermissions.canViewDeed(publicUser, officePending))
        assertFalse(RolePermissions.canApproveDeed(publicUser.role))
        assertFalse(RolePermissions.canManageRoles(publicUser.role))

        // Office viewer read-only inside own office
        assertTrue(RolePermissions.canViewDeed(viewer, officePending))
        assertFalse(RolePermissions.canViewDeed(viewer, otherOfficePending))
        assertFalse(RolePermissions.canEditDeed(viewer, officePending))

        // Office data entry edits pending in own office, not approved, cannot approve
        assertTrue(RolePermissions.canEditDeed(entry, officePending))
        assertFalse(RolePermissions.canEditDeed(entry, approvedPublic.copy(officeId = "sr_narayanganj_sadar")))
        assertFalse(RolePermissions.canApproveDeed(entry.role))

        // Supervisor approves & deletes in own office only
        assertTrue(RolePermissions.canApproveDeed(supervisor.role))
        assertTrue(RolePermissions.canSoftDeleteDeed(supervisor, officePending))
        assertFalse(RolePermissions.canSoftDeleteDeed(supervisor, otherOfficePending))
        assertFalse(RolePermissions.canManageRoles(supervisor.role))

        // Org admin scopes to organization; Platform admin global
        assertTrue(RolePermissions.canManageRoles(orgAdmin.role))
        assertTrue(RolePermissions.canEditDeed(orgAdmin, officePending))
        assertFalse(RolePermissions.canEditDeed(orgAdmin, otherOfficePending))
        assertTrue(RolePermissions.canManageRoles(platformAdmin.role))
        assertTrue(RolePermissions.canEditDeed(platformAdmin, otherOfficePending))
    }
}
