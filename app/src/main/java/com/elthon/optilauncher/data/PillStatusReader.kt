package com.elthon.optilauncher.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * The raw numbers behind the pill's status strip.
 *
 * Kept out of the pure [com.elthon.optilauncher.core.panel.PillStatus] formatter so
 * the platform reads (sticky battery broadcast, the wall clock) stay here and the
 * text logic stays testable.
 */
data class PillStatusData(
    val hourOfDay: Int,
    val minute: Int,
    val dayOfWeek: Int,
    val batteryLevel: Int,
    val batteryScale: Int,
    val charging: Boolean
)

/**
 * A Compose [State] that keeps the pill's clock and battery fresh.
 *
 * The clock ticks once a minute (aligned to the next minute boundary, so it is
 * never more than a minute stale) and the battery is re-read on every tick and on
 * every screen-on resume. This is a *display* surface: it never claims to be a
 * monitoring service, and it stops producing the moment the pill leaves composition.
 */
@Composable
fun rememberPillStatus(context: Context): State<PillStatusData> = produceState(
    initialValue = readPillStatus(context),
    context
) {
    while (true) {
        value = readPillStatus(context)
        val now = LocalDateTime.now()
        val msToNextMinute = ((60 - now.second) * 1000L - now.nano / 1_000_000L)
            .coerceIn(1_000L, 60_000L)
        delay(msToNextMinute)
    }
}

/** One synchronous read of the clock + sticky battery state. */
fun readPillStatus(context: Context): PillStatusData {
    val now = LocalDateTime.now()
    val battery = readBattery(context)
    return PillStatusData(
        hourOfDay = now.hour,
        minute = now.minute,
        dayOfWeek = now.dayOfWeek.value,
        batteryLevel = battery.level,
        batteryScale = battery.scale,
        charging = battery.charging
    )
}

private class Battery(val level: Int, val scale: Int, val charging: Boolean)

private fun readBattery(context: Context): Battery = try {
    val intent = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )
    if (intent == null) {
        Battery(0, 0, false)
    } else {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        Battery(level, scale, charging)
    }
} catch (_: Throwable) {
    Battery(0, 0, false)
}
