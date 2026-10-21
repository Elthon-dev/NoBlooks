package com.elthon.optilauncher.ui.panel

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.core.panel.PANEL_AUTO_HIDE_MS
import com.elthon.optilauncher.core.panel.MultitaskMode
import com.elthon.optilauncher.core.panel.PanelFace
import com.elthon.optilauncher.core.panel.PanelNotification
import com.elthon.optilauncher.core.panel.PillStatus
import com.elthon.optilauncher.core.panel.RecentApp
import com.elthon.optilauncher.core.panel.StopVerdict
import com.elthon.optilauncher.data.rememberPillStatus
import com.elthon.optilauncher.ui.components.Glyph
import com.elthon.optilauncher.ui.components.Hairline
import com.elthon.optilauncher.ui.components.IconGlyph
import com.elthon.optilauncher.ui.components.NbText
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbDim
import com.elthon.optilauncher.ui.theme.NbMotion
import com.elthon.optilauncher.ui.theme.NbType
import kotlinx.coroutines.delay

/**
 * The edge pill: a compact capsule that morphs into its faces.
 *
 * The pill is the always-present form — a status strip (clock + battery) above five
 * buttons: **Home**, **Recents**, **Alerts**, **Habits**, **Access**. Home jumps home;
 * the others *morph the pill itself* into their list, each with an **×** in the
 * header that collapses back to the pill without hiding it. Access opens the window
 * requests: Split, Float, and the three-app picker.
 *
 * Two behaviours are deliberate:
 *  - **It does not hide out from under a finger.** While a touch is held anywhere on
 *    the surface, `interacting` is true and the idle timer is suspended. The timer
 *    only restarts once the finger lifts. See `PanelState.interacting`.
 *  - **Surfaces are solid** and the pill hugs its content — wallpaper stays visible.
 *
 * Motion is critically damped or emphasized-duration only (`NbMotion`); the animation
 * value is read in the layout/draw phases, never during composition.
 */
