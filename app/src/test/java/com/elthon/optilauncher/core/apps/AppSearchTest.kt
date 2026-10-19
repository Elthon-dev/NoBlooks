package com.elthon.optilauncher.core.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {

    private val apps = listOf(
        AppRef("com.android.camera", "Camera", "com.android.camera/.Main"),
        AppRef("com.android.phone", "Phone", "com.android.phone/.Dialer"),
        AppRef("com.google.photos", "Photos", "com.google.photos/.Main"),
        AppRef("com.spotify.music", "Spotify", "com.spotify.music/.Main")
    )

    @Test
    fun `blank query returns the input in its original order`() {
        assertEquals(apps, AppSearch.filter(apps, "   "))
    }

    @Test
    fun `label prefix matches rank above label contains`() {
        val result = AppSearch.filter(apps, "pho")
        assertEquals(listOf("Phone", "Photos"), result.map { it.label })
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(listOf("Spotify"), AppSearch.filter(apps, "SPOT").map { it.label })
    }

    @Test
    fun `package name matches are found when the label does not match`() {
        assertEquals(listOf("Camera"), AppSearch.filter(apps, "android.camera").map { it.label })
    }

    @Test
    fun `no matches reports NO_MATCHES with the query echoed`() {
        val result = AppSearch.search(apps, "zzz")
        assertTrue(result.isEmpty)
        assertEquals(SearchResult.EmptyReason.NO_MATCHES, result.reason)
        assertEquals("zzz", result.query)
    }

    @Test
    fun `no installed apps reports NO_APPS even for a blank query`() {
        val result = AppSearch.search(emptyList(), "")
        assertTrue(result.isEmpty)
        assertEquals(SearchResult.EmptyReason.NO_APPS, result.reason)
    }

    @Test
    fun `a successful search has no reason and is not empty`() {
        val result = AppSearch.search(apps, "cam")
        assertFalse(result.isEmpty)
        assertNull(result.reason)
        assertEquals(listOf("Camera"), result.apps.map { it.label })
    }

    @Test
    fun `alphabetical sorts case-insensitively`() {
        val shuffled = apps.reversed()
        val sorted = AppSearch.alphabetical(shuffled)
        assertEquals(listOf("Camera", "Phone", "Photos", "Spotify"), sorted.map { it.label })
    }

    @Test
    fun `className is derived from the component string`() {
        assertEquals(".Main", apps.first().className)
    }
}
