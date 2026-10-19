package com.elthon.optilauncher.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elthon.optilauncher.core.island.IslandEvent
import com.elthon.optilauncher.core.island.IslandPage
import com.elthon.optilauncher.ui.components.Chip
import com.elthon.optilauncher.ui.components.ClockWidget
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
 * The home screen, and nothing else: a live clock widget at the top-left, the
 * floating island at the bottom-left, and the first-run overlay until the user
 * has acknowledged it.
 *
 * Both surfaces share one inset, so the composition reads as a deliberate
 * left-aligned column over the wallpaper. There is no title bar, no drawer icon
 * row, no second activity. The wallpaper is the interface.
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

        Box(Modifier.fillMaxSize()) {
            // --- clock widget over the wallpaper ---------------------------
            ClockWidget(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = NbDim.ScreenInset, top = 18.dp),
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
                    when (page) {
                        IslandPage.APPS -> AppsPage(
                            apps = ui.apps,
                            query = ui.island.query,
                            pinned = ui.settings.favorites,
                            prediction = ui.habitSummary?.prediction
                                ?: com.elthon.optilauncher.core.habits.Prediction.noneAvailable(""),
                            loading = ui.appsLoading,
                            iconFor = { pkg -> ui.icons[pkg] },
                            onQueryChanged = viewModel::onQueryChanged,
                            onLaunch = viewModel::launchApp,
                            onTogglePin = viewModel::togglePin,
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
