package com.elthon.optilauncher.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The three panel buttons' real work: go Home, show Recents, show the shade.
 *
 * Order of preference is deliberate and honest about reliability:
 *
 *  1. **Accessibility global actions** — `GLOBAL_ACTION_HOME/RECENTS/NOTIFICATIONS`.
 *     These are the platform's own actions and are reliable on every OEM, ColorOS
 *     included. They require the accessibility service to be enabled.
 *  2. **Public intents** — `CATEGORY_HOME` for home, `CATEGORY_APP_RECENTS` for
 *     recents. These work without any special access but are OEM-dependent (the
 *     recents intent, in particular, is a coin flip on ColorOS).
 *
 * Every method returns whether it *actually dispatched* something, so the panel can
 * say "recents isn't available here" instead of pretending.
 */
class SystemActions(private val context: Context) {

    /**
     * Go to the home screen.
     *
     * This is the platform's Home action, not "bring NoBlooks forward": when the
     * user has another launcher selected, Home must honour that choice. The old
     * self-forward shortcut silently overrode the default home app.
     */
    fun home(): Boolean {
        AccessibilityPanelService.instance?.let { a11y ->
            try {
                if (a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
                    return true
                }
            } catch (t: Throwable) {
                Log.w(TAG, "home global action failed: ${t.javaClass.simpleName}")
            }
        }
        return startHomeIntent()
    }

    /** Open the system overview / recents. */
    fun recents(): Boolean {
        AccessibilityPanelService.instance?.let { a11y ->
            try {
                if (a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) {
                    return true
                }
            } catch (t: Throwable) {
                Log.w(TAG, "recents global action failed: ${t.javaClass.simpleName}")
            }
        }
        return try {
            val intent = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN,
                CATEGORY_APP_RECENTS
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            // Documented API, but plenty of ROMs (ColorOS among them) simply do not
            // resolve it. The panel falls back to its own list in that case.
            Log.w(TAG, "recents intent unavailable: ${t.javaClass.simpleName}")
            false
        }
    }

    /** Pull down the notification shade. */
    fun notifications(): Boolean {
        AccessibilityPanelService.instance?.let { a11y ->
            return try {
                a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            } catch (t: Throwable) {
                Log.w(TAG, "notifications global action failed: ${t.javaClass.simpleName}")
                false
            }
        }
        return false
    }

    private fun startHomeIntent(): Boolean = try {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "home intent failed: ${t.javaClass.simpleName}")
        false
    }

    companion object {
        private const val TAG = "SystemActions"

        /**
         * `Intent.CATEGORY_APP_RECENTS`, spelled out. The constant exists but not
         * every SDK/tooling combination resolves it, and the value is stable.
         */
        private const val CATEGORY_APP_RECENTS = "android.intent.category.APP_RECENTS"
    }
}
