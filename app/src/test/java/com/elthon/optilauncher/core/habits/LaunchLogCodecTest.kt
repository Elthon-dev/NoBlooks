package com.elthon.optilauncher.core.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchLogCodecTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `encode and decode round-trip preserves launches newest-last`() {
        val entries = listOf(
            LocalLaunch("com.cam", now - 2 * day),
            LocalLaunch("com.phone", now - 1_000L)
        )
        val decoded = LaunchLogCodec.decode(LaunchLogCodec.encode(entries, now), now)
        assertEquals(entries, decoded)
    }

    @Test
    fun `null or malformed payloads decode to nothing instead of throwing`() {
        assertTrue(LaunchLogCodec.decode(null, now).isEmpty())
        assertTrue(LaunchLogCodec.decode("", now).isEmpty())
        assertTrue(LaunchLogCodec.decode("garbage-without-separator", now).isEmpty())
        assertTrue(LaunchLogCodec.decode("com.cam|not-a-number", now).isEmpty())
    }

    @Test
    fun `entries older than the retention window are trimmed`() {
        val fresh = LocalLaunch("com.cam", now - 1_000L)
        val stale = LocalLaunch("com.old", now - 20 * day)
        val decoded = LaunchLogCodec.decode(LaunchLogCodec.encode(listOf(stale, fresh), now), now)
        assertEquals(listOf(fresh), decoded)
    }

    @Test
    fun `entry count is hard-capped keeping the newest`() {
        val many = (0 until LaunchLogCodec.MAX_ENTRIES + 40).map { i ->
            LocalLaunch("com.app$i", now - (LaunchLogCodec.MAX_ENTRIES + 40 - i) * 1_000L)
        }
        val decoded = LaunchLogCodec.decode(LaunchLogCodec.encode(many, now), now)
        assertEquals(LaunchLogCodec.MAX_ENTRIES, decoded.size)
        assertEquals("com.app${LaunchLogCodec.MAX_ENTRIES + 39}", decoded.last().packageName)
    }

    @Test
    fun `append adds one entry and keeps the log ordered`() {
        val existing = listOf(LocalLaunch("com.cam", now - 10_000L))
        val updated = LaunchLogCodec.append(existing, LocalLaunch("com.phone", now - 1_000L), now)
        assertEquals(listOf("com.cam", "com.phone"), updated.map { it.packageName })
    }

    @Test
    fun `future timestamps are discarded`() {
        val future = LocalLaunch("com.cam", now + day)
        assertTrue(LaunchLogCodec.decode(LaunchLogCodec.encode(listOf(future), now), now).isEmpty())
    }
}
