package com.elthon.optilauncher.core.habits

/**
 * App-habit model.
 *
 * Two distinct, never-conflated inputs:
 *  - [DataSource.SYSTEM]  — aggregate UsageStats reported by Android after the user
 *    granted Usage Access (covers the whole device, subject to the usual caveats:
 *    batching, vendor quirks, cleared data, timezone/reboot effects).
 *  - [DataSource.LOCAL]   — launches NoBlooks itself initiated (self-observed only;
 *    it says nothing about what the user did in other apps).
 *
 * Everything here is deterministic pure math over supplied data. Nothing is
 * fabricated when data is missing: insufficient input produces an explicit
 * "not enough data" result instead of a confident-looking number.
 */
enum class DataSource { SYSTEM, LOCAL }

/** Aggregate usage for one package over a bounded history window. */
data class AppAggregate(
    val packageName: String,
    val launchCount: Int,
    val totalDurationMs: Long,
    val lastUsedEpochMs: Long,
    /** 24 buckets: launches (or foreground sessions) seen in each hour of the day. */
    val hourHistogram: IntArray,
    /** 7 buckets, Monday = 0. */
    val weekdayHistogram: IntArray,
    /** How many distinct calendar days appear in this package's history. */
    val distinctDays: Int,
    val source: DataSource
) {
    init {
        require(hourHistogram.size == 24) { "hourHistogram must have 24 buckets" }
        require(weekdayHistogram.size == 7) { "weekdayHistogram must have 7 buckets" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppAggregate) return false
        return packageName == other.packageName &&
            launchCount == other.launchCount &&
            totalDurationMs == other.totalDurationMs &&
            lastUsedEpochMs == other.lastUsedEpochMs &&
            hourHistogram.contentEquals(other.hourHistogram) &&
            weekdayHistogram.contentEquals(other.weekdayHistogram) &&
            distinctDays == other.distinctDays &&
            source == other.source
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + launchCount
        result = 31 * result + totalDurationMs.hashCode()
        result = 31 * result + lastUsedEpochMs.hashCode()
        result = 31 * result + hourHistogram.contentHashCode()
        result = 31 * result + weekdayHistogram.contentHashCode()
        result = 31 * result + distinctDays
        result = 31 * result + source.hashCode()
        return result
    }
}

enum class Confidence { NONE, LOW, MEDIUM, HIGH }

enum class HabitReason {
    INSUFFICIENT_DATA,
    NO_HISTORY,
    RECENTLY_USED,
    TIME_OF_DAY,
    FREQUENT,
    FAVORITE_UNUSED,
    LOCAL_ONLY
}

data class ScoredApp(
    val packageName: String,
    val score: Float,
    val confidence: Confidence,
    val reason: HabitReason,
    val launchCount: Int,
    val lastUsedEpochMs: Long,
    val source: DataSource
)

data class Prediction(
    val packageName: String?,
    val score: Float,
    val confidence: Confidence,
    val reason: HabitReason,
    val sampleSize: Int,
    val message: String
) {
    val hasPrediction: Boolean get() = packageName != null && confidence != Confidence.NONE

    companion object {
        fun insufficient(reason: HabitReason, message: String, sampleSize: Int = 0): Prediction =
            Prediction(null, 0f, Confidence.NONE, reason, sampleSize, message)

        fun noneAvailable(message: String): Prediction =
            insufficient(HabitReason.NO_HISTORY, message)
    }
}

data class HabitInput(
    val nowEpochMs: Long,
    val aggregates: List<AppAggregate>,
    /** Recent launches observed by NoBlooks itself (bounded, local). */
    val localLaunches: List<LocalLaunch>,
    val hasUsageAccess: Boolean,
    val knownPackages: Set<String> = emptySet(),
    val pinnedPackages: Set<String> = emptySet()
) {
    val totalSignals: Int
        get() = aggregates.sumOf { it.launchCount } + localLaunches.size

    companion object {
        fun empty(nowEpochMs: Long = 0L): HabitInput =
            HabitInput(nowEpochMs, emptyList(), emptyList(), hasUsageAccess = false)
    }
}

