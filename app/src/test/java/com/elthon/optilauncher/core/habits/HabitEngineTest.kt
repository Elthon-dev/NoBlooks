package com.elthon.optilauncher.core.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitEngineTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun aggregate(
        pkg: String,
        launches: Int,
        lastUsedMs: Long,
        distinctDays: Int = 3,
        source: DataSource = DataSource.SYSTEM,
        durationMs: Long = 60_000L
    ) = AppAggregate(
        packageName = pkg,
        launchCount = launches,
        totalDurationMs = durationMs,
        lastUsedEpochMs = lastUsedMs,
        hourHistogram = IntArray(24) { if (it == HabitEngine.hourOf(now)) launches else 0 },
        weekdayHistogram = IntArray(7) { if (it == HabitEngine.weekdayOf(now)) launches else 0 },
        distinctDays = distinctDays,
        source = source
    )

    @Test
    fun `recency decays by half every half-life`() {
        assertEquals(1f, HabitEngine.recencyScore(now, now), 0.001f)
        val halfLifeMs = (HabitEngine.HALF_LIFE_DAYS * day).toLong()
        assertEquals(0.5f, HabitEngine.recencyScore(now - halfLifeMs, now), 0.01f)
        assertEquals(0.25f, HabitEngine.recencyScore(now - 2 * halfLifeMs, now), 0.01f)
        assertEquals(0f, HabitEngine.recencyScore(0L, now), 0.001f)
    }

    @Test
    fun `confidence requires both enough events and enough days`() {
        assertEquals(
            Confidence.NONE,
            HabitEngine.confidenceFor(launchCount = 2, distinctDays = 1, source = DataSource.SYSTEM, hasUsageAccess = true)
        )
        assertEquals(
            Confidence.HIGH,
            HabitEngine.confidenceFor(launchCount = 60, distinctDays = 10, source = DataSource.SYSTEM, hasUsageAccess = true)
        )
    }

    @Test
    fun `launcher-only observations can never be high confidence`() {
        val capped = HabitEngine.confidenceFor(
            launchCount = 200,
            distinctDays = 30,
            source = DataSource.LOCAL,
            hasUsageAccess = false
        )
        assertTrue("was $capped", capped == Confidence.MEDIUM || capped == Confidence.LOW)
    }

    @Test
    fun `score ignores apps with no launches and ranks the recent one first`() {
        val input = HabitInput(
            nowEpochMs = now,
            aggregates = listOf(
                aggregate("com.old.app", launches = 8, lastUsedMs = now - 20 * day),
                aggregate("com.fresh.app", launches = 12, lastUsedMs = now - 5_000L),
                aggregate("com.silent.app", launches = 0, lastUsedMs = 0L)
            ),
            localLaunches = emptyList(),
            hasUsageAccess = true
        )
        val scored = HabitEngine.score(input)
        assertEquals(2, scored.size)
        assertEquals("com.fresh.app", scored.first().packageName)
        assertFalse(scored.any { it.packageName == "com.silent.app" })
    }

    @Test
    fun `recommendation is explicit when there is no data at all`() {
        val prediction = HabitEngine.recommend(HabitInput.empty(now))
        assertFalse(prediction.hasPrediction)
        assertTrue(prediction.message.isNotBlank())
    }

    @Test
    fun `recommendation appears only when confidence exists`() {
        val strong = HabitInput(
            nowEpochMs = now,
            aggregates = listOf(aggregate("com.cam", launches = 30, lastUsedMs = now - 1_000L)),
            localLaunches = emptyList(),
            hasUsageAccess = true
        )
        val prediction = HabitEngine.recommend(strong)
        assertTrue(prediction.hasPrediction)
        assertEquals("com.cam", prediction.packageName)

        val weak = strong.copy(
            aggregates = listOf(aggregate("com.cam", launches = 1, lastUsedMs = now - 1_000L, distinctDays = 1))
        )
        assertFalse(HabitEngine.recommend(weak).hasPrediction)
    }

    @Test
    fun `recommendation skips packages the launcher cannot see, never leaks a name`() {
        // The top-scoring package is not in the launchable set (stale usage row for
        // an uninstalled app). With a populated knownPackages the engine must fall
        // through to the best *known* app instead of suggesting a phantom.
        val input = HabitInput(
            nowEpochMs = now,
            aggregates = listOf(
                aggregate("com.ghost.app", launches = 40, lastUsedMs = now - 1_000L),
                aggregate("com.real.app", launches = 30, lastUsedMs = now - 1_000L)
            ),
            localLaunches = emptyList(),
            hasUsageAccess = true,
            knownPackages = setOf("com.real.app")
        )
        val prediction = HabitEngine.recommend(input)
        assertTrue(prediction.hasPrediction)
        assertEquals("com.real.app", prediction.packageName)
    }

    @Test
    fun `recommendation with no known candidates declines instead of pointing at a ghost`() {
        val input = HabitInput(
            nowEpochMs = now,
            aggregates = listOf(aggregate("com.ghost.app", launches = 40, lastUsedMs = now - 1_000L)),
            localLaunches = emptyList(),
            hasUsageAccess = true,
            knownPackages = setOf("com.real.app")
        )
        assertFalse(HabitEngine.recommend(input).hasPrediction)
    }

    @Test
    fun `summary reports system observation and totals`() {
        val input = HabitInput(
            nowEpochMs = now,
            aggregates = listOf(aggregate("com.cam", launches = 20, lastUsedMs = now - 1_000L)),
            localLaunches = listOf(
                LocalLaunch("com.cam", now - 1_000L),
                LocalLaunch("com.other", now - 20 * day)
            ),
            hasUsageAccess = true
        )
        val summary = HabitEngine.summary(input, todayMs = 3_600_000L, weekMs = 7_200_000L, historyDays = 7)

        assertEquals(DataSource.SYSTEM, summary.observedBy)
        assertEquals(7, summary.historyDays)
        assertEquals(1, summary.totals.todayLaunches)
        assertEquals(1, summary.totals.trackedPackages)
        assertTrue(summary.hasEnoughData)
        assertNotNull(summary.prediction)
    }

    @Test
    fun `summary marks local-only data as insufficient for strong conclusions`() {
        val input = HabitInput(
            nowEpochMs = now,
            aggregates = listOf(aggregate("com.cam", launches = 4, lastUsedMs = now - 1_000L, source = DataSource.LOCAL)),
            localLaunches = listOf(LocalLaunch("com.cam", now - 1_000L)),
            hasUsageAccess = false
        )
        val summary = HabitEngine.summary(input, todayMs = 16_000L, weekMs = 16_000L, historyDays = 7)
        assertEquals(DataSource.LOCAL, summary.observedBy)
        assertFalse(summary.hasEnoughData)
    }
}
