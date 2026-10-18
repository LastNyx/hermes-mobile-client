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

class HistoryAttachmentsTest {
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

    private fun shown(messagesJson: String): List<ChatItem.Message> = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val id = JSONObject(text).optString("id")
                val body = when (JSONObject(text).optString("method")) {
                    "session.resume", "session.activate" -> """{"session_id":"rt1","stored_session_id":"stored-1","running":false}"""
                    "process.list" -> """{"processes":[]}"""
                    else -> "{}"
                }
                webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
            }
        }
        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                request.path!!.startsWith("/api/sessions/stored-1/messages") -> MockResponse().setBody(messagesJson)
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
        withTimeout(8000) { while (link.state.value != LinkState.Connected) delay(10) }
        val chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
        chat.open()
        withTimeout(8000) { while (chat.state.value.items.isEmpty()) delay(10) }
        val out = chat.state.value.items.filterIsInstance<ChatItem.Message>()
        link.stop()
        gateway.shutdown()
        out
    }

    @Test
    fun userImagesBecomePathsFilesStayLinesAndAssistantTextIsUntouched() {
        val rows = shown(
            """{"session_id":"stored-1","messages":[
              {"id":1,"role":"user","content":"@file:/h/a.txt\n\nhello"},
              {"id":2,"role":"assistant","content":"@file:/x"},
              {"id":3,"role":"user","content":"@image:/h/p.png\nWhat do you see in this image?"}]}""",
        )
        assertEquals(listOf("user:hello\n📎 a.txt", "assistant:@file:/x", "user:What do you see in this image?"), rows.map { "${it.role}:${it.text}" })
        assertEquals(listOf(emptyList(), emptyList(), listOf("/h/p.png")), rows.map { it.images })
    }
}
