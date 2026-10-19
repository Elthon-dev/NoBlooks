package com.elthon.optilauncher.core.mic

/**
 * Microphone indicator model.
 *
 * The island's mic glyph must never lie: "permission granted" is NOT "listening".
 * Only [State.RECORDING] means this process is actually capturing audio, and in
 * this build that only happens while the user has explicitly started the
 * microphone level check on the Settings page.
 */
enum class MicPermission { GRANTED, DENIED, NOT_REQUESTED }

enum class MicState {
    /** No microphone feature has been started; nothing is capturing audio. */
    INACTIVE,

    /** Permission held, no capture running. */
    READY,

    /** This process is genuinely capturing audio right now. */
    RECORDING,

    /** Permission missing: the mic cannot be started. */
    PERMISSION_DENIED,

    /** A start was attempted and failed (device busy, policy, hardware). */
    ERROR
}

data class MicIndicator(
    val permission: MicPermission,
    val state: MicState,
    val detail: String? = null
) {
    /** True only while audio is really being captured — drives any live animation. */
    val isActive: Boolean get() = state == MicState.RECORDING

    companion object {
        fun idle(permission: MicPermission): MicIndicator = when (permission) {
            MicPermission.GRANTED -> MicIndicator(permission, MicState.READY)
            MicPermission.DENIED -> MicIndicator(permission, MicState.PERMISSION_DENIED, "Microphone permission denied")
            MicPermission.NOT_REQUESTED -> MicIndicator(permission, MicState.INACTIVE, "Microphone not used")
        }

        fun recording(): MicIndicator = MicIndicator(MicPermission.GRANTED, MicState.RECORDING)

        fun error(permission: MicPermission, detail: String): MicIndicator =
            MicIndicator(permission, MicState.ERROR, detail)
    }
}
