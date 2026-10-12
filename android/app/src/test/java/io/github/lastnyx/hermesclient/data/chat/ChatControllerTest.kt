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
import org.junit.Test
import java.util.Collections

class ChatControllerTest {
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

    private fun event(type: String, payload: String, seq: Int) =
        """{"jsonrpc":"2.0","method":"event","params":{"type":"$type","session_id":"rt2","payload":$payload,"seq":$seq}}"""

    @Test
    fun reconnectWithSameEpochReplaysMissedEventsWithoutDuplicates() = runBlocking {
        val sinceParams = Collections.synchronizedList(mutableListOf<String>())
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                val params = frame.optJSONObject("params") ?: JSONObject()
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (frame.optString("method")) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt2","stored_session_id":"stored-1","running":false}""")
                    "prompt.submit" -> {
                        result("""{"status":"streaming"}""")
                        webSocket.send(event("message.delta", """{"text":"Hel"}""", 1))
                        webSocket.send(event("message.delta", """{"text":"lo"}""", 2))
                        webSocket.close(1001, "")
                    }
                    "session.events.since" -> {
                        sinceParams += params.toString().let { p -> JSONObject(p).let { "${it.getString("session_id")} ${it.getInt("last_seen")} ${it.length()}" } }
                        result(
                            """{"events":[{"type":"message.delta","session_id":"rt2","payload":{"text":"lo"},"seq":2},""" +
                                """{"type":"message.delta","session_id":"rt2","payload":{"text":"!"},"seq":3}],""" +
                                """"latest_seq":3,"truncated":false,"count":2,"epoch":"ep1",""" +
                                """"open_requests":[{"id":"srq-1","method":"approval","params":{"command":"ls"}}]}""",
                        )
                    }
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
        chat.send("go")
        awaitTrue { chat.state.value.openRequests.isNotEmpty() && sinceParams.isNotEmpty() }
        awaitTrue { chat.state.value.items.size == 3 && (chat.state.value.items.last() as ChatItem.Message).text == "Hello!" }

        assertEquals(listOf("rt2 2 2"), sinceParams.toList())
        assertEquals("srq-1", chat.state.value.openRequests.single().id)

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun chatOpensStreamsSteersStopsAndResumesOn4001() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        var resumes = 0

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                val method = frame.optString("method")
                val params = frame.optJSONObject("params") ?: JSONObject()
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (method) {
                    "client.capabilities" -> { calls += method; result("{}") }
                    "session.resume" -> {
                        calls += "$method ${params.getString("session_id")}"
                        resumes++
                        result("""{"session_id":"rt$resumes","stored_session_id":"stored-1","running":false}""")
                    }
                    "prompt.submit" -> {
                        calls += "$method ${params.getString("session_id")} ${params.getString("text")}"
                        if (params.getString("session_id") == "rt1") {
                            webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":4001,"message":"session not found"}}""")
                        } else {
                            result("""{"status":"streaming"}""")
                            webSocket.send(event("message.start", "{}", 1))
                            webSocket.send(event("message.delta", """{"text":"Hel"}""", 2))
                            webSocket.send(event("message.delta", """{"text":"lo"}""", 3))
                            webSocket.send(event("tool.start", """{"tool_id":"call_1","name":"terminal"}""", 4))
                            webSocket.send(event("tool.complete", """{"tool_id":"call_1","name":"terminal","summary":"exit 0"}""", 5))
                            webSocket.send(event("message.delta", """{"text":"!"}""", 6))
                        }
                    }
                    "session.steer" -> {
                        calls += "$method ${params.getString("session_id")} ${params.getString("text")}"
                        result("""{"status":"queued"}""")
                    }
                    "session.interrupt" -> {
                        calls += "$method ${params.getString("session_id")}"
                        result("""{"status":"interrupted"}""")
                        webSocket.send(event("message.complete", """{"text":"Hello!","status":"interrupted"}""", 7))
                    }
                }
            }
        }

        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                request.path!!.startsWith("/api/sessions/stored-1/messages") ->
                    MockResponse().setBody(
                        """{"session_id":"stored-1","messages":[{"role":"user","content":"hi"},{"role":"assistant","content":[{"type":"text","text":"yo"}]},{"role":"tool","content":"x"}]}""",
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val link = LiveLink(http, authed, base, scope, FakeNetwork(), random = { 0.0 })
        link.start()
        awaitTrue { link.state.value == LinkState.Connected }

        val chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
        fun rows() = chat.state.value.items.map {
            when (it) {
                is ChatItem.Message -> "${it.role}:${it.text}"
                is ChatItem.Tool -> "tool:${it.name}:${it.status}"
            }
        }

        chat.open()
        assertEquals(listOf("user:hi", "assistant:yo"), rows())

        chat.send("go")
        awaitTrue { rows().size == 6 }
        assertEquals(
            listOf("user:hi", "assistant:yo", "user:go", "assistant:Hello", "tool:terminal:Done", "assistant:!"),
            rows(),
        )
        assertEquals(true, chat.state.value.running)

        chat.send("more")
        chat.stop()
        awaitTrue { !chat.state.value.running }
        assertEquals(false, (chat.state.value.items.last() as ChatItem.Message).streaming)

        assertEquals(
            listOf(
                "client.capabilities",
                "session.resume stored-1",
                "prompt.submit rt1 go",
                "session.resume stored-1",
                "prompt.submit rt2 go",
                "session.steer rt2 more",
                "session.interrupt rt2",
            ),
            calls.toList(),
        )

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }
}
