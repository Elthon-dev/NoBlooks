package com.elthon.optilauncher.data

import com.elthon.optilauncher.core.panel.PanelEvent
import com.elthon.optilauncher.core.panel.PanelNotification
import com.elthon.optilauncher.core.panel.PanelState
import com.elthon.optilauncher.core.panel.RecentApp
import com.elthon.optilauncher.core.panel.StopVerdict
import com.elthon.optilauncher.core.panel.reduce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The process-wide panel state, shared by the in-app panel and the always-on
 * overlay service.
 *
 * The panel has to keep working when the launcher activity is gone — that is the
 * whole point of an overlay — so its state cannot live in [LauncherUiState], which
 * dies with the ViewModel. This object is the one source of truth:
 *
 *  - the accessibility gesture and the pill both push [PanelEvent]s here,
 *  - the overlay service renders from [state] and [recents] / [notifications],
 *  - the in-app panel mirrors the same flows.
 *
 * It holds only state. The system side effects (going Home, force-stopping an app,
 * dismissing a notification) stay in [PanelController] and [SystemActions], which
 * the *overlay service* owns because it outlives any activity.
 */
object PanelBus {

    private val _state = MutableStateFlow(PanelState())
    val state: StateFlow<PanelState> = _state.asStateFlow()

    private val _recents = MutableStateFlow<List<RecentApp>>(emptyList())
    val recents: StateFlow<List<RecentApp>> = _recents.asStateFlow()

    private val _habits = MutableStateFlow<List<RecentApp>>(emptyList())
    val habits: StateFlow<List<RecentApp>> = _habits.asStateFlow()

    private val _notifications = MutableStateFlow<List<PanelNotification>>(emptyList())
    val notifications: StateFlow<List<PanelNotification>> = _notifications.asStateFlow()

    /** True while the always-on overlay service is running and permitted. */
    private val _overlayActive = MutableStateFlow(false)
    val overlayActive: StateFlow<Boolean> = _overlayActive.asStateFlow()

    /** Applies an event to the shared state. Actions are the caller's job. */
    fun dispatch(event: PanelEvent): PanelState {
        val next = _state.value.reduce(event)
        if (next != _state.value) _state.value = next
        return next
    }

    fun setRecents(list: List<RecentApp>) {
        _recents.value = list
    }

    fun setHabits(list: List<RecentApp>) {
        _habits.value = list
    }

    fun setNotifications(list: List<PanelNotification>) {
        _notifications.value = list
    }

    fun setStopVerdict(verdict: StopVerdict?) {
        dispatch(PanelEvent.SetStopVerdict(verdict))
    }

    fun setOverlayActive(active: Boolean) {
        _overlayActive.value = active
    }

    /**
     * Clears the transient bits a dead service should not leave behind: a visible
     * panel with no window behind it would be a lie the next time it is read.
     */
    fun onOverlayStopped() {
        _state.value = _state.value.reduce(PanelEvent.Hide)
        _overlayActive.value = false
    }
}
