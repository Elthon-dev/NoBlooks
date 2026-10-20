package com.elthon.optilauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elthon.optilauncher.ui.HomeContent
import com.elthon.optilauncher.ui.LauncherViewModel

/**
 * The launcher's only activity.
 *
 * It is transparent and asks the window manager to show the wallpaper behind it,
 * so the wallpaper (not a screenshot of it) is what the home screen renders
 * against — the island and clock composite on top. All behavior lives in the
 * ViewModel; this class only wires window flags, insets, back handling and the
 * system-reduced-motion signal.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    /** Mirrors Settings.Global.ANIMATOR_DURATION_SCALE == 0 (animations off). */
    private var systemReducedMotion by mutableStateOf(false)

    /**
     * Bumped every time the activity resumes from a *stopped* state — i.e. the
     * user is coming back from an app or the task switcher. The home screen fades
     * in once per bump so the return crossfades instead of snapping. The first
     * resume (cold launch) is skipped: there is nothing to crossfade from.
     */
    private var resumeTick by mutableIntStateOf(0)
    private var wasStopped = false

    private val animatorScaleObserver = object : android.database.ContentObserver(
        android.os.Handler(android.os.Looper.getMainLooper())
    ) {
        override fun onChange(selfChange: Boolean) {
            systemReducedMotion = animationsDisabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Composite over the real wallpaper instead of painting an opaque surface.
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        hideStatusBar()

        systemReducedMotion = animationsDisabled()
        registerAnimatorScaleObserver()

        // Home button while already collapsed → go to the system's task switcher
        // behavior instead of trapping the user. (Compose's BackHandler consumes
        // Back first while the island is expanded.)
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }

        setContent {
            HomeContent(
                viewModel = viewModel,
                systemReducedMotion = systemReducedMotion,
                resumeTick = resumeTick
            )
        }
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // HOME was pressed again. This is the return path, so it must stay cheap:
        // the app list comes from the repository cache and the system re-probes run
        // off the main thread. No forced usage-history scan here.
        viewModel.refreshApps()
        viewModel.onReturnedHome()
    }

    override fun onResume() {
        super.onResume()
        // The user may be returning from Usage Access / Home Role / permission UIs,
        // or from any app. Keep the resume path minimal and non-blocking: the system
        // re-probes run off the main thread and the habit data is only reloaded when
        // its cache is actually stale.
        viewModel.onReturnedHome()
        systemReducedMotion = animationsDisabled()
        hideStatusBar()
        // Returning from an app or the switcher: fade the home screen in. The
        // first resume after launch is skipped — the splash already covered it.
        if (wasStopped) {
            wasStopped = false
            resumeTick++
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Some system UI transitions restore the status bar; the launcher's chrome
        // is the wallpaper, so put it away again whenever we get focus back.
        if (hasFocus) hideStatusBar()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(animatorScaleObserver)
        super.onDestroy()
    }

    /**
     * The home screen owns the whole display: no status bar, no clock, no icons.
     * Transient bars stay reachable by swiping from the top, and the navigation
     * bar is left to the system (gesture pill / buttons) so the user can leave.
     */
    private fun hideStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }

    private fun registerAnimatorScaleObserver() {
        try {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                animatorScaleObserver
            )
        } catch (_: Throwable) {
            // Reading system global settings can be restricted on some vendor ROMs;
            // the setting is a nicety, so failing to observe it is not an error.
        }
    }

    private fun animationsDisabled(): Boolean = try {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Throwable) {
        false
    }
}
