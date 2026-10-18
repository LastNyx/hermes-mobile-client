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
import org.junit.Test
import java.util.Collections

class ProfileChatTest {
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

    /** Opens one chat against a mock gateway and returns the `session.create` / `session.resume` params it sent. */
    private fun opened(stored: String?, profile: String?): JSONObject = runBlocking {
        val sent = Collections.synchronizedList(mutableListOf<JSONObject>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (frame.optString("method")) {
                    "client.capabilities" -> result("{}")
                    "session.create", "session.resume" -> {
                        sent += frame.getJSONObject("params")
                        result("""{"session_id":"rt1","stored_session_id":"${stored ?: "s-new"}","running":false}""")
                    }
                    "process.list" -> result("""{"processes":[]}""")
                }
            }
        }
        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                request.path!!.contains("/messages") -> MockResponse().setBody("""{"messages":[]}""")
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
        ChatController(link, SessionsRepository(authed, base, profile), stored, scope, profile = profile).open()
        sent.single()
    }

    @Test
    fun aProfileRidesOnCreateAndResume() {
        assertEquals("work", opened(null, "work").getString("profile"))
        val resume = opened("stored-1", "work")
        assertEquals("work", resume.getString("profile"))
        assertEquals("stored-1", resume.getString("session_id"))
    }

    @Test
    fun noProfileMeansNoKey() {
        assertFalse(opened(null, null).has("profile"))
        assertFalse(opened("stored-1", null).has("profile"))
        assertFalse(opened("stored-1", "default").has("profile"))
    }
}
