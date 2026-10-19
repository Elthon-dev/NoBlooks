package com.elthon.optilauncher.recon

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.WindowManager
import com.elthon.optilauncher.recon.model.ConfigurationInfo
import com.elthon.optilauncher.recon.model.DisplayInfo
import com.elthon.optilauncher.recon.model.ProbeError

/**
 * UI-thread snapshot of display + configuration + window insets.
 *
 * Captured on the main thread (where Displays and WindowInsets are legal to read) and
 * then handed to the scanner, which runs on a worker thread. Collection failures are
 * returned as [ProbeError]s instead of being thrown, so a flaky OEM display API can
 * never crash the launcher.
 */
data class UiSnapshot(
    val capturedAtEpochMs: Long,
    val display: DisplayInfo,
    val configuration: ConfigurationInfo,
    val insets: List<com.elthon.optilauncher.recon.model.InsetsSnapshot>,
    val errors: List<ProbeError>
)

object UiSnapshotCollector {

    private const val SECTION_DISPLAY = "display_configuration"

    fun capture(context: Context, latestInsets: List<com.elthon.optilauncher.recon.model.InsetsSnapshot>): UiSnapshot {
        val errors = mutableListOf<ProbeError>()

        fun <T> note(section: String, probe: String, fallback: T, block: () -> T): T =
            try {
                block()
            } catch (t: Throwable) {
                errors.add(ProbeError(section, probe, t.javaClass.simpleName, t.message ?: t.toString()))
                fallback
            }

        val config = context.resources.configuration
        val configuration = note(SECTION_DISPLAY, "resources.configuration", nullConfig()) {
            ConfigurationInfo(
                orientation = orientationLabel(config.orientation),
                screenWidthDp = config.screenWidthDp,
                screenHeightDp = config.screenHeightDp,
                smallestScreenWidthDp = config.smallestScreenWidthDp,
                densityDpi = config.densityDpi,
                fontScale = config.fontScale,
                uiMode = uiModeLabel(config.uiMode),
                nightMode = nightLabel(config.uiMode),
                locale = runCatching { config.locales.get(0)?.toString() }.getOrNull(),
                layoutDirection = if (config.layoutDirection == View.LAYOUT_DIRECTION_RTL) "rtl" else "ltr"
            )
        }

        val display = captureDisplay(context, errors)
        return UiSnapshot(
            capturedAtEpochMs = System.currentTimeMillis(),
            display = display,
            configuration = configuration,
            insets = latestInsets,
            errors = errors
        )
    }

    private fun captureDisplay(context: Context, errors: MutableList<ProbeError>): DisplayInfo {
        val display: Display = try {
            if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(WindowManager::class.java)?.defaultDisplay
            }
        } catch (t: Throwable) {
            errors.add(ProbeError(SECTION_DISPLAY, "Context.getDisplay", t.javaClass.simpleName, t.message ?: t.toString()))
            null
        } ?: return try {
            // Last resort: any display the DisplayManager knows about.
            val dm = context.getSystemService(DisplayManager::class.java)
            val fallback = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            if (fallback == null) emptyDisplay() else describe(context, fallback, errors)
        } catch (t: Throwable) {
            errors.add(ProbeError(SECTION_DISPLAY, "DisplayManager.getDisplay", t.javaClass.simpleName, t.message ?: t.toString()))
            emptyDisplay()
        }

