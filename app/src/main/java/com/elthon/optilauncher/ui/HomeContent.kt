package com.elthon.optilauncher.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elthon.optilauncher.core.island.IslandEvent
import com.elthon.optilauncher.core.island.IslandPage
import com.elthon.optilauncher.ui.components.Chip
import com.elthon.optilauncher.ui.components.GlanceWidget
import com.elthon.optilauncher.ui.components.Glyph
import com.elthon.optilauncher.ui.island.FloatingIsland
import com.elthon.optilauncher.ui.island.pages.AppsPage
import com.elthon.optilauncher.ui.island.pages.HabitsPage
import com.elthon.optilauncher.ui.island.pages.SettingsPage
import com.elthon.optilauncher.ui.island.pages.WallpaperPage
import com.elthon.optilauncher.ui.onboarding.Onboarding
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbDim
import com.elthon.optilauncher.ui.theme.NoBlooksTheme

/**
 * The home screen, and nothing else: the glance instrument at the top-right, the
 * floating island in the middle of the left edge, and the first-run overlay until
 * the user has acknowledged it.
 *
 * The island is a portrait capsule because it lives on a side edge; when it opens
 * it lands in a fixed slot sized from the instrument's measured height, so the
 * expanded panel always starts just under (or, on wide screens, level with) the
 * instrument instead of wherever the content happened to end. Both share one
 * inset and one corner rhythm. There is no title bar, no drawer icon row, no
 * second activity. The wallpaper is the interface.
 */
