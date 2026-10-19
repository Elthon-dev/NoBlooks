package com.elthon.optilauncher.core.habits

/**
 * Pure aggregation of a bounded usage-event stream into [AppAggregate]s.
 *
 * Kept Android-free so the "how do we read UsageEvents" logic is unit-testable:
 * android.app.usage.UsageEvents rows are mapped to [UsageEvent] by the data layer
 * before they get here.
 */
data class UsageEvent(
    val packageName: String,
    val type: Type,
    val epochMs: Long
) {
    enum class Type { FOREGROUND, BACKGROUND }
}

/**
 * Session rules:
 *  - every FOREGROUND row counts as one "entry" (Android reports resumes too, so
 *    this is an activity-count, not a strict cold-launch count — it is presented
 *    that way in the UI);
 *  - duration accumulates only for matched FOREGROUND -> BACKGROUND pairs, so a
 *    session still open when the window ends is never guessed at;
 *  - a package with no foreground rows contributes nothing (no fabricated rows).
 */
object UsageAggregator {

    fun aggregate(
        events: List<UsageEvent>,
        windowStartMs: Long,
        windowEndMs: Long
    ): List<AppAggregate> {
        if (events.isEmpty()) return emptyList()

        class Acc {
            var entries = 0
            var durationMs = 0L
            var lastUsed = 0L
            val hours = IntArray(24)
            val weekdays = IntArray(7)
            val days = HashSet<Int>()
            var openAt: Long? = null
        }

        val acc = HashMap<String, Acc>()
        val sorted = events.sortedWith(compareBy({ it.epochMs }, { it.type.ordinal }))
        var previousPackage: String? = null

        for (event in sorted) {
            if (event.epochMs < windowStartMs || event.epochMs > windowEndMs) continue
            if (event.packageName.isEmpty()) continue
            val a = acc.getOrPut(event.packageName) { Acc() }

            when (event.type) {
                UsageEvent.Type.FOREGROUND -> {
                    // A FOREGROUND event that directly follows our own previous
                    // foreground for the *same* package is a resume, not a new entry.
                    if (previousPackage != event.packageName) {
                        a.entries++
                    }
                    a.hours[HabitEngine.hourOf(event.epochMs)]++
                    a.weekdays[HabitEngine.weekdayOf(event.epochMs)]++
                    a.days.add(dayIndex(event.epochMs))
                    a.lastUsed = maxOf(a.lastUsed, event.epochMs)
                    if (a.openAt == null) a.openAt = event.epochMs
                    previousPackage = event.packageName
                }
                UsageEvent.Type.BACKGROUND -> {
                    val open = a.openAt
                    if (open != null && event.epochMs >= open) {
                        a.durationMs += (event.epochMs - open)
                    }
                    a.openAt = null
                    if (previousPackage == event.packageName) previousPackage = null
                }
            }
        }

        return acc.map { (pkg, a) ->
            AppAggregate(
                packageName = pkg,
                launchCount = a.entries,
                totalDurationMs = a.durationMs,
                lastUsedEpochMs = a.lastUsed,
                hourHistogram = a.hours,
                weekdayHistogram = a.weekdays,
                distinctDays = a.days.size,
                source = DataSource.SYSTEM
            )
        }.filter { it.launchCount > 0 || it.totalDurationMs > 0 }
            .sortedByDescending { it.lastUsedEpochMs }
    }

    /** Days are bucketed in the device's local zone (calendar day-of-year + era). */
    private fun dayIndex(epochMs: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        return cal.get(java.util.Calendar.DAY_OF_YEAR) * 100 + cal.get(java.util.Calendar.ERA)
    }
}
