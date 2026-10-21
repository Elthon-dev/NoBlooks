# NoBlooks — working agreement

## Build & verification: CI only

**Never build, assemble, test or lint locally.** No `./gradlew` build/test/lint tasks,
no local APK, no local `adb` — the local toolchain here is a phone (Termux/aarch64)
and it is not the source of truth.

The only verification loop is GitHub Actions:

- Workflow: `.github/workflows/android.yml` (`testDebugUnitTest`, `lintDebug`,
  `assembleDebug`, `assembleRelease`)
- Artifacts: `optilauncher-debug-apk` (installable, debuggable) and
  `optilauncher-release-apk` (R8; **the one to judge smoothness with** — Compose runs
  interpreted in debuggable builds). APK files are named `optilauncher-<variant>.apk`.
- The debug keystore is restored from the Actions cache so repeated installs keep one
  signature; the release variant is signed with that key only so CI can produce an
  installable APK (not a Play Store signing setup).
- So: make the change → commit → push → watch the run → fix → push again.

Report build/test results as "CI run <url>: <conclusion>". Anything not covered by CI
(on-device rendering, wallpaper compositing, actual app launching, mic capture) must be
called out as **unverified** rather than claimed as working.

## Releasing updates

`versionCode`/`versionName` are bumped for every installable update (`1.0.4` / code 8
as of the real-actions/multitask pass). The CI debug keystore is cached, so installing a
newer APK over an older one keeps working; if the cache is evicted the signature
changes and one uninstall is required (Android cannot update across signatures).

## Repo

- Package / applicationId / namespace: `com.elthon.optilauncher`
- Product name: **NoBlooks**
- `minSdk 29`, `targetSdk 35`, `compileSdk 35`
- http://github.com/Elthon-dev/NoBlooks (public)

## Code map

- `core/` — pure Kotlin, unit-testable, no Android APIs (color, theme, island state
  machine, app search, habits, launch planner, settings codec, bounded cache)
- `data/` — repositories: apps, icons, usage, launch recorder, wallpaper theme, mic
- `ui/` — Compose: `theme/NbTheme.kt` (palette + `NbDim` geometry tokens),
  `components/` (`Common.kt`, `Glyphs.kt`, `GlanceWidget.kt`),
  `island/FloatingIsland.kt` + `island/pages/*`, `HomeContent.kt`, `LauncherViewModel.kt`
- `recon/` — preserved diagnostics surface (Settings page)

## Design rules

- Home screen = glance instrument (top-right) + floating island (middle of the left
  edge), one shared `NbDim.ScreenInset`, composited over the real wallpaper
  (`FLAG_SHOW_WALLPAPER`, no splash theme, no opaque sheet, no title bar). The collapsed
  island is a **portrait capsule** (it lives on a side edge). When the island expands,
  the glance instrument rises by `NbDim.InstrumentLift` (10dp) so the panel's growth
  never crowds it; read in the layout phase, `snap()` under reduced motion. The expanded panel has a
  **fixed anchor**: directly under the measured instrument on phones, level with its top
  edge on wide screens (≥680dp). The instrument reports its height so font-scale changes
  cannot cause a collision. The panel's bottom is clamped to the IME while the keyboard
  is up, so the app grid never continues underneath it, with 58dp reserved for the
  keyboard action row.
- **One surface at a time.** The edge pill is bottom-anchored and can grow tall, so
  while `panel.visible` the island is collapsed (behaviour) and recedes — alpha to 0
  plus a short slide toward the left edge, draw-phase only, snap under reduced motion
  — and its suggestion chip fades with it. The home then reads as *instrument +
  panel*; the pill never sits on top of a live capsule or chip. The pill's own scrim
  already owns input for the screen. The in-app pill **always mounts from zero** and
  animates its own entrance (40px rise + content alpha); composing it while already
  visible must not snap it open.
- The launcher is **dark by default** (`SettingsState.themeMode = DARK`); `AUTO` is
  still available and follows measured wallpaper luminance.
- **Portrait by default.** `SettingsState.allowLandscape = false` locks the activity to
  `SCREEN_ORIENTATION_PORTRAIT`; turning it on uses `SCREEN_ORIENTATION_FULL_USER`
  (follows the device/auto-rotate preference). Applied in `MainActivity.onCreate` and
  collected live; rotation in other apps is unaffected.
