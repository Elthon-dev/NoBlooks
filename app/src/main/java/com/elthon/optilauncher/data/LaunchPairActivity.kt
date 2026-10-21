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
 * The sequence is deliberate: app 1, one short beat, app 2 adjacent. Firing both in
 * the same tick is what makes the flag get dropped, and the beat stays well inside
 * the platform's background-launch grace period. This is still a **request** — the
 * system can decline to split — so no caller claims a layout was achieved.
 */
class LaunchPairActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val first = intent.getStringExtra(EXTRA_FIRST)
        val second = intent.getStringExtra(EXTRA_SECOND)
        if (first.isNullOrBlank() || second.isNullOrBlank()) {
            finish()
            return
        }

        val launcher = AppLauncher(this)
        val firstOutcome = launcher.launchPackageInMode(first, WindowMode.NORMAL, starter = this)
        if (firstOutcome !is LaunchOutcome.Started) {
            Log.w(TAG, "pair abandoned: first app $firstOutcome")
            finish()
            return
        }

        handler.postDelayed({
            launcher.launchPackageInMode(second, WindowMode.SPLIT, starter = this)
            // Keep the caller's task alive for one more beat so the adjacent
            // request is not resolved against a finishing activity.
            handler.postDelayed({ finish() }, FINISH_GRACE_MS)
        }, PAIR_GAP_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LaunchPairActivity"
        private const val EXTRA_FIRST = "first"
        private const val EXTRA_SECOND = "second"

        /**
         * One visible beat between the full-screen app and the adjacent request.
         * 700ms is long enough for the first task to be focused, short enough to
         * stay inside the background-activity-launch grace window.
         */
        const val PAIR_GAP_MS = 700L

        /** Lets the adjacent launch bind to this activity's token before it finishes. */
        private const val FINISH_GRACE_MS = 400L

        /**
         * Builds the trampoline intent. `NEW_TASK` plus the manifest's dedicated
         * `taskAffinity` gives the trampoline its own task — the launcher's task is
         * never touched. `CLEAR_TASK` is deliberately absent: on an OEM that
         * ignored the affinity it could clear the task it landed in, and finishing
         * itself already prevents trampoline stacking.
         */
        fun intent(context: Context, first: String, second: String): Intent =
            Intent(context, LaunchPairActivity::class.java)
                .putExtra(EXTRA_FIRST, first)
                .putExtra(EXTRA_SECOND, second)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
