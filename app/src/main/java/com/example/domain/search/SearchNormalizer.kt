package com.example.domain.search

import com.example.domain.model.DeedRecord
import com.example.domain.model.MatchReason
import com.example.domain.model.RankedDeedResult
import java.text.Normalizer
import java.util.Locale

interface SearchStrategy {
    fun rank(deeds: List<DeedRecord>, rawQuery: String): List<RankedDeedResult>
}

object SearchNormalizer {

    private val whitespaceRegex = Regex("\\s+")
    private val slashSpacingRegex = Regex("\\s*/\\s*")
    private val punctuationEdgeRegex = Regex("^[,.;:।]+|[,.;:।]+$")

    /**
     * Normalizes Bengali and English text without merging distinct Bengali letters.
     * Converts Bengali digits (০-৯) to ASCII (0-9), applies Unicode NFC, collapses whitespace,
     * normalizes slash spacing (`১২৫৬ / ২০২৪` -> `1256/2024`), and lowercases Latin characters.
     */
    fun normalizeText(input: String): String {
        if (input.isBlank()) return ""
        val nfc = Normalizer.normalize(input.trim(), Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length)
        for (ch in nfc) {
            sb.append(
                when (ch) {
                    '০' -> '0'
                    '১' -> '1'
                    '২' -> '2'
                    '৩' -> '3'
                    '৪' -> '4'
                    '৫' -> '5'
                    '৬' -> '6'
                    '৭' -> '7'
                    '৮' -> '8'
                    '৯' -> '9'
                    else -> ch
                }
            )
        }
        return sb.toString()
            .lowercase(Locale.ENGLISH)
            .replace(slashSpacingRegex, "/")
            .replace(whitespaceRegex, " ")
            .trim()
            .replace(punctuationEdgeRegex, "")
    }

    fun normalizeCode(input: String): String =
        normalizeText(input).replace(" ", "")

    fun normalizePersonName(input: String): String =
        normalizeText(input)

    /**
     * Generates a bounded list (<= 140) of normalized tokens and prefixes for indexed Firestore
     * `array-contains` and Room SQL prefix queries.
     */
    fun buildSearchTokens(deed: DeedRecord, maxTokens: Int = 140): List<String> {
        val tokens = linkedSetOf<String>()

        fun addTermWithPrefixes(raw: String) {
            val norm = normalizeText(raw)
            if (norm.isBlank()) return
            tokens.add(norm)
            val compact = norm.replace(" ", "")
            if (compact.isNotBlank()) tokens.add(compact)

            // Add word-level tokens and bounded prefixes (length >= 2)
            val words = norm.split(' ', '/', '-')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            for (word in words) {
                tokens.add(word)
                if (word.length >= 2) {
                    val limit = minOf(word.length, 8)
                    for (len in 2..limit) {
                        tokens.add(word.substring(0, len))
                    }
                }
            }
        }

        addTermWithPrefixes(deed.deedNumber)
        addTermWithPrefixes(deed.deedYear.toString())
        addTermWithPrefixes(deed.districtName)
        addTermWithPrefixes(deed.upazilaName)
        addTermWithPrefixes(deed.unionName)
        addTermWithPrefixes(deed.mouzaName)
        addTermWithPrefixes(deed.jlNumber)

        (deed.grantors + deed.grantees).forEach { party ->
            addTermWithPrefixes(party.fullName)
            addTermWithPrefixes(party.fatherName)
            addTermWithPrefixes(party.motherName)
            addTermWithPrefixes(party.spouseName)
            party.aliases.forEach { addTermWithPrefixes(it) }
        }

        deed.parcels.forEach { parcel ->
            addTermWithPrefixes(parcel.khatianNumber)
            addTermWithPrefixes(parcel.dagNumber)
        }

        return tokens.take(maxTokens)
    }
}

class DefaultIndexedSearchStrategy : SearchStrategy {

