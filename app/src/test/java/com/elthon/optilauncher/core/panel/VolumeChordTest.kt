package com.elthon.optilauncher.core.panel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeChordTest {

    private fun chord() = VolumeChord(windowMs = 150L, debounceMs = 1_500L)

    @Test
    fun `a single key never fires`() {
        val c = chord()
        assertFalse(c.onKey(VolumeKey.UP, down = true, nowMs = 0L))
        assertFalse(c.onKey(VolumeKey.UP, down = false, nowMs = 10L))
    }

    @Test
    fun `both keys together fire once`() {
        val c = chord()
        assertFalse(c.onKey(VolumeKey.UP, down = true, nowMs = 1_000L))
        assertTrue(c.onKey(VolumeKey.DOWN, down = true, nowMs = 1_080L))
    }

    @Test
    fun `a chord at uptime zero still fires`() {
        // 0 is a valid monotonic reading; it must not double as "key is up".
        val c = chord()
        assertFalse(c.onKey(VolumeKey.UP, down = true, nowMs = 0L))
        assertTrue(c.onKey(VolumeKey.DOWN, down = true, nowMs = 80L))
    }

    @Test
    fun `a repeated down does not count as a fresh press`() {
        val c = chord()
        c.onKey(VolumeKey.UP, down = true, nowMs = 1_000L)
        assertTrue(c.onKey(VolumeKey.DOWN, down = true, nowMs = 1_050L))
        // Held-key repeats arrive with the same state; they must not re-arm.
        assertFalse(c.onKey(VolumeKey.DOWN, down = true, nowMs = 1_060L))
        assertFalse(c.onKey(VolumeKey.DOWN, down = true, nowMs = 2_700L))
    }

    @Test
    fun `order does not matter`() {
        val c = chord()
        assertFalse(c.onKey(VolumeKey.DOWN, down = true, nowMs = 2_000L))
        assertTrue(c.onKey(VolumeKey.UP, down = true, nowMs = 2_120L))
    }

    @Test
    fun `too far apart is not a chord`() {
        val c = chord()
        assertFalse(c.onKey(VolumeKey.UP, down = true, nowMs = 1_000L))
        assertFalse(c.onKey(VolumeKey.DOWN, down = true, nowMs = 1_400L))
    }

    @Test
    fun `a held chord fires once`() {
        val c = chord()
        c.onKey(VolumeKey.UP, down = true, nowMs = 1_000L)
        assertTrue(c.onKey(VolumeKey.DOWN, down = true, nowMs = 1_050L))
        // Key-repeat of the DOWN event must not re-fire.
        assertFalse(c.onKey(VolumeKey.DOWN, down = true, nowMs = 1_100L))
        assertFalse(c.onKey(VolumeKey.DOWN, down = true, nowMs = 1_200L))
    }

    @Test
    fun `releasing and re-chording after the debounce fires again`() {
        val c = chord()
        c.onKey(VolumeKey.UP, down = true, nowMs = 1_000L)
        assertTrue(c.onKey(VolumeKey.DOWN, down = true, nowMs = 1_050L))
        c.onKey(VolumeKey.UP, down = false, nowMs = 1_100L)
        c.onKey(VolumeKey.DOWN, down = false, nowMs = 1_150L)

        c.onKey(VolumeKey.UP, down = true, nowMs = 3_000L)
        assertTrue(c.onKey(VolumeKey.DOWN, down = true, nowMs = 3_050L))
    }

    @Test
    fun `a quick double tap of one key is not a chord`() {
        val c = chord()
        c.onKey(VolumeKey.UP, down = true, nowMs = 1_000L)
        c.onKey(VolumeKey.UP, down = false, nowMs = 1_030L)
        assertFalse(c.onKey(VolumeKey.UP, down = true, nowMs = 1_060L))
    }
}
