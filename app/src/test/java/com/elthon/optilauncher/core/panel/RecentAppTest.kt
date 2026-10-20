package com.elthon.optilauncher.core.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentAppTest {

    private fun r(pkg: String, time: Long, live: Boolean = false) =
        RecentApp(packageName = pkg, label = pkg, lastUsedMs = time, liveProcess = live)

    @Test
    fun `forPanel orders MRU and drops self and system ui`() {
        val list = listOf(
            r("com.a", 100),
            r("com.b", 300),
            r("com.elthon.optilauncher", 500),
            r(SYSTEM_UI_PACKAGE, 400),
            r("com.c", 200)
        )
        val out = list.forPanel(selfPackage = "com.elthon.optilauncher")
        assertEquals(listOf("com.b", "com.c", "com.a"), out.map { it.packageName })
    }

    @Test
    fun `forPanel de-dupes repeated packages keeping the newest`() {
        val list = listOf(r("com.a", 100), r("com.a", 900), r("com.b", 500))
        val out = list.forPanel(selfPackage = "self")
        assertEquals(listOf("com.a", "com.b"), out.map { it.packageName })
        assertEquals(900L, out.first().lastUsedMs)
    }

    @Test
    fun `forPanel respects the cap`() {
        val list = (1..20).map { r("com.pkg$it", it.toLong()) }
        assertEquals(12, list.forPanel(selfPackage = "self").size)
        assertEquals(3, list.forPanel(selfPackage = "self", max = 3).size)
    }

    @Test
    fun `live process flag survives ordering`() {
        val list = listOf(r("com.a", 100, live = true), r("com.b", 300))
        val out = list.forPanel(selfPackage = "self")
        assertTrue(out.first { it.packageName == "com.a" }.liveProcess)
    }
}
