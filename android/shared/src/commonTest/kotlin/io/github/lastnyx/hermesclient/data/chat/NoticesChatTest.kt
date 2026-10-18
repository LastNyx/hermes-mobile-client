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
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticesChatTest {
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

    private class Rig {
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
                    val id = JSONObject(text).optString("id")
                    val body = when (JSONObject(text).optString("method")) {
                        "session.resume" -> """{"session_id":"rt1","stored_session_id":"stored-1","running":false}"""
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
            chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope, processPollMs = 100, clock = System::currentTimeMillis)
        }

        fun event(type: String, payload: String, session: String? = "rt1") {
            val sid = session?.let { """"session_id":"$it",""" }.orEmpty()
            socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"$type",$sid"payload":$payload}}""")
        }
    }

    private companion object {
        suspend fun awaitTrue(check: () -> Boolean) {
            withTimeout(8000) { while (!check()) delay(10) }
        }
    }

    @Test
    fun noticesShowClearExpireAndDismiss() = runBlocking {
        val rig = Rig()
        rig.chat.open()

        rig.event("notification.show", """{"text":"other","level":"info"}""", session = "rt9")
        rig.event("notification.show", """{"text":"none","level":"info"}""", session = null)
        rig.event("notification.show", """{"text":"hello","level":"warn","kind":"sticky","key":"k1"}""")
        awaitTrue { rig.chat.state.value.notices.size == 1 }
        delay(200)
        assertEquals(listOf("hello"), rig.chat.state.value.notices.map { it.text })

        rig.event("notification.clear", """{"key":"k1"}""")
        awaitTrue { rig.chat.state.value.notices.isEmpty() }

        rig.event("notification.show", """{"text":"brief","kind":"ttl","ttl_ms":200}""")
        awaitTrue { rig.chat.state.value.notices.size == 1 }
        awaitTrue { rig.chat.state.value.notices.isEmpty() }

        rig.event("notification.show", """{"text":"stay","kind":"sticky"}""")
        awaitTrue { rig.chat.state.value.notices.size == 1 }
        rig.chat.dismissNotice(rig.chat.state.value.notices.single().uid)
        assertTrue(rig.chat.state.value.notices.isEmpty())
    }
}
