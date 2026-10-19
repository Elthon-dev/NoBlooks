package com.elthon.optilauncher.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.ui.components.Chip
import com.elthon.optilauncher.ui.components.Glyph
import com.elthon.optilauncher.ui.components.IconGlyph
import com.elthon.optilauncher.ui.components.MonoButton
import com.elthon.optilauncher.ui.components.NbText
import com.elthon.optilauncher.ui.components.SectionLabel
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbType

/**
 * First-run overlay.
 *
 * Three honest points, no dark patterns: what the launcher is, the two optional
 * grants (home role, usage access) and a way out that is not hidden. Nothing is
 * required for the launcher to work, and nothing is requested silently.
 */
@Composable
fun Onboarding(
    isDefaultHome: Boolean,
    onSetHome: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceSolid)
            .border(BorderStroke(1.dp, colors.outline), RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NbText("NoBlooks", style = NbType.title, color = colors.onSurface)
        NbText(
            "A wallpaper-first home screen: one floating island, monochrome, everything on-device.",
            style = NbType.bodySmall,
            color = colors.onSurfaceDim
        )

        SectionLabel("Setup")
        SetupRow(
            glyph = Glyph.HOME,
            title = if (isDefaultHome) "Home app: done" else "Make NoBlooks your home app",
            body = if (isDefaultHome) {
                "You will see this screen whenever you press Home."
            } else {
                "Required for the island to appear on the Home button."
            },
            done = isDefaultHome,
            actionLabel = if (isDefaultHome) null else "Set home app",
            onAction = onSetHome
        )
        SetupRow(
            glyph = Glyph.CHART,
            title = "Usage access (optional)",
            body = "Only used to compute habit insights locally. Without it, NoBlooks uses its own launch log.",
            done = false,
            actionLabel = "Grant usage access",
            onAction = onGrantUsageAccess
        )
        SetupRow(
            glyph = Glyph.MIC,
            title = "Microphone (never automatic)",
            body = "NoBlooks never listens in the background. The mic is only touched if you start the level check in Settings.",
            done = true,
            actionLabel = null,
            onAction = {}
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoButton("Start using NoBlooks", onClick = onFinish, primary = true)
            Chip("No accounts · no network", accent = false)
        }
        NbText(
            "You can change or undo all of this in the island's Settings page.",
            style = NbType.mono,
            color = colors.onSurfaceDim
        )
    }
}

@Composable
private fun SetupRow(
    glyph: Glyph,
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    val colors = LocalNbColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconGlyph(
            if (done) Glyph.CHECK else glyph,
            if (done) colors.accent else colors.onSurfaceDim,
            Modifier.padding(top = 2.dp).size(16.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NbText(title, style = NbType.body, color = colors.onSurface)
            NbText(body, style = NbType.bodySmall, color = colors.onSurfaceDim)
        }
        if (actionLabel != null) {
            MonoButton(actionLabel, onClick = onAction)
        }
    }
}
