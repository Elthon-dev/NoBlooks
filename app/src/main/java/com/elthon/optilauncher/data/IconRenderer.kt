package com.elthon.optilauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.elthon.optilauncher.core.settings.IconTreatment
import com.elthon.optilauncher.core.util.BoundedCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders real application icons into bounded-memory, monochrome-friendly bitmaps.
 *
 * The launcher never edits another APK's files and never changes system icons —
 * it only transforms a copy of the icon it draws inside its own UI:
 *
 *  - ORIGINAL   : the icon exactly as the package provides it,
 *  - GRAYSCALE  : luminance-only copy (silhouette + detail preserved),
 *  - MONO       : grayscale blended toward the theme foreground so the whole
 *                 drawer reads as one surface while staying recognizable.
 *
 * Transformations are cached in a hard-capped LRU keyed by package + treatment +
 * tint, and the whole cache is dropped when the theme tint or a package changes.
 */
class IconRenderer(context: Context, private val sizePx: Int = DEFAULT_SIZE_PX) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager
    private val cache = BoundedCache<String, ImageBitmap>(MAX_ENTRIES)

    fun cached(packageName: String, treatment: IconTreatment, tint: Int): ImageBitmap? =
        cache.get(key(packageName, treatment, tint))

    /** Blocking render — always call from a background dispatcher. */
    suspend fun render(
        packageName: String,
        treatment: IconTreatment,
        tint: Int
    ): ImageBitmap? = withContext(Dispatchers.Default) {
        cache.get(key(packageName, treatment, tint)) ?: run {
            val bitmap = draw(packageName, treatment, tint) ?: return@run null
            val image = bitmap.asImageBitmap()
            cache.put(key(packageName, treatment, tint), image)
            image
        }
    }

    fun invalidateTheme() = cache.clear()

    fun invalidatePackage(packageName: String) {
        val stale = cache.keys().filter { it.startsWith("$packageName|") }
        stale.forEach { cache.remove(it) }
    }

    fun clear() = cache.clear()

    private fun key(packageName: String, treatment: IconTreatment, tint: Int) =
        "$packageName|${treatment.name}|$tint|$sizePx"

    private fun draw(packageName: String, treatment: IconTreatment, tint: Int): Bitmap? {
        if (treatment == IconTreatment.ORIGINAL) {
            val drawable = loadDrawable(packageName) ?: return null
            return rasterize(drawable, null, 0)
        }
        val drawable = loadDrawable(packageName) ?: return null
        val filter = when (treatment) {
            IconTreatment.GRAYSCALE -> grayMatrix()
            IconTreatment.MONO -> grayThenTintMatrix(tint, TINT_STRENGTH)
            IconTreatment.ORIGINAL -> null
        }
        return rasterize(drawable, filter, if (treatment == IconTreatment.MONO) tint else 0)
    }

    private fun loadDrawable(packageName: String): Drawable? = try {
        pm.getApplicationIcon(packageName)
    } catch (_: Throwable) {
        try {
            pm.getApplicationIcon(pm.getApplicationInfo(packageName, 0))
        } catch (_: Throwable) {
            null
        }
    }

    private fun rasterize(drawable: Drawable, filter: ColorMatrixColorFilter?, tintUnder: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (tintUnder != 0) {
            // MONO: a quiet tinted plate under the transformed icon keeps light
            // glyphs readable over bright wallpapers without hiding the silhouette.
            val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tintUnder and 0x00FFFFFF or 0x22000000 }
            val inset = (sizePx * 0.06f)
            canvas.drawRoundRect(
                inset,
                inset,
                sizePx - inset,
                sizePx - inset,
                sizePx * 0.22f,
                sizePx * 0.22f,
                plate
            )
        }
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
        val scale = minOf(sizePx / w.toFloat(), sizePx / h.toFloat())
        val dw = (w * scale).toInt().coerceAtLeast(1)
        val dh = (h * scale).toInt().coerceAtLeast(1)
        val left = (sizePx - dw) / 2
        val top = (sizePx - dh) / 2
        drawable.setBounds(left, top, left + dw, top + dh)

        if (filter == null) {
            drawable.draw(canvas)
        } else {
            val layer = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val layerCanvas = Canvas(layer)
            drawable.draw(layerCanvas)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = filter }
            canvas.drawBitmap(layer, 0f, 0f, paint)
            if (layer !== out) layer.recycle()
        }
        return out
    }

    private fun grayMatrix(): ColorMatrixColorFilter {
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        return ColorMatrixColorFilter(matrix)
    }

    private fun grayThenTintMatrix(tint: Int, strength: Float): ColorMatrixColorFilter {
        val k = strength.coerceIn(0f, 1f)
        val tr = (tint shr 16 and 0xFF) / 255f
        val tg = (tint shr 8 and 0xFF) / 255f
        val tb = (tint and 0xFF) / 255f
        val desat = ColorMatrix().apply { setSaturation(0f) }
        val towardTint = ColorMatrix(
            floatArrayOf(
                1f - k, 0f, 0f, 0f, tr * k,
                0f, 1f - k, 0f, 0f, tg * k,
                0f, 0f, 1f - k, 0f, tb * k,
                0f, 0f, 0f, 1f, 0f
            )
        )
        desat.postConcat(towardTint)
        return ColorMatrixColorFilter(desat)
    }

    companion object {
        const val DEFAULT_SIZE_PX = 144
        const val MAX_ENTRIES = 96
        private const val TINT_STRENGTH = 0.30f
    }
}
