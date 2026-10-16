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
import org.junit.Assert.assertNotNull
import org.junit.Test

class SendResultTest {
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

    private fun scenario(running: Boolean, submitFails: Boolean, redirectFails: Boolean, body: suspend (ChatController) -> Unit) = runBlocking {
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val id = JSONObject(text).optString("id")
                fun result(b: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$b}""")
                fun fail() = webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":5000,"message":"boom"}}""")
                when (JSONObject(text).optString("method")) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":$running}""")
                    "prompt.submit" -> if (submitFails) fail() else result("""{"status":"streaming"}""")
                    "session.redirect" -> if (redirectFails) fail() else result("""{"status":"ok"}""")
                    else -> result("{}")
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
        awaitTrue { link.state.value == LinkState.Connected }
        val chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
        chat.open()
        body(chat)
        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun aFailedSubmitReturnsFalseAndSetsTheError() = scenario(false, submitFails = true, redirectFails = false) { chat ->
        assertEquals(false, chat.send("x"))
        assertNotNull(chat.state.value.error)
    }

    @Test
    fun aSuccessfulSubmitReturnsTrue() = scenario(false, submitFails = false, redirectFails = false) { chat ->
        assertEquals(true, chat.send("x"))
    }

    @Test
    fun aFailedRedirectDuringARunningTurnQueuesTheTextAndReturnsTrue() = scenario(true, submitFails = false, redirectFails = true) { chat ->
        assertEquals(true, chat.send("x"))
        assertEquals(listOf("x"), chat.state.value.queue.map { it.text })
    }
}
