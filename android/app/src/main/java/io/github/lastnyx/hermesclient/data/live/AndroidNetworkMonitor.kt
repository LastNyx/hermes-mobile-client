package io.github.lastnyx.hermesclient.data.live

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.StateFlow

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val tracker = NetworkTracker()
    override val online: StateFlow<Boolean> = tracker.online

    private fun NetworkCapabilities.internet() = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    private fun NetworkCapabilities.vpn() = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            tracker.onCapabilities(network.toString(), caps.internet(), caps.vpn())

        override fun onLost(network: Network) = tracker.onLost(network.toString())
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        manager.registerNetworkCallback(request, callback)
    }

    fun close() = manager.unregisterNetworkCallback(callback)
}
