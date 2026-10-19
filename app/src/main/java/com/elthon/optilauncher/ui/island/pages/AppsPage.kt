package com.elthon.optilauncher.ui.island.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elthon.optilauncher.core.apps.AppRef
import com.elthon.optilauncher.core.apps.AppSearch
import com.elthon.optilauncher.core.apps.SearchResult
import com.elthon.optilauncher.core.habits.Prediction
import com.elthon.optilauncher.data.AppEntry
import com.elthon.optilauncher.ui.components.AppIcon
import com.elthon.optilauncher.ui.components.Chip
import com.elthon.optilauncher.ui.components.EmptyState
import com.elthon.optilauncher.ui.components.Glyph
import com.elthon.optilauncher.ui.components.IconGlyph
import com.elthon.optilauncher.ui.components.IconTap
import com.elthon.optilauncher.ui.components.MonoButton
import com.elthon.optilauncher.ui.components.NbText
import com.elthon.optilauncher.ui.components.SectionLabel
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbType

/**
 * Apps grid — the island's default page.
 *
 * Ordering is deliberately boring when there is no query: the prediction (when
 * it has real confidence) gets one prominent row, pinned favorites come next,
 * then everything alphabetical. No fabricated "trending" noise.
 */
@Composable
fun AppsPage(
    apps: List<AppEntry>,
    query: String,
    searchFocused: Boolean,
    pinned: Set<String>,
    prediction: Prediction,
    loading: Boolean,
    iconFor: (String) -> ImageBitmap?,
    onQueryChanged: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppRef) -> Unit,
    onDismissFocus: () -> Unit,
    onPredictedOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchFocused) {
        if (searchFocused) {
            focusRequester.requestFocus()
        }
    }

    val searchResult = remember(apps, query) { AppSearch.search(apps.map { it.toRef() }, query) }
    val entriesByRef = remember(apps) { apps.associateBy { it.component.flattenToString() } }
    val matches: List<AppEntry> = if (query.isBlank()) {
        apps
    } else {
        searchResult.apps.mapNotNull { entriesByRef[it.component] }
    }

    Column(modifier.fillMaxSize()) {
        // --- search bar ----------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceSolid.copy(alpha = 0.60f))
                .border(BorderStroke(1.dp, colors.outline), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconGlyph(Glyph.SEARCH, colors.onSurfaceDim, Modifier.size(16.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                            onDismissFocus()
                            true
                        } else {
                            false
                        }
                    }
                    .semantics { contentDescription = "Search installed apps" },
                textStyle = NbType.body.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onDismissFocus() })
            )
            if (query.isNotEmpty()) {
                IconTap(
                    glyph = Glyph.CLOSE,
                    onClick = { onQueryChanged("") },
                    tint = colors.onSurfaceDim,
                    size = 14.dp,
                    contentDescription = "Clear search"
                )
            }
        }

        // --- results -------------------------------------------------------
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (loading && apps.isEmpty()) {
                EmptyState(
                    title = "Reading installed apps…",
                    description = "The launcher asks Android for launchable activities. Nothing leaves the phone.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp)
                )
            } else if (query.isNotBlank() && matches.isEmpty()) {
                val noApps = searchResult.reason == SearchResult.EmptyReason.NO_APPS
                EmptyState(
                    title = if (noApps) "No launchable apps" else "No matches",
                    description = if (noApps) {
                        "This device reports no apps with a launcher entry."
                    } else {
                        "Nothing on this device matches \"$query\"."
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp)
                )
            } else {
                if (query.isBlank() && prediction.hasPrediction) {
                    PredictionRow(
                        prediction = prediction,
                        iconFor = iconFor,
                        onOpen = onPredictedOpen,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                val pinnedApps = if (query.isBlank()) {
                    matches.filter { it.packageName in pinned }
                } else {
                    emptyList()
                }
                val rest = matches.filterNot { it.packageName in pinned }

                if (pinnedApps.isNotEmpty()) {
                    SectionLabel(
                        "Pinned",
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
                    )
                    AppGrid(pinnedApps, pinned, iconFor, onLaunch, onTogglePin)
                }
                if (rest.isNotEmpty()) {
                    SectionLabel(
                        if (query.isNotBlank()) "${rest.size} results" else "All apps",
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
                    )
                    AppGrid(rest, pinned, iconFor, onLaunch, onTogglePin)
                }
            }
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<AppEntry>,
    pinned: Set<String>,
    iconFor: (String) -> ImageBitmap?,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppRef) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        items(apps, key = { it.component.flattenToString() }) { app ->
            AppCell(
                app = app,
                pinned = app.packageName in pinned,
                iconFor = iconFor,
                onLaunch = { onLaunch(app) },
                onTogglePin = { onTogglePin(app.toRef()) }
            )
        }
    }
}

@Composable
private fun AppCell(
    app: AppEntry,
    pinned: Boolean,
    iconFor: (String) -> ImageBitmap?,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit
) {
    val colors = LocalNbColors.current
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(app.packageName) {
                detectTapGestures(
                    onTap = { onLaunch() },
                    onLongPress = { onTogglePin() }
                )
            }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            AppIcon(
                bitmap = iconFor(app.packageName),
                label = app.label,
                modifier = Modifier.size(42.dp)
            )
            if (pinned) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .border(BorderStroke(1.dp, colors.surfaceSolid), CircleShape)
                )
            }
        }
        NbText(
            app.label,
            style = NbType.appName,
            color = colors.onSurface,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = app.label
        )
    }
}

@Composable
private fun PredictionRow(
    prediction: Prediction,
    iconFor: (String) -> ImageBitmap?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    val pkg = prediction.packageName ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceSolid.copy(alpha = 0.45f))
            .border(BorderStroke(1.dp, colors.outline), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIcon(bitmap = iconFor(pkg), label = pkg, modifier = Modifier.size(34.dp))
        Column(Modifier.weight(1f)) {
            Chip("Next up · ${prediction.confidence.name.lowercase()}", accent = true)
            NbText(
                prediction.message,
                style = NbType.bodySmall,
                color = colors.onSurfaceDim,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        MonoButton("Open", onClick = onOpen, primary = true)
    }
}
