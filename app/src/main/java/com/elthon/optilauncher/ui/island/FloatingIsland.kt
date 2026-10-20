package com.elthon.optilauncher.ui.island

import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.core.island.Expansion
import com.elthon.optilauncher.core.island.IslandEvent
import com.elthon.optilauncher.core.island.IslandPage
import com.elthon.optilauncher.core.island.IslandState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The signature floating island — a portrait capsule anchored at the middle of
 * the left edge.
 *
 * One component, two states: the capsule slides up and opens into a panel. The
 * expanded panel has a *fixed* home: on phones it sits directly below the glance
 * instrument, tucked against the same left inset; on wide screens it shares the
 * instrument's top edge instead. Either way the silhouette ends up in the same
 * spot every time, which is what makes the layout feel composed rather than
 * wherever-the-content-ended up.
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingIsland(
    state: IslandState,
    mic: MicIndicator,
    reducedMotion: Boolean,
    message: String?,
    instrumentHeightPx: Int,
    onEvent: (IslandEvent) -> Unit,
    onMicAction: () -> Unit,
    modifier: Modifier = Modifier,
    // While the widget's control centre is open the island steps aside and holds
    // its collapsed shape: the two surfaces are companions, not competitors, so
    // they never fight for the same screen space and the island can't grow into
    // the panel. A tap on the island while open closes the panel instead.
    controlOpen: Boolean = false,
    onControlDismiss: () -> Unit = {},
    suggestion: (@Composable () -> Unit)? = null,
    pageContent: @Composable (IslandPage) -> Unit
) {
    val colors = LocalNbColors.current

    // Android 13+ predictive back: the system hands us gesture progress, so the
    // island can dip toward the edge *with* the gesture instead of waiting to be
    // told the gesture finished. Held in a state object and only read in the draw
    // phase, so a drag never recomposes the island.
    val backPreview = remember { mutableFloatStateOf(0f) }
    val keyboard = LocalSoftwareKeyboardController.current
    PredictiveBackHandler(enabled = state.isExpanded) { progress ->
        try {
            progress.collect { backPreview.floatValue = it.progress }
            // Back unwinds the innermost thing first: an active search, then the
            // island. Closing the island out from under a typed query loses work.
            if (state.page == IslandPage.APPS && state.query.isNotEmpty()) {
                backPreview.floatValue = 0f
                keyboard?.hide()
                onEvent(IslandEvent.QueryChanged(""))
            } else {
                onEvent(IslandEvent.Back)
            }
        } catch (cancel: CancellationException) {
            // Gesture cancelled: undo the preview and let the gesture owner keep going.
            backPreview.floatValue = 0f
            throw cancel
        }
    }
    // Committed back collapses the island; the preview then fades out with the
    // morph (it is multiplied by the size progress). Clear it on the next open.
    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) backPreview.floatValue = 0f
    }

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
            // The suggestion sits beside the capsule, not above it: with a portrait
            // pill there is no room above it before the widget zone, and a caption
            // next to the capsule keeps the island's anchor honest. It is anchored
            // to the stage's vertical centre, which is exactly the pill's centre.
            AnimatedVisibility(
                visible = state.expansion == Expansion.COLLAPSED && suggestion != null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = NbDim.ScreenInset + NbDim.PillWidth + 12.dp)
                    .widthIn(
                        max = (screenWidth - NbDim.ScreenInset * 2 - NbDim.PillWidth - 12.dp)
                            .coerceAtLeast(72.dp)
                    ),
                enter = fadeIn(tween(NbMotion.Short4, easing = NbMotion.EmphasizedDecelerate)) +
                    expandVertically(
                        animationSpec = tween(NbMotion.Medium2, easing = NbMotion.EmphasizedDecelerate),
                        expandFrom = Alignment.CenterVertically
                    ),
                exit = fadeOut(tween(140, easing = NbMotion.EmphasizedAccelerate)) +
                    shrinkVertically(
                        animationSpec = tween(NbMotion.Short4, easing = NbMotion.EmphasizedAccelerate),
                        shrinkTowards = Alignment.CenterVertically
                    )
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
                instrumentHeightPx = instrumentHeightPx,
                controlOpen = controlOpen,
                backPreview = { backPreview.floatValue },
                onEvent = onEvent,
                onMicAction = onMicAction,
                onControlDismiss = onControlDismiss,
                pageContent = pageContent,
                modifier = Modifier.align(Alignment.TopStart)
            )

            // --- keyboard-side actions -------------------------------------
            // Own composable so watching the IME does not recompose the island.
            KeyboardActions(
                expanded = state.isExpanded,
                onClear = { onEvent(IslandEvent.QueryChanged("")) },
                onHide = { keyboard?.hide() },
                modifier = Modifier.align(Alignment.BottomEnd)
            )

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
    instrumentHeightPx: Int,
    controlOpen: Boolean,
    backPreview: () -> Float,
    onEvent: (IslandEvent) -> Unit,
    onMicAction: () -> Unit,
    onControlDismiss: () -> Unit,
    pageContent: @Composable (IslandPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // The island steps aside while the control centre is open: a critically damped
    // glide, read only in the layout phase, so the shift costs no recomposition.
    // It is a *displacement*, not a new home — collapsing the panel animates the
    // island back to exactly where it was (the same value, reversed).
    // Kept as a State and read only in the layout phase (below), so the glide never
    // recomposes the island or its open page.
    val aside = animateFloatAsState(
        targetValue = if (controlOpen) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else NbMotion.enterSpring,
        label = "islandAside"
    )

    // Local animation target. It is normally identical to state.isExpanded, but it
    // lags by one frame when opening so the page can be composed and measured
    // before the spring starts (see the mount effect below).
    var animateExpanded by remember { mutableStateOf(state.isExpanded) }
    val transition = updateTransition(targetState = animateExpanded, label = "island")

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

    // Content: deliberately shorter than the silhouette and with no stagger.
    // Decorative choreography is not worth a single frame of not-yet-visible
    // content: the page must be ready to read (and to tap) immediately.
    val contentProgress = transition.animateFloat(
        transitionSpec = {
            when {
                reducedMotion -> tween(0)
                targetState -> tween(140, easing = NbMotion.EmphasizedDecelerate)
                else -> tween(110, easing = NbMotion.EmphasizedAccelerate)
            }
        },
        label = "islandContent"
    ) { expanded -> if (expanded) 1f else 0f }

    // --- geometry, resolved once and then only read in later phases --------
    // The expanded panel has a fixed home: under the instrument on phones, level
    // with it on wide screens, and never lower than the bottom breathing room
    // allows. Its height is therefore the only figure that varies with content.
    // Hiding the status bar zeroes statusBars, but a camera cutout still needs
    // clearance; the taller of the two wins.
    val cutoutPx = WindowInsets.displayCutout.getTop(density).toFloat()
    val statusBarPx = max(WindowInsets.statusBars.getTop(density).toFloat(), cutoutPx)
    val navBarPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    // The IME is a real bottom edge for the panel: while searching, the grid has to
    // end above the keyboard instead of continuing underneath it, or half the list
    // is unreachable no matter how far you scroll.
    val imePx = WindowInsets.ime.getBottom(density).toFloat()
    val geometry = remember(
        density,
        screenWidth,
        screenHeight,
        instrumentHeightPx,
        statusBarPx,
        navBarPx,
        imePx
    ) {
        val insetPx = with(density) { NbDim.ScreenInset.toPx() }
        val panelWidthPx = with(density) {
            (screenWidth - NbDim.ScreenInset * 2).coerceAtMost(NbDim.PanelMaxWidth).toPx()
        }
        val screenHeightPx = with(density) { screenHeight.toPx() }
        val pillWidthPx = with(density) { NbDim.PillWidth.toPx() }
        val pillHeightPx = with(density) { NbDim.PillHeight.toPx() }
        val gapPx = with(density) { NbDim.InstrumentGap.toPx() }
        val topInsetPx = statusBarPx + with(density) { NbDim.WidgetTopInset.toPx() }

        // Side by side is only safe when the panel and the instrument cannot meet.
        val sideBySide = screenWidth >= 680.dp
        val instrumentPx = if (instrumentHeightPx > 0) {
            instrumentHeightPx.toFloat()
        } else {
            with(density) { 190.dp.toPx() }
        }
        val panelTopPx = if (sideBySide) topInsetPx else topInsetPx + instrumentPx + gapPx

        // Bottom of the usable area: above the keyboard when it is up, above the
        // gesture bar when it is not.
        // Leave room under the panel for the keyboard-side action row (46dp button
        // + 12dp bottom padding) so it never covers the last result.
        val keyboardActionPx = with(density) { 58.dp.toPx() }
        val usableBottomPx = screenHeightPx - max(navBarPx, imePx) -
            if (imePx > 0f) keyboardActionPx else 0f
        val bottomRoom = usableBottomPx - panelTopPx - with(density) { 24.dp.toPx() }
        val minPanelPx = with(density) { 220.dp.toPx() }
        val maxPanelPx = bottomRoom.coerceAtLeast(minPanelPx)
        // Portrait leaves the wallpaper breathing room: the panel is ~62% of the
        // height. Landscape has almost no height to spare and plenty of width, so
        // the panel claims the whole usable column instead — the grid fills upward
        // rather than a squat card floating in a wide, empty band.
        val landscape = screenWidth > screenHeight
        val preferredPx = if (landscape) maxPanelPx else screenHeightPx * 0.62f
        val panelHeightPx = preferredPx.coerceIn(minPanelPx, maxPanelPx)

        IslandGeometry(
            leftInset = insetPx,
            collapsedWidth = pillWidthPx,
            collapsedHeight = pillHeightPx,
            panelWidth = panelWidthPx,
            panelHeight = panelHeightPx,
            centerY = (screenHeightPx - navBarPx - pillHeightPx) / 2f,
            // Aside home: bottom-left, one screen-inset above the gesture bar. Deep
            // enough that even a tall control panel cannot reach down to it.
            asideY = screenHeightPx - navBarPx - with(density) { NbDim.ScreenInset.toPx() } - pillHeightPx,
            expandedY = panelTopPx,
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
        if (state.isExpanded) {
            // Mount, then move. Composing the page (search field, grid, tab row)
            // is the one expensive frame in the open path; if the spring starts in
            // that same frame its first drawn frame arrives late — and a critically
            // damped spring is already near its target by then, which is why
            // opening looked instant while closing looked smooth (the close has no
            // composition to pay for). Pay that frame while the capsule is still
            // closed, then let the spring run at a steady frame rate.
            showExpanded = true
            if (!reducedMotion) {
                withFrameNanos { }
                delay(16)
            }
            animateExpanded = true
            // Only long enough for the cross-fade to finish; the old 520ms hold
            // kept a dead app grid composed and a hidden pill mounted far longer.
            delay(240)
            showPill = false
        } else {
            animateExpanded = false
            showPill = true
            delay(if (reducedMotion) 0L else 240L)
            showExpanded = false
        }
    }

    Box(
        modifier = modifier
            // Position and size live in the layout phase: no composition, and the
            // parent re-places the island by measurement alone. The capsule rises
            // from the left-middle to the panel's fixed anchor as it opens.
            .offset {
                val p = sizeProgress.value.coerceIn(0f, 1f)
                // Resting vertical home glides down to the aside slot while the
                // control centre is open, then back. `p` then carries it up to the
                // expanded anchor when the island opens (never while open, because
                // expansion is locked then).
                val restY = lerp(geometry.centerY, geometry.asideY, aside.value)
                IntOffset(
                    x = geometry.leftInset.roundToInt(),
                    y = lerp(restY, geometry.expandedY, p).roundToInt()
                )
            }
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
            // Shape, clip, elevation and the back-gesture preview live in the draw phase.
            .graphicsLayer {
                val p = sizeProgress.value.coerceIn(0f, 1f)
                val pillRadius = geometry.collapsedWidth / 2f
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
                // A small dip toward the screen edge that tracks the system back
                // gesture; multiplied by p so it dissolves into the collapse.
                val dip = backPreview().coerceIn(0f, 1f) * p
                scaleX = 1f - 0.045f * dip
                scaleY = 1f - 0.045f * dip
            }
            .background(colors.surfaceSolid)
            .semantics {
                contentDescription = when {
                    controlOpen -> "NoBlooks island, parked while the control centre is open"
                    state.isExpanded -> "NoBlooks island, expanded, ${state.page.label} page"
                    else -> "NoBlooks island, collapsed. Activate to open apps and settings"
                }
                onClick(
                    label = when {
                        controlOpen -> "Close the control centre"
                        state.isExpanded -> "Collapse island"
                        else -> "Expand island"
                    }
                ) {
                    when {
                        controlOpen -> onControlDismiss()
                        state.isExpanded -> onEvent(IslandEvent.Collapse)
                        else -> onEvent(IslandEvent.Expand)
                    }
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
            .pointerInput(state.isExpanded, controlOpen) {
                // Whole-pill tap only while collapsed; expanded content owns its input.
                // While the control centre is open the pill is a dismiss target.
                if (!state.isExpanded) {
                    detectTapGestures {
                        if (controlOpen) onControlDismiss() else onEvent(IslandEvent.Toggle)
                    }
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
        // A portrait stack (search, separator, mic). It is centred in the
        // container, so at rest it fills the capsule exactly; as the panel grows
        // it drifts to the middle of the silhouette and fades out.
        if (showPill) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(NbDim.PillWidth)
                    .height(NbDim.PillHeight)
                    .graphicsLayer {
                        alpha = 1f - sizeProgress.value.coerceIn(0f, 1f)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PillGlyph(
                    glyph = Glyph.SEARCH,
                    contentDescription = "Search apps",
                    tint = colors.onSurface,
                    onClick = { onEvent(IslandEvent.SearchGesture) }
                )
                Box(
                    Modifier
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
                    // Fixed final size, independent of the animated clip window.
                    // The subtree is therefore measured once with identical
                    // constraints and reused, instead of re-laying out the tabs,
                    // the page and the app grid on every frame of the morph.
                    .layout { measurable, _ ->
                        val w = geometry.panelWidth.roundToInt()
                        val h = geometry.panelHeight.roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) { placeable.placeRelative(0, 0) }
                    }
                    .graphicsLayer {
                        alpha = contentProgress.value.coerceIn(0f, 1f)
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
                        // A short cross-fade only. Page slides look like decoration
                        // and delay the thing the user actually asked for.
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                fadeIn(tween(120, easing = NbMotion.EmphasizedDecelerate)) togetherWith
                                    fadeOut(tween(90, easing = NbMotion.EmphasizedAccelerate))
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
                    tween(130, easing = NbMotion.EmphasizedDecelerate)
                } else {
                    tween(100, easing = NbMotion.EmphasizedAccelerate)
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

/**
 * The two controls that live above the keyboard while searching: clear the query
 * and put the keyboard away. They arrive and leave with the IME, ride its inset,
 * and stay right-aligned with the launcher's single screen inset.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyboardActions(
    expanded: Boolean,
    onClear: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imeVisible = WindowInsets.isImeVisible
    AnimatedVisibility(
        visible = imeVisible && expanded,
        enter = fadeIn(tween(NbMotion.Short4)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(NbMotion.Short4)),
        exit = fadeOut(tween(NbMotion.Short4)) +
            scaleOut(targetScale = 0.92f, animationSpec = tween(NbMotion.Short4)),
        modifier = modifier.padding(end = NbDim.ScreenInset, bottom = 12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyboardAction(Glyph.CLOSE, "Clear search", onClear)
            KeyboardAction(Glyph.KEYBOARD, "Hide keyboard", onHide)
        }
    }
}

/**
 * A small, solid circular action shown above the keyboard. The shadow does the
 * separating, in keeping with the rest of the launcher's borderless surfaces.
 */
@Composable
private fun KeyboardAction(glyph: Glyph, description: String, onClick: () -> Unit) {
    val colors = LocalNbColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(46.dp)
            .shadow(elevation = if (pressed) 4.dp else 12.dp, shape = CircleShape)
            .background(colors.surfaceSolid, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        IconGlyph(glyph, colors.onSurface, Modifier.size(20.dp))
    }
}

/** Pixel geometry for the morph, resolved once per screen size. */
private data class IslandGeometry(
    val leftInset: Float,
    val collapsedWidth: Float,
    val collapsedHeight: Float,
    val panelWidth: Float,
    val panelHeight: Float,
    /** Top of the capsule when collapsed (left-edge middle). */
    val centerY: Float,
    /** Where the capsule slides to when the control centre is open: low on the
     *  left edge, well clear of the top-right panel. */
    val asideY: Float,
    /** Fixed top of the expanded panel — under the instrument, or level with it. */
    val expandedY: Float,
    val cornerTop: Float,
    val cornerBottom: Float,
    val shadowCollapsed: Float,
    val shadowExpanded: Float
)

/** Float interpolation used by the morph (kept local so unit math stays readable). */
private fun lerp(from: Float, to: Float, fraction: Float): Float =
    from + (to - from) * fraction.coerceIn(0f, 1f)
