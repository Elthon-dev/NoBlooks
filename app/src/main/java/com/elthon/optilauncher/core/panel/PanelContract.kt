package com.elthon.optilauncher.core.panel

/**
 * Pure state model for the edge control panel ("the pill").
 *
 * The panel is the user's always-available jump bar: it appears over every app,
 * auto-hides when idle, and comes back on a three-finger tap. None of that is
 * testable on a device we don't have, so the interaction rules live here as a
 * plain function of (state, event) — exactly like the floating island's contract.
 *
 * It is deliberately explicit about what it is NOT:
 *  - It cannot force-stop another app on its own; that needs Shizuku (see
 *    `data/PanelController`). The contract only carries the intent.
 *  - It cannot promise a notification reply will land; the panel reports the
 *    attempt, and the platform decides.
 */

/** What the panel is currently showing, beyond the fixed action buttons. */
enum class PanelFace {
    /** The pill itself: four buttons only. */
    OVERVIEW,

    /** The app switcher list (tap to open, long-press to force-stop). */
    RECENTS,

    /** The missed-notification list. */
    NOTIFICATIONS,

    /** The user's habit-ranked apps, for agile jumping. */
    HABITS
}

/**
 * How the panel got into its current state. Diagnostics only — never rendered as
 * a claim to the user.
 */
enum class PanelOrigin { TAP, THREE_FINGER, RESTORED, ARMED }

data class PanelState(
    val visible: Boolean = false,
    val face: PanelFace = PanelFace.OVERVIEW,
    val origin: PanelOrigin = PanelOrigin.RESTORED,
    /**
     * True while the accessibility service is enabled and can deliver the
     * three-finger gesture. When false the panel still works, but only via a
     * tap on the pill — the UI must not pretend the gesture is live.
     */
    val gestureArmed: Boolean = false,
    /** Force-stop verdict for the most recent long-press, for a transient toast. */
    val lastStop: StopVerdict? = null,
    /**
     * True while the user is actively touching the pill/panel. The auto-hide timer
     * is suspended in this state: a panel that vanishes out from under a finger is
     * the opposite of "always there". It resets to false on release.
     */
    val interacting: Boolean = false
) {
    val isRecents: Boolean get() = face == PanelFace.RECENTS
    val isNotifications: Boolean get() = face == PanelFace.NOTIFICATIONS
    val isHabits: Boolean get() = face == PanelFace.HABITS
    /** True when the pill has morphed into a list rather than showing its buttons. */
    val isFaceOpen: Boolean get() = face != PanelFace.OVERVIEW
}

/**
 * The result of asking Shizuku to force-stop a package. Modelled as a verdict
 * rather than a boolean because "the shell ran but the app is still alive"
 * (ColorOS revival) is a real, distinct outcome worth telling the truth about.
 */
enum class StopVerdict {
    /** `am force-stop` ran and the process is gone. */
    STOPPED,

    /** The command ran, but the package was running again a moment later. */
    REVIVED,

    /** Shizuku is unavailable or not granted — nothing was attempted. */
    UNAVAILABLE,

    /** The shell refused (protected package, wrong user, etc.). */
    REFUSED,

    /** Could not read the result back, so we will not claim success. */
    UNVERIFIED
}

sealed interface PanelEvent {
    /** Three-finger tap delivered by the accessibility service. */
    data object ThreeFingerTap : PanelEvent

    /** The user tapped the pill directly. */
    data object Toggle : PanelEvent

    /** Show the panel without toggling it away if already visible. */
    data object Reveal : PanelEvent

    data object Hide : PanelEvent

    data class ShowFace(val face: PanelFace) : PanelEvent

    /**
     * The × button: morph a face back down to the pill, but keep the panel visible.
     * Distinct from [Hide], which takes the whole panel away.
     */
    data object CollapseToPill : PanelEvent

    /** True while a finger is down on the panel; suspends the auto-hide timer. */
    data class SetInteracting(val interacting: Boolean) : PanelEvent

