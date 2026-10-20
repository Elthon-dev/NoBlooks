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

    private val rich = listOf(
        AppRef("com.google.maps", "Google Maps", "com.google.maps/.Main"),
        AppRef("com.google.android.gm", "Gmail", "com.google.android.gm/.Main"),
        AppRef("com.android.dialer", "Phone", "com.android.dialer/.Dialer"),
        AppRef("com.cafe.app", "Café", "com.cafe.app/.Main")
    )

    @Test
    fun `a word inside the label matches before a bare substring`() {
        // "maps" is a *word* of "Google Maps", so it outranks incidental substrings.
        assertEquals("Google Maps", AppSearch.filter(rich, "maps").first().label)
    }

    @Test
    fun `a label prefix outranks word initials`() {
        // "gmail" starts with "gm" (tier 1); "Google Maps" only matches on initials
        // (tier 3). Prefix wins, but both are found.
        assertEquals(
            listOf("Gmail", "Google Maps"),
            AppSearch.filter(rich, "gm").map { it.label }
        )
    }

    @Test
    fun `word initials outrank an incidental substring`() {
        val initialsApps = listOf(
            AppRef("com.google.maps", "Google Maps", "com.google.maps/.Main"),
            AppRef("com.logmein", "LogMeIn", "com.logmein/.Main")
        )
        // "gm" is Google Maps' initials (tier 3) and only a substring of LogMeIn (5).
        assertEquals(
            listOf("Google Maps", "LogMeIn"),
            AppSearch.filter(initialsApps, "gm").map { it.label }
        )
    }

    @Test
    fun `diacritics are folded`() {
        assertEquals(listOf("Café"), AppSearch.filter(rich, "cafe").map { it.label })
    }

    @Test
    fun `tokens can match separate words`() {
        assertEquals("Google Maps", AppSearch.filter(rich, "goo ma").first().label)
    }

    @Test
    fun `priority breaks ties inside a tier without outranking a better tier`() {
        val two = listOf(
            AppRef("a.alpha", "Photon", "a.alpha/.Main"),
            AppRef("b.beta", "Phone", "b.beta/.Main")
        )
        // Both are label prefixes; habit rank floats Photon up on a draw.
        assertEquals(
            listOf("Photon", "Phone"),
            AppSearch.filter(two, "pho", priority = mapOf("a.alpha" to 0)).map { it.label }
        )
        assertEquals(
            listOf("Phone", "Photon"),
            AppSearch.filter(two, "pho").map { it.label }
        )
    }

    @Test
    fun `an exact label still beats a habit-prioritised prefix`() {
        val two = listOf(
            AppRef("a.alpha", "Maps", "a.alpha/.Main"),
            AppRef("b.beta", "Google Maps", "b.beta/.Main")
        )
        // Tier 0 (exact) ignores the higher priority of a tier-1 match.
        assertEquals(
            "Maps",
            AppSearch.filter(two, "maps", priority = mapOf("b.beta" to 0)).first().label
        )
    }
}
