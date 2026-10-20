package com.elthon.optilauncher.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.elthon.optilauncher.core.panel.VolumeChord
import com.elthon.optilauncher.core.panel.VolumeKey
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
 * Second job: it observes the hardware **volume keys**. Pressing both at once is a
 * private reveal gesture the user chose. We only *observe* — we never swallow the
 * keys (swallowing would break volume everywhere), so adjusting volume is an
 * accepted side effect of the gesture.
 *
 * Everything is optional: if the service is disabled, [armed] is false, the
 * three-finger tap and the volume gesture are dead, and the UI must say so.
 */
class AccessibilityPanelService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        armed.value = true
        // Ask for the multi-finger tap family *and* key-event observation. Key
        // events are observed, never filtered — filtering them would make the
        // volume keys stop working, which is not acceptable.
        try {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                flags = flags or
                    AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
        } catch (t: Throwable) {
            Log.w(TAG, "service flags failed: ${t.javaClass.simpleName}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We act on gestures and keys, not on window content; nothing to do here.
    }

    override fun onInterrupt() {}

    /**
     * Volume-key observation. We track both volume keys and fire a reveal when both
     * are down inside a short window — humans never press them in the same
     * millisecond, so a small tolerance is required. The timing rules live in the
     * pure [VolumeChord] so they can be unit-tested.
     *
     * We always return false: the event must reach the system so volume keeps
     * working. The gesture's side effect on volume is accepted by design.
     *
     * Best-effort warning: Android already uses "hold both volume keys" as the
     * global accessibility shortcut, and only one service may filter key events.
     * So this is a secondary trigger — the three-finger tap is the reliable one.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VolumeKey.UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKey.DOWN
            else -> return false
        }
        val down = event.action == KeyEvent.ACTION_DOWN
        if (chord.onKey(key, down, SystemClock.uptimeMillis())) {
            Log.i(TAG, "both volume keys -> reveal panel")
            revealPanel()
        }
        return false
    }

    /**
     * The single reveal path for both triggers: bring the overlay service up (it is
     * long-lived, so this is usually a no-op), open the panel on the shared bus,
     * tick [PanelSignals] so any foreground in-app panel mirrors it too, and give a
     * short haptic tick so the reveal is felt even when the panel is off-screen.
     */
    private fun revealPanel() {
        tick()
        ComposeOverlayService.show(this)
        PanelBus.dispatch(com.elthon.optilauncher.core.panel.PanelEvent.Reveal)
        PanelSignals.reveal()
    }

    /**
     * A single short vibration on reveal. Advanced haptics would be nicer, but a
     * reveal trigger must not depend on a vibration motor whose capability we
     * cannot read here — so this is a guarded, best-effort tick, never a claim.
     */
    private fun tick() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(
                VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "haptic tick failed: ${t.javaClass.simpleName}")
        }
    }

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
     * as "show me". Returning true means we consumed it.
     */
    override fun onGesture(gestureId: Int): Boolean {
        when (gestureId) {
            AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP,
            AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP -> {
                Log.i(TAG, "three-finger tap -> reveal panel")
                revealPanel()
                return true
            }
        }
        return false
    }

    private val chord = VolumeChord()

    companion object {
        private const val TAG = "A11yPanel"

        /** Length of the reveal haptic tick, in milliseconds. */
        private const val TICK_MS = 20L

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
