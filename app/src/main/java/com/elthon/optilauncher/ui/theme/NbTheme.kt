package com.elthon.optilauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.elthon.optilauncher.core.theme.LauncherTheme
import com.elthon.optilauncher.core.theme.ThemeMode

/**
 * Compose binding for [LauncherTheme]. Everything visible in the launcher reads
 * its colors from here, which is what keeps the whole interface monochrome and
 * wallpaper-derived instead of hardcoded per screen.
 */
@Immutable
data class NbColors(
    val onSurface: Color,
    val onSurfaceDim: Color,
    val outline: Color,
    val accent: Color,
    val surface: Color,
    val surfaceSolid: Color,
    val scrim: Color,
    val shadow: Color,
    val isDark: Boolean,
    val wallpaperLuminance: Float,
    /** Contrast of onSurface against the blended panel; used by tests/diagnostics. */
    val textContrast: Float
)

fun LauncherTheme.toNbColors(): NbColors = NbColors(
    onSurface = Color(onSurface),
    onSurfaceDim = Color(onSurfaceDim),
    outline = Color(outline),
    accent = Color(accent),
    surface = Color(surface).copy(alpha = surfaceAlpha),
    surfaceSolid = Color(surfaceSolid),
    scrim = Color(scrim),
    shadow = Color(shadow),
    isDark = isDark,
    wallpaperLuminance = wallpaperLuminance,
    textContrast = textContrast
)

val LocalNbColors = staticCompositionLocalOf { LauncherTheme.fallback(ThemeMode.AUTO).toNbColors() }

object NbType {
    val clock = TextStyle(
        fontSize = 58.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1).sp,
        fontFamily = FontFamily.SansSerif
    )
    val clockLabel = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        fontFamily = FontFamily.Monospace
    )
    val title = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )
    val body = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp
    )
    val bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )
    val label = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp,
        fontFamily = FontFamily.Monospace
    )
    val mono = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Monospace
    )
    val tab = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp
    )
    val appName = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp
    )
}

/**
 * Wraps the whole UI in the wallpaper-derived palette. [darkTheme] only wins when
 * the user pinned an explicit mode, otherwise the derived theme decides — that is
 * what "automatic wallpaper-adaptive theming" means here.
 */
@Composable
fun NoBlooksTheme(
    theme: LauncherTheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalNbColors provides theme.toNbColors()) {
        content()
    }
}
