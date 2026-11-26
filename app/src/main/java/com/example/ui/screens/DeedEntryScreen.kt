package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DolilComponentTokens
import com.example.core.designsystem.DolilConfirmationDialog
import com.example.core.designsystem.DolilErrorBanner
import com.example.core.designsystem.DolilIconSize
import com.example.core.designsystem.DolilIcons
import com.example.core.designsystem.DolilOutlinedField
import com.example.core.designsystem.DolilPrimaryButton
import com.example.core.designsystem.DolilRadius
import com.example.core.designsystem.DolilSecondaryButton
import com.example.core.designsystem.DolilSpacing
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.DolilTonalCard
import com.example.core.designsystem.DolilTopBar
import com.example.core.designsystem.FormSectionCard
import com.example.core.designsystem.dolilColors
import com.example.core.designsystem.dolilStr
import com.example.core.designsystem.editorialNumeric
import com.example.core.designsystem.performSubtleTick
import com.example.core.designsystem.tabularNumerals
import com.example.data.local.BangladeshLocationsCatalog
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedType
import com.example.domain.model.LandClass
import com.example.domain.model.LandParcel
import com.example.domain.model.LandUnit
import com.example.domain.model.PartyInfo
import com.example.domain.model.SurveyType
import com.example.domain.model.UserProfile
import com.example.domain.search.SearchNormalizer
import com.example.domain.validation.ValidationErrorCode
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.TimeZone

private data class EntryFormSnapshot(
    val officeId: String,
    val deedType: DeedType,
    val deedNumber: String,
    val deedYearText: String,
    val registrationDateMillis: Long,
    val districtName: String,
    val upazilaName: String,
    val unionName: String,
    val mouzaName: String,
    val jlNumber: String,
    val grantors: List<PartyInfo>,
    val grantees: List<PartyInfo>,
    val parcels: List<LandParcel>,
    val deedNotes: String
)

private fun stepIndexForError(code: ValidationErrorCode): Int = when (code) {
    ValidationErrorCode.REQUIRED_DEED_NUMBER,
    ValidationErrorCode.INVALID_DEED_YEAR,
    ValidationErrorCode.INVALID_REGISTRATION_DATE,
    ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE -> 0
    ValidationErrorCode.REQUIRED_GRANTOR_NAME -> 1
    ValidationErrorCode.REQUIRED_GRANTEE_NAME -> 2
    ValidationErrorCode.REQUIRED_DISTRICT,
    ValidationErrorCode.REQUIRED_UPAZILA,
    ValidationErrorCode.REQUIRED_MOUZA,
    ValidationErrorCode.REQUIRED_JL_NUMBER -> 3
    ValidationErrorCode.REQUIRED_PARCEL,
    ValidationErrorCode.REQUIRED_KHATIAN,
    ValidationErrorCode.REQUIRED_DAG,
    ValidationErrorCode.INVALID_LAND_AREA -> 4
}

