package com.example

import com.example.core.security.RolePermissions
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandParcel
import com.example.domain.model.PartyInfo
import com.example.domain.model.SortOption
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.domain.search.DeedSorter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeedSorterAndRoleTest {

    private val deedA = DeedRecord(
        id = "a-uuid",
        deedNumber = "100",
        deedYear = 2022,
        registrationDateMillis = 1000L,
        updatedAtMillis = 2000L,
        mouzaName = "আলীগঞ্জ",
        grantors = listOf(PartyInfo(fullName = "Babul")),
        grantees = listOf(PartyInfo(fullName = "Zahir")),
        parcels = listOf(LandParcel(landAreaDecimal = "5.0"))
    )

    private val deedB = DeedRecord(
        id = "b-uuid",
        deedNumber = "200",
        deedYear = 2024,
        registrationDateMillis = 3000L,
        updatedAtMillis = 1000L,
        mouzaName = "ফতুল্লা",
        grantors = listOf(PartyInfo(fullName = "Amina")),
        grantees = listOf(PartyInfo(fullName = "Karim")),
        parcels = listOf(LandParcel(landAreaDecimal = "25.0"))
    )

    private val deedC = DeedRecord(
        id = "c-uuid",
        deedNumber = "200",
        deedYear = 2024,
        registrationDateMillis = 3000L,
        updatedAtMillis = 5000L,
        mouzaName = "ফতুল্লা",
        grantors = listOf(PartyInfo(fullName = "Amina")),
        grantees = listOf(PartyInfo(fullName = "Karim")),
        parcels = listOf(LandParcel(landAreaDecimal = "25.0"))
    )

    @Test
    fun `all 10 SortOptions sort deterministically with UUID tie-breaker`() {
        val list = listOf(deedC, deedA, deedB)

        assertEquals(listOf("b-uuid", "c-uuid", "a-uuid"), DeedSorter.sort(list, SortOption.REGISTRATION_DATE_DESC).map { it.id })
        assertEquals(listOf("a-uuid", "b-uuid", "c-uuid"), DeedSorter.sort(list, SortOption.REGISTRATION_DATE_ASC).map { it.id })
        assertEquals(listOf("b-uuid", "c-uuid", "a-uuid"), DeedSorter.sort(list, SortOption.DEED_YEAR_DESC).map { it.id })
        assertEquals(listOf("a-uuid", "b-uuid", "c-uuid"), DeedSorter.sort(list, SortOption.DEED_YEAR_ASC).map { it.id })
        assertEquals(listOf("a-uuid", "b-uuid", "c-uuid"), DeedSorter.sort(list, SortOption.DEED_NUMBER_ASC).map { it.id })
        assertEquals(listOf("b-uuid", "c-uuid", "a-uuid"), DeedSorter.sort(list, SortOption.GRANTOR_NAME_ASC).map { it.id })
        assertEquals(listOf("b-uuid", "c-uuid", "a-uuid"), DeedSorter.sort(list, SortOption.GRANTEE_NAME_ASC).map { it.id })
        assertEquals(listOf("a-uuid", "b-uuid", "c-uuid"), DeedSorter.sort(list, SortOption.MOUZA_NAME_ASC).map { it.id })
        assertEquals(listOf("b-uuid", "c-uuid", "a-uuid"), DeedSorter.sort(list, SortOption.LAND_AREA_DESC).map { it.id })
        assertEquals(listOf("c-uuid", "a-uuid", "b-uuid"), DeedSorter.sort(list, SortOption.UPDATED_AT_DESC).map { it.id })
    }

    @Test
    fun `paginate clamps pageSize between 1 and 50`() {
        val items = (1..100).toList()
        assertEquals((1..20).toList(), DeedSorter.paginate(items, page = 1, pageSize = 20))
        assertEquals((1..50).toList(), DeedSorter.paginate(items, page = 1, pageSize = 999))
        assertEquals(listOf(1), DeedSorter.paginate(items, page = 0, pageSize = 0))
    }

    @Test
    fun `RBAC RolePermissions enforce strict visibility edit approve delete and restore matrix`() {
        val publicUser = UserProfile(userId = "u_pub", role = UserRole.PUBLIC_USER, officeId = "office_narayanganj_sadar")
        val viewer = UserProfile(userId = "u_view", role = UserRole.OFFICE_VIEWER, officeId = "office_narayanganj_sadar")
        val entry = UserProfile(userId = "u_entry", role = UserRole.OFFICE_DATA_ENTRY, officeId = "office_narayanganj_sadar")
        val supervisor = UserProfile(userId = "u_sup", role = UserRole.OFFICE_SUPERVISOR, officeId = "office_narayanganj_sadar")
        val orgAdmin = UserProfile(userId = "u_org", role = UserRole.ORGANIZATION_ADMIN, organizationId = "org_bd_land")
        val platformAdmin = UserProfile(userId = "u_plat", role = UserRole.PLATFORM_ADMIN)
        val suspendedUser = supervisor.copy(status = "SUSPENDED")

        val publicApproved = DeedRecord(id = "d_pub", createdBy = "u_entry", status = DeedStatus.APPROVED, visibility = DeedVisibility.PUBLIC, officeId = "office_narayanganj_sadar")
        val officePending = DeedRecord(id = "d_off", createdBy = "u_entry", status = DeedStatus.PENDING_REVIEW, visibility = DeedVisibility.OFFICE_ONLY, officeId = "office_narayanganj_sadar")
        val otherOfficePending = officePending.copy(id = "d_other", officeId = "office_fatulla")
        val pubDraft = DeedRecord(id = "d_draft", createdBy = "u_pub", status = DeedStatus.DRAFT, visibility = DeedVisibility.PRIVATE_DRAFT)
        val softDeleted = publicApproved.copy(id = "d_del", status = DeedStatus.SOFT_DELETED, visibility = DeedVisibility.OFFICE_ONLY)

        // Office Stats & Role management
        assertFalse(RolePermissions.canViewOfficeStats(UserRole.PUBLIC_USER))
        assertTrue(RolePermissions.canViewOfficeStats(UserRole.OFFICE_VIEWER))
        assertFalse(RolePermissions.canApproveDeed(UserRole.OFFICE_DATA_ENTRY))
        assertTrue(RolePermissions.canApproveDeed(UserRole.OFFICE_SUPERVISOR))
        assertFalse(RolePermissions.canManageRoles(UserRole.OFFICE_SUPERVISOR))
        assertTrue(RolePermissions.canManageRoles(UserRole.ORGANIZATION_ADMIN))
        assertTrue(RolePermissions.canManageRoles(UserRole.PLATFORM_ADMIN))

        // Viewing
        assertTrue(RolePermissions.canViewDeed(publicUser, publicApproved))
        assertTrue(RolePermissions.canViewDeed(publicUser, pubDraft))
        assertFalse(RolePermissions.canViewDeed(publicUser, officePending))
        assertTrue(RolePermissions.canViewDeed(viewer, officePending))
        assertFalse(RolePermissions.canViewDeed(viewer, otherOfficePending))
        assertTrue(RolePermissions.canViewDeed(orgAdmin, otherOfficePending))
        assertTrue(RolePermissions.canViewDeed(platformAdmin, otherOfficePending))
        assertFalse(RolePermissions.canViewDeed(suspendedUser, publicApproved))

        // Editing
        assertTrue(RolePermissions.canEditDeed(publicUser, pubDraft))
        assertFalse(RolePermissions.canEditDeed(publicUser, publicApproved))
        assertFalse(RolePermissions.canEditDeed(viewer, officePending))
        assertTrue(RolePermissions.canEditDeed(entry, officePending))
        assertFalse(RolePermissions.canEditDeed(entry, publicApproved))
        assertTrue(RolePermissions.canEditDeed(supervisor, publicApproved))
        assertFalse(RolePermissions.canEditDeed(platformAdmin, softDeleted))

        // Soft Delete & Restore
        assertTrue(RolePermissions.canSoftDeleteDeed(publicUser, pubDraft))
        assertFalse(RolePermissions.canSoftDeleteDeed(entry, officePending))
        assertTrue(RolePermissions.canSoftDeleteDeed(supervisor, officePending))
        assertFalse(RolePermissions.canSoftDeleteDeed(supervisor, otherOfficePending))
        assertTrue(RolePermissions.canRestoreDeed(supervisor, softDeleted))
        assertFalse(RolePermissions.canRestoreDeed(entry, softDeleted))
    }
}
