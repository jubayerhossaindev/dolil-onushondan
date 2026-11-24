package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DolilComponentTokens
import com.example.core.designsystem.DolilIcons
import com.example.core.designsystem.DolilPrimaryButton
import com.example.core.designsystem.DolilRadius
import com.example.core.designsystem.DolilSpacing
import com.example.core.designsystem.DolilTopBar
import com.example.core.designsystem.dolilColors
import com.example.core.designsystem.dolilStr
import com.example.core.designsystem.isReducedMotionEnabled
import com.example.core.designsystem.performSubtleTick

private data class GuideTopic(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen(
    onReplayTour: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val haptics = LocalHapticFeedback.current

    val semantic = MaterialTheme.dolilColors

    val topics = remember {
        listOf(
            GuideTopic(DolilIcons.AddCircle, R.string.guide_sec_1_title, R.string.guide_sec_1_body),
            GuideTopic(DolilIcons.SearchSelected, R.string.guide_sec_2_title, R.string.guide_sec_2_body),
            GuideTopic(DolilIcons.Filter, R.string.guide_sec_3_title, R.string.guide_sec_3_body),
            GuideTopic(DolilIcons.Explore, R.string.guide_sec_4_title, R.string.guide_sec_4_body),
            GuideTopic(DolilIcons.Verified, R.string.guide_sec_5_title, R.string.guide_sec_5_body)
        )
    }

    Scaffold(
        topBar = {
            DolilTopBar(
                title = dolilStr(R.string.guide_title),
                subtitle = dolilStr(R.string.guide_subtitle),
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
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .testTag("user_guide_screen"),
                contentPadding = PaddingValues(DolilSpacing.Space4),
                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
            ) {
                itemsIndexed(topics, key = { _, topic -> topic.titleRes }) { index, topic ->
                    val title = dolilStr(topic.titleRes)
                    var expanded by rememberSaveable(topic.titleRes) { mutableStateOf(index == 0) }
                    val stateDesc = if (expanded) dolilStr(R.string.state_expanded) else dolilStr(R.string.state_collapsed)

                    Surface(
                        shape = RoundedCornerShape(DolilRadius.Card),
                        color = if (expanded) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DolilRadius.Card))
                            .then(
                                if (reducedMotion) Modifier else Modifier.animateContentSize(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .semantics { stateDescription = stateDesc }
                                    .clickable(role = Role.Button) {
                                        haptics.performSubtleTick()
                                        expanded = !expanded
                                    }
                                    .padding(DolilSpacing.Space4),
                                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = topic.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (expanded) DolilIcons.ExpandLess else DolilIcons.ExpandMore,
                                    contentDescription = stateDesc,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = expanded) {
                                Text(
                                    text = dolilStr(topic.bodyRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = DolilSpacing.Space4,
                                        end = DolilSpacing.Space4,
                                        bottom = DolilSpacing.Space4
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(DolilSpacing.Space2))
                    DolilPrimaryButton(
                        text = dolilStr(R.string.action_replay_tour),
                        onClick = onReplayTour,
                        icon = DolilIcons.PlayCircle,
                        testTag = "guide_replay_tour_button",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(DolilSpacing.Space4))
                }
            }
        }
    }
}
