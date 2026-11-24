package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DolilComponentTokens
import com.example.core.designsystem.DolilConfirmationDialog
import com.example.core.designsystem.DolilEmptyState
import com.example.core.designsystem.DolilErrorBanner
import com.example.core.designsystem.DolilIconSize
import com.example.core.designsystem.DolilIcons
import com.example.core.designsystem.DolilLoadingSkeleton
import com.example.core.designsystem.DolilOutlinedDestructiveButton
import com.example.core.designsystem.DolilPrimaryButton
import com.example.core.designsystem.DolilRadius
import com.example.core.designsystem.DolilSecondaryButton
import com.example.core.designsystem.DolilSpacing
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.DolilTopBar
import com.example.core.designsystem.FormSectionCard
import com.example.core.designsystem.RecordCard
import com.example.core.designsystem.StatusChip
import com.example.core.designsystem.dolilColors
import com.example.core.designsystem.dolilStr
import com.example.core.designsystem.editorialNumeric
import com.example.core.designsystem.isReducedMotionEnabled
import com.example.core.designsystem.performSubtleTick
import com.example.core.designsystem.tabularNumerals
import com.example.core.print.DeedDocumentBuilder
import com.example.core.print.DeedPdfRenderer
import com.example.core.print.DeedPrintHelper
import com.example.core.security.RolePermissions
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.SortOption
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.search.DeedSorter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

