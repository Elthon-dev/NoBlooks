package com.elthon.optilauncher.core.panel

/**
 * The panel's honest view of "recently used apps".
 *
 * There is no public API for the system's real Overview stack, so this is a
 * *best-effort* MRU list built from usage events (and, when Shizuku is present,
 * corroborated by `dumpsys activity recents`). That distinction matters: the UI
 * must never call this "the system's recents" — it is the launcher's own view.
 *
 * Two things this model makes explicit rather than hiding:
 *  - Entries are package-level. Two windows of the same browser are one row.
 *  - We do not have real task ids, so we cannot move a specific foreign task to
 *    the front; we relaunch by package.
 */
data class RecentApp(
    val packageName: String,
    val label: String,
    val lastUsedMs: Long,
    /** True when the process was observed live (Shizuku `dumpsys`), not just in history. */
    val liveProcess: Boolean = false
)

/**
 * Drops the launcher's own package and system chrome, then orders MRU.
 *
 * `selfPackage` is filtered because "NoBlooks" showing in its own switcher is
 * noise. The system UI package is filtered because the shade/keyboard are always
 * resuming and would otherwise dominate the list.
 */
fun List<RecentApp>.forPanel(selfPackage: String, max: Int = 12): List<RecentApp> =
    filter { it.packageName != selfPackage && it.packageName != SYSTEM_UI_PACKAGE }
        .sortedByDescending { it.lastUsedMs }
        .distinctBy { it.packageName }
        .take(max)

/** The AOSP/SystemUI package that owns the shade, recents and nav. */
const val SYSTEM_UI_PACKAGE = "com.android.systemui"