    override fun rank(deeds: List<DeedRecord>, rawQuery: String): List<RankedDeedResult> {
        val normalizedQuery = SearchNormalizer.normalizeText(rawQuery)
        if (normalizedQuery.isBlank()) {
            return deeds.map { RankedDeedResult(it, 0, MatchReason.SUBSTRING_MATCH) }
        }
        val compactQuery = normalizedQuery.replace(" ", "")

        return deeds.mapNotNull { deed ->
            scoreDeed(deed, normalizedQuery, compactQuery)
        }.sortedWith(
            compareByDescending<RankedDeedResult> { it.score }
                .thenByDescending { it.deed.registrationDateMillis }
                .thenBy { it.deed.id }
        )
    }

    fun scoreDeed(
        deed: DeedRecord,
        normalizedQuery: String,
        compactQuery: String
    ): RankedDeedResult? {
        val normDeedNo = SearchNormalizer.normalizeCode(deed.deedNumber)
        if (normDeedNo == compactQuery || normDeedNo == normalizedQuery) {
            return RankedDeedResult(deed, 1000, MatchReason.EXACT_DEED_NUMBER)
        }

        val hasExactDagOrKhatian = deed.parcels.any { parcel ->
            SearchNormalizer.normalizeCode(parcel.dagNumber) == compactQuery ||
                SearchNormalizer.normalizeCode(parcel.khatianNumber) == compactQuery
        }
        if (hasExactDagOrKhatian) {
            return RankedDeedResult(deed, 900, MatchReason.EXACT_DAG_OR_KHATIAN)
        }

        val allParties = deed.grantors + deed.grantees
        val hasExactPerson = allParties.any { party ->
            SearchNormalizer.normalizePersonName(party.fullName) == normalizedQuery ||
                SearchNormalizer.normalizePersonName(party.fatherName) == normalizedQuery ||
                SearchNormalizer.normalizePersonName(party.motherName) == normalizedQuery ||
                SearchNormalizer.normalizePersonName(party.spouseName) == normalizedQuery ||
                party.aliases.any { SearchNormalizer.normalizePersonName(it) == normalizedQuery }
        }
        if (hasExactPerson) {
            return RankedDeedResult(deed, 800, MatchReason.EXACT_PERSON_NAME)
        }

        val normMouza = SearchNormalizer.normalizeText(deed.mouzaName)
        if (normMouza == normalizedQuery) {
            return RankedDeedResult(deed, 700, MatchReason.MOUZA_MATCH)
        }

        val candidateFields = buildList {
            add(normDeedNo)
            add(deed.deedYear.toString())
            add(normMouza)
            add(SearchNormalizer.normalizeText(deed.districtName))
            add(SearchNormalizer.normalizeText(deed.upazilaName))
            add(SearchNormalizer.normalizeCode(deed.jlNumber))
            allParties.forEach { p ->
                add(SearchNormalizer.normalizePersonName(p.fullName))
                add(SearchNormalizer.normalizePersonName(p.fatherName))
                add(SearchNormalizer.normalizePersonName(p.motherName))
                add(SearchNormalizer.normalizePersonName(p.spouseName))
                p.aliases.forEach { add(SearchNormalizer.normalizePersonName(it)) }
            }
            deed.parcels.forEach { parcel ->
                add(SearchNormalizer.normalizeCode(parcel.dagNumber))
                add(SearchNormalizer.normalizeCode(parcel.khatianNumber))
            }
        }.filter { it.isNotBlank() }

        if (candidateFields.any { it.startsWith(normalizedQuery) || it.startsWith(compactQuery) }) {
            return RankedDeedResult(deed, 600, MatchReason.PREFIX_MATCH)
        }

        // Multi-word query support (all terms must match at least one field)
        val queryParts = normalizedQuery.split(' ').filter { it.isNotBlank() }
        val allTermsMatch = queryParts.all { term ->
            candidateFields.any { field -> field.contains(term) }
        }
        if (allTermsMatch) {
            return RankedDeedResult(deed, 400, MatchReason.SUBSTRING_MATCH)
        }

        return null
    }
}
