package com.elthon.optilauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Best-effort re-arm of the edge panel after a reboot.
 *
 * This is deliberately timid. A foreground service started from `BOOT_COMPLETED`
 * is easy to get wrong (and on API 34+ a `specialUse` FGS at boot can draw extra
 * scrutiny), so we only do it when the prerequisites are already met:
 *
 *  - the user has granted "Display over other apps", and
 *  - the accessibility service is actually enabled.
 *
 * If either is missing there is nothing to keep alive — the panel only works from
 * a running service — so we stay silent. ColorOS may also delay or drop this
 * broadcast entirely; the panel then simply waits for the next app launch or
 * gesture. We never claim the re-arm succeeded.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                rebindListener(context)
                rearm(context)
            }

            // An update kills the process (and with it the foreground service and
            // the listener binding). Android delivers this once the new APK is in
            // place, so this is the one reliable moment to ask for both back.
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                rebindListener(context)
                rearm(context)
            }
        }
    }

    private fun rebindListener(context: Context) {
        if (NotificationAccess.rebind(context)) {
            Log.i(TAG, "listener rebind requested")
        }
    }

    private fun rearm(context: Context) {
        val canOverlay = try {
            Settings.canDrawOverlays(context)
        } catch (t: Throwable) {
            Log.w(TAG, "canDrawOverlays failed: ${t.javaClass.simpleName}")
            false
        }
        if (!canOverlay) return
        if (!AccessibilityPanelService.isEnabled(context)) return
        ComposeOverlayService.arm(context)
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
