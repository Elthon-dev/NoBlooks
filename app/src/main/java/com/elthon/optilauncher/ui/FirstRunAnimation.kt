package com.elthon.optilauncher.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible

/**
 * One-shot first-run animation for the OPTI-LAUNCHER overlay.
 *
 * The dots "crawl" across the bar, the small label fades in, then the overlay hides
 * smoothly. Everything is pure View animations to keep it lightweight and testable in
 * spirit: it should never touch any of the scan/report writing paths.
 */
object FirstRunAnimation {

    fun play(
        overlay: View,
        mark: View,
        label: View,
        durationMs: Long = 900L,
        onFinished: (() -> Unit)? = null
    ) {
        if (overlay == null) {
            onFinished?.invoke()
            return
        }

        overlay.isVisible = true
        mark.isVisible = true
        label.isVisible = true

        mark.alpha = 0f
        label.alpha = 0f
        label.translationY = 8f

        val crawl = ObjectAnimator.ofFloat(mark, "translationX", -12f, 24f).apply {
            duration = durationMs / 2
            interpolator = DecelerateInterpolator()
        }
        val fadeMark = ObjectAnimator.ofFloat(mark, "alpha", 0f, 1f).apply {
            duration = durationMs / 3
        }
        val fadeLabel = ObjectAnimator.ofFloat(label, "alpha", 0f, 1f).apply {
            duration = durationMs / 3
            startDelay = durationMs / 4
        }
        val liftLabel = ObjectAnimator.ofFloat(label, "translationY", 8f, 0f).apply {
            duration = durationMs / 3
            startDelay = durationMs / 4
        }
        val hide = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
            duration = durationMs / 2
            startDelay = durationMs
        }

        val set = AnimatorSet().apply {
            playTogether(crawl, fadeMark, fadeLabel, liftLabel)
            play(hide).after(durationMs * 3 / 4)
        }

        set.start()

        Handler(Looper.getMainLooper()).postDelayed({
            overlay.isVisible = false
            onFinished?.invoke()
        }, durationMs * 2)
    }

    fun skip(overlay: View?) {
        if (overlay == null) return
        overlay.isVisible = false
    }
}
