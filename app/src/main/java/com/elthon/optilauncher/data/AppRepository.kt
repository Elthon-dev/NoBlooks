package com.elthon.optilauncher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.elthon.optilauncher.core.apps.AppRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Installed, launchable applications.
 *
 * - One full PackageManager scan, cached; recomposition never triggers a scan.
 * - Package add/remove/replace/locale broadcasts invalidate the cache (package
 *   broadcasts are protected: only the system can send them, which is why the
 *   receiver may be exported).
 * - Only MAIN+LAUNCHER activities are visible to us (manifest `<queries>`), and
 *   our own package is intentionally excluded from our own drawer.
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val isSystem: Boolean,
    val resizable: Boolean,
    val lastUpdatedMs: Long
) {
    fun toRef(): AppRef = AppRef(
        packageName = packageName,
        label = label,
        component = component.flattenToString(),
        isSystem = isSystem
    )
}

class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    @Volatile
    private var cache: List<AppEntry>? = null

    @Volatile
    private var receiver: BroadcastReceiver? = null

    suspend fun apps(forceRefresh: Boolean = false): List<AppEntry> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cache?.let { return@withContext it }
        }
        val scanned = queryLaunchableApps()
        cache = scanned
        scanned
    }

    fun cached(): List<AppEntry>? = cache

    fun invalidate() {
        cache = null
    }

    private fun queryLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Throwable) {
            emptyList()
        }
        val now = System.currentTimeMillis()
        return resolved.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            if (activityInfo.packageName == appContext.packageName) return@mapNotNull null
            val appInfo = activityInfo.applicationInfo ?: return@mapNotNull null
            val label = try {
                info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: activityInfo.packageName
            } catch (_: Throwable) {
                activityInfo.packageName
            }
            AppEntry(
                packageName = activityInfo.packageName,
                label = label,
                component = ComponentName(activityInfo.packageName, activityInfo.name),
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                // Per-activity resizability (ActivityInfo.resizeMode) is not part of the
                // public compile SDK surface, so we assume the platform default (true)
                // and let LaunchPlanner downgrade honestly if Android refuses the mode.
                resizable = true,
                lastUpdatedMs = now
            )
        }
            .distinctBy { it.component }
            .sortedWith(
                compareBy<AppEntry> { entry -> entry.label.lowercase(java.util.Locale.ROOT) }
                    .thenBy { entry -> entry.packageName }
            )
    }

    /**
     * Starts listening for package/locale changes. Safe to call repeatedly;
     * the previous receiver (if any) is replaced on the main thread.
     */
    fun startWatching(onChanged: () -> Unit) {
        val existing = receiver
        if (existing != null) {
            unregister(existing)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addDataScheme("package")
        }
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                invalidate()
                onChanged()
            }
        }
        receiver = newReceiver
        try {
            ContextCompat.registerReceiver(
                appContext,
                newReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (t: Throwable) {
            // A failed listener registration only means the list refreshes on next
            // cold load; the launcher keeps working.
            receiver = null
            android.util.Log.w(TAG, "package change listener not registered: ${t.javaClass.simpleName}")
        }
    }

    fun stopWatching() {
        receiver?.let { unregister(it) }
        receiver = null
    }

    private fun unregister(r: BroadcastReceiver) {
        try {
            appContext.unregisterReceiver(r)
        } catch (_: Throwable) {
            // Already unregistered.
        }
    }

    companion object {
        private const val TAG = "AppRepository"
    }
}
