package com.elthon.optilauncher.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

/**
 * The device's radios, and the honest truth about who can flip them.
 *
 * Android gives a third-party launcher no API to toggle Wi-Fi, Bluetooth, mobile
 * data or airplane mode. So there are two modes:
 *
 *  - **Read-only** (no Shizuku): report the live state from the sources a normal
 *    app may read, and a tap opens the platform's own switch panel. Never pretend
 *    a switch happened.
 *  - **Elevated** (Shizuku running and granted): run `svc`/`settings` shell
 *    commands to actually toggle Wi-Fi and mobile data, and read per-radio state
 *    so the two are *distinguishable* rather than both hiding behind "there is a
 *    network". Bluetooth and airplane mode still fall back to their panels — the
 *    shell toggles for those are unreliable across vendors.
 *
 * [snapshot] is suspend because the elevated reads cross a binder; the read-only
 * path just returns immediately.
 */
enum class RadioState { ON, OFF, UNKNOWN }

data class SystemControlSnapshot(
    val wifi: RadioState,
    val bluetooth: RadioState,
    val cellular: RadioState,
    val airplane: RadioState,
    /** True when the values can be toggled in-app (Shizuku granted). */
    val elevated: Boolean = false
)

class SystemControls(private val context: Context) {

    suspend fun snapshot(): SystemControlSnapshot {
        val elevated = ShizukuBridge.available()
        return SystemControlSnapshot(
            wifi = if (elevated) shizukuRadioState(WifiProbe) else wifiState(),
            bluetooth = bluetoothState(),
            cellular = if (elevated) shizukuRadioState(DataProbe) else cellularState(),
            airplane = airplaneState(),
            elevated = elevated
        )
    }

    /**
     * Toggles a radio. Returns the new state, or null when this app cannot toggle
     * it (no Shizuku, or a radio that only has a panel). The caller then opens the
     * panel instead — see [openPanel].
     */
    suspend fun toggle(key: ControlKey): RadioState? {
        if (!ShizukuBridge.available()) return null
        return when (key) {
            ControlKey.WIFI -> {
                val on = shizukuRadioState(WifiProbe) == RadioState.ON
                ShizukuBridge.exec("svc wifi ${if (on) "disable" else "enable"}")
                // Give the service a beat to settle before re-reading.
                kotlinx.coroutines.delay(350)
                shizukuRadioState(WifiProbe)
            }

            ControlKey.CELLULAR -> {
                val on = shizukuRadioState(DataProbe) == RadioState.ON
                ShizukuBridge.exec("svc data ${if (on) "disable" else "enable"}")
                kotlinx.coroutines.delay(350)
                shizukuRadioState(DataProbe)
            }

            // Bluetooth/airplane via shell are unreliable across vendors; let the
            // caller open the real panel rather than lie about the result.
            ControlKey.BLUETOOTH, ControlKey.AIRPLANE -> null
        }
    }

    private suspend fun shizukuRadioState(probe: RadioProbe): RadioState {
        val global = ShizukuBridge.readGlobal(probe.globalKey)
        val fromGlobal = when (global) {
            "1" -> RadioState.ON
            "0" -> RadioState.OFF
            else -> null
        }
        if (fromGlobal != null) return fromGlobal
        // Some ROMs don't keep the global in sync; fall back to the shell read.
        val viaSvc = ShizukuBridge.exec(probe.svcRead)?.lowercase().orEmpty()
        return when {
            viaSvc.contains("enabled") || viaSvc.contains("true") -> RadioState.ON
            viaSvc.contains("disabled") || viaSvc.contains("false") -> RadioState.OFF
            else -> RadioState.UNKNOWN
        }
    }

    private fun wifiState(): RadioState = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) RadioState.ON else RadioState.OFF
    } catch (_: Throwable) {
        RadioState.UNKNOWN
    }

    private fun cellularState(): RadioState = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) RadioState.ON else RadioState.OFF
    } catch (_: Throwable) {
        RadioState.UNKNOWN
    }

    private fun bluetoothState(): RadioState = try {
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

private class RadioProbe(val globalKey: String, val svcRead: String)

private val WifiProbe = RadioProbe("wifi_on", "cmd wifi status")
private val DataProbe = RadioProbe("mobile_data", "svc data")
