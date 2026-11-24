package com.example.domain.validation

import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.search.SearchNormalizer
import java.math.BigDecimal
import java.util.Calendar

enum class ValidationErrorCode {
    REQUIRED_DEED_NUMBER,
    INVALID_DEED_YEAR,
    INVALID_REGISTRATION_DATE,
    REQUIRED_GRANTOR_NAME,
    REQUIRED_GRANTEE_NAME,
    REQUIRED_DISTRICT,
    REQUIRED_UPAZILA,
    REQUIRED_MOUZA,
    REQUIRED_JL_NUMBER,
    REQUIRED_PARCEL,
    REQUIRED_KHATIAN,
    REQUIRED_DAG,
    INVALID_LAND_AREA,
    DUPLICATE_DEED_IN_OFFICE
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationErrorCode> = emptyList(),
    val duplicateCandidateId: String? = null
)

object DeedValidator {

    fun validate(
        deed: DeedRecord,
        existingRecords: List<DeedRecord> = emptyList(),
        isDraft: Boolean = deed.status == DeedStatus.DRAFT,
        currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): ValidationResult {
        val errors = mutableListOf<ValidationErrorCode>()

        val normalizedNo = SearchNormalizer.normalizeCode(deed.deedNumber)
        if (normalizedNo.isBlank()) {
            errors.add(ValidationErrorCode.REQUIRED_DEED_NUMBER)
        }

        if (deed.deedYear < 1900 || deed.deedYear > currentYear + 1) {
            errors.add(ValidationErrorCode.INVALID_DEED_YEAR)
        }

        if (deed.registrationDateMillis <= 0L) {
            errors.add(ValidationErrorCode.INVALID_REGISTRATION_DATE)
        }

        // Strict checks when submitting / saving non-draft record
        if (!isDraft) {
            if (deed.grantors.none { it.fullName.isNotBlank() }) {
                errors.add(ValidationErrorCode.REQUIRED_GRANTOR_NAME)
            }
            if (deed.grantees.none { it.fullName.isNotBlank() }) {
                errors.add(ValidationErrorCode.REQUIRED_GRANTEE_NAME)
            }
            if (deed.districtName.isBlank()) errors.add(ValidationErrorCode.REQUIRED_DISTRICT)
            if (deed.upazilaName.isBlank()) errors.add(ValidationErrorCode.REQUIRED_UPAZILA)
            if (deed.mouzaName.isBlank()) errors.add(ValidationErrorCode.REQUIRED_MOUZA)
            if (deed.jlNumber.isBlank()) errors.add(ValidationErrorCode.REQUIRED_JL_NUMBER)

            if (deed.parcels.isEmpty()) {
                errors.add(ValidationErrorCode.REQUIRED_PARCEL)
            } else {
                val hasMissingKhatian = deed.parcels.any { it.khatianNumber.isBlank() }
                val hasMissingDag = deed.parcels.any { it.dagNumber.isBlank() }
                val hasInvalidArea = deed.parcels.any { parcel ->
                    val normalizedArea = SearchNormalizer.normalizeCode(parcel.landAreaDecimal)
                        .replace(',', '.')
                    val bd = normalizedArea.toBigDecimalOrNull()
                    bd == null || bd <= BigDecimal.ZERO
                }
                if (hasMissingKhatian) errors.add(ValidationErrorCode.REQUIRED_KHATIAN)
                if (hasMissingDag) errors.add(ValidationErrorCode.REQUIRED_DAG)
                if (hasInvalidArea) errors.add(ValidationErrorCode.INVALID_LAND_AREA)
            }
        } else {
            // Even for drafts, reject negative/malformed non-empty area
            val hasNegativeArea = deed.parcels.any { parcel ->
                parcel.landAreaDecimal.isNotBlank() && run {
                    val bd = SearchNormalizer.normalizeCode(parcel.landAreaDecimal)
                        .replace(',', '.')
                        .toBigDecimalOrNull()
                    bd == null || bd <= BigDecimal.ZERO
                }
            }
            if (hasNegativeArea) errors.add(ValidationErrorCode.INVALID_LAND_AREA)
        }

        // Duplicate detection policy: same office + same deedYear + same normalizedDeedNumber (excluding self & soft-deleted)
        val duplicate = if (normalizedNo.isNotBlank()) {
            existingRecords.firstOrNull { existing ->
                existing.id != deed.id &&
                    existing.status != DeedStatus.SOFT_DELETED &&
                    existing.officeId == deed.officeId &&
                    existing.deedYear == deed.deedYear &&
                    SearchNormalizer.normalizeCode(existing.deedNumber) == normalizedNo
            }
        } else null

        if (duplicate != null) {
            errors.add(ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE)
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors.distinct(),
            duplicateCandidateId = duplicate?.id
        )
    }
}
