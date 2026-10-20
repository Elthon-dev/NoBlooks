package com.elthon.optilauncher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
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

    /** Collapsed island pill: a portrait capsule, because it lives on the side edge. */
    val PillWidth = 54.dp
    val PillHeight = 112.dp
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

    /** Instrument slot: both home surfaces start from the same top inset. */
    val WidgetTopInset = 18.dp
    val InstrumentWidth = 252.dp
    /** Breathing room between the instrument's bottom edge and the expanded panel. */
    val InstrumentGap = 14.dp

    /** The control centre floats inside the screen, leaving a strip of wallpaper. */
    val ControlMargin = 26.dp
    val ControlMarginLandscape = 18.dp
}

/**
 * Motion language — fluid, never springy.
 *
 * Two sources, both chosen so nothing ever overshoots:
 *  - [enterSpring] / [exitSpring] are critically damped (damping ratio 1.0), so
 *    they settle in the shortest time *without oscillating*, while still carrying
 *    velocity across an interruption the way a duration curve cannot;
 *  - the emphasized curves below are the Material 3 tokens, used for content
 *    reveals where a literal, designed duration reads better than physics.
 *
 * What is deliberately absent: any under-damped spring. A damping ratio below
 * 1.0 is what makes motion read as "cartoony" — it overshoots its own target.
 */
object NbMotion {
    /** M3 duration steps, milliseconds. */
    const val Short4 = 200
    const val Medium1 = 250
    const val Medium2 = 300
    const val Medium4 = 400

    /** M3 emphasized easing set — decelerate on the way in, accelerate on the way out. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * Silhouette opening: ~180ms, zero bounce. It has to be long enough to read as
     * motion (a 120ms spring looked like a snap) and still settle well under the
     * time a person waits before wondering if the tap registered.
     */
    val enterSpring: SpringSpec<Float> = SpringSpec(Spring.DampingRatioNoBouncy, stiffness = 650f)

    /** Silhouette closing: quicker still, so the launcher is never held hostage. */
    val exitSpring: SpringSpec<Float> = SpringSpec(Spring.DampingRatioNoBouncy, stiffness = 2500f)

    /** Content settling inside a moving container. */
    val contentSpring: SpringSpec<Float> = SpringSpec(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
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
