package com.example.core.security

import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedVisibility
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole

object RolePermissions {

    fun isOfficeUser(role: UserRole): Boolean = role != UserRole.PUBLIC_USER

    fun canViewOfficeStats(role: UserRole): Boolean = role != UserRole.PUBLIC_USER

    fun canSubmitOfficeDeed(role: UserRole): Boolean = role in setOf(
        UserRole.OFFICE_DATA_ENTRY,
        UserRole.OFFICE_SUPERVISOR,
        UserRole.ORGANIZATION_ADMIN,
        UserRole.PLATFORM_ADMIN
    )

    fun canApproveDeed(role: UserRole): Boolean = role in setOf(
        UserRole.OFFICE_SUPERVISOR,
        UserRole.ORGANIZATION_ADMIN,
        UserRole.PLATFORM_ADMIN
    )

    fun canManageRoles(role: UserRole): Boolean = role in setOf(
        UserRole.ORGANIZATION_ADMIN,
        UserRole.PLATFORM_ADMIN
    )

    fun canViewDeed(user: UserProfile, deed: DeedRecord): Boolean {
        if (user.status != "ACTIVE") return false
        if (deed.visibility == DeedVisibility.PUBLIC && deed.status == DeedStatus.APPROVED) {
            return true
        }
        if (deed.createdBy == user.userId) return true
        return when (user.role) {
            UserRole.PUBLIC_USER -> false
            UserRole.OFFICE_VIEWER,
            UserRole.OFFICE_DATA_ENTRY,
            UserRole.OFFICE_SUPERVISOR -> deed.officeId == user.officeId
            UserRole.ORGANIZATION_ADMIN -> deed.organizationId == user.organizationId
            UserRole.PLATFORM_ADMIN -> true
        }
    }

    fun canEditDeed(user: UserProfile, deed: DeedRecord): Boolean {
        if (user.status != "ACTIVE") return false
        if (deed.status == DeedStatus.SOFT_DELETED) return false
        if (deed.ownerUid.isNotBlank() && deed.ownerUid == user.userId) return true
        if (deed.createdBy == user.userId && deed.status == DeedStatus.DRAFT) return true
        return when (user.role) {
            UserRole.PUBLIC_USER,
            UserRole.OFFICE_VIEWER -> false
            UserRole.OFFICE_DATA_ENTRY ->
                deed.officeId == user.officeId && deed.status != DeedStatus.APPROVED
            UserRole.OFFICE_SUPERVISOR -> deed.officeId == user.officeId
            UserRole.ORGANIZATION_ADMIN -> deed.organizationId == user.organizationId
            UserRole.PLATFORM_ADMIN -> true
        }
    }

    fun canSoftDeleteDeed(user: UserProfile, deed: DeedRecord): Boolean {
        if (user.status != "ACTIVE" || deed.status == DeedStatus.SOFT_DELETED) return false
        if (deed.ownerUid.isNotBlank() && deed.ownerUid == user.userId) return true
        if (deed.createdBy == user.userId && deed.status == DeedStatus.DRAFT) return true
        return when (user.role) {
            UserRole.OFFICE_SUPERVISOR -> deed.officeId == user.officeId
            UserRole.ORGANIZATION_ADMIN -> deed.organizationId == user.organizationId
            UserRole.PLATFORM_ADMIN -> true
            else -> false
        }
    }

    fun canRestoreDeed(user: UserProfile, deed: DeedRecord): Boolean {
        if (user.status != "ACTIVE" || deed.status != DeedStatus.SOFT_DELETED) return false
        if (deed.ownerUid.isNotBlank() && deed.ownerUid == user.userId) return true
        if (deed.createdBy == user.userId && deed.visibility == DeedVisibility.PRIVATE_DRAFT) return true
        return when (user.role) {
            UserRole.OFFICE_SUPERVISOR -> deed.officeId == user.officeId
            UserRole.ORGANIZATION_ADMIN -> deed.organizationId == user.organizationId
            UserRole.PLATFORM_ADMIN -> true
            else -> false
        }
    }
}
