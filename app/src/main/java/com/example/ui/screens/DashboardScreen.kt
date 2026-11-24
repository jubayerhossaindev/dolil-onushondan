package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DolilComponentTokens
import com.example.core.designsystem.DolilEmptyState
import com.example.core.designsystem.DolilErrorBanner
import com.example.core.designsystem.DolilIconSize
import com.example.core.designsystem.DolilIcons
import com.example.core.designsystem.DolilLoadingSkeleton
import com.example.core.designsystem.DolilRadius
import com.example.core.designsystem.DolilSearchField
import com.example.core.designsystem.DolilSpacing
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.RecordCard
import com.example.core.designsystem.SectionHeader
import com.example.core.designsystem.dolilColors
import com.example.core.designsystem.dolilHero
import com.example.core.designsystem.dolilStr
import com.example.core.designsystem.editorialNumeric
import com.example.core.designsystem.performSubtleTick
import com.example.core.security.RolePermissions
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.OfficeStats
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile

@Composable
fun DashboardScreen(
    language: AppLanguage,
    userProfile: UserProfile,
    deedsState: UiState<List<DeedRecord>>,
    officeStats: OfficeStats,
    onQuickSearchSubmit: (String) -> Unit,
    onNavigateAllRecords: () -> Unit,
    onNavigatePersons: () -> Unit,
    onNavigateLand: () -> Unit,
    onNavigateProfile: () -> Unit,
    onOpenDeedDetail: (String) -> Unit,
    onNavigateNewDeed: () -> Unit = {},
    isGuestMode: Boolean = false,
    tourTargets: TourTargetsState? = null,
    onNavigateUserGuide: () -> Unit = {},
    onNavigateRecordsWithTab: ((Int) -> Unit)? = null
) {
    var quickQuery by rememberSaveable { mutableStateOf("") }
    val semantic = MaterialTheme.dolilColors
    val hero = MaterialTheme.dolilHero
    val haptics = LocalHapticFeedback.current

    fun openRecordsTab(tabIndex: Int) {
        haptics.performSubtleTick()
        if (onNavigateRecordsWithTab != null) {
            onNavigateRecordsWithTab(tabIndex)
        } else {
            onNavigateAllRecords()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize()
                .testTag("dashboard_screen"),
            contentPadding = PaddingValues(
                start = DolilSpacing.Space4,
                end = DolilSpacing.Space4,
                top = DolilSpacing.Space4,
                bottom = 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space4)
        ) {
            // Item 0: Editorial Paper & Ink Hero Header + Pill Search
            item {
                val heroShape = RoundedCornerShape(DolilRadius.Card)
                Surface(
                    shape = heroShape,
                    color = Color.Transparent,
                    border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.accentGold.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(heroShape)
                        .background(Brush.linearGradient(listOf(hero.gradientStart, hero.gradientEnd)))
                        .tourTarget(tourTargets, TourTargetId.DASHBOARD_HOME)
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
                            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(hero.chipBackground),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = DolilIcons.deedSealPainter(),
                                            contentDescription = DolilStrings.appTitle(language),
                                            tint = semantic.accentGold,
                                            modifier = Modifier.size(DolilIconSize.Lg)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = DolilStrings.appTitle(language),
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = hero.onHero,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val displayGreetingName = if (isGuestMode) {
                                            dolilStr(R.string.guest_profile_name)
                                        } else {
                                            userProfile.fullName.ifBlank { if (language == AppLanguage.BN) "বন্ধু" else "Friend" }
                                        }
                                        Text(
                                            text = if (language == AppLanguage.BN) {
                                                "স্বাগতম, $displayGreetingName"
                                            } else {
                                                "Welcome, $displayGreetingName"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = hero.onHeroMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptics.performSubtleTick()
                                            onNavigateUserGuide()
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("dashboard_help_button")
                                    ) {
                                        Icon(
                                            imageVector = DolilIcons.Help,
                                            contentDescription = dolilStr(R.string.action_view_guide),
                                            tint = hero.onHeroMuted
                                        )
                                    }

                                    Surface(
                                        color = hero.chipBackground,
                                        shape = RoundedCornerShape(DolilRadius.Pill),
                                        border = BorderStroke(
                                            DolilComponentTokens.HairlineWidth,
                                            semantic.accentGold.copy(alpha = 0.45f)
                                        ),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(DolilRadius.Pill))
                                            .clickable(
                                                role = Role.Button,
                                                onClick = {
                                                    haptics.performSubtleTick()
                                                    onNavigateProfile()
                                                }
                                            )
                                            .testTag("dashboard_profile_chip")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = DolilIcons.Verified,
                                                contentDescription = if (isGuestMode) {
                                                    dolilStr(R.string.guest_demo_badge)
                                                } else {
                                                    DolilStrings.roleLabel(userProfile.role, language)
                                                },
                                                tint = semantic.accentGold,
                                                modifier = Modifier.size(DolilIconSize.Xs)
                                            )
                                            Text(
                                                text = if (isGuestMode) {
                                                    dolilStr(R.string.guest_demo_badge)
                                                } else {
                                                    DolilStrings.roleLabel(userProfile.role, language)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = hero.onHero,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            DolilSearchField(
                                value = quickQuery,
                                onValueChange = { quickQuery = it },
                                placeholder = DolilStrings.searchPlaceholder(language),
                                containerColor = hero.searchContainer,
                                testTag = "dashboard_search_input",
                                onSearchAction = { onQuickSearchSubmit(quickQuery) },
                                modifier = Modifier.tourTarget(tourTargets, TourTargetId.SEARCH_INPUT)
                            )
                        }
                    }
                }
            }

            // Item 1: Quick Actions (2x2 Directories & Guide Grid)
            item {
                SectionHeader(
                    title = DolilStrings.quickActions(language),
                    icon = DolilIcons.Bolt
                )
                Spacer(Modifier.height(DolilSpacing.Space2))
                Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        QuickActionTile(
                            painter = DolilIcons.khatianLedgerPainter(),
                            title = DolilStrings.allDeeds(language),
                            testTag = "quick_action_all_deeds",
                            onClick = {
                                haptics.performSubtleTick()
                                onNavigateAllRecords()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionTile(
                            icon = DolilIcons.People,
                            title = DolilStrings.personInfo(language),
                            testTag = "quick_action_persons",
                            onClick = {
                                haptics.performSubtleTick()
                                onNavigatePersons()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        QuickActionTile(
                            painter = DolilIcons.mouzaMapPainter(),
                            title = DolilStrings.landInfo(language),
                            testTag = "quick_action_land",
                            onClick = {
                                haptics.performSubtleTick()
                                onNavigateLand()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionTile(
                            icon = DolilIcons.GuideBook,
                            title = DolilStrings.userGuide(language),
                            testTag = "quick_action_guide",
                            onClick = {
                                haptics.performSubtleTick()
                                onNavigateUserGuide()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Item 2 (if authorized): Office Statistics (Strictly hidden from PUBLIC_USER)
            if (RolePermissions.canViewOfficeStats(userProfile.role)) {
                item {
                    SectionHeader(
                        title = DolilStrings.officeStats(language),
                        icon = DolilIcons.Stats
                    )
                    Spacer(Modifier.height(DolilSpacing.Space2))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        StatCard(
                            icon = DolilIcons.Folder,
                            label = DolilStrings.totalDeeds(language),
                            value = officeStats.totalDeeds.toString(),
                            accentTint = MaterialTheme.colorScheme.primary,
                            onClick = { openRecordsTab(0) },
                            testTag = "stat_card_total_deeds",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = DolilIcons.Today,
                            label = DolilStrings.todayEntries(language),
                            value = officeStats.todayEntries.toString(),
                            accentTint = semantic.success,
                            onClick = { openRecordsTab(0) },
                            testTag = "stat_card_today_entries",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(DolilSpacing.Space2))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        StatCard(
                            icon = DolilIcons.Draft,
                            label = DolilStrings.drafts(language),
                            value = officeStats.draftCount.toString(),
                            accentTint = semantic.info,
                            onClick = { openRecordsTab(1) },
                            testTag = "stat_card_drafts",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = DolilIcons.PendingQueue,
                            label = DolilStrings.pendingReview(language),
                            value = officeStats.pendingReviewCount.toString(),
                            accentTint = semantic.warning,
                            onClick = { openRecordsTab(2) },
                            testTag = "stat_card_pending_review",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Item 2 or 3: Recent Authorized Records header
            item {
                SectionHeader(
                    title = DolilStrings.recentRecords(language),
                    actionLabel = DolilStrings.allDeeds(language),
                    onActionClick = onNavigateAllRecords,
                    icon = DolilIcons.History
                )
            }

            when (deedsState) {
                is UiState.Loading -> {
                    item {
                        DolilLoadingSkeleton(count = 2)
                    }
                }
                is UiState.Error -> {
                    item { DolilErrorBanner(message = deedsState.message) }
                }
                is UiState.Success -> {
                    val recent = deedsState.data.take(5)
                    if (recent.isEmpty()) {
                        item {
                            DolilEmptyState(
                                icon = DolilIcons.Document,
                                title = dolilStr(R.string.empty_records_title),
                                subtitle = dolilStr(R.string.empty_records_body),
                                actionLabel = DolilStrings.navAddDeed(language),
                                onAction = onNavigateNewDeed
                            )
                        }
                    } else {
                        items(recent, key = { it.id }) { deed ->
                            RecordCard(
                                deed = deed,
                                language = language,
                                isDemoMode = isGuestMode,
                                onClick = { onOpenDeedDetail(deed.id) }
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null
) {
    val semantic = MaterialTheme.dolilColors
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DolilRadius.Card)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (isFocused) {
            BorderStroke(DolilComponentTokens.FocusStrokeWidth, semantic.focusRing)
        } else {
            BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder)
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DolilSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(DolilIconSize.Md)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(DolilIconSize.Md)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentTint: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.dolilColors
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DolilRadius.Card)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isFocused) {
            BorderStroke(DolilComponentTokens.FocusStrokeWidth, semantic.focusRing)
        } else {
            BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder)
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(DolilSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.editorialNumeric(),
                    color = accentTint,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentTint,
                        modifier = Modifier.size(DolilIconSize.Sm)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
