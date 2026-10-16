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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class EventGapChatTest {
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

    private class Rig(val truncated: Boolean, val sendEarly: Boolean) {
        val sinceParams = Collections.synchronizedList(mutableListOf<String>())
        val histories = AtomicInteger(0)
        val gateway = MockWebServer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        lateinit var authed: AuthedHttp
        lateinit var base: String
        lateinit var link: LiveLink
        lateinit var chat: ChatController
        lateinit var socket: WebSocket
    }

    private fun rig(truncated: Boolean, sendEarly: Boolean): Rig {
        val r = Rig(truncated, sendEarly)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                r.socket = webSocket
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (frame.optString("method")) {
                    "client.capabilities", "process.list" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt2","stored_session_id":"stored-1","running":true}""")
                    "session.events.since" -> {
                        val p = frame.getJSONObject("params")
                        r.sinceParams += "${p.getString("session_id")} ${p.getInt("last_seen")}"
                        if (r.sendEarly) webSocket.send(delta("E", 5))
                        if (r.truncated) {
                            result("""{"events":[],"latest_seq":4,"truncated":true,"epoch":"ep1","open_requests":[]}""")
                        } else {
                            result(
                                """{"events":[{"type":"message.delta","session_id":"rt2","payload":{"text":"C"},"seq":3},""" +
                                    """{"type":"message.delta","session_id":"rt2","payload":{"text":"D"},"seq":4}],""" +
                                    """"latest_seq":4,"truncated":false,"epoch":"ep1","open_requests":[]}""",
                            )
                        }
                    }
                }
            }
        }
        r.gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                request.path!!.startsWith("/api/sessions/stored-1/messages") -> {
                    r.histories.incrementAndGet()
                    MockResponse().setBody("""{"session_id":"stored-1","messages":[{"role":"user","content":"hi"}]}""")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        r.gateway.start()
        val base = r.gateway.url("/").toString().trimEnd('/')
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val http = OkHttpClient()
        val authed = AuthedHttp(http, tokens, "gw-1", base, now = { 1000 })
        r.authed = authed
        r.base = base
        r.link = LiveLink(http, authed, base, r.scope, FakeNetwork(), random = { 0.0 })
        r.link.start()
        return r
    }

    private fun lastText(r: Rig) = (r.chat.state.value.items.lastOrNull() as? ChatItem.Message)?.text

    private suspend fun open(r: Rig) {
        awaitTrue { r.link.state.value == LinkState.Connected }
        r.chat = ChatController(r.link, SessionsRepository(r.authed, r.base), "stored-1", r.scope)
        r.chat.open()
        r.socket.send(delta("A", 1))
        r.socket.send(delta("B", 2))
        awaitTrue { lastText(r) == "AB" }
    }

    private fun close(r: Rig) {
        r.link.stop()
        r.scope.coroutineContext[Job]!!.cancel()
        r.gateway.shutdown()
    }

    @Test
    fun aMissingSeqTriggersOneCatchUpAndNothingIsLost() = runBlocking {
        val r = rig(truncated = false, sendEarly = false)
        open(r)
        r.socket.send(delta("D", 4))
        awaitTrue { lastText(r) == "ABCD" }
        r.socket.send(delta("E", 5))
        awaitTrue { lastText(r) == "ABCDE" }
        delay(200)
        assertEquals(listOf("rt2 2"), r.sinceParams.toList())
        close(r)
    }

    @Test
    fun aLiveEventDuringTheCatchUpIsHeldAndAppliedAfterIt() = runBlocking {
        val r = rig(truncated = false, sendEarly = true)
        open(r)
        r.socket.send(delta("D", 4))
        awaitTrue { lastText(r) == "ABCDE" }
        delay(200)
        assertEquals(listOf("rt2 2"), r.sinceParams.toList())
        close(r)
    }

    @Test
    fun aTruncatedReplyAfterAGapReloadsHistory() = runBlocking {
        val r = rig(truncated = true, sendEarly = false)
        open(r)
        val before = r.histories.get()
        r.socket.send(delta("D", 4))
        awaitTrue { r.histories.get() > before }
        assertTrue(r.sinceParams.isNotEmpty())
        close(r)
    }
}
