package com.example.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.compose.material3.VerticalDivider
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.MatchReason
import com.example.domain.model.SyncState

enum class PrimaryDestination(
    val route: String,
    val testTag: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "nav_home", DolilIcons.HomeSelected, DolilIcons.HomeUnselected),
    SEARCH("search", "nav_search", DolilIcons.SearchSelected, DolilIcons.SearchUnselected),
    RECORDS("records", "nav_records", DolilIcons.RecordsSelected, DolilIcons.RecordsUnselected),
    PROFILE("profile", "nav_profile", DolilIcons.ProfileSelected, DolilIcons.ProfileUnselected);

    fun label(lang: AppLanguage): String = when (this) {
        HOME -> DolilStrings.navHome(lang)
        SEARCH -> DolilStrings.navSearch(lang)
        RECORDS -> DolilStrings.navRecords(lang)
        PROFILE -> DolilStrings.navProfile(lang)
    }
}

fun isReducedMotionEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}.getOrDefault(false)

fun HapticFeedback.performSubtleTick() {
    runCatching { performHapticFeedback(HapticFeedbackType.TextHandleMove) }
}

fun HapticFeedback.performConfirmPulse() {
    runCatching { performHapticFeedback(HapticFeedbackType.LongPress) }
}

@Composable
fun DolilExtendedAddFab(
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "nav_add_deed"
) {
    val haptics = LocalHapticFeedback.current
    ExtendedFloatingActionButton(
        onClick = {
            haptics.performSubtleTick()
            onClick()
        },
        icon = {
            Icon(
                imageVector = DolilIcons.Add,
                contentDescription = null
            )
        },
        text = {
            Text(
                text = DolilStrings.navAddDeed(language),
                style = MaterialTheme.typography.labelLarge
            )
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(18.dp),
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    )
}

@Composable
fun DolilPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "primary_button",
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performSubtleTick()
            onClick()
        },
        enabled = enabled && !loading,
        shape = RoundedCornerShape(DolilRadius.Button),
        contentPadding = PaddingValues(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = DolilComponentTokens.MinTouchTarget)
            .testTag(testTag)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(DolilSpacing.Space2))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DolilSpacing.Space2))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DolilSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "secondary_button",
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(DolilRadius.Button),
        contentPadding = PaddingValues(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = DolilComponentTokens.MinTouchTarget)
            .testTag(testTag)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(DolilSpacing.Space2))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DolilSpacing.Space2))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DolilOutlinedDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "outlined_destructive_button",
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(DolilRadius.Button),
        contentPadding = PaddingValues(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = DolilComponentTokens.MinTouchTarget)
            .testTag(testTag)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DolilSpacing.Space2))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DolilDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "destructive_button",
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performConfirmPulse()
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(DolilRadius.Button),
        contentPadding = PaddingValues(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = DolilComponentTokens.MinTouchTarget)
            .testTag(testTag)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DolilSpacing.Space2))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DolilTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    centered: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable () -> Unit = {}
) {
    val titleContent: @Composable () -> Unit = {
        Column(
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    val navIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("top_bar_back_button")
            ) {
                Icon(
                    imageVector = DolilIcons.ArrowBack,
                    contentDescription = dolilStr(R.string.action_back)
                )
            }
        }
    }

    if (centered) {
        CenterAlignedTopAppBar(
            title = titleContent,
            navigationIcon = navIcon,
            actions = { actions() },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    } else {
        TopAppBar(
            title = titleContent,
            navigationIcon = navIcon,
            actions = { actions() },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
fun DolilBottomNavigationBar(
    currentRoute: String,
    language: AppLanguage,
    onNavigate: (PrimaryDestination) -> Unit,
    onDestinationPositioned: ((PrimaryDestination, Rect) -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val semantic = MaterialTheme.dolilColors
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = DolilComponentTokens.HairlineWidth,
            color = semantic.cardBorder
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = DolilElevation.Level0
        ) {
            PrimaryDestination.entries.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) haptics.performSubtleTick()
                        onNavigate(dest)
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label(language)
                        )
                    },
                    label = {
                        Text(
                            text = dest.label(language),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                onDestinationPositioned?.invoke(dest, coords.boundsInRoot())
                            }
                        }
                        .testTag(dest.testTag)
                )
            }
        }
    }
}

