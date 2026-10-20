package com.elthon.optilauncher.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityGestureEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The only way a third-party app can see a three-finger tap **anywhere** on the
 * screen (not just on our own pill). Android exposes exactly these multi-finger
 * taps to accessibility services — there is no overlay or other public route, and
 * no root here.
 *
 * It also gives us [GLOBAL_ACTION_HOME]/[GLOBAL_ACTION_RECENTS]/
 * [GLOBAL_ACTION_NOTIFICATIONS], which are the *reliable* way to reach the system
 * overview and shade. That matters on ColorOS, where the `CATEGORY_APP_RECENTS`
 * intent is a coin flip.
 *
 * Everything is optional: if the service is disabled, [AccessibilityPanelService.armed]
 * is false, the three-finger tap is dead, and the UI must say so.
 */
class AccessibilityPanelService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        armed.value = true
        // Ask for the multi-finger tap family explicitly.
        try {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
            }
        } catch (t: Throwable) {
            Log.w(TAG, "multi-finger flag failed: ${t.javaClass.simpleName}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We act on gestures, not on window content; nothing to do here.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        armed.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        armed.value = false
        super.onDestroy()
    }

    /**
     * The gesture callback. We only care about a three-finger *single tap* — the
     * least noisy, hardest-to-trigger-by-accident option — which the panel treats
     * as "show me".
     */
    override fun onGesture(gestureId: Int) {
        when (gestureId) {
            AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP,
            AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP -> {
                Log.i(TAG, "three-finger tap -> reveal panel")
                PanelSignals.reveal()
            }
        }
    }

    override fun onGestureEvent(gestureEvent: AccessibilityGestureEvent) {
        // Some builds deliver through this overload; route it to the same handler
        // by mapping the gesture id back through the public constant space.
        super.onGestureEvent(gestureEvent)
        onGesture(gestureEvent.gestureId)
    }

    companion object {
        private const val TAG = "A11yPanel"

        @Volatile
        var instance: AccessibilityPanelService? = null
            private set

        private val armed = MutableStateFlow(false)

        /** True while the user has this accessibility service enabled. */
        val armedFlow: StateFlow<Boolean> = armed.asStateFlow()

        /** Best-effort, synchronous access-check used by the UI. */
        fun isEnabled(context: Context): Boolean = try {
            val expected = "${context.packageName}/${AccessibilityPanelService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Fire-and-collect seam for the accessibility service, mirroring
 * [NotificationBridge]. A tiny counter-based signal keeps it allocation-light:
 * the UI watches a monotonically increasing reveal request.
 */
object PanelSignals {
    private val _revealTick = MutableStateFlow(0)
    val revealTick: StateFlow<Int> = _revealTick.asStateFlow()

    internal fun reveal() {
        _revealTick.value = _revealTick.value + 1
    }

    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    internal fun home() {
        _homeTick.value = _homeTick.value + 1
    }
}
