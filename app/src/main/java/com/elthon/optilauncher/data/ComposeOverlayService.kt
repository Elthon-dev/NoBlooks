package com.elthon.optilauncher.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.elthon.optilauncher.R
import com.elthon.optilauncher.core.panel.PanelEvent
import com.elthon.optilauncher.core.panel.PanelFace
import com.elthon.optilauncher.core.panel.RecentApp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the always-on edge pill and the control panel as a system overlay, so they
 * work over every app — not just inside the launcher window.
 *
 * Why a foreground service: on API 34+ an overlay added from a *background* service
 * is torn down after a short grace period, silently. `specialUse` is the honest
 * foreground type — this is not media, location, or a call.
 *
 * Why it re-checks [Settings.canDrawOverlays] every start: on ColorOS (and MIUI) a
 * granted overlay permission can still be ignored by the ROM, and the window simply
 * never appears. We report the permission state; we never claim the overlay showed.
 */
class ComposeOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

    override fun onDestroy() {
        hideOverlay()
        if (::controller.isInitialized) controller.close()
        PanelBus.onOverlayStopped()
        store.clear()
        super.onDestroy()
    }

    /** Runs the panel's system actions and keeps the bus fed. */
    private lateinit var controller: OverlayPanelActions

    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        controller = OverlayPanelActions(applicationContext)
        startAsForeground()
        PanelBus.setOverlayActive(true)

        // The window stays mounted for the service's whole life (re-adding a
        // ComposeView breaks its composition, per the platform behaviour). Its
        // touchability tracks panel visibility instead: hidden means touch-through.
        //
        // The launcher's own window already hosts the in-app panel, so while the
        // activity is started the overlay must stay hidden — otherwise a reveal
        // would stack two identical panels on top of each other.
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(PanelBus.state, PanelBus.appForeground) { state, foreground ->
                state to foreground
            }.collect { (state, foreground) ->
                val show = state.visible && !foreground
                if (overlayView == null && show) showOverlay()
                setTouchable(show)
                setWindowVisible(show)
                if (show) {
                    when (state.face) {
                        com.elthon.optilauncher.core.panel.PanelFace.RECENTS -> controller.refreshRecents()
                        com.elthon.optilauncher.core.panel.PanelFace.HABITS -> controller.refreshHabits()
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SHOW -> {
                if (overlayView == null) showOverlay()
                PanelBus.dispatch(PanelEvent.Reveal)
            }

            ACTION_HIDE -> hideOverlay()

            ACTION_ARM -> Unit

            ACTION_DISMISS_PANEL -> PanelBus.dispatch(PanelEvent.Hide)
        }
        // Keep running: the trigger must work from any app. ColorOS may still kill
        // us — that is out of our hands, and the boot path tries to recover.
        return START_STICKY
    }

    // ------------------------------------------------------------------ window

    /** Shows or hides the mounted window without disturbing its composition. */
    private fun setWindowVisible(visible: Boolean) {
        val view = overlayView ?: return
        val wanted = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        if (view.visibility != wanted) view.visibility = wanted
    }

    /** Toggles whether the overlay window accepts touches. */
    private fun setTouchable(touchable: Boolean) {
        val view = overlayView ?: return
        val current = params ?: return
        val wanted = if (touchable) {
            current.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            current.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (wanted == current.flags) return
        current.flags = wanted
        try {
            windowManager?.updateViewLayout(view, current)
        } catch (t: Throwable) {
            Log.w(TAG, "updateViewLayout failed: ${t.javaClass.simpleName}")
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "overlay permission missing; not showing")
            return
        }
        val wm = (getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
        windowManager = wm

        val view = OverlayComposeHost(this, controller).build()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable keeps the keyboard with the app underneath. Starts
            // non-touchable; the bus observer turns touches on when the panel shows.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            wm.addView(view, lp)
            overlayView = view
            params = lp
        } catch (t: Throwable) {
            // The classic ColorOS silent refusal surfaces here as a security error.
            Log.w(TAG, "addView refused: ${t.javaClass.simpleName}")
            PanelBus.setOverlayActive(false)
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        overlayView = null
        params = null
        try {
            windowManager?.removeView(view)
        } catch (t: Throwable) {
            Log.w(TAG, "removeView failed: ${t.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------- foreground

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.overlay_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.overlay_channel_description)
                    setShowBadge(false)
                }
            )
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Class.forName(MAIN_ACTIVITY)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_splash)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "noblocks_overlay"
        private const val NOTIFICATION_ID = 4711
        private const val MAIN_ACTIVITY = "com.elthon.optilauncher.MainActivity"

        const val ACTION_SHOW = "com.elthon.optilauncher.overlay.SHOW"
        const val ACTION_HIDE = "com.elthon.optilauncher.overlay.HIDE"
        const val ACTION_ARM = "com.elthon.optilauncher.overlay.ARM"
        const val ACTION_DISMISS_PANEL = "com.elthon.optilauncher.overlay.DISMISS_PANEL"

        fun show(context: Context) {
            start(context, ACTION_SHOW)
        }

        /**
         * Starts the service without revealing anything. Used by the boot receiver:
         * coming back from a reboot should restore the trigger, not throw a panel in
         * the user's face before the launcher has even drawn.
         */
        fun arm(context: Context) {
            start(context, ACTION_ARM)
        }

        fun hide(context: Context) {
            context.startService(Intent(context, ComposeOverlayService::class.java).setAction(ACTION_HIDE))
        }

        private fun start(context: Context, action: String) {
            val intent = Intent(context, ComposeOverlayService::class.java).setAction(action)
            try {
                context.startForegroundService(intent)
            } catch (t: Throwable) {
                Log.w("OverlayService", "start refused: ${t.javaClass.simpleName}")
            }
        }
    }
}

