package com.elthon.optilauncher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.elthon.optilauncher.core.apps.AppRef
import com.elthon.optilauncher.core.habits.HabitEngine
import com.elthon.optilauncher.core.habits.HabitInput
import com.elthon.optilauncher.core.habits.HabitSummary
import com.elthon.optilauncher.core.island.IslandEvent
import com.elthon.optilauncher.core.island.IslandPage
import com.elthon.optilauncher.core.island.IslandState
import com.elthon.optilauncher.core.island.reduce
import com.elthon.optilauncher.core.launch.LaunchOutcome
import com.elthon.optilauncher.core.launch.LaunchTarget
import com.elthon.optilauncher.core.launch.WindowMode
import com.elthon.optilauncher.core.mic.MicIndicator
import com.elthon.optilauncher.core.mic.MicPermission
import com.elthon.optilauncher.core.mic.MicState
import com.elthon.optilauncher.core.settings.IconTreatment
import com.elthon.optilauncher.core.settings.SettingsRepository
import com.elthon.optilauncher.core.settings.SettingsState
import com.elthon.optilauncher.core.theme.LauncherTheme
import com.elthon.optilauncher.core.theme.ThemeMode
import com.elthon.optilauncher.data.AppEntry
import com.elthon.optilauncher.data.AppLauncher
import com.elthon.optilauncher.data.AppRepository
import com.elthon.optilauncher.data.IconRenderer
import com.elthon.optilauncher.data.LaunchRecorder
import com.elthon.optilauncher.data.MicLevelMeter
import com.elthon.optilauncher.data.PermissionStatus
import com.elthon.optilauncher.data.SharedPreferencesStore
import com.elthon.optilauncher.data.SystemUsageRepository
import com.elthon.optilauncher.data.UsageAccess
import com.elthon.optilauncher.data.WallpaperThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the launcher UI renders, in one immutable snapshot.
 *
 * Deliberately one object: the island, the theme and the habit data change from
 * different sources (wallpaper listener, package broadcasts, usage stats), and a
 * single snapshot keeps the UI consistent instead of letting them disagree.
 *
 * Told to Compose explicitly: every field is a snapshot value or an immutable
 * collection, so composables that receive it can skip instead of re-running.
 */
@Immutable
data class LauncherUiState(
    val island: IslandState = IslandState(),
    val theme: LauncherTheme = LauncherTheme.fallback(),
    val settings: SettingsState = SettingsState(),
    val apps: List<AppEntry> = emptyList(),
    val appsLoading: Boolean = true,
    val icons: Map<String, ImageBitmap> = emptyMap(),
    val habitSummary: HabitSummary? = null,
    val habitLoading: Boolean = false,
    val usageAccess: UsageAccess = UsageAccess.UNKNOWN,
    val mic: MicIndicator = MicIndicator.idle(MicPermission.NOT_REQUESTED),
    val micTestRunning: Boolean = false,
    val micLevel: Float = 0f,
    val message: String? = null,
    val isDefaultHome: Boolean = false,
    val homeRoleNote: String = "Checking home role…",
    val onboardingComplete: Boolean = false
)

