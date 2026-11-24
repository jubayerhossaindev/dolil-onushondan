package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.designsystem.DolilTheme
import com.example.data.local.BangladeshLocationsCatalog
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.SearchFilterState
import com.example.ui.screens.DeedEntryScreen
import com.example.ui.screens.LandMouzaDirectoryScreen
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
class NarayanganjCatalogRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `asset loader loads 5 upazilas 4 revenue circles 4 surveys and 99 deduplicated mouzas`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BangladeshLocationsCatalog.ensureLoaded(context)

        assertEquals(1, BangladeshLocationsCatalog.districts.size)
        assertEquals("NRG", BangladeshLocationsCatalog.districts.first().code)
        assertTrue(BangladeshLocationsCatalog.offices.isNotEmpty())
        assertTrue(BangladeshLocationsCatalog.offices.all { it.districtCode == "NRG" })
        assertTrue(BangladeshLocationsCatalog.upazilas.all { it.districtCode == "NRG" })
        assertTrue(BangladeshLocationsCatalog.mouzas.all { it.districtCode == "NRG" })

        val forbidden = listOf("Dhaka", "Savar", "Mirpur", "Gazipur", "Chattogram", "ঢাকা", "সাভার", "মিরপুর", "গাজীপুর", "চট্টগ্রাম")
        val allNames = BangladeshLocationsCatalog.districts.flatMap { listOf(it.enName, it.bnName) } +
            BangladeshLocationsCatalog.offices.flatMap { listOf(it.enName, it.bnName, it.officeId) } +
            BangladeshLocationsCatalog.upazilas.flatMap { listOf(it.enName, it.bnName) } +
            BangladeshLocationsCatalog.mouzas.flatMap { listOf(it.mouzaEn, it.mouzaBn, it.unionBn) }
        forbidden.forEach { term ->
            assertTrue("Unexpected non-Narayanganj reference '$term'", allNames.none { it.contains(term, ignoreCase = true) })
        }

        val subdivisions = BangladeshLocationsCatalog.narayanganjSubdivisions()
        assertEquals(9, subdivisions.size)
        assertEquals(5, subdivisions.count { !it.isRevenueCircle })
        assertEquals(4, subdivisions.count { it.isRevenueCircle })

        assertEquals(4, BangladeshLocationsCatalog.narayanganjSurveys.size)

        val mouzas = BangladeshLocationsCatalog.narayanganjMouzas()
        assertEquals(99, mouzas.size)

        val deulpara = mouzas.firstOrNull { it.mouzaBn == "দেউল পাড়া" && it.jlNumber == "149" }
        assertTrue(deulpara != null)
        assertEquals(listOf("CS", "SA"), deulpara?.surveyTags)

        // Check Bengali spelling variants resolve identically for Narayanganj
        assertEquals(9, BangladeshLocationsCatalog.upazilasForDistrict("নারায়ণগঞ্জ").size)
        assertEquals(9, BangladeshLocationsCatalog.upazilasForDistrict("নারায়ণগঞ্জ").size)
        assertEquals(9, BangladeshLocationsCatalog.upazilasForDistrict("নারায়নগঞ্জ").size)
        assertEquals(9, BangladeshLocationsCatalog.upazilasForDistrict("Narayanganj").size)
        assertEquals(99, BangladeshLocationsCatalog.mouzasForDistrict("Narayanganj").size)
    }

    @Test
    fun `land directory catalog tab filters Narayanganj mouzas and triggers prefilled deed creation`() {
        var prefillResult: List<String>? = null

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                LandMouzaDirectoryScreen(
                    language = AppLanguage.EN,
                    deedsState = UiState.Success(emptyList<DeedRecord>()),
                    onOpenDeedDetail = {},
                    onCreateDeedFromCatalog = { district, upazila, mouza, jl ->
                        prefillResult = listOf(district, upazila, mouza, jl)
                    },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("land_tab_catalog").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Source: e-porcha.com reference", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("land_subdivision_NRG_RC_10").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("land_search_input").performTextInput("Deulpara")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("land_directory_list")
            .performScrollToNode(hasTestTag("catalog_mouza_card_MZ_NRG_RC_10_057"))
        composeRule.onNodeWithTag("catalog_mouza_card_MZ_NRG_RC_10_057").assertIsDisplayed()
        composeRule.onNodeWithText("JL 149").assertIsDisplayed()
        composeRule.onNodeWithText("CS").assertIsDisplayed()
        composeRule.onNodeWithText("SA").assertIsDisplayed()

        composeRule.onNodeWithTag("catalog_mouza_card_MZ_NRG_RC_10_057").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("Narayanganj", "Fatulla Revenue Circle", "Deulpara", "149"),
            prefillResult
        )
    }

    @Test
    fun `deed entry screen receives catalog prefill values cleanly`() {
        var savedDeed: DeedRecord? = null
        val supervisor = UserProfile(userId = "u1", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                DeedEntryScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    prefillDistrict = "Narayanganj",
                    prefillUpazila = "Fatulla Revenue Circle",
                    prefillMouza = "Deulpara",
                    prefillJl = "149",
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
        composeRule.onNodeWithTag("entry_deed_number").performTextInput("512/2026")
        composeRule.onNodeWithTag("deed_entry_screen")
            .performScrollToNode(hasTestTag("save_draft_button"))
        composeRule.onNodeWithTag("save_draft_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Narayanganj", savedDeed?.districtName)
        assertEquals("Fatulla Revenue Circle", savedDeed?.upazilaName)
        assertEquals("Deulpara", savedDeed?.mouzaName)
        assertEquals("149", savedDeed?.jlNumber)
    }

    @Test
    fun `search filter sheet scopes mouzas to Narayanganj when district is selected`() {
        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                SearchScreen(
                    language = AppLanguage.EN,
                    filterState = SearchFilterState(district = "Narayanganj"),
                    results = emptyList(),
                    onQueryChange = {},
                    onUpdateFilters = { _, _, _, _, _ -> },
                    onClearFilters = {},
                    onLoadMore = {},
                    onOpenDeedDetail = {}
                )
            }
        }

        composeRule.onNodeWithTag("open_filters_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("filter_mouza_input").performScrollTo().performTextInput("Deulpara")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Deulpara (দেউল পাড়া) • JL 149").assertIsDisplayed()
        composeRule.onNodeWithText("CS · SA").assertIsDisplayed()
    }
}
