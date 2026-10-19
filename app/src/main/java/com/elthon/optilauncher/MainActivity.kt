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
import androidx.compose.runtime.getValue
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

        systemReducedMotion = animationsDisabled()
        registerAnimatorScaleObserver()

        // Home button while already collapsed → go to the system's task switcher
        // behavior instead of trapping the user. (Compose's BackHandler consumes
        // Back first while the island is expanded.)
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }

        setContent {
            HomeContent(viewModel = viewModel, systemReducedMotion = systemReducedMotion)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // HOME was pressed again: refresh what can have changed outside.
        viewModel.refreshApps()
        viewModel.refreshHomeRole()
        viewModel.recheckUsageAccess()
    }

    override fun onResume() {
        super.onResume()
        // The user may be returning from Usage Access / Home Role / permission UIs.
        viewModel.refreshHomeRole()
        viewModel.recheckUsageAccess()
        systemReducedMotion = animationsDisabled()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(animatorScaleObserver)
        super.onDestroy()
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
