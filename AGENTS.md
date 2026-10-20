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
  island is a **portrait capsule** (it lives on a side edge). The expanded panel has a
  **fixed anchor**: directly under the measured instrument on phones, level with its top
  edge on wide screens (≥680dp). The instrument reports its height so font-scale changes
  cannot cause a collision. The panel's bottom is clamped to the IME while the keyboard
  is up, so the app grid never continues underneath it, with 58dp reserved for the
  keyboard action row.
- The launcher is **dark by default** (`SettingsState.themeMode = DARK`); `AUTO` is
  still available and follows measured wallpaper luminance.
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
    battery %; `CHG` while charging) above **four buttons** — Home, Recents, Alerts,
    Habits. Home jumps home; the other three **morph the pill itself** into their list
    via `animateContentSize` (critically damped). Each list header carries an **×** that
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
  - **Recents / Alerts / Habits buttons open the pill's own lists** (not the system
    overview or shade). The SystemRecents/SystemNotifications events remain in the
    contract for the fallback path but are not bound to the pill buttons.
  - **Force-stopping another app** is impossible through normal APIs; it goes through
    Shizuku (`am force-stop`, shell uid 2000). The result is a `StopVerdict`, not a
    boolean: `STOPPED` / `REVIVED` (the ROM restarted it) / `UNAVAILABLE` / `REFUSED` /
    `UNVERIFIED`. Without Shizuku the row opens the app-info page instead. Never report
    a bare "stopped".
  - The **recents list** is an MRU package list from `UsageStats` `ACTIVITY_RESUMED`
    (plus live-process corroboration from `dumpsys activity recents` via Shizuku), not
    the system Overview stack — package-level only, no task ids or thumbnails.
  - The **habits list** in the in-app panel is the real `HabitEngine` ranking
    (`LauncherUiState.panelHabits`, derived from `habitSummary` + installed labels). The
    overlay has no habit engine, so it reuses the usage repository's MRU list — real
    usage, but an approximation, never fabricated.
  - **Notifications** need `NotificationListenerService` (special access); the panel
    shows a "grant access" state until it is present. Inline reply via `RemoteInput` is
    best-effort — the UI says "Reply sent", never "delivered".
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
    for the service's life; only its touchability tracks visibility (`FLAG_NOT_TOUCHABLE`
    toggled via `updateViewLayout`), because re-adding a `ComposeView` breaks its
    composition.
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
    `VolumeChordTest`; the service observes the keys and **never swallows them**, so
    volume keeps working. Reveal adds a short guarded vibration tick.
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
