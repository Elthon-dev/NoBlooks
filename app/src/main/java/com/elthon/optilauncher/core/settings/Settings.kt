package com.elthon.optilauncher.core.settings

import com.elthon.optilauncher.core.island.IslandPage
import com.elthon.optilauncher.core.theme.ThemeMode

/**
 * Persistence abstraction. The production implementation is backed by
 * SharedPreferences (data/KeyValueStore.kt); tests use the in-memory one below,
 * which is why favourites/settings survive a round trip in unit tests too.
 */
interface KeyValueStore {
    fun readString(key: String): String?
    fun writeString(key: String, value: String?)
}

class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    private val values = LinkedHashMap(initial)

    override fun readString(key: String): String? = synchronized(values) { values[key] }

    override fun writeString(key: String, value: String?) {
        synchronized(values) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    fun snapshot(): Map<String, String> = synchronized(values) { values.toMap() }
}

enum class IconTreatment { ORIGINAL, GRAYSCALE, MONO }

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val iconTreatment: IconTreatment = IconTreatment.GRAYSCALE,
    val reducedMotion: Boolean = false,
    val suggestionsEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val homeRoleExplained: Boolean = false,
    /** Pinned/favorite package names. */
    val favorites: Set<String> = emptySet(),
    /** When the current habit suggestion was dismissed (0 = not dismissed). */
    val suggestionDismissedAt: Long = 0L,
    val lastPage: IslandPage = IslandPage.APPS,
    /** Bumped when the user asks to wipe local history, so caches are dropped too. */
    val historyEpoch: Long = 0L
) {
    fun isFavorite(packageName: String): Boolean = packageName in favorites
}

/**
 * Compact, hand-rolled encoding (no JSON dependency) that is deliberately
 * forgiving: unknown keys are ignored and malformed values fall back to defaults,
 * so a corrupt preference file can never crash the launcher.
 */
object SettingsCodec {
    const val VERSION = 1
    private const val SEPARATOR = "|"
    private const val PAIR = "="

    fun encode(state: SettingsState): String = buildString {
        append("v").append(PAIR).append(VERSION).append(SEPARATOR)
        append("mode").append(PAIR).append(state.themeMode.name).append(SEPARATOR)
        append("icons").append(PAIR).append(state.iconTreatment.name).append(SEPARATOR)
        append("rm").append(PAIR).append(if (state.reducedMotion) 1 else 0).append(SEPARATOR)
        append("sug").append(PAIR).append(if (state.suggestionsEnabled) 1 else 0).append(SEPARATOR)
        append("ob").append(PAIR).append(if (state.onboardingComplete) 1 else 0).append(SEPARATOR)
        append("hr").append(PAIR).append(if (state.homeRoleExplained) 1 else 0).append(SEPARATOR)
        append("fav").append(PAIR).append(state.favorites.sorted().joinToString(",")).append(SEPARATOR)
        append("dis").append(PAIR).append(state.suggestionDismissedAt).append(SEPARATOR)
        append("page").append(PAIR).append(state.lastPage.name).append(SEPARATOR)
        append("he").append(PAIR).append(state.historyEpoch)
    }

    fun decode(raw: String?): SettingsState {
        val defaults = SettingsState()
        if (raw.isNullOrBlank()) return defaults
        val map = HashMap<String, String>()
        for (pair in raw.split(SEPARATOR)) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf(PAIR)
            if (idx <= 0) continue
            map[pair.substring(0, idx)] = pair.substring(idx + 1)
        }
        if (map.isEmpty()) return defaults

        return SettingsState(
            themeMode = map["mode"]?.toEnumOrNull<ThemeMode>() ?: defaults.themeMode,
            iconTreatment = map["icons"]?.toEnumOrNull<IconTreatment>() ?: defaults.iconTreatment,
            reducedMotion = map["rm"]?.toBooleanStrictOrNull() == true || map["rm"] == "1",
            suggestionsEnabled = map["sug"]?.let { it != "0" } ?: defaults.suggestionsEnabled,
            onboardingComplete = map["ob"] == "1",
            homeRoleExplained = map["hr"] == "1",
            favorites = map["fav"]
                ?.split(",")
                ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
                ?.toSet()
                ?: defaults.favorites,
            suggestionDismissedAt = map["dis"]?.toLongOrNull() ?: 0L,
            lastPage = map["page"]?.toEnumOrNull<IslandPage>() ?: defaults.lastPage,
            historyEpoch = map["he"]?.toLongOrNull() ?: 0L
        )
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        enumValues<T>().firstOrNull { it.name == this }
}

/** Read/write facade over a [KeyValueStore]. */
class SettingsRepository(private val store: KeyValueStore) {

    fun load(): SettingsState = SettingsCodec.decode(store.readString(KEY))

    fun save(state: SettingsState) {
        store.writeString(KEY, SettingsCodec.encode(state))
    }

    fun update(transform: (SettingsState) -> SettingsState): SettingsState {
        val next = transform(load())
        save(next)
        return next
    }

    fun toggleFavorite(packageName: String): SettingsState =
        update { s ->
            val next = if (packageName in s.favorites) s.favorites - packageName else s.favorites + packageName
            s.copy(favorites = next)
        }

    fun clearFavorites(): SettingsState = update { it.copy(favorites = emptySet()) }

    companion object {
        const val KEY = "noblooks_settings_v1"
    }
}
