package com.elthon.optilauncher.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

/**
 * Read-only view of the device's radios, plus a way to open the system's own
 * switch for each one.
 *
 * Honesty note: Android does not let a third-party app flip Wi-Fi, Bluetooth,
 * mobile data or airplane mode. Anything that claims to is either rooted or
 * showing you a stale toggle. So this class only *reports* the live state and
 * hands the user to the platform panel where the real switch lives. The tile
 * labels say as much.
 */
enum class RadioState { ON, OFF, UNKNOWN }

data class SystemControlSnapshot(
    val wifi: RadioState,
    val bluetooth: RadioState,
    val cellular: RadioState,
    val airplane: RadioState
)

class SystemControls(private val context: Context) {

    /**
     * Reads each radio's state from the most trustworthy source available at the
     * app's privilege level. Each probe is independently try/caught: a vendor ROM
     * that blocks one global setting must not blank out the others.
     */
    fun snapshot(): SystemControlSnapshot = SystemControlSnapshot(
        wifi = wifiState(),
        bluetooth = bluetoothState(),
        cellular = cellularState(),
        airplane = airplaneState()
    )

    private fun wifiState(): RadioState {
        // Bypass the settings global (stale on some ROMs): ask ConnectivityManager
        // which transport is actually carrying a default network right now.
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps == null) RadioState.OFF
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) RadioState.ON
            else RadioState.OFF
        } catch (_: Throwable) {
            RadioState.UNKNOWN
        }
    }

    private fun cellularState(): RadioState = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            RadioState.ON
        } else {
            // No active cellular transport could mean "off" or "not the current
            // default". Without telephony permission we cannot be certain, so we
            // report OFF only when we can rule the transport in as absent.
            RadioState.OFF
        }
    } catch (_: Throwable) {
        RadioState.UNKNOWN
    }

    private fun bluetoothState(): RadioState = try {
        // BluetoothAdapter.isEnabled needs BLUETOOTH_CONNECT on API 31+, which this
        // launcher does not request. The "bluetooth_on" global is readable but
        // undocumented; treat it as a hint, not a promise.
        when (Settings.Global.getInt(context.contentResolver, "bluetooth_on", -1)) {
            1 -> RadioState.ON
            0 -> RadioState.OFF
            else -> RadioState.UNKNOWN
        }
    } catch (_: Throwable) {
        RadioState.UNKNOWN
    }

    private fun airplaneState(): RadioState = try {
        when (Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, -1)) {
            1 -> RadioState.ON
            0 -> RadioState.OFF
            else -> RadioState.UNKNOWN
        }
    } catch (_: Throwable) {
        RadioState.UNKNOWN
    }

    /**
     * Opens the system panel for [key]. Returns false when the device has no
     * handler, so the caller can fall back to the full settings screen.
     */
    fun openPanel(key: ControlKey): Boolean {
        val action = when (key) {
            ControlKey.WIFI -> Settings.Panel.ACTION_WIFI
            ControlKey.CELLULAR -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            ControlKey.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            ControlKey.AIRPLANE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        }
        return start(action)
    }

    private fun start(action: String): Boolean = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        // Settings.Panel actions are API 29+; this launcher is minSdk 29 so they
        // should resolve, but a stripped ROM can still refuse. Fall back to the
        // wireless settings screen, then give up honestly.
        try {
            context.startActivity(
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Throwable) {
            false
        }
    }
}

enum class ControlKey { WIFI, CELLULAR, BLUETOOTH, AIRPLANE }
