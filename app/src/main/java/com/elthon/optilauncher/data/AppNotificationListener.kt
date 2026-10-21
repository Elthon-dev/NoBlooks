package com.elthon.optilauncher.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.elthon.optilauncher.core.panel.PanelNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads the notifications the user might have missed, so the control panel can
 * show them without opening the shade.
 *
 * This is a `NotificationListenerService`: it only ever receives anything while
 * the user has explicitly granted notification access (Settings → Notification
 * access). When that grant is absent, [NotificationBridge] reports zero
 * notifications and the panel says so — it never fakes content.
 *
 * Honest limits, enforced here rather than explained away later:
 *  - Ongoing/foreground notifications are excluded: a listener cannot dismiss
 *    them, and they are not "missed".
 *  - We store a flat, text-only copy. `RemoteViews` belong to the posting app
 *    and cannot be read by us.
 *  - Reply is best-effort: the platform decides whether the fill-in lands.
 */
class AppNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        // Register so PanelController can ask us to dismiss/reply. A listener is
        // the only component allowed to act on other apps' notifications.
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBridge.setConnected(true)
        publish(activeNotifications?.toList().orEmpty())
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBridge.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        publish(activeNotifications?.toList().orEmpty())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        publish(activeNotifications?.toList().orEmpty())
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        super.onNotificationRankingUpdate(rankingMap)
        publish(activeNotifications?.toList().orEmpty())
    }

    private fun publish(all: List<StatusBarNotification>) {
        val mapped = all.mapNotNull { it.toPanelNotification() }
        NotificationBridge.setNotifications(mapped)
    }

    companion object {
        /**
         * The live listener instance, if any. `NotificationListenerService` is
         * backed by a single system-bound instance, so a static handle is the
         * standard way for the rest of the app to reach it.
         */
        @Volatile
        var instance: AppNotificationListener? = null
            private set
    }
}

/**
 * Flattens a `StatusBarNotification` into the pure [PanelNotification] the panel
 * understands. Returns null for anything the panel cannot act on.
 */
internal fun StatusBarNotification.toPanelNotification(): PanelNotification? = try {
    buildPanelNotification()
} catch (t: Throwable) {
    Log.w("AppNotificationListener", "skip notification: ${t.javaClass.simpleName}")
    null
}

private fun StatusBarNotification.buildPanelNotification(): PanelNotification? {
    val n = notification ?: return null
    val extras = n.extras ?: return null
    val key = key ?: return null
    val pkg = packageName ?: return null

    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    val body = extraText(extras, n)

    val lines = buildList {
        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.let { add(it) }
        @Suppress("DEPRECATION")
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.forEach { it?.toString()?.let(::add) }
    }.distinct()

    val canReply = n.actions?.any { a ->
        a.remoteInputs?.isNotEmpty() == true && a.actionIntent != null
    } == true

    return PanelNotification(
        key = key,
        packageName = pkg,
        title = title,
        body = body,
        lines = lines,
        postTimeMs = n.`when`.takeIf { it != 0L } ?: postTime,
        isOngoing = isOngoing,
        isClearable = isClearable,
        canReply = canReply
    )
}

private fun extraText(extras: android.os.Bundle, n: Notification): String? {
    extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { return it }
    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { return it }
    val fallback = n.tickerText?.toString()
    return fallback
}

/**
 * Reads the actual special-access grant, independent of whether the listener is
 * currently connected. On ColorOS this is also the bit the hidden settings entry
 * controls; the Settings row uses it so "Granted" is never guessed from a live
 * connection that may simply not have been rebound yet.
 */
object NotificationAccess {
    fun isGranted(context: android.content.Context): Boolean = try {
        androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(context.applicationContext)
            .contains(context.packageName)
    } catch (_: Throwable) {
        false
    }

    /**
     * Asks the system to re-bind the listener without touching the grant.
     *
     * `requestRebind` is the only sanctioned reconnect call before `onListenerConnected`
     * or after `onListenerDisconnected`; it fails for listeners that were never
     * granted, so we check the grant first. This is the app-level half of the OEM
     * story: ColorOS/MIUI kill the listener in the background, and after an app
     * update the system does not always resume it until something asks. Without
     * auto-start the ROM may ignore even this — the UI says so instead of promising.
     */
    fun rebind(context: android.content.Context): Boolean {
        val app = context.applicationContext
        if (!isGranted(app)) return false
        return try {
            NotificationListenerService.requestRebind(
                android.content.ComponentName(app, AppNotificationListener::class.java)
            )
            true
        } catch (t: Throwable) {
            Log.w("NotificationAccess", "rebind refused: ${t.javaClass.simpleName}")
            false
        }
    }
}

/**
 * The seam between the listener service (which has no UI) and the launcher's
 * ViewModel. A process-wide [StateFlow] is the simplest honest bridge: the service
 * pushes, the ViewModel collects. It is intentionally small — no cache, no
 * persistence; when the listener is gone the list empties.
 */
object NotificationBridge {

    private val _notifications = MutableStateFlow<List<PanelNotification>>(emptyList())
    val notifications: StateFlow<List<PanelNotification>> = _notifications.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /**
     * Last time this process saw a listener callback (connect or a posted/removed
     * notification). Zero means "nothing since this launch" — it is a diagnostic,
     * not proof the listener has never worked.
     */
    private val _lastEventAtMs = MutableStateFlow(0L)
    val lastEventAtMs: StateFlow<Long> = _lastEventAtMs.asStateFlow()

    internal fun setNotifications(list: List<PanelNotification>) {
        _notifications.value = list
        _lastEventAtMs.value = System.currentTimeMillis()
    }

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (value) _lastEventAtMs.value = System.currentTimeMillis()
        if (!value) _notifications.value = emptyList()
    }
}
