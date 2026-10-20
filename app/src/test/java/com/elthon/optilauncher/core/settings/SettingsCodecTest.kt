package com.elthon.optilauncher.core.settings

import com.elthon.optilauncher.core.island.IslandPage
import com.elthon.optilauncher.core.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun `missing settings produce documented defaults`() {
        val state = SettingsCodec.decode(null)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(IconTreatment.GRAYSCALE, state.iconTreatment)
        assertFalse(state.reducedMotion)
        assertTrue(state.suggestionsEnabled)
        assertFalse(state.onboardingComplete)
        assertEquals(IslandPage.APPS, state.lastPage)
        assertTrue(state.favorites.isEmpty())
        assertFalse(state.allowLandscape)
    }

    @Test
    fun `every field survives a round-trip`() {
        val state = SettingsState(
            themeMode = ThemeMode.DARK,
            iconTreatment = IconTreatment.MONO,
            reducedMotion = true,
            suggestionsEnabled = false,
            onboardingComplete = true,
            allowLandscape = true,
            homeRoleExplained = true,
            favorites = setOf("com.cam", "com.phone"),
            suggestionDismissedAt = 42L,
            lastPage = IslandPage.SETTINGS,
            historyEpoch = 7L
        )
        assertEquals(state, SettingsCodec.decode(SettingsCodec.encode(state)))
    }

    @Test
    fun `unknown enum values fall back instead of crashing`() {
        val state = SettingsCodec.decode("v=1|mode=SIDEWAYS|icons=PINK|page=NOPE")
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(IconTreatment.GRAYSCALE, state.iconTreatment)
        assertEquals(IslandPage.APPS, state.lastPage)
    }

    @Test
    fun `garbage input is treated as unset`() {
        assertEquals(SettingsState(), SettingsCodec.decode("this is not a settings blob"))
    }

    @Test
    fun `favorites are stored as a set`() {
        val state = SettingsState(favorites = setOf("com.b", "com.a"))
        val decoded = SettingsCodec.decode(SettingsCodec.encode(state))
        assertEquals(setOf("com.b", "com.a"), decoded.favorites)
        assertTrue(decoded.isFavorite("com.a"))
    }

    @Test
    fun `repository update persists through the store`() {
        val store = InMemoryKeyValueStore()
        val repo = SettingsRepository(store)

        val updated = repo.update { it.copy(themeMode = ThemeMode.LIGHT, onboardingComplete = true) }
        assertEquals(ThemeMode.LIGHT, updated.themeMode)
        assertTrue(updated.onboardingComplete)

        val reloaded = repo.load()
        assertEquals(ThemeMode.LIGHT, reloaded.themeMode)
        assertTrue(reloaded.onboardingComplete)
    }

    @Test
    fun `toggle favorite flips membership both ways`() {
        val repo = SettingsRepository(InMemoryKeyValueStore())
        assertTrue(repo.toggleFavorite("com.cam").isFavorite("com.cam"))
        assertFalse(repo.toggleFavorite("com.cam").isFavorite("com.cam"))
        assertTrue(repo.clearFavorites().favorites.isEmpty())
    }
}
