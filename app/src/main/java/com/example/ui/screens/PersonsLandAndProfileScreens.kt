package com.example.ui.screens

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.core.config.AppVersionInfo
import com.example.core.config.CredentialReadinessReport
import com.example.core.config.ReadinessState
import com.example.core.designsystem.DolilComponentTokens
import com.example.core.designsystem.DolilConfirmationDialog
import com.example.core.designsystem.DolilEmptyState
import com.example.core.designsystem.DolilIcons
import com.example.core.designsystem.DolilOutlinedDestructiveButton
import com.example.core.designsystem.DolilOutlinedField
import com.example.core.designsystem.DolilPrimaryButton
import com.example.core.designsystem.DolilRadius
import com.example.core.designsystem.DolilSearchField
import com.example.core.designsystem.DolilSecondaryButton
import com.example.core.designsystem.DolilSettingsRow
import com.example.core.designsystem.DolilSpacing
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.DolilTopBar
import com.example.core.designsystem.FormSectionCard
import com.example.core.designsystem.dolilColors
import com.example.core.designsystem.dolilStr
import com.example.core.designsystem.performSubtleTick
import com.example.core.designsystem.tabularNumerals
import com.example.core.security.AuthManager.findActivity
import com.example.core.security.RolePermissions
import com.example.data.drive.DriveBackupSummary
import com.example.data.drive.DriveDeviceRecord
import com.example.data.local.BangladeshLocationsCatalog
import com.example.data.local.DriveLinkState
import com.example.data.local.DriveSyncStatus
import com.example.data.local.LocalUserPreferences
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.LandParcel
import com.example.domain.model.PartyInfo
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.AccountDriveCleanupChoice
import com.example.domain.search.SearchNormalizer
import java.math.RoundingMode

private data class DirectoryPersonItem(
    val party: PartyInfo,
    val isGrantor: Boolean,
    val roleLabel: String,
    val deedId: String,
    val deedSummary: String
)

