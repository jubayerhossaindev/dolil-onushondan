package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DolilComponentTokens
import com.example.core.designsystem.DolilIcons
import com.example.core.designsystem.DolilRadius
import com.example.core.designsystem.DolilSpacing
import com.example.core.designsystem.dolilColors
import com.example.core.designsystem.dolilStr
import com.example.core.designsystem.isReducedMotionEnabled
import com.example.core.designsystem.performSubtleTick

enum class TourTargetId {
    DASHBOARD_HOME,
    SEARCH_INPUT,
    ADD_DEED_TAB,
    RECORDS_TAB,
    FILTER_SORT,
    PROFILE_TAB
}

class TourTargetsState {
    private val boundsMap = mutableStateMapOf<TourTargetId, Rect>()

    fun register(id: TourTargetId, rect: Rect) {
        if (!rect.isEmpty) {
            boundsMap[id] = rect
        }
    }

    fun getBounds(id: TourTargetId): Rect? = boundsMap[id]
}

fun Modifier.tourTarget(targets: TourTargetsState?, id: TourTargetId): Modifier = composed {
    if (targets == null) {
        this
    } else {
        this.onGloballyPositioned { coords ->
            if (coords.isAttached) {
                targets.register(id, coords.boundsInRoot())
            }
        }
    }
}

private data class TourStepSpec(
    val targetId: TourTargetId,
    val titleRes: Int,
    val bodyRes: Int
)

private val TOUR_STEPS = listOf(
    TourStepSpec(TourTargetId.DASHBOARD_HOME, R.string.tour_step_1_title, R.string.tour_step_1_body),
    TourStepSpec(TourTargetId.SEARCH_INPUT, R.string.tour_step_2_title, R.string.tour_step_2_body),
    TourStepSpec(TourTargetId.ADD_DEED_TAB, R.string.tour_step_3_title, R.string.tour_step_3_body),
    TourStepSpec(TourTargetId.RECORDS_TAB, R.string.tour_step_4_title, R.string.tour_step_4_body),
    TourStepSpec(TourTargetId.FILTER_SORT, R.string.tour_step_5_title, R.string.tour_step_5_body),
    TourStepSpec(TourTargetId.PROFILE_TAB, R.string.tour_step_6_title, R.string.tour_step_6_body)
)

fun tourTargetForStep(stepIndex: Int): TourTargetId =
    TOUR_STEPS[stepIndex.coerceIn(0, TOUR_STEPS.lastIndex)].targetId

@Composable
fun ProductTourOverlay(
    visible: Boolean,
    currentStep: Int,
    targets: TourTargetsState,
    isOfficeUser: Boolean,
    isGuestMode: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit
) {
    BackHandler(enabled = visible) {
        onSkip()
    }

    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) EnterTransition.None else fadeIn(),
        exit = if (reducedMotion) ExitTransition.None else fadeOut()
    ) {
        val safeStep = currentStep.coerceIn(0, TOUR_STEPS.lastIndex)
        val spec = TOUR_STEPS[safeStep]
        val targetRect = targets.getBounds(spec.targetId)
        val primaryColor = MaterialTheme.colorScheme.primary
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("product_tour_overlay")
        ) {
            val containerHeightPx = with(density) { maxHeight.toPx() }
            val padPx = with(density) { 8.dp.toPx() }
            val cornerPx = with(density) { 16.dp.toPx() }

            // Spotlight Cutout Scrim
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                drawRect(color = Color.Black.copy(alpha = 0.64f))
                if (targetRect != null && !targetRect.isEmpty) {
                    val left = (targetRect.left - padPx).coerceAtLeast(0f)
                    val top = (targetRect.top - padPx).coerceAtLeast(0f)
                    val right = (targetRect.right + padPx).coerceAtMost(size.width)
                    val bottom = (targetRect.bottom + padPx).coerceAtMost(size.height)
                    val rectSize = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f))

                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(left, top),
                        size = rectSize,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        blendMode = BlendMode.Clear
                    )
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(left, top),
                        size = rectSize,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // Place tooltip cleanly in the opposite vertical half of the spotlighted target
            val placeAtTop = targetRect != null && !targetRect.isEmpty && targetRect.center.y > containerHeightPx * 0.52f
            val cardAlignment = if (placeAtTop) Alignment.TopCenter else Alignment.BottomCenter

            val haptics = LocalHapticFeedback.current
            val semantic = MaterialTheme.dolilColors

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space4),
                contentAlignment = cardAlignment
            ) {
                Surface(
                    shape = RoundedCornerShape(DolilRadius.Card),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            liveRegion = LiveRegionMode.Polite
                        }
                        .testTag("tour_tooltip_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
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
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("tour_step_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = DolilIcons.Explore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = dolilStr(R.string.tour_step_badge, safeStep + 1, TOUR_STEPS.size),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            TextButton(
                                onClick = onSkip,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tour_skip_button")
                            ) {
                                Text(dolilStr(R.string.action_skip))
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = dolilStr(spec.titleRes),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dolilStr(spec.bodyRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (safeStep == 3 && isOfficeUser && !isGuestMode) {
                            Surface(
                                color = MaterialTheme.dolilColors.infoContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = dolilStr(R.string.tour_step_4_office_tip),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.dolilColors.onInfoContainer,
                                    modifier = Modifier.padding(DolilSpacing.Space2)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (safeStep > 0) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.performSubtleTick()
                                        onPrevious()
                                    },
                                    shape = RoundedCornerShape(DolilRadius.Button),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("tour_prev_button")
                                ) {
                                    Icon(
                                        imageVector = DolilIcons.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = dolilStr(R.string.action_previous),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val isLast = safeStep == TOUR_STEPS.lastIndex
                            Button(
                                onClick = {
                                    haptics.performSubtleTick()
                                    onNext()
                                },
                                shape = RoundedCornerShape(DolilRadius.Button),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("tour_next_button")
                            ) {
                                Text(
                                    text = if (isLast) dolilStr(R.string.action_done) else dolilStr(R.string.action_next),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isLast) DolilIcons.Check else DolilIcons.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
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
