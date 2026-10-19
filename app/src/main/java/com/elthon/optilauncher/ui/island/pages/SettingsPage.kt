package com.elthon.optilauncher.ui.island.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.core.habits.HabitSummary
import com.elthon.optilauncher.core.settings.IconTreatment
import com.elthon.optilauncher.core.theme.ThemeMode
import com.elthon.optilauncher.data.UsageAccess
import com.elthon.optilauncher.ui.components.Glyph
import com.elthon.optilauncher.ui.components.Hairline
import com.elthon.optilauncher.ui.components.MonoSwitch
import com.elthon.optilauncher.ui.components.NbText
import com.elthon.optilauncher.ui.components.OptionRow
import com.elthon.optilauncher.ui.components.SectionLabel
import com.elthon.optilauncher.ui.components.SegmentedRow
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbType

/**
 * Settings page: appearance, behavior, permissions, diagnostics, data, about.
 *
 * Diagnostics surfaces the same information the recon scan collects, so "what
 * does this launcher know about my phone" is answerable from the UI itself.
 */
@Composable
fun SettingsPage(
    themeMode: ThemeMode,
    iconTreatment: IconTreatment,
    reducedMotion: Boolean,
    suggestionsEnabled: Boolean,
    usageAccess: UsageAccess,
    micPermissionLabel: String,
    isDefaultHome: Boolean,
    homeRoleNote: String,
    pinnedCount: Int,
    appsCount: Int,
    historyDays: Int,
    onThemeMode: (ThemeMode) -> Unit,
    onIconTreatment: (IconTreatment) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    onSuggestions: (Boolean) -> Unit,
    onGrantUsageAccess: () -> Unit,
    onMicPermissionAction: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onAppDetails: () -> Unit,
    onClearHistory: () -> Unit,
    onClearFavorites: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    var diagnosticsOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionLabel("Appearance", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        SegmentedRow(
            options = ThemeMode.entries,
            selected = themeMode,
            labelFor = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            onSelect = onThemeMode,
            modifier = Modifier.padding(horizontal = 16.dp),
            contentDescriptionPrefix = "Theme mode"
        )
        SegmentedRow(
            options = IconTreatment.entries,
            selected = iconTreatment,
            labelFor = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            onSelect = onIconTreatment,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentDescriptionPrefix = "Icon treatment"
        )
        NbText(
            "Icons are rendered locally in grayscale so third-party branding never " +
                "clashes with the wallpaper-derived palette.",
            style = NbType.bodySmall,
            color = colors.onSurfaceDim,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Hairline(Modifier.padding(vertical = 14.dp))
        SectionLabel("Behavior", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        OptionRow(
            title = "Reduced motion",
            description = "Island morph and page changes snap instead of spring.",
            trailing = {
                MonoSwitch(
                    checked = reducedMotion,
                    onCheckedChange = onReducedMotion,
                    contentDescription = "Reduced motion"
                )
            }
        )
        OptionRow(
            title = "Suggestions",
            description = "Show the next-app suggestion above the collapsed island.",
            trailing = {
                MonoSwitch(
                    checked = suggestionsEnabled,
                    onCheckedChange = onSuggestions,
                    contentDescription = "Suggestions"
                )
            }
        )

        Hairline(Modifier.padding(vertical = 14.dp))
        SectionLabel("Permissions & home", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        OptionRow(
            title = "Usage access",
            description = when (usageAccess) {
                UsageAccess.GRANTED -> "Granted — habits use real usage stats."
                UsageAccess.DENIED -> "Denied — habits fall back to the launcher's own log."
                UsageAccess.UNKNOWN -> "Not checked yet."
            },
            onClick = onGrantUsageAccess,
            trailing = {
                com.elthon.optilauncher.ui.components.IconGlyph(
                    Glyph.CHEVRON,
                    colors.onSurfaceDim,
                    Modifier.padding(start = 4.dp).size(14.dp)
                )
            }
        )
        OptionRow(
            title = "Microphone",
            description = micPermissionLabel,
            onClick = onMicPermissionAction
        )
        OptionRow(
            title = "Default home app",
            description = if (isDefaultHome) {
                "NoBlooks is your home app."
            } else {
                homeRoleNote
            },
            onClick = onOpenHomeSettings
        )

        Hairline(Modifier.padding(vertical = 14.dp))
        SectionLabel("Diagnostics", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        OptionRow(
            title = if (diagnosticsOpen) "Hide diagnostics" else "Show diagnostics",
            description = "Installed launchable apps, history window, device caps.",
            onClick = { diagnosticsOpen = !diagnosticsOpen }
        )
        AnimatedVisibility(visible = diagnosticsOpen) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                DiagnosticLine("Launchable apps", appsCount.toString())
                DiagnosticLine("Pinned favorites", pinnedCount.toString())
                DiagnosticLine("History window", "$historyDays day(s)")
                DiagnosticLine("Usage access", usageAccess.name)
                DiagnosticLine("Microphone", micPermissionLabel)
                DiagnosticLine("Default home", isDefaultHome.toString())
            }
        }

        Hairline(Modifier.padding(vertical = 14.dp))
        SectionLabel("Data", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        OptionRow(title = "App details for NoBlooks", onClick = onAppDetails)
        OptionRow(
            title = "Clear habit history",
            description = "Deletes the launcher's own launch log. Android's usage stats are never modified.",
            onClick = onClearHistory,
            destructive = true
        )
        OptionRow(
            title = "Clear pinned apps",
            onClick = onClearFavorites,
            destructive = true
        )

        NbText(
            "NoBlooks · wallpaper-reactive home launcher · all processing on-device",
            style = NbType.mono,
            color = colors.onSurfaceDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
        )
    }
}

@Composable
private fun DiagnosticLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NbText(label, style = NbType.bodySmall, color = colors.onSurfaceDim)
        NbText(value, style = NbType.mono, color = colors.onSurface)
    }
}
