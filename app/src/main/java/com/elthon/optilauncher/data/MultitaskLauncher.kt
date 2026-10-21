package com.elthon.optilauncher.data

import android.content.Context
import android.util.Log
import com.elthon.optilauncher.core.launch.LaunchOutcome
import com.elthon.optilauncher.core.launch.WindowMode
import com.elthon.optilauncher.core.panel.MultitaskCapability
import com.elthon.optilauncher.core.panel.MultitaskMode

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
 *  - FLOAT : launch bounds, only when the device declares freeform. Without it the
 *            preflight blocks the request and says so — it never opens full-screen
 *            and calls that "floating".
 *  - TRIPLE: the pair is requested first (A+B), then C is requested floating when
 *            freeform exists. Without freeform the pair is still requested and the
 *            report says the third window is a manual, system-side step.
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

        val assessment = MultitaskCapability.assess(mode, launcher.deviceCaps())
        if (!assessment.usable) {
            return MultitaskReport(
                mode = mode,
                outcomes = listOf(LaunchOutcome.Unsupported(assessment.note, WindowMode.NORMAL)),
                note = assessment.note
            )
        }

        val outcomes = when (mode) {
            MultitaskMode.SPLIT -> pairOutcome(picked[0], picked[1])

            MultitaskMode.FLOAT ->
                listOf(launcher.launchPackageInMode(picked[0], WindowMode.FREEFORM))

            MultitaskMode.TRIPLE -> {
                if (assessment.canFloat) {
                    // A+B form the pair; the trampoline asks for C floating after the
                    // adjacent request, from a real Activity.
                    pairOutcome(picked[0], picked[1], picked[2])
                } else {
                    // Pair first, never fake the floating third. The report explains
                    // the system-side step that remains for C.
                    pairOutcome(picked[0], picked[1])
                }
            }
        }

        return MultitaskReport(
            mode = mode,
            outcomes = outcomes,
            note = noteFor(outcomes, assessment.note)
        )
    }

    private fun pairOutcome(
        first: String,
        second: String,
        third: String? = null
    ): List<LaunchOutcome> {
        val needed = listOfNotNull(first, second, third)
        val missing = needed.any { !launchable(it) }
        if (missing) {
            return listOf(
                LaunchOutcome.Unavailable("One of the chosen apps has no launchable activity any more.")
            )
        }
        return if (startPair(first, second, third)) {
            pairOutcomes(third != null)
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
     * Hands the package names to [LaunchPairActivity]. That Activity is the caller
     * the system pairs against, which the application context can never be.
     */
    private fun startPair(first: String, second: String, third: String?): Boolean = try {
        appContext.startActivity(LaunchPairActivity.intent(appContext, first, second, third))
        true
    } catch (t: Throwable) {
        Log.w(TAG, "pair trampoline refused: ${t.javaClass.simpleName}")
        false
    }

    private fun pairOutcomes(withFloatingThird: Boolean): List<LaunchOutcome> = buildList {
        add(LaunchOutcome.Started(WindowMode.NORMAL))
        add(LaunchOutcome.Started(WindowMode.SPLIT))
        if (withFloatingThird) add(LaunchOutcome.Started(WindowMode.FREEFORM))
    }

    private fun noteFor(
        outcomes: List<LaunchOutcome>,
        assessmentNote: String
    ): String {
        outcomes.filterIsInstance<LaunchOutcome.Unsupported>().firstOrNull()?.let { return it.reason }
        val blocked = outcomes.filterIsInstance<LaunchOutcome.Blocked>().firstOrNull()
        if (blocked != null) return blocked.reason
        val unavailable = outcomes.filterIsInstance<LaunchOutcome.Unavailable>().firstOrNull()
        if (unavailable != null) return unavailable.reason
        return assessmentNote
    }

    private companion object {
        private const val TAG = "MultitaskLauncher"
    }
}

data class MultitaskReport(
    val mode: MultitaskMode,
    val outcomes: List<LaunchOutcome>,
    val note: String
) {
    val started: Int get() = outcomes.count { it is LaunchOutcome.Started }
}