/**
 * The overlay's action layer. Mirrors what `LauncherViewModel.onPanelEvent` does for
 * the in-app panel, but owns its own repositories because it must run with no
 * activity alive.
 */
class OverlayPanelActions(private val context: Context) {

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private val controller = PanelController(context)
    private val systemActions = SystemActions(context)
    private val recentsRepo = RecentsRepository(context)

    /** Cancels in-flight list refreshes when the hosting service dies. */
    fun close() {
        scope.cancel()
    }

    /** Applies an event to the bus and performs any system side effect it implies. */
    fun dispatch(event: PanelEvent) {
        when (event) {
            PanelEvent.Home -> systemActions.home()
            PanelEvent.SystemRecents -> if (!systemActions.recents()) {
                PanelBus.dispatch(PanelEvent.ShowFace(PanelFace.RECENTS))
                refreshRecents()
                return
            }

            PanelEvent.SystemNotifications -> systemActions.notifications()

            is PanelEvent.ShowFace -> when (event.face) {
                PanelFace.RECENTS -> refreshRecents()
                PanelFace.HABITS -> refreshHabits()
                else -> Unit
            }

            PanelEvent.CollapseToPill -> Unit

            else -> Unit
        }
        PanelBus.dispatch(event)
    }

    fun refreshRecents() {
        scope.launch {
            val self = context.packageName
            val live = controller.liveRecentPackages()
            val list = recentsRepo.recents(selfPackage = self, livePackages = live)
            PanelBus.setRecents(list)
        }
    }

    /**
     * Best-effort habit list for the overlay, which has no access to the launcher's
     * full habit engine. It reuses the same usage-history repository, so the list is
     * still real usage, ordered by recency — never fabricated. The in-app panel
     * shows the fully scored ranking; this is the overlay's honest approximation.
     */
    fun refreshHabits() {
        scope.launch {
            val self = context.packageName
            val list = recentsRepo.recents(selfPackage = self)
            PanelBus.setHabits(list)
        }
    }

    fun openApp(app: RecentApp) {
        PanelBus.dispatch(PanelEvent.Hide)
        openAppPackage(app.packageName)
    }

    /** Launches [packageName]'s main activity. No-op if it has none. */
    fun openAppPackage(packageName: String) {
        PanelBus.dispatch(PanelEvent.Hide)
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (t: Throwable) {
            Log.w("OverlayActions", "open failed: ${t.javaClass.simpleName}")
        }
    }

    fun stopApp(app: RecentApp) {
        scope.launch {
            if (!ShizukuBridge.available()) {
                PanelBus.setStopVerdict(com.elthon.optilauncher.core.panel.StopVerdict.UNAVAILABLE)
                return@launch
            }
            val verdict = controller.forceStop(app.packageName)
            PanelBus.setStopVerdict(verdict)
            refreshRecents()
        }
    }

    fun dismissNotification(key: String) {
        controller.dismissNotification(key)
    }
}