@Composable
fun ControlPanel(
    visible: Boolean,
    face: PanelFace,
    recents: List<RecentApp>,
    habits: List<RecentApp>,
    notifications: List<PanelNotification>,
    notificationsGranted: Boolean,
    notificationsConnected: Boolean,
    allApps: List<RecentApp>,
    multitaskMode: MultitaskMode,
    selectedPackages: List<String>,
    lastStop: StopVerdict?,
    reducedMotion: Boolean,
    onHome: () -> Unit,
    onShowRecents: () -> Unit,
    onShowNotifications: () -> Unit,
    onShowHabits: () -> Unit,
    onShowMultitask: () -> Unit,
    onShowMultitaskPicker: (MultitaskMode) -> Unit,
    onToggleMultitaskSelection: (String) -> Unit,
    onLaunchMultitask: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRebindNotification: () -> Unit,
    onCollapse: () -> Unit,
    onOpenApp: (RecentApp) -> Unit,
    onStopApp: (RecentApp) -> Unit,
    onOpenNotification: (PanelNotification) -> Unit,
    onDismissNotification: (PanelNotification) -> Unit,
    onReply: (PanelNotification, String) -> Unit,
    onInteractingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * False in the system overlay: an overlay window cannot take focus, so a text
     * field there would never raise the keyboard. The reply affordance is hidden
     * rather than shown-but-broken.
     */
    allowReply: Boolean = true
) {
    val colors = LocalNbColors.current
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenHeightDp < configuration.screenWidthDp

    var contentHeightPx by remember { mutableIntStateOf(0) }

    // Always start from nothing and mount the first frame invisible. The launcher can
    // compose this surface with the panel already visible (returning Home while the
    // overlay pill is up); snapping straight to the open state is the one case where
    // the reveal reads as a pop. The effect below owns the rise for every mount.
    var showContent by remember { mutableStateOf(false) }
    var animateOpen by remember { mutableFloatStateOf(0f) }

    // The pill is "open" whenever it is visible; the *face* controls how far it grows.
    val progressState = animateFloatAsState(
        targetValue = animateOpen,
        animationSpec = if (reducedMotion) {
            spring(stiffness = Spring.StiffnessHigh)
        } else if (visible) {
            NbMotion.enterSpring
        } else {
            NbMotion.exitSpring
        },
        label = "panelProgress"
    )

    LaunchedEffect(visible, reducedMotion) {
        if (reducedMotion) {
            showContent = visible
            animateOpen = if (visible) 1f else 0f
            return@LaunchedEffect
        }
        if (visible) {
            showContent = true
            withFrameNanos { }
            var waited = 0
            while (contentHeightPx <= 0 && waited < MAX_MEASURE_FRAMES) {
                withFrameNanos { }
                waited++
            }
            animateOpen = 1f
        } else {
            animateOpen = 0f
            var guard = 0
            while (progressState.value > 0.004f && guard < MAX_EXIT_FRAMES) {
                withFrameNanos { }
                guard++
            }
            showContent = false
        }
    }

    // The 5-second idle timer. It pauses whenever the panel is being interacted with
    // (a finger is down) — a panel that vanishes mid-tap is the opposite of "always
    // there". `interacting` is fed back from the pointer handler below.
    var interacting by remember { mutableStateOf(false) }
    LaunchedEffect(visible, face, interacting) {
        // The Access picker is a multi-step flow; the contract already suspends
        // auto-hide there, and the UI must not undercut it with a local timer.
        val timedFace = face != PanelFace.MULTITASK && face != PanelFace.MULTITASK_PICKER
        if (visible && timedFace && !interacting) {
            delay(PANEL_AUTO_HIDE_MS)
            onDismiss()
        }
    }

    if (!showContent) return

    val density = LocalDensity.current
    val targetWidth = if (landscape) NbDim.ControlWidthLandscape else NbDim.EdgePillWidth
    val targetWidthPx = with(density) { targetWidth.roundToPx() }
    val maxHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim: soft, never opaque — the wallpaper must stay underneath.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progressState.value * 0.42f }
                .background(colors.scrim)
                .clickable(onClick = onDismiss)
        )

        val positionModifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = NbDim.ScreenInset, start = NbDim.ScreenInset, end = NbDim.ScreenInset)
            .offset { IntOffset(0, ((1f - progressState.value) * 40f).toInt()) }
            .layout { measurable, constraints ->
                val maxW = constraints.maxWidth
                val maxH = maxHeightPx.coerceAtLeast(1)
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = 0,
                        maxWidth = targetWidthPx.coerceAtMost(maxW),
                        minHeight = 0,
                        maxHeight = maxH
                    )
                )
                if (contentHeightPx == 0) contentHeightPx = placeable.height
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
            .graphicsLayer {
                alpha = (progressState.value * 1.4f - 0.4f).coerceIn(0f, 1f)
                shape = RoundedCornerShape(NbDim.EdgePillCorner)
                clip = true
                shadowElevation = 26.dp.toPx()
            }
            .clip(RoundedCornerShape(NbDim.EdgePillCorner))
            .background(colors.surfaceSolid)
            // Track a held touch so the idle timer cannot fire under a finger.
            // Observed in the Initial pass and *not consumed*: a consuming gesture
            // detector here would swallow every child button's click.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.any { it.pressed }
                        if (pressed != interacting) {
                            interacting = pressed
                            onInteractingChange(pressed)
                        }
                    }
                }
            }

        Column(
            positionModifier.animateContentSize(
                // The pill *becomes* the list: animating the measured size is what
                // turns a button tap into a morph instead of a jump.
                animationSpec = if (reducedMotion) {
                    spring(stiffness = Spring.StiffnessHigh)
                } else {
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 650f)
                }
            )
        ) {
            PillStatusStrip()
            Hairline()

            if (face == PanelFace.OVERVIEW) {
                PillButtons(
                    onHome = onHome,
                    onRecents = onShowRecents,
                    onNotifications = onShowNotifications,
                    onHabits = onShowHabits,
                    onAccess = onShowMultitask
                )
            } else {
                FaceHeader(
                    face = face,
                    multitaskMode = multitaskMode,
                    onCollapse = onCollapse
                )
                Hairline()
                when (face) {
                    PanelFace.RECENTS -> RecentsBody(
                        recents = recents,
                        onOpenApp = onOpenApp,
                        onStopApp = onStopApp
                    )

                    PanelFace.NOTIFICATIONS -> NotificationsBody(
                        notifications = notifications,
                        granted = notificationsGranted,
                        connected = notificationsConnected,
                        allowReply = allowReply,
                        onOpen = onOpenNotification,
                        onDismiss = onDismissNotification,
                        onReply = onReply,
                        onOpenAccess = onOpenNotificationAccess,
                        onRebind = onRebindNotification
                    )

                    PanelFace.HABITS -> HabitsBody(
                        habits = habits,
                        onOpenApp = onOpenApp
                    )

                    PanelFace.MULTITASK -> MultitaskChooserBody(
                        onPick = onShowMultitaskPicker
                    )

                    PanelFace.MULTITASK_PICKER -> MultitaskPickerBody(
                        apps = allApps,
                        mode = multitaskMode,
                        selectedPackages = selectedPackages,
                        onToggle = onToggleMultitaskSelection,
                        onLaunch = onLaunchMultitask
                    )

                    PanelFace.OVERVIEW -> Unit
                }
            }

            if (lastStop != null) {
                Hairline()
                StopVerdictLine(lastStop)
            }
        }
    }
}

