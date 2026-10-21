package com.elthon.optilauncher.data

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.elthon.optilauncher.R
import com.elthon.optilauncher.core.panel.PanelEvent

/**
 * Real Quick Settings shortcut: tapping the NoBlooks tile reveals the same panel
 * as the three-finger gesture. The tile does not pretend the panel is a system
 * service and has no "on" state — it is an action, not a toggle.
 *
 * The overlay service is started from a tile click, which is a user interaction
 * and therefore exempt from the background-service start restrictions. If the
 * overlay permission is missing the window is not drawn; the tap is a no-op
 * rather than a claim that something appeared.
 */
class PanelTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.qs_tile_label)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { openPanel() }
        } else {
            openPanel()
        }
    }

    private fun openPanel() {
        ComposeOverlayService.show(this)
        PanelBus.dispatch(PanelEvent.Reveal)
        PanelSignals.reveal()
    }
}
