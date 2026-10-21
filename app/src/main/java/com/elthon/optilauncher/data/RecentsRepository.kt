package com.elthon.optilauncher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.elthon.optilauncher.core.panel.RecentApp
import com.elthon.optilauncher.core.panel.SYSTEM_UI_PACKAGE
import com.elthon.optilauncher.core.panel.forPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort "recently used apps" for the control panel.
 *
 * This is **not** the system's Overview stack and it says so. Android gives a
 * third-party app no public API for real recent tasks (task ids, card order,
 * thumbnails) — `getRecentTasks` is dead, `queryRecentTasks` is system-only. What
 * we *can* do, with Usage Access already granted, is read `ACTIVITY_RESUMED`
 * events and build a most-recently-used package list. When Shizuku is present we
 * additionally corroborate liveness with `dumpsys activity recents`, so an entry
 * can be marked as a live process rather than just "was used recently".
 *
 * Every limitation is surfaced, not hidden: package-level only, no task ids, and
 * usage events are truncated to the last few minutes by the platform.
 */
class RecentsRepository(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    /**
     * Raw MRU results are cached for a few seconds. Opening a face and then
     * re-opening it used to run a 12-hour `queryEvents` scan every time, on the
     * same hot path that draws the panel. Labels and live-process flags are applied
     * per call, so the cache cannot hide a newly opened app for long.
     */
    private val lock = Any()
    private var cachedMru: List<Pair<String, Long>>? = null
    private var cachedWindowMs = 0L
    private var cachedAtElapsedMs = 0L

    /**
     * Returns the MRU list, newest first. [livePackages], when supplied, marks
     * which entries are currently alive (from `dumpsys activity recents`).
     */
    suspend fun recents(
        selfPackage: String,
        windowMs: Long = DEFAULT_WINDOW_MS,
        max: Int = DEFAULT_MAX,
        livePackages: Set<String> = emptySet()
    ): List<RecentApp> = withContext(Dispatchers.IO) {
        val mru = mruWithCache(windowMs) ?: return@withContext emptyList()

        mru.map { (pkg, ts) ->
            RecentApp(
                packageName = pkg,
                label = labelFor(pkg),
                lastUsedMs = ts,
                liveProcess = pkg in livePackages
            )
        }.forPanel(selfPackage = selfPackage, max = max)
    }

    private fun mruWithCache(windowMs: Long): List<Pair<String, Long>>? {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(lock) {
            val cached = cachedMru
            if (cached != null && cachedWindowMs == windowMs &&
                now - cachedAtElapsedMs < CACHE_TTL_MS
            ) {
                return cached
            }
        }
        val fresh = queryMru(windowMs) ?: return null
        synchronized(lock) {
            cachedMru = fresh
            cachedWindowMs = windowMs
            cachedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        }
        return fresh
    }

    private fun queryMru(windowMs: Long): List<Pair<String, Long>>? {
        val um = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val end = System.currentTimeMillis()
        val begin = end - windowMs

        val lastResume = LinkedHashMap<String, Long>()
        try {
            val events = um.queryEvents(begin, end) ?: return emptyList()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    val pkg = e.packageName ?: continue
                    // Re-inserting moves the key to the end, so insertion order == recency.
                    lastResume.remove(pkg)
                    lastResume[pkg] = e.timeStamp
                }
            }
        } catch (_: Throwable) {
            return emptyList()
        }

        return lastResume.entries.map { it.key to it.value }
    }

    /** Drops the MRU cache after an action that changes recency (force-stop). */
    fun invalidate() {
        synchronized(lock) {
            cachedMru = null
            cachedAtElapsedMs = 0L
        }
    }

    /**
     * App labels, resolved defensively. A package we cannot resolve (stale entry,
     * uninstalled between the query and now) is dropped rather than shown as a raw
     * package name — the panel must never display `com.*` chrome.
     */
    private var labelCache: HashMap<String, String> = HashMap()

    private fun labelFor(pkg: String): String = synchronized(labelCache) {
        labelCache.getOrPut(pkg) {
            try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (_: Throwable) {
                ""
            }
        }
    }

    /** True when this device lets us read usage events at all. */
    fun hasUsageAccess(): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        appOps?.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
        false
    }

    companion object {
        /** Usage events are only retained "a few days"; 12h is plenty for a switcher. */
        const val DEFAULT_WINDOW_MS = 12L * 60 * 60 * 1000
        const val DEFAULT_MAX = 12
        /** Short enough that the list still feels live, long enough to stop re-scans. */
        private const val CACHE_TTL_MS = 5_000L

        /** Packages we never attribute as "live" even if dumpsys mentions them. */
        val IGNORED = setOf(SYSTEM_UI_PACKAGE)
    }
}
