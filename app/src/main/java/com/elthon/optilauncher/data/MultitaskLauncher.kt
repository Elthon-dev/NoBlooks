package com.elthon.optilauncher.data

import android.content.Context
import com.elthon.optilauncher.core.launch.LaunchOutcome
import com.elthon.optilauncher.core.launch.WindowMode
import com.elthon.optilauncher.core.panel.MultitaskMode
import kotlinx.coroutines.delay

/**
 * Performs the Access face's window requests.
 *
 * There is no public API to force a split-screen or place three windows exactly
 * where we want them; `ActivityOptions.setLaunchWindowingMode` is hidden, and
 * `FLAG_ACTIVITY_LAUNCH_ADJACENT` is a request the system may decline. So this
 * launcher does the honest maximum:
 *
 *  - SPLIT : one app with the adjacent flag (Android 12L+ can enter split from a
 *            full-screen state; on older versions it only works if split is
 *            already up).
 *  - FLOAT : launch bounds, only when the device declares freeform; otherwise
 *            [AppLauncher]'s planner downgrades it and the report says so.
 *  - TRIPLE: app 1 requested floating, app 2 normal, app 3 adjacent — the layout
 *            they end up in is the system's decision, not ours. Launches are
 *            staged with short beats so the system has a focused full-screen task
 *            to place the adjacent window beside; firing all three in one tick
 *            usually gets the flag ignored.
 *
 * The [MultitaskReport.note] is shown to the user, so it never claims a layout
 * that was not requested at most.
 */
class MultitaskLauncher(context: Context) {

    private val launcher = AppLauncher(context)

    fun freeformSupported(): Boolean = launcher.deviceCaps().supportsFreeform

    suspend fun launch(packages: List<String>, mode: MultitaskMode): MultitaskReport {
        val picked = packages.take(mode.requiredApps)
        if (picked.size < mode.requiredApps) {
            return MultitaskReport(
                mode = mode,
                outcomes = emptyList(),
                note = "Pick ${mode.requiredApps} app(s) first."
            )
        }

        val outcomes = when (mode) {
            MultitaskMode.SPLIT -> listOf(launcher.launchPackageInMode(picked[0], WindowMode.SPLIT))
            MultitaskMode.FLOAT -> listOf(launcher.launchPackageInMode(picked[0], WindowMode.FREEFORM))
            MultitaskMode.TRIPLE -> {
                val first = launcher.launchPackageInMode(picked[0], WindowMode.FREEFORM)
                // Let the floating window settle before the full-screen launch.
                delay(FLOAT_SETTLE_MS)
                val second = launcher.launchPackageInMode(picked[1], WindowMode.NORMAL)
                // The adjacent request needs a focused full-screen task to sit beside.
                delay(SPLIT_SETTLE_MS)
                val third = launcher.launchPackageInMode(picked[2], WindowMode.SPLIT)
                listOf(first, second, third)
            }
        }

        return MultitaskReport(
            mode = mode,
            outcomes = outcomes,
            note = noteFor(mode, outcomes, freeformSupported())
        )
    }

    private fun noteFor(
        mode: MultitaskMode,
        outcomes: List<LaunchOutcome>,
        freeform: Boolean
    ): String {
        outcomes.filterIsInstance<LaunchOutcome.Unsupported>().firstOrNull()?.let { return it.reason }
        val blocked = outcomes.filterIsInstance<LaunchOutcome.Blocked>().firstOrNull()
        if (blocked != null) return blocked.reason
        return when (mode) {
            MultitaskMode.SPLIT ->
                "Split screen requested. Android decides whether it can enter split from here."

            MultitaskMode.FLOAT ->
                if (freeform) {
                    "Floating window requested; the system may place or resize it differently."
                } else {
                    "This ROM does not expose freeform windows, so Android opened it another way."
                }

            MultitaskMode.TRIPLE ->
                if (freeform) {
                    "Three apps started: one floating, two side by side — Android has the final say on layout."
                } else {
                    "Three apps started, but this ROM has no freeform windows — the first one may open full-screen."
                }
        }
    }
}

private const val FLOAT_SETTLE_MS = 420L
private const val SPLIT_SETTLE_MS = 680L

data class MultitaskReport(
    val mode: MultitaskMode,
    val outcomes: List<LaunchOutcome>,
    val note: String
) {
    val started: Int get() = outcomes.count { it is LaunchOutcome.Started }
}
