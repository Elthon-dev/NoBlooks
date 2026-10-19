package com.elthon.optilauncher.data

import com.elthon.optilauncher.core.habits.LaunchLogCodec
import com.elthon.optilauncher.core.habits.LocalLaunch
import com.elthon.optilauncher.core.settings.KeyValueStore

/**
 * Bounded, on-device record of the launches *NoBlooks itself* performed.
 *
 * This is deliberately not a device-wide activity log: it only ever contains
 * what the launcher did, it is trimmed on every write, and it is deleted from
 * the Settings page. It feeds the habit model only as a clearly-labeled
 * "observed by NoBlooks" signal when Usage Access is unavailable.
 */
class LaunchRecorder(
    private val store: KeyValueStore,
    private val now: () -> Long = System::currentTimeMillis
) {

    fun record(packageName: String) {
        if (packageName.isEmpty()) return
        val at = now()
        val updated = LaunchLogCodec.append(history(), LocalLaunch(packageName, at), at)
        store.writeString(KEY, LaunchLogCodec.encode(updated, at))
    }

    fun history(): List<LocalLaunch> = LaunchLogCodec.decode(store.readString(KEY), now())

    fun clear() {
        store.writeString(KEY, null)
    }

    companion object {
        const val KEY = "noblooks_local_launch_log_v1"
    }
}
