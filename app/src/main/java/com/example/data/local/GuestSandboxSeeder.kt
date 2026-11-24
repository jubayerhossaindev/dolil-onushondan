package com.example.data.local

import com.example.domain.model.AuditAction
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedType
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandClass
import com.example.domain.model.LandParcel
import com.example.domain.model.LandUnit
import com.example.domain.model.PartyInfo
import com.example.domain.model.SurveyType
import com.example.domain.model.SyncState
import com.example.domain.search.SearchNormalizer
import java.util.UUID

object GuestSandboxSeeder {

    const val GUEST_USER_ID = "guest_local_sandbox"

    private val DEMO_SEED_IDS = setOf("demo-deed-1", "demo-deed-2", "demo-deed-3", "demo-deed-4")

    suspend fun getUserCreatedGuestDeeds(dao: DolilDao): List<DeedRecord> {
        return dao.getActiveDeedsForOwnerOnce(GUEST_USER_ID)
            .filterNot { it.id in DEMO_SEED_IDS }
            .map { it.toDomain() }
    }

    suspend fun seedIfEmpty(dao: DolilDao) {
        if (dao.getAllDeedsOnce().isNotEmpty()) return

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        val demoDeeds = listOf(
            DeedRecord(
                id = "demo-deed-1",
                ownerUid = GUEST_USER_ID,
                createdBy = GUEST_USER_ID,
                updatedBy = GUEST_USER_ID,
                officeId = "office_fatulla",
                officeName = "সাব-রেজিস্ট্রি অফিস, ফতুল্লা",
                deedType = DeedType.SAF_KABALA,
                deedNumber = "1256/2024",
                normalizedDeedNumber = SearchNormalizer.normalizeCode("1256/2024"),
                deedYear = 2024,
                registrationDateMillis = now - 30 * dayMs,
                districtName = "নারায়ণগঞ্জ",
                upazilaName = "ফতুল্লা রাজস্ব সার্কেল",
                unionName = "ফতুল্লা",
                mouzaName = "ফতুল্লা",
                jlNumber = "158",
                grantors = listOf(
                    PartyInfo(
                        id = "demo-p1",
                        fullName = "আব্দুল করিম",
                        fatherName = "রহিম উদ্দিন",
                        address = "ফতুল্লা, নারায়ণগঞ্জ"
                    )
                ),
                grantees = listOf(
                    PartyInfo(
                        id = "demo-p2",
                        fullName = "সালমা বেগম",
                        fatherName = "হাবিবুল্লাহ",
                        address = "চাষাড়া, নারায়ণগঞ্জ"
                    )
                ),
                parcels = listOf(
                    LandParcel(
                        id = "demo-parcel-1",
                        surveyType = SurveyType.RS,
                        khatianNumber = "712",
                        dagNumber = "1845",
                        landClass = LandClass.NAL,
                        landAreaDecimal = "15.0",
                        landUnit = LandUnit.DECIMAL,
                        boundaryDescription = "উত্তরে রাস্তা, দক্ষিণে খাল"
                    )
                ),
                status = DeedStatus.APPROVED,
                visibility = DeedVisibility.PUBLIC,
                syncState = SyncState.LOCAL_ONLY_DRAFT,
                notes = "Demo Data • নারায়ণগঞ্জ কৃষি জমি হস্তান্তর দলিলের নমুনা",
                createdAtMillis = now - 30 * dayMs,
                updatedAtMillis = now - 28 * dayMs
            ),
            DeedRecord(
                id = "demo-deed-2",
                ownerUid = GUEST_USER_ID,
                createdBy = GUEST_USER_ID,
                updatedBy = GUEST_USER_ID,
                officeId = "office_fatulla",
                officeName = "সাব-রেজিস্ট্রি অফিস, ফতুল্লা",
                deedType = DeedType.HEBA,
                deedNumber = "842/2023",
                normalizedDeedNumber = SearchNormalizer.normalizeCode("842/2023"),
                deedYear = 2023,
                registrationDateMillis = now - 140 * dayMs,
                districtName = "নারায়ণগঞ্জ",
                upazilaName = "ফতুল্লা রাজস্ব সার্কেল",
                unionName = "ফতুল্লা",
                mouzaName = "চাষাড়া",
                jlNumber = "36",
                grantors = listOf(
                    PartyInfo(
                        id = "demo-p3",
                        fullName = "রফিকুল ইসলাম",
                        fatherName = "মোবারক আলী",
                        address = "চাষাড়া, নারায়ণগঞ্জ"
                    )
                ),
                grantees = listOf(
                    PartyInfo(
                        id = "demo-p4",
                        fullName = "নাসরিন আক্তার",
                        spouseName = "রফিকুল ইসলাম",
                        address = "চাষাড়া, নারায়ণগঞ্জ"
                    )
                ),
                parcels = listOf(
                    LandParcel(
                        id = "demo-parcel-2",
                        surveyType = SurveyType.RS,
                        khatianNumber = "304",
                        dagNumber = "912",
                        landClass = LandClass.BASTU,
                        landAreaDecimal = "4.5",
                        landUnit = LandUnit.KATHA,
                        boundaryDescription = "পূর্বে মসজিদ রোড, পশ্চিমে পুকুর"
                    )
                ),
                status = DeedStatus.APPROVED,
                visibility = DeedVisibility.PUBLIC,
                syncState = SyncState.LOCAL_ONLY_DRAFT,
                notes = "Demo Data • বসতবাড়ি হেবা দলিলের নমুনা",
                createdAtMillis = now - 140 * dayMs,
                updatedAtMillis = now - 135 * dayMs
            ),
            DeedRecord(
                id = "demo-deed-3",
                ownerUid = GUEST_USER_ID,
                createdBy = GUEST_USER_ID,
                updatedBy = GUEST_USER_ID,
                officeId = "office_siddhirganj",
                officeName = "সাব-রেজিস্ট্রি অফিস, সিদ্ধিরগঞ্জ",
                deedType = DeedType.BANTONNAMA,
                deedNumber = "410/2022",
                normalizedDeedNumber = SearchNormalizer.normalizeCode("410/2022"),
                deedYear = 2022,
                registrationDateMillis = now - 365 * dayMs,
                districtName = "নারায়ণগঞ্জ",
                upazilaName = "সিদ্ধিরগঞ্জ রাজস্ব সার্কেল",
                unionName = "সিদ্ধিরগঞ্জ",
                mouzaName = "শিমরাইল",
                jlNumber = "1",
                grantors = listOf(
                    PartyInfo(
                        id = "demo-p5",
                        fullName = "মাহমুদ হাসান",
                        fatherName = "আজিজুল হক",
                        address = "শিমরাইল, সিদ্ধিরগঞ্জ"
                    ),
                    PartyInfo(
                        id = "demo-p6",
                        fullName = "ফাতেমা খাতুন",
                        fatherName = "আজিজুল হক",
                        address = "শিমরাইল, সিদ্ধিরগঞ্জ"
                    )
                ),
                grantees = listOf(
                    PartyInfo(
                        id = "demo-p7",
                        fullName = "কামরুল ইসলাম",
                        fatherName = "শামসুল আলম",
                        address = "গোদনাইল, সিদ্ধিরগঞ্জ"
                    )
                ),
                parcels = listOf(
                    LandParcel(
                        id = "demo-parcel-3",
                        surveyType = SurveyType.SA,
                        khatianNumber = "118",
                        dagNumber = "504",
                        landClass = LandClass.COMMERCIAL,
                        landAreaDecimal = "12.0",
                        landUnit = LandUnit.DECIMAL
                    ),
                    LandParcel(
                        id = "demo-parcel-4",
                        surveyType = SurveyType.SA,
                        khatianNumber = "118",
                        dagNumber = "505",
                        landClass = LandClass.VITI,
                        landAreaDecimal = "6.0",
                        landUnit = LandUnit.DECIMAL
                    )
                ),
                status = DeedStatus.APPROVED,
                visibility = DeedVisibility.PUBLIC,
                syncState = SyncState.LOCAL_ONLY_DRAFT,
                notes = "Demo Data • একাধিক দাতা ও দাগের নমুনা",
                createdAtMillis = now - 365 * dayMs,
                updatedAtMillis = now - 360 * dayMs
            ),
            DeedRecord(
                id = "demo-deed-4",
                ownerUid = GUEST_USER_ID,
                createdBy = GUEST_USER_ID,
                updatedBy = GUEST_USER_ID,
                officeId = "office_narayanganj_sadar",
                officeName = "সাব-রেজিস্ট্রি অফিস, নারায়ণগঞ্জ সদর",
                deedType = DeedType.OTHER,
                deedNumber = "305/2026",
                normalizedDeedNumber = SearchNormalizer.normalizeCode("305/2026"),
                deedYear = 2026,
                registrationDateMillis = now - 2 * dayMs,
                districtName = "নারায়ণগঞ্জ",
                upazilaName = "সিদ্ধিরগঞ্জ রাজস্ব সার্কেল",
                unionName = "সিদ্ধিরগঞ্জ",
                mouzaName = "গোদনাইল",
                jlNumber = "18",
                grantors = listOf(
                    PartyInfo(
                        id = "demo-p8",
                        fullName = "জাহাঙ্গীর আলম",
                        fatherName = "আলী আকবর",
                        address = "গোদনাইল, নারায়ণগঞ্জ"
                    )
                ),
                grantees = listOf(
                    PartyInfo(
                        id = "demo-p9",
                        fullName = "তানভীর আহমেদ",
                        fatherName = "মনির হোসেন",
                        address = "খানপুর, নারায়ণগঞ্জ"
                    )
                ),
                parcels = listOf(
                    LandParcel(
                        id = "demo-parcel-5",
                        surveyType = SurveyType.SA,
                        khatianNumber = "225",
                        dagNumber = "778",
                        landClass = LandClass.BASTU,
                        landAreaDecimal = "3.0",
                        landUnit = LandUnit.KATHA
                    )
                ),
                status = DeedStatus.DRAFT,
                visibility = DeedVisibility.PRIVATE_DRAFT,
                syncState = SyncState.LOCAL_ONLY_DRAFT,
                notes = "Demo Data • খসড়া দলিলের নমুনা (চাইলে সম্পাদনা করে দেখতে পারেন)",
                createdAtMillis = now - 2 * dayMs,
                updatedAtMillis = now - 1 * dayMs
            )
        )

        demoDeeds.forEach { deed ->
            val audit = AuditLogEntry(
                id = UUID.randomUUID().toString(),
                actorId = GUEST_USER_ID,
                actorName = "Demo Sandbox",
                organizationId = deed.organizationId,
                officeId = deed.officeId,
                action = AuditAction.CREATE,
                entityType = "DEED",
                entityId = deed.id,
                previousVersion = 0L,
                newVersion = 1L,
                summary = "Seeded local Demo Data record",
                createdAtMillis = deed.createdAtMillis
            )
            dao.saveDeedWithAuditAndSync(
                deed = deed.toEntity(),
                audit = audit.toEntity(ownerUid = GUEST_USER_ID),
                pendingSync = null
            )
        }
    }
}
