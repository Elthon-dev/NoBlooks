package com.elthon.optilauncher.core.panel

/**
 * A notification as the panel understands it.
 *
 * This is intentionally a *flattened* view of `StatusBarNotification`: the data
 * layer does the Android work and hands us something pure, so grouping, ordering
 * and "is this actionable" rules are unit-testable.
 *
 * What it deliberately does NOT carry: the raw `RemoteViews`, the child
 * notification tree, or any thumbnails. A third-party listener cannot read
 * another app's rendered view, so pretending otherwise would be a lie.
 */
data class PanelNotification(
    /** Stable platform key (`StatusBarNotification.key`) — used to dismiss. */
    val key: String,
    val packageName: String,
    val title: String?,
    val body: String?,
    /** Extra lines, e.g. a stacked conversation. May be empty. */
    val lines: List<String>,
    val postTimeMs: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    /** True when the notification declares a text-reply affordance. */
    val canReply: Boolean
) {
    /** One-line preview for the collapsed row. */
    val preview: String get() = body ?: lines.firstOrNull() ?: ""

    /**
     * Ongoing/foreground notifications cannot be dismissed by a listener, and
     * media/persistent cards are noise in a "what did I miss" list.
     */
    val isMissedCandidate: Boolean get() = !isOngoing
}

/**
 * Groups notifications by package, keeping their post order, and drops the ones
 * the panel cannot act on. Sort is newest-first across the whole set.
 */
fun List<PanelNotification>.forPanel(): List<PanelNotification> =
    filter { it.isMissedCandidate && it.isClearable }
        .sortedByDescending { it.postTimeMs }

/** Distinct packages present in the list, in newest-first order. */
fun List<PanelNotification>.packages(): List<String> =
    forPanel().map { it.packageName }.distinct()
