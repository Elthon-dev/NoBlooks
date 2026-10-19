package com.elthon.optilauncher.ui.island

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.core.island.Expansion
import com.elthon.optilauncher.core.island.IslandEvent
import com.elthon.optilauncher.core.island.IslandPage
import com.elthon.optilauncher.core.island.IslandState
import com.elthon.optilauncher.core.island.index
import com.elthon.optilauncher.core.mic.MicIndicator
import com.elthon.optilauncher.core.mic.MicState
import com.elthon.optilauncher.ui.components.Glyph
import com.elthon.optilauncher.ui.components.Hairline
import com.elthon.optilauncher.ui.components.IconGlyph
import com.elthon.optilauncher.ui.components.NbText
import com.elthon.optilauncher.ui.components.TransientMessage
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbColors
import com.elthon.optilauncher.ui.theme.NbType

/**
 * The signature floating island.
 *
 * One component, two states: a compact pill that morphs — width, height, corner
 * curvature, elevation, shadow, surface alpha and content opacity all driven by a
 * single spring progress — into a tall droplet-silhouette panel. Every geometry
 * property derives from that one progress value, so the animation stays
 * interruptible and coherent, and cheap: no per-frame subtree rebuilds, no blur.
 *
 * Layering contract:
 *  - the outside-tap scrim sits *below* the island stage, so panel touches are
 *    never mistaken for outside taps;
 *  - the stage itself has no pointer handling, so taps fall through to the scrim;
 *  - Back collapses the island before anything else happens.
 */
@Composable
fun FloatingIsland(
    state: IslandState,
    mic: MicIndicator,
    reducedMotion: Boolean,
    message: String?,
    onEvent: (IslandEvent) -> Unit,
    onMicAction: () -> Unit,
    modifier: Modifier = Modifier,
    suggestion: (@Composable () -> Unit)? = null,
    pageContent: @Composable (IslandPage) -> Unit
) {
    val colors = LocalNbColors.current

    // Android Back collapses the island first; the launcher stays behind it.
    BackHandler(enabled = state.isExpanded) { onEvent(IslandEvent.Back) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // --- 1. outside tap surface (only while expanded) -------------------
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(180)),
            exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(tween(160)),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .pointerInput(Unit) {
                        detectTapGestures { onEvent(IslandEvent.OutsideTap) }
                    }
                    .semantics { contentDescription = "Close the expanded island" }
            )
        }

        // --- 2. island stage: clears the keyboard and gesture area ------------
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (message != null) {
                    TransientMessage(message = message)
                }
                if (state.expansion == Expansion.COLLAPSED && suggestion != null) {
                    suggestion()
                }
                IslandBody(
                    state = state,
                    mic = mic,
                    colors = colors,
                    reducedMotion = reducedMotion,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    onEvent = onEvent,
                    onMicAction = onMicAction,
                    pageContent = pageContent
                )
            }
        }
    }
}

