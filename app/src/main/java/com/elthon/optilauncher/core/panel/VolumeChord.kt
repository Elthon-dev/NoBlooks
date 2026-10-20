package com.elthon.optilauncher.core.panel

/**
 * The "press both volume keys" reveal chord, as a pure state machine.
 *
 * This lives in `core/` rather than inside the accessibility service because the
 * timing rules are the whole feature and they deserve to be tested without a
 * device: a human never presses two buttons in the same millisecond, so we accept
 * a small tolerance; a held chord must fire exactly once; and a single key pressed
 * twice in a row must never be mistaken for a chord.
 *
 * It is deliberately honest about the platform truth this ships on top of:
 * Android already uses "hold both volume keys" as the global accessibility
 * shortcut, and only one accessibility service may filter key events at a time.
 * So this trigger is **best-effort**: it announces a reveal when it sees the
 * chord, and it never claims the chord will always reach us.
 *
 * The machine is fed raw key transitions with a monotonic timestamp and returns
 * whether *this transition* completed a fresh chord.
 */
class VolumeChord(
    /** Max gap between the two key-downs for them to count as "together". */
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    /** Minimum gap between two revealed chords, so a held pair fires once. */
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS
) {

    private var upDownAt = 0L
    private var downDownAt = 0L
    private var lastFiredAt = Long.MIN_VALUE

    /**
     * Records a key transition and returns true when it completes a new chord.
     *
     * @param key [VolumeKey.UP] or [VolumeKey.DOWN].
     * @param down true for ACTION_DOWN, false for ACTION_UP.
     * @param nowMs monotonic clock reading (e.g. `SystemClock.uptimeMillis()`).
     */
    fun onKey(key: VolumeKey, down: Boolean, nowMs: Long): Boolean {
        when (key) {
            VolumeKey.UP -> upDownAt = if (down) nowMs else 0L
            VolumeKey.DOWN -> downDownAt = if (down) nowMs else 0L
        }

        if (upDownAt == 0L || downDownAt == 0L) return false
        val withinWindow = kotlin.math.abs(upDownAt - downDownAt) <= windowMs
        if (!withinWindow) return false

        // The same physical press repeats the DOWN event; without this, holding
        // both keys would reveal over and over.
        if (nowMs - lastFiredAt < debounceMs) return false
        lastFiredAt = nowMs
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 150L
        const val DEFAULT_DEBOUNCE_MS = 1_500L
    }
}

/** Which volume key a transition belongs to. */
enum class VolumeKey { UP, DOWN }
