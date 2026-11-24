package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DolilComponentTokens
import com.example.core.designsystem.DolilEmptyState
import com.example.core.designsystem.DolilIconSize
import com.example.core.designsystem.DolilIcons
import com.example.core.designsystem.DolilOutlinedField
import com.example.core.designsystem.DolilPrimaryButton
import com.example.core.designsystem.DolilRadius
import com.example.core.designsystem.DolilSearchField
import com.example.core.designsystem.DolilSecondaryButton
import com.example.core.designsystem.DolilSpacing
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.RecordCard
import com.example.core.designsystem.dolilColors
import com.example.core.designsystem.dolilStr
import com.example.core.designsystem.isReducedMotionEnabled
import com.example.core.designsystem.tabularNumerals
import com.example.data.local.BangladeshLocationsCatalog
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedType
import com.example.domain.model.RankedDeedResult
import com.example.domain.model.SortOption
import com.example.domain.model.UserProfile
import com.example.domain.search.SearchNormalizer
import com.example.ui.SearchFilterState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    language: AppLanguage,
    filterState: SearchFilterState,
    results: List<RankedDeedResult>,
    onQueryChange: (String) -> Unit,
    onUpdateFilters: (DeedType?, Int?, String, String, SortOption) -> Unit,
    onClearFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDeedDetail: (String) -> Unit,
    isGuestMode: Boolean = false,
    tourTargets: TourTargetsState? = null,
    userProfile: UserProfile = UserProfile(),
    onEditDeed: (String) -> Unit = {},
    onApproveDeed: (DeedRecord) -> Unit = {},
    onSoftDeleteDeed: (DeedRecord) -> Unit = {},
    onRestoreDeed: (DeedRecord) -> Unit = {}
) {
    val context = LocalContext.current
    remember(context) {
        BangladeshLocationsCatalog.ensureLoaded(context)
        true
    }
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val semantic = MaterialTheme.dolilColors
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedDeedId by rememberSaveable { mutableStateOf<String?>(null) }

    val activeFilterCount = remember(
        filterState.deedType,
        filterState.year,
        filterState.district,
        filterState.mouza
    ) {
        listOf(
            filterState.deedType != null,
            filterState.year != null,
            filterState.district.isNotBlank(),
            filterState.mouza.isNotBlank()
        ).count { it }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("search_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        val isExpandedPane = maxWidth >= 840.dp

        @Composable
        fun SearchResultsPane(modifier: Modifier = Modifier) {
            Column(modifier = modifier.fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space3)
                        .then(if (reducedMotion) Modifier else Modifier.animateContentSize()),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    DolilSearchField(
                        value = filterState.query,
                        onValueChange = onQueryChange,
                        placeholder = DolilStrings.searchPlaceholder(language),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        testTag = "search_screen_input",
                        trailingContent = if (filterState.query.isNotBlank()) {
                            {
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("search_clear_button")
                                ) {
                                    Icon(
                                        DolilIcons.Clear,
                                        contentDescription = if (language == AppLanguage.BN) "মুছুন" else "Clear"
                                    )
                                }
                            }
                        } else null
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourTarget(tourTargets, TourTargetId.FILTER_SORT)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = activeFilterCount > 0,
                            onClick = { showFilterSheet = true },
                            leadingIcon = {
                                Icon(DolilIcons.Filter, contentDescription = null, modifier = Modifier.size(DolilIconSize.Sm))
                            },
                            label = {
                                val base = if (language == AppLanguage.BN) "ফিল্টার" else "Filters"
                                Text(if (activeFilterCount > 0) "$base ($activeFilterCount)" else base)
                            },
                            modifier = Modifier.testTag("open_filters_button")
                        )

                        FilterChip(
                            selected = filterState.sortOption != SortOption.REGISTRATION_DATE_DESC,
                            onClick = { showSortSheet = true },
                            leadingIcon = {
                                Icon(DolilIcons.Sort, contentDescription = null, modifier = Modifier.size(DolilIconSize.Sm))
                            },
                            label = {
                                Text(DolilStrings.sortText(filterState.sortOption, language))
                            },
                            modifier = Modifier.testTag("open_sort_button")
                        )

                        if (activeFilterCount > 0) {
                            TextButton(
                                onClick = onClearFilters,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_filters_button")
                            ) {
                                Icon(DolilIcons.Reset, contentDescription = null, modifier = Modifier.size(DolilIconSize.Xs))
                                Spacer(Modifier.width(4.dp))
                                Text(if (language == AppLanguage.BN) "ফিল্টার মুছুন" else "Reset")
                            }
                        }
                    }

                    if (activeFilterCount > 0) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)
                        ) {
                            filterState.deedType?.let { dt ->
                                val label = DolilStrings.deedTypeText(dt, language)
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        onUpdateFilters(null, filterState.year, filterState.district, filterState.mouza, filterState.sortOption)
                                    },
                                    label = { Text(label) },
                                    trailingIcon = {
                                        Icon(
                                            DolilIcons.Close,
                                            contentDescription = dolilStr(R.string.action_clear_filter, label),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                            filterState.year?.let { yr ->
                                val yrStr = yr.toString()
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        onUpdateFilters(filterState.deedType, null, filterState.district, filterState.mouza, filterState.sortOption)
                                    },
                                    label = { Text(yrStr) },
                                    trailingIcon = {
                                        Icon(
                                            DolilIcons.Close,
                                            contentDescription = dolilStr(R.string.action_clear_filter, yrStr),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                            if (filterState.district.isNotBlank()) {
                                val chipText = "${DolilStrings.districtLabel(language)}: ${filterState.district}"
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        onUpdateFilters(filterState.deedType, filterState.year, "", filterState.mouza, filterState.sortOption)
                                    },
                                    label = { Text(chipText) },
                                    trailingIcon = {
                                        Icon(
                                            DolilIcons.Close,
                                            contentDescription = dolilStr(R.string.action_clear_filter, chipText),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                            if (filterState.mouza.isNotBlank()) {
                                val chipText = "${DolilStrings.mouzaLabel(language)}: ${filterState.mouza}"
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        onUpdateFilters(filterState.deedType, filterState.year, filterState.district, "", filterState.sortOption)
                                    },
                                    label = { Text(chipText) },
                                    trailingIcon = {
                                        Icon(
                                            DolilIcons.Close,
                                            contentDescription = dolilStr(R.string.action_clear_filter, chipText),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = if (filterState.query.isNotBlank() && results.isNotEmpty()) {
                            "${dolilStr(R.string.msg_search_found)} (${results.size})"
                        } else if (language == AppLanguage.BN) {
                            "মোট পাওয়া গেছে: ${results.size}টি দলিল"
                        } else {
                            "Showing ${results.size} record(s)"
                        },
                        style = MaterialTheme.typography.labelMedium.tabularNumerals(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("search_results_list"),
                    contentPadding = PaddingValues(
                        start = DolilSpacing.Space4,
                        end = DolilSpacing.Space4,
                        bottom = DolilSpacing.Space6
                    ),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
                ) {
                    if (results.isEmpty()) {
                        item {
                            DolilEmptyState(
                                icon = DolilIcons.SearchOff,
                                title = dolilStr(R.string.empty_search_title),
                                subtitle = dolilStr(R.string.empty_search_body)
                            )
                        }
                    } else {
                        items(results, key = { it.deed.id }) { item ->
                            RecordCard(
                                deed = item.deed,
                                language = language,
                                matchReason = if (filterState.query.isNotBlank()) item.matchReason else null,
                                isDemoMode = isGuestMode,
                                isSelected = isExpandedPane && item.deed.id == selectedDeedId,
                                onClick = {
                                    if (isExpandedPane) {
                                        selectedDeedId = item.deed.id
                                    } else {
                                        onOpenDeedDetail(item.deed.id)
                                    }
                                },
                                modifier = if (reducedMotion) Modifier else Modifier.animateItem()
                            )
                        }

                        if (results.size >= filterState.page * filterState.pageSize) {
                            item {
                                DolilSecondaryButton(
                                    text = if (language == AppLanguage.BN) "আরও দেখুন" else "Load More",
                                    onClick = onLoadMore,
                                    testTag = "search_load_more_button",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
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
                SearchResultsPane(modifier = Modifier.weight(0.44f))

                VerticalDivider(
                    thickness = DolilComponentTokens.HairlineWidth,
                    color = semantic.cardBorder,
                    modifier = Modifier.padding(vertical = DolilSpacing.Space4)
                )

                val selectedDeed = remember(results, selectedDeedId) {
                    results.firstOrNull { it.deed.id == selectedDeedId }?.deed
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
            SearchResultsPane(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
            )
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                    modifier = Modifier.padding(bottom = DolilSpacing.Space2)
                ) {
                    Icon(
                        imageVector = DolilIcons.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (language == AppLanguage.BN) "সাজানোর ধরন নির্বাচন করুন" else "Sort By",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                SortOption.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(DolilStrings.sortText(option, language)) },
                        leadingContent = {
                            RadioButton(
                                selected = filterState.sortOption == option,
                                onClick = {
                                    onUpdateFilters(
                                        filterState.deedType,
                                        filterState.year,
                                        filterState.district,
                                        filterState.mouza,
                                        option
                                    )
                                    showSortSheet = false
                                }
                            )
                        },
                        modifier = Modifier
                            .clickable {
                                onUpdateFilters(
                                    filterState.deedType,
                                    filterState.year,
                                    filterState.district,
                                    filterState.mouza,
                                    option
                                )
                                showSortSheet = false
                            }
                            .testTag("sort_option_${option.name}")
                    )
                }
                Spacer(Modifier.height(DolilSpacing.Space4))
            }
        }
    }

    if (showFilterSheet) {
        var selectedType by remember { mutableStateOf(filterState.deedType) }
        var yearText by remember { mutableStateOf(filterState.year?.toString().orEmpty()) }
        var districtText by remember { mutableStateOf(filterState.district) }
        var mouzaText by remember { mutableStateOf(filterState.mouza) }
        var districtExpanded by remember { mutableStateOf(false) }
        var mouzaExpanded by remember { mutableStateOf(false) }

        val filteredDistricts = remember(districtText) {
            val q = BangladeshLocationsCatalog.normalizeLocationKey(districtText)
            val exact = BangladeshLocationsCatalog.findDistrict(districtText) != null
            if (q.isBlank() || exact) BangladeshLocationsCatalog.districts
            else BangladeshLocationsCatalog.districts.filter {
                BangladeshLocationsCatalog.normalizeLocationKey(it.bnName).contains(q) ||
                    BangladeshLocationsCatalog.normalizeLocationKey(it.enName).contains(q)
            }
        }
        val filteredMouzas = remember(districtText, mouzaText) {
            val base = if (districtText.isBlank()) {
                BangladeshLocationsCatalog.mouzas
            } else {
                BangladeshLocationsCatalog.mouzasForDistrict(districtText)
            }
            val q = BangladeshLocationsCatalog.normalizeLocationKey(mouzaText)
            if (q.isBlank()) base
            else base.filter {
                BangladeshLocationsCatalog.normalizeLocationKey(it.mouzaBn).contains(q) ||
                    BangladeshLocationsCatalog.normalizeLocationKey(it.mouzaEn).contains(q) ||
                    it.jlNumber.contains(q)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        Icon(
                            imageVector = DolilIcons.Filter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (language == AppLanguage.BN) "উন্নত ফিল্টার" else "Advanced Filters",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = DolilIcons.Document,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(DolilIconSize.Xs)
                        )
                        Text(
                            text = DolilStrings.deedTypeLabel(language),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                        verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)
                    ) {
                        FilterChip(
                            selected = selectedType == null,
                            onClick = { selectedType = null },
                            label = { Text(if (language == AppLanguage.BN) "সকল ধরন" else "All Types") }
                        )
                        DeedType.entries.forEach { dt ->
                            FilterChip(
                                selected = selectedType == dt,
                                onClick = { selectedType = dt },
                                label = { Text(DolilStrings.deedTypeText(dt, language)) }
                            )
                        }
                    }
                    DolilOutlinedField(
                        value = yearText,
                        onValueChange = { yearText = it },
                        label = DolilStrings.deedYearLabel(language),
                        trailingIcon = {
                            Icon(
                                DolilIcons.Calendar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(DolilIconSize.Sm)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        testTag = "filter_year_input"
                    )
                    ExposedDropdownMenuBox(
                        expanded = districtExpanded && filteredDistricts.isNotEmpty(),
                        onExpandedChange = { districtExpanded = !districtExpanded }
                    ) {
                        DolilOutlinedField(
                            value = districtText,
                            onValueChange = {
                                districtText = it
                                districtExpanded = true
                            },
                            label = DolilStrings.districtLabel(language),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        DolilIcons.Location,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(DolilIconSize.Sm)
                                    )
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded)
                                }
                            },
                            testTag = "filter_district_input",
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = districtExpanded && filteredDistricts.isNotEmpty(),
                            onDismissRequest = { districtExpanded = false }
                        ) {
                            filteredDistricts.forEach { d ->
                                val label = if (language == AppLanguage.BN) "${d.bnName} (${d.enName})" else "${d.enName} (${d.bnName})"
                                val chosen = if (language == AppLanguage.BN) d.bnName else d.enName
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        if (BangladeshLocationsCatalog.normalizeLocationKey(districtText) !=
                                            BangladeshLocationsCatalog.normalizeLocationKey(chosen)
                                        ) {
                                            mouzaText = ""
                                        }
                                        districtText = chosen
                                        districtExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = mouzaExpanded && filteredMouzas.isNotEmpty(),
                        onExpandedChange = { mouzaExpanded = !mouzaExpanded }
                    ) {
                        DolilOutlinedField(
                            value = mouzaText,
                            onValueChange = {
                                mouzaText = it
                                mouzaExpanded = true
                            },
                            label = DolilStrings.mouzaLabel(language),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = DolilIcons.mouzaMapPainter(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(DolilIconSize.Sm)
                                    )
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = mouzaExpanded)
                                }
                            },
                            testTag = "filter_mouza_input",
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = mouzaExpanded && filteredMouzas.isNotEmpty(),
                            onDismissRequest = { mouzaExpanded = false }
                        ) {
                            filteredMouzas.forEach { m ->
                                val primary = if (language == AppLanguage.BN) {
                                    "${m.mouzaBn} (${m.mouzaEn}) • জে.এল. ${m.jlNumber}"
                                } else {
                                    "${m.mouzaEn} (${m.mouzaBn}) • JL ${m.jlNumber}"
                                }
                                val surveys = m.surveyTags.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                                val chosen = if (language == AppLanguage.BN) m.mouzaBn else m.mouzaEn
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(primary, style = MaterialTheme.typography.bodyMedium.tabularNumerals())
                                            if (!surveys.isNullOrBlank()) {
                                                Text(
                                                    text = surveys,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        mouzaText = chosen
                                        mouzaExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space3),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        DolilSecondaryButton(
                            text = if (language == AppLanguage.BN) "রিসেট" else "Reset",
                            icon = DolilIcons.Reset,
                            onClick = {
                                selectedType = null
                                yearText = ""
                                districtText = ""
                                mouzaText = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        DolilPrimaryButton(
                            text = if (language == AppLanguage.BN) "ফিল্টার প্রয়োগ করুন" else "Apply Filters",
                            icon = DolilIcons.Filter,
                            onClick = {
                                onUpdateFilters(
                                    selectedType,
                                    SearchNormalizer.normalizeCode(yearText).toIntOrNull(),
                                    districtText.trim(),
                                    mouzaText.trim(),
                                    filterState.sortOption
                                )
                                showFilterSheet = false
                            },
                            testTag = "apply_filters_button",
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }
            }
        }
    }
}
