package com.elthon.optilauncher.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedCacheTest {

    @Test
    fun `put and get round-trip`() {
        val cache = BoundedCache<String, String>(4)
        assertNull(cache.put("a", "1"))
        assertEquals("1", cache.get("a"))
        assertEquals("1", cache.put("a", "2"))
        assertEquals("2", cache.get("a"))
        assertEquals(1, cache.size)
    }

    @Test
    fun `native eviction drops the least recently used entry`() {
        val cache = BoundedCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)

        // Touch "a" so "b" becomes the least recently used.
        assertEquals(1, cache.get("a"))

        cache.put("c", 3)
        assertEquals(2, cache.size)
        assertEquals(1, cache.get("a"))
        assertEquals(3, cache.get("c"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `explicit remove and clear work`() {
        val cache = BoundedCache<String, Int>(4)
        cache.put("a", 1)
        assertEquals(1, cache.remove("a"))
        assertTrue(cache.size == 0)
        cache.put("b", 2)
        cache.clear()
        assertEquals(0, cache.size)
        assertTrue(cache.keys().isEmpty())
    }

    @Test
    fun `a non-positive capacity is rejected`() {
        val error = runCatching { BoundedCache<String, Int>(0) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
