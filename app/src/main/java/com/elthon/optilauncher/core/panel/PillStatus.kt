package com.elthon.optilauncher.core.panel

/**
 * Pure formatting for the pill's status strip (clock + battery).
 *
 * The Android side reads the raw numbers; this file turns them into the exact
 * strings the pill shows. It lives in `core/` so the edge cases that always bite
 * clocks — midnight, single-digit minutes, 100% battery — are covered by a unit
 * test instead of by hoping.
 */
object PillStatus {

    /**
     * `HH:mm`, 24-hour, zero-padded. [hourOfDay] is clamped into range so a bad
     * reading can never render "25:99".
     */
    fun clock(hourOfDay: Int, minute: Int): String {
        val h = hourOfDay.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        return "${pad(h)}:${pad(m)}"
    }

    /** Short weekday from `java.time.DayOfWeek.value` (1 = Monday … 7 = Sunday). */
    fun weekday(isoDayOfWeek: Int): String = when (isoDayOfWeek) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        else -> "Sun"
    }

    /**
     * Battery percentage text. [level] and [scale] come straight from
     * `BatteryManager`; a zero/negative scale would divide by zero, so it is
     * guarded and the result is clamped to 0..100.
     */
    fun batteryPercent(level: Int, scale: Int): String {
        if (scale <= 0) return "—"
        val pct = (level * 100 / scale).coerceIn(0, 100)
        return "$pct%"
    }

    /** True when the battery reading should read as "low" (at or below [threshold]). */
    fun isLow(level: Int, scale: Int, threshold: Int = 15): Boolean {
        if (scale <= 0) return false
        return (level * 100 / scale) <= threshold
    }

    private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()
}
