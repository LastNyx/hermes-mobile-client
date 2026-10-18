package io.github.lastnyx.hermesclient.data

import io.github.lastnyx.hermesclient.data.live.RpcError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class FriendlyErrorTest {
    private val cantReach = "Can't reach the gateway. Check your connection."
    private val generic = "Something went wrong. Try again."
    private val gatewayError = "The gateway hit an error. Try again."

    @Test
    fun errorsBecomeFixedSentences() {
        assertEquals(cantReach, friendlyError(IOException("failed to connect to 10.0.0.1/10.0.0.1:9119")))
        assertEquals(cantReach, friendlyError(SocketTimeoutException("timeout")))
        val rpc = mapOf(
            4009 to "The agent is busy. Wait for it to finish, then try again.",
            4001 to "This chat session expired. Reopen the chat.",
            4130 to "This chat is too long to continue. Start a new chat.",
            4018 to "That changed on the gateway. Reload the chat and try again.",
            4028 to "That would erase the whole chat. Nothing was changed.",
            5000 to gatewayError,
            5063 to gatewayError,
            4099 to generic,
            -32000 to generic,
        )
        rpc.forEach { (code, text) -> assertEquals(text, friendlyError(RpcError(code, "server secret"))) }
        val http = mapOf(
            401 to "Signed out. Sign in again.",
            403 to "Signed out. Sign in again.",
            404 to "The gateway doesn't have that.",
            409 to "That can't be done right now. Try again in a moment.",
            500 to gatewayError,
            503 to gatewayError,
            418 to generic,
        )
        http.forEach { (code, text) -> assertEquals(text, friendlyError(HttpFailure(code))) }
        val other = friendlyError(IllegalStateException("secret-looking text"))
        assertEquals(generic, other)
        assertFalse(other.contains("secret"))

        assertEquals("Sign-in timed out", signInErrorText(IllegalStateException("Sign-in timed out")))
        assertEquals("Sign-in failed. Try again.", signInErrorText(IllegalStateException("access_denied")))
        assertEquals("Sign-in failed. Try again.", signInErrorText(IllegalStateException("Gateway answered HTTP 500")))
        assertEquals(cantReach, signInErrorText(IOException("x")))
    }
}
