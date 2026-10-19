package com.elthon.optilauncher.data

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.elthon.optilauncher.core.color.ColorMath
import com.elthon.optilauncher.core.theme.PaletteSource
import com.elthon.optilauncher.core.theme.ThemeDeriver
import com.elthon.optilauncher.core.theme.ThemeMode
import com.elthon.optilauncher.core.theme.WallpaperPalette
import com.elthon.optilauncher.core.theme.LauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads the system wallpaper palette and turns it into the launcher theme.
 *
 * - [WallpaperManager.getWallpaperColors] + [WallpaperManager.OnColorsChangedListener]
 *   do the work; the reported colors are *inputs* to [ThemeDeriver], not a claim
 *   that we read the wallpaper's exact palette.
 * - Every read happens off the main thread; the listener callback only schedules
 *   a refresh.
 * - When detection fails, [WallpaperPalette.fallback] keeps the launcher in a
 *   predefined grayscale theme instead of an unreadable state.
 * - No bitmap of the wallpaper is retained or processed repeatedly.
 */
class WallpaperThemeController(context: Context) {

    private val appContext = context.applicationContext
    private val wallpaperManager = WallpaperManager.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _theme = MutableStateFlow(ThemeDeriver.derive(null, ThemeMode.AUTO))
    val theme: StateFlow<LauncherTheme> = _theme.asStateFlow()

    @Volatile
    private var mode: ThemeMode = ThemeMode.AUTO

    @Volatile
    private var listener: WallpaperManager.OnColorsChangedListener? = null

    private var refreshJob: Job? = null

    fun start() {
        if (listener != null) return
        val newListener = WallpaperManager.OnColorsChangedListener { _, _ ->
            // Cheap callback: hop to our own scope instead of reading colors here.
            refresh("wallpaper-changed")
        }
        listener = newListener
        try {
            wallpaperManager.addOnColorsChangedListener(newListener, mainHandler)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "colors listener not registered: ${t.javaClass.simpleName}")
            listener = null
        }
        refresh("start")
    }

    fun stop() {
        listener?.let {
            try {
                wallpaperManager.removeOnColorsChangedListener(it)
            } catch (_: Throwable) {
            }
        }
        listener = null
        refreshJob?.cancel()
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    fun setMode(newMode: ThemeMode) {
        if (mode == newMode) return
        mode = newMode
        refresh("mode-changed")
    }

    fun refresh(reason: String) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val palette = readPalette()
            val derived = ThemeDeriver.derive(palette, mode)
            _theme.value = derived
            android.util.Log.d(TAG, "theme refreshed ($reason): source=${derived.source} dark=${derived.isDark} contrast=${"%.2f".format(derived.textContrast)}")
        }
    }

    /** Blocking read — always runs on [Dispatchers.IO]. */
    suspend fun readPalette(): WallpaperPalette? = withContext(Dispatchers.IO) {
        try {
            val colors = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?: return@withContext null
            toPalette(colors)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "wallpaper colors unavailable: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun toPalette(colors: WallpaperColors): WallpaperPalette {
        val primary = colors.primaryColor.toArgb()
        val secondary = colors.secondaryColor?.toArgb()
        val tertiary = colors.tertiaryColor?.toArgb()
        val reported = listOfNotNull(primary, secondary, tertiary)

        val luminance = if (reported.isEmpty()) {
            0.35f
        } else {
            val weights = floatArrayOf(0.6f, 0.3f, 0.1f)
            var acc = 0f
            var weightSum = 0f
            reported.forEachIndexed { index, color ->
                val w = weights.getOrElse(index) { 0.1f }
                acc += ColorMath.relativeLuminance(color) * w
                weightSum += w
            }
            if (weightSum > 0f) acc / weightSum else 0.35f
        }

        // getColorHints() is API 31+; on older platforms the hint is simply unknown
        // (null), which ThemeDeriver already handles by falling back to luminance.
        val supportsDark = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_THEME) != 0
        } else {
            null
        }

        return WallpaperPalette(
            colors = reported,
            supportsDarkTheme = supportsDark,
            source = PaletteSource.WALLPAPER_COLORS,
            wallpaperLuminance = luminance
        )
    }

    companion object {
        private const val TAG = "WallpaperTheme"
    }
}
