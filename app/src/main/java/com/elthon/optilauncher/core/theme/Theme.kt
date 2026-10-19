package com.elthon.optilauncher.core.theme

import com.elthon.optilauncher.core.color.ColorMath

/** How the user wants theming to behave. */
enum class ThemeMode { AUTO, DARK, LIGHT }

/** Where the wallpaper palette inputs came from. */
enum class PaletteSource { WALLPAPER_COLORS, FALLBACK }

/**
 * The raw inputs handed to [ThemeDeriver]. [colors] are the colors Android's
 * [android.app.wallpaper.WallpaperColors] reported (primary/secondary/tertiary),
 * not a claim about the wallpaper's exact palette. Null [colors] means detection
 * failed or was unavailable — the deriver must then fall back gracefully.
 */
data class WallpaperPalette(
    val colors: List<Int>,
    val supportsDarkTheme: Boolean?,
    val source: PaletteSource,
    val wallpaperLuminance: Float
) {
    companion object {
        fun fallback(): WallpaperPalette = WallpaperPalette(
            colors = emptyList(),
            supportsDarkTheme = null,
            source = PaletteSource.FALLBACK,
            wallpaperLuminance = 0.35f
        )
    }
}

/**
 * The complete, compact theme model every launcher surface reads from.
 * Principal interface stays monochrome; [accent] is a restrained identity color.
 */
data class LauncherTheme(
    val mode: ThemeMode,
    val isDark: Boolean,
    val source: PaletteSource,
    val wallpaperLuminance: Float,
    /** Tint of the floating island / translucent panels (premultiplied by [surfaceAlpha]). */
    val surface: Int,
    val surfaceAlpha: Float,
    /** Stronger opaque surface for toasts of text that must always be readable. */
    val surfaceSolid: Int,
    val onSurface: Int,
    val onSurfaceDim: Int,
    /** Hairlines and outlines. */
    val outline: Int,
    /** Shadow color for the island (alpha carries depth). */
    val shadow: Int,
    /** Scrim behind the expanded island. */
    val scrim: Int,
    /** Restrained accent used for active/selected states. */
    val accent: Int,
    /** Measured contrast of [onSurface] against the blended panel over the wallpaper. */
    val textContrast: Float
) {
    /** Contrast-safe foreground for arbitrary text drawn directly on the wallpaper. */
    fun onWallpaper(): Int = ColorMath.bestForeground(
        background = if (isDark) 0xFF101012.toInt() else 0xFFEFEFEF.toInt(),
        light = onSurface,
        dark = onSurfaceDim
    )

    /** Panel color blended over an assumed wallpaper luminance (for contrast checks). */
    fun effectivePanelBackground(wallpaperLuminance: Float = this.wallpaperLuminance): Int {
        val wallpaperGray = grayAt(wallpaperLuminance)
        return ColorMath.blend(
            foreground = ColorMath.withAlpha(surface, surfaceAlpha),
            background = wallpaperGray,
            t = surfaceAlpha
        )
    }

    private fun grayAt(luminance: Float): Int {
        val v = (luminance.coerceIn(0f, 1f) * 255).toInt()
        return ColorMath.argb(255, v, v, v)
    }

    companion object {
        /**
         * Predefined grayscale used when wallpaper detection fails entirely, and as
         * the base that [ThemeDeriver] adjusts. Deep, calm, readable on anything.
         */
        fun fallback(mode: ThemeMode = ThemeMode.AUTO): LauncherTheme = LauncherTheme(
            mode = mode,
            isDark = mode != ThemeMode.LIGHT,
            source = PaletteSource.FALLBACK,
            wallpaperLuminance = 0.35f,
            surface = if (mode == ThemeMode.LIGHT) 0xFFFFFFFF.toInt() else 0xFF0B0B0C.toInt(),
            surfaceAlpha = if (mode == ThemeMode.LIGHT) 0.86f else 0.82f,
            surfaceSolid = if (mode == ThemeMode.LIGHT) 0xFFF6F6F7.toInt() else 0xFF121214.toInt(),
            onSurface = if (mode == ThemeMode.LIGHT) 0xFF0A0A0B.toInt() else 0xFFF4F4F6.toInt(),
            onSurfaceDim = if (mode == ThemeMode.LIGHT) 0xFF5A5A60.toInt() else 0xFF9A9AA2.toInt(),
            outline = if (mode == ThemeMode.LIGHT) 0x33000000 else 0x33FFFFFF,
            shadow = 0x66000000,
            scrim = if (mode == ThemeMode.LIGHT) 0x14000000 else 0x4D000000,
            accent = if (mode == ThemeMode.LIGHT) 0xFF4A4A52.toInt() else 0xFFD8D8DE.toInt(),
            textContrast = 15f
        )
    }
}

/**
 * Turns wallpaper inputs into a deliberate monochrome theme.
 *
 * Rules:
 *  - brightness is decided by measured luminance (and [WallpaperColors]'s
 *    supportsDarkTheme hint when present), never by a coin flip;
 *  - foreground/background contrast is checked and repaired before the theme is
 *    published, so text stays readable over bright *and* dark wallpapers;
 *  - color survives only as a low-saturation accent;
 *  - a missing palette produces the predefined grayscale fallback, never a crash.
 */
object ThemeDeriver {

    const val MIN_TEXT_CONTRAST = 4.5f
    const val MIN_ACCENT_CONTRAST = 3.0f
    const val MAX_ACCENT_SATURATION = 0.22f

