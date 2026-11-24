package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.DolilTheme
import com.example.data.local.DriveLinkState
import com.example.data.local.LocalUserPreferences
import com.example.data.local.NamespaceHelper
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedConflict
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedType
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandParcel
import com.example.domain.model.LandUnit
import com.example.domain.model.MatchReason
import com.example.domain.model.OfficeStats
import com.example.domain.model.PartyInfo
import com.example.domain.model.RankedDeedResult
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.domain.validation.ValidationErrorCode
import com.example.ui.ConflictResolutionDialog
import com.example.ui.SearchFilterState
import com.example.ui.screens.AccessSelectionScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeedDetailsScreen
import com.example.ui.screens.DeedEntryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PersonsDirectoryScreen
import com.example.ui.screens.ProductTourOverlay
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.RecordsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TourTargetsState
import com.example.ui.screens.UserGuideScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class QaUiScreensAuditRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val supervisor = UserProfile(
        userId = "qa-sup-1",
        fullName = "QA Supervisor",
        email = "sup@example.com",
        role = UserRole.OFFICE_SUPERVISOR,
        officeId = "sr_narayanganj_sadar",
        officeName = "Sub-Registry Office, Narayanganj Sadar"
    )

    private val adminUser = UserProfile(
        userId = "qa-admin-1",
        fullName = "QA Admin",
        email = "admin@example.com",
        role = UserRole.ORGANIZATION_ADMIN,
        officeId = "sr_narayanganj_sadar",
        officeName = "Sub-Registry Office, Narayanganj Sadar"
    )

    private fun makeDeed(
        id: String,
        number: String,
        status: DeedStatus = DeedStatus.APPROVED,
        year: Int = 2026
    ) = DeedRecord(
        id = id,
        createdBy = "qa-sup-1",
        deedNumber = number,
        deedYear = year,
        deedType = DeedType.SAF_KABALA,
        officeId = "sr_narayanganj_sadar",
        officeName = "Sub-Registry Office, Narayanganj Sadar",
        districtName = "Narayanganj",
        upazilaName = "Fatulla Revenue Circle",
        mouzaName = "Deulpara",
        jlNumber = "149",
        status = status,
        visibility = if (status == DeedStatus.APPROVED) DeedVisibility.PUBLIC else DeedVisibility.OFFICE_ONLY,
        grantors = listOf(PartyInfo(fullName = "QA_TEST_2026_Karim")),
        grantees = listOf(PartyInfo(fullName = "QA_TEST_2026_Rahim")),
        parcels = listOf(LandParcel(khatianNumber = "712", dagNumber = "1845", landAreaDecimal = "15.0"))
    )

    @Test
    fun `qa_test_2026 splash and access selection and onboarding full traversal`() {
        var stepScreen by mutableIntStateOf(0)
        var skipped = false
        var guestEntered = false
        var currentLang by mutableStateOf(AppLanguage.EN)

        composeRule.setContent {
            DolilTheme(language = currentLang) {
                when (stepScreen) {
                    0 -> SplashScreen(
                        language = currentLang,
                        onSplashFinished = {}
                    )
                    1 -> OnboardingScreen(
                        language = currentLang,
                        onLanguageChange = { currentLang = it },
                        onSkipOrFinishToAccess = { skipped = true },
                        onLoginOrRegisterFromOnboarding = { stepScreen = 2 },
                        onExploreAsGuest = { guestEntered = true }
                    )
                    else -> AccessSelectionScreen(
                        language = currentLang,
                        onLanguageChange = { currentLang = it },
                        onEnterGuestMode = { guestEntered = true },
                        onViewOnboardingAgain = { stepScreen = 1 },
                        onAuthSuccess = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("splash_screen").assertIsDisplayed()

        stepScreen = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_prev_button").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_guest_button").performClick()
        assertTrue(guestEntered)
        composeRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("access_selection_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("access_lang_bn").performClick()
        assertEquals(AppLanguage.BN, currentLang)
        composeRule.onNodeWithTag("access_view_intro_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_skip_button").performClick()
        assertTrue(skipped)
    }

    @Test
    fun `qa_test_2026 dashboard renders loading error empty and clickable stat cards and quick actions`() {
        var state by mutableStateOf<UiState<List<DeedRecord>>>(UiState.Loading)
        var clickedTab = -1
        var quickSearched = ""
        var openedRoute = ""

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                DashboardScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    deedsState = state,
                    officeStats = OfficeStats(totalDeeds = 10, todayEntries = 2, draftCount = 3, pendingReviewCount = 1),
                    onQuickSearchSubmit = { quickSearched = it },
                    onNavigateAllRecords = { openedRoute = "all" },
                    onNavigatePersons = { openedRoute = "persons" },
                    onNavigateLand = { openedRoute = "land" },
                    onNavigateProfile = { openedRoute = "profile" },
                    onOpenDeedDetail = { openedRoute = "detail:$it" },
                    onNavigateNewDeed = { openedRoute = "new" },
                    onNavigateUserGuide = { openedRoute = "guide" },
                    onNavigateRecordsWithTab = { clickedTab = it }
                )
            }
        }

        // Loading state shows skeletons without crashing
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()

        // Error banner state
        state = UiState.Error("QA_TEST_2026_NET_ERR")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("error_banner"))
        composeRule.onNodeWithTag("error_banner").assertIsDisplayed()

        // Empty state with CTA
        state = UiState.Success(emptyList())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("empty_state_action"))
        composeRule.onNodeWithTag("empty_state_action").performClick()
        assertEquals("new", openedRoute)

        // Populated state + Stat cards + Quick actions + Quick search
        state = UiState.Success(listOf(makeDeed("qa-d1", "100/2026")))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToIndex(0)
        composeRule.onNodeWithTag("dashboard_search_input").performTextInput("100")
        composeRule.onNodeWithTag("dashboard_help_button").performClick()
        assertEquals("guide", openedRoute)
        composeRule.onNodeWithTag("dashboard_profile_chip").performClick()
        assertEquals("profile", openedRoute)

        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("quick_action_persons"))
        composeRule.onNodeWithTag("quick_action_persons").performClick()
        assertEquals("persons", openedRoute)
        composeRule.onNodeWithTag("quick_action_land").performClick()
        assertEquals("land", openedRoute)

        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("stat_card_drafts"))
        composeRule.onNodeWithTag("stat_card_drafts").performClick()
        assertEquals(1, clickedTab)
        composeRule.onNodeWithTag("stat_card_pending_review").performClick()
        assertEquals(2, clickedTab)
        assertTrue(quickSearched.isEmpty())
    }

    @Test
    fun `qa_test_2026 wizard validates each step converts land units and shows discard and duplicate dialogs`() {
        var savedRecord: DeedRecord? = null
        var duplicateForced = false
        var duplicateViewedId = ""
        var backExited = false
        var duplicateIdState by mutableStateOf<String?>("existing-deed-99")

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                DeedEntryScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    validationErrors = if (duplicateIdState != null) {
                        listOf(ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE)
                    } else emptyList(),
                    duplicateCandidateId = duplicateIdState,
                    isSaving = false,
                    onSaveDeed = { deed, _, ignoreDup ->
                        savedRecord = deed
                        duplicateForced = ignoreDup
                    },
                    onViewDuplicateDeed = { duplicateViewedId = it },
                    onClearValidation = { duplicateIdState = null },
                    onBack = { backExited = true }
                )
            }
        }

        // Duplicate warning modal actions
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duplicate_view_existing_button").performClick()
        composeRule.waitForIdle()
        assertEquals("existing-deed-99", duplicateViewedId)
        duplicateIdState = "existing-deed-99"
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duplicate_save_anyway_button").performClick()
        composeRule.waitForIdle()
        assertTrue(duplicateForced)
        duplicateIdState = null
        composeRule.waitForIdle()

        // Step 0 blocks advancing while blank
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("wizard_next_button"))
        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("error_banner").assertIsDisplayed()

        // Enter Bangla numerals for year ("২০২৬") and valid deed number
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("entry_deed_number"))
        composeRule.onNodeWithTag("entry_deed_number").performTextInput("QA_TEST_2026_77")
        composeRule.onNodeWithTag("entry_deed_year").performTextClearance()
        composeRule.onNodeWithTag("entry_deed_year").performTextInput("২০২৬")
        composeRule.onNodeWithTag("deed_type_HEBA").performScrollTo().performClick()

        // Advance to Step 1 (Grantors): dynamic add & remove
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("wizard_next_button"))
        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grantor_name_0").performTextInput("QA Grantor One")
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("add_grantor_button"))
        composeRule.onNodeWithTag("add_grantor_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("grantor_name_1").assertCountEquals(1)
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("remove_grantor_1"))
        composeRule.onNodeWithTag("remove_grantor_1").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("grantor_name_1").assertCountEquals(0)

        // Advance to Step 2 (Grantees)
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("wizard_next_button"))
        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grantee_name_0").performTextInput("QA Grantee One")

        // Advance to Step 3 (Location) -> fill location
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("wizard_next_button"))
        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("entry_upazila"))
        composeRule.onNodeWithTag("entry_upazila").performTextInput("Fatulla Revenue Circle")
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("entry_mouza"))
        composeRule.onNodeWithTag("entry_mouza").performTextInput("Deulpara")
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("entry_jl_number"))
        composeRule.onNodeWithTag("entry_jl_number").performTextInput("149")

        // Advance to Step 4 (Land & Review): test Katha -> Shotok conversion (2 Katha = 3.30 Shotok)
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("wizard_next_button"))
        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("parcel_khatian_0").performTextInput("501")
        composeRule.onNodeWithTag("parcel_dag_0").performTextInput("902")
        composeRule.onNodeWithTag("parcel_unit_0_${LandUnit.KATHA.name}").performClick()
        composeRule.onNodeWithTag("parcel_area_0").performTextInput("2")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("live_total_area_card"))
        composeRule.onNodeWithText("Computed Total Area: 3.30 Shotok (Decimal)").assertIsDisplayed()

        // Submit full form
        composeRule.onNodeWithTag("deed_entry_screen").performScrollToNode(hasTestTag("save_submit_button"))
        composeRule.onNodeWithTag("save_submit_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2026, savedRecord?.deedYear)
        assertEquals(DeedType.HEBA, savedRecord?.deedType)
        assertEquals("QA_TEST_2026_77", savedRecord?.deedNumber)
        assertEquals(false, backExited)
    }

    @Test
    @Config(qualifiers = "w1000dp-h900dp")
    fun `qa_test_2026 search filter Bangla year pagination and tablet 960dp dual pane`() {
        val sampleResults = (1..22).map { i ->
            RankedDeedResult(makeDeed("qa-s-$i", "100$i/2026"), 1000, MatchReason.EXACT_DEED_NUMBER)
        }
        var appliedYear: Int? = null
        var loadedMore = false
        var clearedFilters = false

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                Box(modifier = Modifier.width(960.dp)) {
                    SearchScreen(
                        language = AppLanguage.EN,
                        filterState = SearchFilterState(
                            query = "1001",
                            deedType = DeedType.SAF_KABALA,
                            year = 2026,
                            page = 1,
                            pageSize = 3
                        ),
                        results = sampleResults.take(3),
                        userProfile = supervisor,
                        onQueryChange = {},
                        onUpdateFilters = { _, yr, _, _, _ -> appliedYear = yr },
                        onClearFilters = { clearedFilters = true },
                        onLoadMore = { loadedMore = true },
                        onOpenDeedDetail = {}
                    )
                }
            }
        }

        // Tablet dual-pane shows details pane beside search list
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("deed_details_pane").assertIsDisplayed()

        // Clear active filters button & load more
        composeRule.onNodeWithTag("search_results_list").performScrollToNode(hasTestTag("search_load_more_button"))
        composeRule.onNodeWithTag("search_load_more_button").performClick()
        assertTrue(loadedMore)

        composeRule.onNodeWithTag("clear_filters_button").performClick()
        assertTrue(clearedFilters)

        // Open filter modal sheet and apply Bangla year "২০২৫"
        composeRule.onNodeWithTag("open_filters_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("filter_year_input").performTextClearance()
        composeRule.onNodeWithTag("filter_year_input").performTextInput("২০২৫")
        composeRule.onNodeWithTag("apply_filters_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2025, appliedYear)
    }

    @Test
    fun `qa_test_2026 records tabs details archive restore guide tour and profile settings`() {
        var screenMode by mutableIntStateOf(0)
        var restored = false
        var synced = false
        var tourReplayed = false
        var signedOut = false
        var conflictAction = ""
        var assignedRole: UserRole? = null

        val archivedDeed = makeDeed("qa-arch-1", "555/2026", status = DeedStatus.SOFT_DELETED)

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                    Box(modifier = Modifier.width(360.dp)) {
                        when (screenMode) {
                            0 -> RecordsScreen(
                                language = AppLanguage.EN,
                                userProfile = supervisor,
                                deedsState = UiState.Success(listOf(makeDeed("qa-r1", "111/2026"), archivedDeed)),
                                onOpenDeedDetail = {},
                                onCreateNewDeed = {}
                            )
                            1 -> DeedDetailsScreen(
                                language = AppLanguage.EN,
                                userProfile = supervisor,
                                deed = archivedDeed,
                                onEditDeed = {},
                                onApproveDeed = {},
                                onSoftDeleteDeed = {},
                                onRestoreDeed = { restored = true },
                                onBack = {}
                            )
                            2 -> PersonsDirectoryScreen(
                                language = AppLanguage.EN,
                                deedsState = UiState.Success(listOf(makeDeed("qa-r1", "111/2026"))),
                                onOpenDeedDetail = {},
                                onBack = {}
                            )
                            3 -> UserGuideScreen(
                                onReplayTour = { tourReplayed = true },
                                onBack = {}
                            )
                            4 -> ProductTourOverlay(
                                visible = true,
                                currentStep = 4,
                                targets = TourTargetsState(),
                                isOfficeUser = true,
                                isGuestMode = false,
                                onNext = {},
                                onPrevious = {},
                                onSkip = {}
                            )
                            5 -> ProfileSettingsScreen(
                                language = AppLanguage.EN,
                                userProfile = adminUser,
                                preferences = LocalUserPreferences(language = AppLanguage.EN, themeMode = ThemeMode.LIGHT),
                                pendingSyncCount = 2,
                                adminUsers = listOf(supervisor),
                                driveLinkState = DriveLinkState(
                                    firebaseUid = adminUser.userId,
                                    accountNamespaceId = NamespaceHelper.computeAccountNamespaceId(adminUser.userId),
                                    driveSubjectId = "qa-drive-subject",
                                    driveEmail = "qa-drive@example.com"
                                ),
                                onLanguageChange = {},
                                onThemeChange = {},
                                onDynamicColorChange = {},
                                onUpdateProfileNamePhone = { _, _ -> },
                                onAssignUserRole = { _, role, _, _ -> assignedRole = role },
                                onSyncNow = { synced = true },
                                onReplayProductTour = {},
                                onOpenUserGuide = {},
                                onSignOut = { signedOut = true }
                            )
                            else -> ConflictResolutionDialog(
                                language = AppLanguage.EN,
                                conflict = DeedConflict(
                                    localDeed = makeDeed("c1", "201/2026"),
                                    serverDeed = makeDeed("c1", "201/2026").copy(rowVersion = 2L)
                                ),
                                onKeepLocal = { conflictAction = "local" },
                                onAcceptServer = { conflictAction = "server" },
                                onDismiss = {}
                            )
                        }
                    }
                }
            }
        }

        // 0: Records tabs on 360dp + fontScale 1.5f
        composeRule.onNodeWithTag("records_tab_archive").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Deed #555/2026 (2026)").assertIsDisplayed()

        // 1: Details Restore confirmation flow + PDF & Print buttons visible
        screenMode = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("details_download_pdf_button").assertIsDisplayed()
        composeRule.onNodeWithTag("details_print_button").assertIsDisplayed()
        composeRule.onNodeWithTag("deed_details_screen").performScrollToNode(hasTestTag("details_restore_button"))
        composeRule.onNodeWithTag("details_restore_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_confirm_button").performClick()
        assertTrue(restored)

        // 2: Persons directory
        screenMode = 2
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("persons_directory_screen").assertIsDisplayed()
        composeRule.onNodeWithText("QA_TEST_2026_Karim").assertIsDisplayed()

        // 3: User Guide + Replay Tour
        screenMode = 3
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("user_guide_screen").performScrollToNode(hasTestTag("guide_replay_tour_button"))
        composeRule.onNodeWithTag("guide_replay_tour_button").performClick()
        composeRule.waitForIdle()
        assertTrue(tourReplayed)

        // 4: Product Tour step 4 overlay
        screenMode = 4
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_tour_overlay").assertIsDisplayed()
        composeRule.waitForIdle()
        // The overlay's full-screen clickable scrim merges descendant semantics by design.
        // Inspect the unmerged tree for the tooltip's internal badge test tag.
        composeRule.onNodeWithTag("tour_step_badge", useUnmergedTree = true).assertIsDisplayed()

        // 5: Profile Settings (Sync Now + Admin role chip + Sign Out confirmation)
        screenMode = 5
        composeRule.waitForIdle()
        // This scenario verifies Sync Now, so model an actually connected Drive account.
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_sync_now_button"))
        composeRule.onNodeWithTag("profile_sync_now_button").assertIsDisplayed().performClick()
        assertTrue(synced)

        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("admin_office_dropdown_qa-sup-1"))
        composeRule.onNodeWithText("Office Data Entry").performClick()
        assertEquals(UserRole.OFFICE_DATA_ENTRY, assignedRole)

        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_sign_out_button"))
        composeRule.onNodeWithTag("profile_sign_out_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_confirm_button").performClick()
        assertTrue(signedOut)

        // 6: Conflict Resolution Dialog
        screenMode = 6
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("conflict_keep_local_button").performClick()
        assertEquals("local", conflictAction)
        composeRule.onNodeWithTag("conflict_accept_server_button").performClick()
        assertEquals("server", conflictAction)
    }
}
