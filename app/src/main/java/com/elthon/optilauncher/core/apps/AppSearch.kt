package com.elthon.optilauncher.core.apps

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
 * Instant, allocation-light filtering for the island's search field.
 *
 * Ranking: label-prefix > label-contains > package-prefix > package-contains,
 * alphabetical inside each bucket. Empty/blank queries return the input order
 * (callers pass an already-sorted alphabetical list).
 */
object AppSearch {

    fun filter(apps: List<AppRef>, rawQuery: String): List<AppRef> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return apps
        val lower = query.lowercase()

        val scored = ArrayList<Pair<Int, AppRef>>(apps.size)
        for (app in apps) {
            val label = app.label.lowercase()
            val pkg = app.packageName.lowercase()
            val rank = when {
                label == lower -> 0
                label.startsWith(lower) -> 1
                label.contains(lower) -> 2
                pkg.startsWith(lower) -> 3
                pkg.contains(lower) -> 4
                app.className.lowercase().contains(lower) -> 5
                else -> continue
            }
            scored.add(rank to app)
        }
        return scored
            .sortedWith(compareBy({ it.first }, { it.second.label.lowercase() }, { it.second.packageName }))
            .map { it.second }
    }

    fun search(apps: List<AppRef>, rawQuery: String): SearchResult {
        val query = rawQuery.trim()
        if (apps.isEmpty()) {
            return SearchResult(emptyList(), query, isEmpty = true, reason = SearchResult.EmptyReason.NO_APPS)
        }
        val matches = filter(apps, query)
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
            compareBy<AppRef> { app -> app.label.lowercase(java.util.Locale.ROOT) }
                .thenBy { app -> app.packageName }
        )
}
