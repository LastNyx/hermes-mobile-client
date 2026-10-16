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
import kotlinx.coroutines.async
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

class HistoryPagingChatTest {
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

    /** Rows `from..to` as raw history rows, ids equal to their number. */
    private fun rows(from: Int, to: Int) =
        (from..to).joinToString(",") { """{"id":$it,"role":"user","content":"r$it"}""" }

    private class Rig(val answer: (offset: Int) -> MockResponse) {
        val offsets = Collections.synchronizedList(mutableListOf<Int>())
        @Volatile var delayMs = 0L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val chat: ChatController

        init {
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val id = JSONObject(text).optString("id")
                    val body = when (JSONObject(text).optString("method")) {
                        "session.resume" -> """{"session_id":"rt1","stored_session_id":"stored-1","running":false}"""
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
                    request.path!!.startsWith("/api/sessions/stored-1/messages") -> {
                        val offset = request.requestUrl!!.queryParameter("offset")!!.toInt()
                        offsets += offset
                        answer(offset).setBodyDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
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
        }

        fun texts() = chat.state.value.items.filterIsInstance<ChatItem.Message>().map { it.text }
    }

    private companion object {
        suspend fun awaitTrue(check: () -> Boolean) {
            withTimeout(8000) { while (!check()) delay(10) }
        }
    }

    private fun page(from: Int, to: Int, returned: Int = to - from + 1) =
        MockResponse().setBody("""{"messages":[${rows(from, to)}],"pagination":{"limit":120,"offset":0,"order":"latest","returned":$returned}}""")

    @Test
    fun shortChatHasNoOlderRows() = runBlocking {
        val rig = Rig { page(1, 5) }
        rig.chat.open()
        assertFalse(rig.chat.state.value.hasOlder)
        rig.chat.loadOlder()
        assertEquals(listOf(0), rig.offsets)
    }

    @Test
    fun olderPagesPrependOnceWithoutDuplicates() = runBlocking {
        val rig = Rig { offset ->
            when (offset) {
                0 -> page(201, 320)
                120 -> page(80, 200, returned = 121)
                else -> page(1, 10, returned = 10)
            }
        }
        rig.chat.open()
        assertTrue(rig.chat.state.value.hasOlder)
        assertEquals(120, rig.texts().size)

        rig.delayMs = 300
        val first = async(Dispatchers.IO) { rig.chat.loadOlder() }
        val second = async(Dispatchers.IO) { rig.chat.loadOlder() }
        first.await(); second.await()
        assertEquals(listOf(0, 120), rig.offsets)
        // Row 200 does not overlap; 80..200 are 121 rows, all new.
        assertEquals(241, rig.texts().size)
        assertEquals("r80", rig.texts().first())
        assertEquals("r320", rig.texts().last())
        assertTrue(rig.chat.state.value.hasOlder)

        rig.delayMs = 0
        rig.chat.loadOlder()
        assertEquals(listOf(0, 120, 241), rig.offsets)
        assertEquals(listOf("r1", "r2", "r3"), rig.texts().take(3))
        assertEquals(251, rig.texts().size)
        assertEquals(251, rig.chat.state.value.items.map { it.key }.toSet().size)
        assertFalse(rig.chat.state.value.hasOlder)
        rig.chat.loadOlder()
        assertEquals(3, rig.offsets.size)
    }

    @Test
    fun overlapRowAddsNoDuplicate() = runBlocking {
        val rig = Rig { offset -> if (offset == 0) page(11, 130) else page(1, 11, returned = 11) }
        rig.chat.open()
        rig.chat.loadOlder()
        assertEquals(130, rig.texts().size)
        assertEquals(1, rig.texts().count { it == "r11" })
        assertFalse(rig.chat.state.value.hasOlder)
    }

    @Test
    fun idLessRowsKeepDistinctKeysAcrossPages() = runBlocking {
        fun idLess(from: Int, to: Int, returned: Int) = MockResponse().setBody(
            """{"messages":[${(from..to).joinToString(",") { """{"role":"user","content":"r$it"}""" }}],"pagination":{"limit":120,"offset":0,"order":"latest","returned":$returned}}"""
        )
        val rig = Rig { offset -> if (offset == 0) idLess(201, 320, 120) else idLess(1, 5, 5) }
        rig.chat.open()
        rig.chat.loadOlder()
        val keys = rig.chat.state.value.items.filterIsInstance<ChatItem.Message>().map { it.key }
        assertEquals(125, keys.size)
        assertEquals(125, keys.toSet().size)
    }

    @Test
    fun failedOlderPageKeepsEverythingAndSetsError() = runBlocking {
        val rig = Rig { offset -> if (offset == 0) page(1, 120) else MockResponse().setResponseCode(500) }
        rig.chat.open()
        rig.chat.loadOlder()
        assertEquals(120, rig.texts().size)
        assertTrue(rig.chat.state.value.hasOlder)
        assertEquals("Couldn't load earlier messages", rig.chat.state.value.error)
    }
}
