package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import com.example.core.designsystem.DolilTheme
import com.example.core.print.A4Spec
import com.example.core.print.DeedDocumentBuilder
import com.example.core.print.DeedPdfRenderer
import com.example.core.print.DeedPrintDocumentAdapter
import com.example.core.print.DeedPrintHelper
import com.example.core.print.PaginatedDocumentPlan
import com.example.core.print.PdfExportBackend
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedType
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandClass
import com.example.domain.model.LandParcel
import com.example.domain.model.LandUnit
import com.example.domain.model.MatchReason
import com.example.domain.model.PartyInfo
import com.example.domain.model.RankedDeedResult
import com.example.domain.model.SurveyType
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.SearchFilterState
import com.example.ui.screens.DeedDetailsScreen
import com.example.ui.screens.RecordsScreen
import com.example.ui.screens.SearchScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeedDocumentPdfAndPrintRobolectricTest {

    /**
     * Executes the exact production PaginatedDocumentPlan.drawPage(Canvas, pageIndex)
     * onto real Skia Bitmaps via Robolectric Native Graphics (since Robolectric's JVM runtime
     * lacks Android's libpdfium native PdfDocument JNI) and asserts every page draws visible ink.
     */
    private class SkiaCanvasVerifierBackend : PdfExportBackend {
        val renderedPageCount: Int get() = drawnPages.size
        val drawnPages = mutableListOf<Int>()

        override fun writePages(
            plan: PaginatedDocumentPlan,
            pageIndices: List<Int>,
            outputStream: OutputStream,
            isCancelled: () -> Boolean
        ): Boolean {
            pageIndices.forEach { pageIndex ->
                if (isCancelled()) return false
                val bitmap = Bitmap.createBitmap(
                    plan.pageWidthPt,
                    plan.pageHeightPt,
                    Bitmap.Config.ARGB_8888
                )
                try {
                    val canvas = Canvas(bitmap)
                    plan.drawPage(canvas, pageIndex)
                    assertTrue(
                        "Expected rendered page ${pageIndex + 1} to contain drawn non-white ink pixels",
                        hasNonWhiteInk(bitmap)
                    )
                    drawnPages.add(pageIndex)
                } finally {
                    bitmap.recycle()
                }
            }
            if (isCancelled()) return false
            val payload = "%PDF-1.4\n% Dolil Onusondhan Verified Pages=${pageIndices.size}\n%%EOF\n"
            outputStream.write(payload.toByteArray(Charsets.US_ASCII))
            outputStream.flush()
            return true
        }

        private fun hasNonWhiteInk(bitmap: Bitmap): Boolean {
            val w = bitmap.width
            val h = bitmap.height
            var y = 30
            while (y < h - 30) {
                var x = 40
                while (x < w - 40) {
                    if (bitmap.getPixel(x, y) != Color.WHITE) return true
                    x += 6
                }
                y += 6
            }
            return false
        }
    }

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun buildSmallSampleDeed(): DeedRecord = DeedRecord(
        id = "deed-pdf-101",
        createdBy = "uid-clerk-1",
        officeName = "সাব-রেজিস্ট্রি অফিস, নারায়ণগঞ্জ সদর",
        deedType = DeedType.SAF_KABALA,
        deedNumber = "1256/2024",
        deedYear = 2024,
        registrationDateMillis = 1715000000000L,
        districtName = "নারায়ণগঞ্জ",
        upazilaName = "ফতুল্লা রাজস্ব সার্কেল",
        unionName = "কাশিপুর",
        mouzaName = "দেউলপাড়া",
        jlNumber = "১৪৯",
        status = DeedStatus.APPROVED,
        visibility = DeedVisibility.PUBLIC,
        rowVersion = 3L,
        createdAtMillis = 1715000000000L,
        updatedAtMillis = 1716000000000L,
        grantors = listOf(
            PartyInfo(
                fullName = "মোঃ আব্দুল করিম",
                fatherName = "মৃত রহিম উদ্দিন",
                motherName = "হালিমা খাতুন",
                spouseName = "সালমা আক্তার",
                address = "গ্রাম: দেউলপাড়া, ডাকঘর: ফতুল্লা, নারায়ণগঞ্জ",
                aliases = listOf("করিম মিয়া", "Abdul Karim")
            )
        ),
        grantees = listOf(
            PartyInfo(
                fullName = "সালমা বেগম",
                fatherName = "হাবিবুল্লাহ সরকার",
                motherName = "আয়েশা বেগম",
                spouseName = "",
                address = "চাষাড়া, নারায়ণগঞ্জ সদর",
                aliases = emptyList()
            )
        ),
        parcels = listOf(
            LandParcel(
                surveyType = SurveyType.RS,
                khatianNumber = "৭১২",
                dagNumber = "১৮৪৫",
                landClass = LandClass.NAL,
                landAreaDecimal = "15.0",
                landUnit = LandUnit.DECIMAL,
                boundaryDescription = "উত্তরে: পাকা রাস্তা, দক্ষিণে: করিমের জমি, পূর্বে: খাল, পশ্চিমে: স্কুল মাঠ",
                notes = "নিরবচ্ছিন্ন ভোগদখলীয় সম্পত্তি"
            )
        ),
        notes = "মূল দলিলের সার্টিফাইড কপি ও নামজারি পরচা যাচাইকৃত।"
    )

    private fun buildLargeMultiPageDeed(): DeedRecord {
        val longBoundary = "উত্তরে: ২০ ফুট প্রশস্ত পাকা সড়ক ও হাজী সাহেবের মার্কেট, দক্ষিণে: সরকারি হালট এবং নাল জমি, " +
            "পূর্বে: দেউলপাড়া খাল সংলগ্ন সীমানা প্রাচীর, পশ্চিমে: প্রাইমারি স্কুল ও মসজিদ প্রাঙ্গণ।"
        val grantors = (1..5).map { idx ->
            PartyInfo(
                fullName = "দাতা মোঃ রফিকুল ইসলাম #$idx",
                fatherName = "হাজী আব্দুল মালেক #$idx",
                motherName = "মরিয়ম বেগম #$idx",
                spouseName = "নাজমা সুলতানা #$idx",
                address = "হোল্ডিং নং-$idx/খ, দেউলপাড়া, ফতুল্লা, নারায়ণগঞ্জ-১৪০০",
                aliases = listOf("রফিক #$idx", "Rafiq-$idx")
            )
        }
        val grantees = (1..4).map { idx ->
            PartyInfo(
                fullName = "গ্রহীতা ফাতেমা তুজ জোহরা #$idx",
                fatherName = "শামসুল হক মিয়া #$idx",
                motherName = "রাবেয়া খাতুন #$idx",
                spouseName = "আতাউর রহমান #$idx",
                address = "সেক্টর-$idx, চাষাড়া, নারায়ণগঞ্জ সদর, নারায়ণগঞ্জ",
                aliases = listOf("ফাতেমা #$idx")
            )
        }
        val parcels = (1..18).map { idx ->
            LandParcel(
                surveyType = if (idx % 2 == 0) SurveyType.BS else SurveyType.RS,
                khatianNumber = "${700 + idx}",
                dagNumber = "${1800 + idx}",
                landClass = if (idx % 3 == 0) LandClass.BASTU else LandClass.NAL,
                landAreaDecimal = "${2 + idx}.5",
                landUnit = if (idx % 4 == 0) LandUnit.KATHA else LandUnit.DECIMAL,
                boundaryDescription = longBoundary,
                notes = "তফসিল অংশ #$idx — সীমানা চিহ্নিত এবং দখল বুঝাইয়া দেওয়া হইল।"
            )
        }
        return DeedRecord(
            id = "deed-large-999",
            createdBy = "uid-supervisor",
            officeName = "Sub-Registry Office, Narayanganj Sadar",
            deedType = DeedType.SAF_KABALA,
            deedNumber = "৯৮৭৬/২০২৫",
            deedYear = 2025,
            registrationDateMillis = 1740000000000L,
            districtName = "Narayanganj",
            upazilaName = "Fatulla Revenue Circle",
            unionName = "Kashipur",
            mouzaName = "Deulpara",
            jlNumber = "149",
            status = DeedStatus.PENDING_REVIEW,
            visibility = DeedVisibility.OFFICE_ONLY,
            rowVersion = 7L,
            createdAtMillis = 1739000000000L,
            updatedAtMillis = 1740000000000L,
            grantors = grantors,
            grantees = grantees,
            parcels = parcels,
            notes = "বহু দাতা ও বহু গ্রহীতা সম্বলিত বৃহৎ তফসিলের দলিল। প্রতিটি দাগের চৌহদ্দি ও খতিয়ান সরেজমিনে যাচাই করা হইয়াছে।"
        )
    }

    @Test
    fun `suggestedPdfFileName handles ascii, bangla numerals, symbols, and draft fallback`() {
        val asciiDeed = DeedRecord(id = "rec-1", deedNumber = "1256/2024", deedYear = 2024)
        assertEquals("Dolil_1256-2024.pdf", DeedDocumentBuilder.suggestedPdfFileName(asciiDeed))

        val banglaDeed = DeedRecord(id = "rec-2", deedNumber = "১২৫৬/২০২৪", deedYear = 2024)
        assertEquals("Dolil_1256-2024.pdf", DeedDocumentBuilder.suggestedPdfFileName(banglaDeed))

        val noYearDeed = DeedRecord(id = "rec-3", deedNumber = "  45 / A ", deedYear = 2025)
        assertEquals("Dolil_45-A_2025.pdf", DeedDocumentBuilder.suggestedPdfFileName(noYearDeed))

        val draftDeed = DeedRecord(id = "abcd1234-efgh", deedNumber = "", deedYear = 2025)
        assertEquals("Dolil_Draft_abcd1234.pdf", DeedDocumentBuilder.suggestedPdfFileName(draftDeed))
    }

    @Test
    fun `document builder maps all deed entry fields and metadata cleanly in both BN and EN`() {
        val deed = buildSmallSampleDeed()

        val enDoc = DeedDocumentBuilder.build(deed, AppLanguage.EN, generatedAtMillis = 1716500000000L)
        assertEquals(AppLanguage.EN, enDoc.language)
        assertEquals("Deed Registration & Land Schedule", enDoc.documentTitle)
        assertEquals("Approved", enDoc.statusText)
        val enGeneralValues = enDoc.generalGridRows.flatMap { listOfNotNull(it.first, it.second) }
        assertTrue(enGeneralValues.any { it.label == "Visibility" && it.value == "Public" })
        assertTrue(enGeneralValues.any { it.label == "Revision" && it.value == "v3" })
        assertTrue(enGeneralValues.any { it.label == "Record ID" && it.value == "deed-pdf-101" })
        assertTrue(enGeneralValues.any { it.label == "Sub-Registry Office" })
        assertEquals("—", enDoc.granteeSections.first().aliases.value)
        assertTrue(enDoc.grantorSections.first().aliases.value.contains("করিম মিয়া"))
        assertTrue(enDoc.totalAreaSummaryText.contains("15.00 Shotok"))

        val bnDoc = DeedDocumentBuilder.build(deed, AppLanguage.BN, generatedAtMillis = 1716500000000L)
        assertEquals(AppLanguage.BN, bnDoc.language)
        assertEquals("দলিল নিবন্ধন ও তফসিল বিবরণী", bnDoc.documentTitle)
        assertEquals("অনুমোদিত", bnDoc.statusText)
        val bnGeneralValues = bnDoc.generalGridRows.flatMap { listOfNotNull(it.first, it.second) }
        assertTrue(bnGeneralValues.any { it.label == "দৃশ্যমানতা" && it.value == "সর্বজনীন" })
        assertTrue(bnGeneralValues.any { it.label == "রেকর্ড সংস্করণ" && it.value == "v3" })
        assertTrue(bnDoc.totalAreaSummaryText.contains("১৫.০০") || bnDoc.totalAreaSummaryText.contains("15.00"))
        assertEquals("পৃষ্ঠা ১ / ৩", DeedDocumentBuilder.formatPageIndicator(AppLanguage.BN, 1, 3))
        assertEquals("Page 2 of 3", DeedDocumentBuilder.formatPageIndicator(AppLanguage.EN, 2, 3))
    }

    @Test
    fun `small deed produces single page A4 PDF with valid PDF bytes in both EN and BN`() {
        val deed = buildSmallSampleDeed()

        listOf(AppLanguage.EN, AppLanguage.BN).forEach { lang ->
            val plan = DeedPdfRenderer.planDocument(context, deed, lang)
            assertEquals(A4Spec.WIDTH_PT, plan.pageWidthPt)
            assertEquals(A4Spec.HEIGHT_PT, plan.pageHeightPt)
            assertTrue(
                "Small deed should fit in 1..2 A4 pages, got ${plan.pages.size}",
                plan.pages.size in 1..2
            )

            val usableBottom = A4Spec.HEIGHT_PT - A4Spec.MARGIN_BOTTOM_PT - A4Spec.FOOTER_HEIGHT_PT - 8f
            plan.pages.forEach { page ->
                page.blocks.forEach { positioned ->
                    assertTrue(
                        "Block exceeded usable bottom on page ${page.pageNumber}",
                        positioned.y + positioned.block.heightPt <= usableBottom + 0.5f
                    )
                }
            }

            val backend = SkiaCanvasVerifierBackend()
            val out = ByteArrayOutputStream()
            val result = DeedPdfRenderer.writePdfToStreamWithBackend(
                context = context,
                deed = deed,
                language = lang,
                outputStream = out,
                backend = backend
            )
            assertTrue("Expected writePdfToStream success for $lang: ${result.exceptionOrNull()}", result.isSuccess)
            assertEquals(plan.pages.size, result.getOrThrow())
            assertEquals(plan.pages.size, backend.renderedPageCount)

            val bytes = out.toByteArray()
            assertTrue("PDF byte array should be non-empty", bytes.isNotEmpty())
            val header = String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)
            assertEquals("%PDF-", header)
        }
    }

    @Test
    fun `large multi-page deed paginates without footer collision and repeats parcel table header`() {
        val largeDeed = buildLargeMultiPageDeed()
        val plan = DeedPdfRenderer.planDocument(context, largeDeed, AppLanguage.BN)

        assertTrue("Large deed must span at least 3 pages, got ${plan.pages.size}", plan.pages.size >= 3)
        plan.pages.forEachIndexed { idx, page ->
            assertEquals(idx + 1, page.pageNumber)
        }

        val usableBottom = A4Spec.HEIGHT_PT - A4Spec.MARGIN_BOTTOM_PT - A4Spec.FOOTER_HEIGHT_PT - 8f
        var pagesWithParcelRows = 0
        plan.pages.forEach { page ->
            page.blocks.forEach { positioned ->
                assertTrue(
                    "Block on page ${page.pageNumber} overlapped footer area: bottom=${positioned.y + positioned.block.heightPt}, usableBottom=$usableBottom",
                    positioned.y + positioned.block.heightPt <= usableBottom + 0.5f
                )
            }
            val hasRows = page.blocks.any { it.block.isParcelTableRow }
            if (hasRows) {
                pagesWithParcelRows++
                assertTrue(
                    "Page ${page.pageNumber} has parcel rows without a ParcelTableHeaderBlock",
                    page.blocks.any { it.block.isParcelTableHeader }
                )
            }
        }
        assertTrue("Expected parcel table to span multiple pages", pagesWithParcelRows >= 2)

        val backend = SkiaCanvasVerifierBackend()
        val out = ByteArrayOutputStream()
        val writeResult = DeedPdfRenderer.writePdfToStreamWithBackend(
            context = context,
            deed = largeDeed,
            language = AppLanguage.BN,
            outputStream = out,
            backend = backend
        )
        assertTrue("Expected writePdfToStream success: ${writeResult.exceptionOrNull()}", writeResult.isSuccess)
        assertEquals(plan.pages.size, writeResult.getOrThrow())
        assertEquals((0 until plan.pages.size).toList(), backend.drawnPages)
        val bytes = out.toByteArray()
        assertEquals("%PDF-", String(bytes.copyOfRange(0, 5), Charsets.US_ASCII))
    }

    @Test
    fun `print adapter defaults to ISO A4 Portrait and writes matching PDF output`() {
        val attrs = DeedPrintHelper.defaultA4PrintAttributes()
        assertEquals(PrintAttributes.MediaSize.ISO_A4, attrs.mediaSize)
        assertEquals(PrintAttributes.COLOR_MODE_COLOR, attrs.colorMode)

        val deed = buildLargeMultiPageDeed()
        val expectedPlan = DeedPdfRenderer.planDocument(context, deed, AppLanguage.EN)
        val backend = SkiaCanvasVerifierBackend()
        val adapter = DeedPrintDocumentAdapter(context, deed, AppLanguage.EN, backend)

        val layoutOutcome = adapter.performLayout(
            oldAttributes = null,
            newAttributes = attrs,
            cancellationSignal = CancellationSignal()
        )
        assertTrue("Expected Finished layout outcome, got $layoutOutcome", layoutOutcome is DeedPrintDocumentAdapter.LayoutOutcome.Finished)
        val finishedLayout = layoutOutcome as DeedPrintDocumentAdapter.LayoutOutcome.Finished
        assertTrue(finishedLayout.changed)
        assertEquals(expectedPlan.pages.size, finishedLayout.info.pageCount)
        assertEquals(DeedDocumentBuilder.suggestedPdfFileName(deed), finishedLayout.info.name)

        // Verify cancellation propagates cleanly
        val cancelledSignal = CancellationSignal().apply { cancel() }
        assertTrue(
            adapter.performLayout(attrs, attrs, cancelledSignal) is DeedPrintDocumentAdapter.LayoutOutcome.Cancelled
        )

        val tempFile = File.createTempFile("dolil_print_test", ".pdf", context.cacheDir)
        try {
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
            val writeOutcome = pfd.use {
                adapter.performWrite(
                    pages = arrayOf(PageRange.ALL_PAGES),
                    destination = it,
                    cancellationSignal = CancellationSignal()
                )
            }

            assertTrue("Expected Finished write outcome, got $writeOutcome", writeOutcome is DeedPrintDocumentAdapter.WriteOutcome.Finished)
            val finishedWrite = writeOutcome as DeedPrintDocumentAdapter.WriteOutcome.Finished
            assertEquals(listOf(PageRange.ALL_PAGES), finishedWrite.writtenRanges.toList())
            assertEquals(expectedPlan.pages.size, backend.renderedPageCount)
            val fileBytes = tempFile.readBytes()
            assertTrue("Printed output file must not be empty", fileBytes.isNotEmpty())
            assertEquals("%PDF-", String(fileBytes.copyOfRange(0, 5), Charsets.US_ASCII))

            // Verify partial page range filtering (page indices 1..2)
            backend.drawnPages.clear()
            val partialPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
            val partialOutcome = partialPfd.use {
                adapter.performWrite(
                    pages = arrayOf(PageRange(1, 2)),
                    destination = it,
                    cancellationSignal = CancellationSignal()
                )
            }
            assertTrue(partialOutcome is DeedPrintDocumentAdapter.WriteOutcome.Finished)
            assertEquals(listOf(1, 2), backend.drawnPages)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `deed details screen exposes download and print actions without attachment or viewer clutter`() {
        var approved = false
        val supervisor = UserProfile(userId = "u2", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)
        val deed = buildSmallSampleDeed().copy(status = DeedStatus.PENDING_REVIEW)

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                DeedDetailsScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    deed = deed,
                    onEditDeed = {},
                    onApproveDeed = { approved = true },
                    onSoftDeleteDeed = {},
                    onRestoreDeed = {},
                    onBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("details_download_pdf_button").assertIsDisplayed()
        composeRule.onNodeWithTag("details_print_button").assertIsDisplayed()

        // Negative-scope assertion: no upload/attach/PDF preview or Change History controls exist
        composeRule.onAllNodesWithContentDescription("Upload", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Attach", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Audit Trail", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("পরিবর্তন ইতিহাস", substring = true)
            .assertCountEquals(0)

        // Existing Approve workflow remains intact
        composeRule.onNodeWithTag("deed_details_screen").performScrollToIndex(5)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("details_approve_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(approved)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h600dp")
    fun `tablet records list-detail pane displays download and print actions in pane top bar`() {
        val supervisor = UserProfile(userId = "u2", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)
        val deed = buildSmallSampleDeed()

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                RecordsScreen(
                    language = AppLanguage.EN,
                    userProfile = supervisor,
                    deedsState = UiState.Success(listOf(deed)),
                    onOpenDeedDetail = {},
                    onCreateNewDeed = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("details_download_pdf_button").assertDoesNotExist()
        composeRule.onNodeWithTag("record_card_deed-pdf-101").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("details_download_pdf_button").assertIsDisplayed()
        composeRule.onNodeWithTag("details_print_button").assertIsDisplayed()
        composeRule.onAllNodesWithText("Audit Trail", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("পরিবর্তন ইতিহাস", substring = true).assertCountEquals(0)
        assertFalse(deed.deedNumber.isBlank())
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h600dp")
    fun `tablet search list-detail pane displays download and print actions in pane top bar`() {
        val supervisor = UserProfile(userId = "u2", fullName = "Supervisor", role = UserRole.OFFICE_SUPERVISOR)
        val deed = buildSmallSampleDeed()

        composeRule.setContent {
            DolilTheme(language = AppLanguage.EN) {
                SearchScreen(
                    language = AppLanguage.EN,
                    filterState = SearchFilterState(),
                    results = listOf(RankedDeedResult(deed, 1000, MatchReason.EXACT_DEED_NUMBER)),
                    onQueryChange = {},
                    onUpdateFilters = { _, _, _, _, _ -> },
                    onClearFilters = {},
                    onLoadMore = {},
                    onOpenDeedDetail = {},
                    userProfile = supervisor
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("record_card_deed-pdf-101").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("details_download_pdf_button").assertIsDisplayed()
        composeRule.onNodeWithTag("details_print_button").assertIsDisplayed()
        composeRule.onAllNodesWithText("Audit Trail", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("পরিবর্তন ইতিহাস", substring = true).assertCountEquals(0)
    }

    @Test
    fun `change history section is globally absent across multiple deeds and languages`() {
        val publicUser = UserProfile(userId = "pub-1", fullName = "Citizen", role = UserRole.PUBLIC_USER)
        val firstDeedBn = buildSmallSampleDeed().copy(
            id = "deed-bn-1",
            deedNumber = "101/2026",
            deedType = DeedType.SAF_KABALA,
            status = DeedStatus.APPROVED
        )
        val secondDeedEn = buildSmallSampleDeed().copy(
            id = "deed-en-2",
            deedNumber = "202/2025",
            deedType = DeedType.HEBA,
            status = DeedStatus.DRAFT
        )
        var currentDeed by mutableStateOf(firstDeedBn)
        var currentLang by mutableStateOf(AppLanguage.BN)

        composeRule.setContent {
            DolilTheme(language = currentLang) {
                DeedDetailsScreen(
                    language = currentLang,
                    userProfile = publicUser,
                    deed = currentDeed,
                    onEditDeed = {},
                    onApproveDeed = {},
                    onSoftDeleteDeed = {},
                    onRestoreDeed = {},
                    onBack = {}
                )
            }
        }

        // First deed in Bangla (Approved Safkobla, no action buttons)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("deed_details_screen").performScrollToIndex(4)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("পরিবর্তন ইতিহাস", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("এখনো কোনো স্থানীয় লগ নেই", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Audit Trail", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Change History", substring = true, ignoreCase = true).assertCountEquals(0)

        // Second deed in English (Draft Heba)
        currentDeed = secondDeedEn
        currentLang = AppLanguage.EN
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("deed_details_screen").performScrollToIndex(4)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("পরিবর্তন ইতিহাস", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Audit Trail", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("No audit entries recorded yet", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Change History", substring = true, ignoreCase = true).assertCountEquals(0)
    }
}
