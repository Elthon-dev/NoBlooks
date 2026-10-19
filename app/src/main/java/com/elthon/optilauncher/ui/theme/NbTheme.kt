package com.elthon.optilauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/**
 * Shared geometry tokens.
 *
 * The home widget and the floating island deliberately share one screen inset and
 * one corner rhythm, so the two surfaces on the wallpaper read as a set instead of
 * two unrelated cards.
 */
object NbDim {
    /** Left/right inset used by every surface drawn on the wallpaper. */
    val ScreenInset = 16.dp

    /** Collapsed island pill. */
    val PillWidth = 122.dp
    val PillHeight = 48.dp
    val PillIcon = 20.dp
    val PillTapTarget = 40.dp

    /** Expanded panel. */
    val PanelMaxWidth = 372.dp
    val PanelMaxHeight = 430.dp
    val PanelCornerTop = 30.dp
    val PanelCornerBottom = 24.dp

    /** Horizontal content inset inside every island page. */
    val ContentInset = 16.dp
    val ControlGap = 10.dp
    val TabIcon = 18.dp

    /** Home widget card. */
    val WidgetCorner = 26.dp
    val WidgetPadding = 18.dp
}


object NbType {
    val clock = TextStyle(
        fontSize = 52.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1.5).sp,
        fontFamily = FontFamily.SansSerif
    )
    val clockLabel = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        fontFamily = FontFamily.Monospace
    )
    /** Small live values inside the widget card (battery, ring readout). */
    val widgetValue = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        fontFamily = FontFamily.Monospace
    )
    val weekday = TextStyle(
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.1.sp,
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