@Composable
private fun IslandBody(
    state: IslandState,
    mic: MicIndicator,
    colors: NbColors,
    reducedMotion: Boolean,
    screenWidth: Dp,
    screenHeight: Dp,
    onEvent: (IslandEvent) -> Unit,
    onMicAction: () -> Unit,
    pageContent: @Composable (IslandPage) -> Unit
) {
    val transition = updateTransition(targetState = state.isExpanded, label = "island")
    val progress by transition.animateFloat(
        transitionSpec = {
            if (reducedMotion) {
                tween(1)
            } else {
                spring(dampingRatio = 0.90f, stiffness = Spring.StiffnessMediumLow)
            }
        },
        label = "islandProgress"
    ) { expanded -> if (expanded) 1f else 0f }

    val p = progress

    // --- geometry: one lerp drives the whole silhouette ---------------------
    val maxExpandedWidth = (screenWidth - 30.dp).coerceAtMost(440.dp).coerceAtLeast(240.dp)
    val collapsedWidth = 148.dp
    val collapsedHeight = 54.dp
    val expandedHeight = (screenHeight * 0.64f).coerceAtMost(screenHeight - 40.dp).coerceAtLeast(300.dp)

    val width = lerp(collapsedWidth, maxExpandedWidth, p)
    val height = lerp(collapsedHeight, expandedHeight, p)
    val halfPill = height / 2
    val shape = RoundedCornerShape(
        topStart = lerp(halfPill, 44.dp, p),
        topEnd = lerp(halfPill, 44.dp, p),
        bottomStart = lerp(halfPill, 34.dp, p),
        bottomEnd = lerp(halfPill, 34.dp, p)
    )
    val elevation = lerp(7.dp, 26.dp, p)

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow
            )
            .clip(shape)
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.outline), shape)
            .semantics {
                contentDescription = if (state.isExpanded) {
                    "NoBlooks island, expanded, ${state.page.label} page"
                } else {
                    "NoBlooks island, collapsed. Activate to open apps and settings"
                }
                onClick(label = if (state.isExpanded) "Collapse island" else "Expand island") {
                    onEvent(if (state.isExpanded) IslandEvent.Collapse else IslandEvent.Expand)
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Expand island") {
                        onEvent(IslandEvent.Expand)
                        true
                    },
                    CustomAccessibilityAction("Collapse island") {
                        onEvent(IslandEvent.Collapse)
                        true
                    }
                )
            }
            .pointerInput(state.isExpanded) {
                // Whole-pill tap only while collapsed; expanded content owns its input.
                if (!state.isExpanded) {
                    detectTapGestures { onEvent(IslandEvent.Toggle) }
                }
            }
    ) {
        // --- collapsed face --------------------------------------------------
        if (p < 0.999f) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - p).coerceIn(0f, 1f)
                        translationY = -14f * p
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                PillGlyph(
                    glyph = Glyph.SEARCH,
                    contentDescription = "Search apps",
                    tint = colors.onSurface,
                    onClick = { onEvent(IslandEvent.SearchGesture) }
                )
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(colors.outline)
                )
                MicGlyph(mic = mic, onClick = onMicAction, tint = colors.onSurface)
            }
        }

        // --- expanded face ---------------------------------------------------
        if (p > 0.001f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = p.coerceIn(0f, 1f)
                        translationY = 26f * (1f - p)
                    }
            ) {
                IslandTabs(
                    current = state.page,
                    onSelect = { onEvent(IslandEvent.SelectPage(it)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Hairline(color = colors.outline)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AnimatedContent(
                        targetState = state.page,
                        transitionSpec = {
                            val dir = if (targetState.index() >= initialState.index()) 1 else -1
                            if (reducedMotion) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                (fadeIn(tween(180)) + slideInHorizontally(tween(210)) { it * dir / 5 }) togetherWith
                                    (fadeOut(tween(130)) + slideOutHorizontally(tween(160)) { -it * dir / 5 })
                            }
                        },
                        label = "islandPages"
                    ) { page ->
                        Box(Modifier.fillMaxSize()) {
                            pageContent(page)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandTabs(
    current: IslandPage,
    onSelect: (IslandPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (page in IslandPage.ordered) {
            val active = page == current
            val tint = if (active) colors.accent else colors.onSurfaceDim
            val glyph = when (page) {
                IslandPage.APPS -> Glyph.GRID
                IslandPage.HABITS -> Glyph.CHART
                IslandPage.WALLPAPER -> Glyph.IMAGE
                IslandPage.SETTINGS -> Glyph.SLIDERS
            }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .let { if (active) it.background(colors.surfaceSolid.copy(alpha = 0.55f)) else it }
                    .pointerInput(page) {
                        detectTapGestures { onSelect(page) }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics {
                        contentDescription = "${page.label} page"
                        onClick(label = "Open ${page.label} page") {
                            onSelect(page)
                            true
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                IconGlyph(glyph, tint, Modifier.size(18.dp))
                NbText(page.label, style = NbType.tab, color = tint, maxLines = 1)
            }
        }
    }
}

/**
 * Mic affordance inside the collapsed pill. The glyph must never imply
 * "listening" unless audio is really being captured (see [MicIndicator]).
 */
@Composable
private fun MicGlyph(
    mic: MicIndicator,
    onClick: () -> Unit,
    tint: Color
) {
    val colors = LocalNbColors.current
    val recording = mic.state == MicState.RECORDING
    val glyph = if (mic.state == MicState.PERMISSION_DENIED) Glyph.MIC_OFF else Glyph.MIC
    val color = when {
        recording -> colors.accent
        mic.state == MicState.PERMISSION_DENIED -> colors.onSurfaceDim.copy(alpha = 0.65f)
        else -> tint.copy(alpha = 0.85f)
    }
    val description = when (mic.state) {
        MicState.RECORDING -> "Microphone recording — activate for microphone settings"
        MicState.READY -> "Microphone permission granted, not recording — open microphone settings"
        MicState.PERMISSION_DENIED -> "Microphone permission denied — open microphone settings"
        MicState.ERROR -> "Microphone unavailable — open microphone settings"
        MicState.INACTIVE -> "Microphone not used by NoBlooks — open microphone settings"
    }
    Box(
        Modifier
            .size(44.dp)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (recording) {
            // Live pulse, but ONLY while audio is genuinely being captured.
            val pulse = rememberInfiniteTransition(label = "micPulse")
            val alpha by pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "micAlpha"
            )
            IconGlyph(glyph, color, Modifier.size(20.dp).alpha(alpha))
        } else {
            IconGlyph(glyph, color, Modifier.size(20.dp))
        }
    }
}

/** Icon-only tap target inside the collapsed pill (44dp, ripple-free). */
@Composable
private fun PillGlyph(
    glyph: Glyph,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        IconGlyph(glyph, tint, Modifier.size(19.dp))
    }
}

/** Dp interpolation used by the morph (kept local so unit math stays readable). */
private fun lerp(from: Dp, to: Dp, fraction: Float): Dp =
    from + (to - from) * fraction.coerceIn(0f, 1f)
