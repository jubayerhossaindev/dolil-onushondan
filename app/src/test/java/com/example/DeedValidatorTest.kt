package com.example

import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.LandParcel
import com.example.domain.model.LandUnit
import com.example.domain.model.PartyInfo
import com.example.domain.validation.DeedValidator
import com.example.domain.validation.ValidationErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class DeedValidatorTest {

    private fun validDeed() = DeedRecord(
        id = "deed-1",
        officeId = "office_narayanganj_sadar",
        deedNumber = "1256/2024",
        deedYear = 2024,
        registrationDateMillis = 1_700_000_000_000L,
        districtName = "নারায়ণগঞ্জ",
        upazilaName = "ফতুল্লা রাজস্ব সার্কেল",
        mouzaName = "ফতুল্লা",
        jlNumber = "140",
        grantors = listOf(PartyInfo(fullName = "আব্দুল করিম")),
        grantees = listOf(PartyInfo(fullName = "সালমা বেগম")),
        parcels = listOf(
            LandParcel(
                khatianNumber = "712",
                dagNumber = "1845",
                landAreaDecimal = "10.50",
                landUnit = LandUnit.DECIMAL
            )
        )
    )

    @Test
    fun `valid submitted deed passes with zero errors`() {
        val result = DeedValidator.validate(validDeed(), isDraft = false, currentYear = 2026)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `submitted deed with missing fields reports all expected ValidationErrorCodes`() {
        val invalid = DeedRecord(
            id = "deed-bad",
            deedNumber = "",
            deedYear = 1850,
            registrationDateMillis = 0L,
            districtName = "",
            upazilaName = "",
            mouzaName = "",
            jlNumber = "",
            grantors = listOf(PartyInfo(fullName = "")),
            grantees = listOf(PartyInfo(fullName = "")),
            parcels = emptyList()
        )
        val result = DeedValidator.validate(invalid, isDraft = false, currentYear = 2026)
        assertFalse(result.isValid)
        assertTrue(ValidationErrorCode.REQUIRED_DEED_NUMBER in result.errors)
        assertTrue(ValidationErrorCode.INVALID_DEED_YEAR in result.errors)
        assertTrue(ValidationErrorCode.INVALID_REGISTRATION_DATE in result.errors)
        assertTrue(ValidationErrorCode.REQUIRED_GRANTOR_NAME in result.errors)
        assertTrue(ValidationErrorCode.REQUIRED_GRANTEE_NAME in result.errors)
        assertTrue(ValidationErrorCode.REQUIRED_DISTRICT in result.errors)
        assertTrue(ValidationErrorCode.REQUIRED_UPAZILA in result.errors)
        assertTrue(ValidationErrorCode.REQUIRED_MOUZA in result.errors)
        assertTrue(ValidationErrorCode.REQUIRED_JL_NUMBER in result.errors)
        assertTrue(ValidationErrorCode.REQUIRED_PARCEL in result.errors)
    }

    @Test
    fun `parcel missing khatian dag or non-positive area fails validation`() {
        val badParcel = validDeed().copy(
            parcels = listOf(
                LandParcel(khatianNumber = "", dagNumber = "", landAreaDecimal = "-5", landUnit = LandUnit.DECIMAL)
            )
        )
        val result = DeedValidator.validate(badParcel, isDraft = false, currentYear = 2026)
        assertFalse(result.isValid)
        assertTrue(ValidationErrorCode.REQUIRED_KHATIAN in result.errors)
        assertTrue(ValidationErrorCode.REQUIRED_DAG in result.errors)
        assertTrue(ValidationErrorCode.INVALID_LAND_AREA in result.errors)
    }

    @Test
    fun `draft allows incomplete location and parties but rejects negative area and missing deed number`() {
        val draftOk = DeedRecord(
            id = "draft-1",
            deedNumber = "৫৫",
            deedYear = 2026,
            registrationDateMillis = 1_700_000_000_000L,
            parcels = listOf(LandParcel(landAreaDecimal = ""))
        )
        assertTrue(DeedValidator.validate(draftOk, isDraft = true, currentYear = 2026).isValid)

        val draftBadArea = draftOk.copy(
            parcels = listOf(LandParcel(landAreaDecimal = "0"))
        )
        val res = DeedValidator.validate(draftBadArea, isDraft = true, currentYear = 2026)
        assertFalse(res.isValid)
        assertEquals(listOf(ValidationErrorCode.INVALID_LAND_AREA), res.errors)
    }

    @Test
    fun `duplicate detection matches Bengali vs ASCII deed number in same office and year while ignoring soft deleted`() {
        val existingActive = validDeed().copy(id = "existing-1", deedNumber = "1256/2024")
        val existingDeleted = validDeed().copy(id = "existing-del", deedNumber = "777/2024", status = DeedStatus.SOFT_DELETED)
        val otherOffice = validDeed().copy(id = "existing-other", officeId = "office_fatulla", deedNumber = "888/2024")

        val duplicateCandidate = validDeed().copy(id = "new-1", deedNumber = "১২৫৬ / ২০২৪")
        val resDup = DeedValidator.validate(
            deed = duplicateCandidate,
            existingRecords = listOf(existingActive, existingDeleted, otherOffice),
            isDraft = false,
            currentYear = 2026
        )
        assertFalse(resDup.isValid)
        assertTrue(ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE in resDup.errors)
        assertEquals("existing-1", resDup.duplicateCandidateId)

        val reusingDeletedNo = validDeed().copy(id = "new-2", deedNumber = "777/2024")
        assertTrue(
            DeedValidator.validate(
                reusingDeletedNo,
                existingRecords = listOf(existingDeleted),
                isDraft = false,
                currentYear = 2026
            ).isValid
        )
    }

    @Test
    fun `BigDecimal area conversions compute Katha Bigha Acre and Bengali digits accurately`() {
        val deed = validDeed().copy(
            parcels = listOf(
                LandParcel(landAreaDecimal = "২", landUnit = LandUnit.KATHA), // 2 * 1.65 = 3.30
                LandParcel(landAreaDecimal = "1", landUnit = LandUnit.BIGHA), // 33.00
                LandParcel(landAreaDecimal = "0.5", landUnit = LandUnit.ACRE) // 50.00
            )
        )
        assertEquals(BigDecimal("86.30"), deed.computeTotalShotokDecimal())
        assertEquals("86.3", deed.totalAreaDecimalText)
    }
}
