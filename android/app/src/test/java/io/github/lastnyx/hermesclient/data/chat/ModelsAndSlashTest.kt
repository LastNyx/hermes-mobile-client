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

class ModelsAndSlashTest {
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
    fun modelReasoningAndSlashCommandsUseDocumentedCalls() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
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
                    "client.capabilities" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false}""")
                    "model.options" -> {
                        calls += "model.options ${params.getString("session_id")}"
                        result("""{"providers":[{"slug":"anthropic","name":"Anthropic","models":["opus","sonnet"],"is_current":true},{"slug":"openai","name":"OpenAI","models":["gpt-5"]}],"model":"sonnet","provider":"anthropic"}""")
                    }
                    "config.get" -> {
                        calls += "config.get ${params.getString("session_id")} ${params.getString("key")}"
                        result("""{"value":"medium","display":"medium"}""")
                    }
                    "config.set" -> {
                        calls += "config.set ${params.getString("session_id")} ${params.getString("key")} ${params.getString("value")} ${params.length()}"
                        result("""{"key":"x","value":"y"}""")
                    }
                    "commands.catalog" -> result("""{"pairs":[["status","Show status"],["goal","Set a goal"],["compress","Compact"]],"sub":{},"canon":{},"commands":{},"categories":[],"skills":{},"skill_count":0,"warning":""}""")
                    "slash.exec" -> {
                        calls += "slash.exec ${params.getString("session_id")} ${params.getString("command")} ${params.length()}"
                        result("""{"output":"All good"}""")
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
        val loaded = models.state.value
        assertEquals(listOf("anthropic" to listOf("opus", "sonnet"), "openai" to listOf("gpt-5")), loaded.groups.map { it.slug to it.models })
        assertEquals("sonnet", loaded.model)
        assertEquals("anthropic", loaded.provider)
        assertEquals("medium", loaded.reasoningEffort)
        assertEquals("medium", loaded.reasoningDisplay)
        assertEquals("", reasoningFromConfig(JSONObject("{}")).value)
        assertEquals("auto", reasoningFromConfig(JSONObject("""{"display":"auto"}""")).display)

        models.selectModel("openai", "gpt-5")
        models.selectReasoning("high")
        assertEquals("gpt-5", models.state.value.model)

        val slash = SlashCommands(chat)
        slash.load()
        assertEquals(listOf("/status", "/goal", "/compress"), slash.suggestions("/").map { it.text })
        slash.runLine("/status")

        assertEquals(
            listOf(
                "model.options rt1",
                "config.get rt1 reasoning",
                "config.set rt1 model gpt-5 --provider openai --session 3",
                "config.set rt1 reasoning high 3",
                "slash.exec rt1 status 2",
            ),
            calls.toList(),
        )
        assertEquals("All good", (chat.state.value.items.last() as ChatItem.CommandOutput).text)
        assertEquals(
            listOf(ChatItem.CommandOutput("n0", "/status", "All good", false)),
            chat.state.value.items.filterIsInstance<ChatItem.CommandOutput>().map { it.copy(key = "n0") },
        )

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }
}
