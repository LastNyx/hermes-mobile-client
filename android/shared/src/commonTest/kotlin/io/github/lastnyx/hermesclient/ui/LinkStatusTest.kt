package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.auth.AuthState
import io.github.lastnyx.hermesclient.data.live.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkStatusTest {
    private val signedIn = AuthState.SignedIn("u")

    private fun s(
        link: LinkState,
        auth: AuthState = signedIn,
        online: Boolean = true,
        had: Boolean = false,
    ) = linkStatus(link, auth, online, had)

    @Test
    fun plainWordingForEachState() {
        assertEquals("Connected", s(LinkState.Connected).text)
        assertEquals("Offline · retrying", s(LinkState.Offline).text)
        assertEquals("Waiting for network…", s(LinkState.Offline, online = false).text)
        assertEquals("Connecting…", s(LinkState.Connecting).text)
        assertEquals("Reconnecting…", s(LinkState.Connecting, had = true).text)
        assertEquals("Waiting for network…", s(LinkState.Connecting, online = false).text)
        assertEquals("Offline · retrying", s(LinkState.Offline, AuthState.AuthUnavailable).text)
        listOf(LinkState.Connected, LinkState.Offline, LinkState.Connecting)
            .forEach { assertFalse(s(it).problem) }
    }

    @Test
    fun problems() {
        val signedOut = "Signed out · sign in again"
        listOf(
            s(LinkState.SignedOut),
            s(LinkState.Offline, AuthState.SignedOut),
            s(LinkState.Connected, AuthState.Expired),
        ).forEach {
            assertEquals(signedOut, it.text)
            assertTrue(it.problem)
        }
        val err = s(LinkState.Error("x"))
        assertEquals("Can't connect · check the gateway", err.text)
        assertTrue(err.problem)
        assertFalse(err.text.contains("x"))
    }
}
