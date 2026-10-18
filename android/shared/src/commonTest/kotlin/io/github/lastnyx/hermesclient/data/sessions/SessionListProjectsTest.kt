package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.RpcError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class SessionListProjectsTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private val listBody = """{"sessions":[{"id":"a","title":"A","preview":"p","last_active":1.0}]}"""
    private val treeBody = """{"projects":[{"id":"p1","label":"repo","sessionIds":["a"]}]}"""

    private fun model(gateway: MockWebServer, rpc: (suspend (String, JSONObject) -> JSONObject)?): SessionListModel {
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val base = gateway.url("/").toString().trimEnd('/')
        return SessionListModel(
            SessionsRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base),
            Dispatchers.IO,
            rpc,
        )
    }

    @Test
    fun loadAsksTheGatewayForProjectsOnce() = runBlocking {
        val gateway = MockWebServer().apply { start() }
        val calls = mutableListOf<Pair<String, String>>()
        val m = model(gateway) { method, params -> calls += method to params.toString(); JSONObject(treeBody) }
        gateway.enqueue(MockResponse().setBody(listBody))
        m.load()
        assertEquals(listOf("projects.tree" to """{"session_limit":2000}"""), calls)
        assertEquals(listOf("repo"), m.state.value.projects!!.map { it.label })
    }

    @Test
    fun aFailingCallLeavesNoProjectsAndNoError() = runBlocking {
        for (failure in listOf<Exception>(RpcError(-32601, "nope"), IOException("Offline"))) {
            val gateway = MockWebServer().apply { start() }
            val m = model(gateway) { _, _ -> throw failure }
            gateway.enqueue(MockResponse().setBody(listBody))
            m.load()
            assertNull(m.state.value.projects)
            assertNull(m.state.value.error)
            assertEquals(listOf("a"), m.state.value.sessions.map { it.id })
        }
    }

    @Test
    fun aSearchDoesNotAskForProjects() = runBlocking {
        val gateway = MockWebServer().apply { start() }
        var calls = 0
        val m = model(gateway) { _, _ -> calls++; JSONObject(treeBody) }
        gateway.enqueue(MockResponse().setBody("""{"results":[]}"""))
        m.search("hello")
        assertEquals(0, calls)
    }

    @Test
    fun connectingWhileLoadingStillLoadsProjects() = runBlocking {
        val gateway = MockWebServer().apply { start() }
        var calls = 0
        val m = model(gateway) { _, _ -> calls++; JSONObject(treeBody) }
        gateway.enqueue(MockResponse().setBody(listBody).setBodyDelay(500, TimeUnit.MILLISECONDS))
        val loading = async { m.load() }
        while (!m.state.value.loading) delay(10)
        m.onLinkState(LinkState.Connected)
        assertEquals(1, calls)
        assertNotNull(m.state.value.projects)
        loading.await()
        assertEquals(2, calls)
    }

    @Test
    fun withoutRpcThereAreNoProjects() = runBlocking {
        val gateway = MockWebServer().apply { start() }
        val m = model(gateway, null)
        gateway.enqueue(MockResponse().setBody(listBody))
        m.load()
        assertNull(m.state.value.projects)
        assertEquals(listOf("a"), m.state.value.sessions.map { it.id })
    }
}