// ---------------------------------------------------------------- status strip

@Composable
private fun PillStatusStrip() {
    val colors = LocalNbColors.current
    val context = LocalContext.current

    // Reads clock + battery now, then keeps them fresh (clock ticks each minute).
    val status by rememberPillStatus(context)
    val clock = PillStatus.clock(status.hourOfDay, status.minute)
    val weekday = PillStatus.weekday(status.dayOfWeek)
    val battery = PillStatus.batteryPercent(status.batteryLevel, status.batteryScale)
    val low = PillStatus.isLow(status.batteryLevel, status.batteryScale)

    Row(
        Modifier
            .fillMaxWidth()
            .height(NbDim.EdgePillStatusHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NbText(weekday, style = NbType.weekday, color = colors.onSurfaceDim)
        Spacer(Modifier.width(8.dp))
        NbText(clock, style = NbType.pillClock, color = colors.onSurface)
        Spacer(Modifier.weight(1f))
        if (status.charging) {
            NbText("CHG", style = NbType.mono, color = colors.accent)
            Spacer(Modifier.width(6.dp))
        }
        NbText(
            battery,
            style = NbType.mono,
            color = if (low) colors.accent else colors.onSurfaceDim
        )
    }
}

// --------------------------------------------------------------------- buttons

@Composable
private fun PillButtons(
    onHome: () -> Unit,
    onRecents: () -> Unit,
    onNotifications: () -> Unit,
    onHabits: () -> Unit,
    onAccess: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PillButton(Glyph.HOME, "Home", Modifier.weight(1f), onHome)
        PillButton(Glyph.RECENTS, "Recents", Modifier.weight(1f), onRecents)
        PillButton(Glyph.BELL, "Alerts", Modifier.weight(1f), onNotifications)
        PillButton(Glyph.GRID, "Habits", Modifier.weight(1f), onHabits)
        PillButton(Glyph.SPLIT, "Access", Modifier.weight(1f), onAccess)
    }
}

@Composable
private fun PillButton(
    glyph: Glyph,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalNbColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconGlyph(glyph, colors.onSurface, Modifier.size(NbDim.EdgePillButtonIcon))
        Spacer(Modifier.height(6.dp))
        NbText(label, style = NbType.tab, color = colors.onSurface)
    }
}

// ---------------------------------------------------------------------- header

@Composable
private fun FaceHeader(
    face: PanelFace,
    multitaskMode: MultitaskMode,
    onCollapse: () -> Unit
) {
    val colors = LocalNbColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            NbText("CONTROL PANEL", style = NbType.weekday, color = colors.onSurfaceDim)
            NbText(
                text = when (face) {
                    PanelFace.RECENTS -> "Recently used"
                    PanelFace.NOTIFICATIONS -> "Missed notifications"
                    PanelFace.HABITS -> "Your frequent apps"
                    PanelFace.MULTITASK -> "Open windows"
                    PanelFace.MULTITASK_PICKER -> "${multitaskMode.title} · pick ${multitaskMode.requiredApps}"
                    PanelFace.OVERVIEW -> ""
                },
                style = NbType.bodySmall,
                color = colors.onSurface
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center
        ) {
            IconGlyph(
                glyph = Glyph.CLOSE,
                tint = colors.onSurface,
                modifier = Modifier.size(22.dp),
                contentDescription = "Back to pill"
            )
        }
    }
}

// ------------------------------------------------------------------------ body

