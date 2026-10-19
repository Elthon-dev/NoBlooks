package com.elthon.optilauncher.core.theme

import com.elthon.optilauncher.core.color.ColorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDeriverTest {

    private fun palette(
        colors: List<Int>,
        luminance: Float,
        supportsDark: Boolean? = null
    ) = WallpaperPalette(
        colors = colors,
        supportsDarkTheme = supportsDark,
        source = PaletteSource.WALLPAPER_COLORS,
        wallpaperLuminance = luminance
    )

    @Test
    fun `missing palette falls back to a readable grayscale theme`() {
        val theme = ThemeDeriver.derive(null, ThemeMode.AUTO)
        assertEquals(PaletteSource.FALLBACK, theme.source)
        assertTrue(
            "text contrast was ${theme.textContrast}",
            theme.textContrast >= ThemeDeriver.MIN_TEXT_CONTRAST
        )
    }

    @Test
    fun `explicit light mode is honoured even for a dark wallpaper`() {
        val theme = ThemeDeriver.derive(palette(listOf(0xFF101012.toInt()), 0.05f), ThemeMode.LIGHT)
        assertFalse(theme.isDark)
    }

    @Test
    fun `explicit dark mode is honoured even for a bright wallpaper`() {
        val theme = ThemeDeriver.derive(palette(listOf(0xFFFDFDFD.toInt()), 0.95f), ThemeMode.DARK)
        assertTrue(theme.isDark)
    }

    @Test
    fun `auto derives dark from a dark wallpaper and light from a bright one`() {
        assertTrue(ThemeDeriver.derive(palette(listOf(0xFF0B0B0E.toInt()), 0.08f), ThemeMode.AUTO).isDark)
        assertFalse(ThemeDeriver.derive(palette(listOf(0xFFF2F2F4.toInt()), 0.9f), ThemeMode.AUTO).isDark)
    }

    @Test
    fun `text contrast stays readable for extreme wallpapers`() {
        val cases = listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        for (luminance in cases) {
            for (mode in ThemeMode.entries) {
                val theme = ThemeDeriver.derive(palette(listOf(0xFF808080.toInt()), luminance), mode)
                assertTrue(
                    "mode=$mode luminance=$luminance contrast=${theme.textContrast}",
                    theme.textContrast >= ThemeDeriver.MIN_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun `accent stays quiet and legible`() {
        val theme = ThemeDeriver.derive(
            palette(listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()), 0.2f),
            ThemeMode.DARK
        )
        val saturation = ColorMath.saturation(theme.accent)
        assertTrue("accent saturation was $saturation", saturation <= ThemeDeriver.MAX_ACCENT_SATURATION + 0.01f)
    }

    @Test
    fun `wallpaper source is preserved`() {
        val theme = ThemeDeriver.derive(palette(listOf(0xFF223344.toInt()), 0.3f), ThemeMode.AUTO)
        assertEquals(PaletteSource.WALLPAPER_COLORS, theme.source)
    }

    @Test
    fun `support dark hint is overridden by a clearly bright wallpaper`() {
        val theme = ThemeDeriver.derive(
            palette(listOf(0xFFF8F8F8.toInt()), luminance = 0.8f, supportsDark = true),
            ThemeMode.AUTO
        )
        assertFalse(theme.isDark)
    }
}
