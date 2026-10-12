package io.github.lastnyx.hermesclient.data.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class AuthedHttpTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    @Test
    fun concurrent401sShareOneRefreshAndRetryWithNewToken() {
        val refreshes = java.util.concurrent.atomic.AtomicInteger()
        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/auth/native/refresh" -> {
                    refreshes.incrementAndGet()
                    MockResponse().setBody(
                        """{"access_token":"at-2","refresh_token":"rt-2","token_type":"Bearer","expires_at":1760000000,"provider":"basic","user_id":"u1"}""",
                    )
                }
                request.getHeader("Authorization") == "Bearer at-2" -> MockResponse().setBody("ok")
                else -> MockResponse().setResponseCode(401).setBody(
                    """{"error":"session_expired","detail":"Unauthorized","reason":"invalid_or_expired_session"}""",
                )
            }
        }
        gateway.start()
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val authed = AuthedHttp(OkHttpClient(), tokens, "gw-1", gateway.url("/").toString().trimEnd('/'), now = { 1000 })

        val bodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        val start = CountDownLatch(1)
        val workers = List(2) {
            thread {
                start.await()
                authed.execute(Request.Builder().url(gateway.url("/api/sessions")).build()).use { bodies += it.body!!.string() }
            }
        }
        start.countDown()
        workers.forEach { it.join() }

        assertEquals(listOf("ok", "ok"), bodies.toList())
        assertEquals(1, refreshes.get())
        assertEquals("at-2", tokens.load("gw-1")!!.accessToken)
        assertEquals("rt-2", tokens.load("gw-1")!!.refreshToken)
        assertEquals(AuthState.SignedIn("u1"), authed.state.value)

        authed.signOut()
        assertEquals(null, tokens.load("gw-1"))
        assertEquals(AuthState.SignedOut, authed.state.value)
        gateway.shutdown()
    }
}