@Composable
private fun RecentsBody(
    recents: List<RecentApp>,
    onOpenApp: (RecentApp) -> Unit,
    onStopApp: (RecentApp) -> Unit
) {
    val colors = LocalNbColors.current
    if (recents.isEmpty()) {
        PanelEmpty("No recent apps", "Apps you leave will collect here.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
        items(recents, key = { it.packageName }) { app ->
            AppRow(
                label = app.label.ifBlank { "Unknown app" },
                sub = if (app.liveProcess) "running" else null,
                onOpen = { onOpenApp(app) },
                onStop = { onStopApp(app) }
            )
        }
    }
    NbText(
        "Stop uses Shizuku; without it, open the app's settings to force stop.",
        style = NbType.mono,
        color = colors.onSurfaceDim,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun HabitsBody(
    habits: List<RecentApp>,
    onOpenApp: (RecentApp) -> Unit
) {
    val colors = LocalNbColors.current
    if (habits.isEmpty()) {
        PanelEmpty("No habits yet", "Use a few apps and they will rank here.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
        items(habits, key = { it.packageName }) { app ->
            AppRow(
                label = app.label.ifBlank { "Unknown app" },
                sub = null,
                onOpen = { onOpenApp(app) },
                onStop = null
            )
        }
    }
    NbText(
        "Ranked by how you actually use them. Tap to jump.",
        style = NbType.mono,
        color = colors.onSurfaceDim,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun AppRow(
    label: String,
    sub: String?,
    onOpen: () -> Unit,
    onStop: (() -> Unit)?
) {
    val colors = LocalNbColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            NbText(label, style = NbType.body, color = colors.onSurface)
            if (sub != null) {
                NbText(sub, style = NbType.mono, color = colors.accent)
            }
        }
        if (onStop != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                IconGlyph(
                    Glyph.STOP,
                    colors.onSurfaceDim,
                    Modifier.size(22.dp),
                    contentDescription = "Force stop $label"
                )
            }
        }
    }
}

@Composable
private fun MultitaskChooserBody(
    onPick: (MultitaskMode) -> Unit
) {
    val colors = LocalNbColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WindowModeRow(
            glyph = Glyph.SPLIT,
            title = MultitaskMode.SPLIT.title,
            description = "Pick two apps; the second is launched beside the first.",
            onClick = { onPick(MultitaskMode.SPLIT) }
        )
        WindowModeRow(
            glyph = Glyph.WINDOW,
            title = MultitaskMode.FLOAT.title,
            description = "A small movable window. Requires freeform support; many phones hide it.",
            onClick = { onPick(MultitaskMode.FLOAT) }
        )
        WindowModeRow(
            glyph = Glyph.GRID,
            title = MultitaskMode.TRIPLE.title,
            description = "Pick three apps: one requested floating, two side by side.",
            onClick = { onPick(MultitaskMode.TRIPLE) }
        )
        NbText(
            "Split and floating are requests, not guarantees — the system has the final say.",
            style = NbType.mono,
            color = colors.onSurfaceDim,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun WindowModeRow(
    glyph: Glyph,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val colors = LocalNbColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconGlyph(glyph, colors.onSurface, Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NbText(title, style = NbType.body, color = colors.onSurface)
            NbText(description, style = NbType.bodySmall, color = colors.onSurfaceDim)
        }
    }
}

@Composable
private fun MultitaskPickerBody(
    apps: List<RecentApp>,
    mode: MultitaskMode,
    selectedPackages: List<String>,
    onToggle: (String) -> Unit,
    onLaunch: () -> Unit
) {
    val colors = LocalNbColors.current
    val ready = selectedPackages.size == mode.requiredApps
    if (apps.isEmpty()) {
        PanelEmpty("No apps found", "The app list is still loading. Reopen in a moment.")
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NbText(
                "${selectedPackages.size}/${mode.requiredApps} selected",
                style = NbType.mono,
                color = if (ready) colors.accent else colors.onSurfaceDim
            )
            Spacer(Modifier.weight(1f))
            NbText(mode.title, style = NbType.mono, color = colors.onSurfaceDim)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
            items(apps, key = { it.packageName }) { app ->
                val selected = app.packageName in selectedPackages
                PickerRow(
                    label = app.label.ifBlank { "Unknown app" },
                    selected = selected,
                    enabled = selected || selectedPackages.size < mode.requiredApps,
                    onToggle = { onToggle(app.packageName) }
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (ready) colors.accent else colors.surface)
                .clickable(enabled = ready, onClick = onLaunch)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            NbText(
                if (ready) mode.action else "Select ${mode.requiredApps - selectedPackages.size} more",
                style = NbType.bodySmall,
                color = if (ready) colors.surfaceSolid else colors.onSurfaceDim
            )
        }
        NbText(
            "The layout is a request. Floating needs freeform; Android may arrange the rest itself.",
            style = NbType.mono,
            color = colors.onSurfaceDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalNbColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NbText(
            label,
            style = NbType.body,
            color = if (enabled || selected) colors.onSurface else colors.onSurfaceDim,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) colors.accent else colors.surface),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                IconGlyph(
                    Glyph.CHECK,
                    colors.surfaceSolid,
                    Modifier.size(16.dp),
                    contentDescription = "$label selected"
                )
            }
        }
    }
}

@Composable
private fun NotificationsBody(
    notifications: List<PanelNotification>,
    granted: Boolean,
    connected: Boolean,
    allowReply: Boolean,
    onOpen: (PanelNotification) -> Unit,
    onDismiss: (PanelNotification) -> Unit,
    onReply: (PanelNotification, String) -> Unit,
    onOpenAccess: () -> Unit,
    onRebind: () -> Unit
) {
    if (!granted) {
        PanelEmpty(
            "Notification access off",
            "Android only exposes notifications through this special access. " +
                "The button opens NoBlooks' own entry — some ROMs hide it from the list.",
            actionLabel = "Open access settings",
            onAction = onOpenAccess
        )
        return
    }
    if (!connected) {
        // The grant is on, but Android is not delivering events: on ColorOS/MIUI the
        // listener dies with the app's process. "Off" would be a lie, and "Nothing
        // missed" would be worse — offer the only supported reconnect request.
        PanelEmpty(
            "Listener not bound",
            "Access is granted, but Android isn't delivering events right now. " +
                "Force a reconnect; if it stays silent, allow Auto-start in App info.",
            actionLabel = "Force reconnect",
            onAction = onRebind
        )
        return
    }
    if (notifications.isEmpty()) {
        PanelEmpty("Nothing missed", "Clear, calm, and empty.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
        items(notifications, key = { it.key }) { n ->
            NotificationRow(n, allowReply, onOpen, onDismiss, onReply)
            Hairline(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun NotificationRow(
    n: PanelNotification,
    allowReply: Boolean,
    onOpen: (PanelNotification) -> Unit,
    onDismiss: (PanelNotification) -> Unit,
    onReply: (PanelNotification, String) -> Unit
) {
    val colors = LocalNbColors.current
    var replying by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(n) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        NbText(
            n.title?.takeIf { it.isNotBlank() } ?: "Notification",
            style = NbType.body,
            color = colors.onSurface,
            maxLines = 1
        )
        if (n.preview.isNotEmpty()) {
            NbText(n.preview, style = NbType.bodySmall, color = colors.onSurfaceDim, maxLines = 2)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (n.canReply && allowReply) {
                NbText(
                    "Reply",
                    style = NbType.mono,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { replying = !replying }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            NbText(
                "Dismiss",
                style = NbType.mono,
                color = colors.onSurfaceDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDismiss(n) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (replying) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = NbType.body.copy(color = colors.onSurface),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
                Spacer(Modifier.width(8.dp))
                NbText(
                    "Send",
                    style = NbType.mono,
                    color = if (draft.isBlank()) colors.onSurfaceDim else colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = draft.isNotBlank()) {
                            onReply(n, draft)
                            draft = ""
                            replying = false
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun StopVerdictLine(verdict: StopVerdict) {
    val colors = LocalNbColors.current
    val text = when (verdict) {
        StopVerdict.STOPPED -> "Stopped."
        StopVerdict.REVIVED -> "It came back — this ROM restarted it."
        StopVerdict.UNAVAILABLE -> "Shizuku not available, so nothing was stopped."
        StopVerdict.REFUSED -> "The system refused to stop it."
        StopVerdict.UNVERIFIED -> "Couldn't confirm it stopped."
    }
    NbText(
        text,
        style = NbType.bodySmall,
        color = if (verdict == StopVerdict.STOPPED) colors.onSurface else colors.accent,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun PanelEmpty(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalNbColors.current
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NbText(title, style = NbType.title, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        NbText(description, style = NbType.bodySmall, color = colors.onSurfaceDim)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            NbText(
                actionLabel,
                style = NbType.mono,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

private const val MAX_MEASURE_FRAMES = 8
private const val MAX_EXIT_FRAMES = 48
