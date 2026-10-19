package com.elthon.optilauncher.ui.island

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
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
import com.elthon.optilauncher.ui.theme.NbMotion
import com.elthon.optilauncher.ui.theme.NbType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The signature floating island — anchored at the middle of the left edge.
 *
 * One component, two states: a compact pill that morphs into a panel. Because
 * the container is centred on its own left edge, the pill does not travel when
 * the panel opens: the silhouette grows outward from the pill's own centre, which
 * is what makes the morph read as one object changing shape rather than two
 * views swapping places.
 *
 * Motion: every spec is either a critically damped spring (damping ratio 1.0 —
 * settles in the shortest time *without oscillating*) or a Material emphasized
 * duration curve. Nothing overshoots its target, which is the difference between
 * "smooth" and "cartoony"; the springs still carry velocity across an
 * interrupted open/close, which a duration curve cannot.
 *
 * Performance shape: the animated progress values are never read during
 * composition. Geometry is resolved in the layout phase and the visual
 * properties (corner radius, clip, elevation, alpha, translation) in the draw
 * phase, so a frame of the morph costs a measure and a draw — not a recomposition
 * of the island, its tabs, and whatever page is open. The two faces are mounted
 * from discrete state (with a delayed unmount so the cross-fade still plays), so
 * the idle home screen composes one small pill and nothing else.
 *
 * Surface contract: solid colour, no border, depth from a soft drop shadow plus a
 * faint top sheen. The scrim behind it stays faint — it only has to push the
 * wallpaper back a little.
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
            enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(NbMotion.Short4)),
            exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(tween(NbMotion.Short4)),
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

        // --- 2. island stage: clears the keyboard and the gesture area --------
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = NbDim.ScreenInset),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Offer only makes sense on the collapsed pill, and it animates
                // its own height so it nudges the island instead of jumping it.
                AnimatedVisibility(
                    visible = state.expansion == Expansion.COLLAPSED && suggestion != null,
                    enter = fadeIn(tween(NbMotion.Short4, easing = NbMotion.EmphasizedDecelerate)) +
                        expandVertically(tween(NbMotion.Medium2, easing = NbMotion.EmphasizedDecelerate)),
                    exit = fadeOut(tween(140, easing = NbMotion.EmphasizedAccelerate)) +
                        shrinkVertically(tween(NbMotion.Short4, easing = NbMotion.EmphasizedAccelerate))
                ) {
                    suggestion?.invoke()
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

            // Feedback sits on its own anchor at the bottom of the screen so a
            // launch note can never shift the island that produced it.
            TransientMessage(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = NbDim.ScreenInset,
                        end = NbDim.ScreenInset,
                        bottom = 28.dp
                    )
            )
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
    val density = LocalDensity.current

    val transition = updateTransition(targetState = state.isExpanded, label = "island")

    // Silhouette: critically damped, so it never bounces past the target.
    val sizeProgress = transition.animateFloat(
        transitionSpec = {
            when {
                reducedMotion -> tween(0)
                targetState -> NbMotion.enterSpring
                else -> NbMotion.exitSpring
            }
        },
        label = "islandSize"
    ) { expanded -> if (expanded) 1f else 0f }

    // Content: a designed duration with a small stagger, so it settles *after*
    // the container has already started moving rather than riding every frame of
    // the spring. Emphasized decelerate on the way in, accelerate on the way out.
    val contentProgress = transition.animateFloat(
        transitionSpec = {
            when {
                reducedMotion -> tween(0)
                targetState -> tween(
                    durationMillis = NbMotion.Medium1,
                    delayMillis = 60,
                    easing = NbMotion.EmphasizedDecelerate
                )
                else -> tween(NbMotion.Short4, easing = NbMotion.EmphasizedAccelerate)
            }
        },
        label = "islandContent"
    ) { expanded -> if (expanded) 1f else 0f }

    // --- geometry, resolved once and then only read in later phases --------
    // Panel sizing leaves the top fifth of the screen free for the widget and
    // never asks for more than half the height.
    val geometry = remember(density, screenWidth, screenHeight) {
        val panelWidthDp = (screenWidth - NbDim.ScreenInset * 2).coerceAtMost(NbDim.PanelMaxWidth)
        val maxPanelHeight = (screenHeight - 210.dp).coerceAtLeast(240.dp)
        val panelHeightDp = (screenHeight * 0.5f).coerceIn(240.dp, maxPanelHeight)
        IslandGeometry(
            collapsedWidth = with(density) { NbDim.PillWidth.toPx() },
            collapsedHeight = with(density) { NbDim.PillHeight.toPx() },
            panelWidth = with(density) { panelWidthDp.toPx() },
            panelHeight = with(density) { panelHeightDp.toPx() },
            cornerTop = with(density) { NbDim.PanelCornerTop.toPx() },
            cornerBottom = with(density) { NbDim.PanelCornerBottom.toPx() },
            shadowCollapsed = with(density) { 10.dp.toPx() },
            shadowExpanded = with(density) { 22.dp.toPx() }
        )
    }

    // The cross-fade needs both faces mounted while it plays, but the idle home
    // screen must not compose an app grid behind a pill. Discrete state decides
    // what is mounted; the animation only decides what is visible.
    var showExpanded by remember { mutableStateOf(state.isExpanded) }
    var showPill by remember { mutableStateOf(!state.isExpanded) }
    LaunchedEffect(state.isExpanded, reducedMotion) {
        val settle = if (reducedMotion) 0L else 520L
        if (state.isExpanded) {
            showExpanded = true
            delay(settle)
            showPill = false
        } else {
            showPill = true
            delay(settle)
            showExpanded = false
        }
    }

    Box(
        modifier = Modifier
            // Size lives in the layout phase: no composition, and the parent
            // re-places the island by measurement alone.
            .layout { measurable, constraints ->
                val p = sizeProgress.value.coerceIn(0f, 1f)
                val width = lerp(geometry.collapsedWidth, geometry.panelWidth, p)
                    .roundToInt()
                    .coerceAtLeast(1)
                val height = lerp(geometry.collapsedHeight, geometry.panelHeight, p)
                    .roundToInt()
                    .coerceAtLeast(1)
                val placeable = measurable.measure(
                    constraints.copy(minWidth = width, maxWidth = width, minHeight = height, maxHeight = height)
                )
                layout(width, height) { placeable.placeRelative(0, 0) }
            }
            // Shape, clip and elevation live in the draw phase.
            .graphicsLayer {
                val p = sizeProgress.value.coerceIn(0f, 1f)
                val pillRadius = geometry.collapsedHeight / 2f
                val topRadius = lerp(pillRadius, geometry.cornerTop, p)
                val bottomRadius = lerp(pillRadius, geometry.cornerBottom, p)
                shape = RoundedCornerShape(
                    topStart = topRadius.toDp(),
                    topEnd = topRadius.toDp(),
                    bottomStart = bottomRadius.toDp(),
                    bottomEnd = bottomRadius.toDp()
                )
                clip = true
                shadowElevation = lerp(geometry.shadowCollapsed, geometry.shadowExpanded, p)
            }
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
                            listOf(Color.White.copy(alpha = 0.055f), Color.Transparent)
                        } else {
                            listOf(Color.Black.copy(alpha = 0.035f), Color.Transparent)
                        }
                    )
                )
        )

        // --- collapsed face --------------------------------------------------
        // Aligned to the container's own centre-left, which is exactly where the
        // container sits when it is the size of the pill — so the glyphs hold
        // their position while the silhouette grows around them.
        if (showPill) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(NbDim.PillWidth)
                    .height(NbDim.PillHeight)
                    .graphicsLayer {
                        val p = sizeProgress.value.coerceIn(0f, 1f)
                        alpha = 1f - p
                        translationY = 8.dp.toPx() * p
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
        if (showExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val c = contentProgress.value.coerceIn(0f, 1f)
                        alpha = c
                        translationY = 10.dp.toPx() * (1f - c)
                    }
            ) {
                IslandTabs(
                    current = state.page,
                    reducedMotion = reducedMotion,
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
                                (fadeIn(tween(NbMotion.Short4, easing = NbMotion.EmphasizedDecelerate)) +
                                    slideInHorizontally(
                                        tween(NbMotion.Medium1, easing = NbMotion.EmphasizedDecelerate)
                                    ) { it * dir / 5 }) togetherWith
                                    (fadeOut(tween(140, easing = NbMotion.EmphasizedAccelerate)) +
                                        slideOutHorizontally(
                                            tween(NbMotion.Short4, easing = NbMotion.EmphasizedAccelerate)
                                        ) { -it * dir / 5 })
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
    reducedMotion: Boolean,
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
            // The active-tab wash fades rather than springs: this is a small
            // state change, and a bounce here would be the cartoon again.
            val wash by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = if (reducedMotion) {
                    tween(0)
                } else if (active) {
                    tween(NbMotion.Medium2, easing = NbMotion.EmphasizedDecelerate)
                } else {
                    tween(NbMotion.Short4, easing = NbMotion.EmphasizedAccelerate)
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
            // Live pulse, but ONLY while audio is genuinely being captured — and
            // read in the draw phase, so it costs no recomposition while it runs.
            val pulse = rememberInfiniteTransition(label = "micPulse")
            val pulseAlpha = pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = NbMotion.Standard),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "micAlpha"
            )
            IconGlyph(
                glyph = glyph,
                tint = color,
                modifier = Modifier
                    .size(NbDim.PillIcon)
                    .graphicsLayer { alpha = pulseAlpha.value }
            )
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

/** Pixel geometry for the morph, resolved once per screen size. */
private data class IslandGeometry(
    val collapsedWidth: Float,
    val collapsedHeight: Float,
    val panelWidth: Float,
    val panelHeight: Float,
    val cornerTop: Float,
    val cornerBottom: Float,
    val shadowCollapsed: Float,
    val shadowExpanded: Float
)

/** Float interpolation used by the morph (kept local so unit math stays readable). */
private fun lerp(from: Float, to: Float, fraction: Float): Float =
    from + (to - from) * fraction.coerceIn(0f, 1f)
