package com.elthon.optilauncher.ui.island.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.core.theme.LauncherTheme
import com.elthon.optilauncher.core.theme.PaletteSource
import com.elthon.optilauncher.ui.components.Chip
import com.elthon.optilauncher.ui.components.MonoButton
import com.elthon.optilauncher.ui.components.NbText
import com.elthon.optilauncher.ui.components.SectionLabel
import com.elthon.optilauncher.ui.theme.NbType

/**
 * Wallpaper page.
 *
 * The point is to make the theme derivation inspectable, not magical: the user
 * can see where the palette came from, what was derived, and jump straight to
 * the system picker to change the wallpaper.
 */
@Composable
fun WallpaperPage(
    theme: LauncherTheme,
    onOpenWallpaperPicker: () -> Unit,
    onReDeriveTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = com.elthon.optilauncher.ui.theme.LocalNbColors.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionLabel(
            "Wallpaper",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
        )
        NbText(
            "Colors above are derived from the current wallpaper — change it and the whole " +
                "launcher re-derives automatically. No filters, no network.",
            style = NbType.bodySmall,
            color = colors.onSurfaceDim,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MonoButton("Open wallpaper picker", onClick = onOpenWallpaperPicker, primary = true)
            MonoButton("Re-derive", onClick = onReDeriveTheme)
        }

        SectionLabel(
            "Derived palette",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Swatch("Accent", Color(theme.accent), Modifier.weight(1f))
            Swatch("Text", Color(theme.onSurface), Modifier.weight(1f))
            Swatch("Dim", Color(theme.onSurfaceDim), Modifier.weight(1f))
            Swatch("Line", Color(theme.outline), Modifier.weight(1f))
            Swatch("Panel", Color(theme.surfaceSolid), Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Chip(
                if (theme.isDark) "Dark surface" else "Light surface",
                glyph = com.elthon.optilauncher.ui.components.Glyph.IMAGE,
                accent = true
            )
            Chip("Luminance ${(theme.wallpaperLuminance * 100).toInt()}%")
            Chip(
                when (theme.source) {
                    PaletteSource.WALLPAPER_COLORS -> "From WallpaperColors"
                    PaletteSource.FALLBACK -> "Fallback palette"
                }
            )
        }

        NbText(
            if (theme.source == PaletteSource.WALLPAPER_COLORS) {
                "Source: the system reported the wallpaper's dominant colors."
            } else {
                "Source: no live wallpaper colors available, so a neutral fallback palette is in use."
            },
            style = NbType.mono,
            color = colors.onSurfaceDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun Swatch(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = com.elthon.optilauncher.ui.theme.LocalNbColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .border(BorderStroke(1.dp, colors.outline), RoundedCornerShape(12.dp))
        )
        NbText(label, style = NbType.label, color = colors.onSurfaceDim, maxLines = 1)
    }
}