@Composable
fun DolilNavigationRail(
    currentRoute: String,
    language: AppLanguage,
    onNavigate: (PrimaryDestination) -> Unit,
    onDestinationPositioned: ((PrimaryDestination, Rect) -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val semantic = MaterialTheme.dolilColors
    Row {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Spacer(Modifier.height(DolilSpacing.Space4))
            PrimaryDestination.entries.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationRailItem(
                    selected = selected,
                    onClick = {
                        if (!selected) haptics.performSubtleTick()
                        onNavigate(dest)
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label(language)
                        )
                    },
                    label = {
                        Text(
                            text = dest.label(language),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                onDestinationPositioned?.invoke(dest, coords.boundsInRoot())
                            }
                        }
                        .testTag(dest.testTag)
                )
            }
        }
        VerticalDivider(
            thickness = DolilComponentTokens.HairlineWidth,
            color = semantic.cardBorder
        )
    }
}

@Composable
fun DolilSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "search_input",
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onSearchAction: () -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = DolilIcons.SearchSelected,
                contentDescription = placeholder,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = trailingContent,
        singleLine = true,
        shape = RoundedCornerShape(DolilRadius.Pill),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .testTag(testTag)
    )
}

@Composable
fun DolilOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String = "text_field",
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        isError = isError,
        readOnly = readOnly,
        enabled = enabled,
        singleLine = singleLine,
        supportingText = if (!supportingText.isNullOrBlank()) {
            { Text(supportingText, style = MaterialTheme.typography.bodySmall) }
        } else null,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(DolilRadius.Input),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            disabledLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.primary,
            disabledSupportingTextColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
fun DolilTonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val semantic = MaterialTheme.dolilColors
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DolilRadius.Card)
    Surface(
        shape = shape,
        color = containerColor,
        border = if (isFocused) {
            BorderStroke(DolilComponentTokens.FocusStrokeWidth, semantic.focusRing)
        } else {
            BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder)
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(DolilSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3),
            content = content
        )
    }
}

