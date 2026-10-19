package com.elthon.optilauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elthon.optilauncher.core.island.IslandEvent
import com.elthon.optilauncher.core.island.IslandPage
import com.elthon.optilauncher.ui.components.Chip
import com.elthon.optilauncher.ui.components.Glyph
import com.elthon.optilauncher.ui.components.NbText
import com.elthon.optilauncher.ui.island.FloatingIsland
import com.elthon.optilauncher.ui.island.pages.AppsPage
import com.elthon.optilauncher.ui.island.pages.HabitsPage
import com.elthon.optilauncher.ui.island.pages.SettingsPage
import com.elthon.optilauncher.ui.island.pages.WallpaperPage
import com.elthon.optilauncher.ui.onboarding.Onboarding
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbType
import com.elthon.optilauncher.ui.theme.NoBlooksTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The home screen: a live clock over the wallpaper, the floating island at the
 * bottom, and the first-run overlay until the user has acknowledged it.
 *
 * There is intentionally nothing else — no drawer icon row, no widgets, no
 * dependency on a second activity. The wallpaper is the interface.
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
        val onWallpaper = Color(ui.theme.onWallpaper())

        Box(Modifier.fillMaxSize()) {
            // --- clock over the wallpaper ---------------------------------
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 34.dp)
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val now by rememberMinuteTick()
                val time = remember(now) {
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
                }
                val date = remember(now) {
                    LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
                        .uppercase(Locale.getDefault())
                }
                NbText(
                    time,
                    style = NbType.clock,
                    color = onWallpaper,
                    maxLines = 1,
                    contentDescription = "Current time"
                )
                NbText(
                    date,
                    style = NbType.clockLabel,
                    color = onWallpaper.copy(alpha = 0.72f),
                    maxLines = 1,
                    contentDescription = "Current date"
                )
            }

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
                            accent = true,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                },
                pageContent = { page ->
                    when (page) {
                        IslandPage.APPS -> AppsPage(
                            apps = ui.apps,
                            query = ui.island.query,
                            searchFocused = ui.island.searchFocused,
                            pinned = ui.settings.favorites,
                            prediction = ui.habitSummary?.prediction
                                ?: com.elthon.optilauncher.core.habits.Prediction.noneAvailable(""),
                            loading = ui.appsLoading,
                            iconFor = { pkg -> ui.icons[pkg] },
                            onQueryChanged = viewModel::onQueryChanged,
                            onLaunch = viewModel::launchApp,
                            onTogglePin = viewModel::togglePin,
                            onDismissFocus = { viewModel.onEvent(IslandEvent.SearchFocusLost) },
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
                        .background(colors.scrim.copy(alpha = 0.72f))
                        .clip(RoundedCornerShape(0.dp)),
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

/** Ticks once a minute (plus a second at the start) — cheap and sufficient. */
@Composable
private fun rememberMinuteTick(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(15_000L)
    }
}
