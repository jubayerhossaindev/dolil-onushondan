package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.core.designsystem.AccentAntiqueGold
import com.example.core.designsystem.CharcoalDarkBackground
import com.example.core.designsystem.CharcoalDarkSurfaceHigh
import com.example.core.designsystem.CharcoalOutlineDark
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.DolilTheme
import com.example.core.designsystem.InkTextPrimaryDark
import com.example.core.designsystem.InkTextPrimaryLight
import com.example.core.designsystem.PaperDocumentOnMuted
import com.example.core.designsystem.PaperDocumentOnSurface
import com.example.core.designsystem.PaperDocumentSurface
import com.example.core.designsystem.PaperLightBackground
import com.example.core.designsystem.PaperLightSurfaceHigh
import com.example.core.designsystem.PaperLightSurfaceLowest
import com.example.core.designsystem.PaperOutlineLight
import com.example.core.designsystem.StatusApprovedContainerDark
import com.example.core.designsystem.StatusApprovedContainerLight
import com.example.core.designsystem.StatusApprovedDark
import com.example.core.designsystem.StatusApprovedLight
import com.example.core.designsystem.StatusArchivedContainerDark
import com.example.core.designsystem.StatusArchivedContainerLight
import com.example.core.designsystem.StatusArchivedDark
import com.example.core.designsystem.StatusArchivedLight
import com.example.core.designsystem.StatusDraftContainerDark
import com.example.core.designsystem.StatusDraftContainerLight
import com.example.core.designsystem.StatusDraftDark
import com.example.core.designsystem.StatusDraftLight
import com.example.core.designsystem.StatusErrorContainerDark
import com.example.core.designsystem.StatusErrorContainerLight
import com.example.core.designsystem.StatusErrorDark
import com.example.core.designsystem.StatusErrorLight
import com.example.core.designsystem.StatusPendingContainerDark
import com.example.core.designsystem.StatusPendingContainerLight
import com.example.core.designsystem.StatusPendingDark
import com.example.core.designsystem.StatusPendingLight
import com.example.core.designsystem.dolilColors
import com.example.data.local.LocalUserPreferences
import com.example.data.local.UserPreferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandParcel
import com.example.domain.model.MatchReason
import com.example.domain.model.PartyInfo
import com.example.domain.model.RankedDeedResult
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.SearchFilterState
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.RecordsScreen
import com.example.ui.screens.SearchScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w960dp-h600dp")
class PaperAndInkRedesignRobolectricTest {

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
    fun `user preferences datastore defaults useDynamicColor to false and persists changes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesDataStore(context)

        val initial = prefs.preferencesFlow.first()
        assertFalse(initial.useDynamicColor)

        prefs.setUseDynamicColor(true)
        assertTrue(prefs.preferencesFlow.first().useDynamicColor)

        prefs.setUseDynamicColor(false)
        assertFalse(prefs.preferencesFlow.first().useDynamicColor)
    }

    @Test
    fun `profile settings renders segmented controls and toggles Material You dynamic color`() {
        var selectedLanguage = AppLanguage.EN
        var selectedTheme = ThemeMode.SYSTEM
        var dynamicEnabled = false

        composeRule.setContent {
            DolilTheme(language = selectedLanguage, themeMode = selectedTheme, useDynamicColor = dynamicEnabled) {
                ProfileSettingsScreen(
                    language = selectedLanguage,
                    preferences = LocalUserPreferences(
                        language = selectedLanguage,
                        themeMode = selectedTheme,
                        useDynamicColor = dynamicEnabled
                    ),
                    userProfile = UserProfile(
                        userId = "u1",
                        fullName = "Fatema Begum",
                        email = "fatema@narayanganj.gov.bd",
                        role = UserRole.OFFICE_DATA_ENTRY
                    ),
                    pendingSyncCount = 0,
                    adminUsers = emptyList(),
                    onUpdateProfileNamePhone = { _, _ -> },
                    onLanguageChange = { selectedLanguage = it },
                    onThemeChange = { selectedTheme = it },
                    onSyncNow = {},
                    onAssignUserRole = { _, _, _, _ -> },
                    onSignOut = {},
                    onDynamicColorChange = { dynamicEnabled = it }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_lang_bn"))
        composeRule.onNodeWithTag("profile_lang_bn").performClick()
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_theme_dark"))
        composeRule.onNodeWithTag("profile_theme_dark").performClick()
        composeRule.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("profile_dynamic_color_switch"))
        composeRule.onNodeWithTag("profile_dynamic_color_switch").performClick()
        composeRule.waitForIdle()

        assertEquals(AppLanguage.BN, selectedLanguage)
        assertEquals(ThemeMode.DARK, selectedTheme)
        assertTrue(dynamicEnabled)
    }

    @Test
    fun `records screen displays tablet list-detail panes and updates live preview on selection`() {
        var openedDetailId: String? = null
        val supervisor = UserProfile(userId = "u2", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                RecordsScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    deedsState = UiState.Success(listOf(sampleDeed)),
                    onOpenDeedDetail = { openedDetailId = it },
                    onCreateNewDeed = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("records_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("deed_details_pane").assertIsDisplayed()
        composeRule.onNodeWithText(DolilStrings.selectDeedPreviewTitle(AppLanguage.EN)).assertIsDisplayed()

        composeRule.onNodeWithTag("record_card_deed-ui-1").performClick()
        composeRule.waitForIdle()

        assertEquals(null, openedDetailId)
        composeRule.onNodeWithTag("deed_details_screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("1256/2024", substring = true).assertCountEquals(4)
        composeRule.onNodeWithTag("deed_details_screen").performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rahim Uddin", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `search screen displays tablet list-detail panes and updates live preview on selection`() {
        var openedDetailId: String? = null
        val supervisor = UserProfile(userId = "u2", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                SearchScreen(
                    language = AppLanguage.EN,
                    filterState = SearchFilterState(),
                    results = listOf(RankedDeedResult(sampleDeed, 1000, MatchReason.EXACT_DEED_NUMBER)),
                    onQueryChange = {},
                    onUpdateFilters = { _, _, _, _, _ -> },
                    onClearFilters = {},
                    onLoadMore = {},
                    onOpenDeedDetail = { openedDetailId = it },
                    userProfile = supervisor
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("deed_details_pane").assertIsDisplayed()
        composeRule.onNodeWithText(DolilStrings.selectDeedPreviewTitle(AppLanguage.EN)).assertIsDisplayed()

        composeRule.onNodeWithTag("record_card_deed-ui-1").performClick()
        composeRule.waitForIdle()

        assertEquals(null, openedDetailId)
        composeRule.onNodeWithTag("deed_details_screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("1256/2024", substring = true).assertCountEquals(4)
        composeRule.onNodeWithTag("deed_details_screen").performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rahim Uddin", substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `paper and ink light, dark, archival, and semantic status pairs satisfy WCAG AA contrast`() {
        var lightScheme: androidx.compose.material3.ColorScheme? = null
        var darkScheme: androidx.compose.material3.ColorScheme? = null

        composeRule.setContent {
            DolilTheme(themeMode = ThemeMode.LIGHT, useDynamicColor = false) {
                lightScheme = MaterialTheme.colorScheme
            }
            DolilTheme(themeMode = ThemeMode.DARK, useDynamicColor = false) {
                darkScheme = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()

        val l = lightScheme!!
        val d = darkScheme!!

        // Exact Paper & Ink Light/Dark token verification + 4.5:1 text contrast
        assertEquals(PaperLightBackground, l.background)
        assertEquals(PaperLightSurfaceLowest, l.surfaceContainerLowest)
        assertEquals(PaperLightSurfaceHigh, l.surfaceContainerHigh)
        assertEquals(InkTextPrimaryLight, l.onSurface)
        assertEquals(PaperOutlineLight, l.outline)

        assertTrue(contrastRatio(l.onSurface, l.background) >= 4.5)
        assertTrue(contrastRatio(l.onSurfaceVariant, l.background) >= 4.5)
        assertTrue(contrastRatio(l.onSurfaceVariant, l.surfaceContainerHigh) >= 4.5)
        assertTrue(contrastRatio(l.onPrimary, l.primary) >= 4.5)
        assertTrue(contrastRatio(l.onPrimaryContainer, l.primaryContainer) >= 4.5)
        assertTrue(contrastRatio(l.onSecondary, l.secondary) >= 4.5)
        assertTrue(contrastRatio(l.onSecondaryContainer, l.secondaryContainer) >= 4.5)
        assertTrue(contrastRatio(l.onTertiary, l.tertiary) >= 4.5)
        assertTrue(contrastRatio(l.onTertiaryContainer, l.tertiaryContainer) >= 4.5)
        assertTrue(contrastRatio(l.onError, l.error) >= 4.5)
        assertTrue(contrastRatio(l.onErrorContainer, l.errorContainer) >= 4.5)
        assertTrue(contrastRatio(l.outline, l.surfaceContainerLowest) >= 3.0)
        assertTrue(contrastRatio(l.outline, l.background) >= 3.0)

        assertEquals(CharcoalDarkBackground, d.background)
        assertEquals(CharcoalDarkSurfaceHigh, d.surfaceContainerHigh)
        assertEquals(InkTextPrimaryDark, d.onSurface)
        assertEquals(CharcoalOutlineDark, d.outline)

        assertTrue(contrastRatio(d.onSurface, d.background) >= 4.5)
        assertTrue(contrastRatio(d.onSurfaceVariant, d.background) >= 4.5)
        assertTrue(contrastRatio(d.onSurfaceVariant, d.surfaceContainerHigh) >= 4.5)
        assertTrue(contrastRatio(d.onPrimary, d.primary) >= 4.5)
        assertTrue(contrastRatio(d.onPrimaryContainer, d.primaryContainer) >= 4.5)
        assertTrue(contrastRatio(d.onSecondary, d.secondary) >= 4.5)
        assertTrue(contrastRatio(d.onSecondaryContainer, d.secondaryContainer) >= 4.5)
        assertTrue(contrastRatio(d.onTertiary, d.tertiary) >= 4.5)
        assertTrue(contrastRatio(d.onTertiaryContainer, d.tertiaryContainer) >= 4.5)
        assertTrue(contrastRatio(d.onError, d.error) >= 4.5)
        assertTrue(contrastRatio(d.onErrorContainer, d.errorContainer) >= 4.5)
        assertTrue(contrastRatio(d.outline, d.surfaceContainerLowest) >= 3.0)
        assertTrue(contrastRatio(d.outline, d.background) >= 3.0)

        // Archival Document Preview contrast (>= 4.5:1)
        assertTrue(contrastRatio(PaperDocumentOnSurface, PaperDocumentSurface) >= 4.5)
        assertTrue(contrastRatio(PaperDocumentOnMuted, PaperDocumentSurface) >= 4.5)

        // Strictly Independent Semantic Status Colors contrast (>= 4.5:1)
        assertTrue(contrastRatio(StatusApprovedLight, StatusApprovedContainerLight) >= 4.5)
        assertTrue(contrastRatio(StatusPendingLight, StatusPendingContainerLight) >= 4.5)
        assertTrue(contrastRatio(StatusDraftLight, StatusDraftContainerLight) >= 4.5)
        assertTrue(contrastRatio(StatusArchivedLight, StatusArchivedContainerLight) >= 4.5)
        assertTrue(contrastRatio(StatusErrorLight, StatusErrorContainerLight) >= 4.5)

        assertTrue(contrastRatio(StatusApprovedDark, StatusApprovedContainerDark) >= 4.5)
        assertTrue(contrastRatio(StatusPendingDark, StatusPendingContainerDark) >= 4.5)
        assertTrue(contrastRatio(StatusDraftDark, StatusDraftContainerDark) >= 4.5)
        assertTrue(contrastRatio(StatusArchivedDark, StatusArchivedContainerDark) >= 4.5)
        assertTrue(contrastRatio(StatusErrorDark, StatusErrorContainerDark) >= 4.5)
    }

    @Test
    fun `semantic status colors and archival parchment stay independent when Dynamic Color is enabled`() {
        var lightDynamicSemantic: com.example.core.designsystem.DolilSemanticColors? = null
        var darkDynamicSemantic: com.example.core.designsystem.DolilSemanticColors? = null

        composeRule.setContent {
            DolilTheme(themeMode = ThemeMode.LIGHT, useDynamicColor = true) {
                lightDynamicSemantic = MaterialTheme.dolilColors
            }
            DolilTheme(themeMode = ThemeMode.DARK, useDynamicColor = true) {
                darkDynamicSemantic = MaterialTheme.dolilColors
            }
        }
        composeRule.waitForIdle()

        val ls = lightDynamicSemantic!!
        val ds = darkDynamicSemantic!!

        // Archival paper surface preserved even in Dark + Dynamic Color mode
        assertEquals(PaperDocumentSurface, ls.paperSurface)
        assertEquals(PaperDocumentOnSurface, ls.onPaperSurface)
        assertEquals(AccentAntiqueGold, ls.accentGold)
        assertEquals(PaperDocumentSurface, ds.paperSurface)
        assertEquals(PaperDocumentOnSurface, ds.onPaperSurface)
        assertEquals(AccentAntiqueGold, ds.accentGold)

        // Neutral Slate Archived (distinct from Crimson Red Error) preserved in Dynamic Color
        assertEquals(StatusArchivedLight, ls.archived)
        assertEquals(StatusArchivedContainerLight, ls.archivedContainer)
        assertEquals(StatusErrorLight, ls.error)
        assertEquals(StatusErrorContainerLight, ls.errorContainer)

        assertEquals(StatusArchivedDark, ds.archived)
        assertEquals(StatusArchivedContainerDark, ds.archivedContainer)
        assertEquals(StatusErrorDark, ds.error)
        assertEquals(StatusErrorContainerDark, ds.errorContainer)
    }
}
