package com.elthon.optilauncher.core.color

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Color math for the wallpaper-adaptive monochrome theme.
 *
 * Everything here is plain Kotlin over packed 0xAARRGGBB ints so it can be unit
 * tested without Android. Channel math follows the sRGB relative-luminance and
 * WCAG contrast-ratio definitions — this is a deliberate selection algorithm,
 * not an attempt to read a "true" palette out of the wallpaper.
 */
object ColorMath {

    const val OPAQUE = 0xFF

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)

    fun alpha(color: Int): Int = color ushr 24 and 0xFF
    fun red(color: Int): Int = color shr 16 and 0xFF
    fun green(color: Int): Int = color shr 8 and 0xFF
    fun blue(color: Int): Int = color and 0xFF

    /** sRGB channel (0..255) -> linear light (0..1). */
    fun linearize(channel: Int): Float {
        val c = channel / 255f
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    /** WCAG relative luminance of an opaque color, 0 (black) .. 1 (white). */
    fun relativeLuminance(color: Int): Float {
        val r = linearize(red(color))
        val g = linearize(green(color))
        val b = linearize(blue(color))
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** WCAG contrast ratio between two colors, 1 .. 21. Alpha of inputs is ignored. */
    fun contrastRatio(a: Int, b: Int): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = max(la, lb)
        val darker = min(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Picks whichever of [light]/[dark] reads better on [background].
     * Returns the candidate with the higher contrast ratio (ties -> [light]).
     */
    fun bestForeground(background: Int, light: Int = 0xFFFFFFFF.toInt(), dark: Int = 0xFF0A0A0B.toInt()): Int =
        if (contrastRatio(background, light) >= contrastRatio(background, dark)) light else dark

    fun blend(foreground: Int, background: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val a = alpha(background) / 255f
        val outA = alpha(foreground) / 255f * k + a * (1f - k)
        if (outA <= 0f) return 0
        fun ch(fg: Int, bg: Int): Int {
            val v = (fg * k + bg * (1f - k)) / outA
            return v.roundToInt().coerceIn(0, 255)
        }
        return argb(
            (outA * 255).roundToInt(),
            ch(red(foreground), red(background)),
            ch(green(foreground), green(background)),
            ch(blue(foreground), blue(background))
        )
    }

    fun withAlpha(color: Int, alphaFraction: Float): Int =
        argb((alphaFraction.coerceIn(0f, 1f) * 255).roundToInt(), red(color), green(color), blue(color))

    /** Perceptual grayscale (Rec.709 luma), alpha preserved. */
    fun grayscale(color: Int): Int {
        val l = (0.2126f * red(color) + 0.7152f * green(color) + 0.0722f * blue(color)).roundToInt()
        return argb(alpha(color), l, l, l)
    }

    /** Saturation in [0,1] of the RGB triple (0 = gray, 1 = fully saturated). */
    fun saturation(color: Int): Float {
        val r = red(color)
        val g = green(color)
        val b = blue(color)
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        if (maxC == 0) return 0f
        return (maxC - minC) / maxC.toFloat()
    }

    /**
     * Caps HSV saturation at [maxSaturation] while preserving hue and average
     * lightness.
     *
     * A single lerp toward the luma gray does NOT bound saturation (it leaves a
     * pure red around 0.57 even "desaturated to 0.22"), so the blend factor is
     * solved by bisection against the same [saturation] metric callers assert on.
     */
    fun desaturate(color: Int, maxSaturation: Float): Int {
        val s = saturation(color)
        val cap = maxSaturation.coerceIn(0f, 1f)
        if (s <= cap || s == 0f) return color

        val r = red(color)
        val g = green(color)
        val b = blue(color)
        val gray = (r + g + b) / 3f

        fun candidateAt(k: Float): Triple<Int, Int, Int> {
            fun mix(channel: Int): Int =
                (channel + (gray - channel) * k).roundToInt().coerceIn(0, 255)
            return Triple(mix(r), mix(g), mix(b))
        }

        var low = 0f
        var high = 1f
        repeat(18) {
            val mid = (low + high) / 2f
            val (cr, cg, cb) = candidateAt(mid)
            val mixed = argb(255, cr, cg, cb)
            if (saturation(mixed) > cap) low = mid else high = mid
        }

        val (cr, cg, cb) = candidateAt(high)
        return argb(alpha(color), cr, cg, cb)
    }

    /** Scales luminance up/down while keeping hue proportions, clamped to valid channels. */
    fun withLuminance(color: Int, targetLuminance: Float): Int {
        val current = relativeLuminance(color)
        val target = targetLuminance.coerceIn(0f, 1f)
        if (current <= 0.0001f) {
            val v = (target * 255).roundToInt()
            return argb(alpha(color), v, v, v)
        }
        // Gamma-ish scaling keeps the ramp perceptually even instead of clipping hard.
        val scale = (target / current).pow(0.6f)
        return argb(
            alpha(color),
            (red(color) * scale).roundToInt().coerceIn(0, 255),
            (green(color) * scale).roundToInt().coerceIn(0, 255),
            (blue(color) * scale).roundToInt().coerceIn(0, 255)
        )
    }

    /** Lightens (t > 0) or darkens (t < 0) toward white/black by a fraction. */
    fun shade(color: Int, t: Float): Int =
        if (t >= 0) blend(0xFFFFFFFF.toInt(), color, t) else blend(0xFF000000.toInt(), color, -t)

    fun toHex(color: Int): String = buildString(9) {
        append('#')
        append(alpha(color).toString(16).padStart(2, '0').uppercase())
        append(red(color).toString(16).padStart(2, '0').uppercase())
        append(green(color).toString(16).padStart(2, '0').uppercase())
        append(blue(color).toString(16).padStart(2, '0').uppercase())
    }
}
