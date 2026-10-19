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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.elthon.optilauncher.ui.theme.NbDim
import com.elthon.optilauncher.ui.theme.NbType

/**
 * Apps page — habit-first, search-second.
 *
 * With no query the page is an answer, not a phone book: the prediction row,
 * then the apps the user actually opens (usage-ranked habits plus pinned
 * favourites). Everything else is one query away, and the field says how many
 * apps are searchable. Typing switches the whole grid over to matches; pinned
 * apps are never filtered out of the results.
 *
 * The grid is the only scrollable on the page — headers are full-span items, so
 * there is no lazy grid nested inside another scroll container and no double
 * gesture handling. The search field is a plain text field: it takes focus only
 * when tapped, so opening the island never throws the keyboard up at the user.
 */
@Composable
fun AppsPage(
    apps: List<AppEntry>,
    query: String,
    pinned: Set<String>,
    habitPackages: List<String>,
    prediction: Prediction,
    loading: Boolean,
    iconFor: (String) -> ImageBitmap?,
    onQueryChanged: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppRef) -> Unit,
    onPredictedOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Derived once per app list, not per keystroke: refs back the search, and the
    // component map turns a result back into an entry without another O(n) scan.
    val refs = remember(apps) { apps.map { it.toRef() } }
    val entriesByComponent = remember(apps) {
        apps.associateBy { it.component.flattenToString() }
    }
    val byPackage = remember(apps) {
        val map = HashMap<String, AppEntry>(apps.size)
        for (app in apps) if (!map.containsKey(app.packageName)) map[app.packageName] = app
        map
    }
    val searchResult = remember(refs, query) { AppSearch.search(refs, query) }
    val isSearching = query.isNotBlank()
    // Every match, no cap: searching is a lookup, not a recommendation list.
    val matches: List<AppEntry> = remember(searchResult, entriesByComponent, isSearching) {
        if (!isSearching) {
            emptyList()
        } else {
            searchResult.apps.mapNotNull { entriesByComponent[it.component] }
        }
    }

    // Habits in usage-rank order, then pinned favourites the ranking missed.
    val habitApps = remember(apps, pinned, habitPackages, byPackage) {
        val seen = HashSet<String>()
        val ranked = habitPackages.mapNotNull { pkg ->
            byPackage[pkg]?.takeIf { seen.add(it.packageName) }
        }
        val pinnedApps = apps.filter { it.packageName in pinned && seen.add(it.packageName) }
        ranked + pinnedApps
    }

    Column(modifier.fillMaxSize()) {
        // --- search bar ----------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NbDim.ContentInset, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                // Tonal inset, not a border: the panel is solid, so the field needs
                // its own shade to read as an input at all.
                .background(
                    if (colors.isDark) {
                        Color.White.copy(alpha = 0.065f)
                    } else {
                        Color.Black.copy(alpha = 0.045f)
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconGlyph(Glyph.SEARCH, colors.onSurfaceDim, Modifier.size(16.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    NbText(
                        if (apps.isEmpty()) {
                            "Search apps"
                        } else {
                            "Search ${apps.size} apps"
                        },
                        style = NbType.body,
                        color = colors.onSurfaceDim,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Search installed apps" },
                    textStyle = NbType.body.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            // Enter launches the best match — the reason people type.
                            matches.firstOrNull()?.let(onLaunch)
                        }
                    )
                )
            }
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 16.dp)
        ) {
            when {
                loading && apps.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "Reading installed apps…",
                        description = "The launcher asks Android for launchable activities. Nothing leaves the phone.",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 24.dp)
                    )
                }

                isSearching && matches.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    val noApps = searchResult.reason == SearchResult.EmptyReason.NO_APPS
                    EmptyState(
                        title = if (noApps) "No launchable apps" else "No matches",
                        description = if (noApps) {
                            "This device reports no apps with a launcher entry."
                        } else {
                            "Nothing on this device matches \"$query\"."
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 24.dp)
                    )
                }

                isSearching -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "results-label") {
                        SectionLabel(
                            if (matches.size == 1) "1 result" else "${matches.size} results",
                            Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                    items(matches, key = { it.component.flattenToString() }, contentType = { "app" }) { app ->
                        AppCell(
                            modifier = Modifier.animateItem(),
                            app = app,
                            pinned = app.packageName in pinned,
                            iconFor = iconFor,
                            onLaunch = { onLaunch(app) },
                            onTogglePin = { onTogglePin(app.toRef()) }
                        )
                    }
                }

                else -> {
                    if (prediction.hasPrediction) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "prediction") {
                            PredictionRow(
                                prediction = prediction,
                                iconFor = iconFor,
                                onOpen = onPredictedOpen,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                    }

                    if (habitApps.isEmpty()) {
                        // No usage history yet: an empty page would be a dead end,
                        // so fall back to the honest full list until habits exist.
                        item(span = { GridItemSpan(maxLineSpan) }, key = "all-label") {
                            SectionLabel(
                                "All apps",
                                Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }
                        items(apps, key = { it.component.flattenToString() }, contentType = { "app" }) { app ->
                            AppCell(
                                modifier = Modifier.animateItem(),
                                app = app,
                                pinned = app.packageName in pinned,
                                iconFor = iconFor,
                                onLaunch = { onLaunch(app) },
                                onTogglePin = { onTogglePin(app.toRef()) }
                            )
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "habit-label") {
                            SectionLabel(
                                "Habit apps",
                                Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }
                        items(habitApps, key = { it.component.flattenToString() }, contentType = { "app" }) { app ->
                            AppCell(
                                modifier = Modifier.animateItem(),
                                app = app,
                                pinned = app.packageName in pinned,
                                iconFor = iconFor,
                                onLaunch = { onLaunch(app) },
                                onTogglePin = { onTogglePin(app.toRef()) }
                            )
                        }
                        if (apps.size > habitApps.size) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search-hint") {
                                NbText(
                                    "Search to see the other ${apps.size - habitApps.size} apps.",
                                    style = NbType.bodySmall,
                                    color = colors.onSurfaceDim,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCell(
    app: AppEntry,
    pinned: Boolean,
    iconFor: (String) -> ImageBitmap?,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    Column(
        modifier = modifier
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
            .background(colors.surfaceSolid)
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