data class LocalLaunch(val packageName: String, val epochMs: Long)

data class UsageTotals(
    val todayMs: Long,
    val weekMs: Long,
    val todayLaunches: Int,
    val trackedPackages: Int
)

data class HabitSummary(
    val totals: UsageTotals,
    val topApps: List<ScoredApp>,
    val recentLaunches: List<LocalLaunch>,
    val prediction: Prediction,
    val hasEnoughData: Boolean,
    val observedBy: DataSource,
    val historyDays: Int
)

/**
 * Recency-weighted scoring model.
 *
 * score = 0.45·recency + 0.30·frequency + 0.15·timeOfDay + 0.10·weekday
 *
 * - recency    : exponential half-life decay over [HALF_LIFE_DAYS]
 * - frequency  : log-scaled share of the busiest app (compresses heavy outliers)
 * - timeOfDay  : share of this app's launches that usually happen in this hour bucket
 * - weekday    : same, for today's day of week
 *
 * Confidence is derived from *how much* data exists (event count + distinct days),
 * never from the score itself, and is capped at LOW when the only source is the
 * launcher's own observations.
 */
object HabitEngine {

    const val HALF_LIFE_DAYS = 3.0
    const val MIN_LAUNCHES_FOR_PREDICTION = 5
    const val MIN_DISTINCT_DAYS_FOR_PREDICTION = 2
    const val MIN_LOCAL_LAUNCHES = 3

    private const val W_RECENCY = 0.45f
    private const val W_FREQUENCY = 0.30f
    private const val W_TIME = 0.15f
    private const val W_WEEKDAY = 0.10f

    fun score(input: HabitInput): List<ScoredApp> {
        if (input.aggregates.isEmpty()) return emptyList()
        val now = input.nowEpochMs
        val maxCount = input.aggregates.maxOfOrNull { it.launchCount }?.coerceAtLeast(1) ?: 1
        val nowHour = hourOf(now)
        val nowWeekday = weekdayOf(now)

        return input.aggregates
            .filter { it.launchCount > 0 }
            .map { agg ->
                val recency = recencyScore(agg.lastUsedEpochMs, now)
                val frequency = kotlin.math.ln(1f + agg.launchCount) / kotlin.math.ln(1f + maxCount)
                val time = agg.hourHistogram[nowHour] / agg.launchCount.toFloat()
                val weekday = agg.weekdayHistogram[nowWeekday] / agg.launchCount.toFloat()
                val raw = W_RECENCY * recency + W_FREQUENCY * frequency + W_TIME * time + W_WEEKDAY * weekday
                ScoredApp(
                    packageName = agg.packageName,
                    score = raw.coerceIn(0f, 1f),
                    confidence = confidenceFor(agg.launchCount, agg.distinctDays, agg.source, input.hasUsageAccess),
                    reason = primaryReason(recency, time, agg.launchCount),
                    launchCount = agg.launchCount,
                    lastUsedEpochMs = agg.lastUsedEpochMs,
                    source = agg.source
                )
            }
            .sortedWith(compareByDescending<ScoredApp> { it.score }.thenBy { it.packageName })
    }

    fun recencyScore(lastUsedEpochMs: Long, nowEpochMs: Long): Float {
        if (lastUsedEpochMs <= 0L) return 0f
        val ageDays = (nowEpochMs - lastUsedEpochMs) / 86_400_000.0
        if (ageDays < 0) return 1f
        return Math.pow(0.5, ageDays / HALF_LIFE_DAYS).toFloat().coerceIn(0f, 1f)
    }

