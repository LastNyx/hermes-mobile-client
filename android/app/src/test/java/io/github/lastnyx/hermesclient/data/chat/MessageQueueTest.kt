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

class MessageQueueTest {
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

    private suspend fun awaitTrue(check: () -> Boolean) {
        withTimeout(8000) { while (!check()) delay(10) }
    }

    @Test
    fun queueKeepsOrderIgnoresBlankEditsRemovesAndYieldsNext() {
        val queue = MessageQueue()
        val a = queue.add("first")!!
        assertEquals(null, queue.add("   "))
        val b = queue.add("second")!!
        val c = queue.add("third")!!
        assertEquals(listOf("first", "second", "third"), queue.entries.map { it.text })
        queue.edit(b.id, "second!")
        queue.remove(c.id)
        assertEquals(listOf("first", "second!"), queue.entries.map { it.text })
        assertEquals(a, queue.next())
        assertEquals(listOf("second!"), queue.entries.map { it.text })
        assertEquals(null, queue.addFirst("  "))
        queue.addFirst("zero")
        assertEquals(listOf("zero", "second!"), queue.entries.map { it.text })
    }

    @Test
    fun failedSubmitOfDrainedEntryPutsItBackAtFrontAndDoesNotRetryOnItsOwn() = runBlocking {
        val submits = Collections.synchronizedList(mutableListOf<String>())
        var socket: WebSocket? = null
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
                    "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":true}""")
                    "prompt.submit" -> {
                        submits += frame.getJSONObject("params").getString("text")
                        webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":5000,"message":"boom"}}""")
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
        awaitTrue { link.state.value == LinkState.Connected }

        val chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
        chat.open()
        var seq = 0
        fun complete() {
            seq++
            socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"rt1","payload":{"text":"done","status":"complete"},"seq":$seq}}""")
        }

        chat.queue("one")
        chat.queue("two")
        complete()
        awaitTrue { submits.size == 1 && chat.state.value.error == "boom" }
        assertEquals(listOf("one", "two"), chat.state.value.queue.map { it.text })

        complete()
        delay(300)
        assertEquals(listOf("one"), submits.toList())
        assertEquals(listOf("one", "two"), chat.state.value.queue.map { it.text })

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun queuedTextsDrainOncePerCleanCompletionAndSendNowInterruptsThenSubmitsThatEntry() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val submits = Collections.synchronizedList(mutableListOf<String>())
        var socket: WebSocket? = null
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
                calls += method
                when (method) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":true}""")
                    "prompt.submit" -> { submits += frame.getJSONObject("params").getString("text"); result("{}") }
                    "session.interrupt" -> result("""{"status":"interrupted"}""")
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
        awaitTrue { link.state.value == LinkState.Connected }

        val chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
        chat.open()
        var seq = 0
        fun complete(status: String) {
            seq++
            socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"rt1","payload":{"text":"done","status":"$status"},"seq":$seq}}""")
        }

        chat.queue("one")
        chat.queue("two")
        chat.queue("three")
        assertEquals(listOf("one", "two", "three"), chat.state.value.queue.map { it.text })

        complete("complete")
        awaitTrue { submits.size == 1 }
        assertEquals("one", submits[0])
        assertEquals(true, chat.state.value.items.any { it is ChatItem.Message && it.role == "user" && it.text == "one" })
        assertEquals(listOf("two", "three"), chat.state.value.queue.map { it.text })

        complete("error")
        delay(300)
        assertEquals(listOf("one"), submits.toList())
        assertEquals(listOf("two", "three"), chat.state.value.queue.map { it.text })

        chat.send("fresh")
        assertEquals(listOf("one", "fresh"), submits.toList())
        chat.sendNow(chat.state.value.queue[1].id)
        assertEquals(true, calls.contains("session.interrupt"))
        assertEquals(listOf("one", "fresh"), submits.toList())
        complete("interrupted")
        awaitTrue { submits.size == 3 }
        assertEquals("three", submits[2])
        assertEquals(listOf("two"), chat.state.value.queue.map { it.text })

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }
}
