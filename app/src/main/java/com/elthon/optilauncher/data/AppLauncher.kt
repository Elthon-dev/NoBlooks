package com.elthon.optilauncher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import com.elthon.optilauncher.core.launch.DeviceCaps
import com.elthon.optilauncher.core.launch.IntentFlags
import com.elthon.optilauncher.core.launch.LaunchOutcome
import com.elthon.optilauncher.core.launch.LaunchPlanner
import com.elthon.optilauncher.core.launch.LaunchTarget
import com.elthon.optilauncher.core.launch.WindowMode
import kotlin.math.roundToInt

/**
 * Real launching, kept behind the [LaunchPlanner] decision table so the intent
 * construction is the only Android-specific part.
 *
 * Supported honestly:
 *  - NORMAL  : plain launch with the standard home-launch flags,
 *  - SPLIT   : FLAG_ACTIVITY_LAUNCH_ADJACENT request — Android may decline it if
 *              the windowing state is unavailable, which we cannot detect, so the
 *              caller is told it was *requested*, not promised,
 *  - FREEFORM: launch bounds are applied only when the device actually declares
 *              freeform window management; otherwise the planner already
 *              downgraded the request and we say so.
 *
 * No accessibility service, no root, no overlays, no fake windows.
 */
class AppLauncher(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    fun deviceCaps(): DeviceCaps = DeviceCaps(
        supportsSplitScreen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
        supportsFreeform = try {
            pm.hasSystemFeature(DeviceCaps.FREEFORM_FEATURE)
        } catch (_: Throwable) {
            false
        }
    )

    fun launch(target: LaunchTarget, requested: WindowMode): LaunchOutcome {
        val caps = deviceCaps()
        val plan = LaunchPlanner.plan(target, requested, caps)

        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(target.packageName, target.className)
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = plan.flags
        }

        // Proactive "gone/disabled" detection (subject to package visibility, which
        // the manifest <queries> declaration covers for launcher activities).
        val resolves = try {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (_: Throwable) {
            null
        }
        if (resolves == false) {
            return LaunchOutcome.Unavailable("The app has no launchable activity any more (uninstalled or disabled).")
        }

        val options = if (plan.mode == WindowMode.FREEFORM) freeformBounds() else null

        return try {
            appContext.startActivity(intent, options)
            LaunchPlanner.outcomeForPlan(plan)
        } catch (error: Throwable) {
            LaunchPlanner.outcomeFor(error, plan)
        }
    }

    /** Best-effort freeform bounds; only used when the device declared freeform. */
    private fun freeformBounds(): android.os.Bundle? = try {
        val metrics = appContext.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        if (screenW <= 0 || screenH <= 0) {
            null
        } else {
            val w = (screenW * 0.72f).roundToInt().coerceAtLeast(320)
            val h = (screenH * 0.66f).roundToInt().coerceAtLeast(480)
            val left = (screenW - w) / 2
            val top = (screenH - h) / 3
            android.app.ActivityOptions.makeBasic()
                .setLaunchBounds(Rect(left, top, left + w, top + h))
                .toBundle()
        }
    } catch (t: Throwable) {
        android.util.Log.w(TAG, "freeform bounds unavailable: ${t.javaClass.simpleName}")
        null
    }

    /**
     * Launches [packageName] via its launcher intent. Used by the control panel's
     * recents list, which only has a package name (no resolved component).
     * Returns false when the package has no launchable activity.
     */
    fun launchPackage(packageName: String): Boolean = try {
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }

    fun openAppDetails(packageName: String): Boolean = try {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * Hands the package to the system uninstaller. There is no silent uninstall
     * path for a third-party app, and pretending otherwise would be a lie.
     */
    fun uninstall(packageName: String): Boolean = try {
        val intent = Intent(
            Intent.ACTION_DELETE,
            android.net.Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }

    fun openWallpaperPicker(): Boolean {
        val picker = Intent(Intent.ACTION_SET_WALLPAPER)
        val resolver = Intent.createChooser(picker, "Choose wallpaper").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            appContext.startActivity(resolver)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                val alternative = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(alternative)
                true
            } catch (_: Throwable) {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun openUsageAccessSettings(): Boolean = try {
        appContext.startActivity(
            Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Throwable) {
        try {
            appContext.startActivity(
                Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun openHomeSettings(): Boolean = try {
        appContext.startActivity(
            Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * Opens the system's battery/background settings for *this* app.
     *
     * This is the one action that actually helps the "blank wallpaper for seconds
     * when returning Home" problem on ColorOS/OxygenOS: those ROMs aggressively
     * freeze and kill background processes (dontkillmyapp.com/oppo), so returning
     * Home is often a cold restart. No launcher code can override an OEM process
     * killer — but the user can exempt this app, and this is where they do it.
     *
     * Preference order: the app's own battery page (API 33+ has a per-app intent;
     * below that, the app-details page carries the battery entry), then the general
     * battery-optimization list. Nothing here is hidden-API or reflection.
     */
    fun openAppBatterySettings(packageName: String): Boolean {
        val appUri = android.net.Uri.parse("package:$packageName")
        val candidates = listOf(
            // The app's own details page is where the platform's "Battery" entry
            // lives on every supported version; it is the most reliable target.
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri),
            // The general battery-optimisation list (lets the user set "Unrestricted").
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            // Last resort: the whole battery/power settings screen.
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        )
        for (intent in candidates) {
            try {
                appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Throwable) {
                // Try the next, less specific target.
            }
        }
        return false
    }

    companion object {
        private const val TAG = "AppLauncher"

        /** Mirrors of Intent flags used in tests of the planner output. */
        val NORMAL_FLAGS: Int =
            IntentFlags.FLAG_ACTIVITY_NEW_TASK or IntentFlags.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
}
