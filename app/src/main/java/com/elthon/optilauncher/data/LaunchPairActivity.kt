package com.elthon.optilauncher.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.elthon.optilauncher.core.launch.LaunchOutcome
import com.elthon.optilauncher.core.launch.WindowMode

/**
 * Invisible trampoline that turns two package names into a split-screen pair.
 *
 * Why this exists: `FLAG_ACTIVITY_LAUNCH_ADJACENT` pairs the target with the
 * **caller's** window. Asking for it from the application context — or as the only
 * app in the request — leaves the system with nothing to pair, so the flag is either
 * ignored or the target lands beside the launcher task. Starting app 1 normally and
 * then asking for app 2 from a real Activity is the recipe shipping split-screen
 * launchers use; the platform has a full-screen task to place the adjacent window
 * beside.
 *
 * The sequence is deliberate and event-driven: app 1 starts normally; the system
 * tells us it is visible by stopping this (fully covered) trampoline; only then is
 * app 2 requested adjacent. A guarded fallback timer exists for ROMs that never
 * deliver `onStop`, but the normal path is not "sleep 700ms and hope".
 *
 * An optional third package is requested floating **after** the pair, from this
 * real Activity, and only when the preflight already confirmed freeform support.
 * This is still a **request** — the system can decline to split or float — so no
 * caller claims a layout was achieved.
 */
class LaunchPairActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var firstStarted = false
    private var secondRequested = false
    private var finished = false
    private val fallback = Runnable { requestSecond() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val first = intent.getStringExtra(EXTRA_FIRST)
        val second = intent.getStringExtra(EXTRA_SECOND)
        val third = intent.getStringExtra(EXTRA_THIRD)
        if (first.isNullOrBlank() || second.isNullOrBlank()) {
            finishOnce()
            return
        }

        val launcher = AppLauncher(this)
        val firstOutcome = PerfTrace.section("NoBlooks.pair.first") {
            launcher.launchPackageInMode(first, WindowMode.NORMAL, starter = this)
        }
        if (firstOutcome !is LaunchOutcome.Started) {
            Log.w(TAG, "pair abandoned: first app $firstOutcome")
            finishOnce()
            return
        }

        firstStarted = true
        // Fallback only: the primary trigger is onStop below, which is the system's
        // own signal that app 1 is covering this trampoline.
        handler.postDelayed(fallback, STOP_FALLBACK_MS)
    }

    override fun onStop() {
        super.onStop()
        if (firstStarted) requestSecond()
    }

    /**
     * App 1 is visible. Ask for app 2 adjacent, then (on a real Activity, still
     * inside the launch grace window) ask for the optional third floating.
     */
    private fun requestSecond() {
        if (secondRequested || isFinishing) return
        secondRequested = true
        handler.removeCallbacks(fallback)
        val second = intent.getStringExtra(EXTRA_SECOND) ?: run {
            finishOnce()
            return
        }
        val third = intent.getStringExtra(EXTRA_THIRD)
        val launcher = AppLauncher(this)

        // One short settle after the visibility signal so the first task's window
        // is focused; this is a beat, not the sequencing mechanism.
        handler.postDelayed({
            val secondOutcome = PerfTrace.section("NoBlooks.pair.second") {
                launcher.launchPackageInMode(second, WindowMode.SPLIT, starter = this)
            }
            if (secondOutcome is LaunchOutcome.Started && !third.isNullOrBlank()) {
                handler.postDelayed({
                    launcher.launchPackageInMode(third, WindowMode.FREEFORM, starter = this)
                    handler.postDelayed({ finishOnce() }, FINISH_GRACE_MS)
                }, THIRD_SETTLE_MS)
            } else {
                // Keep the caller's task alive for one more beat so the adjacent
                // request is not resolved against a finishing activity.
                handler.postDelayed({ finishOnce() }, FINISH_GRACE_MS)
            }
        }, VISIBILITY_SETTLE_MS)
    }

    private fun finishOnce() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LaunchPairActivity"
        private const val EXTRA_FIRST = "first"
        private const val EXTRA_SECOND = "second"
        private const val EXTRA_THIRD = "third"

        /**
         * Fallback if a ROM never reports the trampoline stopped. It is several
         * times longer than the normal path so it cannot race a healthy onStop.
         */
        private const val STOP_FALLBACK_MS = 1_800L

        /** Small settle after app 1 is visible, before the adjacent request. */
        private const val VISIBILITY_SETTLE_MS = 120L

        /** Settle after the adjacent request, before the optional floating third. */
        private const val THIRD_SETTLE_MS = 300L

        /** Lets the adjacent launch bind to this activity's token before it finishes. */
        private const val FINISH_GRACE_MS = 400L

        /**
         * Builds the trampoline intent. `NEW_TASK` plus the manifest's dedicated
         * `taskAffinity` gives the trampoline its own task — the launcher's task is
         * never touched. `CLEAR_TASK` is deliberately absent: on an OEM that
         * ignored the affinity it could clear the task it landed in, and finishing
         * itself already prevents trampoline stacking.
         */
        fun intent(context: Context, first: String, second: String, third: String? = null): Intent =
            Intent(context, LaunchPairActivity::class.java).apply {
                putExtra(EXTRA_FIRST, first)
                putExtra(EXTRA_SECOND, second)
                if (third != null) putExtra(EXTRA_THIRD, third)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