@Composable
fun FormSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    val semantic = MaterialTheme.dolilColors
    Surface(
        shape = RoundedCornerShape(DolilRadius.Card),
        color = containerColor,
        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(DolilSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(DolilIconSize.Sm)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun DolilSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(
                imageVector = DolilIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun StatusChip(
    status: DeedStatus,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.dolilColors
    val (bg, fg, icon) = when (status) {
        DeedStatus.APPROVED -> Triple(semantic.successContainer, semantic.onSuccessContainer, DolilIcons.Approve)
        DeedStatus.PENDING_REVIEW -> Triple(semantic.warningContainer, semantic.onWarningContainer, DolilIcons.Pending)
        DeedStatus.DRAFT -> Triple(semantic.infoContainer, semantic.onInfoContainer, DolilIcons.Draft)
        DeedStatus.SOFT_DELETED -> Triple(semantic.archivedContainer, semantic.onArchivedContainer, DolilIcons.Archive)
    }
    val iconTint = when (status) {
        DeedStatus.APPROVED -> semantic.success
        DeedStatus.PENDING_REVIEW -> semantic.warning
        DeedStatus.DRAFT -> semantic.info
        DeedStatus.SOFT_DELETED -> semantic.archived
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(DolilComponentTokens.BadgeCorner),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(DolilIconSize.Xs)
            )
            Text(
                text = DolilStrings.statusText(status, language),
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RecordCard(
    deed: DeedRecord,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    matchReason: MatchReason? = null,
    isDemoMode: Boolean = false,
    isSelected: Boolean = false
) {
    val semantic = MaterialTheme.dolilColors
    val dateStr = DolilStrings.formatDate(deed.registrationDateMillis, language)
    val firstParcel = deed.parcels.firstOrNull()
    val parcelText = if (firstParcel != null && (firstParcel.dagNumber.isNotBlank() || firstParcel.khatianNumber.isNotBlank())) {
        if (language == AppLanguage.BN) {
            "খতিয়ান ${firstParcel.khatianNumber.ifBlank { "-" }} • দাগ ${firstParcel.dagNumber.ifBlank { "-" }}"
        } else {
            "Khatian ${firstParcel.khatianNumber.ifBlank { "-" }} • Dag ${firstParcel.dagNumber.ifBlank { "-" }}"
        }
    } else ""

    var isFocused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(DolilRadius.Card)
    val targetBg = when {
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val containerColor by animateColorAsState(targetValue = targetBg, label = "record_card_bg")

    Surface(
        shape = cardShape,
        color = containerColor,
        border = when {
            isFocused -> BorderStroke(DolilComponentTokens.FocusStrokeWidth, semantic.focusRing)
            isSelected -> BorderStroke(DolilComponentTokens.HighlightBorderWidth, MaterialTheme.colorScheme.primary)
            else -> BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder)
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("record_card_${deed.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Tier 1 + Tier 2: Identity, Status & Party Transfer
            Column(
                modifier = Modifier.padding(
                    start = DolilSpacing.Space4,
                    end = DolilSpacing.Space4,
                    top = DolilSpacing.Space4,
                    bottom = DolilSpacing.Space3
                ),
                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = DolilStrings.deedNumberFormatted(deed.deedNumber, deed.deedYear, language),
                            style = MaterialTheme.typography.titleMedium.tabularNumerals(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${DolilStrings.deedTypeText(deed.deedType, language)} • $dateStr",
                            style = MaterialTheme.typography.bodySmall.tabularNumerals(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(DolilSpacing.Space2))

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusChip(status = deed.status, language = language)
                        if (isDemoMode) {
                            Surface(
                                color = semantic.warningContainer,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.testTag("demo_data_badge_${deed.id}")
                            ) {
                                Text(
                                    text = dolilStr(R.string.guest_demo_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.onWarningContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Tier 2: Party Transfer Line with Material Symbols Transfer Arrow
                if (deed.primaryGrantorName.isNotBlank() || deed.primaryGranteeName.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = deed.primaryGrantorName.ifBlank { "-" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            painter = DolilIcons.partyTransferPainter(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = deed.primaryGranteeName.ifBlank { "-" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (matchReason != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = DolilStrings.matchReasonText(matchReason, language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Tier 3: Tonal Land Metadata Footer (Location + Khatian/Dag + Sync + Area Badge)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val locStr = listOf(deed.mouzaName, deed.upazilaName)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    val metaLine = listOf(locStr, parcelText)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                        .ifBlank { "-" }

                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall.tabularNumerals(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(DolilSpacing.Space2))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val syncIcon = when (deed.syncState) {
                            SyncState.SYNCED -> DolilIcons.CloudDone
                            SyncState.PENDING_UPLOAD -> DolilIcons.CloudUpload
                            SyncState.CONFLICT -> DolilIcons.SyncProblem
                            SyncState.LOCAL_ONLY_DRAFT -> DolilIcons.CloudOff
                        }
                        val syncColor = if (deed.syncState == SyncState.CONFLICT) {
                            semantic.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(
                            imageVector = syncIcon,
                            contentDescription = DolilStrings.syncText(deed.syncState, language),
                            tint = syncColor,
                            modifier = Modifier.size(DolilIconSize.Xs)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(DolilComponentTokens.BadgeCorner)
                        ) {
                            Text(
                                text = "${deed.totalAreaDecimalText} ${DolilStrings.shotokUnit(language)}",
                                style = MaterialTheme.typography.labelMedium.tabularNumerals(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(DolilIconSize.Sm)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
        }
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = DolilIcons.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(DolilIconSize.Xs)
                )
            }
        }
    }
}

@Composable
fun DolilGuestBanner(
    visible: Boolean,
    onCreateAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.dolilColors
    AnimatedVisibility(visible = visible) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier
                .fillMaxWidth()
                .testTag("guest_mode_banner")
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    Icon(
                        imageVector = DolilIcons.Explore,
                        contentDescription = null,
                        tint = semantic.info,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = dolilStr(R.string.guest_banner_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onCreateAccountClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("guest_create_account_button")
                    ) {
                        Text(
                            text = dolilStr(R.string.guest_banner_cta),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(
                    thickness = DolilComponentTokens.HairlineWidth,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
fun DolilOfflineBanner(
    visible: Boolean,
    pendingCount: Int,
    language: AppLanguage
) {
    val semantic = MaterialTheme.dolilColors
    AnimatedVisibility(visible = visible || pendingCount > 0) {
        Surface(
            color = if (visible) semantic.warningContainer else semantic.infoContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
            ) {
                Icon(
                    imageVector = if (visible) DolilIcons.CloudOff else DolilIcons.CloudUpload,
                    contentDescription = null,
                    tint = if (visible) semantic.warning else semantic.info,
                    modifier = Modifier.size(18.dp)
                )
                val msg = when {
                    visible && language == AppLanguage.BN ->
                        "অফলাইন মোড — খসড়া ডিভাইসে সংরক্ষিত থাকছে ($pendingCount টি অপেক্ষমাণ)"
                    visible ->
                        "Offline mode — changes stay safe locally ($pendingCount pending sync)"
                    language == AppLanguage.BN ->
                        "$pendingCount টি রেকর্ড Cloud Sync-এর অপেক্ষায় রয়েছে"
                    else ->
                        "$pendingCount record(s) queued for cloud sync"
                }
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (visible) semantic.onWarningContainer else semantic.onInfoContainer
                )
            }
        }
    }
}

@Composable
fun DolilErrorBanner(
    message: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.dolilColors
    Surface(
        color = semantic.errorContainer,
        shape = RoundedCornerShape(DolilRadius.Input),
        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.error.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("error_banner")
    ) {
        Row(
            modifier = Modifier.padding(DolilSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
        ) {
            Icon(
                imageVector = DolilIcons.Error,
                contentDescription = null,
                tint = semantic.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = dolilStr(R.string.action_ok),
                        color = semantic.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun DolilEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.dolilColors
    Surface(
        shape = RoundedCornerShape(DolilRadius.Card),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DolilSpacing.Space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(DolilSpacing.Space2))
                DolilPrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    testTag = "empty_state_action"
                )
            }
        }
    }
}

@Composable
fun DolilLoadingSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 3
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val baseColor = MaterialTheme.colorScheme.surfaceContainer
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val shimmerBrush: Brush = if (reducedMotion) {
        Brush.linearGradient(listOf(baseColor, baseColor))
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
        val offset by transition.animateFloat(
            initialValue = 0f,
            targetValue = 900f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_x"
        )
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(offset - 300f, 0f),
            end = Offset(offset, 240f)
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
    ) {
        Text(
            text = dolilStr(R.string.loading_fetching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        repeat(count) {
            Surface(
                shape = RoundedCornerShape(DolilRadius.Card),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth()
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(shimmerBrush)
                        )
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(shimmerBrush)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

@Composable
fun DolilConfirmationDialog(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val semantic = MaterialTheme.dolilColors
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = DolilIcons.Warning,
                contentDescription = null,
                tint = if (isDestructive) semantic.error else MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = {
                    haptics.performConfirmPulse()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) semantic.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isDestructive) semantic.onError else MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dialog_confirm_button")
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dialog_dismiss_button")
            ) {
                Text(dismissText)
            }
        }
    )
}
