package com.elthon.optilauncher.data

import android.content.Context
import android.util.Log
import com.elthon.optilauncher.core.launch.LaunchOutcome
import com.elthon.optilauncher.core.launch.WindowMode
import com.elthon.optilauncher.core.panel.MultitaskMode
import kotlinx.coroutines.delay

/**
 * Performs the Access face's window requests.
 *
 * There is no public API that force-places windows exactly where we want them;
 * `ActivityOptions.setLaunchWindowingMode` is hidden, and
 * `FLAG_ACTIVITY_LAUNCH_ADJACENT` is a request the system may decline. What the
 * API *does* specify is that the adjacent window is paired with the caller's
 * window — so the caller has to be a real, full-screen Activity with a task.
 * That is why this launcher does not fire adjacent launches from the application
 * context any more:
 *
 *  - SPLIT : two apps. App 1 opens normally, then [LaunchPairActivity] asks for app 2
 *            with the adjacent flag. One app can never form a pair; the old
 *            single-app request was being paired with the launcher itself or
 *            dropped, which is why the old Split looked like a no-op.
 *  - FLOAT : launch bounds, only when the device declares freeform; otherwise the
 *            planner downgrades it and the report says so.
 *  - TRIPLE: app 1 requested floating (or plain when the ROM has no freeform), then
 *            apps 2 and 3 through the same pair trampoline.
 *
 * The [MultitaskReport.note] is shown to the user, so it never claims a layout
 * that was not at most requested.
 */
class MultitaskLauncher(context: Context) {

    private val appContext = context.applicationContext
    private val launcher = AppLauncher(appContext)

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
            MultitaskMode.SPLIT -> pairOutcome(picked[0], picked[1])

            MultitaskMode.FLOAT -> listOf(launcher.launchPackageInMode(picked[0], WindowMode.FREEFORM))

            MultitaskMode.TRIPLE -> {
                val first = launcher.launchPackageInMode(
                    picked[0],
                    if (freeformSupported()) WindowMode.FREEFORM else WindowMode.NORMAL
                )
                // Let the first window settle before the pair request; the trampoline
                // owns the beat between app 2 and the adjacent app 3.
                delay(FLOAT_SETTLE_MS)
                listOf(first) + pairOutcome(picked[1], picked[2])
            }
        }

        return MultitaskReport(
            mode = mode,
            outcomes = outcomes,
            note = noteFor(mode, outcomes, freeformSupported())
        )
    }

    private fun pairOutcome(first: String, second: String): List<LaunchOutcome> {
        val missing = listOf(first, second).any { !launchable(it) }
        if (missing) {
            return listOf(
                LaunchOutcome.Unavailable("One of the chosen apps has no launchable activity any more.")
            )
        }
        return if (startPair(first, second)) {
            pairOutcomes()
        } else {
            listOf(LaunchOutcome.Blocked("Android refused to start the split-pair request."))
        }
    }

    private fun launchable(packageName: String): Boolean = try {
        appContext.packageManager.getLaunchIntentForPackage(packageName) != null
    } catch (_: Throwable) {
        false
    }

    /**
     * Hands two package names to [LaunchPairActivity]. That Activity is the caller
     * the system pairs against, which the application context can never be.
     */
    private fun startPair(first: String, second: String): Boolean = try {
        appContext.startActivity(LaunchPairActivity.intent(appContext, first, second))
        true
    } catch (t: Throwable) {
        Log.w(TAG, "pair trampoline refused: ${t.javaClass.simpleName}")
        false
    }

    private fun pairOutcomes(): List<LaunchOutcome> = listOf(
        LaunchOutcome.Started(WindowMode.NORMAL),
        LaunchOutcome.Started(WindowMode.SPLIT)
    )

    private fun noteFor(
        mode: MultitaskMode,
        outcomes: List<LaunchOutcome>,
        freeform: Boolean
    ): String {
        outcomes.filterIsInstance<LaunchOutcome.Unsupported>().firstOrNull()?.let { return it.reason }
        val blocked = outcomes.filterIsInstance<LaunchOutcome.Blocked>().firstOrNull()
        if (blocked != null) return blocked.reason
        val unavailable = outcomes.filterIsInstance<LaunchOutcome.Unavailable>().firstOrNull()
        if (unavailable != null) return unavailable.reason
        return when (mode) {
            MultitaskMode.SPLIT ->
                "Two apps launched: the second was requested beside the first. " +
                    "If they didn't pair, open Recents → Split."

            MultitaskMode.FLOAT ->
                if (freeform) {
                    "Floating window requested; the system may place or resize it differently."
                } else {
                    "This ROM does not expose freeform windows, so Android opened it another way."
                }

            MultitaskMode.TRIPLE ->
                if (freeform) {
                    "Three apps launched: two as a split pair, one requested floating — Android has the final say."
                } else {
                    "Three apps launched: two as a split pair, the first full-screen — Android has the final say."
                }
        }
    }

    private companion object {
        private const val TAG = "MultitaskLauncher"
    }
}

private const val FLOAT_SETTLE_MS = 420L

data class MultitaskReport(
    val mode: MultitaskMode,
    val outcomes: List<LaunchOutcome>,
    val note: String
) {
    val started: Int get() = outcomes.count { it is LaunchOutcome.Started }
}
