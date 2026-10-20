package com.elthon.optilauncher.data

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.elthon.optilauncher.core.panel.StopVerdict
import kotlinx.coroutines.delay

/**
 * The panel's action layer: dismiss a notification, send a reply, or force-stop an
 * app. Each action is deliberately conservative about what it claims.
 *
 * Everything here is optional-access guarded:
 *  - Notification dismiss/reply needs notification access (the listener is bound).
 *  - Force-stop needs Shizuku; without it we return [StopVerdict.UNAVAILABLE] and
 *    the UI offers the app-info page instead.
 */
class PanelController(private val context: Context) {

    /**
     * Dismisses a notification by key. Ongoing notifications are silently ignored
     * by the platform — that is expected, not a bug, so we make no claim here.
     */
    fun dismissNotification(key: String): Boolean {
        val listener = AppNotificationListener.instance ?: return false
        return try {
            listener.cancelNotification(key)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "dismiss failed: ${t.javaClass.simpleName}")
            false
        }
    }

    /**
     * Sends a text reply to a messaging notification, best-effort.
     *
     * We do this the way the platform documents for listeners: build a fill-in
     * Intent carrying the reply in `RemoteInput`, then `send()` the action's
     * PendingIntent. If the app declared its reply action immutable, or set its own
     * ClipData, the fill-in is dropped and the reply is silently lost — so the
     * caller must treat `true` as "sent", never "delivered".
     */
    fun reply(key: String, text: CharSequence): Boolean {
        val listener = AppNotificationListener.instance ?: return false
        val sbn = try {
            listener.activeNotifications?.firstOrNull { it.key == key }
        } catch (_: Throwable) {
            null
        } ?: return false
        val notification = sbn.notification ?: return false
        val action = notification.actions?.firstOrNull { a ->
            a.remoteInputs?.isNotEmpty() == true && a.actionIntent != null
        } ?: return false

        return try {
            val remoteInputs = action.remoteInputs ?: return false
            val target = remoteInputs.firstOrNull { it.allowFreeFormInput } ?: remoteInputs.first()
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(target.resultKey, text) }
            RemoteInput.addResultsToIntent(remoteInputs, intent, results)
            action.actionIntent?.send(context, 0, intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "reply failed: ${t.javaClass.simpleName}")
            false
        }
    }

    /**
     * Force-stops [packageName] through Shizuku and verifies the result.
     *
     * The verification is the point: we re-run `ps` and, if the package is back a
     * moment later, we report [StopVerdict.REVIVED] rather than lying that the app
     * is stopped (some ROMs, ColorOS included, revive "protected" apps).
     */
    suspend fun forceStop(packageName: String): StopVerdict {
        if (!ShizukuBridge.available()) return StopVerdict.UNAVAILABLE

        val out = ShizukuBridge.exec("am force-stop $packageName")
            ?: return StopVerdict.UNAVAILABLE

        // `am` prints nothing useful on refusal; a permission/parse error shows up
        // as our wrapper's "error:" prefix.
        if (out.startsWith("error:") || out.contains("Exception")) {
            return StopVerdict.REFUSED
        }

        // Give the process a beat to actually die, then read the truth.
        delay(STOP_SETTLE_MS)
        val alive = isRunning(packageName)
        return when {
            alive == null -> StopVerdict.UNVERIFIED
            alive -> StopVerdict.REVIVED
            else -> StopVerdict.STOPPED
        }
    }

    /**
     * True when a live process for [packageName] exists; null when we cannot tell.
     * Shell-only, best-effort: `ps -A` reliably lists process names.
     */
    suspend fun isRunning(packageName: String): Boolean? {
        if (!ShizukuBridge.available()) return null
        val ps = ShizukuBridge.exec("ps -A -o NAME=") ?: return null
        return ps.lineSequence().any { line ->
            val name = line.trim()
            name == packageName || name.startsWith("$packageName:")
        }
    }

    /**
     * Package names with a live process, from `dumpsys activity recents`. Used to
     * mark recents rows as "live" rather than merely "recently used".
     */
    suspend fun liveRecentPackages(): Set<String> {
        if (!ShizukuBridge.available()) return emptySet()
        val raw = ShizukuBridge.exec(RECENTS_CMD) ?: return emptySet()
        val cmp = Regex("""cmp=([A-Za-z0-9._]+)/""")
        val pkg = Regex("""A="?([A-Za-z0-9._]+)"?""")
        return raw.lineSequence()
            .filter { it.contains("Recent #") }
            .mapNotNull { line ->
                cmp.find(line)?.groupValues?.getOrNull(1)
                    ?: pkg.find(line)?.groupValues?.getOrNull(1)
            }
            .filter { it.isNotBlank() && it !in RecentsRepository.IGNORED }
            .toSet()
    }

    companion object {
        private const val TAG = "PanelController"
        private const val STOP_SETTLE_MS = 700L
        private const val RECENTS_CMD =
            "dumpsys activity recents | grep -E 'Recent #[0-9]+|cmp=' | head -n 80"
    }
}
