package com.elthon.optilauncher.core.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelStateTest {

    @Test
    fun `three finger tap reveals a hidden panel`() {
        val s = PanelState().reduce(PanelEvent.ThreeFingerTap)
        assertTrue(s.visible)
        assertEquals(PanelOrigin.THREE_FINGER, s.origin)
    }

    @Test
    fun `three finger tap keeps a visible panel visible`() {
        val shown = PanelState(visible = true, origin = PanelOrigin.TAP)
        val s = shown.reduce(PanelEvent.ThreeFingerTap)
        assertTrue(s.visible)
        assertEquals(PanelOrigin.THREE_FINGER, s.origin)
    }

    @Test
    fun `toggle flips visibility`() {
        val hidden = PanelState()
        assertTrue(hidden.reduce(PanelEvent.Toggle).visible)
        assertFalse(hidden.reduce(PanelEvent.Toggle).reduce(PanelEvent.Toggle).visible)
    }

    @Test
    fun `reveal is idempotent when already visible`() {
        val visible = PanelState(visible = true, origin = PanelOrigin.THREE_FINGER)
        val s = visible.reduce(PanelEvent.Reveal)
        assertTrue(s.visible)
        // Reveal must not stamp TAP over a gesture origin.
        assertEquals(PanelOrigin.THREE_FINGER, s.origin)
    }

    @Test
    fun `hide and auto-hide are idempotent`() {
        val hidden = PanelState()
        assertEquals(hidden, hidden.reduce(PanelEvent.Hide))
        assertEquals(hidden, hidden.reduce(PanelEvent.AutoHideElapsed))

        val visible = PanelState(visible = true)
        assertFalse(visible.reduce(PanelEvent.Hide).visible)
        assertFalse(visible.reduce(PanelEvent.AutoHideElapsed).visible)
    }

    @Test
    fun `auto-hide does not disturb face or arming`() {
        val onRecents = PanelState(visible = true, face = PanelFace.RECENTS, gestureArmed = true)
        val s = onRecents.reduce(PanelEvent.AutoHideElapsed)
        assertFalse(s.visible)
        assertEquals(PanelFace.RECENTS, s.face)
        assertTrue(s.gestureArmed)
    }

    @Test
    fun `show face implies visibility`() {
        val s = PanelState().reduce(PanelEvent.ShowFace(PanelFace.NOTIFICATIONS))
        assertTrue(s.visible)
        assertTrue(s.isNotifications)
    }

    @Test
    fun `home system-recents and system-notifications all land visible=false`() {
        val open = PanelState(visible = true, face = PanelFace.RECENTS)
        assertFalse(open.reduce(PanelEvent.Home).visible)
        assertFalse(open.reduce(PanelEvent.SystemRecents).visible)
        assertFalse(open.reduce(PanelEvent.SystemNotifications).visible)
        // Leaving via a system action resets the face so the panel reopens on overview.
        assertEquals(PanelFace.OVERVIEW, open.reduce(PanelEvent.SystemRecents).face)
    }

    @Test
    fun `arming never changes visibility or face`() {
        val hidden = PanelState()
        val a = hidden.reduce(PanelEvent.SetGestureArmed(true))
        assertTrue(a.gestureArmed)
        assertFalse(a.visible)
        assertEquals(PanelFace.OVERVIEW, a.face)

        val disarmed = a.reduce(PanelEvent.SetGestureArmed(false))
        assertFalse(disarmed.gestureArmed)
    }

    @Test
    fun `enter home lands hidden overview and clears the verdict`() {
        val messy = PanelState(
            visible = true,
            face = PanelFace.RECENTS,
            origin = PanelOrigin.THREE_FINGER,
            lastStop = StopVerdict.STOPPED
        )
        val s = messy.reduce(PanelEvent.EnterHome)
        assertFalse(s.visible)
        assertEquals(PanelFace.OVERVIEW, s.face)
        assertEquals(PanelOrigin.RESTORED, s.origin)
        assertNull(s.lastStop)
    }

    @Test
    fun `stop verdict round-trips and clears`() {
        val s = PanelState().reduce(PanelEvent.SetStopVerdict(StopVerdict.REVIVED))
        assertEquals(StopVerdict.REVIVED, s.lastStop)
        assertNull(s.reduce(PanelEvent.SetStopVerdict(null)).lastStop)
    }

    @Test
    fun `face toggles flip between overview and the given face`() {
        val overview = PanelState(visible = true)
        assertEquals(PanelFace.RECENTS, overview.toggleRecentsFace().face)
        assertEquals(PanelFace.OVERVIEW, overview.toggleRecentsFace().toggleRecentsFace().face)
        assertEquals(PanelFace.NOTIFICATIONS, overview.toggleNotificationsFace().face)
    }

    @Test
    fun `repeating every event converges instead of corrupting`() {
        var s: PanelState = PanelState().reduce(PanelEvent.SetGestureArmed(true))
        val first = s.reduce(PanelEvent.ThreeFingerTap)
        s = first.reduce(PanelEvent.ThreeFingerTap).reduce(PanelEvent.ThreeFingerTap)
        assertEquals(first, s)
    }

    @Test
    fun `auto hide constant is the five second promise`() {
        assertEquals(5_000L, PANEL_AUTO_HIDE_MS)
    }

    @Test
    fun `collapse to pill keeps the panel visible and returns to overview`() {
        val onHabits = PanelState(visible = true, face = PanelFace.HABITS)
        val s = onHabits.reduce(PanelEvent.CollapseToPill)
        assertTrue(s.visible)
        assertEquals(PanelFace.OVERVIEW, s.face)
    }

    @Test
    fun `interacting suspends the auto-hide timer`() {
        val held = PanelState(visible = true).reduce(PanelEvent.SetInteracting(true))
        assertTrue(held.interacting)
        // The idle timer must not take a panel away from a finger that is on it.
        val afterTimer = held.reduce(PanelEvent.AutoHideElapsed)
        assertTrue(afterTimer.visible)
        assertEquals(held, afterTimer)

        // Once released, the timer works again.
        val released = afterTimer.reduce(PanelEvent.SetInteracting(false))
        assertFalse(released.reduce(PanelEvent.AutoHideElapsed).visible)
    }

    @Test
    fun `hide clears the interacting flag`() {
        val held = PanelState(visible = true, interacting = true)
        assertFalse(held.reduce(PanelEvent.Hide).interacting)
    }

    @Test
    fun `habits toggle flips between overview and habits`() {
        val overview = PanelState(visible = true)
        assertEquals(PanelFace.HABITS, overview.toggleHabitsFace().face)
        assertEquals(PanelFace.OVERVIEW, overview.toggleHabitsFace().toggleHabitsFace().face)
    }

    @Test
    fun `face open is only true away from overview`() {
        assertFalse(PanelState().isFaceOpen)
        assertTrue(PanelState(face = PanelFace.HABITS).isFaceOpen)
        assertTrue(PanelState(face = PanelFace.RECENTS).isFaceOpen)
    }
}
