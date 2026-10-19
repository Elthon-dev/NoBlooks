package com.elthon.optilauncher.core.util

/**
 * Tiny insertion-ordered LRU with a hard entry cap.
 *
 * Used for transformed icon bitmaps so a 3 GB device never keeps every app icon
 * decoded at once, and for other short-lived caches. Pure Kotlin + unit testable.
 */
class BoundedCache<K : Any, V : Any>(private val maxSize: Int) {

    init {
        require(maxSize > 0) { "maxSize must be > 0" }
    }

    private val map = LinkedHashMap<K, V>(16, 0.75f, /* accessOrder = */ true)

    val size: Int get() = map.size

    fun get(key: K): V? = synchronized(map) { map[key] }

    fun put(key: K, value: V): V? = synchronized(map) {
        val previous = map.put(key, value)
        evictIfNeeded()
        previous
    }

    fun remove(key: K): V? = synchronized(map) { map.remove(key) }

    fun clear() = synchronized(map) { map.clear() }

    fun keys(): List<K> = synchronized(map) { map.keys.toList() }

    private fun evictIfNeeded() {
        while (map.size > maxSize) {
            val eldest = map.entries.iterator()
            if (!eldest.hasNext()) return
            eldest.next()
            eldest.remove()
        }
    }
}
