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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class SlashCatalogTest {
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

    private val catalogJson = """{
        "pairs":[["/new","Fresh"],["/undo","Undo"],["/help","Help"],["/term","Terminal only"],["review","Review code"]],
        "categories":[
            {"name":"Session","pairs":[["/new","Fresh"],["/undo","Undo"],["/term","Terminal only"]]},
            {"name":"Info","pairs":[["/help","Help"]]}
        ],
        "canon":{"/reset":"/new"},
        "commands":{
            "/new":{"argument_mode":"none","desktop":null},
            "/reset":{"argument_mode":"none","desktop":null},
            "/undo":{"argument_mode":"none","desktop":null},
            "/help":{"argument_mode":"none","desktop":null},
            "/term":{"argument_mode":"none","desktop":"terminal"}
        },
        "warning":"careful"
    }"""

    @Test
    fun catalogAndCompletionsParseAndCallDocumentedMethods() = runBlocking {
        val catalog = parseSlashCatalog(JSONObject(catalogJson))
        assertEquals(
            listOf("/new" to "Session", "/undo" to "Session", "/help" to "Info", "/review" to "Skills"),
            catalog.suggestions.map { it.text to it.group },
        )
        assertEquals(SlashKind.Skill, catalog.suggestions.last().kind)
        assertEquals("new", catalog.canonical("Reset"))
        assertEquals("new", catalog.canonical("/reset"))
        assertTrue(catalog.unavailable("term"))
        assertFalse(catalog.unavailable("review"))
        assertFalse(catalog.unavailable("new"))
        assertEquals("careful", catalog.warning)
        assertEquals("", parseSlashCatalog(JSONObject("{}")).warning)

        val command = parseSlashCompletions(
            "/per",
            JSONObject("""{"items":[{"text":"/personality","display":"/personality","meta":"Set persona"}],"replace_from":1}"""),
        ).single()
        assertEquals(SlashKind.Command, command.kind)
        assertEquals("/personality", command.text)
        assertEquals("Set persona", command.description)
        val option = parseSlashCompletions(
            "/personality al",
            JSONObject("""{"items":[{"text":"alpha","display":"alpha","meta":""}],"replace_from":13}"""),
        ).single()
        assertEquals(SlashKind.Option, option.kind)
        assertEquals("/personality alpha", option.text)

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
                    "commands.catalog" -> result(catalogJson)
                    "complete.slash" -> {
                        calls += "complete.slash ${params.getString("session_id")} ${params.getString("text")} ${params.length()}"
                        if (params.getString("text") == "/bad") {
                            webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":5000,"message":"boom"}}""")
                        } else {
                            result("""{"items":[{"text":"/review","display":"/review","meta":"Review code","kind":"skill"},{"text":"/term","display":"/term","meta":"x"}],"replace_from":1}""")
                        }
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

        val slash = SlashCommands(chat)
        slash.load()
        val notesAfterLoad = chat.state.value.items.filterIsInstance<ChatItem.Note>().map { it.text }
        assertEquals(listOf("careful"), notesAfterLoad)
        assertEquals(catalog, slash.catalog.value)

        assertEquals(catalog.suggestions, slash.suggestions("/"))
        assertEquals(emptyList<String>(), calls.toList())
        assertEquals(listOf("/review"), slash.suggestions("/rev").map { it.text })
        assertEquals(listOf("complete.slash rt1 /rev 2"), calls.toList())
        assertEquals(emptyList<SlashSuggestion>(), slash.suggestions("/bad"))
        assertEquals(1, chat.state.value.items.filterIsInstance<ChatItem.Note>().size)

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }
}