        return describe(context, display, errors)
    }

    private fun describe(context: Context, display: Display, errors: MutableList<ProbeError>): DisplayInfo {
        fun <T> note(probe: String, fallback: T, block: () -> T): T =
            try {
                block()
            } catch (t: Throwable) {
                errors.add(ProbeError(SECTION_DISPLAY, probe, t.javaClass.simpleName, t.message ?: t.toString()))
                fallback
            }

        val widthHeight = note("WindowManager.getCurrentWindowMetrics", null as Pair<Int, Int>?) {
            if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
                val wm = context.getSystemService(WindowManager::class.java)
                val bounds = wm?.currentWindowMetrics?.bounds
                if (bounds != null) bounds.width() to bounds.height() else null
            } else {
                null
            }
        }

        val refreshRates = note("Display.getSupportedModes", emptyList<Float>()) {
            if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
                display.supportedModes
                    .map { it.refreshRate }
                    .distinct()
                    .sorted()
            } else {
                emptyList()
            }
        }

        return DisplayInfo(
            displayId = note("Display.getDisplayId", null) { display.displayId },
            widthPx = widthHeight?.first,
            heightPx = widthHeight?.second,
            densityDpi = note("Configuration.densityDpi", null) { context.resources.displayMetrics.densityDpi },
            refreshRateHz = note("Display.getRefreshRate", null) { display.refreshRate },
            displayState = note("Display.getState", null) { displayStateLabel(display.state) },
            rotation = note("Display.getRotation", null) { rotationLabel(display.rotation) },
            currentMode = note("Display.getMode", null) {
                if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
                    val mode = display.mode
                    "${mode.physicalWidth}x${mode.physicalHeight}@${mode.refreshRate}Hz"
                } else {
                    null
                }
            },
            supportedModeCount = note("Display.getSupportedModes.count", null) {
                if (Build.VERSION.SDK_INT >= VERSION_CODES.M) display.supportedModes.size else null
            },
            supportedRefreshRatesHz = refreshRates
        )
    }

    private fun emptyDisplay() = DisplayInfo(
        displayId = null,
        widthPx = null,
        heightPx = null,
        densityDpi = null,
        refreshRateHz = null,
        displayState = null,
        rotation = null,
        currentMode = null,
        supportedModeCount = null,
        supportedRefreshRatesHz = emptyList()
    )

    private fun nullConfig() = ConfigurationInfo(
        orientation = null,
        screenWidthDp = null,
        screenHeightDp = null,
        smallestScreenWidthDp = null,
        densityDpi = null,
        fontScale = null,
        uiMode = null,
        nightMode = null,
        locale = null,
        layoutDirection = null
    )

    internal fun orientationLabel(orientation: Int): String = when (orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> "landscape"
        Configuration.ORIENTATION_PORTRAIT -> "portrait"
        Configuration.ORIENTATION_SQUARE -> "square"
        else -> "undefined($orientation)"
    }

    private fun uiModeLabel(uiMode: Int): String {
        val type = uiMode and Configuration.UI_MODE_TYPE_MASK
        val typeName = when (type) {
            Configuration.UI_MODE_TYPE_NORMAL -> "normal"
            Configuration.UI_MODE_TYPE_TELEVISION -> "television"
            Configuration.UI_MODE_TYPE_CAR -> "car"
            Configuration.UI_MODE_TYPE_DESK -> "desk"
            Configuration.UI_MODE_TYPE_APPLIANCE -> "appliance"
            Configuration.UI_MODE_TYPE_WATCH -> "watch"
            Configuration.UI_MODE_TYPE_VR_HEADSET -> "vr_headset"
            else -> "unknown($type)"
        }
        return typeName
    }

    private fun nightLabel(uiMode: Int): String =
        if ((uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) "night" else "day"

    internal fun displayStateLabel(state: Int): String = when (state) {
        Display.STATE_ON -> "ON"
        Display.STATE_OFF -> "OFF"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_VR -> "VR"
        else -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && state == Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPENDED"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && state == Display.STATE_ON_SUSPEND -> "ON_SUSPENDED"
            else -> "UNKNOWN($state)"
        }
    }

    internal fun rotationLabel(rotation: Int): String = when (rotation) {
        Surface.ROTATION_0 -> "ROTATION_0"
        Surface.ROTATION_90 -> "ROTATION_90"
        Surface.ROTATION_180 -> "ROTATION_180"
        Surface.ROTATION_270 -> "ROTATION_270"
        else -> "UNKNOWN($rotation)"
    }
}
