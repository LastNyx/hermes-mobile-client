package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.live.NetworkMonitor
import io.github.lastnyx.hermesclient.data.live.RpcError
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class SlashRunTest {
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

    private val catalogJson = """{"pairs":[["/term","T"]],"categories":[],"canon":{},"commands":{"/term":{"argument_mode":"none","desktop":"terminal"}}}"""

    @Test
    fun parsesRunsAndDispatchesSlashLines() = runBlocking {
        assertEquals(SlashLine("status", ""), parseSlashLine("/status"))
        assertEquals(SlashLine("goal", "pause now"), parseSlashLine("/Goal  pause now  "))
        for (plain in listOf("/usr/local/bin", "/", "hello /x", "/ hi", "//")) assertNull(plain, parseSlashLine(plain))
        assertEquals(SlashResult.Output("Done.", ""), parseSlashResult(JSONObject("""{"type":"exec","output":"(no output)"}""")))
        assertEquals(SlashResult.Output("hi", "careful"), parseSlashResult(JSONObject("""{"output":"\u001B[1mhi\u001B[0m","warning":"\u001B[31mcareful"}""")))
        assertEquals(SlashResult.Alias("/b"), parseSlashResult(JSONObject("""{"type":"alias","target":"/b"}""")))
        assertEquals(SlashResult.Send("m", null, "n"), parseSlashResult(JSONObject("""{"type":"send","message":"m","notice":"n"}""")))
        assertEquals(SlashResult.Prefill("p", null), parseSlashResult(JSONObject("""{"type":"prefill","message":"p"}""")))
        assertNull(parseSlashResult(JSONObject("""{"type":"weird","message":"p"}""")))
        assertEquals("The gateway's command helper isn't ready. Try again in a moment.", slashErrorText(RpcError(5030, "secret"), "x"))
        assertEquals("usage: /queue <prompt>", slashErrorText(RpcError(4004, "usage: /queue <prompt>"), "queue"))
        assertEquals("Can't reach the gateway. Check your connection.", slashErrorText(IOException("10.0.0.1:80"), "x"))
        assertEquals("/x didn't work. Try again.", slashErrorText(RpcError(5000, "boom"), "x"))

        val calls = Collections.synchronizedList(mutableListOf<String>())
        val histories = AtomicInteger()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                val method = frame.optString("method")
                val params = frame.optJSONObject("params") ?: JSONObject()
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                fun error(code: Int, message: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":$code,"message":"$message"}}""")
                when (method) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false}""")
                    "commands.catalog" -> result(catalogJson)
                    "prompt.submit" -> { calls += "submit ${params.keys().asSequence().sorted().toList()} ${params.getString("text")}"; result("{}") }
                    "slash.exec" -> {
                        val command = params.getString("command")
                        calls += "exec $command"
                        when (command) {
                            "review src" -> result("""{"type":"skill","message":"BIG SCAFFOLD","display":"/review src","name":"review"}""")
                            "goal pause" -> error(4018, "skill command: use command.dispatch for /goal")
                            "undo" -> result("""{"type":"prefill","message":"redo me","notice":"Removed 1"}""")
                            "loop" -> result("""{"type":"alias","target":"/loop"}""")
                            else -> error(5030, "worker died")
                        }
                    }
                    "command.dispatch" -> {
                        calls += "dispatch ${params.keys().asSequence().sorted().toList()} ${params.getString("name")}"
                        if (params.getString("name") == "goal") result("""{"type":"exec","output":"\u001B[1mpaused\u001B[0m"}""")
                        else error(4018, "not a quick/plugin/bundle/skill command: x")
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
                    histories.incrementAndGet()
                    MockResponse().setBody("""{"session_id":"stored-1","messages":[]}""")
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val link = LiveLink(http, authed, base, scope, FakeNetwork(), random = { 0.0 })
        link.start()
        withTimeout(8000) { while (link.state.value != LinkState.Connected) delay(10) }
        val chat = ChatController(link, SessionsRepository(authed, base), "stored-1", scope)
        chat.open()
        val slash = SlashCommands(chat)
        slash.load()
        fun notes() = chat.state.value.items.filter { it is ChatItem.Note || it is ChatItem.CommandOutput }.map {
            when (it) {
                is ChatItem.Note -> it.text
                is ChatItem.CommandOutput -> it.text
                else -> ""
            }
        }
        fun lastOutput() = chat.state.value.items.filterIsInstance<ChatItem.CommandOutput>().last()

        assertNull(slash.runLine("not a command"))
        assertEquals(emptyList<String>(), calls.toList())

        slash.runLine("/term")
        assertEquals(listOf("/term isn't available in this app."), notes())
        assertEquals(ChatItem.CommandOutput(lastOutput().key, "/term", "/term isn't available in this app.", true), lastOutput())
        assertEquals(emptyList<String>(), calls.toList())

        slash.runLine("/goal pause")
        assertEquals(listOf("exec goal pause", "dispatch [arg, name, session_id] goal"), calls.toList())
        assertEquals("paused", notes().last())

        val before = histories.get()
        assertEquals("redo me", slash.runLine("/undo"))
        assertEquals(listOf("Removed 1"), notes().takeLast(1))
        assertEquals(before + 1, histories.get())

        slash.runLine("/loop")
        assertEquals("/loop loops back on itself.", notes().last())
        assertTrue(lastOutput().failed)

        slash.runLine("/fail")
        assertEquals("The gateway's command helper isn't ready. Try again in a moment.", notes().last())
        assertTrue(lastOutput().failed)

        calls.clear()
        slash.runLine("/review src")
        val items = chat.state.value.items
        assertTrue(items.any { it is ChatItem.Message && it.role == "user" && it.text == "/review src" })
        assertFalse(items.any { it is ChatItem.Message && it.text.contains("BIG SCAFFOLD") })
        assertEquals(listOf("exec review src", "submit [session_id, text] BIG SCAFFOLD"), calls.toList())

        calls.clear()
        slash.runLine("/review src")
        assertEquals("A reply is running. Stop it or wait, then run /review again.", notes().last())
        assertTrue(lastOutput().failed)
        assertEquals(listOf("exec review src"), calls.toList())

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }
}
