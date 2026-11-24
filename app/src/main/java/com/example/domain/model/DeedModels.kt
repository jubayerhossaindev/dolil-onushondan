package com.example.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class PartyInfo(
    val id: String = UUID.randomUUID().toString(),
    val fullName: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val spouseName: String = "",
    val address: String = "",
    val aliases: List<String> = emptyList()
)

data class LandParcel(
    val id: String = UUID.randomUUID().toString(),
    val surveyType: SurveyType = SurveyType.RS,
    val khatianNumber: String = "",
    val dagNumber: String = "",
    val landClass: LandClass = LandClass.NAL,
    val landAreaDecimal: String = "",
    val landUnit: LandUnit = LandUnit.DECIMAL,
    val boundaryDescription: String = "",
    val notes: String = ""
) {
    fun areaAsShotok(): BigDecimal {
        val sb = StringBuilder(landAreaDecimal.length)
        for (ch in landAreaDecimal.trim()) {
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
                    ',' -> '.'
                    else -> ch
                }
            )
        }
        val parsed = sb.toString().toBigDecimalOrNull() ?: BigDecimal.ZERO
        if (parsed <= BigDecimal.ZERO) return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
        return parsed.multiply(BigDecimal.valueOf(landUnit.toShotokFactor))
            .setScale(4, RoundingMode.HALF_UP)
    }
}

data class DeedRecord(
    val id: String = UUID.randomUUID().toString(),
    val ownerUid: String = "",
    val deviceId: String = "",
    val revisionId: String = UUID.randomUUID().toString(),
    val createdBy: String = "",
    val updatedBy: String = "",
    val organizationId: String = "org_bd_land",
    val officeId: String = "office_narayanganj_sadar",
    val officeName: String = "সাব-রেজিস্ট্রি অফিস, নারায়ণগঞ্জ সদর",
    val deedType: DeedType = DeedType.SAF_KABALA,
    val deedNumber: String = "",
    val normalizedDeedNumber: String = "",
    val deedYear: Int = 2024,
    val registrationDateMillis: Long = System.currentTimeMillis(),
    val districtName: String = "",
    val upazilaName: String = "",
    val unionName: String = "",
    val mouzaName: String = "",
    val jlNumber: String = "",
    val status: DeedStatus = DeedStatus.DRAFT,
    val visibility: DeedVisibility = DeedVisibility.PRIVATE_DRAFT,
    val rowVersion: Long = 1L,
    val grantors: List<PartyInfo> = listOf(PartyInfo()),
    val grantees: List<PartyInfo> = listOf(PartyInfo()),
    val parcels: List<LandParcel> = listOf(LandParcel()),
    val notes: String = "",
    val syncState: SyncState = SyncState.LOCAL_ONLY_DRAFT,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deletedAtMillis: Long? = null,
    val isDeleted: Boolean = false,
    val deletedByDeviceId: String? = null
) {
    val primaryGrantorName: String
        get() = grantors.firstOrNull { it.fullName.isNotBlank() }?.fullName.orEmpty()

    val primaryGranteeName: String
        get() = grantees.firstOrNull { it.fullName.isNotBlank() }?.fullName.orEmpty()

    fun computeTotalShotokDecimal(): BigDecimal =
        parcels.fold(BigDecimal.ZERO) { acc, parcel -> acc.add(parcel.areaAsShotok()) }
            .setScale(2, RoundingMode.HALF_UP)

    val totalAreaDecimalText: String
        get() = computeTotalShotokDecimal().stripTrailingZeros().toPlainString().ifBlank { "0" }
}

data class UserProfile(
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.PUBLIC_USER,
    val organizationId: String = "org_bd_land",
    val officeId: String = "office_narayanganj_sadar",
    val officeName: String = "সাব-রেজিস্ট্রি অফিস, নারায়ণগঞ্জ সদর",
    val status: String = "ACTIVE",
    val preferredLanguage: AppLanguage = AppLanguage.BN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isEmailVerified: Boolean = false,
    val authProviderIds: List<String> = emptyList()
)

data class AuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val actorId: String = "",
    val actorName: String = "",
    val organizationId: String = "org_bd_land",
    val officeId: String = "office_narayanganj_sadar",
    val action: AuditAction = AuditAction.CREATE,
    val entityType: String = "DEED",
    val entityId: String = "",
    val previousVersion: Long = 0L,
    val newVersion: Long = 1L,
    val summary: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class LocationDistrict(
    val code: String,
    val bnName: String,
    val enName: String
)

data class LocationUpazila(
    val code: String,
    val districtCode: String,
    val bnName: String,
    val enName: String,
    val isRevenueCircle: Boolean = false,
    val bbsCode: String = ""
)

data class LocationMouza(
    val code: String,
    val upazilaCode: String,
    val unionBn: String,
    val unionEn: String,
    val mouzaBn: String,
    val mouzaEn: String,
    val jlNumber: String,
    val districtCode: String = "",
    val surveyTags: List<String> = emptyList(),
    val verificationStatus: String = "verified"
)

data class LocationSurveyType(
    val id: String,
    val codeEn: String,
    val nameBn: String,
    val order: Int = 99
)

enum class MatchReason(val bnLabel: String, val enLabel: String) {
    EXACT_DEED_NUMBER("সঠিক দলিল নম্বর", "Exact Deed Number"),
    EXACT_DAG_OR_KHATIAN("দাগ / খতিয়ান মিল", "Exact Dag / Khatian"),
    EXACT_PERSON_NAME("ব্যক্তির নাম মিল", "Exact Person Name"),
    PREFIX_MATCH("আংশিক / প্রিফিক্স মিল", "Prefix Match"),
    MOUZA_MATCH("মৌজা মিল", "Mouza Match"),
    SUBSTRING_MATCH("সাধারণ অনুসন্ধান মিল", "Keyword Match")
}

data class RankedDeedResult(
    val deed: DeedRecord,
    val score: Int,
    val matchReason: MatchReason
)

data class DeedConflict(
    val localDeed: DeedRecord,
    val serverDeed: DeedRecord,
    val remoteDeviceId: String = "",
    val remoteCommitId: String = "",
    val deedId: String = localDeed.id
)

data class OfficeStats(
    val totalDeeds: Int = 0,
    val todayEntries: Int = 0,
    val draftCount: Int = 0,
    val pendingReviewCount: Int = 0
)
