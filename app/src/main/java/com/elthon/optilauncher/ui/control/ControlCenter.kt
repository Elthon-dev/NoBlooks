package com.elthon.optilauncher.ui.control

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.data.ControlKey
import com.elthon.optilauncher.data.RadioState
import com.elthon.optilauncher.data.SystemControls
import com.elthon.optilauncher.data.SystemControlSnapshot
import com.elthon.optilauncher.ui.components.Glyph
import com.elthon.optilauncher.ui.components.IconGlyph
import com.elthon.optilauncher.ui.components.NbText
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbDim
import com.elthon.optilauncher.ui.theme.NbMotion
import com.elthon.optilauncher.ui.theme.NbType
import kotlin.math.roundToInt

/**
 * The control centre that grows out of the glance widget.
 *
 * Tapping the widget does not swap one screen for another: the widget itself
 * *becomes* the panel. The morph starts from the widget's measured bounds
 * ([originX],[originY],[originWidth],[originHeight]) and expands to a fixed
 * target slot at the same top-right anchor, its corner radius easing from the
 * widget's rounded card to the panel's softer radius and its content fading in
 * only once the silhouette is most of the way there.
 *
 * The tiles are *honest*: a third-party launcher cannot toggle a radio, so a tap
 * opens the platform's own switch panel, and the footer says so. Read the state,
 * don't fake the switch.
 */
@Composable
fun ControlCenterOverlay(
    open: Boolean,
    originX: Float,
    originY: Float,
    originWidth: Float,
    originHeight: Float,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val controls = remember(context) { SystemControls(context) }

    // Live state, refreshed every time the panel opens. Reading it on open (not on
    // every frame) keeps the radio probes off the animation path.
    var snapshot by remember { mutableStateOf(SystemControlSnapshot(RadioState.UNKNOWN, RadioState.UNKNOWN, RadioState.UNKNOWN, RadioState.UNKNOWN)) }
    LaunchedEffect(open) {
        if (open) snapshot = controls.snapshot()
    }

    // Mount, then move: the tiles compose one frame before the spring starts, so
    // the open path never pays a first-layout cost mid-morph.
    var showContent by remember { mutableStateOf(open) }
    var animateOpen by remember { mutableFloatStateOf(if (open) 1f else 0f) }
    LaunchedEffect(open, reducedMotion) {
        if (reducedMotion) {
            showContent = open
            animateOpen = if (open) 1f else 0f
            return@LaunchedEffect
        }
        if (open) {
            showContent = true
            withFrameNanos { }
            animateOpen = 1f
        } else {
            animateOpen = 0f
            withFrameNanos { }
            showContent = false
        }
    }

    if (!showContent) return

    BackHandler(enabled = open) { onDismiss() }

    val progress by animateFloatAsState(
        targetValue = animateOpen,
        animationSpec = if (reducedMotion) {
            spring(stiffness = Spring.StiffnessHigh)
        } else if (open) {
            NbMotion.enterSpring
        } else {
            NbMotion.exitSpring
        },
        label = "controlProgress"
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val targetWidthPx = with(density) {
            maxWidth.coerceAtMost(360.dp).toPx()
        }
        val targetHeightPx = with(density) {
            val wide = maxWidth > maxHeight
            (if (wide) 230.dp else 300.dp).toPx()
        }
        val targetX = screenWidthPx - targetWidthPx - with(density) { NbDim.ScreenInset.toPx() }
        val targetY = originY

        // Scrim: pure tap-catcher, faded with progress so a partial back gesture
        // never leaves a half-black screen behind.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(colors.scrim)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )

        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val p = progress.coerceIn(0f, 1f)
                    val w = lerp(originWidth, targetWidthPx, p).roundToInt().coerceAtLeast(1)
                    val h = lerp(originHeight, targetHeightPx, p).roundToInt().coerceAtLeast(1)
                    val x = lerp(originX, targetX, p).roundToInt()
                    val y = lerp(originY, targetY, p).roundToInt()
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = w,
                            maxWidth = w,
                            minHeight = h,
                            maxHeight = h
                        )
                    )
                    layout(w, h) { placeable.placeRelative(x, y) }
                }
                .graphicsLayer {
                    val p = progress.coerceIn(0f, 1f)
                    val corner = lerp(with(density) { NbDim.WidgetCorner.toPx() }, with(density) { NbDim.PanelCornerTop.toPx() }, p)
                    shape = RoundedCornerShape(corner.toDp())
                    clip = true
                    shadowElevation = lerp(with(density) { 18.dp.toPx() }, with(density) { 30.dp.toPx() }, p)
                }
                .background(colors.surfaceSolid)
                .semantics { contentDescription = "Control centre" }
        ) {
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

            ControlContent(
                snapshot = snapshot,
                // Read the animation in the draw phase, never in composition: the
                // content fades from ~35% progress but must not recompose per frame.
                progress = { progress },
                onTile = { key -> controls.openPanel(key) }
            )
        }
    }
}

@Composable
private fun ControlContent(
    snapshot: SystemControlSnapshot,
    progress: () -> Float,
    onTile: (ControlKey) -> Unit
) {
    val colors = LocalNbColors.current
    Column(
        modifier = Modifier
            .graphicsLayer {
                // Content resolves a little after the silhouette so the tiles do
                // not appear to have been there all along.
                alpha = (progress() * 1.35f - 0.35f).coerceIn(0f, 1f)
            }
            .padding(NbDim.ScreenInset)
    ) {
        NbText(
            text = "CONTROL CENTRE",
            style = NbType.weekday,
            color = colors.onSurfaceDim
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlTile(
                glyph = Glyph.WIFI,
                label = "Wi-Fi",
                state = snapshot.wifi,
                modifier = Modifier.weight(1f),
                onClick = { onTile(ControlKey.WIFI) }
            )
            ControlTile(
                glyph = Glyph.BLUETOOTH,
                label = "Bluetooth",
                state = snapshot.bluetooth,
                modifier = Modifier.weight(1f),
                onClick = { onTile(ControlKey.BLUETOOTH) }
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlTile(
                glyph = Glyph.CELLULAR,
                label = "Mobile data",
                state = snapshot.cellular,
                modifier = Modifier.weight(1f),
                onClick = { onTile(ControlKey.CELLULAR) }
            )
            ControlTile(
                glyph = Glyph.AIRPLANE,
                label = "Airplane",
                state = snapshot.airplane,
                modifier = Modifier.weight(1f),
                onClick = { onTile(ControlKey.AIRPLANE) }
            )
        }
        Spacer(Modifier.weight(1f))
        NbText(
            text = "Tiles open Android's own switches.",
            style = NbType.bodySmall,
            color = colors.onSurfaceDim
        )
    }
}

@Composable
private fun ControlTile(
    glyph: Glyph,
    label: String,
    state: RadioState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalNbColors.current
    val on = state == RadioState.ON
    val interaction = remember { MutableInteractionSource() }
    val tint = if (on) colors.accent else colors.onSurfaceDim
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceSolid.copy(alpha = 0.6f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconGlyph(glyph, tint, Modifier.size(22.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (on) colors.accent else colors.outline)
            )
        }
        Column {
            NbText(text = label, style = NbType.bodySmall, color = colors.onSurface, maxLines = 1)
            NbText(text = stateLabel(state), style = NbType.weekday, color = tint, maxLines = 1)
        }
    }
}

private fun stateLabel(state: RadioState): String = when (state) {
    RadioState.ON -> "ON"
    RadioState.OFF -> "OFF"
    RadioState.UNKNOWN -> "UNKNOWN"
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
