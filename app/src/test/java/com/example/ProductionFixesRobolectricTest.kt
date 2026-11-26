package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.DolilTheme
import com.example.core.security.AuthManager
import com.example.core.security.AuthManager.findActivity
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.screens.AccessSelectionScreen
import com.example.ui.screens.DeedEntryScreen
import com.example.ui.screens.localMillisToUtcDatePickerMillis
import com.example.ui.screens.utcDatePickerMillisToLocalNoonMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class ProductionFixesRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `access screen never shows developer firebase setup warning banner in BN or EN`() {
        composeRule.setContent {
            DolilTheme(language = AppLanguage.BN) {
                AccessSelectionScreen(
                    language = AppLanguage.BN,
                    onLanguageChange = {},
                    onEnterGuestMode = {},
                    onViewOnboardingAgain = {},
                    onAuthSuccess = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_selection_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("access_login_button").assertIsDisplayed()
        composeRule.onAllNodesWithText("google-services.json", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("লাইভ Firebase লগইনের জন্য", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Firebase Authentication requires", substring = true).assertCountEquals(0)
    }

    @Test
    fun `auth manager unwraps wrapped activity contexts, detects provisioned config, and maps credential exceptions`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assertNull(appContext.findActivity())

        val controller = Robolectric.buildActivity(Activity::class.java)
        val realActivity = controller.get()
        val localizedWrapper = ContextWrapper(ContextWrapper(realActivity))
        assertEquals(realActivity, localizedWrapper.findActivity())

        // Locally (without gitignored google-services.json), Firebase is unconfigured -> safeFirebaseAuth returns null.
        // In CI (dummy google-services.json with empty oauth_client), FirebaseApp initializes -> safeFirebaseAuth is non-null while webClientId is null.
        // In full builds with Web OAuth client, webClientId also resolves to *.apps.googleusercontent.com.
        val configured = AuthManager.isFirebaseConfigured(appContext)
        val webClientId = AuthManager.resolveDefaultWebClientId(appContext)
        if (!configured) {
            assertNull(AuthManager.safeFirebaseAuth(appContext))
            assertNull(webClientId)
        } else {
            assertNotNull(AuthManager.safeFirebaseAuth(appContext))
            if (webClientId != null) {
                assertTrue(webClientId.endsWith(".apps.googleusercontent.com"))
            }
        }

        // Verify localized Credential Manager diagnostics
        val noCredEn = AuthManager.mapGoogleSignInException(NoCredentialException("none"), AppLanguage.EN)
        val noCredBn = AuthManager.mapGoogleSignInException(NoCredentialException("none"), AppLanguage.BN)
        assertTrue(noCredEn.contains("SHA-1", ignoreCase = true))
        assertTrue(noCredBn.contains("Google"))

        val providerEn = AuthManager.mapGoogleSignInException(
            GetCredentialProviderConfigurationException("missing"),
            AppLanguage.EN
        )
        assertTrue(providerEn.contains("Play Services"))

        val unsupportedBn = AuthManager.mapGoogleSignInException(
            GetCredentialUnsupportedException("unsupported"),
            AppLanguage.BN
        )
        assertTrue(unsupportedBn.contains("Credential Manager"))
    }

    @Test
    fun `registration date utc-local helpers preserve exact calendar day across timezones`() {
        val originalTz = TimeZone.getDefault()
        try {
            // Test both negative-UTC (PST) and positive-UTC (Dhaka)
            listOf("America/Los_Angeles", "Asia/Dhaka", "UTC").forEach { tzId ->
                TimeZone.setDefault(TimeZone.getTimeZone(tzId))
                val pickedUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear()
                    set(2024, Calendar.MARCH, 15, 0, 0, 0)
                }.timeInMillis

                val localNoon = utcDatePickerMillisToLocalNoonMillis(pickedUtc)
                val formatted = DolilStrings.formatDate(localNoon, AppLanguage.EN)
                assertEquals("15 Mar 2024", formatted)

                val roundTripUtc = localMillisToUtcDatePickerMillis(localNoon)
                assertEquals(pickedUtc, roundTripUtc)
            }
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun `tapping registration date field and calendar icon opens DatePickerDialog, updates date, and saves selected date`() {
        val initialCal = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.JANUARY, 10, 12, 0, 0)
        }
        val initialSeedDeed = DeedRecord(
            id = "date-test-1",
            deedNumber = "456/2025",
            deedYear = 2025,
            registrationDateMillis = initialCal.timeInMillis
        )
        var savedDeed: DeedRecord? = null

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                DeedEntryScreen(
                    language = AppLanguage.EN,
                    userProfile = UserProfile(
                        userId = "u1",
                        fullName = "Test User",
                        role = UserRole.PUBLIC_USER,
                        officeId = "sro_narayanganj_sadar",
                        officeName = "Narayanganj Sadar Sub-Registry Office"
                    ),
                    initialDeed = initialSeedDeed,
                    validationErrors = emptyList(),
                    duplicateCandidateId = null,
                    isSaving = false,
                    onSaveDeed = { deed, _, _ -> savedDeed = deed },
                    onViewDuplicateDeed = {},
                    onClearValidation = {},
                    onBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("entry_reg_date").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("entry_reg_date")
            .assertTextContains(DolilStrings.formatDate(initialCal.timeInMillis, AppLanguage.EN))

        // 1. Clicking the date field container opens DatePickerDialog
        composeRule.onNodeWithTag("entry_reg_date_box").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_date_picker_dialog").assertIsDisplayed()

        // Dismiss via cancel button
        composeRule.onNodeWithTag("date_picker_cancel_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_date_picker_dialog").assertDoesNotExist()

        // 2. Clicking the trailing calendar IconButton also opens DatePickerDialog
        composeRule.onNodeWithTag("entry_reg_date_calendar_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_date_picker_dialog").assertIsDisplayed()

        // Select a different day in January 2025 (e.g. 22)
        composeRule.onNodeWithText("January 22, 2025", substring = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("date_picker_confirm_button").performClick()
        composeRule.waitForIdle()

        val expectedNewCal = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.JANUARY, 22, 12, 0, 0)
        }
        val expectedFormatted = DolilStrings.formatDate(expectedNewCal.timeInMillis, AppLanguage.EN)
        composeRule.onNodeWithTag("entry_reg_date").assertTextContains(expectedFormatted)

        // Save Draft from bottom bar and verify persisted registrationDateMillis matches Jan 22, 2025
        composeRule.onNodeWithTag("save_draft_button").performClick()
        composeRule.waitForIdle()
        assertNotNull(savedDeed)
        val savedCal = Calendar.getInstance().apply { timeInMillis = savedDeed!!.registrationDateMillis }
        assertEquals(2025, savedCal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, savedCal.get(Calendar.MONTH))
        assertEquals(22, savedCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1024dp-h540dp")
    fun `calendar picker dialog and manual date entry work smoothly on desktop and wide screens`() {
        val initialCal = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.MAY, 5, 12, 0, 0)
        }
        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                DeedEntryScreen(
                    language = AppLanguage.EN,
                    userProfile = UserProfile(
                        userId = "u-desktop",
                        fullName = "Desktop User",
                        role = UserRole.PUBLIC_USER
                    ),
                    initialDeed = DeedRecord(
                        id = "desktop-date-test",
                        deedNumber = "789/2025",
                        deedYear = 2025,
                        registrationDateMillis = initialCal.timeInMillis
                    ),
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

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("entry_reg_date_calendar_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_date_picker_dialog").assertIsDisplayed()

        // Switch to text input mode so users on desktop can also type/add a date directly
        composeRule.onNodeWithContentDescription("Switch to text input mode").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("Date")).performTextReplacement("06182025")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("date_picker_confirm_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        val expectedCal = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.JUNE, 18, 12, 0, 0)
        }
        composeRule.onNodeWithTag("entry_reg_date")
            .assertTextContains(DolilStrings.formatDate(expectedCal.timeInMillis, AppLanguage.EN))
    }
}
