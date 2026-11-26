package com.example.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.CredentialReadinessChecker
import com.example.core.config.CredentialReadinessReport
import com.example.core.designsystem.DolilStrings
import com.example.core.security.AuthManager
import com.example.core.security.RolePermissions
import com.example.data.drive.DriveAccountInfo
import com.example.data.drive.DriveAuthGateway
import com.example.data.drive.DriveAuthResult
import com.example.data.drive.DriveBackupEngine
import com.example.data.drive.DriveBackupSummary
import com.example.data.drive.DriveDeviceRecord
import com.example.data.drive.DriveRemoteStorage
import com.example.data.drive.DriveSyncOutcome
import com.example.data.drive.HttpDriveApiV3Client
import com.example.data.drive.PlayServicesDriveAuthGateway
import com.example.data.local.ConflictAuditRecord
import com.example.data.local.DeviceIdentityStore
import com.example.data.local.DolilDao
import com.example.data.local.DriveLinkState
import com.example.data.local.DriveSyncStatus
import com.example.data.local.GuestSandboxSeeder
import com.example.data.local.LEGACY_LOCAL_OWNER_UID
import com.example.data.local.LocalAccountProfile
import com.example.data.local.LocalUserPreferences
import com.example.data.local.NamespaceHelper
import com.example.data.local.SessionPassphraseVault
import com.example.data.local.UserPreferencesDataStore
import com.example.data.repository.DolilSyncRepository
import com.example.data.repository.SaveDeedOutcome
import com.example.data.sync.DriveSyncScheduler
import com.example.domain.model.AppLanguage
import com.example.domain.model.AuditAction
import com.example.domain.model.DeedConflict
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedVisibility
import com.example.domain.model.OfficeStats
import com.example.domain.model.RankedDeedResult
import com.example.domain.model.SortOption
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.domain.search.DeedSorter
import com.example.domain.search.DefaultIndexedSearchStrategy
import com.example.domain.validation.DeedValidator
import com.example.domain.validation.ValidationErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class SearchFilterState(
    val query: String = "",
    val deedType: DeedTypeFilter? = null,
    val year: Int? = null,
    val district: String = "",
    val mouza: String = "",
    val status: DeedStatus? = null,
    val sortOption: SortOption = SortOption.REGISTRATION_DATE_DESC,
    val page: Int = 1,
    val pageSize: Int = 20
)

typealias DeedTypeFilter = com.example.domain.model.DeedType

enum class AccountDriveCleanupChoice {
    KEEP_DRIVE_BACKUPS,
    DELETE_DRIVE_BACKUPS
}

private enum class PendingDriveOperation {
    CONNECT,
    SWITCH_ACCOUNT,
    SYNC_NOW,
    FORCE_UPLOAD_SNAPSHOT,
    LIST_BACKUPS,
    LIST_DEVICES,
    RESTORE_BACKUP,
    RETIRE_DEVICE
}