private data class DirectoryParcelItem(
    val parcel: LandParcel,
    val deed: DeedRecord
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsDirectoryScreen(
    language: AppLanguage,
    deedsState: UiState<List<DeedRecord>>,
    onOpenDeedDetail: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var query by remember { mutableStateOf("") }
    val semantic = MaterialTheme.dolilColors

    val deeds = (deedsState as? UiState.Success)?.data.orEmpty()
        .filter { it.status != DeedStatus.SOFT_DELETED }

    val people = remember(deeds, query, language) {
        val norm = SearchNormalizer.normalizeText(query)
        val items = buildList {
            deeds.forEach { d ->
                val deedTitle = "${DolilStrings.deedNumberFormatted(d.deedNumber, d.deedYear, language)} • ${d.mouzaName}"
                d.grantors.filter { it.fullName.isNotBlank() }.forEach { g ->
                    add(
                        DirectoryPersonItem(
                            party = g,
                            isGrantor = true,
                            roleLabel = if (language == AppLanguage.BN) "দাতা" else "Grantor",
                            deedId = d.id,
                            deedSummary = deedTitle
                        )
                    )
                }
                d.grantees.filter { it.fullName.isNotBlank() }.forEach { g ->
                    add(
                        DirectoryPersonItem(
                            party = g,
                            isGrantor = false,
                            roleLabel = if (language == AppLanguage.BN) "গ্রহীতা" else "Grantee",
                            deedId = d.id,
                            deedSummary = deedTitle
                        )
                    )
                }
            }
        }
        if (norm.isBlank()) items
        else items.filter { item ->
            SearchNormalizer.normalizePersonName(item.party.fullName).contains(norm) ||
                SearchNormalizer.normalizePersonName(item.party.fatherName).contains(norm) ||
                SearchNormalizer.normalizePersonName(item.party.motherName).contains(norm)
        }
    }

    Scaffold(
        topBar = {
            DolilTopBar(
                title = DolilStrings.personInfo(language),
                subtitle = if (language == AppLanguage.BN) "দাতা ও গ্রহীতা তালিকা" else "Grantors & Grantees Directory",
                onBack = onBack
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
                    .testTag("persons_directory_screen")
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = DolilSpacing.Space4,
                        end = DolilSpacing.Space4,
                        top = DolilSpacing.Space3,
                        bottom = DolilSpacing.Space2
                    )
                ) {
                    DolilSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = if (language == AppLanguage.BN) "ব্যক্তির নাম বা পিতার নাম দিয়ে খুঁজুন" else "Search person or father's name",
                        testTag = "persons_search_input"
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = DolilSpacing.Space4,
                        end = DolilSpacing.Space4,
                        bottom = DolilSpacing.Space6
                    ),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
                ) {
                    item {
                        Surface(
                            color = semantic.infoContainer,
                            shape = RoundedCornerShape(DolilRadius.Input),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(DolilSpacing.Space3),
                                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = DolilIcons.Info,
                                    contentDescription = null,
                                    tint = semantic.onInfoContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (language == AppLanguage.BN) {
                                        "সতর্কতা: একই নাম থাকা মানেই একই ব্যক্তি নয়—দলিল নম্বর ও পিতা/মাতার নাম যাচাই করুন।"
                                    } else {
                                        "Notice: Similar names across deeds do not guarantee the same individual; verify parent names and deed number."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.onInfoContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    if (people.isEmpty()) {
                        item {
                            DolilEmptyState(
                                icon = DolilIcons.People,
                                title = if (language == AppLanguage.BN) "কোনো ব্যক্তির তথ্য নেই" else "No Persons Found",
                                subtitle = if (language == AppLanguage.BN) {
                                    "অনুমোদিত বা আপনার তৈরি দলিল থেকে ব্যক্তিদের তালিকা প্রদর্শিত হবে।"
                                } else {
                                    "Grantors and grantees from authorized deeds will appear here."
                                }
                            )
                        }
                    } else {
                        items(people, key = { "${it.deedId}_${it.party.id}_${it.roleLabel}" }) { item ->
                            val initialChar = item.party.fullName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                            val avatarBg = if (item.isGrantor) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                            val avatarFg = if (item.isGrantor) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }

                            Surface(
                                shape = RoundedCornerShape(DolilRadius.Card),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(DolilRadius.Card))
                                    .clickable(role = Role.Button) { onOpenDeedDetail(item.deedId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(DolilSpacing.Space4),
                                    horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(avatarBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initialChar,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = avatarFg,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.party.fullName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = avatarBg,
                                                shape = RoundedCornerShape(DolilComponentTokens.BadgeCorner)
                                            ) {
                                                Text(
                                                    text = item.roleLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = avatarFg,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (item.party.fatherName.isNotBlank()) {
                                            Text(
                                                text = "${DolilStrings.fatherNameLabel(language)}: ${item.party.fatherName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (item.party.address.isNotBlank()) {
                                            Text(
                                                text = item.party.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = item.deedSummary,
                                            style = MaterialTheme.typography.labelMedium.tabularNumerals(),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LandMouzaDirectoryScreen(
    language: AppLanguage,
    deedsState: UiState<List<DeedRecord>>,
    onOpenDeedDetail: (String) -> Unit,
    onCreateDeedFromCatalog: (district: String, upazila: String, mouza: String, jlNumber: String) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    remember(context) {
        BangladeshLocationsCatalog.ensureLoaded(context)
        true
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Saved Deed Parcels, 1 = Narayanganj Catalog
    var selectedSubdivisionCode by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val semantic = MaterialTheme.dolilColors

    val deeds = (deedsState as? UiState.Success)?.data.orEmpty()
        .filter { it.status != DeedStatus.SOFT_DELETED }

    val parcelItems = remember(deeds, query) {
        val norm = SearchNormalizer.normalizeText(query)
        val compact = SearchNormalizer.normalizeCode(query)
        val all = deeds.flatMap { deed ->
            deed.parcels.map { parcel -> DirectoryParcelItem(parcel, deed) }
        }
        if (norm.isBlank()) all
        else all.filter { item ->
            SearchNormalizer.normalizeText(item.deed.mouzaName).contains(norm) ||
                SearchNormalizer.normalizeCode(item.deed.jlNumber).contains(compact) ||
                SearchNormalizer.normalizeCode(item.parcel.dagNumber).contains(compact) ||
                SearchNormalizer.normalizeCode(item.parcel.khatianNumber).contains(compact)
        }
    }

    val narayanganjSubdivisions = remember { BangladeshLocationsCatalog.narayanganjSubdivisions() }
    val subdivisionByCode = remember(narayanganjSubdivisions) {
        narayanganjSubdivisions.associateBy { it.code }
    }
    val catalogMouzas = remember(selectedSubdivisionCode, query, narayanganjSubdivisions) {
        val base = BangladeshLocationsCatalog.narayanganjMouzas()
            .filter { selectedSubdivisionCode == null || it.upazilaCode == selectedSubdivisionCode }
        val norm = BangladeshLocationsCatalog.normalizeLocationKey(query)
        val compact = SearchNormalizer.normalizeCode(query)
        if (norm.isBlank()) {
            base
        } else {
            base.filter { m ->
                val sub = subdivisionByCode[m.upazilaCode]
                BangladeshLocationsCatalog.normalizeLocationKey(m.mouzaBn).contains(norm) ||
                    BangladeshLocationsCatalog.normalizeLocationKey(m.mouzaEn).contains(norm) ||
                    SearchNormalizer.normalizeCode(m.jlNumber).contains(compact) ||
                    (sub != null && (
                        BangladeshLocationsCatalog.normalizeLocationKey(sub.bnName).contains(norm) ||
                            BangladeshLocationsCatalog.normalizeLocationKey(sub.enName).contains(norm)
                        ))
            }
        }
    }

    Scaffold(
        topBar = {
            DolilTopBar(
                title = DolilStrings.landInfo(language),
                subtitle = if (language == AppLanguage.BN) {
                    "মৌজা, খতিয়ান, দাগ ও নারায়ণগঞ্জ সূচি"
                } else {
                    "Mouza, Khatian & Narayanganj Directory"
                },
                onBack = onBack
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
                    .testTag("land_directory_screen")
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = DolilSpacing.Space4,
                        end = DolilSpacing.Space4,
                        top = DolilSpacing.Space3,
                        bottom = DolilSpacing.Space2
                    ),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("land_tab_parcels")
                        ) {
                            Text(
                                text = if (language == AppLanguage.BN) "সংরক্ষিত জমি" else "Saved Parcels",
                                maxLines = 1
                            )
                        }
                        SegmentedButton(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("land_tab_catalog")
                        ) {
                            Text(
                                text = if (language == AppLanguage.BN) "নারায়ণগঞ্জ মৌজা" else "Narayanganj Catalog",
                                maxLines = 1
                            )
                        }
                    }

                    DolilSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = if (selectedTab == 0) {
                            if (language == AppLanguage.BN) "মৌজা, জে.এল., দাগ বা খতিয়ান লিখুন" else "Filter by Mouza, JL, Dag, or Khatian"
                        } else {
                            if (language == AppLanguage.BN) "মৌজা, জে.এল. বা উপজেলা/সার্কেল খুঁজুন" else "Search Mouza, JL, or Upazila/Circle"
                        },
                        testTag = "land_search_input"
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("land_directory_list"),
                    contentPadding = PaddingValues(
                        start = DolilSpacing.Space4,
                        end = DolilSpacing.Space4,
                        bottom = DolilSpacing.Space6
                    ),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
                ) {
                    if (selectedTab == 0) {
                        if (parcelItems.isEmpty()) {
                            item {
                                DolilEmptyState(
                                    icon = DolilIcons.Land,
                                    title = if (language == AppLanguage.BN) "কোনো জমির তথ্য নেই" else "No Land Parcels Found",
                                    subtitle = if (language == AppLanguage.BN) {
                                        "দলিল থেকে জমির তফসিল ও মৌজা তথ্য এখানে প্রদর্শিত হবে।"
                                    } else {
                                        "Land schedules and Mouza/Dag details from authorized deeds appear here."
                                    }
                                )
                            }
                        } else {
                            items(parcelItems, key = { "${it.deed.id}_${it.parcel.id}" }) { item ->
                                Surface(
                                    shape = RoundedCornerShape(DolilRadius.Card),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(DolilRadius.Card))
                                        .clickable(role = Role.Button) { onOpenDeedDetail(item.deed.id) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(DolilSpacing.Space4),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${item.deed.mouzaName} (JL ${item.deed.jlNumber.ifBlank { "-" }})",
                                                style = MaterialTheme.typography.titleMedium.tabularNumerals(),
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${item.parcel.areaAsShotok().setScale(2, RoundingMode.HALF_UP)} ${DolilStrings.shotokUnit(language)}",
                                                style = MaterialTheme.typography.labelLarge.tabularNumerals(),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "${item.parcel.surveyType.code} • ${DolilStrings.khatianLabel(language)}: ${item.parcel.khatianNumber.ifBlank { "-" }} • ${DolilStrings.dagLabel(language)}: ${item.parcel.dagNumber.ifBlank { "-" }}",
                                            style = MaterialTheme.typography.bodyMedium.tabularNumerals()
                                        )
                                        Text(
                                            text = "${DolilStrings.landClassText(item.parcel.landClass, language)} • ${DolilStrings.deedNumberFormatted(item.deed.deedNumber, item.deed.deedYear, language)}",
                                            style = MaterialTheme.typography.bodySmall.tabularNumerals(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Surface(
                                color = semantic.infoContainer,
                                shape = RoundedCornerShape(DolilRadius.Input),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(DolilSpacing.Space3),
                                    horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = DolilIcons.Info,
                                        contentDescription = null,
                                        tint = semantic.onInfoContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = BangladeshLocationsCatalog.narayanganjSourceNote(language),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.onInfoContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                            ) {
                                FilterChip(
                                    selected = selectedSubdivisionCode == null,
                                    onClick = { selectedSubdivisionCode = null },
                                    label = {
                                        Text(
                                            if (language == AppLanguage.BN) "সকল (${narayanganjSubdivisions.size})"
                                            else "All (${narayanganjSubdivisions.size})"
                                        )
                                    },
                                    modifier = Modifier.testTag("land_subdivision_all")
                                )
                                narayanganjSubdivisions.forEach { sub ->
                                    val subLabel = if (language == AppLanguage.BN) sub.bnName else sub.enName
                                    FilterChip(
                                        selected = selectedSubdivisionCode == sub.code,
                                        onClick = {
                                            selectedSubdivisionCode =
                                                if (selectedSubdivisionCode == sub.code) null else sub.code
                                        },
                                        label = { Text(subLabel) },
                                        modifier = Modifier.testTag("land_subdivision_${sub.code}")
                                    )
                                }
                            }
                        }
                        if (catalogMouzas.isEmpty()) {
                            item {
                                val selectedSub = selectedSubdivisionCode?.let { subdivisionByCode[it] }
                                DolilEmptyState(
                                    icon = DolilIcons.Location,
                                    title = if (language == AppLanguage.BN) "এই ফিল্টারে কোনো মৌজা পাওয়া যায়নি" else "No Mouzas Match Filter",
                                    subtitle = if (selectedSub != null) {
                                        if (language == AppLanguage.BN) {
                                            "${selectedSub.bnName}-এর মৌজা তালিকা সরকারি API-তে এখনো উন্মুক্ত হয়নি।"
                                        } else {
                                            "Mouza list for ${selectedSub.enName} was not exposed by the public API."
                                        }
                                    } else {
                                        if (language == AppLanguage.BN) {
                                            "অনুসন্ধান শব্দ বা উপজেলা পরিবর্তন করে দেখুন।"
                                        } else {
                                            "Try adjusting your search query or subdivision filter."
                                        }
                                    }
                                )
                            }
                        } else {
                            items(catalogMouzas, key = { it.code }) { mouza ->
                                val sub = subdivisionByCode[mouza.upazilaCode]
                                val nrgDistrict = BangladeshLocationsCatalog.findDistrict("NRG")
                                val districtLabel = if (language == AppLanguage.BN) {
                                    nrgDistrict?.bnName ?: "নারায়ণগঞ্জ"
                                } else {
                                    nrgDistrict?.enName ?: "Narayanganj"
                                }
                                val upazilaLabel = if (language == AppLanguage.BN) {
                                    sub?.bnName ?: mouza.unionBn
                                } else {
                                    sub?.enName ?: mouza.unionEn
                                }
                                val mouzaLabel = if (language == AppLanguage.BN) mouza.mouzaBn else mouza.mouzaEn

                                Surface(
                                    shape = RoundedCornerShape(DolilRadius.Card),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(DolilRadius.Card))
                                        .testTag("catalog_mouza_card_${mouza.code}")
                                        .clickable {
                                            onCreateDeedFromCatalog(
                                                districtLabel,
                                                upazilaLabel,
                                                mouzaLabel,
                                                mouza.jlNumber
                                            )
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(DolilSpacing.Space4),
                                        verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (language == AppLanguage.BN) {
                                                        "${mouza.mouzaBn} (${mouza.mouzaEn})"
                                                    } else {
                                                        "${mouza.mouzaEn} (${mouza.mouzaBn})"
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = upazilaLabel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(DolilComponentTokens.BadgeCorner)
                                            ) {
                                                Text(
                                                    text = "JL ${mouza.jlNumber.ifBlank { "-" }}",
                                                    style = MaterialTheme.typography.labelMedium.tabularNumerals(),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space1),
                                            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)
                                        ) {
                                            if (sub != null) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    shape = RoundedCornerShape(DolilComponentTokens.BadgeCorner)
                                                ) {
                                                    Text(
                                                        text = if (sub.isRevenueCircle) {
                                                            if (language == AppLanguage.BN) "রাজস্ব সার্কেল" else "Revenue Circle"
                                                        } else {
                                                            if (language == AppLanguage.BN) "উপজেলা" else "Upazila"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            mouza.surveyTags.forEach { tag ->
                                                Surface(
                                                    color = semantic.infoContainer,
                                                    shape = RoundedCornerShape(DolilComponentTokens.BadgeCorner)
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = semantic.onInfoContainer,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = DolilIcons.AddCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (language == AppLanguage.BN) {
                                                    "এই মৌজায় নতুন দলিল শুরু করুন"
                                                } else {
                                                    "Start New Deed in this Mouza"
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    language: AppLanguage,
    preferences: LocalUserPreferences,
    userProfile: UserProfile,
    pendingSyncCount: Int,
    adminUsers: List<UserProfile>,
    onUpdateProfileNamePhone: (String, String) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onSyncNow: () -> Unit,
    onAssignUserRole: (targetUid: String, role: UserRole, officeId: String, officeName: String) -> Unit,
    onSignOut: () -> Unit,
    isGuestMode: Boolean = false,
    onReplayProductTour: () -> Unit = {},
    onOpenUserGuide: () -> Unit = {},
    onExitGuestToSignIn: () -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    driveLinkState: DriveLinkState = DriveLinkState(),
    isDriveBusy: Boolean = false,
    legacyDeedCount: Int = 0,
    customGuestDeedCount: Int = 0,
    availableBackups: List<DriveBackupSummary> = emptyList(),
    isBackupsLoading: Boolean = false,
    connectedDevices: List<DriveDeviceRecord> = emptyList(),
    isDevicesLoading: Boolean = false,
    readinessReport: CredentialReadinessReport? = null,
    onConnectDrive: () -> Unit = {},
    onSwitchDriveAccount: () -> Unit = {},
    onDisconnectDrive: () -> Unit = {},
    onRevokeDriveAccess: () -> Unit = {},
    onToggleAutoSync: (Boolean) -> Unit = {},
    onEnableEncryption: (String) -> Unit = {},
    onDisableEncryption: () -> Unit = {},
    onLoadBackups: () -> Unit = {},
    onRestoreBackup: (DriveBackupSummary, String?) -> Unit = { _, _ -> },
    onLoadDevices: () -> Unit = {},
    onRetireDevice: (String) -> Unit = {},
    onExportPortableJson: () -> Unit = {},
    onImportPortableJson: () -> Unit = {},
    onImportLegacyDeeds: () -> Unit = {},
    onImportCustomGuestDeeds: () -> Unit = {},
    onResendVerificationEmail: () -> Unit = {},
    onRefreshEmailVerification: () -> Unit = {},
    onDeleteAccount: (
        currentPassword: String?,
        activityForGoogleReauth: Activity?,
        credentialManager: CredentialManager?,
        driveCleanupChoice: AccountDriveCleanupChoice,
        onDone: () -> Unit,
        onErr: (String) -> Unit
    ) -> Unit = { _, _, _, _, _, _ -> },
    onRefreshReadiness: () -> Unit = {}
) {
    var fullName by remember(userProfile.fullName) { mutableStateOf(userProfile.fullName) }
    var phone by remember(userProfile.phone) { mutableStateOf(userProfile.phone) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }
    var showEncryptionSetupDialog by remember { mutableStateOf(false) }
    var encryptionPassphraseInput by remember { mutableStateOf("") }
    var encryptionConfirmInput by remember { mutableStateOf("") }
    var encryptionDialogError by remember { mutableStateOf<String?>(null) }
    var showRestoreBackupsDialog by remember { mutableStateOf(false) }
    var backupToRestorePassphrasePrompt by remember { mutableStateOf<DriveBackupSummary?>(null) }
    var restorePassphraseInput by remember { mutableStateOf("") }
    var showDevicesDialog by remember { mutableStateOf(false) }
    var showReadinessDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteAccountPassword by remember { mutableStateOf("") }
    var deleteDriveChoice by remember { mutableStateOf(AccountDriveCleanupChoice.KEEP_DRIVE_BACKUPS) }
    var deleteAccountError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val semantic = MaterialTheme.dolilColors
    val haptics = LocalHapticFeedback.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .testTag("profile_screen"),
            contentPadding = PaddingValues(DolilSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space4)
        ) {
            // SECTION 1: Appearance (দৃশ্যমানতা ও থিম)
            item(key = "section_appearance") {
                FormSectionCard(
                    title = if (language == AppLanguage.BN) "১. দৃশ্যমানতা ও থিম" else "1. Appearance",
                    icon = DolilIcons.Palette,
                    modifier = Modifier.testTag("section_appearance")
                ) {
                    Text(
                        text = if (language == AppLanguage.BN) "থিম মোড" else "Theme Mode",
                        style = MaterialTheme.typography.labelLarge
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            val modeIcon = when (mode) {
                                ThemeMode.LIGHT -> DolilIcons.LightMode
                                ThemeMode.DARK -> DolilIcons.DarkMode
                                ThemeMode.SYSTEM -> DolilIcons.BrightnessAuto
                            }
                            SegmentedButton(
                                selected = preferences.themeMode == mode,
                                onClick = {
                                    haptics.performSubtleTick()
                                    onThemeChange(mode)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = preferences.themeMode == mode) {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("profile_theme_${mode.code}")
                            ) {
                                Text(
                                    text = DolilStrings.themeText(mode, language),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(DolilRadius.Input),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DolilSettingsRow(
                            icon = DolilIcons.Palette,
                            title = DolilStrings.dynamicColorTitle(language),
                            subtitle = DolilStrings.dynamicColorSubtitle(language, supportsDynamicColor),
                            onClick = if (supportsDynamicColor) {
                                {
                                    haptics.performSubtleTick()
                                    onDynamicColorChange(!preferences.useDynamicColor)
                                }
                            } else null,
                            trailingContent = {
                                Switch(
                                    checked = preferences.useDynamicColor && supportsDynamicColor,
                                    enabled = supportsDynamicColor,
                                    onCheckedChange = { enabled ->
                                        haptics.performSubtleTick()
                                        onDynamicColorChange(enabled)
                                    },
                                    modifier = Modifier.testTag("profile_dynamic_color_switch")
                                )
                            }
                        )
                    }
                }
            }

            // SECTION 2: Language & Help (ভাষা ও সহায়তা)
            item(key = "section_language") {
                FormSectionCard(
                    title = if (language == AppLanguage.BN) "২. ভাষা ও নির্দেশিকা" else "2. Language & Guide",
                    icon = DolilIcons.Translate,
                    modifier = Modifier.testTag("section_language_guide")
                ) {
                    Text(
                        text = if (language == AppLanguage.BN) "অ্যাপের ভাষা" else "App Language",
                        style = MaterialTheme.typography.labelLarge
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppLanguage.entries.forEachIndexed { index, lang ->
                            SegmentedButton(
                                selected = preferences.language == lang,
                                onClick = {
                                    haptics.performSubtleTick()
                                    onLanguageChange(lang)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = AppLanguage.entries.size),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = preferences.language == lang) {
                                        Icon(
                                            imageVector = DolilIcons.Translate,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("profile_lang_${lang.code}")
                            ) {
                                Text(
                                    text = lang.displayLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        DolilSecondaryButton(
                            text = dolilStr(R.string.action_replay_tour),
                            onClick = onReplayProductTour,
                            icon = DolilIcons.PlayCircle,
                            testTag = "profile_replay_tour_button",
                            modifier = Modifier.weight(1f)
                        )
                        DolilSecondaryButton(
                            text = dolilStr(R.string.action_view_guide),
                            onClick = onOpenUserGuide,
                            icon = DolilIcons.Help,
                            testTag = "profile_user_guide_button",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // SECTION 3: Account (অ্যাকাউন্ট)
            item(key = "section_account") {
                FormSectionCard(
                    title = if (language == AppLanguage.BN) "৩. অ্যাকাউন্ট" else "3. Account",
                    icon = DolilIcons.Person,
                    modifier = Modifier.testTag("section_account")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = DolilIcons.Person,
                                contentDescription = if (isGuestMode) dolilStr(R.string.guest_profile_name) else userProfile.fullName,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isGuestMode) {
                                    dolilStr(R.string.guest_profile_name)
                                } else {
                                    userProfile.fullName.ifBlank { "User" }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isGuestMode) {
                                    dolilStr(R.string.guest_profile_subtitle)
                                } else {
                                    userProfile.email
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isGuestMode) {
                                Spacer(Modifier.height(4.dp))
                                val providerBadge = if ("password" in userProfile.authProviderIds && !userProfile.isEmailVerified) {
                                    if (language == AppLanguage.BN) "ইমেইল অযাচাইকৃত" else "Email Unverified"
                                } else if (userProfile.isEmailVerified) {
                                    if (language == AppLanguage.BN) "যাচাইকৃত অ্যাকাউন্ট" else "Verified Account"
                                } else {
                                    DolilStrings.roleLabel(userProfile.role, language)
                                }
                                Surface(
                                    color = if ("password" in userProfile.authProviderIds && !userProfile.isEmailVerified) {
                                        semantic.warningContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = providerBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if ("password" in userProfile.authProviderIds && !userProfile.isEmailVerified) {
                                            semantic.onWarningContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isGuestMode) {
                        DolilPrimaryButton(
                            text = dolilStr(R.string.guest_banner_cta),
                            onClick = onExitGuestToSignIn,
                            icon = DolilIcons.SignIn,
                            testTag = "profile_create_account_button",
                            modifier = Modifier.fillMaxWidth()
                        )
                        DolilSecondaryButton(
                            text = if (language == AppLanguage.BN) "Guest Mode থেকে বের হন" else "Exit Guest Mode",
                            onClick = onExitGuestToSignIn,
                            icon = DolilIcons.SignOut,
                            testTag = "profile_exit_guest_button",
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        if ("password" in userProfile.authProviderIds && !userProfile.isEmailVerified) {
                            Surface(
                                color = semantic.warningContainer,
                                shape = RoundedCornerShape(DolilRadius.Input),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(DolilSpacing.Space3),
                                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                                ) {
                                    Text(
                                        text = if (language == AppLanguage.BN) {
                                            "আপনার ইমেইল ঠিকানাটি এখনো যাচাই করা হয়নি। ইনবক্সে পাঠানো লিংকে ক্লিক করুন অথবা নতুন লিংক পাঠান।"
                                        } else {
                                            "Your email address is not verified yet. Check your inbox or resend the verification link."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.onWarningContainer
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                                        OutlinedButton(
                                            onClick = onResendVerificationEmail,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("profile_resend_verification_button")
                                        ) {
                                            Text(if (language == AppLanguage.BN) "পুনরায় পাঠান" else "Resend")
                                        }
                                        TextButton(
                                            onClick = onRefreshEmailVerification,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("profile_refresh_verification_button")
                                        ) {
                                            Text(if (language == AppLanguage.BN) "স্থিতি দেখুন" else "I Verified")
                                        }
                                    }
                                }
                            }
                        }

                        DolilOutlinedField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = DolilStrings.fullNameLabel(language),
                            testTag = "profile_name_input"
                        )
                        DolilOutlinedField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = if (language == AppLanguage.BN) "ফোন নম্বর" else "Phone Number",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            testTag = "profile_phone_input"
                        )
                        DolilPrimaryButton(
                            text = if (language == AppLanguage.BN) "প্রোফাইল সংরক্ষণ" else "Save Profile",
                            onClick = { onUpdateProfileNamePhone(fullName, phone) },
                            testTag = "profile_save_button",
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (legacyDeedCount > 0) {
                            Surface(
                                color = semantic.infoContainer,
                                shape = RoundedCornerShape(DolilRadius.Input),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(DolilSpacing.Space3),
                                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                                ) {
                                    Text(
                                        text = if (language == AppLanguage.BN) {
                                            "ডিভাইসে পুরোনো ${legacyDeedCount}টি দলিল পাওয়া গেছে। এগুলো বর্তমান অ্যাকাউন্টে আনবেন?"
                                        } else {
                                            "Found $legacyDeedCount legacy local deed(s) from before account isolation. Import them into this account?"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.onInfoContainer
                                    )
                                    DolilSecondaryButton(
                                        text = if (language == AppLanguage.BN) "পুরোনো দলিল ইমপোর্ট করুন" else "Import Legacy Deeds",
                                        onClick = onImportLegacyDeeds,
                                        icon = DolilIcons.UploadFile,
                                        testTag = "profile_import_legacy_button",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        if (customGuestDeedCount > 0) {
                            DolilSecondaryButton(
                                text = if (language == AppLanguage.BN) {
                                    "Guest Mode থেকে ${customGuestDeedCount}টি নিজস্ব দলিল আনুন"
                                } else {
                                    "Import $customGuestDeedCount Custom Guest Deed(s)"
                                },
                                onClick = onImportCustomGuestDeeds,
                                icon = DolilIcons.UploadFile,
                                testTag = "profile_import_guest_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                        DolilOutlinedDestructiveButton(
                            text = DolilStrings.signOut(language),
                            onClick = { showSignOutDialog = true },
                            icon = DolilIcons.SignOut,
                            testTag = "profile_sign_out_button",
                            modifier = Modifier.fillMaxWidth()
                        )

                        TextButton(
                            onClick = {
                                deleteAccountError = null
                                deleteAccountPassword = ""
                                deleteDriveChoice = AccountDriveCleanupChoice.KEEP_DRIVE_BACKUPS
                                showDeleteAccountDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("profile_delete_account_button")
                        ) {
                            Icon(
                                imageVector = DolilIcons.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (language == AppLanguage.BN) "অ্যাকাউন্ট স্থায়ীভাবে মুছুন" else "Delete Account Permanently",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // SECTION 4: Cloud Backup & Sync (Personal Google Drive)
            if (!isGuestMode) {
                item(key = "section_cloud_backup") {
                    val lastSyncStr = if (driveLinkState.lastSyncUtcMillis > 0L) {
                        DolilStrings.formatDateTime(driveLinkState.lastSyncUtcMillis, language)
                    } else if (preferences.lastSyncMillis > 0L) {
                        DolilStrings.formatDateTime(preferences.lastSyncMillis, language)
                    } else {
                        if (language == AppLanguage.BN) "এখনো ব্যাকআপ হয়নি" else "No backup yet"
                    }

                    FormSectionCard(
                        title = if (language == AppLanguage.BN) "৪. ক্লাউড ব্যাকআপ ও সিঙ্ক (Google Drive)" else "4. Cloud Backup & Sync (Google Drive)",
                        subtitle = if (language == AppLanguage.BN) {
                            "আপনার ব্যক্তিগত Google Drive-এর 'Dolil Onusondhan Backup' ফোল্ডারে এনক্রিপ্টেড/নিরাপদ কপি"
                        } else {
                            "Syncs exclusively to 'Dolil Onusondhan Backup' in your personal Google Drive"
                        },
                        icon = DolilIcons.Backup,
                        modifier = Modifier.testTag("section_cloud_sync")
                    ) {
                        if (driveLinkState.syncStatus == DriveSyncStatus.AUTHORIZATION_REQUIRED) {
                            Surface(
                                color = semantic.warningContainer,
                                shape = RoundedCornerShape(DolilRadius.Input),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(DolilSpacing.Space3),
                                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                                ) {
                                    Text(
                                        text = if (language == AppLanguage.BN) {
                                            "ব্যাকগ্রাউন্ড সিঙ্ক বিরতিতে আছে: Google Drive-এর অনুমোদন পুনরায় নিশ্চিত করুন।"
                                        } else {
                                            "Sync paused: Google Drive authorization needs user re-consent."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.onWarningContainer
                                    )
                                    DolilSecondaryButton(
                                        text = if (language == AppLanguage.BN) "পুনরায় অনুমোদন দিন" else "Re-authorize Google Drive",
                                        onClick = onConnectDrive,
                                        icon = DolilIcons.Key,
                                        testTag = "profile_reauthorize_drive_button",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(DolilRadius.Input),
                            border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(DolilSpacing.Space3),
                                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)
                            ) {
                                Text(
                                    text = if (driveLinkState.isConnected) {
                                        if (language == AppLanguage.BN) {
                                            "সংযুক্ত Drive: ${driveLinkState.driveEmail.ifBlank { driveLinkState.driveSubjectId.take(10) }}"
                                        } else {
                                            "Connected Drive: ${driveLinkState.driveEmail.ifBlank { driveLinkState.driveSubjectId.take(10) }}"
                                        }
                                    } else {
                                        if (language == AppLanguage.BN) "কোনো Google Drive সংযুক্ত নয়" else "Google Drive not connected"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (language == AppLanguage.BN) {
                                        "স্থিতি: ${driveLinkState.syncStatus.bnLabel} • অপেক্ষমাণ: $pendingSyncCount • সর্বশেষ: $lastSyncStr"
                                    } else {
                                        "Status: ${driveLinkState.syncStatus.enLabel} • Pending: $pendingSyncCount • Last: $lastSyncStr"
                                    },
                                    style = MaterialTheme.typography.bodySmall.tabularNumerals(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                driveLinkState.lastErrorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        if (!driveLinkState.isConnected) {
                            DolilPrimaryButton(
                                text = if (language == AppLanguage.BN) "Google Drive সংযুক্ত করুন" else "Connect Google Drive",
                                onClick = onConnectDrive,
                                icon = DolilIcons.CloudUpload,
                                enabled = !isDriveBusy,
                                testTag = "profile_connect_drive_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                            ) {
                                DolilPrimaryButton(
                                    text = if (isDriveBusy) {
                                        if (language == AppLanguage.BN) "সিঙ্ক হচ্ছে..." else "Syncing..."
                                    } else {
                                        if (language == AppLanguage.BN) "এখনই ব্যাকআপ করুন" else "Back Up Now"
                                    },
                                    onClick = onSyncNow,
                                    icon = DolilIcons.CloudSync,
                                    enabled = !isDriveBusy,
                                    testTag = "profile_sync_now_button",
                                    modifier = Modifier.weight(1f)
                                )
                                DolilSecondaryButton(
                                    text = if (language == AppLanguage.BN) "অ্যাকাউন্ট বদলান" else "Switch Drive",
                                    onClick = onSwitchDriveAccount,
                                    icon = DolilIcons.Sync,
                                    enabled = !isDriveBusy,
                                    testTag = "profile_switch_drive_button",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(DolilRadius.Input),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DolilSettingsRow(
                                icon = DolilIcons.CloudSync,
                                title = if (language == AppLanguage.BN) "স্বয়ংক্রিয় ব্যাকগ্রাউন্ড সিঙ্ক" else "Automatic Background Sync",
                                subtitle = if (language == AppLanguage.BN) {
                                    "পরিবর্তনের পর Wi-Fi/মোবাইল নেটওয়ার্কে ব্যাকআপ শিডিউল হবে"
                                } else {
                                    "Schedules background Drive sync when connected to network"
                                },
                                onClick = {
                                    haptics.performSubtleTick()
                                    onToggleAutoSync(!driveLinkState.autoSyncEnabled)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = driveLinkState.autoSyncEnabled,
                                        onCheckedChange = {
                                            haptics.performSubtleTick()
                                            onToggleAutoSync(it)
                                        },
                                        modifier = Modifier.testTag("profile_auto_sync_switch")
                                    )
                                }
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(DolilRadius.Input),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DolilSettingsRow(
                                icon = if (driveLinkState.encryptionEnabled) DolilIcons.Encrypted else DolilIcons.Unencrypted,
                                title = if (language == AppLanguage.BN) "ক্লায়েন্ট-সাইড AES-256 এনক্রিপশন" else "Client-Side AES-256 Encryption",
                                subtitle = if (language == AppLanguage.BN) {
                                    "পাসফ্রেজ ভুলে গেলে এনক্রিপ্টেড ব্যাকআপ পুনরুদ্ধার সম্ভব নয়"
                                } else {
                                    "Encrypts snapshots with your passphrase (lost passphrase cannot be recovered)"
                                },
                                onClick = {
                                    if (driveLinkState.encryptionEnabled) {
                                        onDisableEncryption()
                                    } else {
                                        encryptionPassphraseInput = ""
                                        encryptionConfirmInput = ""
                                        encryptionDialogError = null
                                        showEncryptionSetupDialog = true
                                    }
                                },
                                trailingContent = {
                                    Switch(
                                        checked = driveLinkState.encryptionEnabled,
                                        onCheckedChange = { enabled ->
                                            if (!enabled) {
                                                onDisableEncryption()
                                            } else {
                                                encryptionPassphraseInput = ""
                                                encryptionConfirmInput = ""
                                                encryptionDialogError = null
                                                showEncryptionSetupDialog = true
                                            }
                                        },
                                        modifier = Modifier.testTag("profile_encryption_switch")
                                    )
                                }
                            )
                        }

                        if (driveLinkState.isConnected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                            ) {
                                DolilSecondaryButton(
                                    text = if (language == AppLanguage.BN) "ব্যাকআপ রিস্টোর" else "Restore Backup",
                                    onClick = {
                                        onLoadBackups()
                                        showRestoreBackupsDialog = true
                                    },
                                    icon = DolilIcons.Restore,
                                    testTag = "profile_restore_backups_button",
                                    modifier = Modifier.weight(1f)
                                )
                                DolilSecondaryButton(
                                    text = if (language == AppLanguage.BN) "ডিভাইস তালিকা" else "Devices",
                                    onClick = {
                                        onLoadDevices()
                                        showDevicesDialog = true
                                    },
                                    icon = DolilIcons.Devices,
                                    testTag = "profile_devices_button",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                        ) {
                            DolilSecondaryButton(
                                text = if (language == AppLanguage.BN) "JSON এক্সপোর্ট" else "Export JSON",
                                onClick = onExportPortableJson,
                                icon = DolilIcons.Download,
                                testTag = "profile_export_json_button",
                                modifier = Modifier.weight(1f)
                            )
                            DolilSecondaryButton(
                                text = if (language == AppLanguage.BN) "JSON ইমপোর্ট" else "Import JSON",
                                onClick = onImportPortableJson,
                                icon = DolilIcons.UploadFile,
                                testTag = "profile_import_json_button",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        DolilSecondaryButton(
                            text = if (language == AppLanguage.BN) "অথেনটিকেশন ও Drive প্রস্তুতি যাচাই" else "Credential & OAuth Status",
                            onClick = {
                                onRefreshReadiness()
                                showReadinessDialog = true
                            },
                            icon = DolilIcons.Security,
                            testTag = "profile_readiness_button",
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (driveLinkState.isConnected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                            ) {
                                OutlinedButton(
                                    onClick = onDisconnectDrive,
                                    enabled = !isDriveBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("profile_disconnect_drive_button")
                                ) {
                                    Text(if (language == AppLanguage.BN) "ডিভাইস বিচ্ছিন্ন" else "Disconnect")
                                }
                                TextButton(
                                    onClick = { showRevokeDialog = true },
                                    enabled = !isDriveBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("profile_revoke_drive_button")
                                ) {
                                    Text(
                                        text = if (language == AppLanguage.BN) "OAuth প্রত্যাহার" else "Revoke Access",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Conditional Admin Panel preserved for Role permission compatibility
            if (!isGuestMode && RolePermissions.canManageRoles(userProfile.role) && adminUsers.isNotEmpty()) {
                item(key = "section_admin_roles") {
                    FormSectionCard(
                        title = if (language == AppLanguage.BN) "ভূমিকা ও অফিস ব্যবস্থাপনা" else "Role & Office Assignment (Admin)",
                        icon = DolilIcons.AdminPanel
                    ) {
                        adminUsers.forEachIndexed { idx, target ->
                            if (idx > 0) HorizontalDivider(color = semantic.divider)
                            val currentOffice = BangladeshLocationsCatalog.offices.firstOrNull {
                                it.officeId == target.officeId
                            } ?: BangladeshLocationsCatalog.offices.first()
                            var officeMenuExpanded by remember(target.userId) { mutableStateOf(false) }

                            Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                                Text(
                                    text = "${target.fullName.ifBlank { target.email }} (${target.email})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                ExposedDropdownMenuBox(
                                    expanded = officeMenuExpanded,
                                    onExpandedChange = { officeMenuExpanded = !officeMenuExpanded }
                                ) {
                                    DolilOutlinedField(
                                        value = if (language == AppLanguage.BN) currentOffice.bnName else currentOffice.enName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = DolilStrings.officeLabel(language),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = officeMenuExpanded) },
                                        testTag = "admin_office_dropdown_${target.userId}",
                                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = officeMenuExpanded,
                                        onDismissRequest = { officeMenuExpanded = false }
                                    ) {
                                        BangladeshLocationsCatalog.offices.forEach { opt ->
                                            val display = if (language == AppLanguage.BN) opt.bnName else opt.enName
                                            DropdownMenuItem(
                                                text = { Text(display) },
                                                onClick = {
                                                    onAssignUserRole(target.userId, target.role, opt.officeId, display)
                                                    officeMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: Application Information (অ্যাপ্লিকেশন তথ্য — Version shown ONLY here)
            item(key = "section_app_info") {
                FormSectionCard(
                    title = if (language == AppLanguage.BN) "৫. অ্যাপ্লিকেশন তথ্য" else "5. Application Information",
                    icon = DolilIcons.Info,
                    modifier = Modifier.testTag("section_app_info")
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(DolilRadius.Input),
                        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(DolilSpacing.Space3),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == AppLanguage.BN) "অ্যাপ সংস্করণ (Version)" else "App Version",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(DolilRadius.Pill)
                            ) {
                                Text(
                                    text = AppVersionInfo.formattedVersion,
                                    style = MaterialTheme.typography.labelLarge.tabularNumerals(),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .testTag("profile_app_version_text")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        DolilSecondaryButton(
                            text = if (language == AppLanguage.BN) "গোপনীয়তা নীতি" else "Privacy Policy",
                            onClick = { showPrivacyDialog = true },
                            icon = DolilIcons.PrivacyTip,
                            testTag = "profile_privacy_button",
                            modifier = Modifier.weight(1f)
                        )
                        DolilSecondaryButton(
                            text = if (language == AppLanguage.BN) "অ্যাপ সম্পর্কে" else "About App",
                            onClick = { showAboutDialog = true },
                            icon = DolilIcons.Info,
                            testTag = "profile_about_button",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // SECTION 6: Developer Identity (নির্মাতার পরিচয়)
            item(key = "section_developer_identity") {
                FormSectionCard(
                    title = if (language == AppLanguage.BN) "৬. নির্মাতার পরিচয়" else "6. Meet the Developer",
                    icon = DolilIcons.Code,
                    modifier = Modifier.testTag("section_developer_identity")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_developer_card"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("profile_developer_monogram")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "JH",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (language == AppLanguage.BN) "জুয়েল হোসাইন" else "Jewel Hossain",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = semantic.successContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (language == AppLanguage.BN) "অ্যাপ নির্মাতা" else "App Developer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.onSuccessContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = if (language == AppLanguage.BN) {
                                    "জমির দলিল ব্যবস্থাপনায় স্বচ্ছতা ও ব্যক্তিগত তথ্যের সুরক্ষার লক্ষ্যে নির্মিত।"
                                } else {
                                    "Crafted for clarity, reliability, and private personal land deed recordkeeping."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(DolilSpacing.Space4))
            }
        }
    }

    if (showSignOutDialog) {
        DolilConfirmationDialog(
            title = DolilStrings.signOutConfirmTitle(language),
            body = DolilStrings.signOutConfirmBody(language),
            confirmText = DolilStrings.signOut(language),
            dismissText = DolilStrings.cancel(language),
            isDestructive = true,
            onConfirm = {
                showSignOutDialog = false
                onSignOut()
            },
            onDismiss = { showSignOutDialog = false }
        )
    }

    if (showRevokeDialog) {
        DolilConfirmationDialog(
            title = if (language == AppLanguage.BN) "Google Drive অনুমতি প্রত্যাহার করবেন?" else "Revoke Google Drive Access?",
            body = if (language == AppLanguage.BN) {
                "এটি অ্যাপের সব সেশন থেকে Google Drive OAuth অনুমতি বাতিল করবে। ভবিষ্যতে সিঙ্ক করতে পুনরায় অনুমতি দিতে হবে। আপনার Drive-এ থাকা ফাইল মুছবে না।"
            } else {
                "This revokes Google Drive OAuth authorization across all sessions. You will need to re-consent before syncing again. Existing Drive files remain untouched."
            },
            confirmText = if (language == AppLanguage.BN) "প্রত্যাহার করুন" else "Revoke Access",
            dismissText = DolilStrings.cancel(language),
            isDestructive = true,
            onConfirm = {
                showRevokeDialog = false
                onRevokeDriveAccess()
            },
            onDismiss = { showRevokeDialog = false }
        )
    }

    if (showEncryptionSetupDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptionSetupDialog = false },
            title = {
                Text(if (language == AppLanguage.BN) "ব্যাকআপ এনক্রিপশন সেটআপ" else "Set Backup Passphrase")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                    Surface(
                        color = semantic.warningContainer,
                        shape = RoundedCornerShape(DolilRadius.Input),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (language == AppLanguage.BN) {
                                "সতর্কতা: পাসফ্রেজ হারিয়ে গেলে বা ভুলে গেলে Google Drive থেকে এনক্রিপ্টেড ব্যাকআপ কোনোভাবেই উদ্ধার করা যাবে না।"
                            } else {
                                "Warning: Passphrases are never stored on our servers or Drive. If forgotten, encrypted backups cannot be recovered."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.onWarningContainer,
                            modifier = Modifier.padding(DolilSpacing.Space2)
                        )
                    }
                    OutlinedTextField(
                        value = encryptionPassphraseInput,
                        onValueChange = { encryptionPassphraseInput = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(if (language == AppLanguage.BN) "পাসফ্রেজ (কমপক্ষে ৬ অক্ষর)" else "Passphrase (min 6 chars)") },
                        modifier = Modifier.fillMaxWidth().testTag("encryption_passphrase_input")
                    )
                    OutlinedTextField(
                        value = encryptionConfirmInput,
                        onValueChange = { encryptionConfirmInput = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(if (language == AppLanguage.BN) "পাসফ্রেজ নিশ্চিত করুন" else "Confirm Passphrase") },
                        modifier = Modifier.fillMaxWidth().testTag("encryption_confirm_input")
                    )
                    encryptionDialogError?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            encryptionPassphraseInput.length < 6 -> {
                                encryptionDialogError = if (language == AppLanguage.BN) "কমপক্ষে ৬ অক্ষরের পাসফ্রেজ দিন।" else "Use at least 6 characters."
                            }
                            encryptionPassphraseInput != encryptionConfirmInput -> {
                                encryptionDialogError = if (language == AppLanguage.BN) "পাসফ্রেজ দুটি মিলছে না।" else "Passphrases do not match."
                            }
                            else -> {
                                val pass = encryptionPassphraseInput
                                showEncryptionSetupDialog = false
                                onEnableEncryption(pass)
                            }
                        }
                    },
                    modifier = Modifier.testTag("encryption_confirm_button")
                ) {
                    Text(if (language == AppLanguage.BN) "এনক্রিপশন চালু করুন" else "Enable Encryption")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptionSetupDialog = false }) {
                    Text(dolilStr(R.string.action_cancel))
                }
            }
        )
    }

    if (showRestoreBackupsDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreBackupsDialog = false },
            title = { Text(if (language == AppLanguage.BN) "Google Drive ব্যাকআপ তালিকা" else "Drive Backup Snapshots") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    if (isBackupsLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (availableBackups.isEmpty()) {
                        Text(
                            text = if (language == AppLanguage.BN) "কোনো ব্যাকআপ স্ন্যাপশট পাওয়া যায়নি।" else "No Drive backup snapshots found yet.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        availableBackups.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(DolilRadius.Input),
                                border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(DolilSpacing.Space2),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "#${item.sequenceNumber} • ${item.timestampUtc.take(19).replace('T', ' ')}",
                                            style = MaterialTheme.typography.labelLarge.tabularNumerals(),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (item.encrypted) {
                                                if (language == AppLanguage.BN) "AES-256 এনক্রিপ্টেড" else "Encrypted (AES-256-GCM)"
                                            } else {
                                                if (language == AppLanguage.BN) "সাধারণ JSON স্ন্যাপশট" else "Standard JSON Snapshot"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        enabled = !isDriveBusy,
                                        onClick = {
                                            if (item.encrypted) {
                                                restorePassphraseInput = ""
                                                backupToRestorePassphrasePrompt = item
                                            } else {
                                                showRestoreBackupsDialog = false
                                                onRestoreBackup(item, null)
                                            }
                                        }
                                    ) {
                                        Text(if (language == AppLanguage.BN) "রিস্টোর" else "Restore")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreBackupsDialog = false }) {
                    Text(dolilStr(R.string.action_ok))
                }
            }
        )
    }

    backupToRestorePassphrasePrompt?.let { targetBackup ->
        AlertDialog(
            onDismissRequest = { backupToRestorePassphrasePrompt = null },
            title = { Text(if (language == AppLanguage.BN) "পাসফ্রেজ লিখুন" else "Enter Backup Passphrase") },
            text = {
                OutlinedTextField(
                    value = restorePassphraseInput,
                    onValueChange = { restorePassphraseInput = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(if (language == AppLanguage.BN) "এনক্রিপশন পাসফ্রেজ" else "Encryption Passphrase") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = restorePassphraseInput
                        backupToRestorePassphrasePrompt = null
                        showRestoreBackupsDialog = false
                        onRestoreBackup(targetBackup, pass)
                    }
                ) {
                    Text(if (language == AppLanguage.BN) "রিস্টোর করুন" else "Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToRestorePassphrasePrompt = null }) {
                    Text(dolilStr(R.string.action_cancel))
                }
            }
        )
    }

    if (showDevicesDialog) {
        AlertDialog(
            onDismissRequest = { showDevicesDialog = false },
            title = { Text(if (language == AppLanguage.BN) "সংযুক্ত ডিভাইসসমূহ" else "Connected Devices") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    if (isDevicesLoading) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (connectedDevices.isEmpty()) {
                        Text(
                            text = if (language == AppLanguage.BN) "কোনো নিবন্ধিত ডিভাইস পাওয়া যায়নি।" else "No registered devices found yet.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        connectedDevices.forEach { dev ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(DolilRadius.Input),
                                border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(DolilSpacing.Space2),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.deviceName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Seq #${dev.lastSequence} • ${if (dev.retired) "Retired" else "Active"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!dev.retired) {
                                        TextButton(
                                            enabled = !isDriveBusy,
                                            onClick = { onRetireDevice(dev.deviceId) }
                                        ) {
                                            Text(if (language == AppLanguage.BN) "অবসর" else "Retire")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicesDialog = false }) {
                    Text(dolilStr(R.string.action_ok))
                }
            }
        )
    }

    if (showReadinessDialog) {
        AlertDialog(
            onDismissRequest = { showReadinessDialog = false },
            title = { Text(if (language == AppLanguage.BN) "কনফিগারেশন ও OAuth স্থিতি" else "Credential & OAuth Status") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    val stages = readinessReport?.stages.orEmpty()
                    stages.forEach { status ->
                        val isReady = status.state == ReadinessState.READY
                        Surface(
                            color = if (isReady) semantic.successContainer else semantic.warningContainer,
                            shape = RoundedCornerShape(DolilRadius.Input),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(DolilSpacing.Space2)) {
                                Text(
                                    text = "${if (language == AppLanguage.BN) status.state.labelBn else status.state.labelEn} • ${if (language == AppLanguage.BN) status.stage.titleBn else status.stage.titleEn}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isReady) semantic.onSuccessContainer else semantic.onWarningContainer
                                )
                                Text(
                                    text = if (language == AppLanguage.BN) status.detailBn else status.detailEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isReady) semantic.onSuccessContainer else semantic.onWarningContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReadinessDialog = false }) {
                    Text(dolilStr(R.string.action_ok))
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        val usesPassword = "password" in userProfile.authProviderIds
        val activity = context.findActivity()
        AlertDialog(
            onDismissRequest = { if (!isDriveBusy) showDeleteAccountDialog = false },
            title = {
                Text(if (language == AppLanguage.BN) "অ্যাকাউন্ট স্থায়ীভাবে মুছুন" else "Delete Account Permanently")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    Text(
                        text = if (language == AppLanguage.BN) {
                            "এই অ্যাকাউন্ট এবং ফোনের স্থানীয় দলিল তথ্য মুছে যাবে। Google Drive-এ থাকা ব্যাকআপের ক্ষেত্রে কী করতে চান তা বেছে নিন:"
                        } else {
                            "This permanently removes your Firebase account and local device records for this user. Choose how to handle your Google Drive backups:"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = !isDriveBusy, role = Role.RadioButton) {
                                deleteDriveChoice = AccountDriveCleanupChoice.KEEP_DRIVE_BACKUPS
                            }
                    ) {
                        RadioButton(
                            selected = deleteDriveChoice == AccountDriveCleanupChoice.KEEP_DRIVE_BACKUPS,
                            enabled = !isDriveBusy,
                            onClick = { deleteDriveChoice = AccountDriveCleanupChoice.KEEP_DRIVE_BACKUPS }
                        )
                        Column {
                            Text(
                                text = if (language == AppLanguage.BN) "Drive ব্যাকআপ রেখে দিন" else "Keep Google Drive Backups",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = if (language == AppLanguage.BN) {
                                    "পুরোনো UID নেইমস্পেস আলাদাভাবে সুরক্ষিত থাকবে; নতুন অ্যাকাউন্টের সাথে মিশবে না।"
                                } else {
                                    "Stays isolated under your old UID namespace; a new Firebase account will not merge automatically."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = !isDriveBusy, role = Role.RadioButton) {
                                deleteDriveChoice = AccountDriveCleanupChoice.DELETE_DRIVE_BACKUPS
                            }
                    ) {
                        RadioButton(
                            selected = deleteDriveChoice == AccountDriveCleanupChoice.DELETE_DRIVE_BACKUPS,
                            enabled = !isDriveBusy,
                            onClick = { deleteDriveChoice = AccountDriveCleanupChoice.DELETE_DRIVE_BACKUPS }
                        )
                        Text(
                            text = if (language == AppLanguage.BN) "Google Drive ব্যাকআপও মুছুন" else "Also Delete Drive Backups",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (usesPassword) {
                        OutlinedTextField(
                            value = deleteAccountPassword,
                            onValueChange = { deleteAccountPassword = it },
                            enabled = !isDriveBusy,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(if (language == AppLanguage.BN) "নিশ্চিত করতে বর্তমান পাসওয়ার্ড দিন" else "Current Password to Re-authenticate") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_account_password_input")
                        )
                    }

                    deleteAccountError?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDriveBusy,
                    onClick = {
                        if (usesPassword && deleteAccountPassword.isBlank()) {
                            deleteAccountError = if (language == AppLanguage.BN) {
                                "অ্যাকাউন্ট মুছতে বর্তমান পাসওয়ার্ড লিখুন।"
                            } else {
                                "Please enter your current password to confirm deletion."
                            }
                            return@TextButton
                        }
                        deleteAccountError = null
                        onDeleteAccount(
                            deleteAccountPassword.takeIf { usesPassword },
                            if (!usesPassword) activity else null,
                            if (!usesPassword) credentialManager else null,
                            deleteDriveChoice,
                            { showDeleteAccountDialog = false },
                            { err -> deleteAccountError = err }
                        )
                    },
                    modifier = Modifier.testTag("delete_account_confirm_button")
                ) {
                    if (isDriveBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (language == AppLanguage.BN) "অ্যাকাউন্ট মুছুন" else "Delete Account",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDriveBusy,
                    onClick = { showDeleteAccountDialog = false }
                ) {
                    Text(dolilStr(R.string.action_cancel))
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(if (language == AppLanguage.BN) "গোপনীয়তা ও আইনগত ঘোষণা" else "Privacy & Legal Notice") },
            text = {
                Text(
                    if (language == AppLanguage.BN) {
                        "দলিল অনুসন্ধান (Dolil Onusondhan) শুধুমাত্র তথ্য সংরক্ষণ ও অনুসন্ধানের সহায়ক অ্যাপ। এটি সরকারি সাব-রেজিস্ট্রি বা এসি ল্যান্ড অফিসের প্রত্যয়িত দলিলের বিকল্প নয়। আপনার দলিল তথ্য ফোনের স্থানীয় Room ডেটাবেসে সংরক্ষিত থাকে এবং শুধুমাত্র আপনার অনুমোদিত ব্যক্তিগত Google Drive (drive.file স্কোপ) ফোল্ডারে ব্যাকআপ হয়। Firebase শুধুমাত্র অ্যাকাউন্ট লগইনের জন্য ব্যবহৃত হয়।"
                    } else {
                        "Dolil Onusondhan is an informational land deed registry tool and does not replace certified Sub-Registry or AC Land office copies. Your deeds stay in your device's isolated Room database and back up exclusively to your personal Google Drive via drive.file scope. Firebase is used strictly for user authentication."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text(dolilStr(R.string.action_ok)) }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("${DolilStrings.appTitle(language)} (${AppVersionInfo.formattedVersion})") },
            text = {
                Text(
                    if (language == AppLanguage.BN) {
                        "দলিল অনুসন্ধান ${AppVersionInfo.formattedVersion} — বাংলা ও ইংরেজি দলিল তথ্য সংরক্ষণ, দ্রুত অনুসন্ধান, অফলাইন খসড়া এবং ব্যক্তিগত Google Drive ক্লাউড ব্যাকআপ।"
                    } else {
                        "Dolil Onusondhan ${AppVersionInfo.formattedVersion} — Bilingual Land Deed Management, Offline-First Room Storage, and Personal Google Drive Cloud Backup."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text(dolilStr(R.string.action_ok)) }
            }
        )
    }
}
