package com.elthon.optilauncher.ui.island.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.core.habits.Confidence
import com.elthon.optilauncher.core.habits.HabitSummary
import com.elthon.optilauncher.core.habits.Prediction
import com.elthon.optilauncher.data.UsageAccess
import com.elthon.optilauncher.ui.components.AppIcon
import com.elthon.optilauncher.ui.components.ConfidenceDots
import com.elthon.optilauncher.ui.components.EmptyState
import com.elthon.optilauncher.ui.components.MonoButton
import com.elthon.optilauncher.ui.components.NbText
import com.elthon.optilauncher.ui.components.SectionLabel
import com.elthon.optilauncher.ui.components.StatBar
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbDim
import com.elthon.optilauncher.ui.theme.NbType

/**
 * Habits page.
 *
 * Every number here is honest: system usage stats when access is granted, the
 * launcher's own launch log otherwise (capped at LOW confidence), and an
 * explicit empty state — never invented activity — when neither exists.
 */
@Composable
fun HabitsPage(
    summary: HabitSummary?,
    usageAccess: UsageAccess,
    loading: Boolean,
    hasLocalHistory: Boolean,
    iconFor: (String) -> ImageBitmap?,
    labelFor: (String) -> String?,
    onGrantUsageAccess: () -> Unit,
    onAppDetails: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        when {
            summary == null && loading -> EmptyState(
                title = if (usageAccess == UsageAccess.GRANTED) {
                    "Reading usage history…"
                } else {
                    "Collecting launcher activity…"
                },
                description = "Bounded window, up to 7 days, capped event scan. Results update in place.",
                modifier = Modifier.padding(horizontal = NbDim.ContentInset, vertical = 28.dp)
            )

            summary == null || (!hasLocalHistory && usageAccess != UsageAccess.GRANTED) -> EmptyState(
                title = "No habit data yet",
                description = if (usageAccess == UsageAccess.GRANTED) {
                    "Usage access is granted but no usage events have been recorded yet."
                } else {
                    "Grant usage access for the real picture, or just use the launcher — " +
                        "its own launch log is honest but limited."
                },
                modifier = Modifier.padding(horizontal = NbDim.ContentInset, vertical = 28.dp),
                action = {
                    if (usageAccess != UsageAccess.GRANTED) {
                        MonoButton(
                            "Grant usage access",
                            onClick = onGrantUsageAccess,
                            primary = true
                        )
                    }
                }
            )

            else -> {
                val data = summary ?: return@Column
                TotalsRow(data, Modifier.padding(horizontal = NbDim.ContentInset, vertical = 6.dp))

                SectionLabel(
                    "Prediction",
                    Modifier.padding(start = NbDim.ContentInset, end = NbDim.ContentInset, top = 14.dp, bottom = 6.dp)
                )
                PredictionCard(
                    prediction = data.prediction,
                    labelFor = labelFor,
                    iconFor = iconFor,
                    onOpenDetails = { pkg -> if (pkg != null) onAppDetails(pkg) },
                    modifier = Modifier.padding(horizontal = NbDim.ContentInset)
                )

                if (data.topApps.isNotEmpty()) {
                    SectionLabel(
                        "Most used",
                        Modifier.padding(start = NbDim.ContentInset, end = NbDim.ContentInset, top = 16.dp, bottom = 2.dp)
                    )
                    data.topApps.take(8).forEach { scored ->
                        HabitRow(
                            packageName = scored.packageName,
                            score = scored.score,
                            launches = scored.launchCount,
                            confidence = scored.confidence,
                            labelFor = labelFor,
                            iconFor = iconFor,
                            onClick = { onAppDetails(scored.packageName) },
                            modifier = Modifier.padding(horizontal = NbDim.ContentInset)
                        )
                    }
                }

                NbText(
                    buildString {
                        append("Observed by ")
                        append(
                            when (data.observedBy) {
                                com.elthon.optilauncher.core.habits.DataSource.SYSTEM ->
                                    "Android usage stats"
                                com.elthon.optilauncher.core.habits.DataSource.LOCAL ->
                                    "NoBlooks' own launch log"
                            }
                        )
                        append(" · ")
                        append(data.historyDays)
                        append(" day(s) of history")
                    },
                    style = NbType.mono,
                    color = colors.onSurfaceDim,
                    modifier = Modifier.padding(horizontal = NbDim.ContentInset, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun TotalsRow(
    summary: HabitSummary,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCell(
            label = "Today",
            value = formatDuration(summary.totals.todayMs),
            modifier = Modifier.weight(1f)
        )
        StatCell(
            label = "This week",
            value = formatDuration(summary.totals.weekMs),
            modifier = Modifier.weight(1f)
        )
        StatCell(
            label = "Launches",
            value = summary.totals.todayLaunches.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceSolid)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NbText(label.uppercase(), style = NbType.label, color = colors.onSurfaceDim, maxLines = 1)
        NbText(value, style = NbType.title, color = colors.onSurface, maxLines = 1)
    }
}

@Composable
private fun PredictionCard(
    prediction: Prediction,
    labelFor: (String) -> String?,
    iconFor: (String) -> ImageBitmap?,
    onOpenDetails: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    val pkg = prediction.packageName
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceSolid)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (pkg != null) {
            AppIcon(bitmap = iconFor(pkg), label = labelFor(pkg) ?: pkg, modifier = Modifier.size(34.dp))
        }
        Column(Modifier.weight(1f)) {
            NbText(
                if (pkg != null) (labelFor(pkg) ?: pkg) else "Not enough data",
                style = NbType.body,
                color = colors.onSurface,
                maxLines = 1
            )
            NbText(
                prediction.message,
                style = NbType.bodySmall,
                color = colors.onSurfaceDim,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        ConfidenceDots(prediction.confidence)
        if (pkg != null) {
            MonoButton("Open", onClick = { onOpenDetails(pkg) })
        }
    }
}

@Composable
private fun HabitRow(
    packageName: String,
    score: Float,
    launches: Int,
    confidence: Confidence,
    labelFor: (String) -> String?,
    iconFor: (String) -> ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIcon(bitmap = iconFor(packageName), label = labelFor(packageName) ?: packageName, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            NbText(
                labelFor(packageName) ?: packageName,
                style = NbType.body,
                color = colors.onSurface,
                maxLines = 1
            )
            StatBar(fraction = score, modifier = Modifier.padding(top = 5.dp))
        }
        NbText("$launches", style = NbType.mono, color = colors.onSurfaceDim)
        ConfidenceDots(confidence)
    }
}

/** "1h 20m" / "12m" / "0m" — no false precision below the minute. */
internal fun formatDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
