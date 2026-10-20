package com.elthon.optilauncher.core.apps

import java.text.Normalizer
import java.util.Locale

/**
 * A launchable application as the core layer sees it: plain strings only, so
 * search/sort/favourite logic stays unit-testable without Android types.
 */
data class AppRef(
    val packageName: String,
    val label: String,
    /** "package/class" of the launchable activity, as reported by PackageManager. */
    val component: String,
    val isSystem: Boolean = false
) {
    val className: String
        get() = component.substringAfter('/', component)
}

data class SearchResult(
    val apps: List<AppRef>,
    val query: String,
    val isEmpty: Boolean,
    val reason: EmptyReason?
) {
    enum class EmptyReason { NO_MATCHES, NO_APPS }
}

/**
 * Instant filtering for the island's search field.
 *
 * Rule-based tiers, most specific first (same hierarchy the big launchers
 * settled on after years of complaints):
 *
 *  0. exact label
 *  1. label prefix
 *  2. any word of the label starts with the query   ("maps" → Google Maps)
 *  3. word initials                                  ("gm"   → Google Maps)
 *  4. every query token prefixes a label word        ("goo ma")
 *  5. label substring
 *  6. package prefix, 7. package substring
 *
 * Ties inside a tier are broken by [priority] (habit rank), then alphabetically.
 * That is what makes habit-first survive typing: a familiar app wins a draw
 * without ever outranking a genuinely better textual match.
 *
 * Matching is accent- and case-insensitive. Empty queries return the input in
 * its original order; callers pass an already-sorted list.
 */
object AppSearch {

    private val SEPARATORS = charArrayOf(' ', '-', '_', '.', '·', '(', ')', '/', '\'', ',')

    fun filter(
        apps: List<AppRef>,
        rawQuery: String,
        priority: Map<String, Int> = emptyMap()
    ): List<AppRef> {
        val query = normalize(rawQuery.trim())
        if (query.isEmpty()) return apps
        val tokens = query.split(*SEPARATORS).filter { it.isNotEmpty() }
        // One canonical single-space form, so "goo  ma" and "goo-ma" behave alike.
        val flat = tokens.joinToString(" ")

        val scored = ArrayList<ScoredApp>(apps.size)
        for (app in apps) {
            val label = normalize(app.label)
            val tier = tierOf(label, tokens, flat, app.packageName) ?: continue
            scored.add(ScoredApp(app, tier, priority[app.packageName] ?: Int.MAX_VALUE, label))
        }
        scored.sortWith(
            compareBy(
                { it.tier },
                { it.priority },
                { it.label },
                { it.app.packageName }
            )
        )
        return scored.map { it.app }
    }

    fun search(
        apps: List<AppRef>,
        rawQuery: String,
        priority: Map<String, Int> = emptyMap()
    ): SearchResult {
        val query = rawQuery.trim()
        if (apps.isEmpty()) {
            return SearchResult(emptyList(), query, isEmpty = true, reason = SearchResult.EmptyReason.NO_APPS)
        }
        val matches = filter(apps, query, priority)
        return SearchResult(
            apps = matches,
            query = query,
            isEmpty = matches.isEmpty(),
            reason = if (matches.isEmpty()) SearchResult.EmptyReason.NO_MATCHES else null
        )
    }

    /** Case-insensitive alphabetical order used for the "all apps" fallback. */
    fun alphabetical(apps: List<AppRef>): List<AppRef> =
        apps.sortedWith(
            compareBy<AppRef> { app -> normalize(app.label) }
                .thenBy { app -> app.packageName }
        )

    private fun tierOf(label: String, tokens: List<String>, query: String, rawPackage: String): Int? {
        val words = label.split(*SEPARATORS).filter { it.isNotEmpty() }
        val pkg = normalize(rawPackage)
        return when {
            label == query -> 0
            label.startsWith(query) -> 1
            words.any { it.startsWith(query) } -> 2
            words.size > 1 && initials(words).startsWith(query) -> 3
            tokens.size > 1 && tokens.all { token -> words.any { it.startsWith(token) } } -> 4
            label.contains(query) -> 5
            pkg.startsWith(query) -> 6
            pkg.contains(query) -> 7
            else -> null
        }
    }

    private fun initials(words: List<String>): String = buildString(words.size) {
        for (word in words) append(word.first())
    }

    /**
     * Lowercase plus diacritic folding. The ASCII fast path matters: most labels
     * never touch the Unicode normalizer, and this runs per app per keystroke.
     */
    internal fun normalize(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        var ascii = true
        for (ch in lower) {
            if (ch.code >= 0x80) {
                ascii = false
                break
            }
        }
        if (ascii) return lower
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) out.append(ch)
        }
        return out.toString()
    }

    private class ScoredApp(
        val app: AppRef,
        val tier: Int,
        val priority: Int,
        val label: String
    )
}
