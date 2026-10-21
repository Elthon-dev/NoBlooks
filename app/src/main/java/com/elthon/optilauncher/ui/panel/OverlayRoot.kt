package com.elthon.optilauncher.ui.panel

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.elthon.optilauncher.core.panel.PanelEvent
import com.elthon.optilauncher.core.theme.LauncherTheme
import com.elthon.optilauncher.data.NotificationAccess
import com.elthon.optilauncher.data.NotificationBridge
import com.elthon.optilauncher.data.OverlayPanelActions
import com.elthon.optilauncher.data.PanelBus
import com.elthon.optilauncher.ui.theme.NoBlooksTheme

/**
 * The composable the system-overlay service hosts.
 *
 * It renders the same [ControlPanel] the in-app surface uses, but reading the
 * process-wide [PanelBus] instead of the ViewModel — because in the overlay there
 * is no Activity and no ViewModel. All system actions route through
 * [OverlayPanelActions], which the service owns.
 *
 * Reply is disabled here: an overlay window cannot take focus, so a text field
 * would never raise the keyboard. Better to omit it than to show it broken.
 */
@Composable
fun OverlayRoot(actions: OverlayPanelActions) {
    val context = LocalContext.current
    val panel by PanelBus.state.collectAsState()
    val recents by PanelBus.recents.collectAsState()
    val habits by PanelBus.habits.collectAsState()
    val apps by PanelBus.apps.collectAsState()
    val notifications by NotificationBridge.notifications.collectAsState()
    val notificationsConnected by NotificationBridge.connected.collectAsState()
    // The grant can change while the overlay lives (the user grants it in Settings,
    // then goes back to another app). Re-read on each reveal / face change instead
    // of polling: it is a cheap settings read, not a binder transaction.
    val notificationsGranted = remember(panel.visible, panel.face) {
        notificationsConnected || NotificationAccess.isGranted(context)
    }

    // The system animation scale is the honest "reduced motion" signal available
    // outside the launcher's own settings.
    val reducedMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    // Match the in-app chooser and the MultitaskLauncher preflight.
    val freeformSupported = remember(context) {
        context.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT
        )
    }
    val lowRam = remember(context) {
        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager)?.isLowRamDevice == true
    }

    NoBlooksTheme(theme = LauncherTheme.fallback()) {
        ControlPanel(
            visible = panel.visible,
            face = panel.face,
            recents = recents,
            habits = habits,
            notifications = notifications,
            notificationsGranted = notificationsGranted,
            notificationsConnected = notificationsConnected,
            allApps = apps,
            multitaskMode = panel.multitaskMode,
            selectedPackages = panel.selectedPackages,
            lastStop = panel.lastStop,
            reducedMotion = reducedMotion,
            freeformSupported = freeformSupported,
            lowRam = lowRam,
            allowReply = false,
            onHome = { actions.dispatch(PanelEvent.Home) },
            onShowRecents = { actions.dispatch(PanelEvent.SystemRecents) },
            onShowNotifications = { actions.dispatch(PanelEvent.SystemNotifications) },
            onShowHabits = { actions.openHabits() },
            onShowMultitask = { actions.dispatch(PanelEvent.ShowMultitaskChooser) },
            onShowMultitaskPicker = { actions.dispatch(PanelEvent.ShowMultitaskPicker(it)) },
            onToggleMultitaskSelection = { actions.dispatch(PanelEvent.ToggleMultitaskSelection(it)) },
            onLaunchMultitask = { actions.dispatch(PanelEvent.LaunchMultitask) },
            onOpenNotificationAccess = { actions.openNotificationAccess() },
            onRebindNotification = { actions.rebindNotificationListener() },
            onCollapse = { actions.dispatch(PanelEvent.CollapseToPill) },
            onOpenApp = { actions.openApp(it) },
            onStopApp = { actions.stopApp(it) },
            onOpenNotification = { actions.openAppPackage(it.packageName) },
            onDismissNotification = { actions.dismissNotification(it.key) },
            onReply = { _, _ -> },
            onInteractingChange = { actions.dispatch(PanelEvent.SetInteracting(it)) },
            onDismiss = { actions.dispatch(PanelEvent.Hide) }
        )
    }
}
