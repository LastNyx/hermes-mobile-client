package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.HttpFailure
import io.github.lastnyx.hermesclient.data.friendlyError
import io.github.lastnyx.hermesclient.data.gateway.restClient
import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.live.LinkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SessionListLinkRefreshTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private fun repo(client: OkHttpClient, base: String): SessionsRepository {
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        return SessionsRepository(AuthedHttp(client, tokens, "gw-1", base, now = { 1000 }), base)
    }

    @Test
    fun networkFailuresGiveFriendlyMessageAndHttpErrorGivesDetail() {
        val refused = MockWebServer().also { it.start() }
        val refusedBase = refused.url("/").toString().trimEnd('/')
        refused.shutdown()
        assertEquals(CANT_REACH_GATEWAY, friendlyError(repo(OkHttpClient(), refusedBase).list().exceptionOrNull()!!))

        val silent = MockWebServer().also { it.start() }
        silent.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val shortClient = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        assertEquals(CANT_REACH_GATEWAY, friendlyError(repo(shortClient, silent.url("/").toString().trimEnd('/')).list().exceptionOrNull()!!))
        silent.shutdown()

        assertEquals(CANT_REACH_GATEWAY, friendlyError(repo(OkHttpClient(), "http://no-such-host.invalid").list().exceptionOrNull()!!))

        val broken = MockWebServer().also { it.start() }
        broken.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"gateway down"}"""))
        val failure = repo(OkHttpClient(), broken.url("/").toString().trimEnd('/')).list().exceptionOrNull()!!
        assertEquals("The gateway hit an error. Try again.", friendlyError(failure))
        assertTrue(failure is HttpFailure && failure.code == 500 && !failure.message!!.contains("gateway down"))
        broken.shutdown()
    }

    @Test
    fun restClientCallTimeoutTurnsASlowGatewayIntoCantReach() {
        val slow = MockWebServer().also { it.start() }
        slow.enqueue(MockResponse().setBody("""{"sessions":[]}""").setBodyDelay(2, TimeUnit.SECONDS))
        val base = OkHttpClient()
        val rest = restClient(base, 200)
        assertEquals(200, rest.callTimeoutMillis)
        assertEquals(0, base.callTimeoutMillis)
        assertEquals(base.readTimeoutMillis, rest.readTimeoutMillis)
        assertEquals(30_000, restClient(base).callTimeoutMillis)
        assertEquals(CANT_REACH_GATEWAY, friendlyError(repo(rest, slow.url("/").toString().trimEnd('/')).list().exceptionOrNull()!!))
        slow.shutdown()
    }

    @Test
    fun connectedReloadsAndClearsStaleErrorButNotWhileLoading() = runBlocking {
        val gateway = MockWebServer().also { it.start() }
        val base = gateway.url("/").toString().trimEnd('/')
        val model = SessionListModel(repo(OkHttpClient(), base), Dispatchers.IO)

        gateway.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"gateway down"}"""))
        model.load()
        assertEquals("The gateway hit an error. Try again.", model.state.value.error)

        model.onLinkState(LinkState.Connecting)
        assertEquals(1, gateway.requestCount)

        gateway.enqueue(MockResponse().setBody("""{"sessions":[{"id":"a","title":"A","preview":"","last_active":1.0}]}"""))
        model.onLinkState(LinkState.Connected)
        assertEquals(2, gateway.requestCount)
        assertNull(model.state.value.error)
        assertEquals(listOf("a"), model.state.value.sessions.map { it.id })

        gateway.enqueue(MockResponse().setBody("""{"sessions":[]}""").setBodyDelay(600, TimeUnit.MILLISECONDS))
        val inFlight = async(Dispatchers.Default) { model.load() }
        while (!model.state.value.loading) delay(10)
        model.onLinkState(LinkState.Connected)
        inFlight.await()
        assertEquals(3, gateway.requestCount)
        assertTrue(model.state.value.sessions.isEmpty())
        gateway.shutdown()
    }
}