    fun confidenceFor(
        launchCount: Int,
        distinctDays: Int,
        source: DataSource,
        hasUsageAccess: Boolean
    ): Confidence {
        val enoughEvents = launchCount >= MIN_LAUNCHES_FOR_PREDICTION
        val enoughDays = distinctDays >= MIN_DISTINCT_DAYS_FOR_PREDICTION
        if (!enoughEvents || !enoughDays) return Confidence.NONE

        val sample = (launchCount / 30f).coerceIn(0f, 1f)
        val span = (distinctDays / 7f).coerceIn(0f, 1f)
        val combined = 0.5f * sample + 0.5f * span

        val level = when {
            combined >= 0.75f -> Confidence.HIGH
            combined >= 0.45f -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        // Launcher-only observations cannot outrank system usage data.
        if (source == DataSource.LOCAL || !hasUsageAccess) {
            return if (level == Confidence.HIGH) Confidence.MEDIUM else Confidence.LOW
        }
        return level
    }

    private fun primaryReason(recency: Float, time: Float, launches: Int): HabitReason = when {
        recency > 0.85f -> HabitReason.RECENTLY_USED
        time >= 0.5f -> HabitReason.TIME_OF_DAY
        launches >= 10 -> HabitReason.FREQUENT
        else -> HabitReason.FREQUENT
    }

    /**
     * Optional recommendation shown as a single chip. Returns an explicit
     * insufficient-data prediction when the history cannot support one, and
     * never launches anything by itself.
     */
    fun recommend(input: HabitInput): Prediction {
        if (!input.hasUsageAccess && input.localLaunches.isEmpty() && input.aggregates.isEmpty()) {
            return Prediction.noneAvailable("No usage data yet — grant Usage access to build habit insights.")
        }
        if (input.hasUsageAccess && input.aggregates.isEmpty()) {
            return Prediction.noneAvailable("No usage history in the selected window yet.")
        }

        val candidates = score(input).filter { it.confidence != Confidence.NONE }
        if (candidates.isEmpty()) {
            val samples = input.totalSignals
            return if (samples == 0) {
                Prediction.noneAvailable("Not enough activity recorded yet — recommendations appear after a few days of use.")
            } else {
                Prediction.insufficient(
                    reason = HabitReason.INSUFFICIENT_DATA,
                    message = "Gathering data: $samples signals seen so far, need a little more history.",
                    sampleSize = samples
                )
            }
        }

        val best = candidates.first()
        return Prediction(
            packageName = best.packageName,
            score = best.score,
            confidence = best.confidence,
            reason = best.reason,
            sampleSize = best.launchCount,
            message = messageFor(best)
        )
    }

    private fun messageFor(app: ScoredApp): String = when (app.reason) {
        HabitReason.RECENTLY_USED -> "Recently used"
        HabitReason.TIME_OF_DAY -> "Usually around this time"
        HabitReason.FREQUENT -> "Frequently opened"
        else -> "Suggested"
    }

    fun summary(input: HabitInput, todayMs: Long, weekMs: Long, historyDays: Int): HabitSummary {
        val scored = score(input)
        val top = scored.take(6)
        val todayLaunches = input.localLaunches.count { input.nowEpochMs - it.epochMs < todayMs }
        val observedBy = if (input.hasUsageAccess) DataSource.SYSTEM else DataSource.LOCAL
        val maxDistinctDays = input.aggregates.maxOfOrNull { it.distinctDays } ?: 0
        val enough = input.hasUsageAccess &&
            input.aggregates.sumOf { it.launchCount } >= MIN_LAUNCHES_FOR_PREDICTION &&
            maxDistinctDays >= MIN_DISTINCT_DAYS_FOR_PREDICTION
        return HabitSummary(
            totals = UsageTotals(
                todayMs = todayMs,
                weekMs = weekMs,
                todayLaunches = todayLaunches,
                trackedPackages = input.aggregates.size
            ),
            topApps = top,
            recentLaunches = input.localLaunches.sortedByDescending { it.epochMs }.take(8),
            prediction = recommend(input),
            hasEnoughData = enough,
            observedBy = observedBy,
            historyDays = historyDays
        )
    }

    /** 0..23 bucket for an epoch timestamp in the device's current zone. */
    fun hourOf(epochMs: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }

    /** Monday = 0 .. Sunday = 6 (matches aggregate weekday histograms). */
    fun weekdayOf(epochMs: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) // Sunday = 1
        return (dow + 5) % 7
    }
}