    fun derive(palette: WallpaperPalette?, mode: ThemeMode = ThemeMode.AUTO): LauncherTheme {
        if (palette == null || palette.source == PaletteSource.FALLBACK || palette.colors.isEmpty()) {
            return themeFromInputs(
                mode = mode,
                isDark = mode != ThemeMode.LIGHT,
                luminance = palette?.wallpaperLuminance ?: 0.35f,
                accentInput = null,
                source = PaletteSource.FALLBACK
            )
        }

        val luminance = palette.wallpaperLuminance.coerceIn(0f, 1f)
        val isDark = when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.AUTO -> palette.supportsDarkTheme ?: (luminance < 0.5f)
        }
        // In AUTO, a wallpaper that says "I support dark content" is a hint about the
        // *wallpaper*, not about the launcher text. Guard against a bright wallpaper
        // paired with a dark-ish surface decision by re-checking luminance.
        val effectiveDark = when (mode) {
            ThemeMode.AUTO -> if (palette.supportsDarkTheme == true && luminance > 0.62f) false else isDark
            else -> isDark
        }

        val dominant = dominantColor(palette.colors)
        return themeFromInputs(
            mode = mode,
            isDark = effectiveDark,
            luminance = luminance,
            accentInput = dominant,
            source = palette.source
        )
    }

    /** Average of the reported colors weighted equally (they are already curated). */
    internal fun dominantColor(colors: List<Int>): Int? {
        val opaque = colors.filter { ColorMath.alpha(it) > 0 }
        if (opaque.isEmpty()) return null
        var r = 0L
        var g = 0L
        var b = 0L
        for (c in opaque) {
            r += ColorMath.red(c)
            g += ColorMath.green(c)
            b += ColorMath.blue(c)
        }
        val n = opaque.size
        return ColorMath.argb(255, (r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun themeFromInputs(
        mode: ThemeMode,
        isDark: Boolean,
        luminance: Float,
        accentInput: Int?,
        source: PaletteSource
    ): LauncherTheme {
        // Panel tint follows brightness: translucent black over dark wallpapers,
        // translucent white over bright ones, so the wallpaper reads through either way.
        val surface = if (isDark) 0xFF08080A.toInt() else 0xFFFFFFFF.toInt()
        val surfaceAlpha = if (isDark) 0.84f else 0.88f
        val surfaceSolid = if (isDark) 0xFF121214.toInt() else 0xFFF5F5F6.toInt()

        val panel = ColorMath.blend(ColorMath.withAlpha(surface, surfaceAlpha), grayAt(luminance), surfaceAlpha)
        var onSurface = if (isDark) 0xFFF5F5F7.toInt() else 0xFF0B0B0C.toInt()
        var onSurfaceDim = if (isDark) 0xFF9C9CA4.toInt() else 0xFF55555C.toInt()
        var contrast = ColorMath.contrastRatio(panel, onSurface)

        // Repair pass: if the blended panel lost contrast (e.g. a mid-gray wallpaper
        // under a light panel), push the foreground to the extreme that wins.
        if (contrast < MIN_TEXT_CONTRAST) {
            val candidate = ColorMath.bestForeground(panel, light = 0xFFFFFFFF.toInt(), dark = 0xFF000000.toInt())
            onSurface = candidate
            onSurfaceDim = if (candidate == 0xFFFFFFFF.toInt()) 0xFFBDBDC4.toInt() else 0xFF45454C.toInt()
            contrast = ColorMath.contrastRatio(panel, onSurface)
        }

        val accent = accentFrom(accentInput, panel, isDark)
        val outline = if (isDark) 0x2EFFFFFF else 0x2A000000
        val scrim = if (isDark) 0x59000000 else 0x1F000000

        return LauncherTheme(
            mode = mode,
            isDark = isDark,
            source = source,
            wallpaperLuminance = luminance,
            surface = surface,
            surfaceAlpha = surfaceAlpha,
            surfaceSolid = surfaceSolid,
            onSurface = onSurface,
            onSurfaceDim = onSurfaceDim,
            outline = outline,
            shadow = if (isDark) 0x99000000.toInt() else 0x52000000,
            scrim = scrim,
            accent = accent,
            textContrast = contrast
        )
    }

    internal fun accentFrom(input: Int?, panel: Int, isDark: Boolean): Int {
        val fallbackAccent = if (isDark) 0xFFD8D8DE.toInt() else 0xFF3C3C44.toInt()
        if (input == null) return fallbackAccent
        var accent = ColorMath.desaturate(input, MAX_ACCENT_SATURATION)
        // Keep the accent legible but quiet: a mid luminance band, then verify contrast.
        val target = if (isDark) 0.72f else 0.34f
        accent = ColorMath.withLuminance(accent, target)
        var guard = 0
        while (ColorMath.contrastRatio(panel, accent) < MIN_ACCENT_CONTRAST && guard < 8) {
            accent = ColorMath.shade(accent, if (isDark) 0.12f else -0.12f)
            guard++
        }
        if (ColorMath.contrastRatio(panel, accent) < MIN_ACCENT_CONTRAST) return fallbackAccent
        return accent
    }

    private fun grayAt(luminance: Float): Int {
        val v = (luminance.coerceIn(0f, 1f) * 255).toInt()
        return ColorMath.argb(255, v, v, v)
    }
}
