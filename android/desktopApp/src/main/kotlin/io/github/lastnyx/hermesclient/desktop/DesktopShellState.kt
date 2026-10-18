package io.github.lastnyx.hermesclient.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.lastnyx.hermesclient.data.gateway.Gateway
import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry

sealed interface RightSide {
    data object Manage : RightSide
    data class SignIn(val gateway: Gateway) : RightSide
    data class Sessions(val gateway: Gateway) : RightSide
}

/** Which gateway is selected and what the area right of the gateway column shows. */
class DesktopShellState(
    private val registry: GatewayRegistry,
    private val signedIn: (String) -> Boolean,
) {
    var gateways by mutableStateOf(registry.list())
        private set
    var manageOpen by mutableStateOf(registry.list().isEmpty())
        private set

    val selected: Gateway? get() = registry.active()

    val right: RightSide
        get() {
            val gateway = selected
            return when {
                manageOpen || gateway == null -> RightSide.Manage
                !signedIn(gateway.id) -> RightSide.SignIn(gateway)
                else -> RightSide.Sessions(gateway)
            }
        }

    fun select(id: String) {
        registry.setActive(id)
        manageOpen = false
        gateways = registry.list()
    }

    fun openManage() {
        manageOpen = true
    }

    /** The Manage page edits the registry itself, so re-read it when the page closes. */
    fun closeManage() {
        refresh()
        manageOpen = false
        if (selected == null) gateways.firstOrNull()?.let { registry.setActive(it.id) }
        if (selected == null) manageOpen = true
    }

    fun remove(id: String) {
        val index = gateways.indexOfFirst { it.id == id }
        registry.remove(id)
        refresh()
        if (selected == null) gateways.getOrNull(index.coerceAtMost(gateways.lastIndex))?.let { registry.setActive(it.id) }
        manageOpen = selected == null
    }

    private fun refresh() {
        gateways = registry.list()
    }
}
