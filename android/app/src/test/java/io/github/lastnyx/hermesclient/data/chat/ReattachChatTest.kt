package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.live.NetworkMonitor
import io.github.lastnyx.hermesclient.data.sessions.SessionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class ReattachChatTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private class FakeNetwork : NetworkMonitor {
        override val online = MutableStateFlow(true)
    }

    private suspend fun awaitTrue(check: () -> Boolean) {
        withTimeout(8000) { while (!check()) delay(10) }
    }

    private fun delta(text: String, seq: Int) =
        """{"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"rt2","payload":{"text":"$text"},"seq":$seq}}"""

    /** [activateCode] null answers session.activate normally; otherwise with that RPC error code. */
    private fun scenario(activateCode: Int?, body: suspend (calls: List<String>, chat: ChatController, socket: () -> WebSocket) -> Unit) = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val connections = AtomicInteger(0)
        lateinit var socket: WebSocket
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                connections.incrementAndGet()
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                val method = frame.optString("method")
                fun result(b: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$b}""")
                if (method.isNotEmpty()) {
                    val keys = frame.optJSONObject("params")?.keys()?.asSequence()?.sorted()?.joinToString(",").orEmpty()
                    calls += "$method:$keys"
                }
                when (method) {
                    "client.capabilities", "process.list" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt2","stored_session_id":"stored-1","running":true}""")
                    "session.activate" ->
                        if (activateCode == null) result("""{"session_id":"rt2","stored_session_id":"stored-1","running":true}""")
                        else webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":$activateCode,"message":"refused"}}""")
                    "session.events.since" -> result("""{"events":[],"latest_seq":1,"truncated":false,"epoch":"ep1","open_requests":[]}""")
                }
            }
        }
        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                request.path!!.startsWith("/api/sessions/stored-1/messages") ->
                    MockResponse().setBody("""{"session_id":"stored-1","messages":[{"role":"user","content":"hi"}]}""")
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
        val link = LiveLink(http, authed, base, scope, FakeNetwork(), random = { 0.0 })
        link.start()
        awaitTrue { link.state.value == LinkState.Connected }
        val chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
        chat.open()
        socket.send(delta("A", 1))
        awaitTrue { (chat.state.value.items.lastOrNull() as? ChatItem.Message)?.text == "A" }
        assertFalse(calls.any { it.startsWith("session.activate") })
        calls.clear()
        val first = connections.get()
        socket.close(1001, "")
        awaitTrue { connections.get() > first && link.state.value == LinkState.Connected }
        body(calls, chat) { socket }
        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    private fun methods(calls: List<String>) = calls.toList().map { it.substringBefore(':') }

    @Test
    fun aReconnectActivatesBeforeCatchUpAndLaterEventsApply() = scenario(null) { calls, chat, socket ->
        awaitTrue { calls.any { it.startsWith("session.events.since") } }
        assertEquals(listOf("session.activate:session_id", "session.events.since:last_seen,session_id"), calls.toList().filter { it.startsWith("session.") })
        assertNull(chat.state.value.error)
        socket().send(
            """{"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"rt2","payload":{"status":"interrupted"},"seq":2}}""",
        )
        awaitTrue { !chat.state.value.running }
    }

    @Test
    fun aStaleSessionReloadsAndResumesWithoutSince() {
        for (code in listOf(4001, 4007)) scenario(code) { calls, chat, _ ->
            awaitTrue { calls.any { it.startsWith("session.resume") } }
            delay(300)
            assertFalse(methods(calls).contains("session.events.since"))
            assertNull(chat.state.value.error)
        }
    }

    @Test
    fun otherRefusalsStillCatchUp() {
        for (code in listOf(4009, -32601)) scenario(code) { calls, chat, _ ->
            awaitTrue { calls.any { it.startsWith("session.events.since") } }
            assertTrue(methods(calls).indexOf("session.activate") < methods(calls).indexOf("session.events.since"))
            assertNull(chat.state.value.error)
        }
    }
}
