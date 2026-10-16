package io.github.lastnyx.hermesclient.data.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Online = a physical (non-VPN) network with internet exists. A VPN such as Tailscale stays up
 * in airplane mode, so it must never count.
 */
class NetworkTracker {
    private val live = HashSet<String>()
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online

    fun onAvailable(id: String, hasInternet: Boolean, isVpn: Boolean) = onCapabilities(id, hasInternet, isVpn)

    fun onCapabilities(id: String, hasInternet: Boolean, isVpn: Boolean) = synchronized(live) {
        if (hasInternet && !isVpn) live.add(id) else live.remove(id)
        _online.value = live.isNotEmpty()
    }

    fun onLost(id: String) = synchronized(live) {
        live.remove(id)
        _online.value = live.isNotEmpty()
    }
}
