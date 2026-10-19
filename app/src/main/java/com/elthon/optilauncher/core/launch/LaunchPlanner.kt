package com.elthon.optilauncher.core.launch

/**
 * Launch planning kept separate from [android.content.Intent] construction so the
 * decision table (what to attempt, what to fall back to, how failures are read)
 * is unit-testable and so device-specific windowing improvements can be slotted in
 * behind the same interface later.
 */
enum class WindowMode { NORMAL, SPLIT, FREEFORM }

/** What the device/OS can reasonably be asked for. */
data class DeviceCaps(
    val supportsSplitScreen: Boolean = true,
    /** pm.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT) */
    val supportsFreeform: Boolean = false
) {
    companion object {
        const val FREEFORM_FEATURE = "android.software.freeform_window_management"
    }
}

data class LaunchTarget(val packageName: String, val className: String, val resizable: Boolean = true)

data class LaunchPlan(
    val target: LaunchTarget,
    val mode: WindowMode,
    /** NEW_TASK etc. — plain ints so the plan stays Android-free. */
    val flags: Int,
    /** True when FLAG_ACTIVITY_LAUNCH_ADJACENT is in [flags]. */
    val adjacent: Boolean,
    /** Set when the requested mode was downgraded before launching. */
    val downgradedFrom: WindowMode? = null,
    val note: String? = null
)

sealed interface LaunchOutcome {
    data class Started(val mode: WindowMode) : LaunchOutcome
    /** No activity could be resolved for the target (uninstalled/disabled). */
    data class Unavailable(val reason: String) : LaunchOutcome
    /** The requested window mode cannot be offered here; a fallback was used or offered. */
    data class Unsupported(val reason: String, val fallback: WindowMode) : LaunchOutcome
    /** Android refused the launch (security/background-start restrictions). */
    data class Blocked(val reason: String) : LaunchOutcome
    data class Failed(val reason: String) : LaunchOutcome
}

/**
 * Flag values mirrored from android.content.Intent so the decision table has no
 * Intent construction in it (values are stable public API and identical on every
 * API level we support, minSdk 29 > the API 11 introduction of all four). The
 * outcome mapper at the bottom is the only place that names Android exception
 * types, and only for classification.
 */
object IntentFlags {
    const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
    const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
    const val FLAG_ACTIVITY_RESET_TASK_IF_NEEDED = 0x00200000
    const val FLAG_ACTIVITY_MULTIPLE_TASK = 0x08000000
    const val FLAG_ACTIVITY_LAUNCH_ADJACENT = 0x00001000
}

object LaunchPlanner {

    fun plan(target: LaunchTarget, requested: WindowMode, caps: DeviceCaps): LaunchPlan = when (requested) {
        WindowMode.NORMAL -> LaunchPlan(
            target = target,
            mode = WindowMode.NORMAL,
            flags = IntentFlags.FLAG_ACTIVITY_NEW_TASK or IntentFlags.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            adjacent = false
        )

        WindowMode.SPLIT -> {
            if (!caps.supportsSplitScreen || !target.resizable) {
                LaunchPlan(
                    target = target,
                    mode = WindowMode.NORMAL,
                    flags = IntentFlags.FLAG_ACTIVITY_NEW_TASK or IntentFlags.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                    adjacent = false,
                    downgradedFrom = WindowMode.SPLIT,
                    note = if (!target.resizable) {
                        "This app does not support resizing, so split screen was skipped."
                    } else {
                        "This device does not report split-screen support."
                    }
                )
            } else {
                LaunchPlan(
                    target = target,
                    mode = WindowMode.SPLIT,
                    flags = IntentFlags.FLAG_ACTIVITY_NEW_TASK or
                        IntentFlags.FLAG_ACTIVITY_MULTIPLE_TASK or
                        IntentFlags.FLAG_ACTIVITY_LAUNCH_ADJACENT,
                    adjacent = true,
                    note = "Split-screen is a request — Android decides whether the windowing state allows it."
                )
            }
        }

        WindowMode.FREEFORM -> {
            when {
                caps.supportsFreeform && target.resizable -> LaunchPlan(
                    target = target,
                    mode = WindowMode.FREEFORM,
                    flags = IntentFlags.FLAG_ACTIVITY_NEW_TASK or IntentFlags.FLAG_ACTIVITY_MULTIPLE_TASK,
                    adjacent = false,
                    note = "Freeform bounds are applied best-effort; the device may place the window differently."
                )
                else -> {
                    // Honest ladder: freeform -> split (if usable) -> plain launch.
                    val splitUsable = caps.supportsSplitScreen && target.resizable
                    LaunchPlan(
                        target = target,
                        mode = if (splitUsable) WindowMode.SPLIT else WindowMode.NORMAL,
                        flags = if (splitUsable) {
                            IntentFlags.FLAG_ACTIVITY_NEW_TASK or
                                IntentFlags.FLAG_ACTIVITY_MULTIPLE_TASK or
                                IntentFlags.FLAG_ACTIVITY_LAUNCH_ADJACENT
                        } else {
                            IntentFlags.FLAG_ACTIVITY_NEW_TASK or IntentFlags.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        },
                        adjacent = splitUsable,
                        downgradedFrom = WindowMode.FREEFORM,
                        note = if (!caps.supportsFreeform) {
                            "This device does not expose freeform (floating) windowing for other apps. " +
                                "Used ${if (splitUsable) "split screen" else "a normal launch"} instead."
                        } else {
                            "This app cannot be resized, so it was opened normally."
                        }
                    )
                }
            }
        }
    }

    /** Maps a thrown failure into an honest, user-explainable outcome. */
    fun outcomeFor(error: Throwable, plan: LaunchPlan): LaunchOutcome = when (error) {
        is android.content.ActivityNotFoundException -> LaunchOutcome.Unavailable(
            "The app has no launchable activity any more (uninstalled or disabled)."
        )
        is SecurityException -> LaunchOutcome.Blocked(
            "Android refused to start this app: ${error.message ?: "security restriction"}"
        )
        is android.util.AndroidRuntimeException -> LaunchOutcome.Blocked(
            error.message ?: "Android refused to start this app from the launcher."
        )
        is java.util.concurrent.CancellationException -> throw error
        else -> LaunchOutcome.Failed(
            "${error.javaClass.simpleName}: ${error.message ?: "unknown error"}"
        )
    }

    fun outcomeForPlan(plan: LaunchPlan): LaunchOutcome =
        if (plan.downgradedFrom != null) {
            LaunchOutcome.Unsupported(plan.note ?: "Requested window mode is unavailable.", plan.mode)
        } else {
            LaunchOutcome.Started(plan.mode)
        }
}
