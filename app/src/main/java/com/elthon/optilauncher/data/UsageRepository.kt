package com.elthon.optilauncher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.elthon.optilauncher.core.habits.AppAggregate
import com.elthon.optilauncher.core.habits.DataSource
import com.elthon.optilauncher.core.habits.UsageEvent
import com.elthon.optilauncher.core.habits.UsageAggregator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Whether Android currently lets this app read usage statistics. */
enum class UsageAccess { GRANTED, DENIED, UNKNOWN }

data class UsageData(
    val aggregates: List<AppAggregate>,
    val access: UsageAccess,
    val historyDays: Int,
    val eventsScanned: Int,
    val truncated: Boolean,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val source: DataSource = DataSource.SYSTEM
) {
    companion object {
        fun empty(access: UsageAccess, windowStartMs: Long, windowEndMs: Long): UsageData =
            UsageData(emptyList(), access, 0, 0, false, windowStartMs, windowEndMs)
    }
}

interface UsageRepository {
    /** Cheap, synchronous permission probe (own-uid app ops). */
    fun access(): UsageAccess

    /** Bounded history load. Implementations cache; callers may request a refresh. */
    suspend fun load(nowMs: Long, forceRefresh: Boolean = false): UsageData

    fun clearCache()
}

/**
 * UsageStats-backed implementation.
 *
 * Honesty notes baked into the shape of the result:
 *  - only a bounded window is queried (default 7 days) and the event scan is
 *    capped, so [UsageData.truncated] is reported rather than hidden;
 *  - the data is Android's own usage stats: batched, vendor-dependent, never
 *    claimed to be perfect or real-time;
 *  - when access is missing, [UsageData.aggregates] is empty and the access flag
 *    says so — nothing is fabricated from launcher clicks here.
 */
class SystemUsageRepository(
    context: Context,
    private val historyDays: Int = DEFAULT_HISTORY_DAYS,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS
) : UsageRepository {

    private val appContext = context.applicationContext
    private val usageStatsManager: UsageStatsManager? =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    @Volatile
    private var cached: UsageData? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    override fun access(): UsageAccess {
        val appOps = try {
            appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        } catch (_: Throwable) {
            null
        } ?: return UsageAccess.UNKNOWN

        return try {
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
            when (mode) {
                AppOpsManager.MODE_ALLOWED -> UsageAccess.GRANTED
                AppOpsManager.MODE_ERRORED, AppOpsManager.MODE_IGNORED -> UsageAccess.DENIED
                else -> UsageAccess.UNKNOWN
            }
        } catch (t: Throwable) {
            // Deliberately no "guess from query results" fallback: an empty stats
            // list is ambiguous (denied *or* nothing recorded), and guessing here
            // would put a false permission claim in the UI.
            android.util.Log.w(TAG, "usage access probe failed: ${t.javaClass.simpleName}")
            UsageAccess.UNKNOWN
        }
    }

    override suspend fun load(nowMs: Long, forceRefresh: Boolean): UsageData = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cached?.let {
                if (nowMs - cachedAtMs < CACHE_TTL_MS) return@withContext it
            }
        }

        val windowStart = startOfWindow(nowMs, historyDays)
        val currentAccess = access()
        if (currentAccess != UsageAccess.GRANTED) {
            val empty = UsageData.empty(currentAccess, windowStart, nowMs)
            cached = empty
            cachedAtMs = nowMs
            return@withContext empty
        }

        val usm = usageStatsManager
        if (usm == null) {
            val empty = UsageData.empty(UsageAccess.UNKNOWN, windowStart, nowMs)
            cached = empty
            cachedAtMs = nowMs
            return@withContext empty
        }

        val events = ArrayList<UsageEvent>(512)
        var scanned = 0
        var truncated = false
        val days = HashSet<Int>()
        try {
            val result: UsageEvents = usm.queryEvents(windowStart, nowMs)
            val row = UsageEvents.Event()
            while (result.hasNextEvent()) {
                if (!result.getNextEvent(row)) break
                scanned++
                if (scanned > maxEvents) {
                    truncated = true
                    break
                }
                val type = when (row.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Type.FOREGROUND
                    UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvent.Type.BACKGROUND
                    else -> continue
                }
                val pkg = row.packageName ?: continue
                if (pkg == appContext.packageName) continue
                val ts = row.timeStamp
                events.add(UsageEvent(pkg, type, ts))
                if (type == UsageEvent.Type.FOREGROUND) days.add(dayIndex(ts))
            }
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "usage events unavailable: ${t.javaClass.simpleName} ${t.message ?: ""}")
            val empty = UsageData.empty(currentAccess, windowStart, nowMs)
            cached = empty
            cachedAtMs = nowMs
            return@withContext empty
        }

        val aggregates = UsageAggregator.aggregate(events, windowStart, nowMs)
        val data = UsageData(
            aggregates = aggregates,
            access = currentAccess,
            historyDays = days.size,
            eventsScanned = scanned,
            truncated = truncated,
            windowStartMs = windowStart,
            windowEndMs = nowMs
        )
        cached = data
        cachedAtMs = nowMs
        data
    }

    override fun clearCache() {
        cached = null
        cachedAtMs = 0L
    }

    private fun startOfWindow(nowMs: Long, days: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }
        return cal.timeInMillis
    }

    private fun dayIndex(epochMs: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        return cal.get(Calendar.DAY_OF_YEAR) * 100 + cal.get(Calendar.ERA)
    }

    companion object {
        private const val TAG = "UsageRepository"
        const val DEFAULT_HISTORY_DAYS = 7
        const val DEFAULT_MAX_EVENTS = 6000
        const val CACHE_TTL_MS = 90_000L
    }
}

/**
 * Deterministic in-memory implementation used by unit tests and previews.
 * Nothing here is ever used as the app's real usage history.
 */
class FakeUsageRepository(
    private val access: UsageAccess = UsageAccess.GRANTED,
    private val data: UsageData? = null
) : UsageRepository {

    var loadCount = 0
        private set

    override fun access(): UsageAccess = access

    override suspend fun load(nowMs: Long, forceRefresh: Boolean): UsageData {
        loadCount++
        return data ?: UsageData.empty(access, nowMs - 86_400_000L, nowMs)
    }

    override fun clearCache() {
        loadCount = 0
    }
}

/** Used by tests to assert package-visibility expectations without Android. */
object UsageAccessPolicy {
    fun isReadable(access: UsageAccess): Boolean = access == UsageAccess.GRANTED

    fun explanationFor(access: UsageAccess): String = when (access) {
        UsageAccess.GRANTED -> "Usage access granted — habit insights use Android's own usage statistics."
        UsageAccess.DENIED -> "Usage access not granted — habit insights are limited to launches made inside NoBlooks."
        UsageAccess.UNKNOWN -> "Could not determine usage access — habit insights stay disabled."
    }
}
