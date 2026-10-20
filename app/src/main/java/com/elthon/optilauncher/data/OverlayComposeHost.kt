package com.elthon.optilauncher.data

import android.content.Context
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.elthon.optilauncher.ui.panel.OverlayRoot

/**
 * Wires a [ComposeView] for use inside a [android.app.Service].
 *
 * Compose needs three owners that an Activity provides for free and a Service does
 * not: a lifecycle, a view-model store, and a saved-state registry. Missing any one
 * of them throws `ViewTreeLifecycleOwner not found` the moment composition starts.
 */
internal class OverlayComposeHost(
    private val service: ComposeOverlayService,
    private val actions: OverlayPanelActions
) {
    fun build(): View {
        val view = ComposeView(service)
        view.setViewTreeLifecycleOwner(service)
        view.setViewTreeSavedStateRegistryOwner(service)
        view.setViewTreeViewModelStoreOwner(service)
        view.setContent {
            OverlayRoot(actions)
        }
        return view
    }
}
