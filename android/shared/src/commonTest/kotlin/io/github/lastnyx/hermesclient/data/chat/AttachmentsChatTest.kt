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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class AttachmentsChatTest {
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

    private class Rig(val submitFails: Boolean = false) {
        val calls = Collections.synchronizedList(mutableListOf<Pair<String, JSONObject>>())
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
                    val method = frame.optString("method")
                    fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                    if (method != "client.capabilities" && method != "session.resume" && method != "process.list" && method != "subagent.list") calls += method to frame.getJSONObject("params")
                    when (method) {
                        "client.capabilities", "process.list", "subagent.list" -> result("{}")
                        "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false}""")
                        "image.attach_bytes" -> result("""{"attached":true,"path":"/img/1.jpg"}""")
                        "file.attach" -> result("""{"ref_text":"@file:notes.pdf"}""")
                        "image.detach" -> result("""{"detached":true,"count":0}""")
                        "prompt.submit" ->
                            if (submitFails) webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":5000,"message":"boom"}}""")
                            else result("""{"status":"streaming"}""")
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
            val link = LiveLink(http, authed, base, scope, FakeNetwork(), random = { 0.0 })
            link.start()
            runBlocking { awaitTrue { link.state.value == LinkState.Connected } }
            chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
        }
    }

    private companion object {
        suspend fun awaitTrue(check: () -> Boolean) {
            withTimeout(8000) { while (!check()) delay(10) }
        }

        fun image() = PreparedAttachment("a.jpg", AttachmentKind.Image, byteArrayOf(1), "image/jpeg")
        fun pdf() = PreparedAttachment("notes.pdf", AttachmentKind.File, byteArrayOf(2), "application/pdf")
    }

    @Test
    fun aPhotoIsAttachedThenSubmittedWithTheDefaultSentence() = runBlocking {
        val rig = Rig()
        rig.chat.open()
        assertEquals(AttachSendResult.Sent, rig.chat.sendWithAttachments("", listOf(image())))
        awaitTrue { rig.calls.any { it.first == "prompt.submit" } }
        assertEquals(listOf("image.attach_bytes", "prompt.submit"), rig.calls.map { it.first })
        val submit = rig.calls.last().second
        assertEquals(setOf("session_id", "text"), submit.keys().asSequence().toSet())
        assertEquals("What do you see in this image?", submit.getString("text"))
        assertEquals("[image]", (rig.chat.state.value.items.single() as ChatItem.Message).text)
    }

    @Test
    fun aFilePromptHasItsRefLineAndTheTypedText() = runBlocking {
        val rig = Rig()
        rig.chat.open()
        assertEquals(AttachSendResult.Sent, rig.chat.sendWithAttachments("summarise", listOf(pdf())))
        awaitTrue { rig.calls.any { it.first == "prompt.submit" } }
        assertEquals("@file:notes.pdf\n\nsummarise", rig.calls.last().second.getString("text"))
        assertEquals("summarise\n📎 notes.pdf", (rig.chat.state.value.items.single() as ChatItem.Message).text)
    }

    @Test
    fun aFailedSubmitDetachesAndLeavesNothingBehind() = runBlocking {
        val rig = Rig(submitFails = true)
        rig.chat.open()
        val result = rig.chat.sendWithAttachments("hello", listOf(image()))
        assertEquals(AttachSendResult.Failed("Couldn't send your message. Try again.", "hello"), result)
        awaitTrue { rig.calls.any { it.first == "image.detach" } }
        val detach = rig.calls.single { it.first == "image.detach" }.second
        assertEquals("/img/1.jpg", detach.getString("path"))
        assertEquals("rt1", detach.getString("session_id"))
        val s = rig.chat.state.value
        assertTrue(s.items.isEmpty())
        assertEquals(false, s.running)
        assertNull(s.error)
    }

    @Test
    fun sendingWhileRunningSendsNothing() = runBlocking {
        val rig = Rig()
        rig.chat.open()
        rig.socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"message.start","session_id":"rt1","payload":{}}}""")
        awaitTrue { rig.chat.state.value.running }
        val result = rig.chat.sendWithAttachments("x", listOf(image()))
        assertEquals(AttachSendResult.Failed("Wait for the reply to finish before sending attachments.", "x"), result)
        assertTrue(rig.calls.isEmpty())
    }
}
