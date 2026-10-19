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
    data class Settings(val gateway: Gateway?) : RightSide
    data class Capabilities(val gateway: Gateway) : RightSide
}

private enum class Overlay { None, Settings, Capabilities }

/** The packaged launcher sets the version; `gradle run` does not, so it is a development build. */
fun desktopVersionLine(appVersion: String?): String = appVersion?.takeIf { it.isNotBlank() } ?: "development build"

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

    private var overlay by mutableStateOf(Overlay.None)

    val right: RightSide
        get() {
            val gateway = selected
            val signed = gateway?.takeIf { signedIn(it.id) }
            return when {
                overlay == Overlay.Capabilities && signed != null -> RightSide.Capabilities(signed)
                overlay != Overlay.None -> RightSide.Settings(signed)
                manageOpen || gateway == null -> RightSide.Manage
                signed == null -> RightSide.SignIn(gateway)
                else -> RightSide.Sessions(gateway)
            }
        }

    fun openSettings() {
        overlay = Overlay.Settings
    }

    fun openCapabilities() {
        if (overlay == Overlay.Settings && selected?.let { signedIn(it.id) } == true) overlay = Overlay.Capabilities
    }

    fun back() {
        overlay = if (overlay == Overlay.Capabilities) Overlay.Settings else Overlay.None
    }

    fun select(id: String) {
        registry.setActive(id)
        overlay = Overlay.None
        manageOpen = false
        gateways = registry.list()
    }

    /** Session the chat pane should open next; cleared by [sessionOpened] once SessionsHost has it. */
    var pendingSession by mutableStateOf<String?>(null)
        private set

    fun openSession(gatewayId: String, sessionId: String) {
        select(gatewayId)
        pendingSession = sessionId
    }

    fun sessionOpened() {
        pendingSession = null
    }

    fun openManage() {
        overlay = Overlay.None
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
