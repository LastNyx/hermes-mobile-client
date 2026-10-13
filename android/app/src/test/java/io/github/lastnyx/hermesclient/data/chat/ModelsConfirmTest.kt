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
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Collections

class ModelsConfirmTest {
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

    @Test
    fun expensiveModelIsConfirmedOrCancelled() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
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
                    "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false}""")
                    "model.options" -> result("""{"providers":[{"slug":"anthropic","name":"Anthropic","models":["sonnet"]},{"slug":"openai","name":"OpenAI","models":["gpt-5"]}],"model":"sonnet","provider":"anthropic"}""")
                    "config.set" -> {
                        val confirmed = params.optBoolean("confirm_expensive_model")
                        calls += "config.set ${params.getString("value")} confirm=$confirmed ${params.length()}"
                        if (confirmed) result("""{"key":"model","value":"gpt-5"}""")
                        else result("""{"confirm_required":true,"confirm_message":"gpt-5 is expensive. Continue?"}""")
                    }
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

        val models = ModelsController(chat)
        models.load()

        models.selectModel("openai", "gpt-5")
        assertEquals("gpt-5 is expensive. Continue?", models.state.value.confirmMessage)
        assertEquals("sonnet", models.state.value.model)

        models.cancelConfirm()
        assertNull(models.state.value.confirmMessage)
        assertEquals("sonnet", models.state.value.model)

        models.selectModel("openai", "gpt-5")
        models.confirmPending()
        assertNull(models.state.value.confirmMessage)
        assertEquals("gpt-5", models.state.value.model)
        assertEquals("openai", models.state.value.provider)

        assertEquals(
            listOf(
                "config.set gpt-5 --provider openai --session confirm=false 3",
                "config.set gpt-5 --provider openai --session confirm=false 3",
                "config.set gpt-5 --provider openai --session confirm=true 4",
            ),
            calls.toList(),
        )

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }
}
