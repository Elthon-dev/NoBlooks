package com.elthon.optilauncher.data

import android.content.pm.PackageManager
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Optional elevated access, via Shizuku, for the control centre's radio tiles.
 *
 * Android gives a third-party launcher no way to flip Wi-Fi or mobile data. With
 * Shizuku running (and this app granted) we can run shell commands that *do* —
 * `svc wifi enable`, `svc data enable` — and read back a state that is genuinely
 * per-radio, which is what makes Wi-Fi and mobile data distinguishable instead of
 * both collapsing into "there is a network".
 *
 * Everything here is guarded: if Shizuku is not installed, not running, not
 * granted, or dies mid-call, [available] is false and the caller falls back to the
 * read-only tiles. Nothing is requested at startup — the user opts in from the
 * control centre.
 */
object ShizukuBridge {

    private const val PERMISSION_REQUEST_CODE = 0x53B0

    @Volatile
    private var permissionResult: ((Boolean) -> Unit)? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == PERMISSION_REQUEST_CODE) {
            permissionResult?.invoke(grant == PackageManager.PERMISSION_GRANTED)
            permissionResult = null
        }
    }

    /** True only when the Shizuku service is alive *and* this app has been granted. */
    fun available(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** True when Shizuku is running but this app has not been granted yet. */
    fun needsPermission(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun registerListener() {
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {
            // Shizuku absent: nothing to listen to.
        }
    }

    fun unregisterListener() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {
        }
    }

    /**
     * Asks Shizuku for the permission. [onResult] fires once with the decision.
     * Returns false when there is nothing to ask (Shizuku not running).
     */
    fun requestPermission(onResult: (Boolean) -> Unit): Boolean = try {
        if (!Shizuku.pingBinder()) {
            false
        } else if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            onResult(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
            true
        } else {
            permissionResult = onResult
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        }
    } catch (_: Throwable) {
        false
    }

    suspend fun exec(command: String): String? = withContext(Dispatchers.IO) {
        if (!available()) return@withContext null
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (_: Throwable) {
            null
        }
    }

    /** Runs a command and returns just the trimmed first line, for `settings get`. */
    suspend fun readGlobal(key: String): String? =
        exec("settings get global $key")?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}

/** Keep this small holder off R8's shrinker: Shizuku reflects on it. */
@Keep
internal object ShizukuKeepAlive
