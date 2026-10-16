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
import java.util.Collections

class CheckpointsChatTest {
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
        val calls = Collections.synchronizedList(mutableListOf<Pair<String, JSONObject>>())
        @Volatile var listReply = """"result":{"enabled":true,"checkpoints":[{"hash":"abc","timestamp":"t","message":"m"}]}"""
        @Volatile var diffReply = """"result":{"stat":"s","diff":"d"}"""
        @Volatile var restoreReply = """"result":{"success":true,"restored_to":"abc"}"""
        @Volatile var messagesBody = """{"session_id":"stored-1","messages":[]}"""
        val historyGets = java.util.concurrent.atomic.AtomicInteger()
        @Volatile var socket: WebSocket? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val chat: ChatController
        val checkpoints: CheckpointsController

        init {
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socket = webSocket
                    webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val frame = JSONObject(text)
                    val id = frame.optString("id")
                    val method = frame.optString("method")
                    fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                    fun raw(member: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id",$member}""")
                    when (method) {
                        "client.capabilities" -> result("{}")
                        "session.resume", "session.activate" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false}""")
                        "subagent.list" -> result("{}")
                        "process.list" -> result("""{"processes":[]}""")
                        "rollback.list", "rollback.diff", "rollback.restore" -> {
                            calls += method to frame.getJSONObject("params")
                            raw(
                                when (method) {
                                    "rollback.list" -> listReply
                                    "rollback.diff" -> diffReply
                                    else -> restoreReply
                                },
                            )
                        }
                    }
                }
            }
            val gateway = MockWebServer()
            gateway.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                    request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                    request.path!!.startsWith("/api/sessions/stored-1/messages") -> {
                        historyGets.incrementAndGet()
                        MockResponse().setBody(messagesBody)
                    }
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
            chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
            runBlocking { chat.open() }
            checkpoints = CheckpointsController(chat)
        }

        fun keys(i: Int) = calls[i].second.keys().asSequence().toSet()
    }

    private companion object {
        suspend fun awaitTrue(check: () -> Boolean) {
            withTimeout(8000) { while (!check()) delay(10) }
        }

        const val RESTORED = "Restored to abc"
    }

    @Test
    fun loadAndDiff() = runBlocking {
        val rig = Rig()
        rig.checkpoints.load()
        assertEquals(1, rig.calls.size)
        assertEquals("rollback.list", rig.calls[0].first)
        assertEquals(setOf("session_id"), rig.keys(0))
        assertEquals(listOf(Checkpoint("abc", "t", "m")), rig.checkpoints.state.value.checkpoints)
        assertEquals(true, rig.checkpoints.state.value.enabled)

        rig.listReply = """"result":{"enabled":false,"checkpoints":[]}"""
        rig.checkpoints.load()
        assertEquals(false, rig.checkpoints.state.value.enabled)
        assertTrue(rig.checkpoints.state.value.checkpoints.isEmpty())

        rig.checkpoints.showDiff("abc")
        assertEquals("rollback.diff", rig.calls[2].first)
        assertEquals(setOf("session_id", "hash"), rig.keys(2))
        assertEquals("abc", rig.calls[2].second.getString("hash"))
        assertEquals("s\n\nd", rig.checkpoints.state.value.diffText)

        rig.diffReply = """"error":{"code":4014,"message":"hash required"}"""
        rig.checkpoints.showDiff("")
        assertEquals("Couldn't load that diff.", rig.checkpoints.state.value.message)
    }

    @Test
    fun restoreReloadsHistoryOnSuccessOnly() = runBlocking {
        val rig = Rig()
        rig.messagesBody = """{"session_id":"stored-1","messages":[{"id":1,"role":"user","content":"after restore"}]}"""
        val before = rig.historyGets.get()
        rig.checkpoints.restore("abc")
        assertEquals("rollback.restore", rig.calls[0].first)
        assertEquals(setOf("session_id", "hash"), rig.keys(0))
        assertEquals("rollback.list", rig.calls[1].first)
        assertTrue(rig.historyGets.get() > before)
        assertTrue(rig.chat.state.value.items.any { it is ChatItem.Message && it.text == "after restore" })
        assertTrue(rig.checkpoints.state.value.message!!.startsWith(RESTORED))

        val gets = rig.historyGets.get()
        rig.restoreReply = """"result":{"success":false,"error":"container refused"}"""
        rig.checkpoints.restore("abc")
        assertEquals(gets, rig.historyGets.get())
        assertEquals("container refused", rig.checkpoints.state.value.message)
    }

    @Test
    fun restoreGuardsAndErrors() = runBlocking {
        val rig = Rig()
        rig.socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"rt1","payload":{"text":"x"}}}""")
        awaitTrue { rig.chat.state.value.running }
        rig.checkpoints.restore("abc")
        assertTrue(rig.calls.isEmpty())

        rig.chat.reloadHistory()
        rig.socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"rt1","payload":{"text":"x"}}}""")
        awaitTrue { !rig.chat.state.value.running }
        rig.restoreReply = """"error":{"code":4009,"message":"session busy"}"""
        rig.checkpoints.restore("abc")
        assertEquals("Wait for the reply to finish, then try again.", rig.checkpoints.state.value.message)

        rig.restoreReply = """"error":{"code":5021,"message":"secret gateway text"}"""
        rig.checkpoints.restore("abc")
        assertEquals("Couldn't restore that checkpoint.", rig.checkpoints.state.value.message)
        assertFalse(rig.checkpoints.state.value.message!!.contains("secret"))
    }
}
