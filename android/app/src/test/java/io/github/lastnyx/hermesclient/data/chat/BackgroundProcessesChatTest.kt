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
import java.util.Collections

class BackgroundProcessesChatTest {
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

    private class Rig(listBody: String, val killReply: String) {
        val lists = Collections.synchronizedList(mutableListOf<JSONObject>())
        val kills = Collections.synchronizedList(mutableListOf<JSONObject>())
        @Volatile var listBody = listBody
        @Volatile var socket: WebSocket? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val chat: ChatController
        val link: LiveLink

        init {
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socket = webSocket
                    webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val frame = JSONObject(text)
                    val id = frame.optString("id")
                    fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                    when (frame.optString("method")) {
                        "client.capabilities" -> result("{}")
                        "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false}""")
                        "process.list" -> { lists += frame.getJSONObject("params"); result(this@Rig.listBody) }
                        "process.kill" -> {
                            kills += frame.getJSONObject("params")
                            if (this@Rig.killReply == "error") webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":5000,"message":"boom"}}""")
                            else result(this@Rig.killReply)
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
            link = LiveLink(http, authed, base, scope, FakeNetwork(), random = { 0.0 })
            link.start()
            runBlocking { awaitTrue { link.state.value == LinkState.Connected } }
            chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope, processPollMs = 100)
        }

        fun event(type: String, payload: String) =
            socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"$type","session_id":"rt1","payload":$payload}}""")
    }

    private companion object {
        suspend fun awaitTrue(check: () -> Boolean) {
            withTimeout(8000) { while (!check()) delay(10) }
        }

        const val RUNNING = """{"processes":[{"session_id":"p1","command":"sleep 9","status":"running"}]}"""
        const val NONE = """{"processes":[]}"""
    }

    @Test
    fun listsOnOpenAndOnTriggersOnly() = runBlocking {
        val rig = Rig(NONE, "{}")
        rig.chat.open()
        awaitTrue { rig.lists.size == 1 }
        assertEquals("rt1", rig.lists[0].getString("session_id"))

        rig.event("status.update", """{"kind":"other","text":"x"}""")
        rig.event("tool.complete", """{"tool_id":"t1","name":"terminal"}""")
        awaitTrue { rig.lists.size == 2 }
        rig.event("status.update", """{"kind":"process","text":"done"}""")
        awaitTrue { rig.lists.size == 3 }
        delay(400)
        assertEquals(3, rig.lists.size)
    }

    @Test
    fun reconnectRefreshesProcessesExactlyOnce() = runBlocking {
        val rig = Rig(NONE, "{}")
        rig.chat.open()
        awaitTrue { rig.lists.size == 1 }

        val old = rig.socket
        old!!.close(1001, "")
        awaitTrue { rig.socket !== old && rig.link.state.value == LinkState.Connected }
        awaitTrue { rig.lists.size == 2 }
        delay(400)
        assertEquals(2, rig.lists.size)
    }

    @Test
    fun pollsOnlyWhileSomethingRuns() = runBlocking {
        val rig = Rig(RUNNING, "{}")
        rig.chat.open()
        awaitTrue { rig.chat.state.value.processes.size == 1 }
        awaitTrue { rig.lists.size >= 3 }
        rig.listBody = NONE
        awaitTrue { rig.chat.state.value.processes.isEmpty() }
        delay(300)
        val settled = rig.lists.size
        delay(500)
        assertEquals(settled, rig.lists.size)
    }

    @Test
    fun killSendsIdsAndRefreshesButErrorLeavesRow() = runBlocking {
        val rig = Rig(RUNNING, """{"status":"killed"}""")
        rig.chat.open()
        awaitTrue { rig.chat.state.value.processes.size == 1 }
        rig.listBody = NONE
        rig.chat.killProcess("p1")
        assertEquals(listOf("process_id", "session_id"), rig.kills.single().keys().asSequence().sorted().toList())
        assertEquals("p1", rig.kills.single().getString("process_id"))
        awaitTrue { rig.chat.state.value.processes.isEmpty() }

        val bad = Rig(RUNNING, "error")
        bad.chat.open()
        awaitTrue { bad.chat.state.value.processes.size == 1 }
        bad.chat.killProcess("p1")
        assertEquals(1, bad.kills.size)
        assertTrue(bad.chat.state.value.processes.single().state == ProcessState.Running)
    }
}