- The home screen **hides the status bar** (transient bars by swipe from the top). Top
  clearance is therefore `max(statusBars, displayCutout)` — hiding the bar must never
  put the instrument under a camera cutout.
- While the IME is up, a right-aligned action row sits above it: clear-query, then
  hide-keyboard, 10dp apart and `NbDim.ScreenInset` from the edge. It is its own
  composable so watching the IME cannot recompose the island.
- Apps page is **habit-first**: with no query it shows usage-ranked habits plus pinned
  favourites (falling back to the full list only when there is no history); typing
  switches the grid to search over every app, pinned apps included, with no result cap.
  Discovery asks for LAUNCHER *and* LEANBACK entries without `MATCH_DEFAULT_ONLY`, so
  apps that omit `CATEGORY_DEFAULT` are searchable too. Enter launches the best match.
  Launching clears the query. Back unwinds an active search before it collapses the
  island.
- Search is **rule-based and ranked**: exact label, label prefix, word start, word
  initials (`gm` → Google Maps), query tokens against separate words, substring, then
  package. Accent- and case-insensitive. Habit rank only breaks ties *inside* a tier —
  it must never let a worse textual match win. New ranking rules need a unit test in
  `AppSearchTest`.
- Long-press an app for the action sheet (pin/unpin, app info, uninstall) with a
  haptic tick. No hidden gestures: anything the UI can do is discoverable from this
  sheet, and uninstall always goes through the system uninstaller.
- The **glance widget owns the control centre**: tapping it grows the widget *itself*
  into a control panel (Wi-Fi / Bluetooth / mobile data / airplane tiles). The morph
  starts from the widget's `boundsInRoot`, springs to a top-right slot, and settles its
  corner from the widget radius to the panel radius. It is a full-screen overlay with a
  scrim and a `BackHandler`, mounted once and owning both its open and close morph.
- The control centre **fits its content** (the panel adopts the tiles' measured height,
  clamped to the screen) and floats with a real `NbDim.ControlMargin` so a strip of
  wallpaper stays visible all around. It is clamped on-screen for *every* frame of the
  morph, not just at the ends. While it is open, the floating island **steps aside** to
  a lower-left home (`IslandGeometry.asideY`) and is **locked collapsed** — expansion
  events are swallowed. Tapping the island, or anywhere else, minimises the widget and
  the island glides back to exactly its original spot (same value, reversed).
- **System tiles are honest**, never fake switches: a third-party launcher cannot flip
  a radio, so a tile reads the live state (`ConnectivityManager` transport,
  `AIRPLANE_MODE_ON`, `bluetooth_on`) and opens the platform's own panel
  (`Settings.Panel.ACTION_WIFI` / `ACTION_INTERNET_CONNECTIVITY`,
  `ACTION_BLUETOOTH_SETTINGS`, `ACTION_AIRPLANE_MODE_SETTINGS`). The footer says so.
- **Shizuku is optional and opt-in** (`data/ShizukuBridge.kt`): only when it is running
  and granted do the tiles become *real* toggles — Wi-Fi (`svc wifi`), mobile data
  (`svc data`), Bluetooth (`svc bluetooth` + `cmd bluetooth_manager`) and airplane mode
  (`cmd connectivity airplane-mode` + the global write/broadcast). Shizuku 13.1 removed
  `newProcess`, so commands run through a bound AIDL `ShellUserService`
  (`app/src/main/aidl/.../IShellService.aidl`) — keep both ends off R8. Nothing is
  requested at startup; it is asked for from the control centre only. When Shizuku is
  absent every path degrades to the read-only tiles — never a fake switch.
