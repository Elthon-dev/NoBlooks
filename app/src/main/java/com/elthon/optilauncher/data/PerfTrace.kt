package com.elthon.optilauncher.data

import android.os.SystemClock
import android.os.Trace
import com.elthon.optilauncher.BuildConfig

/**
 * Lightweight tracing for the paths that decide whether the launcher feels
 * immediate: overlay composition, panel data loads, and pair launches.
 *
 * `Trace` sections show up in Perfetto/Studio when tracing is enabled; the log
 * line is debug-only so release builds stay quiet. There is no fabricated
 * measurement here — a section measures only its own block.
 */
internal object PerfTrace {

    inline fun <T> section(label: String, block: () -> T): T {
        Trace.beginSection(label)
        val startedAt = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            Trace.endSection()
            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    "NoBlooksPerf",
                    "$label ${SystemClock.elapsedRealtime() - startedAt}ms"
                )
            }
        }
    }
}