/**
 * State holder for the whole launcher.
 *
 * Every side-effecting capability is a repository the ViewModel owns, so the
 * Compose layer stays declarative and the behavior stays testable in the core
 * layer (island transitions, habit scoring, settings codec all have unit tests).
 */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(SharedPreferencesStore(app))
    private val appRepo = AppRepository(app)
    private val usageRepo = SystemUsageRepository(app)
    private val recorder = LaunchRecorder(SharedPreferencesStore(app))
    private val appLauncher = AppLauncher(app)
    private val wallpaperController = WallpaperThemeController(app)
    private val iconRenderer = IconRenderer(app)
    private val micMeter = MicLevelMeter()

    private val _ui = kotlinx.coroutines.flow.MutableStateFlow(LauncherUiState())
    val ui: kotlinx.coroutines.flow.StateFlow<LauncherUiState> = _ui

    private var messageJob: Job? = null
    private var iconsJob: Job? = null
    private var habitJob: Job? = null

    init {
        val settings = settingsRepo.load()
        update {
            it.copy(
                settings = settings,
                onboardingComplete = settings.onboardingComplete,
                island = it.island.copy(page = settings.lastPage)
            )
        }

        // Wallpaper-derived theme, live.
        wallpaperController.setMode(settings.themeMode)
        wallpaperController.start()
        viewModelScope.launch {
            wallpaperController.theme.collect { theme ->
                update { it.copy(theme = theme) }
                refreshIcons()
            }
        }

        // Mic indicator: never claims capture unless it is real.
        update { it.copy(mic = PermissionStatus.micIndicator(getApplication())) }
        viewModelScope.launch {
            micMeter.state.collect { state ->
                if (state != null) {
                    update { it.copy(mic = state, micTestRunning = state.state == MicState.RECORDING) }
                }
            }
        }
        viewModelScope.launch {
            micMeter.level.collect { level -> update { it.copy(micLevel = level) } }
        }

        refreshApps()
        appRepo.startWatching { refreshApps(force = true) }
        refreshHomeRole()
        // Habit stats are a second, slower source (UsageStatsManager over a week via
        // binder). Two things must be true before they are worth computing: the app
        // list must be known (so the engine can restrict predictions to real apps
        // and never leak a package name), and it must not contend with the first
        // search. So instead of a fixed delay, wait for the app list — then give the
        // first frames a beat.
        viewModelScope.launch {
            // Poll cheaply for the cached list rather than reading state each frame;
            // it lands within a frame or two of launch on any real device.
            repeat(60) {
                if (!appRepo.cached().isNullOrEmpty()) return@launch
                delay(16)
            }
        }.invokeOnCompletion {
            viewModelScope.launch {
                delay(200)
                refreshHabits()
            }
        }
    }

    override fun onCleared() {
        appRepo.stopWatching()
        wallpaperController.stop()
        wallpaperController.destroy()
        micMeter.destroy()
        super.onCleared()
    }

    // ---------------------------------------------------------------- island

    fun onEvent(event: IslandEvent) {
        val previous = _ui.value.island
        val next = previous.reduce(event)
        if (next == previous && event !is IslandEvent.SelectPage) return

        update { it.copy(island = next) }

        if (next.page != previous.page) {
            settingsRepo.update { it.copy(lastPage = next.page) }
            update { it.copy(settings = settingsRepo.load()) }
            if (next.page == IslandPage.HABITS) refreshHabits()
        }
        if (event is IslandEvent.EnterHome) {
            settingsRepo.update { it.copy(onboardingComplete = true) }
            update { it.copy(settings = settingsRepo.load(), onboardingComplete = true) }
            refreshHomeRole()
        }
    }

    fun onBack(): Boolean {
        val island = _ui.value.island
        return if (island.isExpanded) {
            onEvent(IslandEvent.Back)
            true
        } else {
            false
        }
    }

    // ----------------------------------------------------------------- apps

    fun refreshApps(force: Boolean = false) {
        viewModelScope.launch {
            update { it.copy(appsLoading = it.apps.isEmpty()) }
            val apps = appRepo.apps(forceRefresh = force)
            update { it.copy(apps = apps, appsLoading = false) }
            refreshIcons()
        }
    }

    /**
     * Rasterizes app icons, but *progressively and in priority order*.
     *
     * The old version rendered every installed app's icon in one background pass
     * and then committed the whole map at once. On a device with a few hundred
     * apps that is hundreds of `getApplicationIcon` binder calls and hundreds of
     * 144px bitmap renders in flight while the user is opening the island and
     * typing a search — which is exactly the "everything feels a frame behind"
     * complaint. Instead: warm the apps the user is most likely to see first
     * (habits, then pinned, then declaration order), commit a partial map as soon
     * as it exists, and keep filling the rest in the background. A missing icon is
     * simply drawn as the monochrome placeholder [AppIcon] already provides.
     */
    private fun refreshIcons() {
        iconsJob?.cancel()
        val snapshot = _ui.value
        val apps = snapshot.apps
        if (apps.isEmpty()) return
        val treatment = snapshot.settings.iconTreatment
        val tint = if (snapshot.theme.isDark) DARK_TINT else LIGHT_TINT

        val byPackage = HashMap<String, AppEntry>(apps.size)
        for (app in apps) byPackage.putIfAbsent(app.packageName, app)
        val habitOrder = snapshot.habitSummary?.topApps?.map { it.packageName }.orEmpty()
        val pinned = snapshot.settings.favorites
        val order = LinkedHashMap<String, AppEntry>(apps.size)
        for (pkg in habitOrder) byPackage[pkg]?.let { order.putIfAbsent(pkg, it) }
        for (pkg in pinned) byPackage[pkg]?.let { order.putIfAbsent(pkg, it) }
        for (app in apps) order.putIfAbsent(app.packageName, app)

        iconsJob = viewModelScope.launch {
            val filled = HashMap<String, ImageBitmap>(_ui.value.icons)
            var sinceCommit = 0
            for (entry in order.values) {
                // Reuse anything already rendered for this treatment/tint.
                iconRenderer.cached(entry.packageName, treatment, tint)?.let {
                    filled[entry.packageName] = it
                    continue
                }
                val bitmap = withContext(Dispatchers.Default) {
                    iconRenderer.render(entry.packageName, treatment, tint)
                }
                if (bitmap != null) filled[entry.packageName] = bitmap
                // Commit every 12 icons rather than once at the end, so the first
                // grid the user sees is already populated instead of placeholder-
                // only until the long tail finishes.
                if (++sinceCommit >= 12) {
                    sinceCommit = 0
                    val committed = HashMap(filled)
                    update { it.copy(icons = committed) }
                }
            }
            val finalMap = HashMap(filled)
            update { it.copy(icons = finalMap) }
        }
    }

    fun launchApp(entry: AppEntry) {
        val outcome = appLauncher.launch(
            LaunchTarget(entry.packageName, entry.component.className, entry.resizable),
            WindowMode.NORMAL
        )
        val text = when (outcome) {
            is LaunchOutcome.Started -> {
                recorder.record(entry.packageName)
                "Opened ${entry.label}"
            }

            is LaunchOutcome.Unavailable -> "${entry.label} is no longer installed"
            is LaunchOutcome.Unsupported -> "Cannot open ${entry.label}: ${outcome.reason}"
            is LaunchOutcome.Blocked -> "Blocked: ${outcome.reason}"
            is LaunchOutcome.Failed -> "Couldn't open ${entry.label}"
        }
        showMessage(text)
        onEvent(IslandEvent.AppLaunched)
        refreshHabits(force = false)
    }

    fun launchPrediction() {
        val pkg = _ui.value.habitSummary?.prediction?.packageName ?: return
        val entry = _ui.value.apps.firstOrNull { it.packageName == pkg }
        if (entry != null) {
            launchApp(entry)
        } else {
            openAppDetails(pkg)
        }
    }

    fun togglePin(ref: AppRef) {
        val updated = settingsRepo.toggleFavorite(ref.packageName)
        update { it.copy(settings = updated) }
        showMessage(
            if (updated.isFavorite(ref.packageName)) "Pinned ${ref.label}" else "Unpinned ${ref.label}"
        )
    }

    fun onQueryChanged(query: String) = onEvent(IslandEvent.QueryChanged(query))

    // --------------------------------------------------------------- habits

    fun refreshHabits(force: Boolean = false) {
        habitJob?.cancel()
        habitJob = viewModelScope.launch {
            val access = usageRepo.access()
            update { it.copy(usageAccess = access, habitLoading = true) }

            val now = System.currentTimeMillis()
            val usage = usageRepo.load(now, forceRefresh = force)
            val local = recorder.history()
            val pinned = settingsRepo.load().favorites
            val known = appRepo.cached()?.map { it.packageName }?.toSet().orEmpty()

            val input = HabitInput(
                nowEpochMs = now,
                aggregates = usage.aggregates,
                localLaunches = local,
                hasUsageAccess = usage.access == UsageAccess.GRANTED,
                knownPackages = known,
                pinnedPackages = pinned
            )

            val weekMs = usage.aggregates.sumOf { it.totalDurationMs }
            // "Today" is estimated from each app's launch-hour histogram applied to
            // its total foreground time — an estimate, and labeled as such in the UI.
            val nowHour = HabitEngine.hourOf(now)
            val todayMs = usage.aggregates.sumOf { aggregate ->
                if (aggregate.launchCount <= 0) return@sumOf 0L
                val share = aggregate.hourHistogram.getOrElse(nowHour) { 0 } / aggregate.launchCount.toFloat()
                (aggregate.totalDurationMs * share).toLong()
            }

            val summary = HabitEngine.summary(
                input = input,
                todayMs = todayMs,
                weekMs = weekMs,
                historyDays = usage.historyDays
            )
            update { it.copy(habitSummary = summary, habitLoading = false) }
        }
    }

    fun clearHabitHistory() {
        recorder.clear()
        update { it.copy(habitSummary = null) }
        showMessage("Habit history cleared")
        refreshHabits(force = true)
    }

    fun clearFavorites() {
        val updated = settingsRepo.clearFavorites()
        update { it.copy(settings = updated) }
        showMessage("Pinned apps cleared")
    }

    // ------------------------------------------------------------- settings

    fun setThemeMode(mode: ThemeMode) {
        val updated = settingsRepo.update { it.copy(themeMode = mode) }
        update { it.copy(settings = updated) }
        wallpaperController.setMode(mode)
    }

    fun setIconTreatment(treatment: IconTreatment) {
        val updated = settingsRepo.update { it.copy(iconTreatment = treatment) }
        update { it.copy(settings = updated) }
        iconRenderer.invalidateTheme()
        refreshIcons()
    }

    fun setReducedMotion(reduced: Boolean) {
        val updated = settingsRepo.update { it.copy(reducedMotion = reduced) }
        update { it.copy(settings = updated) }
    }

    fun setSuggestionsEnabled(enabled: Boolean) {
        val updated = settingsRepo.update { it.copy(suggestionsEnabled = enabled) }
        update { it.copy(settings = updated) }
    }

    // ---------------------------------------------------------- permissions

    fun openUsageAccessSettings() {
        val opened = appLauncher.openUsageAccessSettings()
        showMessage(
            if (opened) "Grant Usage access for NoBlooks, then come back" else "No usage-settings screen on this device"
        )
    }

    /** Re-probes access; called when the user returns from the system screen. */
    fun recheckUsageAccess() {
        update { it.copy(usageAccess = usageRepo.access()) }
        refreshHabits(force = true)
    }

    fun openWallpaperPicker() {
        val opened = appLauncher.openWallpaperPicker()
        showMessage(if (opened) "Wallpaper picker opened" else "No wallpaper picker available")
        if (opened) wallpaperController.refresh("picker-opened")
    }

    fun reDeriveTheme() {
        wallpaperController.refresh("manual")
        showMessage("Re-derived theme from wallpaper")
    }

    fun openHomeSettings() {
        appLauncher.openHomeSettings()
        showMessage("Pick NoBlooks as your home app")
    }

    fun openAppDetails(packageName: String = getApplication<Application>().packageName) {
        val opened = appLauncher.openAppDetails(packageName)
        if (!opened) showMessage("No app-details screen available")
    }

    fun uninstallApp(entry: AppEntry) {
        val opened = appLauncher.uninstall(entry.packageName)
        showMessage(
            if (opened) "Uninstalling ${entry.label}…" else "No uninstaller available"
        )
    }

    fun refreshHomeRole() {
        val state = PermissionStatus.homeRoleState(getApplication())
        val note = when {
            state.hasError -> "Home-role probe unavailable on this device"
            state.roleHeld == true -> "NoBlooks holds the home role."
            state.roleAvailable == true -> "Tap to make NoBlooks your home app."
            state.roleAvailable == false -> "No app currently holds the home role."
            else -> "Home role unknown on this device."
        }
        update { it.copy(isDefaultHome = state.roleHeld == true, homeRoleNote = note) }
    }

    // ------------------------------------------------------------ microphone

    /**
     * Starts the mic level check. [hasPermission] must reflect the *result* of a
     * runtime request, so "recording" is never shown when audio is not captured.
     */
    fun startMicCheck(hasPermission: Boolean) {
        val started = micMeter.start(hasPermission)
        if (!started) {
            update { it.copy(mic = MicIndicator.idle(MicPermission.DENIED)) }
            showMessage("Microphone permission is required for the level check")
        } else {
            showMessage("Listening — tap the mic again to stop")
        }
    }

    fun stopMicCheck() {
        micMeter.stop()
        update { it.copy(mic = PermissionStatus.micIndicator(getApplication()), micLevel = 0f) }
    }

    fun setMicPermissionResult(granted: Boolean) {
        update {
            it.copy(
                mic = MicIndicator.idle(
                    if (granted) MicPermission.GRANTED else MicPermission.DENIED
                )
            )
        }
    }

    // -------------------------------------------------------------- messages

    fun showMessage(text: String) {
        messageJob?.cancel()
        update { it.copy(message = text) }
        messageJob = viewModelScope.launch {
            delay(MESSAGE_MILLIS)
            update { it.copy(message = null) }
        }
    }

    fun dismissMessage() {
        messageJob?.cancel()
        update { it.copy(message = null) }
    }

    private inline fun update(transform: (LauncherUiState) -> LauncherUiState) {
        _ui.value = transform(_ui.value)
    }

    companion object {
        private const val MESSAGE_MILLIS = 2600L
        private val DARK_TINT = 0xFFEDEDEF.toInt()
        private val LIGHT_TINT = 0xFF16171A.toInt()
    }
}
