package com.elthon.optilauncher.recon

import java.io.File
import java.nio.charset.Charset

/**
 * Local, bounded log + latest-report store.
 *
 * Everything lives in the app's private storage (`filesDir/recon`): nothing is uploaded,
 * nothing leaves the device, and the log can never grow without bound — once it passes
 * [maxLogBytes] it is rotated once into `scan.prev.log` and restarted.
 *
 * Pure java.io on purpose so the rotation behaviour is unit-testable on the JVM.
 */
class ReportLogger(
    private val dir: File,
    private val maxLogBytes: Long = DEFAULT_MAX_LOG_BYTES
) {
    companion object {
        const val DEFAULT_MAX_LOG_BYTES: Long = 512L * 1024L
        const val LOG_FILE = "scan.log"
        const val LOG_PREV_FILE = "scan.prev.log"
        const val REPORT_FILE = "report-latest.txt"
        private val UTF8: Charset = Charsets.UTF_8
    }

    val logFile: File get() = File(dir, LOG_FILE)
    val prevLogFile: File get() = File(dir, LOG_PREV_FILE)
    val reportFile: File get() = File(dir, REPORT_FILE)

    init {
        dir.mkdirs()
    }

    @Synchronized
    fun log(line: String, epochMs: Long = System.currentTimeMillis()) {
        val stamped = "$epochMs $line\n"
        logFile.parentFile?.mkdirs()
        logFile.appendText(stamped, UTF8)
        // Rotate immediately after an append that crossed the cap so the live log is
        // always <= maxLogBytes (assuming a single line never exceeds the cap itself).
        rotateIfNeeded()
    }

    @Synchronized
    fun readLog(): String {
        val file = logFile
        if (!file.exists()) return ""
        if (file.length() <= maxLogBytes) return file.readText(UTF8)
        // Defence in depth: never hand back more than one rotation's worth.
        val bytes = file.readBytes()
        return String(bytes, bytes.size - maxLogBytes.toInt().coerceAtMost(bytes.size), maxLogBytes.toInt().coerceAtMost(bytes.size), UTF8)
    }

    @Synchronized
    fun writeReport(text: String) {
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(text, UTF8)
    }

    @Synchronized
    fun readReport(): String? =
        if (reportFile.exists()) reportFile.readText(UTF8) else null

    @Synchronized
    fun logSizeBytes(): Long = if (logFile.exists()) logFile.length() else 0L

    /** Visible for tests: rotates only when the cap is actually exceeded. */
    internal fun rotateIfNeeded() {
        val file = logFile
        if (!file.exists() || file.length() <= maxLogBytes) return
        if (prevLogFile.exists()) prevLogFile.delete()
        if (!file.renameTo(prevLogFile)) {
            // Rename can fail on odd filesystems; fall back to copy+truncate so the
            // log still cannot grow past the cap.
            prevLogFile.writeBytes(file.readBytes())
            file.writeText("", UTF8)
        }
    }

    @Synchronized
    fun clear() {
        if (logFile.exists()) logFile.delete()
        if (prevLogFile.exists()) prevLogFile.delete()
        if (reportFile.exists()) reportFile.delete()
    }
}