@Composable
fun HomeContent(
    viewModel: LauncherViewModel,
    systemReducedMotion: Boolean
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    NoBlooksTheme(ui.theme) {
        val colors = LocalNbColors.current
        val context = LocalContext.current
        val micPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            viewModel.setMicPermissionResult(granted)
            if (granted) viewModel.startMicCheck(hasPermission = true)
        }
        val reducedMotion = systemReducedMotion || ui.settings.reducedMotion
        // Measured, not assumed: the panel's fixed top anchor is derived from the
        // instrument's real height, so font-scale changes cannot cause a collision.
        var instrumentHeightPx by remember { mutableIntStateOf(0) }
        // With the status bar hidden, statusBars is 0 — but a camera cutout still
        // needs clearance, so the widget hugs whichever is taller.
        val topInsets = WindowInsets.statusBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Top)

        Box(Modifier.fillMaxSize()) {
            // --- instrument widget over the wallpaper ----------------------
            // Top-right, so the island can own the true middle of the left edge
            // without the two ever colliding, in either orientation.
            GlanceWidget(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(topInsets)
                    .padding(end = NbDim.ScreenInset, top = NbDim.WidgetTopInset)
                    .onSizeChanged { instrumentHeightPx = it.height },
                onClick = {
                    viewModel.onEvent(IslandEvent.SelectPage(IslandPage.APPS))
                    viewModel.onEvent(IslandEvent.Expand)
                }
            )

            // --- island ----------------------------------------------------
            FloatingIsland(
                state = ui.island,
                mic = ui.mic,
                reducedMotion = reducedMotion,
                message = ui.message,
                instrumentHeightPx = instrumentHeightPx,
                onEvent = viewModel::onEvent,
                onMicAction = {
                    viewModel.onEvent(IslandEvent.SelectPage(IslandPage.SETTINGS))
                    viewModel.onEvent(IslandEvent.Expand)
                },
                suggestion = {
                    val prediction = ui.habitSummary?.prediction
                    if (
                        ui.settings.suggestionsEnabled &&
                        !ui.island.suggestionDismissed &&
                        prediction != null &&
                        prediction.hasPrediction
                    ) {
                        val label = ui.apps
                            .firstOrNull { it.packageName == prediction.packageName }
                            ?.label ?: prediction.packageName.orEmpty()
                        Chip(
                            text = "$label · ${prediction.message}",
                            glyph = Glyph.OPEN,
                            accent = true
                        )
                    }
                },
                pageContent = { page ->
                    // Derived here so the list identity is stable across unrelated
                    // recompositions; otherwise AppsPage re-derives its habit grid
                    // every time the mic level or a message tick changes.
                    val habitPackages = remember(ui.habitSummary) {
                        ui.habitSummary?.topApps?.map { it.packageName }.orEmpty()
                    }
                    when (page) {
                        IslandPage.APPS -> AppsPage(
                            apps = ui.apps,
                            query = ui.island.query,
                            pinned = ui.settings.favorites,
                            habitPackages = habitPackages,
                            prediction = ui.habitSummary?.prediction
                                ?: com.elthon.optilauncher.core.habits.Prediction.noneAvailable(""),
                            loading = ui.appsLoading,
                            iconFor = { pkg -> ui.icons[pkg] },
                            onQueryChanged = viewModel::onQueryChanged,
                            onLaunch = viewModel::launchApp,
                            onTogglePin = viewModel::togglePin,
                            onAppInfo = { viewModel.openAppDetails(it.packageName) },
                            onUninstall = viewModel::uninstallApp,
                            onPredictedOpen = viewModel::launchPrediction
                        )

                        IslandPage.HABITS -> HabitsPage(
                            summary = ui.habitSummary,
                            usageAccess = ui.usageAccess,
                            loading = ui.habitLoading,
                            hasLocalHistory = ui.habitSummary?.recentLaunches?.isNotEmpty() == true,
                            iconFor = { pkg -> ui.icons[pkg] },
                            labelFor = { pkg -> ui.apps.firstOrNull { it.packageName == pkg }?.label },
                            onGrantUsageAccess = viewModel::openUsageAccessSettings,
                            onAppDetails = viewModel::openAppDetails
                        )

                        IslandPage.WALLPAPER -> WallpaperPage(
                            theme = ui.theme,
                            onOpenWallpaperPicker = viewModel::openWallpaperPicker,
                            onReDeriveTheme = viewModel::reDeriveTheme
                        )

                        IslandPage.SETTINGS -> SettingsPage(
                            themeMode = ui.settings.themeMode,
                            iconTreatment = ui.settings.iconTreatment,
                            reducedMotion = ui.settings.reducedMotion,
                            suggestionsEnabled = ui.settings.suggestionsEnabled,
                            usageAccess = ui.usageAccess,
                            micPermissionLabel = ui.mic.detail ?: ui.mic.state.name.lowercase(),
                            isDefaultHome = ui.isDefaultHome,
                            homeRoleNote = ui.homeRoleNote,
                            pinnedCount = ui.settings.favorites.size,
                            appsCount = ui.apps.size,
                            historyDays = ui.habitSummary?.historyDays ?: 0,
                            onThemeMode = viewModel::setThemeMode,
                            onIconTreatment = viewModel::setIconTreatment,
                            onReducedMotion = viewModel::setReducedMotion,
                            onSuggestions = viewModel::setSuggestionsEnabled,
                            onGrantUsageAccess = viewModel::openUsageAccessSettings,
                            onMicPermissionAction = {
                                when {
                                    ui.micTestRunning -> viewModel.stopMicCheck()
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED ->
                                        viewModel.startMicCheck(hasPermission = true)

                                    else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onOpenHomeSettings = viewModel::openHomeSettings,
                            onAppDetails = { viewModel.openAppDetails() },
                            onClearHistory = viewModel::clearHabitHistory,
                            onClearFavorites = viewModel::clearFavorites
                        )
                    }
                }
            )

            // --- first-run overlay ----------------------------------------
            if (!ui.onboardingComplete) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.scrim),
                    contentAlignment = Alignment.Center
                ) {
                    Onboarding(
                        isDefaultHome = ui.isDefaultHome,
                        onSetHome = viewModel::openHomeSettings,
                        onGrantUsageAccess = viewModel::openUsageAccessSettings,
                        onFinish = { viewModel.onEvent(IslandEvent.EnterHome) }
                    )
                }
            }
        }
    }
}
