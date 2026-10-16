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
import java.util.Collections
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

class RewindChatTest {
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

    /** [submit] answers each `prompt.submit` by its attempt number (1-based) with a raw JSON-RPC fragment, or null to drop the socket. */
    private class Rig(val submit: (Int) -> String?) {
        val submits = Collections.synchronizedList(mutableListOf<JSONObject>())
        val resumes = Collections.synchronizedList(mutableListOf<Int>())
        @Volatile var historyRequests = 0
        @Volatile var historyFails = false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val chat: ChatController

        init {
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val frame = JSONObject(text)
                    val id = frame.optString("id")
                    fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                    when (frame.optString("method")) {
                        "client.capabilities", "process.list" -> result("{}")
                        "session.resume" -> { resumes += 1; result("""{"session_id":"rt${resumes.size}","stored_session_id":"stored-1","running":false}""") }
                        "prompt.submit" -> {
                            submits += frame.getJSONObject("params")
                            val reply = submit(submits.size)
                            if (reply == null) webSocket.close(1001, "") else webSocket.send("""{"jsonrpc":"2.0","id":"$id",$reply}""")
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
                        historyRequests++
                        if (historyFails && historyRequests > 1) MockResponse().setResponseCode(500)
                        else MockResponse().setBody(
                            """{"session_id":"stored-1","messages":[
                            {"id":11,"role":"user","content":"q1"},{"id":12,"role":"assistant","content":"a1"},
                            {"id":13,"role":"user","content":"q2"},{"id":14,"role":"assistant","content":"a2"}]}""",
                        )
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
            chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope, processPollMs = 100000)
            runBlocking { chat.open() }
        }
    }

    private companion object {
        suspend fun awaitTrue(check: () -> Boolean) {
            withTimeout(8000) { while (!check()) delay(10) }
        }

        const val OK = """"result":{"status":"streaming"}"""
        fun err(code: Int) = """"error":{"code":$code,"message":"x"}"""
        fun texts(r: Rig) = r.chat.state.value.items.map { (it as ChatItem.Message).text }
    }

    @Test
    fun rewindCutsAndResendsAndMapsRefusals() = runBlocking {
        val r = Rig { OK }
        val items = r.chat.state.value.items.map { it as ChatItem.Message }
        assertEquals(listOf<Long?>(11, 12, 13, 14), items.map { it.rowId })

        assertEquals(RewindResult.Empty, r.chat.rewind(13, "   "))
        assertEquals(RewindResult.Stale, r.chat.rewind(999, "x"))
        assertEquals(RewindResult.Stale, r.chat.rewind(14, "x"))
        assertTrue(r.submits.isEmpty())

        assertEquals(RewindResult.Sent, r.chat.rewind(13, "  new "))
        val p = r.submits.single()
        assertEquals(setOf("session_id", "text", "confirm_truncate", "truncate_before_row_id"), p.keys().asSequence().toSet())
        assertEquals("new", p.getString("text"))
        assertEquals(13L, p.getLong("truncate_before_row_id"))
        assertEquals(listOf("q1", "a1", "new"), texts(r))
        assertTrue(r.chat.state.value.running)

        assertEquals(RewindResult.Busy, r.chat.rewind(11, "again"))
        assertEquals(1, r.submits.size)
    }

    @Test
    fun firstUserMessageAsksToEraseAll() = runBlocking {
        val r = Rig { OK }
        assertEquals(RewindResult.Sent, r.chat.rewind(11, "new"))
        assertTrue(r.submits.single().getBoolean("confirm_empty_truncate"))
        assertEquals(listOf("new"), texts(r))
    }

    @Test
    fun refusalsLeaveTheChatAsItWas() = runBlocking {
        val codes = mapOf(4009 to RewindResult.Busy, 4028 to RewindResult.WouldEraseAll, 4004 to RewindResult.NoTarget)
        for ((code, expected) in codes) {
            val r = Rig { err(code) }
            val before = r.chat.state.value.items
            assertEquals(expected, r.chat.rewind(13, "new"))
            assertEquals(before, r.chat.state.value.items)
            assertFalse(r.chat.state.value.running)
        }
        val gone = Rig { """"error":{"code":4018,"message":"x","data":{"user_turn_count":2,"ordinal":1}}""" }
        gone.historyFails = true
        assertEquals(RewindResult.Stale, gone.chat.rewind(13, "new"))
        assertEquals(2, gone.historyRequests)
        assertEquals(4, gone.chat.state.value.items.size)

        val dropped = Rig { null }
        val before = dropped.chat.state.value.items
        assertEquals(RewindResult.Unreachable, dropped.chat.rewind(13, "new"))
        assertEquals(before, dropped.chat.state.value.items)
    }

    @Test
    fun aStaleRuntimeIdResumesAndRetriesTheSameParams() = runBlocking {
        val r = Rig { n -> if (n == 1) err(4001) else OK }
        assertEquals(RewindResult.Sent, r.chat.rewind(13, "new"))
        assertEquals(2, r.submits.size)
        assertEquals(r.submits[0].getLong("truncate_before_row_id"), r.submits[1].getLong("truncate_before_row_id"))
        assertEquals("new", r.submits[1].getString("text"))
        assertEquals(2, r.resumes.size)
    }
}
