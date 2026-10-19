package com.elthon.optilauncher.core.island

/**
 * Pure state model for the signature floating island.
 *
 * Keeping this free of Compose means the interaction rules (outside tap, Back,
 * accessibility actions, page switching, repeated taps mid-animation) are unit
 * testable instead of only observable by hand on a device.
 */
enum class IslandPage(val label: String) {
    APPS("Apps"),
    HABITS("Habits"),
    WALLPAPER("Wallpaper"),
    SETTINGS("Settings");

    companion object {
        /** Order used for paged transitions and tab layout. */
        val ordered: List<IslandPage> = entries.toList()
    }
}

enum class Expansion { COLLAPSED, EXPANDED }

/** What launched the island into its current state — used only for diagnostics. */
enum class IslandOrigin { TAP, SEARCH_GESTURE, ACCESSIBILITY, RESTORED }

data class IslandState(
    val expansion: Expansion = Expansion.COLLAPSED,
    val page: IslandPage = IslandPage.APPS,
    val query: String = "",
    val searchFocused: Boolean = false,
    val origin: IslandOrigin = IslandOrigin.TAP,
    val suggestionDismissed: Boolean = false
) {
    val isExpanded: Boolean get() = expansion == Expansion.EXPANDED
}

sealed interface IslandEvent {
    /** Tap on the collapsed pill body. */
    data object Toggle : IslandEvent

    /** Tap on the pill's search affordance: expand straight into Apps with focus. */
    data object SearchGesture : IslandEvent

    data object Expand : IslandEvent
    data object Collapse : IslandEvent

    /** Tap on the wallpaper/scrim outside the panel. */
    data object OutsideTap : IslandEvent

    /** Android Back / predictive back. */
    data object Back : IslandEvent

    data class SelectPage(val page: IslandPage) : IslandEvent

    /** An app was chosen: perform the action, then dismiss. */
    data object AppLaunched : IslandEvent

    data class QueryChanged(val value: String) : IslandEvent
    data object SearchFocusLost : IslandEvent

    /** Onboarding finished or was skipped: land collapsed on the home screen. */
    data object EnterHome : IslandEvent

    data class DismissSuggestion(val dismissed: Boolean) : IslandEvent
}

/**
 * The single source of truth for island transitions.
 *
 * Invariants encoded here (all covered by tests):
 *  - Back and OutsideTap collapse only; they never navigate or crash on a collapsed state.
 *  - Selecting a page never changes expansion (switching pages must not collapse).
 *  - Repeating any event is safe: states converge instead of corrupting.
 *  - Expanding via the search affordance focuses the search field; plain tap does not.
 */
fun IslandState.reduce(event: IslandEvent): IslandState = when (event) {
    IslandEvent.Toggle -> when (expansion) {
        Expansion.COLLAPSED -> copy(
            expansion = Expansion.EXPANDED,
            origin = IslandOrigin.TAP,
            searchFocused = false
        )
        Expansion.EXPANDED -> collapse()
    }

    IslandEvent.SearchGesture -> copy(
        expansion = Expansion.EXPANDED,
        page = IslandPage.APPS,
        origin = IslandOrigin.SEARCH_GESTURE,
        searchFocused = true
    )

    IslandEvent.Expand -> if (isExpanded) this else copy(
        expansion = Expansion.EXPANDED,
        origin = IslandOrigin.ACCESSIBILITY,
        searchFocused = false
    )

    IslandEvent.Collapse -> if (expansion == Expansion.COLLAPSED) this else collapse()

    IslandEvent.OutsideTap -> if (expansion == Expansion.COLLAPSED) this else collapse()

    IslandEvent.Back -> if (expansion == Expansion.COLLAPSED) this else collapse()

    is IslandEvent.SelectPage ->
        // Page switches are explicitly expansion-preserving.
        copy(page = event.page, expansion = expansion)

    IslandEvent.AppLaunched -> collapse()

    is IslandEvent.QueryChanged -> copy(query = event.value)

    IslandEvent.SearchFocusLost -> if (searchFocused) copy(searchFocused = false) else this

    IslandEvent.EnterHome -> copy(
        expansion = Expansion.COLLAPSED,
        query = "",
        searchFocused = false,
        origin = IslandOrigin.RESTORED
    )

    is IslandEvent.DismissSuggestion -> copy(suggestionDismissed = event.dismissed)
}

fun IslandState.collapse(): IslandState =
    if (expansion == Expansion.COLLAPSED) this
    else copy(expansion = Expansion.COLLAPSED, searchFocused = false)

/** Tab index for the given page, for pager/transition math. */
fun IslandPage.index(): Int = IslandPage.ordered.indexOf(this)
