package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.auth.AuthState
import io.github.lastnyx.hermesclient.data.live.LinkState

data class LinkStatus(val text: String, val problem: Boolean)

fun linkStatus(
    link: LinkState,
    auth: AuthState,
    networkOnline: Boolean,
    hasConnected: Boolean,
): LinkStatus = when {
    auth == AuthState.SignedOut || auth == AuthState.Expired || link == LinkState.SignedOut ->
        LinkStatus("Signed out · sign in again", true)
    link == LinkState.Connected -> LinkStatus("Connected", false)
    link is LinkState.Error -> LinkStatus("Can't connect · check the gateway", true)
    !networkOnline -> LinkStatus("Waiting for network…", false)
    link == LinkState.Connecting -> LinkStatus(if (hasConnected) "Reconnecting…" else "Connecting…", false)
    else -> LinkStatus("Offline · retrying", false)
}
