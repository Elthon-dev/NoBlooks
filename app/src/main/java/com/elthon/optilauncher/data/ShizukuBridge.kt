package com.elthon.optilauncher.data

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.annotation.Keep
import com.elthon.optilauncher.IShellService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs

/**
 * Optional elevated access, via Shizuku, for the control centre's radio tiles.
 *
 * Android gives a third-party launcher no way to flip Wi-Fi or mobile data. With
 * Shizuku running (and this app granted) we can run shell commands that *do* —
 * `svc wifi enable`, `svc data enable` — and read back a state that is genuinely
 * per-radio, which is what makes Wi-Fi and mobile data distinguishable instead of
 * both collapsing into "there is a network".
 *
 * Shizuku 13.1 removed `Shizuku#newProcess`, so commands run through a
 * [ShellUserService] bound over AIDL — the supported way. Everything here is
 * guarded: if Shizuku is not installed, not running, not granted, or dies mid-call,
 * [available] is false and the caller falls back to the read-only tiles. Nothing is
 * requested at startup — the user opts in from the control centre.
 */
object ShizukuBridge {

    private const val PERMISSION_REQUEST_CODE = 0x53B0
    private const val SERVICE_TAG = "noblooks-shell"
    private const val SERVICE_VERSION = 1

    @Volatile
    private var permissionResult: ((Boolean) -> Unit)? = null

    @Volatile
    private var service: IShellService? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == PERMISSION_REQUEST_CODE) {
            permissionResult?.invoke(grant == PackageManager.PERMISSION_GRANTED)
            permissionResult = null
        }
    }

    private val serviceArgs = UserServiceArgs(
        ComponentName("com.elthon.optilauncher", ShellUserService::class.java.name)
    )
        .processNameSuffix("shell")
        .tag(SERVICE_TAG)
        .version(SERVICE_VERSION)
        .debuggable(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IShellService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
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
        // Bind eagerly once granted; a no-op when it is not.
        bindService()
    }

    fun unregisterListener() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {
        }
        unbindService()
    }

    private fun bindService() {
        if (!available() || service != null) return
        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (_: Throwable) {
            // Shizuku died between the check and the bind.
        }
    }

    private fun unbindService() {
        try {
            Shizuku.unbindUserService(serviceArgs, connection, true)
        } catch (_: Throwable) {
        }
        service = null
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

    /** Runs [command] through the bound user service, returning its output or null. */
    suspend fun exec(command: String): String? = withContext(Dispatchers.IO) {
        if (!available()) return@withContext null
        // The bind is async; nudge it and wait briefly so the first tap is not a dud.
        if (service == null) {
            bindService()
            var waited = 0
            while (service == null && waited < 1000) {
                delay(50)
                waited += 50
            }
        }
        val target = service ?: return@withContext null
        try {
            target.exec(command)
        } catch (_: Throwable) {
            service = null
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
