package io.github.lastnyx.hermesclient.data.live

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class LiveLinkTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private fun ready(heartbeat: Boolean) =
        """{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":$heartbeat,"replay_epoch":"ep1"}}}"""

    private suspend fun awaitTrue(what: String, check: () -> Boolean) {
        withTimeout(8000) { while (!check()) delay(10) }
    }

    @Test
    fun liveLinkLifecycleAgainstMockGateway() = runBlocking {
        val tickets = AtomicInteger()
        val connections = AtomicInteger()
        val upgradePaths = Collections.synchronizedList(mutableListOf<String>())
        val clientFrames = Collections.synchronizedList(mutableListOf<JSONObject>())
        var slowId = ""

        fun listenerFor(index: Int) = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                when (index) {
                    1 -> webSocket.send(ready(false))
                    2 -> webSocket.close(4403, "")
                    else -> webSocket.send(ready(true))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (index != 1) return
                val frame = JSONObject(text)
                clientFrames += frame
                val id = frame.optString("id")
                when (frame.optString("method")) {
                    "client.capabilities" -> webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":{}}""")
                    "slow" -> slowId = id
                    "fast" -> {
                        webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":{"v":"fast"}}""")
                        webSocket.send("""{"jsonrpc":"2.0","id":"$slowId","result":{"v":"slow"}}""")
                    }
                    "bad" -> webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":4000,"message":"nope"}}""")
                    "trigger" -> {
                        webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s1","payload":{"text":"hi"},"seq":7}}""")
                        webSocket.send("""{"jsonrpc":"2.0","id":"srq-abc","method":"approval","params":{"session_id":"s1"}}""")
                        webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":{}}""")
                    }
                    "" -> if (id == "srq-abc") webSocket.close(4401, "")
                }
            }
        }

        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" ->
                    MockResponse().setBody("""{"ticket":"ticket-${tickets.incrementAndGet()}","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> {
                    upgradePaths += request.path!!
                    MockResponse().withWebSocketUpgrade(listenerFor(connections.incrementAndGet()))
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val http = OkHttpClient()
        val authed = AuthedHttp(http, tokens, "gw-1", base, now = { 1000 })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val link = LiveLink(http, authed, base, scope, FakeNetwork(true), heartbeatMs = 50, silenceMs = 150, random = { 0.0 })

        link.start()
        awaitTrue("connected") { link.state.value == LinkState.Connected }
        assertEquals("/api/ws?ticket=ticket-1", upgradePaths[0])
        assertEquals("client.capabilities", clientFrames[0].getString("method"))
        assertTrue(clientFrames[0].getJSONObject("params").getBoolean("server_requests"))
        assertEquals("ep1", link.replayEpoch)

        val slow = async(start = CoroutineStart.UNDISPATCHED) { link.call("slow") }
        val fast = async(start = CoroutineStart.UNDISPATCHED) { link.call("fast") }
        assertEquals("fast", fast.await().getString("v"))
        assertEquals("slow", slow.await().getString("v"))

        try {
            link.call("bad")
            fail("expected RpcError")
        } catch (e: RpcError) {
            assertEquals(4000, e.code)
            assertEquals("nope", e.message)
        }

        val event = async(start = CoroutineStart.UNDISPATCHED) { link.events.first { it.type == "message.delta" } }
        val request = async(start = CoroutineStart.UNDISPATCHED) { link.serverRequests.first() }
        link.call("trigger")
        val got = withTimeout(5000) { event.await() }
        assertEquals("s1", got.sessionId)
        assertEquals("hi", got.payload!!.getString("text"))
        assertEquals(7L, got.seq)
        val srq = withTimeout(5000) { request.await() }
        assertEquals("srq-abc", srq.id)
        assertEquals("approval", srq.method)
        link.respond(srq.id, JSONObject().put("choice", "once"))

        awaitTrue("4401 reconnect") { connections.get() == 2 }
        assertEquals("/api/ws?ticket=ticket-2", upgradePaths[1])
        awaitTrue("4403 error") { link.state.value is LinkState.Error }
        delay(300)
        assertEquals(2, tickets.get())
        assertTrue(link.state.value is LinkState.Error)

        link.start()
        awaitTrue("silence reconnect") { connections.get() >= 4 }
        link.stop()
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        gateway.shutdown()
    }

    private class FakeNetwork(initial: Boolean) : NetworkMonitor {
        override val online = MutableStateFlow(initial)
    }

    @Test
    fun linkFollowsNetworkMonitor() = runBlocking {
        val tickets = AtomicInteger()
        val closed = AtomicInteger()
        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" ->
                    MockResponse().setBody("""{"ticket":"t${tickets.incrementAndGet()}","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(ready(true)) }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { closed.incrementAndGet() }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.incrementAndGet() }
                })
                else -> MockResponse().setResponseCode(404)
            }
        }
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val http = OkHttpClient()
        val authed = AuthedHttp(http, tokens, "gw-1", base, now = { 1000 })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val network = FakeNetwork(false)
        // random = 1.0 makes any backoff sleep non-zero, so an instant reconnect proves no sleep happened.
        val link = LiveLink(http, authed, base, scope, network, random = { 1.0 })

        link.start()
        delay(300)
        assertEquals(LinkState.Offline, link.state.value)
        assertEquals(0, gateway.requestCount)

        network.online.value = true
        withTimeout(1000) { while (link.state.value != LinkState.Connected) delay(10) }

        network.online.value = false
        withTimeout(1000) { while (link.state.value != LinkState.Offline) delay(10) }
        awaitTrue("socket closed") { closed.get() >= 1 }
        delay(300)
        assertEquals(1, tickets.get())

        network.online.value = true
        withTimeout(1000) { while (link.state.value != LinkState.Connected) delay(10) }
        assertEquals(2, tickets.get())

        link.stop()
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        gateway.shutdown()
    }
}
