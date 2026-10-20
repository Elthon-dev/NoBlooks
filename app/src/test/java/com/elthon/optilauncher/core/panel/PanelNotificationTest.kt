package com.elthon.optilauncher.core.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelNotificationTest {

    private fun n(
        key: String,
        pkg: String = "com.example",
        time: Long = 1000L,
        ongoing: Boolean = false,
        clearable: Boolean = true,
        body: String? = "hello"
    ) = PanelNotification(
        key = key,
        packageName = pkg,
        title = "Title",
        body = body,
        lines = emptyList(),
        postTimeMs = time,
        isOngoing = ongoing,
        isClearable = clearable,
        canReply = false
    )

    @Test
    fun `ongoing notifications are not missed candidates`() {
        assertFalse(n("a", ongoing = true).isMissedCandidate)
        assertTrue(n("b").isMissedCandidate)
    }

    @Test
    fun `forPanel drops ongoing and non-clearable and sorts newest first`() {
        val list = listOf(
            n("a", pkg = "com.a", time = 100),
            n("b", pkg = "com.b", time = 300),
            n("c", pkg = "com.c", time = 200, ongoing = true),
            n("d", pkg = "com.d", time = 400, clearable = false)
        )
        val out = list.forPanel()
        assertEquals(listOf("b", "a"), out.map { it.key })
    }

    @Test
    fun `preview falls back to first line then empty`() {
        assertEquals("hello", n("a").preview)
        val noBody = n("b", body = null).copy(lines = listOf("line one", "line two"))
        assertEquals("line one", noBody.preview)
        assertEquals("", n("c", body = null).preview)
    }

    @Test
    fun `packages are newest first and distinct`() {
        val list = listOf(
            n("a", pkg = "com.a", time = 100),
            n("b", pkg = "com.b", time = 300),
            n("c", pkg = "com.a", time = 200)
        )
        assertEquals(listOf("com.b", "com.a"), list.packages())
    }
}
