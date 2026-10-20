package com.elthon.optilauncher.ui.panel

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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.core.panel.PANEL_AUTO_HIDE_MS
import com.elthon.optilauncher.core.panel.PanelFace
import com.elthon.optilauncher.core.panel.PanelNotification
import com.elthon.optilauncher.core.panel.RecentApp
import com.elthon.optilauncher.core.panel.StopVerdict
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
 * The edge control panel: three jump buttons (Home / Recents / Notifications) plus
 * the launcher's own app-switcher and missed-notification lists.
 *
 * It lives inside the launcher's own composition (Pass 1), so it is fully
 * verifiable. The three-finger gesture and the always-on pill are the accessibility
 * service's job; this composable is what that gesture *reveals*.
 *
 * Layout is orientation-aware: a single column on a phone in portrait, a wider
 * two-column body on landscape / wide screens. It never stretches to fill the
 * screen — it hugs its content so wallpaper stays visible around it.
 */
@Composable
fun ControlPanel(
    visible: Boolean,
    face: PanelFace,
    armed: Boolean,
    recents: List<RecentApp>,
    notifications: List<PanelNotification>,
    notificationsGranted: Boolean,
    lastStop: StopVerdict?,
    reducedMotion: Boolean,
    onHome: () -> Unit,
    onRecents: () -> Unit,
    onNotifications: () -> Unit,
    onShowRecents: () -> Unit,
    onShowNotifications: () -> Unit,
    onOpenApp: (RecentApp) -> Unit,
    onStopApp: (RecentApp) -> Unit,
    onOpenNotification: (PanelNotification) -> Unit,
    onDismissNotification: (PanelNotification) -> Unit,
    onReply: (PanelNotification, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenHeightDp < configuration.screenWidthDp

    var contentHeightPx by remember { mutableIntStateOf(0) }
    var showContent by remember { mutableStateOf(visible) }
    var animateOpen by remember { mutableFloatStateOf(if (visible) 1f else 0f) }

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

    // The 5-second idle timer. Rendered here so it lives exactly as long as the
    // panel is on screen; any interaction recomposes `visible` and restarts it.
    LaunchedEffect(visible) {
        if (visible) {
            delay(PANEL_AUTO_HIDE_MS)
            onDismiss()
        }
    }

    if (!showContent) return

    val density = LocalDensity.current
    val targetWidth = if (landscape) NbDim.ControlWidthLandscape else NbDim.ControlWidthPortrait
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
                shape = RoundedCornerShape(NbDim.PanelCornerTop)
                clip = true
                shadowElevation = 26.dp.toPx()
            }
            .clip(RoundedCornerShape(NbDim.PanelCornerTop))
            .background(colors.surfaceSolid)

        Column(positionModifier) {
            PanelHeader(
                face = face,
                armed = armed,
                onShowRecents = onShowRecents,
                onShowNotifications = onShowNotifications
            )
            Hairline()

            when (face) {
                PanelFace.OVERVIEW -> OverviewBody(
                    landscape = landscape,
                    onHome = onHome,
                    onRecents = onRecents,
                    onNotifications = onNotifications
                )

                PanelFace.RECENTS -> RecentsBody(
                    recents = recents,
                    onOpenApp = onOpenApp,
                    onStopApp = onStopApp
                )

                PanelFace.NOTIFICATIONS -> NotificationsBody(
                    notifications = notifications,
                    granted = notificationsGranted,
                    onOpen = onOpenNotification,
                    onDismiss = onDismissNotification,
                    onReply = onReply
                )
            }

            if (lastStop != null) {
                Hairline()
                StopVerdictLine(lastStop)
            }
        }
    }
}

