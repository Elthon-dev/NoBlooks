package com.elthon.optilauncher.core.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandStateTest {

    @Test
    fun `toggle expands then collapses and never corrupts state`() {
        val expanded = IslandState().reduce(IslandEvent.Toggle)
        assertTrue(expanded.isExpanded)
        assertEquals(IslandOrigin.TAP, expanded.origin)

        val collapsed = expanded.reduce(IslandEvent.Toggle)
        assertFalse(collapsed.isExpanded)
    }

    @Test
    fun `selecting a page preserves expansion`() {
        val expanded = IslandState().reduce(IslandEvent.Toggle)
        val onHabits = expanded.reduce(IslandEvent.SelectPage(IslandPage.HABITS))
        assertTrue("page switch must not collapse the island", onHabits.isExpanded)
        assertEquals(IslandPage.HABITS, onHabits.page)

        val collapsed = IslandState().reduce(IslandEvent.SelectPage(IslandPage.WALLPAPER))
        assertFalse(collapsed.isExpanded)
        assertEquals(IslandPage.WALLPAPER, collapsed.page)
    }

    @Test
    fun `back and outside tap on a collapsed island are no-ops`() {
        val start = IslandState()
        assertEquals(start, start.reduce(IslandEvent.Back))
        assertEquals(start, start.reduce(IslandEvent.OutsideTap))
    }

    @Test
    fun `back collapses an expanded island`() {
        val expanded = IslandState().reduce(IslandEvent.Toggle)
        assertFalse(expanded.reduce(IslandEvent.Back).isExpanded)
        assertFalse(expanded.reduce(IslandEvent.OutsideTap).isExpanded)
    }

    @Test
    fun `search gesture expands into apps with focus`() {
        val state = IslandState().reduce(IslandEvent.SearchGesture)
        assertTrue(state.isExpanded)
        assertEquals(IslandPage.APPS, state.page)
        assertTrue(state.searchFocused)
        assertEquals(IslandOrigin.SEARCH_GESTURE, state.origin)

        // Losing focus must keep the island open and the query intact.
        val typed = state.reduce(IslandEvent.QueryChanged("cam"))
        val blurred = typed.reduce(IslandEvent.SearchFocusLost)
        assertTrue(blurred.isExpanded)
        assertEquals("cam", blurred.query)
        assertFalse(blurred.searchFocused)
    }

    @Test
    fun `plain tap does not focus search`() {
        val state = IslandState().reduce(IslandEvent.Toggle)
        assertFalse(state.searchFocused)
    }

    @Test
    fun `launching an app dismisses the island`() {
        val expanded = IslandState().reduce(IslandEvent.Toggle)
            .reduce(IslandEvent.QueryChanged("ma"))
        val after = expanded.reduce(IslandEvent.AppLaunched)
        assertFalse(after.isExpanded)
    }

    @Test
    fun `entering home resets transient search state`() {
        val busy = IslandState()
            .reduce(IslandEvent.SearchGesture)
            .reduce(IslandEvent.QueryChanged("x"))
        val home = busy.reduce(IslandEvent.EnterHome)
        assertFalse(home.isExpanded)
        assertEquals("", home.query)
        assertFalse(home.searchFocused)
        assertEquals(IslandOrigin.RESTORED, home.origin)
    }

    @Test
    fun `repeating expand and collapse converges`() {
        val once = IslandState().reduce(IslandEvent.Expand)
        assertEquals(once, once.reduce(IslandEvent.Expand))
        val collapsed = once.reduce(IslandEvent.Collapse)
        assertEquals(collapsed, collapsed.reduce(IslandEvent.Collapse))
    }

    @Test
    fun `page order drives transition direction`() {
        assertEquals(0, IslandPage.APPS.index())
        assertEquals(1, IslandPage.HABITS.index())
        assertEquals(2, IslandPage.WALLPAPER.index())
        assertEquals(3, IslandPage.SETTINGS.index())
        assertEquals(4, IslandPage.ordered.size)
    }

    @Test
    fun `suggestion dismissal sticks across other events`() {
        val dismissed = IslandState().reduce(IslandEvent.DismissSuggestion(true))
        assertTrue(dismissed.suggestionDismissed)
        assertTrue(dismissed.reduce(IslandEvent.SelectPage(IslandPage.HABITS)).suggestionDismissed)
    }
}