    data object Home : PanelEvent

    /** Open the system overview / recents. */
    data object SystemRecents : PanelEvent

    /** Open the notification shade. */
    data object SystemNotifications : PanelEvent

    /** The 5-second idle timer elapsed. */
    data object AutoHideElapsed : PanelEvent

    /** Accessibility service connect/disconnect. */
    data class SetGestureArmed(val armed: Boolean) : PanelEvent

    data class SetStopVerdict(val verdict: StopVerdict?) : PanelEvent

    /** Onboarding finished: land hidden. */
    data object EnterHome : PanelEvent
}

/** How long the panel stays up with no interaction, in milliseconds. */
const val PANEL_AUTO_HIDE_MS = 5_000L

/**
 * The single source of truth for panel transitions.
 *
 * Invariants (all covered by `PanelStateTest`):
 *  - `Hide` and `AutoHideElapsed` are idempotent: hiding a hidden panel is a no-op.
 *  - A three-finger tap never leaves the panel hidden: it reveals or keeps it.
 *  - Showing a face implies visibility; you can never be on `RECENTS` while hidden.
 *  - `CollapseToPill` morphs back to the buttons but never hides the panel.
 *  - `AutoHideElapsed` is a no-op while `interacting` — the timer cannot take a
 *    panel away from a finger that is on it.
 *  - `EnterHome` always lands hidden on `OVERVIEW`, clearing the transient verdict.
 *  - Arming is orthogonal: it never changes visibility or face.
 */
fun PanelState.reduce(event: PanelEvent): PanelState = when (event) {
    PanelEvent.ThreeFingerTap ->
        if (visible) copy(origin = PanelOrigin.THREE_FINGER)
        else copy(visible = true, origin = PanelOrigin.THREE_FINGER)

    PanelEvent.Toggle -> if (visible) copy(visible = false, interacting = false) else copy(
        visible = true,
        origin = PanelOrigin.TAP
    )

    PanelEvent.Reveal -> if (visible) this else copy(visible = true, origin = PanelOrigin.TAP)

    PanelEvent.Hide -> if (!visible) this else copy(visible = false, interacting = false)

    PanelEvent.AutoHideElapsed ->
        if (!visible || interacting) this else copy(visible = false)

    is PanelEvent.ShowFace -> copy(visible = true, face = event.face)

    PanelEvent.CollapseToPill -> copy(face = PanelFace.OVERVIEW)

    is PanelEvent.SetInteracting -> copy(interacting = event.interacting)

    PanelEvent.Home -> copy(visible = false, face = PanelFace.OVERVIEW, interacting = false)

    PanelEvent.SystemRecents -> copy(visible = false, face = PanelFace.OVERVIEW)

    PanelEvent.SystemNotifications -> copy(visible = false, face = PanelFace.OVERVIEW)

    is PanelEvent.SetGestureArmed -> copy(gestureArmed = event.armed)

    is PanelEvent.SetStopVerdict -> copy(lastStop = event.verdict)

    PanelEvent.EnterHome -> copy(
        visible = false,
        face = PanelFace.OVERVIEW,
        origin = PanelOrigin.RESTORED,
        lastStop = null,
        interacting = false
    )
}

/** Cycles between the overview and the recents list. */
fun PanelState.toggleRecentsFace(): PanelState =
    copy(face = if (face == PanelFace.RECENTS) PanelFace.OVERVIEW else PanelFace.RECENTS)

/** Cycles between the overview and the notification list. */
fun PanelState.toggleNotificationsFace(): PanelState =
    copy(face = if (face == PanelFace.NOTIFICATIONS) PanelFace.OVERVIEW else PanelFace.NOTIFICATIONS)

/** Cycles between the overview and the habit-app list. */
fun PanelState.toggleHabitsFace(): PanelState =
    copy(face = if (face == PanelFace.HABITS) PanelFace.OVERVIEW else PanelFace.HABITS)
