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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaBatchingChatTest {
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

    private class Rig(batchMs: Long) {
        @Volatile var socket: WebSocket? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val chat: ChatController

        init {
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socket = webSocket
                    webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val frame = JSONObject(text)
                    val id = frame.optString("id")
                    val body = when (frame.optString("method")) {
                        "client.capabilities", "prompt.submit", "session.redirect" -> "{}"
                        "session.resume" -> """{"session_id":"rt1","stored_session_id":"stored-1","running":false}"""
                        "process.list" -> """{"processes":[]}"""
                        else -> return
                    }
                    webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
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
            val link = LiveLink(http, authed, base, scope, FakeNetwork(), random = { 0.0 })
            link.start()
            runBlocking { awaitTrue { link.state.value == LinkState.Connected } }
            chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope, deltaBatchMs = batchMs)
        }

        fun event(type: String, payload: String, seq: Int) =
            socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"$type","session_id":"rt1","payload":$payload,"seq":$seq}}""")
    }

    private companion object {
        suspend fun awaitTrue(check: () -> Boolean) {
            withTimeout(8000) { while (!check()) delay(10) }
        }
    }

    private fun ChatState.view() = items.map {
        when (it) {
            is ChatItem.Message -> "M:${it.text}:${it.streaming}"
            is ChatItem.Tool -> "T:${it.name}"
            else -> "?"
        }
    }

    @Test
    fun deltasWaitInTheWindowAndFlushBeforeTheNextEvent() = runBlocking {
        val rig = Rig(60_000)
        rig.chat.open()
        rig.event("message.delta", """{"text":"Hel"}""", 1)
        rig.event("message.delta", """{"text":"lo"}""", 2)
        delay(300)
        assertTrue(rig.chat.state.value.items.isEmpty())
        rig.event("tool.start", """{"tool_id":"1","name":"ls"}""", 3)
        awaitTrue { rig.chat.state.value.items.size == 2 }
        assertEquals(listOf("M:Hello:false", "T:ls"), rig.chat.state.value.view())
    }

    @Test
    fun deltasFlushOnTheirOwnAfterTheWindow() = runBlocking {
        val rig = Rig(30)
        rig.chat.open()
        rig.event("message.delta", """{"text":"Hel"}""", 1)
        rig.event("message.delta", """{"text":"lo"}""", 2)
        awaitTrue { (rig.chat.state.value.items.lastOrNull() as? ChatItem.Message)?.text == "Hello" }
        rig.event("message.complete", """{"status":"complete"}""", 3)
        awaitTrue { (rig.chat.state.value.items.single() as ChatItem.Message).streaming.not() }
    }

    @Test
    fun duplicateSeqIsDroppedAndASendKeepsKeysUnique() = runBlocking {
        val rig = Rig(60_000)
        rig.chat.open()
        rig.event("message.delta", """{"text":"A"}""", 1)
        rig.event("message.delta", """{"text":"B"}""", 1)
        delay(300)
        rig.chat.send("go")
        val items = rig.chat.state.value.items
        assertEquals(listOf("M:A:true", "M:go:false"), rig.chat.state.value.view())
        assertEquals(items.size, items.map { it.key }.toSet().size)
        assertFalse(items.isEmpty())
    }
}
