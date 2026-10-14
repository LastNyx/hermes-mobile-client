package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.auth.SignInState
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayStatusTest {
    @Test
    fun statusLineForEveryState() {
        assertEquals(GatewayStatus("Signed in as x", StatusTone.Good), gatewayStatusLine(SignInState.SignedIn("x"), null))
        assertEquals(GatewayStatus("Signed in", StatusTone.Good), gatewayStatusLine(SignInState.SignedIn("Signed in"), null))
        assertEquals(GatewayStatus("Signing in…", StatusTone.Normal), gatewayStatusLine(SignInState.SigningIn, null))
        assertEquals(
            GatewayStatus("Gateway auth unavailable, retrying", StatusTone.Warning),
            gatewayStatusLine(SignInState.Unavailable, null),
        )
        assertEquals(
            GatewayStatus("Signed out — sign in again", StatusTone.Error),
            gatewayStatusLine(SignInState.SignedOut("Signed out — sign in again"), null),
        )
        assertEquals(GatewayStatus("Sign-in failed", StatusTone.Normal), gatewayStatusLine(SignInState.SignedOut("Sign-in failed"), null))
        assertEquals(GatewayStatus("Signed out", StatusTone.Normal), gatewayStatusLine(SignInState.SignedOut(null), null))
    }

    @Test
    fun testResultIsAppended() {
        assertEquals(
            GatewayStatus("Signed in as x · Online, Hermes 0.21.5", StatusTone.Good),
            gatewayStatusLine(SignInState.SignedIn("x"), "Online, Hermes 0.21.5"),
        )
    }
}
