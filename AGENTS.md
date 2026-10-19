# NoBlooks — working agreement

## Build & verification: CI only

**Never build, assemble, test or lint locally.** No `./gradlew` build/test/lint tasks,
no local APK, no local `adb` — the local toolchain here is a phone (Termux/aarch64)
and it is not the source of truth.

The only verification loop is GitHub Actions:

- Workflow: `.github/workflows/android.yml` (`testDebugUnitTest`, `lintDebug`, `assembleDebug`)
- Artifact: `noblooks-debug-apk`
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
  (`FLAG_SHOW_WALLPAPER`, no splash theme, no opaque sheet, no title bar). The two are
  diagonal on purpose so neither can cover the other at any screen height.
- Surfaces are **solid colour** with soft drop shadows for depth; no borders framing
  cards, no wallpaper bleed through panels.
- Motion is fluid, never cartoony: only critically damped springs
  (`Spring.DampingRatioNoBouncy`) or Material emphasized duration curves (`NbMotion`).
  Under-damped springs are banned — overshoot is what reads as "cartoony". The island
  morph keeps the pill's centre fixed so the silhouette grows out of it.
- Animated values are read in the layout/draw phase (`Modifier.layout`, `graphicsLayer`,
  Canvas lambdas), never during composition — a frame of animation must not recompose
  the island, its tabs, or the open page. Faces are mounted from discrete state.
- Never summon the soft keyboard on open — only when the user taps a text field.
- Icons are hand-drawn `Canvas` glyphs; no Material 3 / icon libraries. Type goes
  through `NbText`.
- Honesty over decoration: no fabricated data, no fake confidence, no implied recording.
