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
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BranchChatTest {
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

    /** [reply] answers each branch call by its attempt number (1-based) with a raw JSON-RPC fragment, or null to drop the socket. */
    private class Rig(val reply: (Int) -> String?) {
        val calls = Collections.synchronizedList(mutableListOf<Pair<String, JSONObject>>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val chat: ChatController

        init {
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val frame = JSONObject(text)
                    val id = frame.optString("id")
                    val method = frame.optString("method")
                    when (method) {
                        "client.capabilities", "process.list" -> webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":{}}""")
                        "session.resume" -> webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":{"session_id":"rt","stored_session_id":"stored-1","running":false}}""")
                        "session.branch", "session.branch_whole" -> {
                            calls += method to frame.getJSONObject("params")
                            val r = reply(calls.size)
                            if (r == null) webSocket.close(1001, "") else webSocket.send("""{"jsonrpc":"2.0","id":"$id",$r}""")
                        }
                    }
                }
            }
            val gateway = MockWebServer()
            gateway.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                    request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                    request.path!!.startsWith("/api/sessions/stored-1/messages") -> MockResponse().setBody(
                        """{"session_id":"stored-1","messages":[
                        {"id":11,"role":"user","content":"q1"},{"id":12,"role":"assistant","content":"a1"},
                        {"id":13,"role":"user","content":"q2"},{"id":14,"role":"assistant","content":"a2"},
                        {"id":15,"role":"user","content":"q3"},{"id":16,"role":"assistant","content":"a3"}]}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
            gateway.start()
            val base = gateway.url("/").toString().trimEnd('/')
            val tokens = TokenStore(MemoryStorage(), XorCipher())
            tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
            val http = OkHttpClient()
            val authed = AuthedHttp(http, tokens, "gw-1", base, now = { 1000 })
            val link = LiveLink(http, authed, base, scope, FakeNetwork(), random = { 0.0 })
            link.start()
            runBlocking { withTimeout(8000) { while (link.state.value != LinkState.Connected) delay(10) } }
            chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope, processPollMs = 100000)
            runBlocking { chat.open() }
        }

        fun key(n: Int) = (chat.state.value.items[n] as ChatItem.Message).key
    }

    private companion object {
        fun ok(stored: String) = """"result":{"session_id":"new-rt","stored_session_id":$stored,"title":"t"}"""
        fun err(code: Int) = """"error":{"code":$code,"message":"x"}"""
        fun keys(o: JSONObject) = o.keys().asSequence().toSet()
    }

    @Test
    fun branchesAMiddleTurnWithACountAndTheLastTurnWhole() = runBlocking {
        val r = Rig { ok("\"new-1\"") }
        assertEquals("new-1", r.chat.branch(r.key(2)))
        val (method, p) = r.calls.single()
        assertEquals("session.branch", method)
        assertEquals(setOf("session_id", "count"), keys(p))
        assertEquals(4, p.getInt("count"))

        assertEquals("new-1", r.chat.branch(r.key(4)))
        val (whole, wp) = r.calls[1]
        assertEquals("session.branch_whole", whole)
        assertEquals(setOf("session_id"), keys(wp))
    }

    @Test
    fun aStaleRuntimeIdResumesAndRetries() = runBlocking {
        val r = Rig { n -> if (n == 1) err(4001) else ok("\"new-1\"") }
        assertEquals("new-1", r.chat.branch(r.key(0)))
        assertEquals(2, r.calls.size)
    }

    @Test
    fun failuresReturnNullWithAFixedTextAndLeaveTheChat() = runBlocking {
        val replies = listOf(err(4008), err(5008), ok("null"), ok("\"\""), null)
        for (reply in replies) {
            val r = Rig { reply }
            val before = r.chat.state.value.items
            assertNull(r.chat.branch(r.key(0)))
            assertEquals(before, r.chat.state.value.items)
            assertFalse(r.chat.state.value.running)
            assertNotNull(r.chat.state.value.error)
            assertEquals("Couldn't branch this chat.", r.chat.state.value.error)
        }
    }
}
