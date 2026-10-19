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
    val origin: IslandOrigin = IslandOrigin.TAP,
    val suggestionDismissed: Boolean = false
) {
    val isExpanded: Boolean get() = expansion == Expansion.EXPANDED
}

sealed interface IslandEvent {
    /** Tap on the collapsed pill body. */
    data object Toggle : IslandEvent

    /**
     * Tap on the pill's search affordance: expand straight into Apps.
     *
     * It deliberately does NOT focus the search field — a launcher that throws the
     * keyboard up the instant the island opens is hostile. The keyboard only ever
     * appears when the user taps the text field itself.
     */
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
 *  - No event ever summons the keyboard; only the user tapping the field does.
 */
fun IslandState.reduce(event: IslandEvent): IslandState = when (event) {
    IslandEvent.Toggle -> when (expansion) {
        Expansion.COLLAPSED -> copy(
            expansion = Expansion.EXPANDED,
            origin = IslandOrigin.TAP
        )
        Expansion.EXPANDED -> collapse()
    }

    IslandEvent.SearchGesture -> copy(
        expansion = Expansion.EXPANDED,
        page = IslandPage.APPS,
        origin = IslandOrigin.SEARCH_GESTURE
    )

    IslandEvent.Expand -> if (isExpanded) this else copy(
        expansion = Expansion.EXPANDED,
        origin = IslandOrigin.ACCESSIBILITY
    )

    IslandEvent.Collapse -> if (expansion == Expansion.COLLAPSED) this else collapse()

    IslandEvent.OutsideTap -> if (expansion == Expansion.COLLAPSED) this else collapse()

    IslandEvent.Back -> if (expansion == Expansion.COLLAPSED) this else collapse()

    is IslandEvent.SelectPage ->
        // Page switches are explicitly expansion-preserving.
        copy(page = event.page, expansion = expansion)

    IslandEvent.AppLaunched -> collapse()

    is IslandEvent.QueryChanged -> copy(query = event.value)

    IslandEvent.EnterHome -> copy(
        expansion = Expansion.COLLAPSED,
        query = "",
        origin = IslandOrigin.RESTORED
    )

    is IslandEvent.DismissSuggestion -> copy(suggestionDismissed = event.dismissed)
}

fun IslandState.collapse(): IslandState =
    if (expansion == Expansion.COLLAPSED) this
    else copy(expansion = Expansion.COLLAPSED)

/** Tab index for the given page, for pager/transition math. */
fun IslandPage.index(): Int = IslandPage.ordered.indexOf(this)
