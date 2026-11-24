package com.example.core.print

import com.example.core.designsystem.DolilStrings
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.LandParcel
import com.example.domain.model.PartyInfo
import java.math.RoundingMode

object A4Spec {
    const val WIDTH_PT = 595
    const val HEIGHT_PT = 842
    const val MARGIN_HORIZONTAL_PT = 40f
    const val MARGIN_TOP_PT = 40f
    const val MARGIN_BOTTOM_PT = 40f
    const val RUNNING_HEADER_HEIGHT_PT = 24f
    const val FOOTER_HEIGHT_PT = 24f
}

data class DocumentKeyValue(
    val label: String,
    val value: String
)

data class DocumentPartySection(
    val heading: String,
    val gridRows: List<Pair<DocumentKeyValue, DocumentKeyValue?>>,
    val address: DocumentKeyValue,
    val aliases: DocumentKeyValue
)

data class DocumentParcelRow(
    val indexText: String,
    val surveyText: String,
    val khatianText: String,
    val dagText: String,
    val landClassText: String,
    val areaText: String,
    val boundaryAndNotesText: String
)

data class DeedDocumentData(
    val language: AppLanguage,
    val appTitle: String,
    val documentTitle: String,
    val officeName: String,
    val deedNumberDisplay: String,
    val statusText: String,
    val status: DeedStatus,
    val regDateText: String,
    val runningHeaderLeft: String,
    val runningHeaderRight: String,
    val footerLeftText: String,
    val generalSectionTitle: String,
    val generalGridRows: List<Pair<DocumentKeyValue, DocumentKeyValue?>>,
    val notesField: DocumentKeyValue,
    val grantorsSectionTitle: String,
    val grantorSections: List<DocumentPartySection>,
    val granteesSectionTitle: String,
    val granteeSections: List<DocumentPartySection>,
    val locationSectionTitle: String,
    val locationGridRows: List<Pair<DocumentKeyValue, DocumentKeyValue?>>,
    val landSectionTitle: String,
    val parcelHeaders: List<String>,
    val parcelRows: List<DocumentParcelRow>,
    val totalAreaSummaryText: String
)

object DeedDocumentBuilder {

    private const val EMPTY_VALUE = "—"

    fun suggestedPdfFileName(deed: DeedRecord): String {
        val asciiDigits = buildString(deed.deedNumber.length) {
            for (ch in deed.deedNumber.trim()) {
                append(
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
                        '/', '\\', ' ' -> '-'
                        else -> ch
                    }
                )
            }
        }
        val cleanNumber = asciiDigits
            .replace(Regex("[^A-Za-z0-9_-]"), "")
            .replace(Regex("-{2,}"), "-")
            .trim('-', '_')

