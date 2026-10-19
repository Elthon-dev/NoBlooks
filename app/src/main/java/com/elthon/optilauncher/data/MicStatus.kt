package com.elthon.optilauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.elthon.optilauncher.core.mic.MicIndicator
import com.elthon.optilauncher.core.mic.MicPermission
import com.elthon.optilauncher.recon.HomeRoleProbe
import com.elthon.optilauncher.recon.HomeRoleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.sqrt

/**
 * Permission/status probes that the UI reads. Every probe is cheap, synchronous
 * and safe to call from composition; none of them ever *requests* anything.
 */
object PermissionStatus {

    fun micPermission(context: Context): MicPermission {
        val declared = try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            info.requestedPermissions?.contains(Manifest.permission.RECORD_AUDIO) == true
        } catch (_: Throwable) {
            false
        }
        if (!declared) return MicPermission.NOT_REQUESTED

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) MicPermission.GRANTED else MicPermission.DENIED
    }

    fun micIndicator(context: Context): MicIndicator =
        MicIndicator.idle(micPermission(context))

    /** Delegates to the existing read-only probe so diagnostics and UI agree. */
    fun homeRoleState(context: Context): HomeRoleState = HomeRoleProbe.probe(context)

    fun isDefaultHome(context: Context): Boolean =
        homeRoleState(context).roleHeld == true
}

/**
 * User-initiated microphone level check — the ONLY thing in this app that ever
 * captures audio.
 *
 * Contract:
 *  - started only from an explicit tap on the Settings page, after the runtime
 *    permission dialog was answered,
 *  - foreground-only: the scope is cancelled by the caller when the screen goes
 *    away or the test is stopped; there is no service and no background capture,
 *  - releases the AudioRecord on every exit path (success, error, cancellation),
 *  - never claims the system privacy indicator: Android owns that.
 */
class MicLevelMeter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _state = MutableStateFlow<MicIndicator?>(null)
    val state: StateFlow<MicIndicator?> = _state.asStateFlow()

    @Volatile
    private var recording = false

    private var record: AudioRecord? = null

    fun isActive(): Boolean = recording

    /** Returns false when permission is missing (caller should request it first). */
    fun start(hasPermission: Boolean): Boolean {
        if (recording) return true
        if (!hasPermission) {
            _state.value = MicIndicator.idle(MicPermission.DENIED)
            return false
        }
        _state.value = MicIndicator.recording()
        recording = true

        scope.launch {
            try {
                captureLoop()
            } catch (t: Throwable) {
                _state.value = MicIndicator.error(MicPermission.GRANTED, "${t.javaClass.simpleName}: ${t.message ?: "capture failed"}")
            } finally {
                release()
                recording = false
                _level.value = 0f
            }
        }
        return true
    }

    fun stop() {
        recording = false
        // The loop exits and releases; state settles to "idle, granted".
        _state.value = MicIndicator.idle(MicPermission.GRANTED)
    }

    fun destroy() {
        stop()
        scope.cancel()
        release()
    }

    /**
     * Everything here runs only after [start] was called with
     * `hasPermission = true`, which itself only happens after the runtime
     * RECORD_AUDIO dialog was answered. The construction is additionally wrapped
     * in a SecurityException-tolerant try/catch below, so lint's blanket
     * "missing permission check" does not apply.
     */
    @SuppressLint("MissingPermission")
    private suspend fun captureLoop() {
        val sampleRate = SAMPLE_RATE_HZ
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            _state.value = MicIndicator.error(MicPermission.GRANTED, "Unsupported capture configuration on this device")
            return
        }
        val bufferSize = maxOf(minBuffer, sampleRate / 5 * 2) // ~200 ms headroom
        val audio = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (t: Throwable) {
            _state.value = MicIndicator.error(MicPermission.GRANTED, "Microphone unavailable: ${t.message ?: t.javaClass.simpleName}")
            return
        }

        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            _state.value = MicIndicator.error(MicPermission.GRANTED, "Microphone failed to initialize")
            return
        }
        record = audio

        val buffer = ShortArray(bufferSize / 2)
        try {
            audio.startRecording()
            while (recording && isActiveScope()) {
                val read = audio.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        val sample = buffer[i] / 32768.0
                        sum += sample * sample
                    }
                    val rms = sqrt(sum / read).toFloat()
                    _level.value = (rms * 3.2f).coerceIn(0f, 1f)
                } else if (read < 0) {
                    _state.value = MicIndicator.error(MicPermission.GRANTED, "Capture read error ($read)")
                    break
                }
            }
        } finally {
            try {
                audio.stop()
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun isActiveScope(): Boolean =
        kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true

    private fun release() {
        val audio = record ?: return
        record = null
        try {
            audio.stop()
        } catch (_: Throwable) {
        }
        try {
            audio.release()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 16_000
    }
}