private fun validateWizardStep(
    step: Int,
    deedNumber: String,
    deedYearText: String,
    registrationDateMillis: Long,
    grantors: List<PartyInfo>,
    grantees: List<PartyInfo>,
    districtName: String,
    upazilaName: String,
    mouzaName: String,
    jlNumber: String,
    parcels: List<LandParcel>
): List<ValidationErrorCode> {
    val errors = mutableListOf<ValidationErrorCode>()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    when (step) {
        0 -> {
            if (SearchNormalizer.normalizeCode(deedNumber).isBlank()) {
                errors.add(ValidationErrorCode.REQUIRED_DEED_NUMBER)
            }
            val year = SearchNormalizer.normalizeCode(deedYearText).toIntOrNull() ?: 0
            if (year < 1900 || year > currentYear + 1) {
                errors.add(ValidationErrorCode.INVALID_DEED_YEAR)
            }
            if (registrationDateMillis <= 0L) {
                errors.add(ValidationErrorCode.INVALID_REGISTRATION_DATE)
            }
        }
        1 -> {
            if (grantors.none { it.fullName.isNotBlank() }) {
                errors.add(ValidationErrorCode.REQUIRED_GRANTOR_NAME)
            }
        }
        2 -> {
            if (grantees.none { it.fullName.isNotBlank() }) {
                errors.add(ValidationErrorCode.REQUIRED_GRANTEE_NAME)
            }
        }
        3 -> {
            if (districtName.isBlank()) errors.add(ValidationErrorCode.REQUIRED_DISTRICT)
            if (upazilaName.isBlank()) errors.add(ValidationErrorCode.REQUIRED_UPAZILA)
            if (mouzaName.isBlank()) errors.add(ValidationErrorCode.REQUIRED_MOUZA)
            if (jlNumber.isBlank()) errors.add(ValidationErrorCode.REQUIRED_JL_NUMBER)
        }
        4 -> {
            if (parcels.isEmpty()) {
                errors.add(ValidationErrorCode.REQUIRED_PARCEL)
            } else {
                if (parcels.any { it.khatianNumber.isBlank() }) errors.add(ValidationErrorCode.REQUIRED_KHATIAN)
                if (parcels.any { it.dagNumber.isBlank() }) errors.add(ValidationErrorCode.REQUIRED_DAG)
                val hasInvalidArea = parcels.any { parcel ->
                    val bd = SearchNormalizer.normalizeCode(parcel.landAreaDecimal)
                        .replace(',', '.')
                        .toBigDecimalOrNull()
                    bd == null || bd <= BigDecimal.ZERO
                }
                if (hasInvalidArea) errors.add(ValidationErrorCode.INVALID_LAND_AREA)
            }
        }
    }
    return errors
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeedEntryScreen(
    language: AppLanguage,
    userProfile: UserProfile,
    initialDeed: DeedRecord? = null,
    prefillDistrict: String = "",
    prefillUpazila: String = "",
    prefillMouza: String = "",
    prefillJl: String = "",
    validationErrors: List<ValidationErrorCode>,
    duplicateCandidateId: String?,
    isSaving: Boolean,
    savingAsDraft: Boolean? = null,
    onSaveDeed: (DeedRecord, saveAsDraft: Boolean, ignoreDuplicate: Boolean) -> Unit,
    onViewDuplicateDeed: (String) -> Unit,
    onClearValidationError: (ValidationErrorCode) -> Unit = {},
    onClearValidation: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val semantic = MaterialTheme.dolilColors
    remember(context) {
        BangladeshLocationsCatalog.ensureLoaded(context)
        true
    }

    val isEditMode = initialDeed != null
    val initialYear = initialDeed?.deedYear ?: Calendar.getInstance().get(Calendar.YEAR)
    val initialRegMillis = remember(initialDeed?.id) {
        initialDeed?.registrationDateMillis ?: System.currentTimeMillis()
    }
    val initialGrantors = remember(initialDeed?.id) {
        initialDeed?.grantors?.takeIf { it.isNotEmpty() } ?: listOf(PartyInfo())
    }
    val initialGrantees = remember(initialDeed?.id) {
        initialDeed?.grantees?.takeIf { it.isNotEmpty() } ?: listOf(PartyInfo())
    }
    val initialParcels = remember(initialDeed?.id) {
        initialDeed?.parcels?.takeIf { it.isNotEmpty() } ?: listOf(LandParcel())
    }
    val initialOfficeId = remember(initialDeed?.id) {
        initialDeed?.officeId?.ifBlank { userProfile.officeId } ?: userProfile.officeId
    }
    val initialOfficeName = remember(initialDeed?.id) {
        initialDeed?.officeName?.ifBlank { userProfile.officeName } ?: userProfile.officeName
    }
    val defaultDistrictName = if (language == AppLanguage.BN) "নারায়ণগঞ্জ" else "Narayanganj"
    val startDistrict = initialDeed?.districtName?.ifBlank { defaultDistrictName }
        ?: prefillDistrict.ifBlank { defaultDistrictName }
    val startUpazila = initialDeed?.upazilaName ?: prefillUpazila
    val startMouza = initialDeed?.mouzaName ?: prefillMouza
    val startJl = initialDeed?.jlNumber ?: prefillJl

    var officeId by remember(initialDeed?.id) { mutableStateOf(initialOfficeId) }
    var officeName by remember(initialDeed?.id) { mutableStateOf(initialOfficeName) }
    var deedType by remember(initialDeed?.id) { mutableStateOf(initialDeed?.deedType ?: DeedType.SAF_KABALA) }
    var deedNumber by remember(initialDeed?.id) { mutableStateOf(initialDeed?.deedNumber.orEmpty()) }
    var deedYearText by remember(initialDeed?.id) { mutableStateOf(initialYear.toString()) }
    var registrationDateMillis by remember(initialDeed?.id) { mutableStateOf(initialRegMillis) }
    var districtName by remember(initialDeed?.id, prefillDistrict) { mutableStateOf(startDistrict) }
    var upazilaName by remember(initialDeed?.id, prefillUpazila) { mutableStateOf(startUpazila) }
    var unionName by remember(initialDeed?.id) { mutableStateOf(initialDeed?.unionName.orEmpty()) }
    var mouzaName by remember(initialDeed?.id, prefillMouza) { mutableStateOf(startMouza) }
    var jlNumber by remember(initialDeed?.id, prefillJl) { mutableStateOf(startJl) }
    var grantors by remember(initialDeed?.id) { mutableStateOf(initialGrantors) }
    var grantees by remember(initialDeed?.id) { mutableStateOf(initialGrantees) }
    var parcels by remember(initialDeed?.id) { mutableStateOf(initialParcels) }
    var deedNotes by remember(initialDeed?.id) { mutableStateOf(initialDeed?.notes.orEmpty()) }

    var currentStep by rememberSaveable(initialDeed?.id) { mutableIntStateOf(0) }
    var localStepErrors by remember(initialDeed?.id) { mutableStateOf<List<ValidationErrorCode>>(emptyList()) }

    val combinedErrors = remember(validationErrors, localStepErrors) {
        (validationErrors + localStepErrors).distinct()
    }

    fun clearError(code: ValidationErrorCode) {
        if (code in localStepErrors) {
            localStepErrors = localStepErrors.filterNot { it == code }
        }
        onClearValidationError(code)
    }

    fun clearAllErrors() {
        localStepErrors = emptyList()
        onClearValidation()
    }

    val initialSnapshot = remember(initialDeed?.id, prefillDistrict, prefillUpazila, prefillMouza, prefillJl) {
        EntryFormSnapshot(
            officeId = initialOfficeId,
            deedType = initialDeed?.deedType ?: DeedType.SAF_KABALA,
            deedNumber = initialDeed?.deedNumber.orEmpty(),
            deedYearText = initialYear.toString(),
            registrationDateMillis = initialRegMillis,
            districtName = startDistrict,
            upazilaName = startUpazila,
            unionName = initialDeed?.unionName.orEmpty(),
            mouzaName = startMouza,
            jlNumber = startJl,
            grantors = initialGrantors,
            grantees = initialGrantees,
            parcels = initialParcels,
            deedNotes = initialDeed?.notes.orEmpty()
        )
    }

    val isDirty by remember {
        derivedStateOf {
            EntryFormSnapshot(
                officeId = officeId,
                deedType = deedType,
                deedNumber = deedNumber,
                deedYearText = deedYearText,
                registrationDateMillis = registrationDateMillis,
                districtName = districtName,
                upazilaName = upazilaName,
                unionName = unionName,
                mouzaName = mouzaName,
                jlNumber = jlNumber,
                grantors = grantors,
                grantees = grantees,
                parcels = parcels,
                deedNotes = deedNotes
            ) != initialSnapshot
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var lastRequestedDraft by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val handleExitRequest: () -> Unit = {
        when {
            isDirty -> showDiscardDialog = true
            currentStep > 0 -> {
                localStepErrors = emptyList()
                currentStep -= 1
            }
            else -> {
                clearAllErrors()
                onBack()
            }
        }
    }

    BackHandler(onBack = handleExitRequest)

    var previousInitialDeedId by remember { mutableStateOf(initialDeed?.id) }
    LaunchedEffect(initialDeed?.id) {
        if (previousInitialDeedId != initialDeed?.id) {
            previousInitialDeedId = initialDeed?.id
            clearAllErrors()
        }
    }

    // Auto-jump to the step containing the first server/ViewModel validation error
    LaunchedEffect(validationErrors) {
        if (validationErrors.isNotEmpty()) {
            currentStep = stepIndexForError(validationErrors.first())
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(currentStep) {
        listState.scrollToItem(0)
    }

    fun buildCurrentRecord(): DeedRecord {
        val base = initialDeed ?: DeedRecord(
            createdBy = userProfile.userId,
            organizationId = userProfile.organizationId
        )
        return base.copy(
            officeId = officeId,
            officeName = officeName,
            deedType = deedType,
            deedNumber = deedNumber.trim(),
            deedYear = SearchNormalizer.normalizeCode(deedYearText).toIntOrNull() ?: 0,
            registrationDateMillis = registrationDateMillis,
            districtName = districtName.trim(),
            upazilaName = upazilaName.trim(),
            unionName = unionName.trim(),
            mouzaName = mouzaName.trim(),
            jlNumber = jlNumber.trim(),
            grantors = grantors,
            grantees = grantees,
            parcels = parcels,
            notes = deedNotes.trim()
        )
    }

    val totalShotok = remember(parcels) {
        parcels.fold(BigDecimal.ZERO) { acc, parcel -> acc.add(parcel.areaAsShotok()) }
            .setScale(2, RoundingMode.HALF_UP)
    }
    val totalKatha = remember(totalShotok) {
        totalShotok.divide(BigDecimal("1.65"), 2, RoundingMode.HALF_UP)
    }
    val totalSqFt = remember(totalShotok) {
        totalShotok.multiply(BigDecimal("435.6")).setScale(1, RoundingMode.HALF_UP)
    }

    val sectionLabels = remember(language) {
        if (language == AppLanguage.BN) {
            listOf("১. সাধারণ", "২. দাতা", "৩. গ্রহীতা", "৪. অবস্থান", "৫. জমি ও রিভিউ")
        } else {
            listOf("1. General", "2. Grantors", "3. Grantees", "4. Location", "5. Land & Review")
        }
    }

    val stepCompletedFlags by remember {
        derivedStateOf {
            listOf(
                deedNumber.isNotBlank() && (SearchNormalizer.normalizeCode(deedYearText).toIntOrNull() ?: 0) >= 1900,
                grantors.any { it.fullName.isNotBlank() },
                grantees.any { it.fullName.isNotBlank() },
                districtName.isNotBlank() && upazilaName.isNotBlank() && mouzaName.isNotBlank() && jlNumber.isNotBlank(),
                parcels.any {
                    it.khatianNumber.isNotBlank() &&
                        it.dagNumber.isNotBlank() &&
                        it.areaAsShotok() > BigDecimal.ZERO
                }
            )
        }
    }

    Scaffold(
        topBar = {
            DolilTopBar(
                title = if (isEditMode) DolilStrings.editDeedTitle(language) else DolilStrings.newDeedTitle(language),
                subtitle = if (language == AppLanguage.BN) {
                    "ধাপ ${currentStep + 1}/5 • ${sectionLabels[currentStep]}"
                } else {
                    "Step ${currentStep + 1}/5 • ${sectionLabels[currentStep]}"
                },
                onBack = {
                    if (isDirty) {
                        showDiscardDialog = true
                    } else {
                        clearAllErrors()
                        onBack()
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
            ) {
                val completedSectionsCount = remember(stepCompletedFlags) {
                    stepCompletedFlags.count { it }
                }
                val stepProgressTarget = ((currentStep + 1) / 5f).coerceIn(0.2f, 1f)
                val completionFraction by animateFloatAsState(
                    targetValue = stepProgressTarget,
                    label = "entry_wizard_progress"
                )

                // Pinned 5-step wizard stepper & progress indicator
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
                            horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sectionLabels.forEachIndexed { idx, label ->
                                val selected = currentStep == idx
                                val isCompleted = stepCompletedFlags[idx]
                                val hasStepError = combinedErrors.any { stepIndexForError(it) == idx }
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptics.performSubtleTick()
                                        localStepErrors = emptyList()
                                        currentStep = idx
                                    },
                                    leadingIcon = when {
                                        isCompleted && !hasStepError -> {
                                            {
                                                Icon(
                                                    painter = DolilIcons.stepCheckPainter(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(DolilIconSize.Xs)
                                                )
                                            }
                                        }
                                        hasStepError -> {
                                            {
                                                Icon(
                                                    imageVector = DolilIcons.Error,
                                                    contentDescription = null,
                                                    tint = semantic.error,
                                                    modifier = Modifier.size(DolilIconSize.Xs)
                                                )
                                            }
                                        }
                                        else -> null
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("entry_section_pill_$idx")
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(DolilRadius.Pill)
                            ) {
                                Text(
                                    text = "$completedSectionsCount/5",
                                    style = MaterialTheme.typography.labelSmall.tabularNumerals(),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { completionFraction },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                }

                val currentStepErrors = remember(combinedErrors, currentStep) {
                    combinedErrors.filter { stepIndexForError(it) == currentStep }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .navigationBarsPadding()
                        .testTag("deed_entry_screen"),
                    contentPadding = PaddingValues(DolilSpacing.Space4),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space4)
                ) {
                    if (currentStepErrors.isNotEmpty()) {
                        item {
                            val combinedMessage = currentStepErrors.joinToString("\n") { code ->
                                "• ${DolilStrings.validationMessage(code, language)}"
                            }
                            DolilErrorBanner(
                                message = combinedMessage,
                                onDismiss = { clearAllErrors() }
                            )
                        }
                    }

                    // Active Step Form Content
                    when (currentStep) {
                        0 -> item {
                                val deedNumberError = ValidationErrorCode.REQUIRED_DEED_NUMBER in combinedErrors
                                val deedYearError = ValidationErrorCode.INVALID_DEED_YEAR in combinedErrors

                                FormSectionCard(
                                    title = DolilStrings.secGeneral(language),
                                    subtitle = if (language == AppLanguage.BN) {
                                        "দলিলের ধরন, নম্বর, তারিখ ও অফিস নির্ধারণ করুন"
                                    } else {
                                        "Specify deed classification, number, date, and office"
                                    },
                                    icon = DolilIcons.Document
                                ) {
                                    Text(
                                        text = DolilStrings.deedTypeLabel(language),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                                        verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)
                                    ) {
                                        DeedType.entries.forEach { type ->
                                            FilterChip(
                                                selected = deedType == type,
                                                onClick = { deedType = type },
                                                label = { Text(DolilStrings.deedTypeText(type, language)) },
                                                modifier = Modifier.testTag("deed_type_${type.name}")
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                                    ) {
                                        DolilOutlinedField(
                                            value = deedNumber,
                                            onValueChange = {
                                                deedNumber = it
                                                clearError(ValidationErrorCode.REQUIRED_DEED_NUMBER)
                                                clearError(ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE)
                                            },
                                            label = DolilStrings.deedNumberLabel(language),
                                            supportingText = if (deedNumberError) {
                                                DolilStrings.validationMessage(ValidationErrorCode.REQUIRED_DEED_NUMBER, language)
                                            } else {
                                                dolilStr(R.string.helper_deed_number)
                                            },
                                            testTag = "entry_deed_number",
                                            isError = deedNumberError,
                                            modifier = Modifier.weight(1.2f)
                                        )
                                        DolilOutlinedField(
                                            value = deedYearText,
                                            onValueChange = {
                                                deedYearText = it
                                                clearError(ValidationErrorCode.INVALID_DEED_YEAR)
                                                clearError(ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE)
                                            },
                                            label = DolilStrings.deedYearLabel(language),
                                            supportingText = if (deedYearError) {
                                                DolilStrings.validationMessage(ValidationErrorCode.INVALID_DEED_YEAR, language)
                                            } else {
                                                dolilStr(R.string.helper_deed_year)
                                            },
                                            testTag = "entry_deed_year",
                                            isError = deedYearError,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    val regDateFormatted = DolilStrings.formatDate(registrationDateMillis, language)
                                    val regDateError = ValidationErrorCode.INVALID_REGISTRATION_DATE in combinedErrors
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            DolilOutlinedField(
                                                value = regDateFormatted,
                                                onValueChange = {},
                                                label = DolilStrings.regDateLabel(language),
                                                readOnly = true,
                                                isError = regDateError,
                                                testTag = "entry_reg_date",
                                                trailingIcon = {
                                                    Spacer(Modifier.size(48.dp))
                                                }
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .padding(top = 6.dp)
                                                    .clip(RoundedCornerShape(DolilRadius.Input))
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .clickable(
                                                        role = Role.Button,
                                                        onClickLabel = DolilStrings.regDateLabel(language)
                                                    ) {
                                                        haptics.performSubtleTick()
                                                        showDatePicker = true
                                                    }
                                                    .testTag("entry_reg_date_box")
                                            )
                                            IconButton(
                                                onClick = {
                                                    haptics.performSubtleTick()
                                                    showDatePicker = true
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .padding(top = 6.dp, end = 4.dp)
                                                    .minimumInteractiveComponentSize()
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .testTag("entry_reg_date_calendar_button")
                                            ) {
                                                Icon(
                                                    imageVector = DolilIcons.Calendar,
                                                    contentDescription = DolilStrings.regDateLabel(language),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        if (regDateError) {
                                            Text(
                                                text = DolilStrings.validationMessage(
                                                    ValidationErrorCode.INVALID_REGISTRATION_DATE,
                                                    language
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(
                                                    start = DolilSpacing.Space4,
                                                    top = DolilSpacing.Space1
                                                )
                                            )
                                        }
                                    }

                                    OfficeDropdownField(
                                        language = language,
                                        selectedOfficeName = officeName,
                                        onOfficeSelected = { id, name ->
                                            officeId = id
                                            officeName = name
                                            clearError(ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE)
                                        }
                                    )
                                }
                        }

                        1 -> item {
                                FormSectionCard(
                                    title = DolilStrings.secGrantors(language),
                                    subtitle = if (language == AppLanguage.BN) {
                                        "একাধিক দাতা যোগ করা যাবে"
                                    } else {
                                        "Add one or more grantors / sellers"
                                    },
                                    icon = DolilIcons.Person
                                ) {
                                    grantors.forEachIndexed { index, grantor ->
                                        PartyEditorCard(
                                            index = index,
                                            isGrantor = true,
                                            party = grantor,
                                            language = language,
                                            canRemove = grantors.size > 1,
                                            isNameError = index == 0 && ValidationErrorCode.REQUIRED_GRANTOR_NAME in combinedErrors,
                                            onUpdate = { updated ->
                                                val nextList = grantors.toMutableList().also { it[index] = updated }
                                                grantors = nextList
                                                if (nextList.any { it.fullName.isNotBlank() }) {
                                                    clearError(ValidationErrorCode.REQUIRED_GRANTOR_NAME)
                                                }
                                            },
                                            onRemove = {
                                                if (grantors.size > 1) {
                                                    grantors = grantors.toMutableList().also { it.removeAt(index) }
                                                }
                                            }
                                        )
                                    }
                                    DolilSecondaryButton(
                                        text = dolilStr(R.string.btn_add_grantor),
                                        onClick = { grantors = grantors + PartyInfo() },
                                        testTag = "add_grantor_button",
                                        icon = DolilIcons.Add,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                        }

                        2 -> item {
                                FormSectionCard(
                                    title = DolilStrings.secGrantees(language),
                                    subtitle = if (language == AppLanguage.BN) {
                                        "একাধিক গ্রহীতা যোগ করা যাবে"
                                    } else {
                                        "Add one or more grantees / buyers"
                                    },
                                    icon = DolilIcons.Group
                                ) {
                                    grantees.forEachIndexed { index, grantee ->
                                        PartyEditorCard(
                                            index = index,
                                            isGrantor = false,
                                            party = grantee,
                                            language = language,
                                            canRemove = grantees.size > 1,
                                            isNameError = index == 0 && ValidationErrorCode.REQUIRED_GRANTEE_NAME in combinedErrors,
                                            onUpdate = { updated ->
                                                val nextList = grantees.toMutableList().also { it[index] = updated }
                                                grantees = nextList
                                                if (nextList.any { it.fullName.isNotBlank() }) {
                                                    clearError(ValidationErrorCode.REQUIRED_GRANTEE_NAME)
                                                }
                                            },
                                            onRemove = {
                                                if (grantees.size > 1) {
                                                    grantees = grantees.toMutableList().also { it.removeAt(index) }
                                                }
                                            }
                                        )
                                    }
                                    DolilSecondaryButton(
                                        text = dolilStr(R.string.btn_add_grantee),
                                        onClick = { grantees = grantees + PartyInfo() },
                                        testTag = "add_grantee_button",
                                        icon = DolilIcons.Add,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                        }

                        3 -> item {
                                FormSectionCard(
                                    title = DolilStrings.secLocation(language),
                                    subtitle = if (language == AppLanguage.BN) {
                                        "জেলা → উপজেলা → মৌজা অনুযায়ী ইউনিয়ন ও জে.এল. নম্বর স্বয়ংক্রিয়ভাবে পূরণ হবে"
                                    } else {
                                        "Select District → Upazila → Mouza (auto-populates Union & J.L.)"
                                    },
                                    icon = DolilIcons.Location
                                ) {
                                    CascadingLocationFields(
                                        language = language,
                                        districtName = districtName,
                                        upazilaName = upazilaName,
                                        unionName = unionName,
                                        mouzaName = mouzaName,
                                        jlNumber = jlNumber,
                                        validationErrors = combinedErrors,
                                        onDistrictChange = { newDistrict ->
                                            districtName = newDistrict
                                            upazilaName = ""
                                            unionName = ""
                                            mouzaName = ""
                                            jlNumber = ""
                                            if (newDistrict.isNotBlank()) {
                                                clearError(ValidationErrorCode.REQUIRED_DISTRICT)
                                            }
                                        },
                                        onUpazilaChange = { newUpazila ->
                                            upazilaName = newUpazila
                                            unionName = ""
                                            mouzaName = ""
                                            jlNumber = ""
                                            if (newUpazila.isNotBlank()) {
                                                clearError(ValidationErrorCode.REQUIRED_UPAZILA)
                                            }
                                        },
                                        onUnionChange = { unionName = it },
                                        onMouzaChange = { newMouza, autoUnion, autoJl ->
                                            mouzaName = newMouza
                                            if (newMouza.isNotBlank()) {
                                                clearError(ValidationErrorCode.REQUIRED_MOUZA)
                                            }
                                            if (autoUnion.isNotBlank()) unionName = autoUnion
                                            if (autoJl.isNotBlank()) {
                                                jlNumber = autoJl
                                                clearError(ValidationErrorCode.REQUIRED_JL_NUMBER)
                                            }
                                        },
                                        onJlChange = {
                                            jlNumber = it
                                            if (it.isNotBlank()) {
                                                clearError(ValidationErrorCode.REQUIRED_JL_NUMBER)
                                            }
                                        }
                                    )
                                }
                        }

                        4 -> {
                            item {
                                FormSectionCard(
                                        title = DolilStrings.secLand(language),
                                        subtitle = if (language == AppLanguage.BN) {
                                            "একাধিক দাগ/খতিয়ান যোগ করলে মোট শতাংশ স্বয়ংক্রিয়ভাবে হিসাব হবে"
                                        } else {
                                            "Multiple parcels automatically aggregate into total Shotok (Decimal)"
                                        },
                                        icon = DolilIcons.Land
                                    ) {
                                        fun syncParcelValidationState(list: List<LandParcel>) {
                                            if (list.isNotEmpty()) {
                                                clearError(ValidationErrorCode.REQUIRED_PARCEL)
                                            }
                                            if (list.none { it.khatianNumber.isBlank() }) {
                                                clearError(ValidationErrorCode.REQUIRED_KHATIAN)
                                            }
                                            if (list.none { it.dagNumber.isBlank() }) {
                                                clearError(ValidationErrorCode.REQUIRED_DAG)
                                            }
                                            if (list.all { p ->
                                                    val bd = SearchNormalizer.normalizeCode(p.landAreaDecimal)
                                                        .replace(',', '.')
                                                        .toBigDecimalOrNull()
                                                    bd != null && bd > BigDecimal.ZERO
                                                }
                                            ) {
                                                clearError(ValidationErrorCode.INVALID_LAND_AREA)
                                            }
                                        }

                                        parcels.forEachIndexed { index, parcel ->
                                            ParcelEditorCard(
                                                index = index,
                                                parcel = parcel,
                                                language = language,
                                                canRemove = parcels.size > 1,
                                                validationErrors = combinedErrors,
                                                onUpdate = { updated ->
                                                    val nextList = parcels.toMutableList().also { it[index] = updated }
                                                    parcels = nextList
                                                    syncParcelValidationState(nextList)
                                                },
                                                onRemove = {
                                                    if (parcels.size > 1) {
                                                        val nextList = parcels.toMutableList().also { it.removeAt(index) }
                                                        parcels = nextList
                                                        syncParcelValidationState(nextList)
                                                    }
                                                }
                                            )
                                        }

                                        DolilSecondaryButton(
                                            text = dolilStr(R.string.btn_add_parcel),
                                            onClick = {
                                                parcels = parcels + LandParcel()
                                                clearError(ValidationErrorCode.REQUIRED_PARCEL)
                                            },
                                            testTag = "add_parcel_button",
                                            icon = DolilIcons.Add,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                }
                            }

                            item {
                                // Live Area Aggregation Card
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(DolilRadius.Input),
                                    border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("live_total_area_card")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(DolilSpacing.Space3),
                                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = DolilIcons.Calculate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(DolilIconSize.Lg)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = if (language == AppLanguage.BN) {
                                                    "মোট হিসাবকৃত জমি: ${totalShotok.toPlainString()} শতাংশ"
                                                } else {
                                                    "Computed Total Area: ${totalShotok.toPlainString()} Shotok (Decimal)"
                                                },
                                                style = MaterialTheme.typography.titleMedium.editorialNumeric(),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (language == AppLanguage.BN) {
                                                    "≈ ${totalKatha.toPlainString()} কাঠা  •  ${totalSqFt.toPlainString()} বর্গফুট"
                                                } else {
                                                    "≈ ${totalKatha.toPlainString()} Katha  •  ${totalSqFt.toPlainString()} Sq. Ft"
                                                },
                                                style = MaterialTheme.typography.bodySmall.tabularNumerals(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                DolilTonalCard {
                                    DolilOutlinedField(
                                        value = deedNotes,
                                        onValueChange = { deedNotes = it },
                                        label = DolilStrings.notesLabel(language),
                                        singleLine = false,
                                        testTag = "entry_deed_notes"
                                    )
                                }
                            }

                            item {
                                WizardReviewSummaryCard(
                                    language = language,
                                    deedNumber = deedNumber,
                                    deedYearText = deedYearText,
                                    deedType = deedType,
                                    grantors = grantors,
                                    grantees = grantees,
                                    districtName = districtName,
                                    upazilaName = upazilaName,
                                    mouzaName = mouzaName,
                                    jlNumber = jlNumber,
                                    totalShotok = totalShotok.toPlainString(),
                                    parcelCount = parcels.size,
                                    onJumpToStep = { stepIdx ->
                                        haptics.performSubtleTick()
                                        currentStep = stepIdx
                                    }
                                )
                            }
                        }
                    }

                    // Step Navigation & Save Action Footer inside LazyColumn for IME & test scroll compatibility
                    item {
                        Surface(
                            shape = RoundedCornerShape(DolilRadius.Card),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(DolilSpacing.Space3),
                                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (currentStep > 0) {
                                        OutlinedButton(
                                            onClick = {
                                                haptics.performSubtleTick()
                                                localStepErrors = emptyList()
                                                currentStep -= 1
                                            },
                                            shape = RoundedCornerShape(DolilRadius.Button),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .weight(0.9f)
                                                .testTag("wizard_prev_button")
                                        ) {
                                            Icon(
                                                imageVector = DolilIcons.ArrowBack,
                                                contentDescription = null,
                                                modifier = Modifier.size(DolilIconSize.Sm)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = dolilStr(R.string.action_previous),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (currentStep < 4) {
                                        DolilPrimaryButton(
                                            text = dolilStr(R.string.action_next),
                                            onClick = {
                                                val stepErrors = validateWizardStep(
                                                    step = currentStep,
                                                    deedNumber = deedNumber,
                                                    deedYearText = deedYearText,
                                                    registrationDateMillis = registrationDateMillis,
                                                    grantors = grantors,
                                                    grantees = grantees,
                                                    districtName = districtName,
                                                    upazilaName = upazilaName,
                                                    mouzaName = mouzaName,
                                                    jlNumber = jlNumber,
                                                    parcels = parcels
                                                )
                                                if (stepErrors.isEmpty()) {
                                                    localStepErrors = emptyList()
                                                    currentStep += 1
                                                } else {
                                                    localStepErrors = stepErrors
                                                }
                                            },
                                            icon = DolilIcons.ArrowForward,
                                            testTag = "wizard_next_button",
                                            modifier = Modifier.weight(1.25f)
                                        )
                                    } else {
                                        DolilPrimaryButton(
                                            text = DolilStrings.saveDeed(language),
                                            onClick = {
                                                lastRequestedDraft = false
                                                localStepErrors = emptyList()
                                                onSaveDeed(buildCurrentRecord(), false, false)
                                            },
                                            enabled = !isSaving,
                                            loading = isSaving && (savingAsDraft == false || savingAsDraft == null),
                                            icon = DolilIcons.Save,
                                            testTag = "save_submit_button",
                                            modifier = Modifier.weight(1.35f)
                                        )
                                    }
                                }

                                DolilSecondaryButton(
                                    text = DolilStrings.saveDraft(language),
                                    onClick = {
                                        lastRequestedDraft = true
                                        localStepErrors = emptyList()
                                        onSaveDeed(buildCurrentRecord(), true, false)
                                    },
                                    enabled = !isSaving,
                                    loading = isSaving && savingAsDraft == true,
                                    icon = DolilIcons.Draft,
                                    testTag = "save_draft_button",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Spacer(Modifier.height(DolilSpacing.Space4))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localMillisToUtcDatePickerMillis(registrationDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedUtcMillis ->
                            registrationDateMillis = utcDatePickerMillisToLocalNoonMillis(selectedUtcMillis)
                            clearError(ValidationErrorCode.INVALID_REGISTRATION_DATE)
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("date_picker_confirm_button")
                ) {
                    Text(if (language == AppLanguage.BN) "নির্বাচন করুন" else "Select")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("date_picker_cancel_button")
                ) {
                    Text(DolilStrings.cancel(language))
                }
            },
            modifier = Modifier.testTag("registration_date_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    if (showDiscardDialog) {
        DolilConfirmationDialog(
            title = dolilStr(R.string.unsaved_changes_title),
            body = dolilStr(R.string.unsaved_changes_body),
            confirmText = dolilStr(R.string.unsaved_changes_discard),
            dismissText = dolilStr(R.string.unsaved_changes_stay),
            isDestructive = true,
            onConfirm = {
                showDiscardDialog = false
                clearAllErrors()
                onBack()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    if (duplicateCandidateId != null) {
        AlertDialog(
            onDismissRequest = { clearAllErrors() },
            icon = {
                Icon(
                    imageVector = DolilIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = if (language == AppLanguage.BN) "সম্ভাব্য ডুপ্লিকেট দলিল!" else "Duplicate Deed Detected",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = if (language == AppLanguage.BN) {
                        "এই সাব-রেজিস্ট্রি অফিস ও সালে একই দলিল নম্বর ইতিমধ্যেই রয়েছে। আপনি কি আগের দলিলটি দেখতে চান নাকি এটি সংরক্ষণ করতে চান?"
                    } else {
                        "A deed with the same number already exists in this office and year. Would you like to view the existing deed or save anyway?"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onSaveDeed(buildCurrentRecord(), lastRequestedDraft, true) },
                    modifier = Modifier.testTag("duplicate_save_anyway_button")
                ) {
                    Text(if (language == AppLanguage.BN) "তবুও সংরক্ষণ করুন" else "Save Anyway")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                    TextButton(
                        onClick = {
                            clearAllErrors()
                            onViewDuplicateDeed(duplicateCandidateId)
                        },
                        modifier = Modifier.testTag("duplicate_view_existing_button")
                    ) {
                        Text(if (language == AppLanguage.BN) "আগেরটি দেখুন" else "View Existing")
                    }
                    TextButton(onClick = { clearAllErrors() }) {
                        Text(DolilStrings.cancel(language))
                    }
                }
            }
        )
    }
}

@Composable
private fun WizardReviewSummaryCard(
    language: AppLanguage,
    deedNumber: String,
    deedYearText: String,
    deedType: DeedType,
    grantors: List<PartyInfo>,
    grantees: List<PartyInfo>,
    districtName: String,
    upazilaName: String,
    mouzaName: String,
    jlNumber: String,
    totalShotok: String,
    parcelCount: Int,
    onJumpToStep: (Int) -> Unit
) {
    val semantic = MaterialTheme.dolilColors
    Surface(
        shape = RoundedCornerShape(DolilRadius.Card),
        color = semantic.paperSurface,
        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.paperDivider),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("entry_review_summary_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(semantic.accentGold)
            )
            Column(
                modifier = Modifier.padding(DolilSpacing.Space4),
                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    Icon(
                        painter = DolilIcons.deedSealPainter(),
                        contentDescription = null,
                        tint = semantic.onPaperSurface,
                        modifier = Modifier.size(DolilIconSize.Sm)
                    )
                    Text(
                        text = if (language == AppLanguage.BN) "সংরক্ষণের আগে সারসংক্ষেপ" else "Review Before Saving",
                        style = MaterialTheme.typography.titleMedium,
                        color = semantic.onPaperSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                HorizontalDivider(
                    thickness = DolilComponentTokens.HairlineWidth,
                    color = semantic.paperDivider
                )

                val fallbackDash = "—"
                val grantorSummary = grantors.mapNotNull { it.fullName.takeIf(String::isNotBlank) }
                    .joinToString(", ").ifBlank { fallbackDash }
                val granteeSummary = grantees.mapNotNull { it.fullName.takeIf(String::isNotBlank) }
                    .joinToString(", ").ifBlank { fallbackDash }
                val locationSummary = listOf(mouzaName, upazilaName, districtName)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .let { if (jlNumber.isNotBlank()) "$it (JL $jlNumber)" else it }
                    .ifBlank { fallbackDash }

                ReviewSummaryRow(
                    label = DolilStrings.secGeneral(language),
                    value = "${DolilStrings.deedTypeText(deedType, language)} • ${deedNumber.ifBlank { fallbackDash }} ($deedYearText)",
                    editLabel = if (language == AppLanguage.BN) "সম্পাদনা" else "Edit",
                    onEdit = { onJumpToStep(0) }
                )
                ReviewSummaryRow(
                    label = DolilStrings.secGrantors(language),
                    value = grantorSummary,
                    editLabel = if (language == AppLanguage.BN) "সম্পাদনা" else "Edit",
                    onEdit = { onJumpToStep(1) }
                )
                ReviewSummaryRow(
                    label = DolilStrings.secGrantees(language),
                    value = granteeSummary,
                    editLabel = if (language == AppLanguage.BN) "সম্পাদনা" else "Edit",
                    onEdit = { onJumpToStep(2) }
                )
                ReviewSummaryRow(
                    label = DolilStrings.secLocation(language),
                    value = locationSummary,
                    editLabel = if (language == AppLanguage.BN) "সম্পাদনা" else "Edit",
                    onEdit = { onJumpToStep(3) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == AppLanguage.BN) "মোট তফসিল ও জমি" else "Parcels & Total Area",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.onPaperMuted
                    )
                    Text(
                        text = "$parcelCount × $totalShotok ${DolilStrings.shotokUnit(language)}",
                        style = MaterialTheme.typography.labelLarge.tabularNumerals(),
                        color = semantic.onPaperSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewSummaryRow(
    label: String,
    value: String,
    editLabel: String,
    onEdit: () -> Unit
) {
    val semantic = MaterialTheme.dolilColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = semantic.onPaperMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.tabularNumerals(),
                color = semantic.onPaperSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            onClick = onEdit,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = DolilIcons.Edit,
                contentDescription = "$editLabel $label",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(DolilIconSize.Xs)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = editLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficeDropdownField(
    language: AppLanguage,
    selectedOfficeName: String,
    onOfficeSelected: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        DolilOutlinedField(
            value = selectedOfficeName,
            onValueChange = {},
            readOnly = true,
            label = DolilStrings.officeLabel(language),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            testTag = "entry_office_dropdown",
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            BangladeshLocationsCatalog.offices.forEach { option ->
                val display = if (language == AppLanguage.BN) option.bnName else option.enName
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = {
                        onOfficeSelected(option.officeId, display)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PartyEditorCard(
    index: Int,
    isGrantor: Boolean,
    party: PartyInfo,
    language: AppLanguage,
    canRemove: Boolean,
    isNameError: Boolean,
    onUpdate: (PartyInfo) -> Unit,
    onRemove: () -> Unit
) {
    val semantic = MaterialTheme.dolilColors
    val prefix = if (isGrantor) "grantor" else "grantee"
    val errCode = if (isGrantor) {
        ValidationErrorCode.REQUIRED_GRANTOR_NAME
    } else {
        ValidationErrorCode.REQUIRED_GRANTEE_NAME
    }
    Surface(
        shape = RoundedCornerShape(DolilRadius.Input),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(DolilSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isGrantor) {
                        if (language == AppLanguage.BN) "দাতা #${index + 1}" else "Grantor #${index + 1}"
                    } else {
                        if (language == AppLanguage.BN) "গ্রহীতা #${index + 1}" else "Grantee #${index + 1}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (canRemove) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("remove_${prefix}_$index")
                    ) {
                        Icon(
                            imageVector = DolilIcons.Delete,
                            contentDescription = DolilStrings.remove(language),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(DolilIconSize.Sm)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(DolilStrings.remove(language), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            DolilOutlinedField(
                value = party.fullName,
                onValueChange = { onUpdate(party.copy(fullName = it)) },
                label = if (isGrantor) DolilStrings.grantorNameLabel(language) else DolilStrings.granteeNameLabel(language),
                supportingText = if (isNameError) {
                    DolilStrings.validationMessage(errCode, language)
                } else if (isGrantor) {
                    dolilStr(R.string.helper_grantor_name)
                } else {
                    dolilStr(R.string.helper_grantee_name)
                },
                isError = isNameError,
                testTag = "${prefix}_name_$index"
            )
            DolilOutlinedField(
                value = party.fatherName,
                onValueChange = { onUpdate(party.copy(fatherName = it)) },
                label = DolilStrings.fatherNameLabel(language),
                testTag = "${prefix}_father_$index"
            )

            var showOptional by remember(party.id) {
                mutableStateOf(
                    party.motherName.isNotBlank() || party.spouseName.isNotBlank() || party.address.isNotBlank()
                )
            }
            val optionalStateDesc = if (showOptional) dolilStr(R.string.state_expanded) else dolilStr(R.string.state_collapsed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DolilRadius.Input))
                    .minimumInteractiveComponentSize()
                    .semantics { stateDescription = optionalStateDesc }
                    .clickable(role = Role.Button) { showOptional = !showOptional }
                    .padding(horizontal = DolilSpacing.Space1, vertical = 4.dp)
                    .testTag("${prefix}_optional_toggle_$index"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DolilStrings.optionalDetails(language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showOptional) DolilIcons.ExpandLess else DolilIcons.ExpandMore,
                    contentDescription = optionalStateDesc,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(DolilIconSize.Sm)
                )
            }

            AnimatedVisibility(visible = showOptional) {
                Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                    DolilOutlinedField(
                        value = party.motherName,
                        onValueChange = { onUpdate(party.copy(motherName = it)) },
                        label = DolilStrings.motherNameLabel(language),
                        testTag = "${prefix}_mother_$index"
                    )
                    DolilOutlinedField(
                        value = party.spouseName,
                        onValueChange = { onUpdate(party.copy(spouseName = it)) },
                        label = DolilStrings.spouseNameLabel(language),
                        testTag = "${prefix}_spouse_$index"
                    )
                    DolilOutlinedField(
                        value = party.address,
                        onValueChange = { onUpdate(party.copy(address = it)) },
                        label = DolilStrings.addressLabel(language),
                        singleLine = false,
                        testTag = "${prefix}_address_$index"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CascadingLocationFields(
    language: AppLanguage,
    districtName: String,
    upazilaName: String,
    unionName: String,
    mouzaName: String,
    jlNumber: String,
    validationErrors: List<ValidationErrorCode>,
    onDistrictChange: (String) -> Unit,
    onUpazilaChange: (String) -> Unit,
    onUnionChange: (String) -> Unit,
    onMouzaChange: (String, String, String) -> Unit,
    onJlChange: (String) -> Unit
) {
    var districtExpanded by remember { mutableStateOf(false) }
    var upazilaExpanded by remember { mutableStateOf(false) }
    var mouzaExpanded by remember { mutableStateOf(false) }

    val availableDistricts = remember(districtName) {
        val normInput = BangladeshLocationsCatalog.normalizeLocationKey(districtName)
        val exactMatch = BangladeshLocationsCatalog.findDistrict(districtName) != null
        if (normInput.isBlank() || exactMatch) BangladeshLocationsCatalog.districts
        else BangladeshLocationsCatalog.districts.filter {
            BangladeshLocationsCatalog.normalizeLocationKey(it.bnName).contains(normInput) ||
                BangladeshLocationsCatalog.normalizeLocationKey(it.enName).contains(normInput)
        }
    }

    val availableUpazilas = remember(districtName, upazilaName) {
        val base = if (districtName.isBlank()) BangladeshLocationsCatalog.upazilas
        else BangladeshLocationsCatalog.upazilasForDistrict(districtName)
        val normInput = BangladeshLocationsCatalog.normalizeLocationKey(upazilaName)
        val exactMatch = BangladeshLocationsCatalog.findUpazila(upazilaName) != null
        if (normInput.isBlank() || exactMatch) base
        else base.filter {
            BangladeshLocationsCatalog.normalizeLocationKey(it.bnName).contains(normInput) ||
                BangladeshLocationsCatalog.normalizeLocationKey(it.enName).contains(normInput)
        }
    }

    val availableMouzas = remember(districtName, upazilaName, mouzaName) {
        val base = when {
            upazilaName.isNotBlank() -> BangladeshLocationsCatalog.mouzasForUpazila(upazilaName)
            districtName.isNotBlank() -> BangladeshLocationsCatalog.mouzasForDistrict(districtName)
            else -> BangladeshLocationsCatalog.mouzas
        }
        val normInput = BangladeshLocationsCatalog.normalizeLocationKey(mouzaName)
        val exactMatch = base.any {
            BangladeshLocationsCatalog.normalizeLocationKey(it.mouzaBn) == normInput ||
                BangladeshLocationsCatalog.normalizeLocationKey(it.mouzaEn) == normInput
        }
        if (normInput.isBlank() || exactMatch) base
        else base.filter {
            BangladeshLocationsCatalog.normalizeLocationKey(it.mouzaBn).contains(normInput) ||
                BangladeshLocationsCatalog.normalizeLocationKey(it.mouzaEn).contains(normInput) ||
                it.jlNumber.contains(normInput)
        }
    }

    val districtError = ValidationErrorCode.REQUIRED_DISTRICT in validationErrors
    val upazilaError = ValidationErrorCode.REQUIRED_UPAZILA in validationErrors
    val mouzaError = ValidationErrorCode.REQUIRED_MOUZA in validationErrors
    val jlError = ValidationErrorCode.REQUIRED_JL_NUMBER in validationErrors

    ExposedDropdownMenuBox(
        expanded = districtExpanded,
        onExpandedChange = { districtExpanded = !districtExpanded }
    ) {
        DolilOutlinedField(
            value = districtName,
            onValueChange = {
                onDistrictChange(it)
                districtExpanded = true
            },
            label = DolilStrings.districtLabel(language),
            supportingText = if (districtError) DolilStrings.validationMessage(ValidationErrorCode.REQUIRED_DISTRICT, language) else null,
            isError = districtError,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
            testTag = "entry_district",
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = districtExpanded,
            onDismissRequest = { districtExpanded = false }
        ) {
            availableDistricts.forEach { d ->
                val label = if (language == AppLanguage.BN) "${d.bnName} (${d.enName})" else "${d.enName} (${d.bnName})"
                val chosen = if (language == AppLanguage.BN) d.bnName else d.enName
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onDistrictChange(chosen)
                        districtExpanded = false
                    }
                )
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = upazilaExpanded,
        onExpandedChange = { upazilaExpanded = !upazilaExpanded }
    ) {
        DolilOutlinedField(
            value = upazilaName,
            onValueChange = {
                onUpazilaChange(it)
                upazilaExpanded = true
            },
            label = DolilStrings.upazilaLabel(language),
            supportingText = if (upazilaError) DolilStrings.validationMessage(ValidationErrorCode.REQUIRED_UPAZILA, language) else null,
            isError = upazilaError,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = upazilaExpanded) },
            testTag = "entry_upazila",
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = upazilaExpanded,
            onDismissRequest = { upazilaExpanded = false }
        ) {
            availableUpazilas.forEach { u ->
                val primary = if (language == AppLanguage.BN) "${u.bnName} (${u.enName})" else "${u.enName} (${u.bnName})"
                val chosen = if (language == AppLanguage.BN) u.bnName else u.enName
                val circleTag = if (u.isRevenueCircle) {
                    if (language == AppLanguage.BN) "রাজস্ব সার্কেল" else "Revenue Circle"
                } else {
                    null
                }
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(primary)
                            if (circleTag != null) {
                                Text(
                                    text = circleTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onUpazilaChange(chosen)
                        upazilaExpanded = false
                    }
                )
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = mouzaExpanded,
        onExpandedChange = { mouzaExpanded = !mouzaExpanded }
    ) {
        DolilOutlinedField(
            value = mouzaName,
            onValueChange = {
                onMouzaChange(it, "", "")
                mouzaExpanded = true
            },
            label = DolilStrings.mouzaLabel(language),
            supportingText = if (mouzaError) {
                DolilStrings.validationMessage(ValidationErrorCode.REQUIRED_MOUZA, language)
            } else {
                dolilStr(R.string.helper_mouza)
            },
            isError = mouzaError,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mouzaExpanded) },
            testTag = "entry_mouza",
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = mouzaExpanded,
            onDismissRequest = { mouzaExpanded = false }
        ) {
            availableMouzas.forEach { m ->
                val primary = if (language == AppLanguage.BN) {
                    "${m.mouzaBn} (জে.এল. ${m.jlNumber})"
                } else {
                    "${m.mouzaEn} (J.L. ${m.jlNumber})"
                }
                val surveySummary = m.surveyTags.takeIf { it.isNotEmpty() }?.joinToString(" • ")
                val chosenMouza = if (language == AppLanguage.BN) m.mouzaBn else m.mouzaEn
                val chosenUnion = if (language == AppLanguage.BN) m.unionBn else m.unionEn
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(primary, style = MaterialTheme.typography.bodyMedium.tabularNumerals())
                            if (!surveySummary.isNullOrBlank()) {
                                Text(
                                    text = surveySummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onMouzaChange(chosenMouza, chosenUnion, m.jlNumber)
                        mouzaExpanded = false
                    }
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
    ) {
        DolilOutlinedField(
            value = unionName,
            onValueChange = onUnionChange,
            label = DolilStrings.unionLabel(language),
            testTag = "entry_union",
            modifier = Modifier.weight(1.3f)
        )
        DolilOutlinedField(
            value = jlNumber,
            onValueChange = onJlChange,
            label = DolilStrings.jlNumberLabel(language),
            supportingText = if (jlError) DolilStrings.validationMessage(ValidationErrorCode.REQUIRED_JL_NUMBER, language) else null,
            isError = jlError,
            testTag = "entry_jl_number",
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParcelEditorCard(
    index: Int,
    parcel: LandParcel,
    language: AppLanguage,
    canRemove: Boolean,
    validationErrors: List<ValidationErrorCode>,
    onUpdate: (LandParcel) -> Unit,
    onRemove: () -> Unit
) {
    val semantic = MaterialTheme.dolilColors
    val khatianError = parcel.khatianNumber.isBlank() && ValidationErrorCode.REQUIRED_KHATIAN in validationErrors
    val dagError = parcel.dagNumber.isBlank() && ValidationErrorCode.REQUIRED_DAG in validationErrors
    val parsedArea = SearchNormalizer.normalizeCode(parcel.landAreaDecimal)
        .replace(',', '.')
        .toBigDecimalOrNull()
    val areaError = (ValidationErrorCode.INVALID_LAND_AREA in validationErrors) &&
        (parsedArea == null || parsedArea <= BigDecimal.ZERO)

    Surface(
        shape = RoundedCornerShape(DolilRadius.Input),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(DolilSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language == AppLanguage.BN) "তফসিল / জমি #${index + 1}" else "Land Parcel #${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (canRemove) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("remove_parcel_$index")
                    ) {
                        Icon(
                            imageVector = DolilIcons.Delete,
                            contentDescription = DolilStrings.remove(language),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(DolilIconSize.Sm)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(DolilStrings.remove(language), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Text(
                text = DolilStrings.surveyTypeLabel(language),
                style = MaterialTheme.typography.labelMedium
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)
            ) {
                SurveyType.entries.forEach { st ->
                    FilterChip(
                        selected = parcel.surveyType == st,
                        onClick = { onUpdate(parcel.copy(surveyType = st)) },
                        label = { Text(if (language == AppLanguage.BN) st.bnLabel else st.enLabel) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
            ) {
                DolilOutlinedField(
                    value = parcel.khatianNumber,
                    onValueChange = { onUpdate(parcel.copy(khatianNumber = it)) },
                    label = DolilStrings.khatianLabel(language),
                    supportingText = if (khatianError) DolilStrings.validationMessage(ValidationErrorCode.REQUIRED_KHATIAN, language) else null,
                    isError = khatianError,
                    testTag = "parcel_khatian_$index",
                    modifier = Modifier.weight(1f)
                )
                DolilOutlinedField(
                    value = parcel.dagNumber,
                    onValueChange = { onUpdate(parcel.copy(dagNumber = it)) },
                    label = DolilStrings.dagLabel(language),
                    supportingText = if (dagError) {
                        DolilStrings.validationMessage(ValidationErrorCode.REQUIRED_DAG, language)
                    } else {
                        dolilStr(R.string.helper_dag)
                    },
                    isError = dagError,
                    testTag = "parcel_dag_$index",
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = DolilStrings.landClassLabel(language),
                style = MaterialTheme.typography.labelMedium
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)
            ) {
                LandClass.entries.forEach { lc ->
                    FilterChip(
                        selected = parcel.landClass == lc,
                        onClick = { onUpdate(parcel.copy(landClass = lc)) },
                        label = { Text(DolilStrings.landClassText(lc, language)) }
                    )
                }
            }

            DolilOutlinedField(
                value = parcel.landAreaDecimal,
                onValueChange = { onUpdate(parcel.copy(landAreaDecimal = it)) },
                label = "${DolilStrings.landAreaLabel(language)} (${DolilStrings.landUnitText(parcel.landUnit, language)})",
                supportingText = if (areaError) {
                    DolilStrings.validationMessage(ValidationErrorCode.INVALID_LAND_AREA, language)
                } else {
                    dolilStr(R.string.helper_land_area)
                },
                isError = areaError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                testTag = "parcel_area_$index"
            )

            Text(
                text = DolilStrings.landUnitLabel(language),
                style = MaterialTheme.typography.labelMedium
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)
            ) {
                LandUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = parcel.landUnit == unit,
                        onClick = { onUpdate(parcel.copy(landUnit = unit)) },
                        label = { Text(DolilStrings.landUnitText(unit, language)) },
                        modifier = Modifier.testTag("parcel_unit_${index}_${unit.name}")
                    )
                }
            }

            var showOptional by remember(parcel.id) {
                mutableStateOf(parcel.boundaryDescription.isNotBlank())
            }
            val optionalStateDesc = if (showOptional) dolilStr(R.string.state_expanded) else dolilStr(R.string.state_collapsed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DolilRadius.Input))
                    .minimumInteractiveComponentSize()
                    .semantics { stateDescription = optionalStateDesc }
                    .clickable(role = Role.Button) { showOptional = !showOptional }
                    .padding(horizontal = DolilSpacing.Space1, vertical = 4.dp)
                    .testTag("parcel_optional_toggle_$index"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DolilStrings.optionalDetails(language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showOptional) DolilIcons.ExpandLess else DolilIcons.ExpandMore,
                    contentDescription = optionalStateDesc,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(DolilIconSize.Sm)
                )
            }

            AnimatedVisibility(visible = showOptional) {
                DolilOutlinedField(
                    value = parcel.boundaryDescription,
                    onValueChange = { onUpdate(parcel.copy(boundaryDescription = it)) },
                    label = DolilStrings.boundaryLabel(language),
                    singleLine = false,
                    testTag = "parcel_boundary_$index"
                )
            }
        }
    }
}

internal fun localMillisToUtcDatePickerMillis(localMillis: Long): Long {
    val localCal = Calendar.getInstance().apply {
        timeInMillis = localMillis
    }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localCal.get(Calendar.YEAR),
            localCal.get(Calendar.MONTH),
            localCal.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

internal fun utcDatePickerMillisToLocalNoonMillis(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    return Calendar.getInstance().apply {
        clear()
        set(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH),
            12,
            0,
            0
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

