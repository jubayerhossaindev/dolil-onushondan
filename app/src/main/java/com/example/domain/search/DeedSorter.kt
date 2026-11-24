package com.example.domain.search

import com.example.domain.model.DeedRecord
import com.example.domain.model.SortOption

object DeedSorter {

    fun sort(records: List<DeedRecord>, option: SortOption): List<DeedRecord> {
        val comparator: Comparator<DeedRecord> = when (option) {
            SortOption.REGISTRATION_DATE_DESC ->
                compareByDescending<DeedRecord> { it.registrationDateMillis }.thenBy { it.id }
            SortOption.REGISTRATION_DATE_ASC ->
                compareBy<DeedRecord> { it.registrationDateMillis }.thenBy { it.id }
            SortOption.DEED_YEAR_DESC ->
                compareByDescending<DeedRecord> { it.deedYear }
                    .thenByDescending { it.registrationDateMillis }
                    .thenBy { it.id }
            SortOption.DEED_YEAR_ASC ->
                compareBy<DeedRecord> { it.deedYear }
                    .thenBy { it.registrationDateMillis }
                    .thenBy { it.id }
            SortOption.DEED_NUMBER_ASC ->
                compareBy<DeedRecord> { SearchNormalizer.normalizeCode(it.deedNumber) }
                    .thenBy { it.deedYear }
                    .thenBy { it.id }
            SortOption.GRANTOR_NAME_ASC ->
                compareBy<DeedRecord> { SearchNormalizer.normalizePersonName(it.primaryGrantorName) }
                    .thenBy { it.id }
            SortOption.GRANTEE_NAME_ASC ->
                compareBy<DeedRecord> { SearchNormalizer.normalizePersonName(it.primaryGranteeName) }
                    .thenBy { it.id }
            SortOption.MOUZA_NAME_ASC ->
                compareBy<DeedRecord> { SearchNormalizer.normalizeText(it.mouzaName) }
                    .thenBy { it.id }
            SortOption.LAND_AREA_DESC ->
                compareByDescending<DeedRecord> { it.computeTotalShotokDecimal() }
                    .thenBy { it.id }
            SortOption.UPDATED_AT_DESC ->
                compareByDescending<DeedRecord> { it.updatedAtMillis }.thenBy { it.id }
        }
        return records.sortedWith(comparator)
    }

    fun <T> paginate(items: List<T>, page: Int, pageSize: Int = 20): List<T> {
        val boundedSize = pageSize.coerceIn(1, 50)
        val safePage = page.coerceAtLeast(1)
        val endIndex = minOf(safePage * boundedSize, items.size)
        return items.subList(0, endIndex)
    }
}
