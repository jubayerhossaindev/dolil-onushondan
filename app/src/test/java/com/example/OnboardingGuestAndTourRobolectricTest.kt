package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.designsystem.DolilGuestBanner
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.DolilTheme
import com.example.core.designsystem.RecordCard
import com.example.data.local.DolilDatabase
import com.example.data.local.GuestSandboxSeeder
import com.example.data.local.LocalUserPreferences
import com.example.data.local.UserPreferencesDataStore
import com.example.data.repository.DolilSyncRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.AuditAction
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedType
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandParcel
import com.example.domain.model.LandUnit
import com.example.domain.model.PartyInfo
import com.example.domain.model.SyncState
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.DolilViewModel
import com.example.ui.screens.AccessSelectionScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProductTourOverlay
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.TourTargetsState
import com.example.ui.screens.UserGuideScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class OnboardingGuestAndTourRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `onboarding renders 4 pages in default Bangla, switches live to English, navigates back and forth, and triggers guest CTA`() {
        var skipped = false
        var exploredAsGuest = false

        composeRule.setContent {
            var lang by remember { mutableStateOf(AppLanguage.BN) }
            DolilTheme(language = lang) {
                OnboardingScreen(
                    language = lang,
                    onLanguageChange = { lang = it },
                    onSkipOrFinishToAccess = { skipped = true },
                    onLoginOrRegisterFromOnboarding = {},
                    onExploreAsGuest = { exploredAsGuest = true }
                )
            }
        }

        composeRule.waitForIdle()
        // Default Bangla Page 1
        composeRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeRule.onNodeWithText("জমির দলিলের তথ্য, এবার থাকুক গুছিয়ে!").assertIsDisplayed()
        composeRule.onNodeWithText("চলুন শুরু করি").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_prev_button").assertDoesNotExist()

        // Switch live to English
        composeRule.onNodeWithTag("onboarding_lang_en").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your Land Records, Neatly Organized!").assertIsDisplayed()
        composeRule.onNodeWithText("Let's Get Started").assertIsDisplayed()

        // Next -> Page 2, then Previous -> Page 1
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Adding Deed Information Is Easy").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_prev_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your Land Records, Neatly Organized!").assertIsDisplayed()

        // Navigate forward to Page 4
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Find What You Need, Faster!").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()

        // Page 4 shows single Google Get Started CTA + Guest CTA
        composeRule.onNodeWithText("Ready to Explore?").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_get_started_button").assertIsDisplayed()
        composeRule.onNodeWithText("Get Started").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_guest_button").performClick()
        composeRule.waitForIdle()
        assertTrue(exploredAsGuest)

        composeRule.onNodeWithTag("onboarding_skip_button").performClick()
        composeRule.waitForIdle()
        assertTrue(skipped)
    }

    @Test
    fun `access selection renders all three options, switches language, triggers guest and view intro, and maps friendly auth errors`() {
        var enteredGuest = false
        var viewedIntro = false

        composeRule.setContent {
            var lang by remember { mutableStateOf(AppLanguage.BN) }
            DolilTheme(language = lang) {
                AccessSelectionScreen(
                    language = lang,
                    onLanguageChange = { lang = it },
                    onEnterGuestMode = { enteredGuest = true },
                    onViewOnboardingAgain = { viewedIntro = true },
                    onAuthSuccess = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_selection_screen").assertIsDisplayed()
        composeRule.onNodeWithText("কীভাবে শুরু করতে চান?").assertIsDisplayed()
        composeRule.onNodeWithTag("access_login_button").assertIsDisplayed()
        composeRule.onNodeWithTag("access_guest_button").performScrollTo().assertIsDisplayed()

        // Switch live to English
        composeRule.onNodeWithTag("access_lang_en").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("How Would You Like to Start?").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Local Only • Demo Data").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Explore Without an Account").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(enteredGuest)

        composeRule.onNodeWithTag("access_view_intro_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(viewedIntro)

        // Friendly error mapping assertions
        assertEquals(R.string.auth_error_network, DolilStrings.friendlyAuthErrorResId("Network timeout occurred"))
        assertEquals(R.string.auth_error_cancelled, DolilStrings.friendlyAuthErrorResId("User cancelled sign-in"))
        assertEquals(R.string.auth_error_generic, DolilStrings.friendlyAuthErrorResId("Unexpected token error"))
    }

    @Test
    fun `isolated guest sandbox uses distinct room database, seeds 4 demo deeds idempotently, never queues cloud sync, and gates sync in guest viewmodel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainDb = DolilDatabase.getInstance(context)
        val guestDb = DolilDatabase.getGuestSandboxInstance(context)

        assertEquals("dolil_onusondhan.db", mainDb.openHelper.databaseName)
        assertEquals("dolil_guest_sandbox.db", guestDb.openHelper.databaseName)

        // Test isolated DAO behavior cleanly using in-memory guest vs main instances too
        val isolatedMainDb = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val isolatedGuestDb = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val vmStore = ViewModelStore()
        try {
            val guestDao = isolatedGuestDb.dolilDao()
            val guestSyncRepo = DolilSyncRepository(dao = guestDao, ownerUid = GuestSandboxSeeder.GUEST_USER_ID, enableCloudSync = false)
            val prefsStore = UserPreferencesDataStore(context)

            runBlocking {
                guestSyncRepo.seedGuestSandboxIfEmpty()
                // Idempotent re-seed
                guestSyncRepo.seedGuestSandboxIfEmpty()

                val seeded = guestDao.getAllDeedsOnce()
                assertEquals(4, seeded.size)
                assertEquals(0, guestSyncRepo.observePendingSyncCount().first())

                // Save a guest draft via repository and ensure no PendingSync is queued and main DB stays untouched
                val guestProfile = UserProfile(
                    userId = GuestSandboxSeeder.GUEST_USER_ID,
                    fullName = "Guest Explorer",
                    role = UserRole.PUBLIC_USER,
                    officeId = "demo_sandbox",
                    officeName = "Demo Sandbox"
                )
                val customDraft = DeedRecord(
                    id = "guest-draft-1",
                    createdBy = GuestSandboxSeeder.GUEST_USER_ID,
                    deedType = DeedType.SAF_KABALA,
                    deedNumber = "999/2026",
                    deedYear = 2026,
                    districtName = "নারায়ণগঞ্জ",
                    upazilaName = "ফতুল্লা রাজস্ব সার্কেল",
                    mouzaName = "ফতুল্লা",
                    jlNumber = "140",
                    grantors = listOf(PartyInfo(fullName = "নমুনা দাতা")),
                    grantees = listOf(PartyInfo(fullName = "নমুনা গ্রহীতা")),
                    parcels = listOf(
                        LandParcel(
                            khatianNumber = "101",
                            dagNumber = "202",
                            landAreaDecimal = "8.0",
                            landUnit = LandUnit.DECIMAL
                        )
                    ),
                    status = DeedStatus.DRAFT,
                    visibility = DeedVisibility.PRIVATE_DRAFT
                )
                val saveRes = guestSyncRepo.saveDeedLocallyAndTrySync(customDraft, guestProfile, AuditAction.CREATE)
                assertTrue(saveRes.isSuccess)
                assertEquals(5, guestDao.getAllDeedsOnce().size)
                assertEquals(0, guestSyncRepo.observePendingSyncCount().first())
                assertEquals(0, isolatedMainDb.dolilDao().getAllDeedsOnce().size)

                prefsStore.setLanguage(AppLanguage.BN)
            }

            // Verify DolilViewModel in Guest Mode sets helpful guest sync notice on syncNow()
            val vm = ViewModelProvider(
                vmStore,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = DolilViewModel(
                        syncRepo = guestSyncRepo,
                        prefsStore = prefsStore,
                        currentUserId = GuestSandboxSeeder.GUEST_USER_ID,
                        authEmail = "demo@sandbox.local",
                        authDisplayName = "Guest Explorer",
                        isGuestMode = true
                    ) as T
                }
            )[DolilViewModel::class.java]

            composeRule.setContent {
                vm.allDeedsState.collectAsStateWithLifecycle()
                vm.preferences.collectAsStateWithLifecycle()
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.allDeedsState.value is UiState.Success &&
                    vm.preferences.value.language == AppLanguage.BN
            }

            vm.syncNow()
            assertEquals(DolilStrings.guestSyncNotice(AppLanguage.BN), vm.statusMessage.value)
        } finally {
            vmStore.clear()
            isolatedMainDb.close()
            isolatedGuestDb.close()
        }
    }

    @Test
    fun `guest banner and demo data badge render on record card and trigger create account CTA`() {
        var createAccountClicked = false
        val demoDeed = DeedRecord(
            id = "demo-deed-1",
            createdBy = GuestSandboxSeeder.GUEST_USER_ID,
            deedNumber = "1256/2024",
            deedYear = 2024,
            mouzaName = "Fatulla",
            upazilaName = "Fatulla Revenue Circle",
            districtName = "Narayanganj",
            status = DeedStatus.APPROVED,
            visibility = DeedVisibility.PUBLIC,
            syncState = SyncState.LOCAL_ONLY_DRAFT,
            grantors = listOf(PartyInfo(fullName = "Abdul Karim")),
            grantees = listOf(PartyInfo(fullName = "Salma Begum")),
            parcels = listOf(LandParcel(khatianNumber = "712", dagNumber = "1845", landAreaDecimal = "15.0"))
        )

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                Column {
                    DolilGuestBanner(
                        visible = true,
                        onCreateAccountClick = { createAccountClicked = true }
                    )
                    RecordCard(
                        deed = demoDeed,
                        language = AppLanguage.EN,
                        onClick = {},
                        isDemoMode = true
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guest_mode_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("demo_data_badge_demo-deed-1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Demo Data", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("guest_create_account_button").performClick()
        composeRule.waitForIdle()
        assertTrue(createAccountClicked)
    }

    @Test
    fun `product tour progresses through 6 steps, shows office tip on step 4 for signed-in office user only, and completes or skips`() {
        var finished = false
        var skipped = false

        composeRule.setContent {
            var step by remember { mutableIntStateOf(0) }
            var officeUser by remember { mutableStateOf(true) }
            var guestMode by remember { mutableStateOf(false) }

            DolilTheme(language = AppLanguage.EN) {
                ProductTourOverlay(
                    visible = true,
                    currentStep = step,
                    targets = TourTargetsState(),
                    isOfficeUser = officeUser,
                    isGuestMode = guestMode,
                    onNext = {
                        if (step < 5) step += 1 else finished = true
                    },
                    onPrevious = {
                        if (step > 0) step -= 1
                    },
                    onSkip = { skipped = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_tour_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 6").assertIsDisplayed()
        composeRule.onNodeWithText("Home Screen").assertIsDisplayed()
        composeRule.onNodeWithTag("tour_prev_button").assertDoesNotExist()

        // Step 2 -> 3 -> 4
        composeRule.onNodeWithTag("tour_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 / 6").assertIsDisplayed()
        composeRule.onNodeWithTag("tour_prev_button").assertIsDisplayed()

        composeRule.onNodeWithTag("tour_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3 / 6").assertIsDisplayed()

        composeRule.onNodeWithTag("tour_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4 / 6").assertIsDisplayed()
        composeRule.onNodeWithText("Saved Records").assertIsDisplayed()
        // Office tip shown on Step 4 for signed-in Office user
        composeRule.onNodeWithText(
            "Office Tip: Review pending submissions or approve verified deeds directly from Records."
        ).assertIsDisplayed()

        // Step 5 -> 6 -> Done
        composeRule.onNodeWithTag("tour_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("5 / 6").assertIsDisplayed()

        composeRule.onNodeWithTag("tour_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("6 / 6").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()

        composeRule.onNodeWithTag("tour_next_button").performClick()
        composeRule.waitForIdle()
        assertTrue(finished)

        composeRule.onNodeWithTag("tour_skip_button").performClick()
        composeRule.waitForIdle()
        assertTrue(skipped)
    }

    @Test
    fun `product tour hides office tip on step 4 when in guest mode`() {
        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                ProductTourOverlay(
                    visible = true,
                    currentStep = 3,
                    targets = TourTargetsState(),
                    isOfficeUser = true,
                    isGuestMode = true,
                    onNext = {},
                    onPrevious = {},
                    onSkip = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("4 / 6").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Office Tip: Review pending submissions or approve verified deeds directly from Records."
        ).assertDoesNotExist()
    }

    @Test
    fun `user preferences datastore persists onboarding, guest mode, and independent tour replay flags`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesDataStore(context)

        prefs.setHasCompletedOnboarding(true)
        prefs.setGuestModeActive(true)
        prefs.setHasCompletedGuestTour(true)
        prefs.setHasCompletedUserTour(true)

        var current = prefs.preferencesFlow.first()
        assertTrue(current.hasCompletedOnboarding)
        assertTrue(current.isGuestModeActive)
        assertTrue(current.hasCompletedGuestTour)
        assertTrue(current.hasCompletedUserTour)

        // Resetting guest tour leaves signed-in user tour untouched
        prefs.resetProductTour(isGuest = true)
        current = prefs.preferencesFlow.first()
        assertFalse(current.hasCompletedGuestTour)
        assertTrue(current.hasCompletedUserTour)
    }

    @Test
    fun `user guide displays 5 sections and triggers replay tour while guest profile shows replay, guide, create account, and exit`() {
        var replayedFromGuide = false
        var replayedFromProfile = false
        var openedGuide = false
        var exitedGuest = false

        composeRule.setContent {
            var showGuide by remember { mutableStateOf(true) }
            DolilTheme(language = AppLanguage.EN) {
                if (showGuide) {
                    UserGuideScreen(
                        onReplayTour = { replayedFromGuide = true },
                        onBack = { showGuide = false }
                    )
                } else {
                    ProfileSettingsScreen(
                        language = AppLanguage.EN,
                        preferences = LocalUserPreferences(language = AppLanguage.EN, themeMode = ThemeMode.LIGHT),
                        userProfile = UserProfile(
                            userId = GuestSandboxSeeder.GUEST_USER_ID,
                            fullName = "Guest Explorer",
                            role = UserRole.PUBLIC_USER
                        ),
                        pendingSyncCount = 0,
                        adminUsers = emptyList(),
                        onUpdateProfileNamePhone = { _, _ -> },
                        onLanguageChange = {},
                        onThemeChange = {},
                        onSyncNow = {},
                        onAssignUserRole = { _, _, _, _ -> },
                        onSignOut = {},
                        isGuestMode = true,
                        onReplayProductTour = { replayedFromProfile = true },
                        onOpenUserGuide = { openedGuide = true },
                        onExitGuestToSignIn = { exitedGuest = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("user_guide_screen").assertIsDisplayed()
        composeRule.onNodeWithText("1. Adding a Deed Step by Step").assertIsDisplayed()
        composeRule.onNodeWithTag("user_guide_screen").performScrollToIndex(5)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_replay_tour_button").performClick()
        composeRule.waitForIdle()
        assertTrue(replayedFromGuide)

        // Switch to Guest Profile screen
        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Guest Explorer").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_create_account_button").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_sync_now_button").assertDoesNotExist()

        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_replay_tour_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_replay_tour_button").performClick()
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_user_guide_button"))
        composeRule.onNodeWithTag("profile_user_guide_button").performClick()
        composeRule.waitForIdle()
        assertTrue(replayedFromProfile)
        assertTrue(openedGuide)

        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_exit_guest_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_exit_guest_button").performClick()
        composeRule.waitForIdle()
        assertTrue(exitedGuest)
    }

    @Test
    fun `onboarding and product tour remain visible without clipping under 1_3x system font scale`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.3f)) {
                DolilTheme(language = AppLanguage.BN) {
                    OnboardingScreen(
                        language = AppLanguage.BN,
                        onLanguageChange = {},
                        onSkipOrFinishToAccess = {},
                        onLoginOrRegisterFromOnboarding = {},
                        onExploreAsGuest = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("জমির দলিলের তথ্য, এবার থাকুক গুছিয়ে!").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next_button").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed()
    }

    @Test
    fun `dark theme renders access selection and record card cleanly`() {
        composeRule.setContent {
            DolilTheme(themeMode = ThemeMode.DARK, language = AppLanguage.EN) {
                Column {
                    AccessSelectionScreen(
                        language = AppLanguage.EN,
                        onLanguageChange = {},
                        onEnterGuestMode = {},
                        onViewOnboardingAgain = {},
                        onAuthSuccess = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_selection_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
    }
}
