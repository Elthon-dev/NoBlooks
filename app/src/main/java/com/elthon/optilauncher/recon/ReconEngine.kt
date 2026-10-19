package com.elthon.optilauncher.recon

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.elthon.optilauncher.recon.model.InsetsSnapshot
import com.elthon.optilauncher.recon.model.ProbeError
import com.elthon.optilauncher.recon.model.ReconReport
import com.elthon.optilauncher.recon.model.ScanTrigger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Executes a single scan pass and exposes its outcome to listeners.
 *
 * Policy:
 *  - Scans always run on a single-worker executor (sequential) to avoid races and to keep
 *    heap pressure predictable on a device with tight RAM.
 *  - The UI thread feeds insets into [lastInsets] immediately on each WindowInsets dispatch.
 *  - If the app is paused and resumed within [RESUME_SCAN_THRESHOLD_MS], the engine will
 *    queue a RESUME-triggered scan. If paused for longer, the next scan is APP_START/USER_REQUEST
 *    style as appropriate.
 *  - A scan cannot start if one is already running. Listeners are notified once per scan.
 */
class ReconEngine private constructor(
    private val context: Context,
    private val executor: ExecutorService,
    private val scanner: ReconScanner,
    private val logger: ReportLogger,
    private val listener: ReconListener?,
    private val formatter: (ReconReport) -> String
) {
    interface ReconListener {
        fun onScanStarted(trigger: ScanTrigger)
        fun onScanSucceeded(report: ReconReport, formattedText: String)
        fun onScanFailed(trigger: ScanTrigger, error: Throwable)
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private val lastInsetsRef = AtomicReference<List<InsetsSnapshot>>(emptyList())
    private val lastScanEpochRef = AtomicReference<Long?>(null)
    private val scanInFlight = AtomicBoolean(false)

    @Volatile
    private var pauseAtEpochMs: Long? = null

    fun onInsetsChanged(snapshots: List<InsetsSnapshot>) {
        if (snapshots.isNotEmpty()) {
            lastInsetsRef.set(snapshots)
        }
    }

    fun onPause() {
        pauseAtEpochMs = System.currentTimeMillis()
    }

    fun onResume() {
        val pausedAt = pauseAtEpochMs
        val now = System.currentTimeMillis()
        if (pausedAt != null && (now - pausedAt) <= RESUME_SCAN_THRESHOLD_MS) {
            requestScan(ScanTrigger.RESUME)
        }
        // If paused for longer, do *not* auto-scan: the caller decides (APP_START/FIRST_RUN/USER_REQUEST).
        pauseAtEpochMs = null
    }

    fun requestScan(trigger: ScanTrigger) {
        if (!scanInFlight.compareAndSet(false, true)) {
            Log.i(TAG, "Ignoring scan request ($trigger): scan already in flight")
            return
        }

        lastScanEpochRef.set(System.currentTimeMillis())
        executor.execute {
            try {
                uiHandler.post { listener?.onScanStarted(trigger) }

                val snapshot = UiSnapshotCollector.capture(context, lastInsetsRef.get())
                val report = scanner.scan(trigger, snapshot)
                val formatted = formatter(report)

                logger.log("SCAN trigger=${trigger.name} durationMs=${report.scanDurationMs} errors=${report.probeErrors.size}")
                logger.writeReport(formatted)

                uiHandler.post { listener?.onScanSucceeded(report, formatted) }
            } catch (t: Throwable) {
                val message = "Scan failed: ${t.javaClass.simpleName} ${t.message ?: ""}"
                logger.log(message)
                uiHandler.post { listener?.onScanFailed(trigger, t) }
            } finally {
                scanInFlight.set(false)
            }
        }
    }

    fun isScanInFlight(): Boolean = scanInFlight.get()

    companion object {
        private const val TAG = "ReconEngine"
        const val RESUME_SCAN_THRESHOLD_MS = 30_000L

        fun create(
            context: Context,
            listener: ReconListener? = null,
            formatter: (ReconReport) -> String = { com.elthon.optilauncher.recon.model.ReportFormatter.format(it) }
        ): ReconEngine {
            val executor: ExecutorService = Executors.newSingleThreadExecutor(
                object : ThreadFactory {
                    private val count = java.util.concurrent.atomic.AtomicInteger(1)
                    override fun newThread(r: Runnable): Thread = Thread(r, "opti-launcher-recon-${count.getAndIncrement()}")
                }
            )
            return ReconEngine(
                context = context.applicationContext,
                executor = executor,
                scanner = ReconScanner(context.applicationContext),
                logger = ReportLogger(context.applicationContext.filesDir.resolve("recon")),
                listener = listener,
                formatter = formatter
            )
        }
    }
}