class DolilViewModel(
    private val syncRepo: DolilSyncRepository,
    private val prefsStore: UserPreferencesDataStore,
    val currentUserId: String,
    private val authEmail: String,
    private val authDisplayName: String,
    val isGuestMode: Boolean = false,
    initialPreferences: LocalUserPreferences = LocalUserPreferences(),
    isEmailVerifiedInitial: Boolean = false,
    private val authProviderIds: List<String> = emptyList(),
    private val appContext: Context? = null,
    private val dao: DolilDao? = null,
    private val guestDao: DolilDao? = null,
    private val identityStore: DeviceIdentityStore? = appContext?.let { DeviceIdentityStore(it) },
    private val driveAuthGateway: DriveAuthGateway = PlayServicesDriveAuthGateway(),
    private val driveStorage: DriveRemoteStorage = HttpDriveApiV3Client()
) : ViewModel() {

    private val searchStrategy = DefaultIndexedSearchStrategy()

    val preferences: StateFlow<LocalUserPreferences> = prefsStore.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = initialPreferences
        )

    private val _isEmailVerified = MutableStateFlow(isEmailVerifiedInitial)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    private val _localProfileOverride = MutableStateFlow(
        if (isGuestMode) null else identityStore?.getLocalProfile(currentUserId)
    )

    val userProfile: StateFlow<UserProfile> = combine(
        _localProfileOverride,
        _isEmailVerified
    ) { localProf, emailVerified ->
        if (isGuestMode) {
            UserProfile(
                userId = GuestSandboxSeeder.GUEST_USER_ID,
                fullName = "Guest Explorer",
                email = "demo@sandbox.local",
                role = UserRole.PUBLIC_USER,
                officeId = "demo_sandbox",
                officeName = "Demo Sandbox"
            )
        } else {
            val resolvedName = localProf?.fullName?.takeIf { it.isNotBlank() }
                ?: authDisplayName.ifBlank { authEmail.substringBefore('@').ifBlank { "User" } }
            UserProfile(
                userId = currentUserId,
                fullName = resolvedName,
                email = authEmail,
                phone = localProf?.phone.orEmpty(),
                role = UserRole.PUBLIC_USER,
                isEmailVerified = emailVerified,
                authProviderIds = authProviderIds
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = if (isGuestMode) {
            UserProfile(
                userId = GuestSandboxSeeder.GUEST_USER_ID,
                fullName = "Guest Explorer",
                email = "demo@sandbox.local",
                role = UserRole.PUBLIC_USER,
                officeId = "demo_sandbox",
                officeName = "Demo Sandbox"
            )
        } else {
            UserProfile(
                userId = currentUserId,
                fullName = authDisplayName.ifBlank { authEmail.substringBefore('@').ifBlank { "User" } },
                email = authEmail,
                role = UserRole.PUBLIC_USER,
                isEmailVerified = isEmailVerifiedInitial,
                authProviderIds = authProviderIds
            )
        }
    )

    val driveLinkState: StateFlow<DriveLinkState> = if (isGuestMode || identityStore == null) {
        MutableStateFlow(DriveLinkState()).asStateFlow()
    } else {
        identityStore.observeDriveLink(currentUserId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = identityStore.getDriveLink(currentUserId)
            )
    }

    val allDeedsState: StateFlow<UiState<List<DeedRecord>>> = combine(
        syncRepo.observeLocalDeeds(),
        userProfile
    ) { localList, profile ->
        localList.filter { RolePermissions.canViewDeed(profile, it) }
    }
        .map<List<DeedRecord>, UiState<List<DeedRecord>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Error(error.message ?: "Failed to load records"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val pendingSyncCount: StateFlow<Int> = syncRepo.observePendingSyncCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0
        )

    val legacyDeedCount: StateFlow<Int> = if (isGuestMode) {
        MutableStateFlow(0).asStateFlow()
    } else {
        syncRepo.observeLegacyLocalDeedCount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = 0
            )
    }

    private val _customGuestDeedCount = MutableStateFlow(0)
    val customGuestDeedCount: StateFlow<Int> = _customGuestDeedCount.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilterState())
    val searchFilter: StateFlow<SearchFilterState> = _searchFilter.asStateFlow()

    val searchResults: StateFlow<List<RankedDeedResult>> = combine(
        allDeedsState,
        _searchFilter
    ) { state, filter ->
        val base = (state as? UiState.Success)?.data.orEmpty()
            .filter { deed ->
                val matchesStatus = if (filter.status != null) {
                    deed.status == filter.status
                } else {
                    deed.status != DeedStatus.SOFT_DELETED
                }
                val matchesType = filter.deedType == null || deed.deedType == filter.deedType
                val matchesYear = filter.year == null || deed.deedYear == filter.year
                val matchesDistrict = filter.district.isBlank() ||
                    com.example.data.local.BangladeshLocationsCatalog.normalizeLocationKey(deed.districtName) ==
                    com.example.data.local.BangladeshLocationsCatalog.normalizeLocationKey(filter.district)
                val matchesMouza = filter.mouza.isBlank() ||
                    com.example.data.local.BangladeshLocationsCatalog.normalizeLocationKey(deed.mouzaName)
                        .contains(com.example.data.local.BangladeshLocationsCatalog.normalizeLocationKey(filter.mouza))

                matchesStatus && matchesType && matchesYear && matchesDistrict && matchesMouza
            }

        if (filter.query.isNotBlank()) {
            val ranked = searchStrategy.rank(base, filter.query)
            DeedSorter.paginate(ranked, filter.page, filter.pageSize)
        } else {
            val sorted = DeedSorter.sort(base, filter.sortOption)
                .map { RankedDeedResult(it, 0, com.example.domain.model.MatchReason.SUBSTRING_MATCH) }
            DeedSorter.paginate(sorted, filter.page, filter.pageSize)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val officeStats: StateFlow<OfficeStats> = combine(allDeedsState, userProfile) { state, profile ->
        val list = (state as? UiState.Success)?.data.orEmpty()
            .filter { it.status != DeedStatus.SOFT_DELETED }
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (!RolePermissions.canViewOfficeStats(profile.role)) {
            OfficeStats()
        } else {
            OfficeStats(
                totalDeeds = list.size,
                todayEntries = list.count { it.createdAtMillis >= startOfDay },
                draftCount = list.count { it.status == DeedStatus.DRAFT },
                pendingReviewCount = list.count { it.status == DeedStatus.PENDING_REVIEW }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = OfficeStats()
    )

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _validationErrors = MutableStateFlow<List<ValidationErrorCode>>(emptyList())
    val validationErrors: StateFlow<List<ValidationErrorCode>> = _validationErrors.asStateFlow()

    private val _duplicateCandidateId = MutableStateFlow<String?>(null)
    val duplicateCandidateId: StateFlow<String?> = _duplicateCandidateId.asStateFlow()

    private val _activeConflict = MutableStateFlow<DeedConflict?>(null)
    val activeConflict: StateFlow<DeedConflict?> = _activeConflict.asStateFlow()

    private val _lastArchivedDeed = MutableStateFlow<DeedRecord?>(null)
    val lastArchivedDeed: StateFlow<DeedRecord?> = _lastArchivedDeed.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _savingAsDraft = MutableStateFlow<Boolean?>(null)
    val savingAsDraft: StateFlow<Boolean?> = _savingAsDraft.asStateFlow()

    private val _pendingDriveConsentIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingDriveConsentIntent: StateFlow<PendingIntent?> = _pendingDriveConsentIntent.asStateFlow()

    private val _isDriveBusy = MutableStateFlow(false)
    val isDriveBusy: StateFlow<Boolean> = _isDriveBusy.asStateFlow()

    private val _availableBackups = MutableStateFlow<List<DriveBackupSummary>>(emptyList())
    val availableBackups: StateFlow<List<DriveBackupSummary>> = _availableBackups.asStateFlow()

    private val _isBackupsLoading = MutableStateFlow(false)
    val isBackupsLoading: StateFlow<Boolean> = _isBackupsLoading.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<DriveDeviceRecord>>(emptyList())
    val connectedDevices: StateFlow<List<DriveDeviceRecord>> = _connectedDevices.asStateFlow()

    private val _isDevicesLoading = MutableStateFlow(false)
    val isDevicesLoading: StateFlow<Boolean> = _isDevicesLoading.asStateFlow()

    private val _passphrasePromptBackupId = MutableStateFlow<String?>(null)
    val passphrasePromptBackupId: StateFlow<String?> = _passphrasePromptBackupId.asStateFlow()

    private val _driveAccountMismatch = MutableStateFlow<DriveSyncOutcome.AccountMismatch?>(null)
    val driveAccountMismatch: StateFlow<DriveSyncOutcome.AccountMismatch?> = _driveAccountMismatch.asStateFlow()

    private val _readinessReport = MutableStateFlow<CredentialReadinessReport?>(null)
    val readinessReport: StateFlow<CredentialReadinessReport?> = _readinessReport.asStateFlow()

    private var pendingDriveOperation: PendingDriveOperation? = null
    private var pendingRestoreSummary: DriveBackupSummary? = null
    private var pendingRestorePassphrase: CharArray? = null
    private var pendingRetireDeviceId: String? = null
    private var lastVerifiedDriveToken: String? = null

    init {
        if (isGuestMode) {
            viewModelScope.launch {
                syncRepo.seedGuestSandboxIfEmpty()
            }
        } else {
            refreshGuestCustomCount()
            refreshCredentialReadiness()
            val link = identityStore?.getDriveLink(currentUserId)
            if (link?.isConnected == true && link.autoSyncEnabled && appContext != null) {
                DriveSyncScheduler.schedulePeriodicSync(appContext, currentUserId)
            }
        }
    }

    fun refreshGuestCustomCount() {
        val gDao = guestDao ?: return
        viewModelScope.launch {
            _customGuestDeedCount.value = runCatching {
                GuestSandboxSeeder.getUserCreatedGuestDeeds(gDao).size
            }.getOrDefault(0)
        }
    }

    fun refreshCredentialReadiness() {
        val ctx = appContext ?: return
        viewModelScope.launch {
            _readinessReport.value = CredentialReadinessChecker.inspect(
                context = ctx,
                firebaseUid = currentUserId.takeIf { !isGuestMode && it.isNotBlank() },
                hasVerifiedDriveTokenThisSession = !lastVerifiedDriveToken.isNullOrBlank()
            )
        }
    }

    fun observeAuditLogs(deedId: String) = syncRepo.observeAuditLogs(deedId)

    fun observeAdminUsers(): Flow<List<UserProfile>> = flowOf(emptyList())

    fun updateSearchQuery(query: String) {
        _searchFilter.value = _searchFilter.value.copy(query = query, page = 1)
    }

    fun updateFilters(
        deedType: com.example.domain.model.DeedType? = _searchFilter.value.deedType,
        year: Int? = _searchFilter.value.year,
        district: String = _searchFilter.value.district,
        mouza: String = _searchFilter.value.mouza,
        status: DeedStatus? = _searchFilter.value.status,
        sortOption: SortOption = _searchFilter.value.sortOption
    ) {
        _searchFilter.value = _searchFilter.value.copy(
            deedType = deedType,
            year = year,
            district = district,
            mouza = mouza,
            status = status,
            sortOption = sortOption,
            page = 1
        )
    }

    fun clearFilters() {
        _searchFilter.value = SearchFilterState(query = _searchFilter.value.query)
    }

    fun loadNextPage() {
        _searchFilter.value = _searchFilter.value.copy(page = _searchFilter.value.page + 1)
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            prefsStore.setLanguage(language)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefsStore.setThemeMode(mode)
        }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.setUseDynamicColor(enabled)
        }
    }

    fun completeProductTour() {
        viewModelScope.launch {
            if (isGuestMode) {
                prefsStore.setHasCompletedGuestTour(true)
            } else {
                prefsStore.setHasCompletedUserTour(true)
            }
        }
    }

    fun replayProductTour() {
        viewModelScope.launch {
            prefsStore.resetProductTour(isGuestMode)
        }
    }

    fun updateProfileNamePhone(fullName: String, phone: String) {
        viewModelScope.launch {
            if (isGuestMode) {
                _statusMessage.value = DolilStrings.guestSyncNotice(preferences.value.language)
                return@launch
            }
            val cleanName = fullName.trim()
            val cleanPhone = phone.trim()
            val local = LocalAccountProfile(
                firebaseUid = currentUserId,
                fullName = cleanName,
                phone = cleanPhone
            )
            identityStore?.saveLocalProfile(local)
            _localProfileOverride.value = local
            if (cleanName.isNotBlank() && appContext != null) {
                runCatching { AuthManager.updateFirebaseDisplayName(appContext, cleanName) }
            }
            _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                "প্রোফাইলের তথ্য স্থানীয়ভাবে সংরক্ষিত হয়েছে।"
            } else {
                "Your profile details have been saved locally."
            }
        }
    }

    fun adminAssignRole(targetUid: String, role: UserRole, officeId: String, officeName: String) {
        _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
            "সার্ভার-ভিত্তিক ভূমিকা পরিবর্তন নিষ্ক্রিয় করা হয়েছে; আপনার ব্যক্তিগত তথ্য Google Drive-এ সংরক্ষিত হয়।"
        } else {
            "Multi-user directory is disabled; records stay private on this device and sync to your Google Drive."
        }
    }

    fun resendVerificationEmail() {
        val ctx = appContext ?: return
        if (isGuestMode) return
        viewModelScope.launch {
            AuthManager.resendEmailVerification(ctx, preferences.value.language)
                .onSuccess {
                    _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                        "যাচাইকরণ ইমেইল পাঠানো হয়েছে। ইনবক্স পরীক্ষা করুন।"
                    } else {
                        "Verification email sent. Please check your inbox."
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to send verification email"
                }
        }
    }

    fun refreshEmailVerificationStatus() {
        val ctx = appContext ?: return
        if (isGuestMode) return
        viewModelScope.launch {
            AuthManager.reloadCurrentUser(ctx)
                .onSuccess { verified ->
                    _isEmailVerified.value = verified
                    _statusMessage.value = if (verified) {
                        if (preferences.value.language == AppLanguage.BN) "ইমেইল সফলভাবে যাচাই হয়েছে!" else "Email verified!"
                    } else {
                        if (preferences.value.language == AppLanguage.BN) "ইমেইল এখনো যাচাই হয়নি।" else "Email is not verified yet."
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to refresh verification status"
                }
        }
    }

    fun saveDeed(
        draftOrDeed: DeedRecord,
        saveAsDraft: Boolean,
        ignoreDuplicateWarning: Boolean = false,
        onSavedSuccess: (DeedRecord) -> Unit = {}
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _validationErrors.value = emptyList()
            _duplicateCandidateId.value = null
            val profile = userProfile.value

            val targetStatus = when {
                saveAsDraft -> DeedStatus.DRAFT
                profile.role == UserRole.OFFICE_DATA_ENTRY -> DeedStatus.PENDING_REVIEW
                else -> DeedStatus.APPROVED
            }
            val targetVisibility = when {
                targetStatus == DeedStatus.DRAFT -> DeedVisibility.PRIVATE_DRAFT
                targetStatus == DeedStatus.APPROVED -> DeedVisibility.PUBLIC
                else -> DeedVisibility.OFFICE_ONLY
            }

            val existingList = (allDeedsState.value as? UiState.Success)?.data.orEmpty()
            val candidate = draftOrDeed.copy(
                ownerUid = currentUserId,
                status = targetStatus,
                visibility = targetVisibility,
                officeId = draftOrDeed.officeId.ifBlank { profile.officeId },
                officeName = draftOrDeed.officeName.ifBlank { profile.officeName },
                organizationId = draftOrDeed.organizationId.ifBlank { profile.organizationId }
            )

            val validation = DeedValidator.validate(
                deed = candidate,
                existingRecords = existingList,
                isDraft = saveAsDraft
            )

            val blockingErrors = if (ignoreDuplicateWarning) {
                validation.errors.filterNot { it == ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE }
            } else {
                validation.errors
            }

            if (blockingErrors.isNotEmpty()) {
                _validationErrors.value = blockingErrors
                if (ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE in blockingErrors) {
                    _duplicateCandidateId.value = validation.duplicateCandidateId
                }
                return@launch
            }

            _isSaving.value = true
            _savingAsDraft.value = saveAsDraft
            val action = when {
                saveAsDraft -> AuditAction.CREATE
                targetStatus == DeedStatus.APPROVED -> AuditAction.APPROVE
                draftOrDeed.rowVersion > 1L -> AuditAction.UPDATE
                else -> AuditAction.SUBMIT_DRAFT
            }

            val isUpdate = draftOrDeed.rowVersion > 1L ||
                existingList.any { it.id == draftOrDeed.id }

            val result = try {
                syncRepo.saveDeedLocallyAndTrySync(candidate, profile, action)
            } finally {
                _isSaving.value = false
                _savingAsDraft.value = null
            }

            result.onSuccess { outcome ->
                when (outcome) {
                    is SaveDeedOutcome.Saved -> {
                        scheduleAutoBackupIfConnected()
                        _statusMessage.value = DolilStrings.saveSuccessMessage(
                            lang = preferences.value.language,
                            isDraft = saveAsDraft,
                            isUpdate = isUpdate
                        )
                        onSavedSuccess(outcome.deed)
                    }
                    is SaveDeedOutcome.Conflict -> {
                        _activeConflict.value = outcome.conflict
                    }
                }
            }.onFailure {
                _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                    "সংরক্ষণ করা যায়নি। আপনার লেখা তথ্য হারায়নি, আবার চেষ্টা করুন।"
                } else {
                    "We couldn't save right now, so your entered info is still here. Please try again."
                }
            }
        }
    }

    fun approveDeed(deed: DeedRecord) {
        viewModelScope.launch {
            val profile = userProfile.value
            if (!RolePermissions.canApproveDeed(profile.role)) {
                _statusMessage.value = "Permission denied"
                return@launch
            }
            val approved = deed.copy(
                status = DeedStatus.APPROVED,
                visibility = DeedVisibility.PUBLIC
            )
            syncRepo.saveDeedLocallyAndTrySync(approved, profile, AuditAction.APPROVE)
                .onSuccess { outcome ->
                    if (outcome is SaveDeedOutcome.Conflict) {
                        _activeConflict.value = outcome.conflict
                    } else {
                        scheduleAutoBackupIfConnected()
                        _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                            "দলিল অনুমোদিত হয়েছে"
                        } else {
                            "Deed approved"
                        }
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to approve deed"
                }
        }
    }

    fun softDeleteDeed(deed: DeedRecord) {
        viewModelScope.launch {
            val profile = userProfile.value
            if (!RolePermissions.canSoftDeleteDeed(profile, deed)) {
                _statusMessage.value = "Permission denied"
                return@launch
            }
            syncRepo.softDeleteDeed(deed, profile)
                .onSuccess { outcome ->
                    val archivedDeed = (outcome as? SaveDeedOutcome.Saved)?.deed
                        ?: deed.copy(status = DeedStatus.SOFT_DELETED)
                    _lastArchivedDeed.value = archivedDeed
                    scheduleAutoBackupIfConnected()
                    _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                        "দলিলটি আর্কাইভ করা হয়েছে।"
                    } else {
                        "Moved to archive."
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to archive deed"
                }
        }
    }

    fun undoLastArchive() {
        val archived = _lastArchivedDeed.value ?: return
        _lastArchivedDeed.value = null
        restoreDeed(archived)
    }

    fun clearLastArchivedDeed() {
        _lastArchivedDeed.value = null
    }

    fun restoreDeed(deed: DeedRecord) {
        viewModelScope.launch {
            val profile = userProfile.value
            if (!RolePermissions.canRestoreDeed(profile, deed)) {
                _statusMessage.value = "Permission denied"
                return@launch
            }
            val targetStatus = when {
                deed.visibility == DeedVisibility.PRIVATE_DRAFT -> DeedStatus.DRAFT
                profile.role == UserRole.OFFICE_DATA_ENTRY -> DeedStatus.PENDING_REVIEW
                else -> DeedStatus.APPROVED
            }
            syncRepo.restoreDeed(deed, profile, targetStatus)
                .onSuccess { outcome ->
                    if (outcome is SaveDeedOutcome.Conflict) {
                        _activeConflict.value = outcome.conflict
                    } else {
                        scheduleAutoBackupIfConnected()
                        _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                            "চমৎকার! দলিলটি আবার ফিরিয়ে আনা হয়েছে।"
                        } else {
                            "Great! Your deed has been restored."
                        }
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to restore deed"
                }
        }
    }

    fun connectGoogleDrive(forceSelectAccount: Boolean = false) {
        if (isGuestMode) {
            _statusMessage.value = DolilStrings.guestSyncNotice(preferences.value.language)
            return
        }
        val ctx = appContext ?: return
        val store = identityStore ?: return
        val currentLink = store.getDriveLink(currentUserId)
        viewModelScope.launch {
            _isDriveBusy.value = true
            pendingDriveOperation = if (forceSelectAccount) {
                PendingDriveOperation.SWITCH_ACCOUNT
            } else {
                PendingDriveOperation.CONNECT
            }
            val authRes = driveAuthGateway.requestInteractiveAuthorization(
                context = ctx,
                targetAccountEmail = if (forceSelectAccount) null else currentLink.driveEmail.ifBlank { null },
                forceSelectAccount = forceSelectAccount
            )
            handleDriveAuthResult(authRes, switchAccount = forceSelectAccount)
        }
    }

    fun onDriveConsentResult(resultData: Intent?) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            _isDriveBusy.value = true
            _pendingDriveConsentIntent.value = null
            val res = driveAuthGateway.handleResolutionResult(ctx, resultData)
            val isSwitch = pendingDriveOperation == PendingDriveOperation.SWITCH_ACCOUNT
            handleDriveAuthResult(res, switchAccount = isSwitch)
        }
    }

    fun clearPendingDriveConsentIntent() {
        _pendingDriveConsentIntent.value = null
        _isDriveBusy.value = false
        _isBackupsLoading.value = false
        _isDevicesLoading.value = false
        clearPendingDriveOperationState()
    }

    private fun clearPendingDriveOperationState() {
        pendingDriveOperation = null
        pendingRestoreSummary = null
        pendingRestorePassphrase?.fill('\u0000')
        pendingRestorePassphrase = null
        pendingRetireDeviceId = null
    }

    private suspend fun handleDriveAuthResult(
        result: DriveAuthResult,
        switchAccount: Boolean
    ) {
        val store = identityStore ?: run {
            _isDriveBusy.value = false
            _isBackupsLoading.value = false
            _isDevicesLoading.value = false
            clearPendingDriveOperationState()
            return
        }
        when (result) {
            is DriveAuthResult.Authorized -> {
                lastVerifiedDriveToken = result.accessToken
                val current = store.getDriveLink(currentUserId)

                // Separate repository notice if switching Drive account on the same Firebase UID
                if (current.isConnected && current.driveSubjectId != result.accountInfo.subjectId && !switchAccount) {
                    _isDriveBusy.value = false
                    _isBackupsLoading.value = false
                    _isDevicesLoading.value = false
                    clearPendingDriveOperationState()
                    _driveAccountMismatch.value = DriveSyncOutcome.AccountMismatch(
                        expectedSubjectId = current.driveSubjectId,
                        expectedEmail = current.driveEmail,
                        actualSubjectId = result.accountInfo.subjectId,
                        actualEmail = result.accountInfo.email
                    )
                    return
                }

                val updatedLink = if (switchAccount && current.driveSubjectId != result.accountInfo.subjectId) {
                    DriveLinkState(
                        firebaseUid = currentUserId,
                        accountNamespaceId = NamespaceHelper.computeAccountNamespaceId(currentUserId),
                        driveSubjectId = result.accountInfo.subjectId,
                        driveEmail = result.accountInfo.email,
                        driveDisplayName = result.accountInfo.displayName,
                        autoSyncEnabled = current.autoSyncEnabled,
                        encryptionEnabled = current.encryptionEnabled,
                        syncStatus = DriveSyncStatus.IDLE
                    )
                } else {
                    current.copy(
                        firebaseUid = currentUserId,
                        accountNamespaceId = NamespaceHelper.computeAccountNamespaceId(currentUserId),
                        driveSubjectId = result.accountInfo.subjectId,
                        driveEmail = result.accountInfo.email,
                        driveDisplayName = result.accountInfo.displayName,
                        syncStatus = DriveSyncStatus.IDLE,
                        lastErrorMessage = null
                    )
                }
                store.saveDriveLink(updatedLink)
                refreshCredentialReadiness()

                val op = pendingDriveOperation
                pendingDriveOperation = null
                when (op) {
                    PendingDriveOperation.LIST_BACKUPS -> executeListBackups(result.accessToken, result.accountInfo)
                    PendingDriveOperation.LIST_DEVICES -> executeListDevices(result.accessToken, result.accountInfo)
                    PendingDriveOperation.RESTORE_BACKUP -> {
                        val summary = pendingRestoreSummary
                        val pass = pendingRestorePassphrase
                        pendingRestoreSummary = null
                        pendingRestorePassphrase = null
                        if (summary != null) {
                            executeRestoreFromBackup(result.accessToken, result.accountInfo, summary, pass)
                        } else {
                            _isDriveBusy.value = false
                        }
                    }
                    PendingDriveOperation.RETIRE_DEVICE -> {
                        val targetId = pendingRetireDeviceId
                        pendingRetireDeviceId = null
                        if (!targetId.isNullOrBlank()) {
                            executeRetireDevice(result.accessToken, result.accountInfo, targetId)
                        } else {
                            _isDriveBusy.value = false
                        }
                    }
                    PendingDriveOperation.FORCE_UPLOAD_SNAPSHOT -> executeDriveSync(result.accessToken, result.accountInfo, forceUpload = true)
                    else -> executeDriveSync(result.accessToken, result.accountInfo, forceUpload = false)
                }
            }
            is DriveAuthResult.NeedsResolution -> {
                _pendingDriveConsentIntent.value = result.pendingIntent
                _isDriveBusy.value = false
                _isBackupsLoading.value = false
                _isDevicesLoading.value = false
            }
            is DriveAuthResult.Cancelled -> {
                _isDriveBusy.value = false
                _isBackupsLoading.value = false
                _isDevicesLoading.value = false
                clearPendingDriveOperationState()
                _statusMessage.value = result.reason
            }
            is DriveAuthResult.Error -> {
                _isDriveBusy.value = false
                _isBackupsLoading.value = false
                _isDevicesLoading.value = false
                clearPendingDriveOperationState()
                if (result.isRevokedOrExpired) {
                    store.updateDriveSyncStatus(
                        currentUserId,
                        DriveSyncStatus.AUTHORIZATION_REQUIRED,
                        result.message
                    )
                } else {
                    store.updateDriveSyncStatus(currentUserId, DriveSyncStatus.FAILED, result.message)
                }
                _statusMessage.value = result.message
                refreshCredentialReadiness()
            }
        }
    }

    fun syncNow() {
        if (isGuestMode) {
            _statusMessage.value = DolilStrings.guestSyncNotice(preferences.value.language)
            return
        }
        val ctx = appContext ?: run {
            _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                "ক্লাউড ব্যাকআপের জন্য Google Drive সংযুক্ত করুন।"
            } else {
                "Connect Google Drive in Settings to back up and sync."
            }
            return
        }
        val store = identityStore ?: return
        val link = store.getDriveLink(currentUserId)
        if (!link.isConnected) {
            connectGoogleDrive(forceSelectAccount = false)
            return
        }

        viewModelScope.launch {
            _isDriveBusy.value = true
            pendingDriveOperation = PendingDriveOperation.SYNC_NOW
            val authRes = driveAuthGateway.requestInteractiveAuthorization(
                context = ctx,
                targetAccountEmail = link.driveEmail.ifBlank { null },
                forceSelectAccount = false
            )
            handleDriveAuthResult(authRes, switchAccount = false)
        }
    }

    private suspend fun executeDriveSync(
        accessToken: String,
        accountInfo: DriveAccountInfo,
        forceUpload: Boolean
    ) {
        val localDao = dao ?: return
        val store = identityStore ?: return
        val engine = DriveBackupEngine(
            driveApi = driveStorage,
            dao = localDao,
            identityStore = store
        )
        val passphrase = SessionPassphraseVault.getPassphrase(currentUserId)
        try {
            when (
                val outcome = engine.syncNow(
                    accessToken = accessToken,
                    accountInfo = accountInfo,
                    firebaseUid = currentUserId,
                    passphrase = passphrase,
                    forceUploadSnapshot = forceUpload
                )
            ) {
                is DriveSyncOutcome.Synced -> {
                    prefsStore.setLastSyncMillis(System.currentTimeMillis())
                    _isOffline.value = false
                    if (store.getDriveLink(currentUserId).autoSyncEnabled && appContext != null) {
                        DriveSyncScheduler.schedulePeriodicSync(appContext, currentUserId)
                    }
                    _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                        "Google Drive ব্যাকআপ ও সিঙ্ক সম্পন্ন হয়েছে!"
                    } else {
                        "Google Drive backup & sync completed!"
                    }
                }
                is DriveSyncOutcome.ConflictDetected -> {
                    _activeConflict.value = outcome.conflicts.firstOrNull()
                    _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                        "${outcome.conflicts.size}টি সংস্করণ দ্বন্দ্ব শনাক্ত হয়েছে।"
                    } else {
                        "${outcome.conflicts.size} sync conflict(s) detected."
                    }
                }
                is DriveSyncOutcome.PassphraseRequired -> {
                    _passphrasePromptBackupId.value = outcome.backupId ?: "__upload__"
                }
                is DriveSyncOutcome.AccountMismatch -> {
                    _driveAccountMismatch.value = outcome
                }
                is DriveSyncOutcome.AuthorizationRequired -> {
                    _statusMessage.value = outcome.message
                }
                is DriveSyncOutcome.Error -> {
                    _statusMessage.value = outcome.message
                }
            }
        } finally {
            passphrase?.fill('\u0000')
            _isDriveBusy.value = false
            refreshCredentialReadiness()
        }
    }

    fun confirmSwitchDriveAccount(accountMismatch: DriveSyncOutcome.AccountMismatch) {
        val store = identityStore ?: return
        _driveAccountMismatch.value = null
        val current = store.getDriveLink(currentUserId)
        store.saveDriveLink(
            DriveLinkState(
                firebaseUid = currentUserId,
                accountNamespaceId = NamespaceHelper.computeAccountNamespaceId(currentUserId),
                driveSubjectId = accountMismatch.actualSubjectId,
                driveEmail = accountMismatch.actualEmail,
                autoSyncEnabled = current.autoSyncEnabled,
                encryptionEnabled = false
            )
        )
        SessionPassphraseVault.clear(currentUserId)
        syncNow()
    }

    fun dismissDriveAccountMismatch() {
        _driveAccountMismatch.value = null
    }

    fun disconnectDriveLocally() {
        identityStore?.disconnectDriveLocally(currentUserId)
        lastVerifiedDriveToken = null
        appContext?.let { DriveSyncScheduler.cancelAllSync(it) }
        refreshCredentialReadiness()
        _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
            "Google Drive সংযোগ বিচ্ছিন্ন করা হয়েছে। আপনার Drive ফাইল ও ফোনের দলিল অক্ষত রয়েছে।"
        } else {
            "Google Drive disconnected on this device. Your remote Drive backups and local deeds remain intact."
        }
    }

    fun revokeGoogleDriveAccess() {
        val ctx = appContext ?: return
        viewModelScope.launch {
            _isDriveBusy.value = true
            val res = driveAuthGateway.revokeGoogleAccess(ctx, lastVerifiedDriveToken)
            identityStore?.disconnectDriveLocally(currentUserId)
            lastVerifiedDriveToken = null
            DriveSyncScheduler.cancelAllSync(ctx)
            _isDriveBusy.value = false
            refreshCredentialReadiness()
            _statusMessage.value = res.fold(
                onSuccess = {
                    if (preferences.value.language == AppLanguage.BN) {
                        "এই অ্যাপের জন্য Google OAuth অনুমতি প্রত্যাহার করা হয়েছে।"
                    } else {
                        "Google Drive OAuth access has been revoked for this app across sessions."
                    }
                },
                onFailure = { err ->
                    err.localizedMessage ?: "Local link cleared; revoke at Google Account settings if offline."
                }
            )
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        val store = identityStore ?: return
        val current = store.getDriveLink(currentUserId)
        store.saveDriveLink(current.copy(autoSyncEnabled = enabled))
        appContext?.let { ctx ->
            if (enabled && current.isConnected) {
                DriveSyncScheduler.schedulePeriodicSync(ctx, currentUserId)
            } else if (!enabled) {
                DriveSyncScheduler.cancelAllSync(ctx)
            }
        }
    }

    fun enableEncryption(passphrase: String) {
        if (passphrase.length < 6) {
            _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                "পাসফ্রেজ কমপক্ষে ৬ অক্ষরের হতে হবে।"
            } else {
                "Encryption passphrase must be at least 6 characters."
            }
            return
        }
        val store = identityStore ?: return
        SessionPassphraseVault.setPassphrase(currentUserId, passphrase)
        val current = store.getDriveLink(currentUserId)
        store.saveDriveLink(current.copy(encryptionEnabled = true))
        if (current.isConnected && appContext != null) {
            viewModelScope.launch {
                _isDriveBusy.value = true
                pendingDriveOperation = PendingDriveOperation.FORCE_UPLOAD_SNAPSHOT
                val authRes = driveAuthGateway.requestInteractiveAuthorization(
                    context = appContext,
                    targetAccountEmail = current.driveEmail.ifBlank { null },
                    forceSelectAccount = false
                )
                handleDriveAuthResult(authRes, switchAccount = false)
            }
        } else {
            _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                "AES-256-GCM এনক্রিপশন চালু হয়েছে।"
            } else {
                "Client-side AES-256-GCM backup encryption enabled."
            }
        }
    }

    fun disableEncryption() {
        val store = identityStore ?: return
        SessionPassphraseVault.clear(currentUserId)
        val current = store.getDriveLink(currentUserId)
        store.saveDriveLink(current.copy(encryptionEnabled = false))
        _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
            "নতুন ব্যাকআপের জন্য এনক্রিপশন বন্ধ করা হয়েছে। আগের এনক্রিপ্টেড ফাইল রিস্টোর করতে পাসফ্রেজ লাগবে।"
        } else {
            "Encryption disabled for new backups. Older encrypted snapshots still require their passphrase."
        }
    }

    fun unlockPassphraseAndSync(passphrase: String) {
        if (passphrase.isBlank()) return
        SessionPassphraseVault.setPassphrase(currentUserId, passphrase)
        _passphrasePromptBackupId.value = null
        syncNow()
    }

    fun dismissPassphrasePrompt() {
        _passphrasePromptBackupId.value = null
    }

    fun loadAvailableBackups() {
        val ctx = appContext ?: return
        val store = identityStore ?: return
        val link = store.getDriveLink(currentUserId)
        if (!link.isConnected) return
        viewModelScope.launch {
            _isBackupsLoading.value = true
            pendingDriveOperation = PendingDriveOperation.LIST_BACKUPS
            val authRes = driveAuthGateway.requestInteractiveAuthorization(
                context = ctx,
                targetAccountEmail = link.driveEmail.ifBlank { null },
                forceSelectAccount = false
            )
            handleDriveAuthResult(authRes, switchAccount = false)
        }
    }

    private suspend fun executeListBackups(accessToken: String, accountInfo: DriveAccountInfo) {
        try {
            val localDao = dao ?: return
            val store = identityStore ?: return
            val engine = DriveBackupEngine(driveStorage, localDao, store)
            engine.listAvailableBackups(accessToken, accountInfo, currentUserId)
                .onSuccess { _availableBackups.value = it }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to load backups" }
        } finally {
            _isBackupsLoading.value = false
            _isDriveBusy.value = false
        }
    }

    fun restoreFromBackup(summary: DriveBackupSummary, passphraseInput: String?) {
        val ctx = appContext ?: return
        val store = identityStore ?: return
        val link = store.getDriveLink(currentUserId)
        viewModelScope.launch {
            _isDriveBusy.value = true
            pendingRestoreSummary = summary
            pendingRestorePassphrase?.fill('\u0000')
            pendingRestorePassphrase = passphraseInput?.toCharArray()
            pendingDriveOperation = PendingDriveOperation.RESTORE_BACKUP
            val authRes = driveAuthGateway.requestInteractiveAuthorization(
                context = ctx,
                targetAccountEmail = link.driveEmail.ifBlank { null },
                forceSelectAccount = false
            )
            handleDriveAuthResult(authRes, switchAccount = false)
        }
    }

    private suspend fun executeRestoreFromBackup(
        accessToken: String,
        accountInfo: DriveAccountInfo,
        summary: DriveBackupSummary,
        passphraseInput: CharArray?
    ) {
        val pass = passphraseInput ?: SessionPassphraseVault.getPassphrase(currentUserId)
        try {
            val localDao = dao ?: return
            val store = identityStore ?: return
            if (passphraseInput != null) {
                val str = String(passphraseInput)
                if (str.isNotBlank()) {
                    SessionPassphraseVault.setPassphrase(currentUserId, str)
                }
            }
            val engine = DriveBackupEngine(driveStorage, localDao, store)
            engine.restoreFromBackup(
                accessToken = accessToken,
                accountInfo = accountInfo,
                firebaseUid = currentUserId,
                backupFileId = summary.fileId,
                passphrase = pass
            ).onSuccess { count ->
                _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                    "ব্যাকআপ থেকে $count টি দলিল পুনরুদ্ধার হয়েছে!"
                } else {
                    "Restored $count deed(s) from Drive backup!"
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Restore failed"
            }
        } finally {
            pass?.fill('\u0000')
            _isDriveBusy.value = false
        }
    }

    fun loadConnectedDevices() {
        val ctx = appContext ?: return
        val store = identityStore ?: return
        val link = store.getDriveLink(currentUserId)
        if (!link.isConnected) return
        viewModelScope.launch {
            _isDevicesLoading.value = true
            pendingDriveOperation = PendingDriveOperation.LIST_DEVICES
            val authRes = driveAuthGateway.requestInteractiveAuthorization(
                context = ctx,
                targetAccountEmail = link.driveEmail.ifBlank { null },
                forceSelectAccount = false
            )
            handleDriveAuthResult(authRes, switchAccount = false)
        }
    }

    private suspend fun executeListDevices(accessToken: String, accountInfo: DriveAccountInfo) {
        try {
            val localDao = dao ?: return
            val store = identityStore ?: return
            val engine = DriveBackupEngine(driveStorage, localDao, store)
            engine.listDevices(accessToken, accountInfo, currentUserId)
                .onSuccess { _connectedDevices.value = it }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to load devices" }
        } finally {
            _isDevicesLoading.value = false
            _isDriveBusy.value = false
        }
    }

    fun retireDevice(targetDeviceId: String) {
        val ctx = appContext ?: return
        val store = identityStore ?: return
        val link = store.getDriveLink(currentUserId)
        viewModelScope.launch {
            _isDriveBusy.value = true
            pendingRetireDeviceId = targetDeviceId
            pendingDriveOperation = PendingDriveOperation.RETIRE_DEVICE
            val authRes = driveAuthGateway.requestInteractiveAuthorization(
                context = ctx,
                targetAccountEmail = link.driveEmail.ifBlank { null },
                forceSelectAccount = false
            )
            handleDriveAuthResult(authRes, switchAccount = false)
        }
    }

    private suspend fun executeRetireDevice(
        accessToken: String,
        accountInfo: DriveAccountInfo,
        targetDeviceId: String
    ) {
        try {
            val localDao = dao ?: return
            val store = identityStore ?: return
            val engine = DriveBackupEngine(driveStorage, localDao, store)
            engine.retireDevice(accessToken, currentUserId, targetDeviceId, accountInfo)
                .onSuccess {
                    _connectedDevices.value = _connectedDevices.value.map {
                        if (it.deviceId == targetDeviceId) it.copy(retired = true) else it
                    }
                    _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                        "ডিভাইসটি অবসরপ্রাপ্ত (Retired) হিসেবে চিহ্নিত হয়েছে।"
                    } else {
                        "Device retired from active checkpoints."
                    }
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to retire device" }
        } finally {
            _isDriveBusy.value = false
        }
    }

    fun buildPortableBackupJson(onResult: (String) -> Unit) {
        val localDao = dao ?: return
        val store = identityStore ?: return
        viewModelScope.launch {
            val engine = DriveBackupEngine(driveStorage, localDao, store)
            val json = engine.exportPortableBundleJson(currentUserId)
            onResult(json)
        }
    }

    fun importPortableBackupJson(rawJson: String) {
        val localDao = dao ?: return
        val store = identityStore ?: return
        viewModelScope.launch {
            val engine = DriveBackupEngine(driveStorage, localDao, store)
            engine.importPortableBundleJson(
                rawJson = rawJson,
                targetOwnerUid = currentUserId,
                deviceId = store.getOrCreateDeviceId()
            ).onSuccess { count ->
                scheduleAutoBackupIfConnected()
                _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                    "পোর্টেবল ব্যাকআপ থেকে $count টি দলিল ইমপোর্ট হয়েছে।"
                } else {
                    "Imported $count deed(s) from portable backup."
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Invalid backup JSON"
            }
        }
    }

    fun importLegacyLocalDeeds() {
        if (isGuestMode) return
        viewModelScope.launch {
            runCatching {
                syncRepo.migrateLegacyLocalDataToCurrentOwner()
            }.onSuccess {
                scheduleAutoBackupIfConnected()
                _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                    "পুরোনো স্থানীয় দলিল আপনার অ্যাকাউন্টে যোগ করা হয়েছে।"
                } else {
                    "Legacy local deeds have been imported into your account."
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to import legacy local deeds"
            }
        }
    }

    fun importCustomGuestDeeds() {
        if (isGuestMode) return
        val gDao = guestDao ?: return
        viewModelScope.launch {
            runCatching {
                val customDeeds = GuestSandboxSeeder.getUserCreatedGuestDeeds(gDao)
                if (customDeeds.isEmpty()) {
                    _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                        "Guest মোডে নিজস্ব কোনো দলিল পাওয়া যায়নি।"
                    } else {
                        "No user-created deeds found in Guest Sandbox."
                    }
                    return@launch
                }
                syncRepo.importDeedsIntoCurrentOwner(customDeeds, userProfile.value)
            }.onSuccess { imported ->
                _customGuestDeedCount.value = 0
                scheduleAutoBackupIfConnected()
                _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                    "Guest মোড থেকে $imported টি নিজস্ব দলিল ইমপোর্ট হয়েছে।"
                } else {
                    "Imported $imported custom deed(s) from Guest Mode."
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to import guest deeds"
            }
        }
    }

    fun deleteAccount(
        currentPassword: String?,
        activityForGoogleReauth: android.app.Activity?,
        credentialManager: androidx.credentials.CredentialManager?,
        driveCleanupChoice: AccountDriveCleanupChoice,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isGuestMode) return
        val ctx = appContext ?: return
        val lang = preferences.value.language
        viewModelScope.launch {
            _isDriveBusy.value = true
            try {
                val reauthRes = when {
                    !currentPassword.isNullOrBlank() ->
                        AuthManager.reauthenticateWithPassword(ctx, currentPassword, lang)
                    activityForGoogleReauth != null && credentialManager != null ->
                        AuthManager.reauthenticateWithGoogle(activityForGoogleReauth, credentialManager, lang)
                    else -> Result.failure(IllegalStateException("Re-authentication is required before deleting your account."))
                }
                if (reauthRes.isFailure) {
                    onError(reauthRes.exceptionOrNull()?.localizedMessage ?: "Re-authentication failed")
                    return@launch
                }

                if (driveCleanupChoice == AccountDriveCleanupChoice.DELETE_DRIVE_BACKUPS &&
                    dao != null && identityStore != null &&
                    driveLinkState.value.isConnected
                ) {
                    val silentAuth = driveAuthGateway.authorizeSilently(
                        ctx,
                        driveLinkState.value.driveEmail.ifBlank { null }
                    )
                    if (silentAuth is DriveAuthResult.Authorized) {
                        val engine = DriveBackupEngine(driveStorage, dao, identityStore)
                        engine.deleteAllRemoteBackupsForUid(silentAuth.accessToken, currentUserId)
                    } else {
                        onError("Please reconnect Google Drive first to delete remote Drive backups, or choose Keep Backups.")
                        return@launch
                    }
                }

                AuthManager.deleteCurrentAccount(ctx, lang)
                    .onSuccess {
                        syncRepo.deleteAllCurrentOwnerData()
                        identityStore?.deleteAccountLocalMetadata(currentUserId)
                        DriveSyncScheduler.cancelAllSync(ctx)
                        onCompleted()
                    }
                    .onFailure { err ->
                        onError(err.localizedMessage ?: "Account deletion failed")
                    }
            } finally {
                _isDriveBusy.value = false
            }
        }
    }

    fun resolveConflictKeepLocal(conflict: DeedConflict) {
        viewModelScope.launch {
            runCatching {
                syncRepo.resolveConflictKeepLocal(conflict, userProfile.value).getOrThrow()
                identityStore?.appendConflictAudit(
                    ConflictAuditRecord(
                        firebaseUid = currentUserId,
                        deedId = conflict.deedId,
                        deedNumber = conflict.localDeed.deedNumber,
                        resolution = "KEEP_LOCAL",
                        localRevisionId = conflict.localDeed.revisionId,
                        remoteRevisionId = conflict.serverDeed.revisionId,
                        remoteDeviceId = conflict.remoteDeviceId
                    )
                )
            }.onSuccess {
                _activeConflict.value = null
                scheduleAutoBackupIfConnected()
                _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                    "স্থানীয় সংস্করণ রাখা হয়েছে ও সিঙ্কের জন্য প্রস্তুত।"
                } else {
                    "Local changes kept and queued for Drive sync."
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to resolve conflict"
            }
        }
    }

    fun resolveConflictAcceptServer(conflict: DeedConflict) {
        viewModelScope.launch {
            runCatching {
                syncRepo.resolveConflictAcceptServer(conflict)
                identityStore?.appendConflictAudit(
                    ConflictAuditRecord(
                        firebaseUid = currentUserId,
                        deedId = conflict.deedId,
                        deedNumber = conflict.serverDeed.deedNumber,
                        resolution = "ACCEPT_DRIVE",
                        localRevisionId = conflict.localDeed.revisionId,
                        remoteRevisionId = conflict.serverDeed.revisionId,
                        remoteDeviceId = conflict.remoteDeviceId
                    )
                )
            }.onSuccess {
                _activeConflict.value = null
                _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                    "Google Drive সংস্করণ গ্রহণ করা হয়েছে।"
                } else {
                    "Google Drive version accepted."
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to apply Drive version"
            }
        }
    }

    fun resolveConflictKeepBoth(conflict: DeedConflict) {
        viewModelScope.launch {
            runCatching {
                syncRepo.resolveConflictKeepBoth(conflict, userProfile.value).getOrThrow()
                identityStore?.appendConflictAudit(
                    ConflictAuditRecord(
                        firebaseUid = currentUserId,
                        deedId = conflict.deedId,
                        deedNumber = conflict.localDeed.deedNumber,
                        resolution = "KEEP_BOTH",
                        localRevisionId = conflict.localDeed.revisionId,
                        remoteRevisionId = conflict.serverDeed.revisionId,
                        remoteDeviceId = conflict.remoteDeviceId
                    )
                )
            }.onSuccess {
                _activeConflict.value = null
                scheduleAutoBackupIfConnected()
                _statusMessage.value = if (preferences.value.language == AppLanguage.BN) {
                    "উভয় সংস্করণই সংরক্ষণ করা হয়েছে।"
                } else {
                    "Both versions preserved cleanly."
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to keep both versions"
            }
        }
    }

    fun dismissConflict() {
        _activeConflict.value = null
    }

    fun reportStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearValidationError(code: ValidationErrorCode) {
        if (code in _validationErrors.value) {
            _validationErrors.value = _validationErrors.value.filterNot { it == code }
            if (code == ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE) {
                _duplicateCandidateId.value = null
            }
        }
    }

    fun clearValidationErrors() {
        _validationErrors.value = emptyList()
        _duplicateCandidateId.value = null
    }

    private fun scheduleAutoBackupIfConnected() {
        val ctx = appContext ?: return
        if (isGuestMode) return
        val link = identityStore?.getDriveLink(currentUserId) ?: return
        if (link.isConnected && link.autoSyncEnabled) {
            DriveSyncScheduler.scheduleDebouncedBackup(ctx, currentUserId)
        }
    }
}
