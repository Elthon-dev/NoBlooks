package com.elthon.optilauncher.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun `contrast between black and white is the maximum 21`() {
        val ratio = ColorMath.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertTrue("expected ~21, got $ratio", ratio > 20.9f && ratio < 21.1f)
    }

    @Test
    fun `contrast is symmetric`() {
        val a = 0xFF3366CC.toInt()
        val b = 0xFFEDEDEF.toInt()
        assertEquals(
            ColorMath.contrastRatio(a, b),
            ColorMath.contrastRatio(b, a),
            0.0001f
        )
    }

    @Test
    fun `relative luminance endpoints are 0 and 1`() {
        assertEquals(0f, ColorMath.relativeLuminance(0xFF000000.toInt()), 0.001f)
        assertEquals(1f, ColorMath.relativeLuminance(0xFFFFFFFF.toInt()), 0.001f)
    }

    @Test
    fun `grayscale keeps channels equal and saturation is driven to zero`() {
        val gray = ColorMath.grayscale(0xFFD22B2B.toInt())
        assertEquals(ColorMath.red(gray), ColorMath.green(gray))
        assertEquals(ColorMath.green(gray), ColorMath.blue(gray))
        assertEquals(0f, ColorMath.saturation(gray), 0.0001f)
    }

    @Test
    fun `desaturate never exceeds the requested saturation cap`() {
        val capped = ColorMath.desaturate(0xFFFF0000.toInt(), 0.22f)
        assertTrue("saturation was ${ColorMath.saturation(capped)}", ColorMath.saturation(capped) <= 0.24f)
    }

    @Test
    fun `withAlpha preserves rgb and sets alpha`() {
        val color = ColorMath.withAlpha(0xFF123456.toInt(), 0.5f)
        assertEquals(0x12, ColorMath.red(color))
        assertEquals(0x34, ColorMath.green(color))
        assertEquals(0x56, ColorMath.blue(color))
        assertTrue(ColorMath.alpha(color) in 120..135)
    }

    @Test
    fun `bestForeground picks the readable extreme`() {
        val onWhite = ColorMath.bestForeground(0xFFFFFFFF.toInt())
        val onBlack = ColorMath.bestForeground(0xFF000000.toInt())
        assertEquals(0xFF0A0A0B.toInt(), onWhite)
        assertEquals(0xFFFFFFFF.toInt(), onBlack)
    }

    @Test
    fun `toHex produces an argb hex string`() {
        val hex = ColorMath.toHex(0xFF1A2B3C.toInt())
        assertEquals(9, hex.length)
        assertTrue(hex.startsWith("#"))
        assertTrue(hex.equals("#ff1a2b3c", ignoreCase = true))
    }
}
