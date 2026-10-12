package io.github.lastnyx.hermesclient.data.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTrackerTest {
    @Test
    fun onlyPhysicalInternetNetworksCount() {
        val t = NetworkTracker()
        assertFalse(t.online.value)

        t.onAvailable("vpn", hasInternet = true, isVpn = true)
        assertFalse(t.online.value)

        t.onAvailable("wifi", hasInternet = true, isVpn = false)
        assertTrue(t.online.value)
        t.onLost("wifi")
        assertFalse(t.online.value)

        t.onAvailable("wifi", hasInternet = true, isVpn = false)
        t.onCapabilities("wifi", hasInternet = false, isVpn = false)
        assertFalse(t.online.value)
        t.onCapabilities("wifi", hasInternet = true, isVpn = false)
        assertTrue(t.online.value)
        t.onCapabilities("wifi", hasInternet = true, isVpn = true)
        assertFalse(t.online.value)

        t.onAvailable("wifi", hasInternet = true, isVpn = false)
        t.onAvailable("cell", hasInternet = true, isVpn = false)
        t.onLost("wifi")
        assertTrue(t.online.value)
        t.onLost("cell")
        assertFalse(t.online.value)
    }
}
