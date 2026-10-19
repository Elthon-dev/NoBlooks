package com.elthon.optilauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.core.habits.Confidence
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbType
import androidx.compose.foundation.text.BasicText

/** Single text primitive — the whole UI renders type through this. */
@Composable
fun NbText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NbType.body,
    color: Color = LocalNbColors.current.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    contentDescription: String? = null
) {
    BasicText(
        text = text,
        modifier = if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        },
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow
    )
}

/** Uppercase monospace section label with an optional trailing slot. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NbText(
            text = text.uppercase(),
            style = NbType.label,
            color = LocalNbColors.current.onSurfaceDim
        )
        trailing?.invoke()
    }
}

/** Hairline separator tuned for translucent panels over a wallpaper. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = LocalNbColors.current.outline) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/**
 * Tactile monochrome button: subtle pressed scale + alpha instead of a ripple,
 * so it never introduces a colored flash over the wallpaper.
 */
@Composable
fun MonoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalNbColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(110), label = "btnScale")

    val background = when {
        primary -> colors.accent.copy(alpha = if (pressed) 0.88f else 0.92f)
        else -> colors.surfaceSolid.copy(alpha = if (pressed) 0.94f else 0.78f)
    }
    val content = if (primary) {
        if (colors.isDark) Color(0xFF0A0A0B) else Color(0xFFF4F4F6)
    } else {
        colors.onSurface
    }

    Row(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(BorderStroke(1.dp, if (primary) Color.Transparent else colors.outline), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (glyph != null) {
            IconGlyph(glyph, content, Modifier.size(16.dp))
        }
        NbText(text, style = NbType.body, color = content)
        trailing?.invoke()
    }
}

/** Icon-only circular button with a ≥40dp target. */
@Composable
fun IconTap(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalNbColors.current.onSurface,
    size: Dp = 20.dp,
    contentDescription: String,
    background: Color? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.55f else 1f, tween(110), label = "iconAlpha")
    Box(
        modifier = modifier
            .size(44.dp)
            .scale(if (pressed) 0.94f else 1f)
            .clip(CircleShape)
            .let { if (background != null) it.background(background) else it }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        IconGlyph(glyph, tint, Modifier.size(size))
    }
}

/** Settings/option row: label, optional description, optional trailing control. */
@Composable
fun OptionRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    destructive: Boolean = false
) {
    val colors = LocalNbColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (pressed && onClick != null) 0.65f else 1f)
            .let {
                if (onClick != null) {
                    it.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    it
                }
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            NbText(
                title,
                style = NbType.body,
                color = if (destructive) Color(0xFFE88C7A) else colors.onSurface
            )
            if (description != null) {
                NbText(
                    description,
                    style = NbType.bodySmall,
                    color = colors.onSurfaceDim,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (value != null) {
            NbText(value, style = NbType.mono, color = colors.onSurfaceDim)
        }
        trailing?.invoke()
    }
}

/** Minimal, hand-drawn switch (no Material dependency). */
@Composable
fun MonoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val colors = LocalNbColors.current
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "switch")
    val trackColor = if (checked) colors.accent else colors.outline
    Box(
        modifier = modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor.copy(alpha = if (checked) 0.85f else 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onCheckedChange(!checked)
            }
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(start = 2.dp)
                .size(20.dp)
                .alpha(0f)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = (2 + progress * 16).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (colors.isDark) Color(0xFF141416) else Color(0xFFF7F7F8))
        )
    }
}

/** Confidence indicator: hollow -> filled dots, never a fake precise percentage. */
@Composable
fun ConfidenceDots(
    confidence: Confidence,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    val filled = when (confidence) {
        Confidence.NONE -> 0
        Confidence.LOW -> 1
        Confidence.MEDIUM -> 2
        Confidence.HIGH -> 3
    }
    val tint = if (confidence == Confidence.NONE) colors.onSurfaceDim else colors.accent
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) tint else Color.Transparent)
                    .border(BorderStroke(1.dp, colors.outline), CircleShape)
            )
        }
    }
}

/** Centered, quiet empty state. */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val colors = LocalNbColors.current
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NbText(title, style = NbType.title, color = colors.onSurface)
        NbText(
            description,
            style = NbType.bodySmall,
            color = colors.onSurfaceDim,
            modifier = Modifier.fillMaxWidth(),
            overflow = TextOverflow.Clip
        )
        action?.invoke()
    }
}

/** Small bordered chip used for tags, statuses and quick info. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    accent: Boolean = false
) {
    val colors = LocalNbColors.current
    val tint = if (accent) colors.accent else colors.onSurfaceDim
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surfaceSolid.copy(alpha = 0.6f))
            .border(BorderStroke(1.dp, colors.outline), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (glyph != null) {
            IconGlyph(glyph, tint, Modifier.size(12.dp))
        }
        NbText(text, style = NbType.label, color = tint, maxLines = 1)
    }
}

/** Transient feedback line shown above the island (launch results, notes). */
@Composable
fun TransientMessage(
    message: String?,
    modifier: Modifier = Modifier,
    onDismissed: () -> Unit = {}
) {
    val colors = LocalNbColors.current
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 },
        modifier = modifier
    ) {
        val text = message ?: return@AnimatedVisibility
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.surfaceSolid.copy(alpha = 0.92f))
                .border(BorderStroke(1.dp, colors.outline), RoundedCornerShape(999.dp))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismissed)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            NbText(text, style = NbType.bodySmall, color = colors.onSurface, maxLines = 2)
        }
    }
}

/**
 * Segmented monochrome selector (theme mode, icon treatment).
 * Selection is shown by inverting, not by adding a colored accent — the whole
 * UI must stay grayscale-forward.
 */
@Composable
fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionPrefix: String = ""
) {
    val colors = LocalNbColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceSolid.copy(alpha = 0.55f))
            .border(BorderStroke(1.dp, colors.outline), RoundedCornerShape(12.dp))
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) colors.accent.copy(alpha = 0.85f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(option) }
                    .padding(vertical = 8.dp)
                    .semantics {
                        contentDescription = "$contentDescriptionPrefix ${labelFor(option)}"
                    },
                contentAlignment = Alignment.Center
            ) {
                NbText(
                    labelFor(option),
                    style = NbType.label,
                    color = if (active) {
                        if (colors.isDark) Color(0xFF0A0A0B) else Color(0xFFF4F4F6)
                    } else {
                        colors.onSurfaceDim
                    },
                    maxLines = 1
                )
            }
        }
    }
}

/** App icon rendered through [com.elthon.optilauncher.data.IconRenderer]. */
@Composable
fun AppIcon(
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = label,
            modifier = modifier.size(size)
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(LocalNbColors.current.surfaceSolid.copy(alpha = 0.7f))
                .border(BorderStroke(1.dp, LocalNbColors.current.outline), RoundedCornerShape(size * 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            IconGlyph(Glyph.GRID, LocalNbColors.current.onSurfaceDim, Modifier.size(size * 0.5f))
        }
    }
}

/** Bar used in the habits page: monochrome, value labeled, never decorative. */
@Composable
fun StatBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    val colors = LocalNbColors.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(260), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.outline)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.accent.copy(alpha = 0.9f))
        )
    }
}
