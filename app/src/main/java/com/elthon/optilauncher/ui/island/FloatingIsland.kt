package com.elthon.optilauncher.ui.island

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
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
import com.elthon.optilauncher.ui.theme.NbDim
import com.elthon.optilauncher.ui.theme.NbType

/**
 * The signature floating island — anchored at the bottom-left of the screen.
 *
 * One component, two states: a compact pill that morphs into a panel. Every
 * geometry property derives from a single spring progress, so the animation is
 * interruptible and coherent. A second, slightly stiffer spring drives the
 * content fade/slide, which lets the silhouette lead and the content settle
 * after it — the small stagger that makes a morph read as physical rather than
 * as two crossfades stacked on a timer.
 *
 * Motion follows the physics-based guidance: springs for state changes (they
 * preserve velocity across interruptions), and the whole morph is anchored to the
 * bottom-left so it grows from where it was instead of teleporting to a centre.
 *
 * Surface contract: solid colour, no border, depth from a soft drop shadow plus
 * a faint top sheen. The scrim behind it is intentionally faint — it only has to
 * push the wallpaper back a little.
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
            enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(200)),
            exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(tween(170)),
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
                    .align(Alignment.BottomStart)
                    .padding(start = NbDim.ScreenInset, bottom = 14.dp, end = NbDim.ScreenInset),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

    // Silhouette: soft, physical, interruptible.
    val sizeProgress by transition.animateFloat(
        transitionSpec = {
            if (reducedMotion) {
                tween(0)
            } else {
                spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow)
            }
        },
        label = "islandSize"
    ) { expanded -> if (expanded) 1f else 0f }

    // Content: snappier than the silhouette, so it settles without lagging.
    val contentProgress by transition.animateFloat(
        transitionSpec = {
            if (reducedMotion) {
                tween(0)
            } else {
                spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
            }
        },
        label = "islandContent"
    ) { expanded -> if (expanded) 1f else 0f }

    val p = sizeProgress
    val c = contentProgress

    // --- geometry: one lerp drives the whole silhouette ---------------------
    val collapsedWidth = NbDim.PillWidth
    val collapsedHeight = NbDim.PillHeight
    val panelWidth = (screenWidth - NbDim.ScreenInset * 2)
        .coerceAtMost(NbDim.PanelMaxWidth)
        .coerceAtLeast(260.dp)
    val panelHeight = (screenHeight * 0.50f)
        .coerceAtMost(screenHeight - 210.dp)
        .coerceAtLeast(280.dp)

    val width = lerp(collapsedWidth, panelWidth, p)
    val height = lerp(collapsedHeight, panelHeight, p)
    val halfPill = height / 2
    val shape = RoundedCornerShape(
        topStart = lerp(halfPill, NbDim.PanelCornerTop, p),
        topEnd = lerp(halfPill, NbDim.PanelCornerTop, p),
        bottomStart = lerp(halfPill, NbDim.PanelCornerBottom, p),
        bottomEnd = lerp(halfPill, NbDim.PanelCornerBottom, p)
    )
    val elevation = lerp(10.dp, 20.dp, p)

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
            .background(colors.surfaceSolid)
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
        // Faint top sheen: the cheap trick that makes a solid surface read as a
        // physical object instead of a filled rectangle.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (colors.isDark) {
                            listOf(
                                Color.White.copy(alpha = 0.055f),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                Color.Black.copy(alpha = 0.035f),
                                Color.Transparent
                            )
                        }
                    )
                )
        )

        // --- collapsed face --------------------------------------------------
        // Pinned to the bottom-left corner of the panel, which is exactly where
        // the pill sits when collapsed, so the icons stay put while the
        // silhouette grows up and to the right around them.
        if (p < 0.999f) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(collapsedWidth)
                    .height(collapsedHeight)
                    .graphicsLayer {
                        alpha = (1f - p).coerceIn(0f, 1f)
                        translationY = 10f * p
                    }
                    .padding(horizontal = 16.dp),
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
                        .padding(horizontal = 3.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(colors.onSurfaceDim)
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
                        alpha = c.coerceIn(0f, 1f)
                        translationY = 14f * (1f - c)
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
                                (fadeIn(tween(200)) + slideInHorizontally(tween(230)) { it * dir / 5 }) togetherWith
                                    (fadeOut(tween(140)) + slideOutHorizontally(tween(170)) { -it * dir / 5 })
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
        modifier = modifier.padding(horizontal = 6.dp, vertical = 6.dp),
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
            // The active-tab wash fades in on a spring instead of snapping, so
            // switching tabs feels like the panel breathing rather than a swap.
            val wash by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = if (active) {
                    spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
                } else {
                    tween(140)
                },
                label = "tabWash"
            )
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceSolid.copy(alpha = 0.92f * wash))
                    .pointerInput(page) {
                        detectTapGestures { onSelect(page) }
                    }
                    .padding(horizontal = 13.dp, vertical = 6.dp)
                    .semantics {
                        contentDescription = "${page.label} page"
                        onClick(label = "Open ${page.label} page") {
                            onSelect(page)
                            true
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconGlyph(glyph, tint, Modifier.size(NbDim.TabIcon))
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
            .size(NbDim.PillTapTarget)
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
            IconGlyph(glyph, color, Modifier.size(NbDim.PillIcon).alpha(alpha))
        } else {
            IconGlyph(glyph, color, Modifier.size(NbDim.PillIcon))
        }
    }
}

/** Icon-only tap target inside the collapsed pill (uniform size, ripple-free). */
@Composable
private fun PillGlyph(
    glyph: Glyph,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(NbDim.PillTapTarget)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        IconGlyph(glyph, tint, Modifier.size(NbDim.PillIcon))
    }
}

/** Dp interpolation used by the morph (kept local so unit math stays readable). */
private fun lerp(from: Dp, to: Dp, fraction: Float): Dp =
    from + (to - from) * fraction.coerceIn(0f, 1f)
