package com.elthon.optilauncher.core.panel

import com.elthon.optilauncher.core.launch.DeviceCaps

/**
 * Preflight for the Access face.
 *
 * The panel can only *request* window modes, but it can be honest before asking:
 * freeform is a declared system feature, and a low-RAM device is a real warning.
 * This pure assessment is what both the chooser and the launcher read, so the
 * copy in the UI and the refusal at launch cannot drift apart.
 */
enum class MultitaskAvailability {
    /** The full request can be made. */
    READY,

    /** Some of the request can be made; the rest is an explicit limitation. */
    PARTIAL,

    /** Nothing sensible can be requested; do not launch anything. */
    BLOCKED
}

data class MultitaskAssessment(
    val mode: MultitaskMode,
    val availability: MultitaskAvailability,
    val canPair: Boolean,
    val canFloat: Boolean,
    val lowRam: Boolean,
    val note: String
) {
    val usable: Boolean get() = availability != MultitaskAvailability.BLOCKED
}

object MultitaskCapability {

    fun assess(mode: MultitaskMode, caps: DeviceCaps): MultitaskAssessment {
        val canPair = caps.supportsSplitScreen
        val canFloat = caps.supportsFreeform
        val lowRam = caps.isLowRam

        val (availability, baseNote) = when (mode) {
            MultitaskMode.SPLIT -> when {
                canPair -> MultitaskAvailability.READY to
                    "Two apps will be requested side by side; the system decides the final layout."
                else -> MultitaskAvailability.BLOCKED to
                    "This device does not report split-screen support, so no pair was requested."
            }

            MultitaskMode.FLOAT -> when {
                canFloat -> MultitaskAvailability.READY to
                    "One app will be requested as a floating window; the system decides placement."
                else -> MultitaskAvailability.BLOCKED to
                    "This device does not expose freeform (floating) windows, so nothing was launched."
            }

            MultitaskMode.TRIPLE -> when {
                canPair && canFloat -> MultitaskAvailability.READY to
                    "The first two apps will be requested side by side, the third floating."
                canPair -> MultitaskAvailability.PARTIAL to
                    "This device cannot float a third app. The first two will be requested side " +
                    "by side; open the third from Recents and use the system's split menu."
                else -> MultitaskAvailability.BLOCKED to
                    "This device does not report split-screen support, so the three-app request " +
                    "was not attempted."
            }
        }

        val note = if (lowRam) {
            "$baseNote This is a low-memory device, so Android may close one of them."
        } else {
            baseNote
        }

        return MultitaskAssessment(
            mode = mode,
            availability = availability,
            canPair = canPair,
            canFloat = canFloat,
            lowRam = lowRam,
            note = note
        )
    }
}