@Composable
private fun PanelHeader(
    face: PanelFace,
    armed: Boolean,
    onShowRecents: () -> Unit,
    onShowNotifications: () -> Unit
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
                    PanelFace.OVERVIEW -> if (armed) "Three-finger tap is live" else "Enable the gesture in Settings"
                    PanelFace.RECENTS -> "Recently used"
                    PanelFace.NOTIFICATIONS -> "Missed notifications"
                },
                style = NbType.bodySmall,
                color = if (armed || face != PanelFace.OVERVIEW) colors.onSurface else colors.accent
            )
        }
        PanelFaceTab(Glyph.RECENTS, face == PanelFace.RECENTS, "Recently used apps", onShowRecents)
        Spacer(Modifier.width(8.dp))
        PanelFaceTab(Glyph.BELL, face == PanelFace.NOTIFICATIONS, "Missed notifications", onShowNotifications)
    }
}

@Composable
private fun PanelFaceTab(
    glyph: Glyph,
    active: Boolean,
    description: String,
    onClick: () -> Unit
) {
    val colors = LocalNbColors.current
    IconGlyph(
        glyph = glyph,
        tint = if (active) colors.accent else colors.onSurfaceDim,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentDescription = description
    )
}

@Composable
private fun OverviewBody(
    landscape: Boolean,
    onHome: () -> Unit,
    onRecents: () -> Unit,
    onNotifications: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            JumpButton(Glyph.HOME, "Home", Modifier.weight(1f), onHome)
            JumpButton(Glyph.RECENTS, "Recents", Modifier.weight(1f), onRecents)
            JumpButton(Glyph.BELL, "Alerts", Modifier.weight(1f), onNotifications)
        }
        Spacer(Modifier.height(12.dp))
        NbText(
            "Home returns here. Recents opens the app switcher — or our own list if this ROM won't " +
                "show the system one. Alerts pulls down the shade.",
            style = NbType.bodySmall,
            color = LocalNbColors.current.onSurfaceDim
        )
        if (landscape) Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun JumpButton(
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
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconGlyph(glyph, colors.onSurface, Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        NbText(label, style = NbType.tab, color = colors.onSurface)
    }
}

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
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenApp(app) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    NbText(
                        // Never leak a package name: unlabeled entries were already
                        // dropped by RecentsRepository, so a blank label just means
                        // "we don't know it" — say that, don't show `com.*`.
                        app.label.ifBlank { "Unknown app" },
                        style = NbType.body,
                        color = colors.onSurface
                    )
                    if (app.liveProcess) {
                        NbText("running", style = NbType.mono, color = colors.accent)
                    }
                }
                IconGlyph(
                    Glyph.STOP,
                    colors.onSurfaceDim,
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onStopApp(app) },
                    contentDescription = "Force stop ${app.label}"
                )
            }
        }
    }
    NbText(
        "Long-press stops an app only with Shizuku. Otherwise open its settings to force stop.",
        style = NbType.mono,
        color = colors.onSurfaceDim,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun NotificationsBody(
    notifications: List<PanelNotification>,
    granted: Boolean,
    onOpen: (PanelNotification) -> Unit,
    onDismiss: (PanelNotification) -> Unit,
    onReply: (PanelNotification, String) -> Unit
) {
    val colors = LocalNbColors.current
    if (!granted) {
        PanelEmpty(
            "Notification access off",
            "Grant notification access in Settings to see what you missed here."
        )
        return
    }
    if (notifications.isEmpty()) {
        PanelEmpty("Nothing missed", "Clear, calm, and empty.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
        items(notifications, key = { it.key }) { n ->
            NotificationRow(n, onOpen, onDismiss, onReply)
            Hairline(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun NotificationRow(
    n: PanelNotification,
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
            // Same rule as recents: resolve a real app name, never a package id.
            n.title?.takeIf { it.isNotBlank() } ?: "Notification",
            style = NbType.body,
            color = colors.onSurface,
            maxLines = 1
        )
        if (n.preview.isNotEmpty()) {
            NbText(n.preview, style = NbType.bodySmall, color = colors.onSurfaceDim, maxLines = 2)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (n.canReply) {
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
private fun PanelEmpty(title: String, description: String) {
    val colors = LocalNbColors.current
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NbText(title, style = NbType.title, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        NbText(description, style = NbType.bodySmall, color = colors.onSurfaceDim)
    }
}

private const val MAX_MEASURE_FRAMES = 8
private const val MAX_EXIT_FRAMES = 48