- The **morph is the widget becoming the panel**, and it is symmetrical: the real
  widget's alpha is handed to the panel over the first fifth of the open and taken
  back over the last fifth of the close (a shared-element handoff, read only in the
  widget's draw lambda), so you never see two cards stacked. Height and width use
  separate, pure functions of the single spring value so the card *unfurls* rather
  than scales. The close is a real animation: the panel stays mounted and frame-polls
  the exit spring until it settles (`MAX_EXIT_FRAMES` guard) instead of unmounting a
  frame in.
- The **suggestion** is a small solid card (icon + app name + reason), tap to launch.
  It must never render a package name: if the predicted package has no known app entry
  the card is skipped, and `HabitEngine.recommend` restricts candidates to known
  packages once the app list exists. No `com.*` fallback text, ever.
- **Bursts and CPU work stay off the hot path.** Package broadcasts are collapsed
  into one forced app scan after 600ms (`PACKAGE_REFRESH_DEBOUNCE_MS`); icon maps
  commit every 24 renders; habit scoring runs on `Dispatchers.Default` and the local
  reads on `Dispatchers.IO`, never on Main. The panel's usage query is cached for 5s
  (`RecentsRepository`) so reopening a face is not a fresh 12-hour `queryEvents` scan,
  and the local launch log is written off-main. `data/PerfTrace.kt` adds real
  `Trace` sections around overlay composition, recents loads and pair launches; its
  log line is debug-only. Never present a stopwatch number that was not measured.
- Native app icons load **progressively and in priority order** (habits, then pinned,
  then the rest), committing partial maps as they render — never one blocking pass over
  every installed app at launch. A not-yet-rendered icon is the monochrome placeholder.
- Returning from an app fades the home screen in (~250ms, from ~0.72 alpha), once per
  resume-from-stopped; the cold-launch resume is skipped, as is reduced motion.
- **The resume path stays cheap.** `onResume`/`onNewIntent` go through
  `LauncherViewModel.onReturnedHome()`, which re-probes only cheap system state (off
  the main thread) and lets the repositories serve their caches. It must never force a
  full usage-history scan: `recheckUsageAccess()` only forces one when the access
  state actually changed. A 7-day `queryEvents` on every return-to-Home was throwing
  away the usage cache each time and is gone. The activity sets
  `alwaysRetainTaskState="true"` so the task survives as long as the process does.
- **The edge control panel is honest about what a third-party app can and cannot do.**
  It is a Compose surface inside the launcher's own window (`ui/panel/ControlPanel.kt`,
  state in `core/panel/PanelContract.kt`), revealed by the accessibility service's
  three-finger tap. Hard truths it must never fake:
  - **Shape:** a compact capsule pill with a **status strip** (weekday, `HH:mm` clock,
    battery %; `CHG` while charging) above **five buttons** — Home, Recents, Alerts,
    Habits, Access. Home jumps home; the rest **morph the pill itself** into their
    list via `animateContentSize` (critically damped). Each list header carries an **×** that
    runs `PanelEvent.CollapseToPill`: back to the pill, *not* hidden. Status formatting is
    pure (`core/panel/PillStatus.kt`, tested) and the clock/battery read lives in
    `data/PillStatusReader.kt` (ticks on the minute, re-reads the sticky battery
    broadcast; display only, never monitoring).
  - **The pill never hides out from under a finger.** A non-consuming pointer observer
    (Initial pass — consuming here would swallow child clicks) sets
    `PanelEvent.SetInteracting`, and `AutoHideElapsed` is a no-op while `interacting`
    is true. Unit-tested in `PanelStateTest`.
  - A **three-finger tap anywhere** needs an `AccessibilityService` with
    `FLAG_REQUEST_MULTI_FINGER_GESTURES`; an overlay sees only its own pixels. While
    the service is off (`PanelState.gestureArmed == false`) the panel is still usable by
    tapping the pill, and the header says the gesture needs enabling — it never claims
    the gesture is live.
  - **Recents / Alerts / Habits are real actions.** Recents performs
    `GLOBAL_ACTION_RECENTS` (falling back to `CATEGORY_APP_RECENTS` on ROMs that resolve
    it) and never substitutes a package-level list; Alerts performs
    `GLOBAL_ACTION_NOTIFICATIONS` — the real shade — and never opens a custom
    notification page; Habits hides the panel and opens the existing in-app habit page
    (the overlay routes through `MainActivity`'s `EXTRA_OPEN_HABITS`). Home performs
    `GLOBAL_ACTION_HOME`, then a `CATEGORY_HOME` intent; it never self-forwards over the
    user's chosen home app. When an action cannot be dispatched, the panel says so and
    stays put.
  - A **Quick Settings tile** (`data/PanelTileService`, `BIND_QUICK_SETTINGS_TILE`) is
    the third reveal trigger. It is an action, not a toggle, and starts the same panel;
    no on/off state is claimed.
  - **Force-stopping another app** is impossible through normal APIs; it goes through
    Shizuku (`am force-stop`, shell uid 2000). The result is a `StopVerdict`, not a
    boolean: `STOPPED` / `REVIVED` (the ROM restarted it) / `UNAVAILABLE` / `REFUSED` /
    `UNVERIFIED`. Without Shizuku the row opens the app-info page instead. Never report
    a bare "stopped" (the UI says "Force-stopped.").
  - The retained **usage-list model** (an MRU package list from `UsageStats`
    `ACTIVITY_RESUMED`, plus live-process corroboration from `dumpsys activity recents`
    via Shizuku) is not bound to the Recents button any more. It is package-level only,
    has no task ids or thumbnails, and must never be presented as the system Overview.
  - The retained **panel habit list** is the real `HabitEngine` ranking
    (`LauncherUiState.panelHabits`). The Habits *button*, however, opens the full in-app
    habit page, not this compact face.
  - The retained **notification-listener feature** is legacy from the old Alerts face and
    is not part of the Alerts path any more. Alerts opens the real shade; the listener
    code and its Settings rows remain only because removing the special-access
    diagnostics wholesale is out of scope. Inline reply via `RemoteInput` is best-effort
    — the UI says "Reply sent", never "delivered".
  - **Granted is not the same as bound.** On ColorOS/MIUI the listener can be killed
    with the process while the grant stays on, which used to read as "access off" in
    the Alerts face. The bridge carries a heartbeat (`lastEventAtMs`), the Alerts face
    distinguishes *grant* from *connection* and offers a reconnect action. There are
    two reconnect paths: `NotificationAccess.rebind()` (plain
    `requestRebind(ComponentName)`, the only sanctioned call, fails without a grant)
    and `forceRebind()` (disable+enable the component with `DONT_KILL_APP`, then
    rebind) for a listener stuck in enabled-but-unbound limbo. The explicit Settings
    action uses the force path and **probes `NotificationBridge.connected` after
    1.6s** before reporting `Listener reconnected`; the request alone is never
    reported as success. The always-on overlay service retries the plain rebind every
    90s while granted-but-unbound. Returning Home retries at most once a minute, and
    `MY_PACKAGE_REPLACED`/boot receivers rebind and re-arm the panel. The ROM may
    still ignore every request; Settings points at **App info → Auto-start +
    background activity**, which is the verified ColorOS workaround. Never claim the
    listener is receiving when only the grant is known.
  - **Access / multitasking** is a request, never a promise.
    `core/panel/MultitaskCapability` is the preflight both the chooser and `data/MultitaskLauncher`
    read: Float is **blocked** when the device lacks
    `FEATURE_FREEFORM_WINDOW_MANAGEMENT` (the request is not made, and nothing is
    silently opened full-screen); Triple is **partial** without freeform (the A+B pair
    is requested, the floating third is an explicit manual step) and **ready** only
    with freeform. Low-RAM is a warning, never a silent downgrade. **Split asks for two
    apps** — a single adjacent launch has nothing to pair with (the flag pairs the
    target with the *caller's* window, and the application context has no task), which
    is why it used to open beside the launcher or do nothing. `data/LaunchPairActivity`
    is an invisible trampoline with its own task affinity: it launches app 1 normally,
    waits for the system's own `onStop` signal that app 1 is covering it (with a guarded
    fallback timer, not a fixed sleep as the primary mechanism), then launches app 2
    from a real Activity with `FLAG_ACTIVITY_LAUNCH_ADJACENT`. **Triple** requests the
    A+B pair first, then C floating through the same trampoline when freeform exists.
    `setLaunchWindowingMode` is hidden API and must not be used/reflected.
    `core/panel/MultitaskMode` caps the picker at the required count (2 for split,
    3 for triple), ticks are checkmarked on the right, and `PanelStateTest` covers the
    caps. The UI/footers say the system decides the final layout, and
    `data/MultitaskLauncher` reports downgrades/blocks honestly.
  - It **never renders a package name**: unlabeled recents entries and untitled
    notifications show "Unknown app" / "Notification", never `com.*`.
  - `Hide` / `AutoHideElapsed` are idempotent, `CollapseToPill` never hides, and a
    three-finger tap never leaves the panel hidden; the same rules as the island,
    unit-tested in `PanelStateTest`.
- **The always-on panel is a foreground-service overlay, and it stays honest about
  its limits.**
  - Over the launcher window the panel is a Compose surface (`ui/panel/ControlPanel.kt`).
    Over *other* apps it is the same composable hosted by `data/ComposeOverlayService`, a
    `LifecycleService` that owns the process-wide `data/PanelBus` state and the same
    system actions. The overlay is a `TYPE_APPLICATION_OVERLAY` window that stays mounted
    for the service's life; visibility and touchability track the shared state
    (`VIEW` alpha/translation + `View.GONE` + `FLAG_NOT_TOUCHABLE`), because re-adding a
    `ComposeView` breaks its composition. It is **pre-mounted at service start**, parked
    and transparent, so the first reveal does not pay for composition. Show is a 180ms
    fade+16dp rise; hide fades for 140ms and only then drops to `GONE` (cancel-safe, and
    `ValueAnimator.areAnimatorsEnabled()` wins). While the launcher activity is started
    (`PanelBus.appForeground`) the
    overlay is suppressed — the in-app panel already owns that screen, and two stacked
    panels/scrims are never allowed. The boot receiver *arms* the service without
    revealing the panel.
  - On API 34+ an overlay added from a **background** service is torn down silently, so
    the service is a **foreground service** with `FOREGROUND_SERVICE_SPECIAL_USE` and the
    `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property. `SYSTEM_ALERT_WINDOW`
    is a special permission: declaring it is what surfaces "Display over other apps".
  - **ColorOS/MIUI can refuse the window even when `canDrawOverlays()` is true** (`addView`
    throws a security error). The code reports the permission state and never claims the
    overlay showed. Persistence is best-effort (`START_STICKY`, a boot receiver that only
    fires when the permission and the service are already on) — never "always on".
  - **Reply is disabled in the overlay** (`allowReply = false`): a `FLAG_NOT_FOCUSABLE`
    window cannot raise the keyboard. Reply stays available in the in-app panel only.
  - Reveal has **two triggers**: the three-finger tap (reliable, needs the accessibility
    service) and **both volume keys together** (best-effort only — Android already uses
    hold-both-volume as the global accessibility shortcut, and only one service may filter
    key events). The chord timing is pure logic in `core/panel/VolumeChord.kt`, tested in
    `VolumeChordTest`; key-repeat `ACTION_DOWN` events are ignored (both in the service
    and by the chord's own state), and the service **never swallows** keys, so volume
    keeps working. Reveal adds a short guarded vibration tick. The Quick Settings tile is
    the third trigger.
- **The ColorOS/OxygenOS return delay is a system behaviour, not ours.** On
  OPPO/OnePlus/Realme ROMs, third-party launchers show a blank-wallpaper stretch when
  swiping Home, with gesture navigation only (3-button is instant), and it affects
  Nova/Niagara/Microsoft alike — the ROM suspends or kills the launcher process and
  the stock launcher owns the home transition. No supported app-level API overrides
  it. The Settings page therefore offers a keep-alive hint
  (`core/util/OemBackgroundPolicy.kt`, detected from `Build.MANUFACTURER`/`BRAND`) that
  deep-links to this app's battery settings. The hint says "may" and never claims the
  exemption is on — that state is not readable.
- Surfaces are **solid colour** with soft drop shadows for depth; no borders framing
  cards, no wallpaper bleed through panels.
- Motion is crisp, never cartoony: only critically damped springs
  (`Spring.DampingRatioNoBouncy`) or short emphasized duration curves (`NbMotion`).
  Under-damped springs are banned — overshoot is what reads as "cartoony". No decorative
  choreography: content must be readable/tappable immediately (fades ≤140ms, no stagger,
  no page slides). Behavior beats animation — when in doubt, cut the animation, not the
  function. Predictive back is gesture-linked, never decorative.
- Animated values are read in the layout/draw phase (`Modifier.layout`, `graphicsLayer`,
  Canvas lambdas), never during composition — a frame of animation must not recompose
  the island, its tabs, or the open page. Faces are mounted from discrete state.
- **Mount, then move.** Opening mounts the expanded face and lets one frame land before
  the size spring starts. A page's first composition is the expensive frame; if it runs
  mid-morph the spring is already near its target and the open reads as a snap. The same
  rule applies to the control centre.
- **Landscape:** the island panel claims the full usable column height instead of
  `0.62 · H`; width and the `sideBySide` (≥680dp) rule are unchanged.
- Never summon the soft keyboard on open — only when the user taps a text field.
- Icons are hand-drawn `Canvas` glyphs; no Material 3 / icon libraries. Type goes
  through `NbText`.
- Honesty over decoration: no fabricated data, no fake confidence, no implied recording.
