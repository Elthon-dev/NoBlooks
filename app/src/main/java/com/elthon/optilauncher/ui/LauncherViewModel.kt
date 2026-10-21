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
import com.elthon.optilauncher.core.panel.PanelEvent
import com.elthon.optilauncher.core.panel.PanelFace
import com.elthon.optilauncher.core.panel.PanelNotification
import com.elthon.optilauncher.core.panel.PanelState
import com.elthon.optilauncher.core.panel.RecentApp
import com.elthon.optilauncher.core.panel.StopVerdict
import com.elthon.optilauncher.core.panel.reduce
import com.elthon.optilauncher.core.settings.IconTreatment
import com.elthon.optilauncher.core.settings.SettingsRepository
import com.elthon.optilauncher.core.settings.SettingsState
import com.elthon.optilauncher.core.theme.LauncherTheme
import com.elthon.optilauncher.core.theme.ThemeMode
import com.elthon.optilauncher.core.util.OemBackgroundPolicy
import com.elthon.optilauncher.data.AppEntry
import com.elthon.optilauncher.data.AppLauncher
import com.elthon.optilauncher.data.AppRepository
import com.elthon.optilauncher.data.IconRenderer
import com.elthon.optilauncher.data.LaunchRecorder
import com.elthon.optilauncher.data.MicLevelMeter
import com.elthon.optilauncher.data.MultitaskLauncher
import com.elthon.optilauncher.data.NotificationAccess
import com.elthon.optilauncher.data.NotificationBridge
import com.elthon.optilauncher.data.PanelController
import com.elthon.optilauncher.data.PanelSignals
import com.elthon.optilauncher.data.PermissionStatus
import com.elthon.optilauncher.data.RecentsRepository
import com.elthon.optilauncher.data.SharedPreferencesStore
import com.elthon.optilauncher.data.SystemActions
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
    val onboardingComplete: Boolean = false,
    /**
     * The OEM background-killing policy for this device. Surfaced so the Settings
     * page can offer the one action that actually helps the ColorOS-style "blank
     * wallpaper on return to Home" delay. Never claims whether the app is exempt.
     */
    val oemPolicy: OemBackgroundPolicy = OemBackgroundPolicy.NONE,
    /** The edge control panel's state (visibility, face, arming). */
    val panel: PanelState = PanelState(),
    /** The panel's best-effort app switcher, newest first. */
    val panelRecents: List<RecentApp> = emptyList(),
    /** Notifications the user might have missed, newest first. */
    val panelNotifications: List<PanelNotification> = emptyList(),
    /** True while the notification listener is connected and can read the shade. */
    val notificationAccess: Boolean = false,
    /** The actual special-access grant, even before the listener has rebound. */
    val notificationAccessGranted: Boolean = false,
    /** Wall-clock time of the last listener callback this process has seen; 0 = none. */
    val notificationLastEventAtMs: Long = 0L,
    /**
     * True while "Display over other apps" is granted. This is only the permission
     * bit; the ROM can still refuse the window, so the UI never equates this with
     * "the overlay is showing".
     */
    val overlayGranted: Boolean = false,
    /** True while the accessibility service is enabled (the reliable reveal path). */
    val accessibilityEnabled: Boolean = false
) {
    /**
     * The habit-ranked apps as panel rows. Derived rather than stored so it can
     * never drift from `habitSummary`/`apps`; labels come from the installed-app
     * list and an unknown package degrades to a blank label ("Unknown app" in UI),
     * never a raw package id.
     */
    val panelHabits: List<RecentApp>
        get() {
            val top = habitSummary?.topApps ?: return emptyList()
            return top.map { scored ->
                val label = apps.firstOrNull { it.packageName == scored.packageName }?.label.orEmpty()
                RecentApp(
                    packageName = scored.packageName,
                    label = label,
                    lastUsedMs = scored.lastUsedEpochMs,
                    liveProcess = false
                )
            }
        }
}

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
    private val recentsRepo = RecentsRepository(app)
    private val panelController = PanelController(app)
    private val systemActions = SystemActions(app)
    private val multitaskLauncher = MultitaskLauncher(app)

    private val _ui = kotlinx.coroutines.flow.MutableStateFlow(LauncherUiState())
    val ui: kotlinx.coroutines.flow.StateFlow<LauncherUiState> = _ui

    private var messageJob: Job? = null
    private var iconsJob: Job? = null
    private var habitJob: Job? = null
    private var appsRefreshJob: Job? = null
    private var lastReturnHandledAtMs = 0L
    private var lastAutoRebindAtMs = 0L

    init {
        val settings = settingsRepo.load()
        update {
            it.copy(
                settings = settings,
                onboardingComplete = settings.onboardingComplete,
                island = it.island.copy(page = settings.lastPage),
                oemPolicy = OemBackgroundPolicy.classify(
                    android.os.Build.MANUFACTURER,
                    android.os.Build.BRAND
                )
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
        // Package add/remove/change bursts (an install emits several broadcasts) are
        // collapsed into one forced scan instead of one scan per broadcast. 600ms is
        // long enough for the burst to settle and short enough to feel immediate.
        appRepo.startWatching { scheduleAppsRefresh() }
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

        // The control panel listens to three external signals:
        //  - the accessibility service's three-finger tap (reveal requests),
        //  - the notification listener's live notification list,
        //  - the notification-access grant state (read on resume, not polled).
        viewModelScope.launch {
            PanelSignals.revealTick.collect {
                onPanelEvent(PanelEvent.ThreeFingerTap)
            }
        }
        viewModelScope.launch {
            NotificationBridge.notifications.collect { list ->
                update { it.copy(panelNotifications = list) }
            }
        }
        viewModelScope.launch {
            NotificationBridge.connected.collect { connected ->
                update { it.copy(notificationAccess = connected) }
            }
        }
        viewModelScope.launch {
            NotificationBridge.lastEventAtMs.collect { at ->
                update { it.copy(notificationLastEventAtMs = at) }
            }
        }
        refreshPanelArmed()
    }

    override fun onCleared() {
        appRepo.stopWatching()
        wallpaperController.stop()
        wallpaperController.destroy()
        micMeter.destroy()
        super.onCleared()
    }

    // --------------------------------------------------------------- panel

    /**
     * The single entry point for panel transitions, mirroring [onEvent].
     *
     * Actions that touch the system (Home / Recents / Notifications) are performed
     * here, then the state transition lands the panel hidden — so the panel is never
     * left open behind the thing it opened.
     */
    fun onPanelEvent(event: PanelEvent) {
        val previous = _ui.value.panel

        when (event) {
            PanelEvent.Home -> systemActions.home()

            PanelEvent.SystemRecents -> {
                // Prefer the real system overview; if this ROM won't show it, open
                // our own list instead of silently doing nothing.
                val opened = systemActions.recents()
                if (!opened) {
                    update { it.copy(panel = it.panel.copy(visible = true, face = PanelFace.RECENTS)) }
                    refreshPanelRecents()
                    return
                }
            }

            PanelEvent.SystemNotifications -> systemActions.notifications()

            is PanelEvent.ShowFace -> when (event.face) {
                PanelFace.RECENTS -> refreshPanelRecents()
                // The habit list is derived from `habitSummary`; a cached refresh
                // keeps it current without the cost of a forced usage scan.
                PanelFace.HABITS -> refreshHabits(force = false)
                else -> Unit
            }

            PanelEvent.LaunchMultitask -> {
                val snapshot = _ui.value
                if (snapshot.panel.selectionReady) {
                    val packages = snapshot.panel.selectedPackages
                    val mode = snapshot.panel.multitaskMode
                    // Hide first so the windows are not launched behind a scrim.
                    update { it.copy(panel = it.panel.reduce(event)) }
                    viewModelScope.launch {
                        val report = multitaskLauncher.launch(packages, mode)
                        showMessage(report.note)
                    }
                }
                return
            }

            PanelEvent.CollapseToPill -> Unit

            is PanelEvent.SetInteracting -> {
                val next = previous.reduce(event)
                if (next != previous) update { it.copy(panel = next) }
                return
            }

            PanelEvent.EnterHome -> {
                settingsRepo.update { it.copy(onboardingComplete = true) }
                update { it.copy(settings = settingsRepo.load(), onboardingComplete = true) }
                refreshHomeRole()
            }

            else -> Unit
        }

        val next = previous.reduce(event)
        if (next != previous) update { it.copy(panel = next) }
    }

    /** Re-reads whether the accessibility gesture is available. */
    fun refreshPanelArmed() {
        val app = getApplication<Application>()
        val armed = com.elthon.optilauncher.data.AccessibilityPanelService.isEnabled(app)
        val overlay = try {
            android.provider.Settings.canDrawOverlays(app)
        } catch (_: Throwable) {
            false
        }
        update {
            it.copy(
                panel = it.panel.reduce(PanelEvent.SetGestureArmed(armed)),
                accessibilityEnabled = armed,
                overlayGranted = overlay,
                notificationAccessGranted =
                    com.elthon.optilauncher.data.NotificationAccess.isGranted(app)
            )
        }
    }

    /**
     * Re-asks the system to bind the notification listener when the grant exists but
     * the listener is not connected.
     *
     * On ColorOS/MIUI a granted listener dies with the process (recents swipe, screen
     * off) and looks identical to "notifications are blocked". `requestRebind` is the
     * only supported reconnect call; it is throttled and never runs while connected.
     * The ROM can still ignore it — the Settings row says so rather than promising.
     */
    private fun maybeAutoRebindListener() {
        val app = getApplication<Application>()
        if (!NotificationAccess.isGranted(app)) return
        if (NotificationBridge.connected.value) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastAutoRebindAtMs < AUTO_REBIND_INTERVAL_MS) return
        lastAutoRebindAtMs = now
        NotificationAccess.rebind(app)
    }

    /** Explicit user action from Settings: re-bind without toggling the grant. */
    fun rebindNotificationListener() {
        val app = getApplication<Application>()
        if (!NotificationAccess.isGranted(app)) {
            update { it.copy(panel = it.panel.reduce(PanelEvent.Hide)) }
            appLauncher.openNotificationListenerSettings()
            showMessage("Grant notification access first, then return")
            return
        }
        lastAutoRebindAtMs = android.os.SystemClock.elapsedRealtime()
        val requested = NotificationAccess.rebind(app)
        showMessage(
            if (requested) "Rebind requested — if alerts stay silent, allow Auto-start in App info"
            else "This ROM refused the rebind request"
        )
    }

    private suspend fun loadPanelRecents() {
        val self = getApplication<Application>().packageName
        val live = panelController.liveRecentPackages()
        val list = recentsRepo.recents(selfPackage = self, livePackages = live)
        update { it.copy(panelRecents = list) }
    }

    fun refreshPanelRecents() {
        viewModelScope.launch { loadPanelRecents() }
    }

    fun openRecentApp(app: RecentApp) {
        update { it.copy(panel = it.panel.reduce(PanelEvent.Hide)) }
        if (!appLauncher.launchPackage(app.packageName)) {
            showMessage("Could not open ${app.label}")
        }
    }

    /**
     * Long-press / stop-button on a recents row. Attempts a Shizuku force-stop and
     * reports the *verdict* — never a bare success. Without Shizuku, it opens the
     * app's info page, which is one tap from the system's own Force stop.
     */
    fun stopRecentApp(app: RecentApp) {
        viewModelScope.launch {
            if (!com.elthon.optilauncher.data.ShizukuBridge.available()) {
                appLauncher.openAppDetails(app.packageName)
                update { it.copy(panel = it.panel.reduce(PanelEvent.SetStopVerdict(StopVerdict.UNAVAILABLE))) }
                return@launch
            }
            val verdict = panelController.forceStop(app.packageName)
            update { it.copy(panel = it.panel.reduce(PanelEvent.SetStopVerdict(verdict))) }
            loadPanelRecents()
        }
    }

    fun openPanelNotification(n: PanelNotification) {
        // Opening a notification means opening its app; we do not have its
        // contentIntent token, so the honest action is to launch the app.
        update { it.copy(panel = it.panel.reduce(PanelEvent.Hide)) }
        if (!appLauncher.launchPackage(n.packageName)) {
            showMessage("Could not open that app")
        }
    }

    fun dismissPanelNotification(n: PanelNotification) {
        panelController.dismissNotification(n.key)
    }

    fun replyPanelNotification(n: PanelNotification, text: String) {
        if (text.isBlank()) return
        val sent = panelController.reply(n.key, text)
        showMessage(if (sent) "Reply sent" else "This app can't be replied to here")
    }

    // --------------------------------------------------------------- island

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

    private fun scheduleAppsRefresh() {
        appsRefreshJob?.cancel()
        appsRefreshJob = viewModelScope.launch {
            delay(PACKAGE_REFRESH_DEBOUNCE_MS)
            refreshApps(force = true)
        }
    }

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
                val cached = iconRenderer.cached(entry.packageName, treatment, tint)
                val bitmap = if (cached != null) {
                    cached
                } else {
                    withContext(Dispatchers.Default) {
                        iconRenderer.render(entry.packageName, treatment, tint)
                    }
                }
                if (bitmap != null) filled[entry.packageName] = bitmap
                // Commit every 24 icons: often enough that the first screen is
                // populated, rare enough that the O(n) map copies do not pile up.
                if (++sinceCommit >= 24) {
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
            val access = withContext(Dispatchers.IO) { usageRepo.access() }
            update { it.copy(usageAccess = access, habitLoading = true) }

            val now = System.currentTimeMillis()
            val usage = usageRepo.load(now, forceRefresh = force)
            val (local, pinned) = withContext(Dispatchers.IO) {
                recorder.history() to settingsRepo.load().favorites
            }
            val known = appRepo.cached()?.map { it.packageName }?.toSet().orEmpty()

            val input = HabitInput(
                nowEpochMs = now,
                aggregates = usage.aggregates,
                localLaunches = local,
                hasUsageAccess = usage.access == UsageAccess.GRANTED,
                knownPackages = known,
                pinnedPackages = pinned
            )

            // Scoring plus the two local reads are CPU/prefs work; keep them off the
            // main thread so the island does not stutter when a refresh lands.
            val summary = withContext(Dispatchers.Default) {
                val weekMs = usage.aggregates.sumOf { it.totalDurationMs }
                // "Today" is estimated from each app's launch-hour histogram applied
                // to its total foreground time — an estimate, and labeled as such.
                val nowHour = HabitEngine.hourOf(now)
                val todayMs = usage.aggregates.sumOf { aggregate ->
                    if (aggregate.launchCount <= 0) return@sumOf 0L
                    val share = aggregate.hourHistogram.getOrElse(nowHour) { 0 } /
                        aggregate.launchCount.toFloat()
                    (aggregate.totalDurationMs * share).toLong()
                }
                HabitEngine.summary(
                    input = input,
                    todayMs = todayMs,
                    weekMs = weekMs,
                    historyDays = usage.historyDays
                )
            }
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

    fun setAllowLandscape(allow: Boolean) {
        val updated = settingsRepo.update { it.copy(allowLandscape = allow) }
        update { it.copy(settings = updated) }
    }

    // ---------------------------------------------------------- permissions

    fun openUsageAccessSettings() {
        val opened = appLauncher.openUsageAccessSettings()
        showMessage(
            if (opened) "Grant Usage access for NoBlooks, then come back" else "No usage-settings screen on this device"
        )
    }

    /**
     * Re-probes access; called when the user returns from the system screen.
     *
     * The expensive part of a habit refresh is `queryEvents` over a week of history,
     * so a full forced reload only happens when the *access* state actually changed
     * (in which case the cached data is meaningless). A plain resume, where nothing
     * changed, reuses the 90s cache instead of throwing it away — which is what made
     * every return-to-home re-scan the whole history on the way in.
     */
    fun recheckUsageAccess() {
        val before = _ui.value.usageAccess
        val access = usageRepo.access()
        update { it.copy(usageAccess = access) }
        if (access != before) {
            refreshHabits(force = true)
        } else {
            // Access unchanged: let the repository serve its cache if it is fresh.
            refreshHabits(force = false)
        }
    }

    /**
     * The cheap "came back to the home screen" path.
     *
     * Called from `onResume`, which fires on every return from an app, so it must not
     * do anything proportional to the device's usage history or app count. It re-reads
     * the two cheap system states and lets the repositories decide whether a refresh
     * is actually warranted — never a forced full scan.
     */
    fun onReturnedHome() {
        // onNewIntent and onResume both fire for the same Home press on a singleTask
        // activity; the probes are cheap but not free, so collapse the duplicate.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastReturnHandledAtMs < RETURN_DEBOUNCE_MS) return
        lastReturnHandledAtMs = now
        viewModelScope.launch {
            // Both probes are binder calls; keep them off the main thread so the
            // first frame after the transition is never waiting on system state.
            val access = withContext(Dispatchers.IO) { usageRepo.access() }
            val role = withContext(Dispatchers.IO) { PermissionStatus.homeRoleState(getApplication()) }
            update {
                it.copy(
                    usageAccess = access,
                    isDefaultHome = role.roleHeld == true,
                    homeRoleNote = homeRoleNote(role)
                )
            }
            // Only a stale cache is refreshed here; a fresh one is simply reused.
            refreshHabits(force = false)
        }
        // The user may have toggled notification access or the accessibility service
        // while away, so the panel's armed / grant state is re-read — both are cheap
        // synchronous reads, never binder scans.
        refreshPanelArmed()
        // Coming back from Settings is exactly when a granted-but-unbound listener can
        // be revived; the throttle keeps ordinary returns silent.
        maybeAutoRebindListener()
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

    /**
     * Opens the battery/background settings for this app. On ColorOS/OxygenOS this is
     * the only thing that reduces the return-to-Home delay, because the delay is the
     * ROM suspending or killing the launcher process — not anything this app renders.
     */
    fun openBackgroundPolicySettings() {
        val pkg = getApplication<Application>().packageName
        val opened = appLauncher.openAppBatterySettings(pkg)
        showMessage(
            if (opened) "Allow NoBlooks to run in the background"
            else "No battery settings screen on this device"
        )
    }

    /**
     * Opens the overlay-permission screen. The panel works in-window without it, but
     * only the overlay lets it appear over other apps — so the message is careful:
     * granting it does not guarantee the window shows (ColorOS can still refuse).
     */
    fun openOverlaySettings() {
        val opened = appLauncher.openOverlaySettings()
        showMessage(
            if (opened) "Allow \"Display over other apps\", then come back"
            else "No overlay-permission screen on this device"
        )
    }

    fun openAccessibilitySettings() {
        val opened = appLauncher.openAccessibilitySettings()
        showMessage(
            if (opened) "Enable NoBlooks under Installed apps"
            else "No accessibility settings screen on this device"
        )
    }

    fun openNotificationAccessSettings() {
        update { it.copy(panel = it.panel.reduce(PanelEvent.Hide)) }
        val opened = appLauncher.openNotificationListenerSettings()
        showMessage(
            if (opened) "Enable NoBlooks for notification access"
            else "No notification-access screen on this device"
        )
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
        viewModelScope.launch {
            // RoleManager probing is several binder calls; never on the main thread.
            val state = withContext(Dispatchers.IO) { PermissionStatus.homeRoleState(getApplication()) }
            update { it.copy(isDefaultHome = state.roleHeld == true, homeRoleNote = homeRoleNote(state)) }
        }
    }

    private fun homeRoleNote(state: com.elthon.optilauncher.recon.HomeRoleState): String = when {
        state.hasError -> "Home-role probe unavailable on this device"
        state.roleHeld == true -> "NoBlooks holds the home role."
        state.roleAvailable == true -> "Tap to make NoBlooks your home app."
        state.roleAvailable == false -> "No app currently holds the home role."
        else -> "Home role unknown on this device."
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
        private const val RETURN_DEBOUNCE_MS = 400L
        private const val PACKAGE_REFRESH_DEBOUNCE_MS = 600L
        private const val AUTO_REBIND_INTERVAL_MS = 60_000L
        private val DARK_TINT = 0xFFEDEDEF.toInt()
        private val LIGHT_TINT = 0xFF16171A.toInt()
    }
}
