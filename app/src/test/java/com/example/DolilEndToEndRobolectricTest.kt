package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.core.designsystem.DolilTheme
import com.example.data.local.LocalUserPreferences
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandParcel
import com.example.domain.model.MatchReason
import com.example.domain.model.OfficeStats
import com.example.domain.model.PartyInfo
import com.example.domain.model.RankedDeedResult
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.domain.validation.ValidationErrorCode
import com.example.ui.SearchFilterState
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeedDetailsScreen
import com.example.ui.screens.DeedEntryScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.SearchScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DolilEndToEndRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleDeed = DeedRecord(
        id = "deed-ui-1",
        createdBy = "uid-1",
        deedNumber = "1256/2024",
        deedYear = 2024,
        districtName = "Narayanganj",
        upazilaName = "Fatulla Revenue Circle",
        mouzaName = "Deulpara",
        jlNumber = "149",
        status = DeedStatus.PENDING_REVIEW,
        visibility = DeedVisibility.OFFICE_ONLY,
        grantors = listOf(PartyInfo(fullName = "Abdul Karim", fatherName = "Rahim Uddin")),
        grantees = listOf(PartyInfo(fullName = "Salma Begum", fatherName = "Habibullah")),
        parcels = listOf(LandParcel(khatianNumber = "712", dagNumber = "1845", landAreaDecimal = "15.0"))
    )

    @Test
    fun `dashboard hides office stats for PUBLIC_USER and renders them for OFFICE_SUPERVISOR`() {
        val publicUser = UserProfile(userId = "u1", fullName = "Citizen", role = UserRole.PUBLIC_USER)
        composeRule.setContent {
            DolilTheme {
                DashboardScreen(
                    language = AppLanguage.EN,
                    userProfile = publicUser,
                    deedsState = UiState.Success(listOf(sampleDeed)),
                    officeStats = OfficeStats(totalDeeds = 12, todayEntries = 3, draftCount = 2, pendingReviewCount = 1),
                    onQuickSearchSubmit = {},
                    onNavigateNewDeed = {},
                    onNavigateAllRecords = {},
                    onNavigatePersons = {},
                    onNavigateLand = {},
                    onNavigateProfile = {},
                    onOpenDeedDetail = {},
                    onNavigateUserGuide = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_action_all_deeds").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_action_persons").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_action_land").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_action_guide").assertIsDisplayed()
        composeRule.onNodeWithText("Office Statistics").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToIndex(3)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("record_card_deed-ui-1").assertIsDisplayed()
    }

    @Test
    fun `dashboard shows office statistics for supervisor`() {
        val supervisor = UserProfile(userId = "u2", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)
        composeRule.setContent {
            DolilTheme {
                DashboardScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    deedsState = UiState.Success(listOf(sampleDeed)),
                    officeStats = OfficeStats(totalDeeds = 12, todayEntries = 3, draftCount = 2, pendingReviewCount = 1),
                    onQuickSearchSubmit = {},
                    onNavigateNewDeed = {},
                    onNavigateAllRecords = {},
                    onNavigatePersons = {},
                    onNavigateLand = {},
                    onNavigateProfile = {},
                    onOpenDeedDetail = {},
                    onNavigateUserGuide = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToIndex(2)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Office Statistics").assertIsDisplayed()
        composeRule.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun `search screen updates query and opens sort sheet`() {
        var latestQuery = ""
        composeRule.setContent {
            DolilTheme {
                SearchScreen(
                    language = AppLanguage.EN,
                    filterState = SearchFilterState(),
                    results = listOf(RankedDeedResult(sampleDeed, 1000, MatchReason.EXACT_DEED_NUMBER)),
                    onQueryChange = { latestQuery = it },
                    onUpdateFilters = { _, _, _, _, _ -> },
                    onClearFilters = {},
                    onLoadMore = {},
                    onOpenDeedDetail = {}
                )
            }
        }
        composeRule.onNodeWithTag("search_screen_input").performTextInput("1256")
        composeRule.waitForIdle()
        assertEquals("1256", latestQuery)

        composeRule.onNodeWithTag("open_sort_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sort_option_REGISTRATION_DATE_DESC").assertIsDisplayed()
        composeRule.onNodeWithTag("sort_option_LAND_AREA_DESC").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `deed entry screen adds dynamic grantor and parcel and computes live Shotok total`() {
        var submittedDeed: DeedRecord? = null
        val supervisor = UserProfile(userId = "u2", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)

        composeRule.setContent {
            DolilTheme {
                DeedEntryScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    validationErrors = listOf(ValidationErrorCode.REQUIRED_DEED_NUMBER),
                    duplicateCandidateId = null,
                    isSaving = false,
                    onSaveDeed = { deed, _, _ -> submittedDeed = deed },
                    onViewDuplicateDeed = {},
                    onClearValidation = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("error_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("entry_deed_number").performTextInput("789/2026")

        composeRule.onNodeWithTag("deed_entry_screen")
            .performScrollToNode(hasTestTag("wizard_next_button"))
        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("deed_entry_screen")
            .performScrollToNode(hasTestTag("grantor_name_0"))
        composeRule.onNodeWithTag("grantor_name_0").performTextInput("Jamal Uddin")

        composeRule.onNodeWithTag("entry_section_pill_4").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("deed_entry_screen")
            .performScrollToNode(hasTestTag("parcel_area_0"))
        composeRule.onNodeWithTag("parcel_area_0").performTextInput("12.5")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("deed_entry_screen")
            .performScrollToNode(hasTestTag("live_total_area_card"))
        composeRule.onNodeWithTag("live_total_area_card").assertIsDisplayed()
        composeRule.onNodeWithText("Computed Total Area: 12.50 Shotok (Decimal)").assertIsDisplayed()
        composeRule.onNodeWithTag("deed_entry_screen")
            .performScrollToNode(hasTestTag("entry_review_summary_card"))
        composeRule.onNodeWithTag("entry_review_summary_card").assertIsDisplayed()

        composeRule.onNodeWithTag("deed_entry_screen")
            .performScrollToNode(hasTestTag("save_draft_button"))
        composeRule.onNodeWithTag("save_draft_button").performClick()
        composeRule.waitForIdle()
        assertEquals("789/2026", submittedDeed?.deedNumber)
        assertEquals("Jamal Uddin", submittedDeed?.primaryGrantorName)
    }

    @Test
    fun `deed details triggers soft delete confirmation dialog and approve action`() {
        var approved = false
        var softDeleted = false
        val supervisor = UserProfile(userId = "u2", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)

        composeRule.setContent {
            DolilTheme {
                DeedDetailsScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    deed = sampleDeed,
                    onEditDeed = {},
                    onApproveDeed = { approved = true },
                    onSoftDeleteDeed = { softDeleted = true },
                    onRestoreDeed = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("deed_details_screen").performScrollToIndex(5)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("details_approve_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(approved)

        composeRule.onNodeWithTag("details_soft_delete_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_confirm_button").performClick()
        composeRule.waitForIdle()
        assertTrue(softDeleted)
    }

    @Test
    fun `profile screen exposes admin role panel only for admins and confirms sign out`() {
        var signedOut = false
        var selectedLang = AppLanguage.EN
        val admin = UserProfile(
            userId = "admin-1",
            fullName = "Admin",
            email = "jubayerhossaingalaxy@gmail.com",
            role = UserRole.PLATFORM_ADMIN
        )

        composeRule.setContent {
            DolilTheme {
                ProfileSettingsScreen(
                    language = AppLanguage.EN,
                    preferences = LocalUserPreferences(language = AppLanguage.EN, themeMode = ThemeMode.LIGHT),
                    userProfile = admin,
                    pendingSyncCount = 2,
                    adminUsers = listOf(
                        UserProfile(userId = "u-other", fullName = "Clerk", email = "clerk@bd.gov", role = UserRole.PUBLIC_USER)
                    ),
                    onUpdateProfileNamePhone = { _, _ -> },
                    onLanguageChange = { selectedLang = it },
                    onThemeChange = {},
                    onSyncNow = {},
                    onAssignUserRole = { _, _, _, _ -> },
                    onSignOut = { signedOut = true }
                )
            }
        }

        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_lang_bn"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_lang_bn").performClick()
        composeRule.waitForIdle()
        assertEquals(AppLanguage.BN, selectedLang)

        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasText("Role & Office Assignment (Admin)"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Role & Office Assignment (Admin)").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_sign_out_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_sign_out_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_confirm_button").performClick()
        composeRule.waitForIdle()
        assertTrue(signedOut)
    }

    @Test
    fun `search screen displays active filter count badge and dismissible chip when district filter is set`() {
        var cleared = false
        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                SearchScreen(
                    language = AppLanguage.EN,
                    filterState = SearchFilterState(district = "Narayanganj"),
                    results = listOf(RankedDeedResult(sampleDeed, 1000, MatchReason.EXACT_DEED_NUMBER)),
                    onQueryChange = {},
                    onUpdateFilters = { _, _, _, _, _ -> },
                    onClearFilters = { cleared = true },
                    onLoadMore = {},
                    onOpenDeedDetail = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_filters_button").assertIsDisplayed()
        composeRule.onNodeWithText("Filters (1)").assertIsDisplayed()
        composeRule.onNodeWithText("District: Narayanganj").assertIsDisplayed()
        composeRule.onNodeWithTag("clear_filters_button").performClick()
        composeRule.waitForIdle()
        assertTrue(cleared)
    }

    @Test
    fun `deed entry screen prompts discard confirmation on back when form is dirty`() {
        var navigatedBack = false
        val supervisor = UserProfile(userId = "u2", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                DeedEntryScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    validationErrors = emptyList(),
                    duplicateCandidateId = null,
                    isSaving = false,
                    onSaveDeed = { _, _, _ -> },
                    onViewDuplicateDeed = {},
                    onClearValidation = {},
                    onBack = { navigatedBack = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("entry_deed_number").performTextInput("456/2026")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_confirm_button").performClick()
        composeRule.waitForIdle()
        assertTrue(navigatedBack)
    }
}
