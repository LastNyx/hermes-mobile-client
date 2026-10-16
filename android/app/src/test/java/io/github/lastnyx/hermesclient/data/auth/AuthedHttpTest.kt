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

    @Test
    fun anUnreadableStoreIsUnreadableStateWithNoRequestAndAnEmptyOneIsSignedOut() {
        val gateway = MockWebServer()
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val request = Request.Builder().url("$base/api/sessions").build()
        val bad = MemoryStorage().apply { text = "%%%not-base64" }
        val authed = AuthedHttp(OkHttpClient(), TokenStore(bad, XorCipher()), "gw-1", base)
        assertEquals(AuthState.Unreadable, authed.state.value)

        val e = runCatching { authed.execute(request) }.exceptionOrNull()

        assertEquals(java.io.IOException::class.java, e?.javaClass)
        assertEquals(SAVED_SIGN_IN_UNREADABLE, e?.message)
        assertEquals(0, gateway.requestCount)
        assertEquals(AuthState.Unreadable, authed.state.value)

        val empty = AuthedHttp(OkHttpClient(), TokenStore(MemoryStorage(), XorCipher()), "gw-1", base)
        val e2 = runCatching { empty.execute(request) }.exceptionOrNull()
        assertEquals("Signed out — sign in again", e2?.message)
        assertEquals(AuthState.SignedOut, empty.state.value)
        gateway.shutdown()
    }

    private val rotated =
        """{"access_token":"at-2","refresh_token":"rt-2","token_type":"Bearer","expires_at":1760000000,"provider":"basic","user_id":"u1"}"""

    private fun setup(gateway: MockWebServer): Pair<TokenStore, AuthedHttp> {
        gateway.start()
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1000, "basic", "u1"))
        return tokens to AuthedHttp(OkHttpClient(), tokens, "gw-1", gateway.url("/").toString().trimEnd('/'), now = { 2000 })
    }

    @Test
    fun expiredByClockTokenRefreshesBeforeRequestAndPersists() {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setBody(rotated))
        gateway.enqueue(MockResponse().setBody("ok"))
        val (tokens, authed) = setup(gateway)

        authed.execute(Request.Builder().url(gateway.url("/api/sessions")).build()).use { assertEquals("ok", it.body!!.string()) }

        assertEquals("/auth/native/refresh", gateway.takeRequest().path)
        assertEquals("Bearer at-2", gateway.takeRequest().getHeader("Authorization"))
        assertEquals(TokenPair("at-2", "rt-2", 1760000000, "basic", "u1"), tokens.load("gw-1"))
        gateway.shutdown()
    }

    @Test
    fun postGets401WithoutReplay() {
        val gateway = MockWebServer()
        gateway.start()
        gateway.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":"session_expired","detail":"Unauthorized","reason":"invalid_or_expired_session"}"""),
        )
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val authed = AuthedHttp(OkHttpClient(), tokens, "gw-1", gateway.url("/").toString().trimEnd('/'), now = { 1000 })
        val post = Request.Builder().url(gateway.url("/api/sessions"))
            .post(okhttp3.RequestBody.create(null, "{}")).build()

        authed.execute(post).use { assertEquals(401, it.code) }

        assertEquals(1, gateway.requestCount)
        assertEquals("at-1", tokens.load("gw-1")!!.accessToken)
        gateway.shutdown()
    }

    @Test
    fun refresh401WipesTokensAndExpires() {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setResponseCode(401))
        val (tokens, authed) = setup(gateway)

        val failure = runCatching { authed.execute(Request.Builder().url(gateway.url("/api/sessions")).build()) }

        assertEquals(true, failure.isFailure)
        assertEquals(null, tokens.load("gw-1"))
        assertEquals(AuthState.Expired, authed.state.value)
        gateway.shutdown()
    }

    @Test
    fun refresh503KeepsTokensAndIsUnavailable() {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setResponseCode(503))
        val (tokens, authed) = setup(gateway)

        val failure = runCatching { authed.execute(Request.Builder().url(gateway.url("/api/sessions")).build()) }

        assertEquals(true, failure.isFailure)
        assertEquals("rt-1", tokens.load("gw-1")!!.refreshToken)
        assertEquals(AuthState.AuthUnavailable, authed.state.value)
        gateway.shutdown()
    }
}
