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

class FriendlyErrorChatTest {
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

    /** [onFailing] gets the socket and request id of the one method under test; the rest is answered ok. */
    private fun scenario(failing: String, onFailing: (WebSocket, String) -> Unit, body: suspend (ChatController) -> Unit) = runBlocking {
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                val id = json.optString("id")
                when (json.optString("method")) {
                    failing -> onFailing(webSocket, id)
                    "session.resume" -> webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":{"session_id":"rt1","stored_session_id":"stored-1","running":false}}""")
                    else -> webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":{}}""")
                }
            }
        }
        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                request.path!!.startsWith("/api/sessions/stored-1/messages") -> MockResponse().setBody("""{"session_id":"stored-1","messages":[]}""")
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
        withTimeout(8000) { while (link.state.value != LinkState.Connected) delay(10) }
        val chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
        chat.open()
        body(chat)
        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    private fun rpcError(socket: WebSocket, id: String, code: Int, message: String) =
        socket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":$code,"message":"$message"}}""")

    @Test
    fun aBusyGatewayShowsTheBusySentence() = scenario("prompt.submit", { s, id -> rpcError(s, id, 4009, "session busy secret-host") }) { chat ->
        chat.send("hi")
        assertEquals("The agent is busy. Wait for it to finish, then try again.", chat.state.value.error)
    }

    @Test
    fun aDroppedSocketDuringSendShowsTheCantReachSentence() = scenario("prompt.submit", { s, _ -> s.close(1001, "") }) { chat ->
        chat.send("hi")
        assertEquals("Can't reach the gateway. Check your connection.", chat.state.value.error)
    }

    @Test
    fun aFailedModelChangeShowsAFriendlySentenceNotTheServerText() = scenario("config.set", { s, id -> rpcError(s, id, 5000, "boom secret-host") }) { chat ->
        val models = ModelsController(chat)
        models.selectModel("anthropic", "opus")
        assertEquals("The gateway hit an error. Try again.", models.state.value.error)
    }
}
