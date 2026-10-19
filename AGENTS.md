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
  is up, so the app grid never continues underneath it.
- Apps page is **habit-first**: with no query it shows usage-ranked habits plus pinned
  favourites (falling back to the full list only when there is no history); typing
  switches the grid to search over every app, pinned apps included, with no result cap.
  Discovery asks for LAUNCHER *and* LEANBACK entries without `MATCH_DEFAULT_ONLY`, so
  apps that omit `CATEGORY_DEFAULT` are searchable too. Enter launches the best match.
  Launching clears the query. Back unwinds an active search before it collapses the
  island.
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
  mid-morph the spring is already near its target and the open reads as a snap.
- Never summon the soft keyboard on open — only when the user taps a text field.
- Icons are hand-drawn `Canvas` glyphs; no Material 3 / icon libraries. Type goes
  through `NbText`.
- Honesty over decoration: no fabricated data, no fake confidence, no implied recording.
