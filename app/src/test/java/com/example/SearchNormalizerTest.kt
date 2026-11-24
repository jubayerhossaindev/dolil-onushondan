package com.example

import com.example.domain.model.DeedRecord
import com.example.domain.model.LandParcel
import com.example.domain.model.MatchReason
import com.example.domain.model.PartyInfo
import com.example.domain.search.DefaultIndexedSearchStrategy
import com.example.domain.search.SearchNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizerTest {

    @Test
    fun `normalizeText converts Bengali digits applies NFC and collapses slashes`() {
        assertEquals("1256/2024", SearchNormalizer.normalizeText("  ১২৫৬ / ২০২৪ "))
        assertEquals("abdul karim", SearchNormalizer.normalizeText("  Abdul   Karim. "))
        val nfcNarayanganj = java.text.Normalizer.normalize("নারায়ণগঞ্জ", java.text.Normalizer.Form.NFC)
        assertEquals(nfcNarayanganj, SearchNormalizer.normalizeText("নারা\u09AF\u09BCণগঞ্জ,"))
        assertEquals(nfcNarayanganj, SearchNormalizer.normalizeText("নারা\u09DFণগঞ্জ"))
    }

    @Test
    fun `normalizeCode strips spaces around digits and slashes`() {
        assertEquals("1256/2024", SearchNormalizer.normalizeCode("১২৫৬ / ২০২৪"))
        assertEquals("rs420", SearchNormalizer.normalizeCode("RS 420"))
    }

    @Test
    fun `buildSearchTokens caps token list and includes Bengali and English tokens`() {
        val deed = DeedRecord(
            id = "d1",
            deedNumber = "১২৫৬/২০২৪",
            deedYear = 2024,
            districtName = "নারায়ণগঞ্জ",
            upazilaName = "ফতুল্লা রাজস্ব সার্কেল",
            mouzaName = "ফতুল্লা",
            jlNumber = "১৪০",
            grantors = listOf(PartyInfo(fullName = "আব্দুল করিম", fatherName = "রহিম উদ্দিন")),
            grantees = listOf(PartyInfo(fullName = "সালমা বেগম")),
            parcels = listOf(LandParcel(khatianNumber = "৭১২", dagNumber = "১৮৪৫"))
        )

        val tokens = SearchNormalizer.buildSearchTokens(deed)
        assertTrue(tokens.size <= 140)
        assertTrue("1256/2024" in tokens)
        assertTrue("1256" in tokens)
        assertTrue("712" in tokens)
        assertTrue("1845" in tokens)
        assertTrue(java.text.Normalizer.normalize("ফতুল্লা", java.text.Normalizer.Form.NFC) in tokens)
        assertTrue("আব্দুল" in tokens)
    }

    @Test
    fun `rank prioritizes exact deed number over dag person mouza and substring`() {
        val deedExactNo = DeedRecord(
            id = "deed_exact",
            deedNumber = "1256/2024",
            mouzaName = "চাষাড়া",
            registrationDateMillis = 1000L
        )
        val deedDag = DeedRecord(
            id = "deed_dag",
            deedNumber = "9999/2023",
            mouzaName = "ফতুল্লা",
            parcels = listOf(LandParcel(dagNumber = "1256/2024", khatianNumber = "88")),
            registrationDateMillis = 5000L
        )
        val deedPerson = DeedRecord(
            id = "deed_person",
            deedNumber = "300/2022",
            grantors = listOf(PartyInfo(fullName = "Rahim Mia")),
            registrationDateMillis = 4000L
        )
        val deedPrefix = DeedRecord(
            id = "deed_prefix",
            deedNumber = "1256/2024-A",
            registrationDateMillis = 3000L
        )

        val strategy = DefaultIndexedSearchStrategy()

        val deedNoResults = strategy.rank(listOf(deedDag, deedPrefix, deedExactNo), "১২৫৬ / ২০২৪")
        assertEquals(3, deedNoResults.size)
        assertEquals("deed_exact", deedNoResults[0].deed.id)
        assertEquals(MatchReason.EXACT_DEED_NUMBER, deedNoResults[0].matchReason)
        assertEquals("deed_dag", deedNoResults[1].deed.id)
        assertEquals(MatchReason.EXACT_DAG_OR_KHATIAN, deedNoResults[1].matchReason)
        assertEquals("deed_prefix", deedNoResults[2].deed.id)
        assertEquals(MatchReason.PREFIX_MATCH, deedNoResults[2].matchReason)

        val personResults = strategy.rank(listOf(deedExactNo, deedPerson), "rahim mia")
        assertEquals(1, personResults.size)
        assertEquals("deed_person", personResults[0].deed.id)
        assertEquals(MatchReason.EXACT_PERSON_NAME, personResults[0].matchReason)
    }
}