private enum class RecordsTab { ALL, DRAFTS, PENDING, ARCHIVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    language: AppLanguage,
    userProfile: UserProfile,
    deedsState: UiState<List<DeedRecord>>,
    onOpenDeedDetail: (String) -> Unit,
    onCreateNewDeed: () -> Unit,
    isGuestMode: Boolean = false,
    onEditDeed: (String) -> Unit = {},
    onApproveDeed: (DeedRecord) -> Unit = {},
    onSoftDeleteDeed: (DeedRecord) -> Unit = {},
    onRestoreDeed: (DeedRecord) -> Unit = {},
    initialTabIndex: Int = 0,
    tabSelectionNonce: Int = 0
) {
    var selectedTab by remember {
        mutableStateOf(RecordsTab.entries.getOrElse(initialTabIndex) { RecordsTab.ALL })
    }
    var sortOption by remember { mutableStateOf(SortOption.REGISTRATION_DATE_DESC) }
    var page by remember { mutableIntStateOf(1) }
    var showSortSheet by remember { mutableStateOf(false) }
    var selectedDeedId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(tabSelectionNonce) {
        if (tabSelectionNonce > 0) {
            selectedTab = RecordsTab.entries.getOrElse(initialTabIndex) { RecordsTab.ALL }
            page = 1
        }
    }

    val context = LocalContext.current
    val semantic = MaterialTheme.dolilColors
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val allDeeds = (deedsState as? UiState.Success)?.data.orEmpty()
    val counts = remember(allDeeds) {
        val active = allDeeds.count { it.status != DeedStatus.SOFT_DELETED }
        val drafts = allDeeds.count { it.status == DeedStatus.DRAFT }
        val pending = allDeeds.count { it.status == DeedStatus.PENDING_REVIEW }
        val archive = allDeeds.count { it.status == DeedStatus.SOFT_DELETED }
        listOf(active, drafts, pending, archive)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("records_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        val isExpandedPane = maxWidth >= 840.dp

        @Composable
        fun RecordsListPane(modifier: Modifier = Modifier) {
            Column(modifier = modifier.fillMaxHeight()) {
                Column(
                    modifier = Modifier.padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space3),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        Icon(
                            painter = DolilIcons.khatianLedgerPainter(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(DolilIconSize.Lg)
                        )
                        Text(
                            text = DolilStrings.navRecords(language),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        val isCustomSort = sortOption != SortOption.REGISTRATION_DATE_DESC
                        FilterChip(
                            selected = isCustomSort,
                            onClick = { showSortSheet = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = DolilIcons.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    if (isCustomSort) DolilStrings.sortText(sortOption, language)
                                    else if (language == AppLanguage.BN) "সাজানো" else "Sort"
                                )
                            },
                            modifier = Modifier.testTag("records_open_sort_button")
                        )
                        FilterChip(
                            selected = selectedTab == RecordsTab.ALL,
                            onClick = { selectedTab = RecordsTab.ALL; page = 1 },
                            label = {
                                Text(
                                    if (language == AppLanguage.BN) "সক্রিয় (${counts[0]})" else "Active (${counts[0]})",
                                    style = MaterialTheme.typography.labelLarge.tabularNumerals()
                                )
                            },
                            modifier = Modifier.testTag("records_tab_all")
                        )
                        FilterChip(
                            selected = selectedTab == RecordsTab.DRAFTS,
                            onClick = { selectedTab = RecordsTab.DRAFTS; page = 1 },
                            label = {
                                Text(
                                    "${DolilStrings.drafts(language)} (${counts[1]})",
                                    style = MaterialTheme.typography.labelLarge.tabularNumerals()
                                )
                            },
                            modifier = Modifier.testTag("records_tab_drafts")
                        )
                        if (RolePermissions.canApproveDeed(userProfile.role)) {
                            FilterChip(
                                selected = selectedTab == RecordsTab.PENDING,
                                onClick = { selectedTab = RecordsTab.PENDING; page = 1 },
                                label = {
                                    Text(
                                        "${DolilStrings.pendingReview(language)} (${counts[2]})",
                                        style = MaterialTheme.typography.labelLarge.tabularNumerals()
                                    )
                                },
                                modifier = Modifier.testTag("records_tab_pending")
                            )
                        }
                        FilterChip(
                            selected = selectedTab == RecordsTab.ARCHIVE,
                            onClick = { selectedTab = RecordsTab.ARCHIVE; page = 1 },
                            label = {
                                Text(
                                    if (language == AppLanguage.BN) "আর্কাইভ (${counts[3]})" else "Archive (${counts[3]})",
                                    style = MaterialTheme.typography.labelLarge.tabularNumerals()
                                )
                            },
                            modifier = Modifier.testTag("records_tab_archive")
                        )
                    }
                }

                when (deedsState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.padding(DolilSpacing.Space4)) {
                            DolilLoadingSkeleton(count = 3)
                        }
                    }
                    is UiState.Error -> {
                        Box(modifier = Modifier.padding(DolilSpacing.Space4)) {
                            DolilErrorBanner(message = deedsState.message)
                        }
                    }
                    is UiState.Success -> {
                        val filtered = remember(deedsState.data, selectedTab, sortOption) {
                            val sub = when (selectedTab) {
                                RecordsTab.ALL -> deedsState.data.filter { it.status != DeedStatus.SOFT_DELETED }
                                RecordsTab.DRAFTS -> deedsState.data.filter { it.status == DeedStatus.DRAFT }
                                RecordsTab.PENDING -> deedsState.data.filter { it.status == DeedStatus.PENDING_REVIEW }
                                RecordsTab.ARCHIVE -> deedsState.data.filter { it.status == DeedStatus.SOFT_DELETED }
                            }
                            DeedSorter.sort(sub, sortOption)
                        }
                        val paged = remember(filtered, page) {
                            DeedSorter.paginate(filtered, page, pageSize = 20)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = DolilSpacing.Space4,
                                end = DolilSpacing.Space4,
                                bottom = 88.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
                        ) {
                            if (paged.isEmpty()) {
                                item {
                                    val emptyTitle = when (selectedTab) {
                                        RecordsTab.DRAFTS -> dolilStr(R.string.empty_drafts_title)
                                        RecordsTab.ARCHIVE -> dolilStr(R.string.empty_archive_title)
                                        else -> dolilStr(R.string.empty_records_title)
                                    }
                                    val emptyBody = when (selectedTab) {
                                        RecordsTab.DRAFTS -> dolilStr(R.string.empty_drafts_body)
                                        RecordsTab.ARCHIVE -> dolilStr(R.string.empty_archive_body)
                                        else -> dolilStr(R.string.empty_records_body)
                                    }
                                    DolilEmptyState(
                                        icon = DolilIcons.FolderCopy,
                                        title = emptyTitle,
                                        subtitle = emptyBody,
                                        actionLabel = if (selectedTab == RecordsTab.ARCHIVE) null else DolilStrings.navAddDeed(language),
                                        onAction = if (selectedTab == RecordsTab.ARCHIVE) null else onCreateNewDeed
                                    )
                                }
                            } else {
                                items(paged, key = { it.id }) { deed ->
                                    RecordCard(
                                        deed = deed,
                                        language = language,
                                        isDemoMode = isGuestMode,
                                        isSelected = isExpandedPane && deed.id == selectedDeedId,
                                        onClick = {
                                            if (isExpandedPane) {
                                                selectedDeedId = deed.id
                                            } else {
                                                onOpenDeedDetail(deed.id)
                                            }
                                        },
                                        modifier = if (reducedMotion) Modifier else Modifier.animateItem()
                                    )
                                }
                                if (paged.size < filtered.size) {
                                    item {
                                        DolilSecondaryButton(
                                            text = if (language == AppLanguage.BN) "আরও দেখুন" else "Load More",
                                            onClick = { page++ },
                                            testTag = "records_load_more_button",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

        if (isExpandedPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = DolilSpacing.Space4),
                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
            ) {
                RecordsListPane(modifier = Modifier.weight(0.44f))

                VerticalDivider(
                    thickness = DolilComponentTokens.HairlineWidth,
                    color = semantic.cardBorder,
                    modifier = Modifier.padding(vertical = DolilSpacing.Space4)
                )

                val selectedDeed = remember(allDeeds, selectedDeedId) {
                    allDeeds.firstOrNull { it.id == selectedDeedId }
                }

                Surface(
                    shape = RoundedCornerShape(DolilRadius.Card),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight()
                        .padding(vertical = DolilSpacing.Space3)
                        .testTag("deed_details_pane")
                ) {
                    if (selectedDeed == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(DolilSpacing.Space6),
                            contentAlignment = Alignment.Center
                        ) {
                            DolilEmptyState(
                                icon = DolilIcons.Document,
                                title = DolilStrings.selectDeedPreviewTitle(language),
                                subtitle = DolilStrings.selectDeedPreviewSubtitle(language)
                            )
                        }
                    } else {
                        val paneSnackbar = remember { SnackbarHostState() }
                        Scaffold(
                            topBar = {
                                DeedPaneTopBar(
                                    deed = selectedDeed,
                                    language = language,
                                    snackbarHostState = paneSnackbar
                                )
                            },
                            snackbarHost = { SnackbarHost(hostState = paneSnackbar) },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) { panePadding ->
                            DeedDetailsPane(
                                language = language,
                                userProfile = userProfile,
                                deed = selectedDeed,
                                onEditDeed = onEditDeed,
                                onApproveDeed = onApproveDeed,
                                onSoftDeleteDeed = onSoftDeleteDeed,
                                onRestoreDeed = onRestoreDeed,
                                snackbarHostState = paneSnackbar,
                                isGuestMode = isGuestMode,
                                modifier = Modifier.padding(panePadding)
                            )
                        }
                    }
                }
            }
        } else {
            RecordsListPane(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
            )
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            LazyColumn(
                modifier = Modifier.navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2)
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                        modifier = Modifier.padding(bottom = DolilSpacing.Space3)
                    ) {
                        Icon(
                            imageVector = DolilIcons.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (language == AppLanguage.BN) "সাজানোর ধরন নির্বাচন করুন" else "Sort Records By",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                }
                items(SortOption.entries) { option ->
                    ListItem(
                        headlineContent = { Text(DolilStrings.sortText(option, language)) },
                        trailingContent = {
                            if (sortOption == option) {
                                Icon(DolilIcons.Approve, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier
                            .clickable(role = Role.Button) {
                                sortOption = option
                                showSortSheet = false
                            }
                            .testTag("records_sort_option_${option.name}")
                    )
                }
                item { Spacer(Modifier.height(DolilSpacing.Space6)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeedDetailsScreen(
    language: AppLanguage,
    userProfile: UserProfile,
    deed: DeedRecord?,
    onEditDeed: (String) -> Unit,
    onApproveDeed: (DeedRecord) -> Unit,
    onSoftDeleteDeed: (DeedRecord) -> Unit,
    onRestoreDeed: (DeedRecord) -> Unit,
    onBack: () -> Unit,
    isGuestMode: Boolean = false
) {
    BackHandler { onBack() }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            DolilTopBar(
                title = if (deed != null) {
                    if (language == AppLanguage.BN) "দলিল #${deed.deedNumber.ifBlank { "খসড়া" }}"
                    else "Deed #${deed.deedNumber.ifBlank { "Draft" }}"
                } else {
                    if (language == AppLanguage.BN) "দলিল বিবরণ" else "Deed Details"
                },
                subtitle = deed?.let { "${DolilStrings.deedTypeText(it.deedType, language)} (${it.deedYear})" },
                onBack = onBack,
                actions = {
                    if (deed != null) {
                        DeedDocumentActions(
                            deed = deed,
                            language = language,
                            snackbarHostState = snackbarHostState
                        )
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
            if (deed == null) {
                Box(modifier = Modifier.padding(DolilSpacing.Space4)) {
                    DolilEmptyState(
                        icon = DolilIcons.FolderCopy,
                        title = if (language == AppLanguage.BN) "দলিল পাওয়া যায়নি" else "Deed Not Found",
                        subtitle = if (language == AppLanguage.BN) {
                            "এই দলিলটি আর উপলব্ধ নেই বা প্রবেশাধিকার সীমিত।"
                        } else {
                            "This record may have been archived or requires higher authorization."
                        },
                        actionLabel = if (language == AppLanguage.BN) "ফিরে যান" else "Go Back",
                        onAction = onBack
                    )
                }
                return@Box
            }

            DeedDetailsPane(
                language = language,
                userProfile = userProfile,
                deed = deed,
                onEditDeed = onEditDeed,
                onApproveDeed = onApproveDeed,
                onSoftDeleteDeed = onSoftDeleteDeed,
                onRestoreDeed = onRestoreDeed,
                snackbarHostState = snackbarHostState,
                isGuestMode = isGuestMode,
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
fun DeedPaneTopBar(
    deed: DeedRecord,
    language: AppLanguage,
    snackbarHostState: SnackbarHostState
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DolilStrings.deedNumberFormatted(deed.deedNumber, deed.deedYear, language),
                    style = MaterialTheme.typography.titleMedium.tabularNumerals(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = DolilStrings.deedTypeText(deed.deedType, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            DeedDocumentActions(
                deed = deed,
                language = language,
                snackbarHostState = snackbarHostState
            )
        }
        HorizontalDivider(
            thickness = DolilComponentTokens.HairlineWidth,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
fun DeedDocumentActions(
    deed: DeedRecord,
    language: AppLanguage,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    val savedMsg = dolilStr(R.string.msg_pdf_saved)
    val saveFailedMsg = dolilStr(R.string.msg_pdf_save_failed)
    val printUnavailableMsg = dolilStr(R.string.msg_print_unavailable)
    val downloadDesc = dolilStr(R.string.action_download_pdf)
    val printDesc = dolilStr(R.string.action_print_deed)

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("Unable to open destination file")
                    stream.use { out ->
                        DeedPdfRenderer.writePdfToStream(
                            context = context,
                            deed = deed,
                            language = language,
                            outputStream = out
                        ).getOrThrow()
                    }
                }
            }
            isExporting = false
            snackbarHostState.showSnackbar(if (result.isSuccess) savedMsg else saveFailedMsg)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isExporting) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        } else {
            IconButton(
                onClick = {
                    haptics.performSubtleTick()
                    createDocumentLauncher.launch(DeedDocumentBuilder.suggestedPdfFileName(deed))
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("details_download_pdf_button")
            ) {
                Icon(
                    imageVector = DolilIcons.Download,
                    contentDescription = downloadDesc,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = {
                    haptics.performSubtleTick()
                    val launched = DeedPrintHelper.launchPrintJob(
                        context = context,
                        deed = deed,
                        language = language
                    )
                    if (!launched) {
                        scope.launch {
                            snackbarHostState.showSnackbar(printUnavailableMsg)
                        }
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("details_print_button")
            ) {
                Icon(
                    imageVector = DolilIcons.Print,
                    contentDescription = printDesc,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DeedDetailsPane(
    language: AppLanguage,
    userProfile: UserProfile,
    deed: DeedRecord,
    onEditDeed: (String) -> Unit,
    onApproveDeed: (DeedRecord) -> Unit,
    onSoftDeleteDeed: (DeedRecord) -> Unit,
    onRestoreDeed: (DeedRecord) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    isGuestMode: Boolean = false
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val copiedMessage = dolilStr(R.string.msg_copied)
    val semantic = MaterialTheme.dolilColors

    fun copyToClipboard(label: String, text: String) {
        if (text.isBlank() || text == "-") return
        haptics.performSubtleTick()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch {
            snackbarHostState.showSnackbar("$copiedMessage: $text")
        }
    }

    val regDateStr = DolilStrings.formatDate(deed.registrationDateMillis, language)
    val totalShotok = deed.computeTotalShotokDecimal()
    val totalKatha = totalShotok.divide(BigDecimal("1.65"), 2, RoundingMode.HALF_UP)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("deed_details_screen"),
        contentPadding = PaddingValues(DolilSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space4)
    ) {
        // Item 0: Archival Parchment Summary Card + General Ledger Card
        item {
            Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space4)) {
                Surface(
                    shape = RoundedCornerShape(DolilRadius.Card),
                    color = semantic.paperSurface,
                    border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.paperDivider),
                    modifier = Modifier.fillMaxWidth()
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
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusChip(status = deed.status, language = language)
                                    if (isGuestMode) {
                                        Surface(
                                            color = semantic.warningContainer,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.testTag("details_demo_badge")
                                        ) {
                                            Text(
                                                text = dolilStr(R.string.guest_demo_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = semantic.onWarningContainer,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = DolilIcons.deedSealPainter(),
                                        contentDescription = null,
                                        tint = semantic.onPaperMuted,
                                        modifier = Modifier.size(DolilIconSize.Sm)
                                    )
                                    Text(
                                        text = DolilStrings.syncText(deed.syncState, language),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = semantic.onPaperMuted
                                    )
                                }
                            }

                            Text(
                                text = DolilStrings.deedNumberFormatted(deed.deedNumber, deed.deedYear, language),
                                style = MaterialTheme.typography.headlineSmall.editorialNumeric(),
                                color = semantic.onPaperSurface,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "${DolilStrings.deedTypeText(deed.deedType, language)} • $regDateStr",
                                style = MaterialTheme.typography.bodyMedium.tabularNumerals(),
                                color = semantic.onPaperMuted
                            )

                            if (deed.primaryGrantorName.isNotBlank() || deed.primaryGranteeName.isNotBlank()) {
                                HorizontalDivider(
                                    thickness = DolilComponentTokens.HairlineWidth,
                                    color = semantic.paperDivider
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                                ) {
                                    Text(
                                        text = deed.primaryGrantorName.ifBlank { "-" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = semantic.onPaperSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(
                                        painter = DolilIcons.partyTransferPainter(),
                                        contentDescription = null,
                                        tint = semantic.onPaperMuted,
                                        modifier = Modifier.size(DolilIconSize.Sm)
                                    )
                                    Text(
                                        text = deed.primaryGranteeName.ifBlank { "-" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = semantic.onPaperSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val locLine = listOf(deed.mouzaName, deed.upazilaName)
                                    .filter { it.isNotBlank() }
                                    .joinToString(", ")
                                    .ifBlank { "-" }
                                Text(
                                    text = locLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.onPaperMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = semantic.accentGold.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(DolilComponentTokens.BadgeCorner)
                                ) {
                                    Text(
                                        text = "${totalShotok.toPlainString()} ${DolilStrings.shotokUnit(language)}",
                                        style = MaterialTheme.typography.labelLarge.editorialNumeric(),
                                        color = semantic.onPaperSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                FormSectionCard(
                    title = DolilStrings.secGeneral(language),
                    icon = DolilIcons.Document
                ) {
                    DetailRow(DolilStrings.deedTypeLabel(language), DolilStrings.deedTypeText(deed.deedType, language))
                    DetailRow(
                        label = DolilStrings.deedNumberLabel(language),
                        value = "${deed.deedNumber.ifBlank { "-" }} (${deed.deedYear})",
                        copyableValue = deed.deedNumber.takeIf { it.isNotBlank() },
                        copyDescription = dolilStr(R.string.action_copy_field, DolilStrings.deedNumberLabel(language)),
                        onCopy = { copyToClipboard(DolilStrings.deedNumberLabel(language), it) }
                    )
                    DetailRow(DolilStrings.regDateLabel(language), regDateStr)
                    DetailRow(DolilStrings.officeLabel(language), deed.officeName.ifBlank { "-" })
                    if (deed.notes.isNotBlank()) {
                        DetailRow(DolilStrings.notesLabel(language), deed.notes)
                    }
                }
            }
        }

        // Item 1: Grantors
        item {
            FormSectionCard(
                title = DolilStrings.secGrantors(language),
                icon = DolilIcons.Person
            ) {
                deed.grantors.forEachIndexed { idx, p ->
                    if (idx > 0) HorizontalDivider(color = semantic.divider)
                    DetailRow("${idx + 1}. ${DolilStrings.fullNameLabel(language)}", p.fullName.ifBlank { "-" })
                    if (p.fatherName.isNotBlank()) DetailRow(DolilStrings.fatherNameLabel(language), p.fatherName)
                    if (p.motherName.isNotBlank()) DetailRow(DolilStrings.motherNameLabel(language), p.motherName)
                    if (p.spouseName.isNotBlank()) DetailRow(DolilStrings.spouseNameLabel(language), p.spouseName)
                    if (p.address.isNotBlank()) DetailRow(DolilStrings.addressLabel(language), p.address)
                }
            }
        }

        // Item 2: Grantees
        item {
            FormSectionCard(
                title = DolilStrings.secGrantees(language),
                icon = DolilIcons.Group
            ) {
                deed.grantees.forEachIndexed { idx, p ->
                    if (idx > 0) HorizontalDivider(color = semantic.divider)
                    DetailRow("${idx + 1}. ${DolilStrings.fullNameLabel(language)}", p.fullName.ifBlank { "-" })
                    if (p.fatherName.isNotBlank()) DetailRow(DolilStrings.fatherNameLabel(language), p.fatherName)
                    if (p.motherName.isNotBlank()) DetailRow(DolilStrings.motherNameLabel(language), p.motherName)
                    if (p.spouseName.isNotBlank()) DetailRow(DolilStrings.spouseNameLabel(language), p.spouseName)
                    if (p.address.isNotBlank()) DetailRow(DolilStrings.addressLabel(language), p.address)
                }
            }
        }

        // Item 3: Location
        item {
            FormSectionCard(
                title = DolilStrings.secLocation(language),
                icon = DolilIcons.Location
            ) {
                DetailRow(DolilStrings.districtLabel(language), deed.districtName.ifBlank { "-" })
                DetailRow(DolilStrings.upazilaLabel(language), deed.upazilaName.ifBlank { "-" })
                DetailRow(DolilStrings.unionLabel(language), deed.unionName.ifBlank { "-" })
                DetailRow(DolilStrings.mouzaLabel(language), deed.mouzaName.ifBlank { "-" })
                DetailRow(DolilStrings.jlNumberLabel(language), deed.jlNumber.ifBlank { "-" })
            }
        }

        // Item 4: Land Parcels
        item {
            FormSectionCard(
                title = DolilStrings.secLand(language),
                icon = DolilIcons.Land
            ) {
                deed.parcels.forEachIndexed { idx, parcel ->
                    if (idx > 0) HorizontalDivider(color = semantic.divider)
                    Text(
                        text = if (language == AppLanguage.BN) {
                            "তফসিল #${idx + 1} (${parcel.surveyType.bnLabel})"
                        } else {
                            "Parcel #${idx + 1} (${parcel.surveyType.enLabel})"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DetailRow(
                        label = DolilStrings.khatianLabel(language),
                        value = parcel.khatianNumber.ifBlank { "-" },
                        copyableValue = parcel.khatianNumber.takeIf { it.isNotBlank() },
                        copyDescription = dolilStr(R.string.action_copy_field, DolilStrings.khatianLabel(language)),
                        onCopy = { copyToClipboard(DolilStrings.khatianLabel(language), it) }
                    )
                    DetailRow(
                        label = DolilStrings.dagLabel(language),
                        value = parcel.dagNumber.ifBlank { "-" },
                        copyableValue = parcel.dagNumber.takeIf { it.isNotBlank() },
                        copyDescription = dolilStr(R.string.action_copy_field, DolilStrings.dagLabel(language)),
                        onCopy = { copyToClipboard(DolilStrings.dagLabel(language), it) }
                    )
                    DetailRow(DolilStrings.landClassLabel(language), DolilStrings.landClassText(parcel.landClass, language))
                    DetailRow(
                        DolilStrings.landAreaLabel(language),
                        "${parcel.landAreaDecimal.ifBlank { "0" }} ${DolilStrings.landUnitText(parcel.landUnit, language)} (${parcel.areaAsShotok().setScale(2, RoundingMode.HALF_UP)} ${DolilStrings.shotokUnit(language)})"
                    )
                    if (parcel.boundaryDescription.isNotBlank()) {
                        DetailRow(DolilStrings.boundaryLabel(language), parcel.boundaryDescription)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(DolilRadius.Input),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (language == AppLanguage.BN) {
                            "মোট জমি: ${totalShotok.toPlainString()} শতাংশ (≈ ${totalKatha.toPlainString()} কাঠা)"
                        } else {
                            "Total Computed Area: ${totalShotok.toPlainString()} Shotok (≈ ${totalKatha.toPlainString()} Katha)"
                        },
                        style = MaterialTheme.typography.titleMedium.editorialNumeric(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(DolilSpacing.Space3)
                    )
                }
            }
        }

        // Item 5: Role-Gated Lifecycle Actions (remains at index 5 when visible)
        val canEdit = RolePermissions.canEditDeed(userProfile, deed)
        val canApprove = RolePermissions.canApproveDeed(userProfile.role) && deed.status == DeedStatus.PENDING_REVIEW
        val canSoftDelete = RolePermissions.canSoftDeleteDeed(userProfile, deed)
        val canRestore = RolePermissions.canRestoreDeed(userProfile, deed)

        if (canEdit || canApprove || canSoftDelete || canRestore) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                    if (canEdit) {
                        DolilPrimaryButton(
                            text = if (language == AppLanguage.BN) "দলিল সংশোধন করুন" else "Edit Deed",
                            onClick = { onEditDeed(deed.id) },
                            icon = DolilIcons.Edit,
                            testTag = "details_edit_button",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (canApprove) {
                        DolilPrimaryButton(
                            text = if (language == AppLanguage.BN) "দলিল অনুমোদন করুন" else "Approve Deed",
                            onClick = { onApproveDeed(deed) },
                            icon = DolilIcons.Approve,
                            testTag = "details_approve_button",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (canSoftDelete) {
                        DolilOutlinedDestructiveButton(
                            text = if (language == AppLanguage.BN) "আর্কাইভে পাঠান" else "Move to Archive",
                            onClick = { showDeleteDialog = true },
                            icon = DolilIcons.Archive,
                            testTag = "details_soft_delete_button",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (canRestore) {
                        DolilSecondaryButton(
                            text = if (language == AppLanguage.BN) "দলিল পুনরুদ্ধার করুন" else "Restore Deed",
                            onClick = { showRestoreDialog = true },
                            icon = DolilIcons.Restore,
                            testTag = "details_restore_button",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        DolilConfirmationDialog(
            title = dolilStr(R.string.delete_confirm_title),
            body = dolilStr(R.string.delete_confirm_body),
            confirmText = dolilStr(R.string.delete_confirm_yes),
            dismissText = dolilStr(R.string.delete_confirm_keep),
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                onSoftDeleteDeed(deed)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showRestoreDialog) {
        DolilConfirmationDialog(
            title = if (language == AppLanguage.BN) "দলিল পুনরুদ্ধার করবেন?" else "Restore Deed Record?",
            body = if (language == AppLanguage.BN) {
                "আপনি কি এই দলিলটি আবার সক্রিয় তালিকায় ফিরিয়ে আনতে চান?"
            } else {
                "Restore this deed record back to active records?"
            },
            confirmText = if (language == AppLanguage.BN) "পুনরুদ্ধার" else "Restore",
            dismissText = DolilStrings.cancel(language),
            isDestructive = false,
            onConfirm = {
                showRestoreDialog = false
                onRestoreDeed(deed)
            },
            onDismiss = { showRestoreDialog = false }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    copyableValue: String? = null,
    copyDescription: String = "Copy",
    onCopy: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.40f)
        )
        Row(
            modifier = Modifier.weight(0.60f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.tabularNumerals(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (copyableValue != null && onCopy != null) {
                IconButton(
                    onClick = { onCopy(copyableValue) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = DolilIcons.Copy,
                        contentDescription = copyDescription,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(DolilIconSize.Xs)
                    )
                }
            }
        }
    }
}
