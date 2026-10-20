package com.elthon.optilauncher.core.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillStatusTest {

    @Test
    fun `clock pads both fields`() {
        assertEquals("09:05", PillStatus.clock(9, 5))
        assertEquals("23:59", PillStatus.clock(23, 59))
    }

    @Test
    fun `clock handles midnight`() {
        assertEquals("00:00", PillStatus.clock(0, 0))
    }

    @Test
    fun `clock clamps bad readings`() {
        assertEquals("23:59", PillStatus.clock(25, 99))
        assertEquals("00:00", PillStatus.clock(-1, -5))
    }

    @Test
    fun `weekday maps iso values`() {
        assertEquals("Mon", PillStatus.weekday(1))
        assertEquals("Sun", PillStatus.weekday(7))
        // Anything unexpected still lands on a real day, never a blank chip.
        assertEquals("Sun", PillStatus.weekday(0))
        assertEquals("Sun", PillStatus.weekday(99))
    }

    @Test
    fun `battery percent computes and clamps`() {
        assertEquals("100%", PillStatus.batteryPercent(100, 100))
        assertEquals("50%", PillStatus.batteryPercent(50, 100))
        assertEquals("0%", PillStatus.batteryPercent(0, 100))
        assertEquals("85%", PillStatus.batteryPercent(85, 100))
    }

    @Test
    fun `battery percent guards a zero scale`() {
        assertEquals("—", PillStatus.batteryPercent(50, 0))
    }

    @Test
    fun `low battery threshold`() {
        assertTrue(PillStatus.isLow(10, 100))
        assertTrue(PillStatus.isLow(15, 100))
        assertFalse(PillStatus.isLow(16, 100))
        assertFalse(PillStatus.isLow(50, 0))
    }
}
