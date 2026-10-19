package com.elthon.optilauncher.core.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregatorTest {

    private fun fg(pkg: String, at: Long) = UsageEvent(pkg, UsageEvent.Type.FOREGROUND, at)
    private fun bg(pkg: String, at: Long) = UsageEvent(pkg, UsageEvent.Type.BACKGROUND, at)

    @Test
    fun `a foreground then background pair counts one entry and its duration`() {
        val start = 1_700_000_000_000L
        val aggregate = UsageAggregator.aggregate(
            events = listOf(fg("com.cam", start), bg("com.cam", start + 5_000L)),
            windowStartMs = start - 1_000L,
            windowEndMs = start + 10_000L
        ).single()

        assertEquals("com.cam", aggregate.packageName)
        assertEquals(1, aggregate.launchCount)
        assertEquals(5_000L, aggregate.totalDurationMs)
        assertEquals(1, aggregate.distinctDays)
        assertEquals(DataSource.SYSTEM, aggregate.source)
    }

    @Test
    fun `an unpaired foreground is counted but never given a guessed duration`() {
        val start = 1_700_000_000_000L
        val aggregate = UsageAggregator.aggregate(
            events = listOf(fg("com.cam", start)),
            windowStartMs = start - 1_000L,
            windowEndMs = start + 10_000L
        ).single()

        assertEquals(1, aggregate.launchCount)
        assertEquals(0L, aggregate.totalDurationMs)
    }

    @Test
    fun `consecutive foreground rows for the same package are treated as one entry`() {
        val start = 1_700_000_000_000L
        val aggregate = UsageAggregator.aggregate(
            events = listOf(
                fg("com.cam", start),
                fg("com.cam", start + 1_000L),
                bg("com.cam", start + 2_000L)
            ),
            windowStartMs = start - 1_000L,
            windowEndMs = start + 10_000L
        ).single()

        assertEquals(1, aggregate.launchCount)
    }

    @Test
    fun `events outside the window are ignored`() {
        val start = 1_700_000_000_000L
        val aggregated = UsageAggregator.aggregate(
            events = listOf(
                fg("com.old", start - 100_000L),
                fg("com.cam", start + 1_000L),
                bg("com.cam", start + 2_000L)
            ),
            windowStartMs = start,
            windowEndMs = start + 10_000L
        )

        assertEquals(listOf("com.cam"), aggregated.map { it.packageName })
    }

    @Test
    fun `days are counted distinctly across calendar boundaries`() {
        val day1 = 1_700_000_000_000L
        val day2 = day1 + 86_400_000L
        val aggregate = UsageAggregator.aggregate(
            events = listOf(
                fg("com.cam", day1),
                bg("com.cam", day1 + 1_000L),
                fg("com.cam", day2),
                bg("com.cam", day2 + 1_000L)
            ),
            windowStartMs = day1 - 1_000L,
            windowEndMs = day2 + 10_000L
        ).single()

        assertEquals(2, aggregate.launchCount)
        assertEquals(2, aggregate.distinctDays)
        assertEquals(2_000L, aggregate.totalDurationMs)
    }

    @Test
    fun `empty input produces nothing`() {
        assertTrue(UsageAggregator.aggregate(emptyList(), 0L, 1_000L).isEmpty())
    }

    @Test
    fun `blank package names are skipped`() {
        assertTrue(
            UsageAggregator.aggregate(
                events = listOf(fg("", 1_700_000_000_000L)),
                windowStartMs = 0L,
                windowEndMs = 1_800_000_000_000L
            ).isEmpty()
        )
    }
}