        return if (cleanNumber.isBlank()) {
            val shortId = deed.id
                .replace(Regex("[^A-Za-z0-9_-]"), "")
                .take(8)
                .ifBlank { "record" }
            "Dolil_Draft_$shortId.pdf"
        } else if (cleanNumber.endsWith("-${deed.deedYear}") || cleanNumber.endsWith("_${deed.deedYear}")) {
            "Dolil_$cleanNumber.pdf"
        } else {
            "Dolil_${cleanNumber}_${deed.deedYear}.pdf"
        }
    }

    fun formatPageIndicator(language: AppLanguage, pageNumber: Int, totalPages: Int): String =
        if (language == AppLanguage.BN) {
            "পৃষ্ঠা ${toBanglaDigits(pageNumber.toString())} / ${toBanglaDigits(totalPages.toString())}"
        } else {
            "Page $pageNumber of $totalPages"
        }

    fun build(
        deed: DeedRecord,
        language: AppLanguage,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): DeedDocumentData {
        val isBn = language == AppLanguage.BN
        val regDate = DolilStrings.formatDate(deed.registrationDateMillis, language)
        val createdAt = DolilStrings.formatDateTime(deed.createdAtMillis, language)
        val updatedAt = DolilStrings.formatDateTime(deed.updatedAtMillis, language)
        val generatedAt = DolilStrings.formatDateTime(generatedAtMillis, language)

        val deedNoText = DolilStrings.deedNumberFormatted(deed.deedNumber, deed.deedYear, language)
        val officeDisplay = deed.officeName.ifBlank { EMPTY_VALUE }
        val statusDisplay = DolilStrings.statusText(deed.status, language)

        val generalGridRows = pairUp(
            listOf(
                DocumentKeyValue(DolilStrings.deedTypeLabel(language), DolilStrings.deedTypeText(deed.deedType, language)),
                DocumentKeyValue(DolilStrings.deedNumberLabel(language), deed.deedNumber.ifBlank { EMPTY_VALUE }),
                DocumentKeyValue(DolilStrings.deedYearLabel(language), deed.deedYear.toString()),
                DocumentKeyValue(DolilStrings.regDateLabel(language), regDate),
                DocumentKeyValue(DolilStrings.officeLabel(language), officeDisplay),
                DocumentKeyValue(if (isBn) "অবস্থা (Status)" else "Status", statusDisplay),
                DocumentKeyValue(if (isBn) "দৃশ্যমানতা" else "Visibility", DolilStrings.visibilityText(deed.visibility, language)),
                DocumentKeyValue(if (isBn) "রেকর্ড সংস্করণ" else "Revision", "v${deed.rowVersion}"),
                DocumentKeyValue(if (isBn) "তৈরির সময়" else "Created At", createdAt),
                DocumentKeyValue(if (isBn) "সর্বশেষ হালনাগাদ" else "Last Updated", updatedAt),
                DocumentKeyValue(if (isBn) "নথি আইডি" else "Record ID", deed.id.ifBlank { EMPTY_VALUE })
            )
        )

        val grantors = deed.grantors.ifEmpty { listOf(PartyInfo()) }
        val grantorSections = grantors.mapIndexed { index, party ->
            buildPartySection(
                heading = if (isBn) "দাতা #${toBanglaDigits((index + 1).toString())}" else "Grantor #${index + 1}",
                party = party,
                language = language
            )
        }

        val grantees = deed.grantees.ifEmpty { listOf(PartyInfo()) }
        val granteeSections = grantees.mapIndexed { index, party ->
            buildPartySection(
                heading = if (isBn) "গ্রহীতা #${toBanglaDigits((index + 1).toString())}" else "Grantee #${index + 1}",
                party = party,
                language = language
            )
        }

        val locationGridRows = pairUp(
            listOf(
                DocumentKeyValue(DolilStrings.districtLabel(language), deed.districtName.ifBlank { EMPTY_VALUE }),
                DocumentKeyValue(DolilStrings.upazilaLabel(language), deed.upazilaName.ifBlank { EMPTY_VALUE }),
                DocumentKeyValue(DolilStrings.unionLabel(language), deed.unionName.ifBlank { EMPTY_VALUE }),
                DocumentKeyValue(DolilStrings.mouzaLabel(language), deed.mouzaName.ifBlank { EMPTY_VALUE }),
                DocumentKeyValue(DolilStrings.jlNumberLabel(language), deed.jlNumber.ifBlank { EMPTY_VALUE })
            )
        )

        val parcels = deed.parcels.ifEmpty { listOf(LandParcel()) }
        val parcelHeaders = if (isBn) {
            listOf("ক্রম", "জরিপ", "খতিয়ান", "দাগ", "শ্রেণি", "জমির পরিমাণ", "চৌহদ্দি ও মন্তব্য")
        } else {
            listOf("#", "Survey", "Khatian", "Dag", "Class", "Area", "Boundary & Notes")
        }

        val parcelRows = parcels.mapIndexed { idx, parcel ->
            val shotokStr = parcel.areaAsShotok().setScale(2, RoundingMode.HALF_UP).toPlainString()
            val enteredStr = parcel.landAreaDecimal.ifBlank { "0" }
            val unitLabel = DolilStrings.landUnitText(parcel.landUnit, language)
            val areaDisplay = "$enteredStr $unitLabel\n($shotokStr ${DolilStrings.shotokUnit(language)})"
            val boundaryNotes = listOf(parcel.boundaryDescription.trim(), parcel.notes.trim())
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .ifBlank { EMPTY_VALUE }
            DocumentParcelRow(
                indexText = if (isBn) toBanglaDigits((idx + 1).toString()) else (idx + 1).toString(),
                surveyText = if (isBn) parcel.surveyType.bnLabel else parcel.surveyType.enLabel,
                khatianText = parcel.khatianNumber.ifBlank { EMPTY_VALUE },
                dagText = parcel.dagNumber.ifBlank { EMPTY_VALUE },
                landClassText = DolilStrings.landClassText(parcel.landClass, language),
                areaText = areaDisplay,
                boundaryAndNotesText = boundaryNotes
            )
        }

        val totalShotok = deed.computeTotalShotokDecimal()
        val totalKatha = totalShotok.divide(java.math.BigDecimal("1.65"), 2, RoundingMode.HALF_UP)
        val totalSummaryText = if (isBn) {
            "মোট জমির পরিমাণ: ${totalShotok.toPlainString()} শতাংশ (≈ ${totalKatha.toPlainString()} কাঠা)  •  মোট তফসিল: ${toBanglaDigits(parcels.size.toString())} টি"
        } else {
            "Total Computed Area: ${totalShotok.toPlainString()} Shotok (≈ ${totalKatha.toPlainString()} Katha)  •  Total Parcels: ${parcels.size}"
        }

        return DeedDocumentData(
            language = language,
            appTitle = DolilStrings.appTitle(language),
            documentTitle = if (isBn) "দলিল নিবন্ধন ও তফসিল বিবরণী" else "Deed Registration & Land Schedule",
            officeName = officeDisplay,
            deedNumberDisplay = deedNoText,
            statusText = statusDisplay,
            status = deed.status,
            regDateText = regDate,
            runningHeaderLeft = "${DolilStrings.appTitle(language)} — $deedNoText",
            runningHeaderRight = officeDisplay,
            footerLeftText = if (isBn) {
                "${DolilStrings.appTitle(language)} • নথি প্রস্তুত: $generatedAt"
            } else {
                "${DolilStrings.appTitle(language)} • Generated: $generatedAt"
            },
            generalSectionTitle = if (isBn) "ক. সাধারণ তথ্য ও নথির বিবরণ" else "A. General Information & Record Metadata",
            generalGridRows = generalGridRows,
            notesField = DocumentKeyValue(DolilStrings.notesLabel(language), deed.notes.ifBlank { EMPTY_VALUE }),
            grantorsSectionTitle = DolilStrings.secGrantors(language),
            grantorSections = grantorSections,
            granteesSectionTitle = DolilStrings.secGrantees(language),
            granteeSections = granteeSections,
            locationSectionTitle = DolilStrings.secLocation(language),
            locationGridRows = locationGridRows,
            landSectionTitle = DolilStrings.secLand(language),
            parcelHeaders = parcelHeaders,
            parcelRows = parcelRows,
            totalAreaSummaryText = totalSummaryText
        )
    }

    private fun buildPartySection(
        heading: String,
        party: PartyInfo,
        language: AppLanguage
    ): DocumentPartySection {
        val isBn = language == AppLanguage.BN
        val gridRows = pairUp(
            listOf(
                DocumentKeyValue(DolilStrings.fullNameLabel(language), party.fullName.ifBlank { EMPTY_VALUE }),
                DocumentKeyValue(DolilStrings.fatherNameLabel(language), party.fatherName.ifBlank { EMPTY_VALUE }),
                DocumentKeyValue(DolilStrings.motherNameLabel(language), party.motherName.ifBlank { EMPTY_VALUE }),
                DocumentKeyValue(DolilStrings.spouseNameLabel(language), party.spouseName.ifBlank { EMPTY_VALUE })
            )
        )
        val aliasesStr = party.aliases.filter { it.isNotBlank() }.joinToString(", ").ifBlank { EMPTY_VALUE }
        return DocumentPartySection(
            heading = heading,
            gridRows = gridRows,
            address = DocumentKeyValue(DolilStrings.addressLabel(language), party.address.ifBlank { EMPTY_VALUE }),
            aliases = DocumentKeyValue(if (isBn) "অন্যান্য নাম / উপনাম" else "Aliases", aliasesStr)
        )
    }

    private fun pairUp(items: List<DocumentKeyValue>): List<Pair<DocumentKeyValue, DocumentKeyValue?>> {
        val pairs = mutableListOf<Pair<DocumentKeyValue, DocumentKeyValue?>>()
        var i = 0
        while (i < items.size) {
            val left = items[i]
            val right = items.getOrNull(i + 1)
            pairs.add(left to right)
            i += 2
        }
        return pairs
    }

    private fun toBanglaDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch) {
                    '0' -> '০'
                    '1' -> '১'
                    '2' -> '২'
                    '3' -> '৩'
                    '4' -> '৪'
                    '5' -> '৫'
                    '6' -> '৬'
                    '7' -> '৭'
                    '8' -> '৮'
                    '9' -> '৯'
                    else -> ch
                }
            )
        }
    }
}
