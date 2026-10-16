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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class ChatControllerTest {
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

    private fun event(type: String, payload: String, seq: Int) =
        """{"jsonrpc":"2.0","method":"event","params":{"type":"$type","session_id":"rt2","payload":$payload,"seq":$seq}}"""

    @Test
    fun reconnectWithSameEpochReplaysMissedEventsWithoutDuplicates() = runBlocking {
        val sinceParams = Collections.synchronizedList(mutableListOf<String>())
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                val params = frame.optJSONObject("params") ?: JSONObject()
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (frame.optString("method")) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt2","stored_session_id":"stored-1","running":false}""")
                    "prompt.submit" -> {
                        result("""{"status":"streaming"}""")
                        webSocket.send(event("message.delta", """{"text":"Hel"}""", 1))
                        webSocket.send(event("message.delta", """{"text":"lo"}""", 2))
                        webSocket.close(1001, "")
                    }
                    "session.activate" -> result("""{"session_id":"rt2","stored_session_id":"stored-1","running":true}""")
                    "session.events.since" -> {
                        sinceParams += params.toString().let { p -> JSONObject(p).let { "${it.getString("session_id")} ${it.getInt("last_seen")} ${it.length()}" } }
                        result(
                            """{"events":[{"type":"message.delta","session_id":"rt2","payload":{"text":"lo"},"seq":2},""" +
                                """{"type":"message.delta","session_id":"rt2","payload":{"text":"!"},"seq":3}],""" +
                                """"latest_seq":3,"truncated":false,"count":2,"epoch":"ep1",""" +
                                """"open_requests":[{"id":"srq-1","method":"approval","params":{"command":"ls"}}]}""",
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
                request.path!!.startsWith("/api/sessions/stored-1/messages") ->
                    MockResponse().setBody("""{"session_id":"stored-1","messages":[{"role":"user","content":"hi"}]}""")
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
        chat.send("go")
        awaitTrue { chat.state.value.openRequests.isNotEmpty() && sinceParams.isNotEmpty() }
        awaitTrue { chat.state.value.items.size == 3 && (chat.state.value.items.last() as ChatItem.Message).text == "Hello!" }

        assertEquals(listOf("rt2 2 2"), sinceParams.toList())
        assertEquals("srq-1", chat.state.value.openRequests.single().id)

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun newEpochOrTruncatedSinceReplyReloadsHistoryAndResumesBySessionIdOnly() = runBlocking {
        val connections = AtomicInteger(0)
        val submits = AtomicInteger(0)
        val histories = AtomicInteger(0)
        val resumeKeys = Collections.synchronizedList(mutableListOf<String>())
        val sinceCalls = AtomicInteger(0)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val epoch = if (connections.incrementAndGet() == 1) "ep1" else "ep2"
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"$epoch"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (frame.optString("method")) {
                    "client.capabilities", "process.list" -> result("{}")
                    "session.resume" -> {
                        resumeKeys += frame.getJSONObject("params").keys().asSequence().sorted().joinToString(",")
                        result("""{"session_id":"rt2","stored_session_id":"stored-1","running":false}""")
                    }
                    "session.activate" -> result("""{"session_id":"rt2","stored_session_id":"stored-1","running":true}""")
                    "session.events.since" -> {
                        sinceCalls.incrementAndGet()
                        result(
                            """{"events":[{"type":"message.delta","session_id":"rt2","payload":{"text":"X"},"seq":8}],""" +
                                """"latest_seq":9,"truncated":true,"count":1,"epoch":"ep2"}""",
                        )
                    }
                    "prompt.submit" -> {
                        result("""{"status":"streaming"}""")
                        when (submits.incrementAndGet()) {
                            1 -> {
                                webSocket.send(event("message.delta", """{"text":"Hel"}""", 5))
                                webSocket.send(event("message.delta", """{"text":"lo"}""", 6))
                                webSocket.close(1001, "")
                            }
                            2 -> {
                                webSocket.send(event("message.delta", """{"text":"A"}""", 1))
                                webSocket.close(1001, "")
                            }
                            else -> {
                                webSocket.send(event("message.delta", """{"text":"dup"}""", 9))
                                webSocket.send(event("message.delta", """{"text":"ok"}""", 10))
                            }
                        }
                    }
                }
            }
        }
        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                request.path!!.startsWith("/api/sessions/stored-1/messages") ->
                    MockResponse().setBody("""{"session_id":"stored-1","messages":[{"role":"user","content":"h${histories.incrementAndGet()}"}]}""")
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
        chat.send("go")

        // New epoch: history is fetched again, session resumed, no since-call.
        awaitTrue { histories.get() == 2 && resumeKeys.size == 2 && chat.state.value.items.map { (it as ChatItem.Message).text } == listOf("h2") }
        assertEquals(0, sinceCalls.get())

        // Events with low seq numbers still count after a new epoch.
        chat.send("go2")
        awaitTrue { (chat.state.value.items.last() as? ChatItem.Message)?.text == "A" }

        // Same epoch, truncated reply: reload, reply events dropped, watermark = latest_seq.
        awaitTrue { histories.get() == 3 && resumeKeys.size == 3 && chat.state.value.items.map { (it as ChatItem.Message).text } == listOf("h3") }
        assertEquals(1, sinceCalls.get())
        chat.send("go3")
        awaitTrue { (chat.state.value.items.last() as? ChatItem.Message)?.text == "ok" }

        assertEquals(listOf("h3", "go3", "ok"), chat.state.value.items.map { (it as ChatItem.Message).text })
        assertEquals(listOf("session_id", "session_id", "session_id"), resumeKeys.toList())

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun approvalsAndQuestionsAreAnsweredWithdrawnOrDeclined() = runBlocking {
        val replies = Collections.synchronizedList(mutableListOf<String>())
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                if (id.startsWith("srq-")) { replies += "$id ${frame.getJSONObject("result")}"; return }
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (frame.optString("method")) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> {
                        result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false,"open_requests":[{"id":"srq-a","method":"approval","params":{"session_id":"rt1","request_id":"ap_1","command":"rm -rf build","description":"recursive delete","choices":["once","session","always","deny"],"tool_name":"terminal"}}]}""")
                        webSocket.send("""{"jsonrpc":"2.0","id":"srq-a","method":"approval","params":{"session_id":"rt1","request_id":"ap_1","command":"rm -rf build"}}""")
                        webSocket.send("""{"jsonrpc":"2.0","id":"srq-c","method":"clarify","params":{"session_id":"rt1","questions":[{"qid":"q1","question":"Which?","choices":["red","blue"],"multi_select":false}]}}""")
                        webSocket.send("""{"jsonrpc":"2.0","id":"srq-w","method":"approval","params":{"session_id":"rt1","request_id":"ap_2","command":"ls"}}""")
                        webSocket.send("""{"jsonrpc":"2.0","id":"srq-s","method":"sudo","params":{"session_id":"rt1","command":"apt"}}""")
                        webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"request.cancel","payload":{"id":"srq-w","method":"approval","reason":"resolved"}}}""")
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
        awaitTrue { replies.size == 1 && chat.state.value.prompts.map { it.id } == listOf("srq-a", "srq-c") }
        assertEquals("srq-s {\"value\":\"\"}", replies.single())
        val approval = chat.state.value.prompts[0] as Prompt.Approval
        assertEquals("rm -rf build", approval.command)
        assertEquals(listOf("once", "session", "always", "deny"), approval.choices)

        chat.answerApproval("srq-a", "session")
        chat.answerClarify("srq-c", mapOf("q1" to "blue"))
        awaitTrue { replies.size == 3 }
        assertEquals(listOf("srq-s {\"value\":\"\"}", "srq-a {\"choice\":\"session\"}", "srq-c {\"answers\":{\"q1\":\"blue\"}}"), replies.toList())
        assertEquals(emptyList<Prompt>(), chat.state.value.prompts)
        assertEquals(
            listOf("Answer this on the PC", "You allowed for this session", "You answered: blue"),
            chat.state.value.items.map { (it as ChatItem.Note).text },
        )

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun aLostAnswerKeepsTheCardAndShowsAnError() = runBlocking {
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                if (id.startsWith("srq-")) return
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (frame.optString("method")) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> {
                        result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false,"open_requests":[]}""")
                        webSocket.send("""{"jsonrpc":"2.0","id":"srq-a","method":"approval","params":{"session_id":"rt1","request_id":"ap_1","command":"rm -rf build"}}""")
                        webSocket.send("""{"jsonrpc":"2.0","id":"srq-c","method":"clarify","params":{"session_id":"rt1","questions":[{"qid":"q1","question":"Which?","choices":["red","blue"],"multi_select":false}]}}""")
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
        awaitTrue { chat.state.value.prompts.map { it.id } == listOf("srq-a", "srq-c") }
        link.stop()

        val sentence = "Couldn't send your answer. Check your connection and try again."
        chat.answerApproval("srq-a", "once")
        assertEquals(listOf("srq-a", "srq-c"), chat.state.value.prompts.map { it.id })
        assertEquals(sentence, chat.state.value.error)
        assertTrue(chat.state.value.items.none { it is ChatItem.Note && it.text.contains("You allowed") })

        chat.answerClarify("srq-c", mapOf("q1" to "blue"))
        assertEquals(listOf("srq-a", "srq-c"), chat.state.value.prompts.map { it.id })
        assertEquals(sentence, chat.state.value.error)
        assertTrue(chat.state.value.items.none { it is ChatItem.Note && it.text.contains("You answered") })

        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun everyApprovalChoiceAndMultiQuestionClarifyAreAnsweredAndCancelledApprovalsSendNothing() = runBlocking {
        val replies = Collections.synchronizedList(mutableListOf<String>())
        var socket: WebSocket? = null
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                if (id.startsWith("srq-")) { replies += "$id ${frame.getJSONObject("result")}"; return }
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (frame.optString("method")) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> {
                        result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false}""")
                        for ((srq, choice) in listOf("srq-1" to "once", "srq-2" to "session", "srq-3" to "always", "srq-4" to "deny", "srq-x" to "x")) {
                            webSocket.send("""{"jsonrpc":"2.0","id":"$srq","method":"approval","params":{"session_id":"rt1","request_id":"ap_$choice","command":"ls"}}""")
                        }
                        webSocket.send("""{"jsonrpc":"2.0","id":"srq-c","method":"clarify","params":{"session_id":"rt1","questions":[{"qid":"q1","question":"Which?","choices":["red","blue"],"multi_select":false},{"qid":"q2","question":"Why?","choices":null,"multi_select":false}]}}""")
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
        awaitTrue { chat.state.value.prompts.size == 6 }

        socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"approval.cancelled","session_id":"rt1","payload":{"session_id":"rt1","reason":"interrupted","cancelled_count":1,"request_ids":["ap_x"]}}}""")
        awaitTrue { chat.state.value.prompts.none { it.id == "srq-x" } }

        chat.answerApproval("srq-1", "once")
        chat.answerApproval("srq-2", "session")
        chat.answerApproval("srq-3", "always")
        chat.answerApproval("srq-4", "deny")
        chat.answerClarify("srq-c", mapOf("q1" to "blue"))
        chat.answerClarify("srq-c", mapOf("q1" to "blue", "q2" to "because"))
        awaitTrue { replies.size == 5 }

        assertEquals(
            listOf(
                "srq-1 {\"choice\":\"once\"}",
                "srq-2 {\"choice\":\"session\"}",
                "srq-3 {\"choice\":\"always\"}",
                "srq-4 {\"choice\":\"deny\"}",
                "srq-c {\"answers\":{\"q1\":\"blue\",\"q2\":\"because\"}}",
            ),
            replies.toList(),
        )
        assertEquals(emptyList<Prompt>(), chat.state.value.prompts)

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun answeringAnAlreadyResolvedOrUnknownApprovalDoesNothing() = runBlocking {
        val replies = Collections.synchronizedList(mutableListOf<String>())
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = JSONObject(text)
                val id = frame.optString("id")
                if (id.startsWith("srq-")) { replies += "$id ${frame.getJSONObject("result")}"; return }
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (frame.optString("method")) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> {
                        result("""{"session_id":"rt1","stored_session_id":"stored-1","running":false}""")
                        for (srq in listOf("srq-1", "srq-2")) {
                            webSocket.send("""{"jsonrpc":"2.0","id":"$srq","method":"approval","params":{"session_id":"rt1","request_id":"ap_$srq","command":"ls"}}""")
                        }
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
        awaitTrue { chat.state.value.prompts.size == 2 }

        chat.answerApproval("srq-1", "once")
        chat.answerApproval("srq-1", "once")
        chat.answerApproval("srq-nope", "deny")
        // srq-2 is a marker: once its reply arrived, any stray earlier reply would have arrived too.
        chat.answerApproval("srq-2", "deny")
        awaitTrue { replies.size >= 2 }
        delay(200)

        assertEquals(listOf("srq-1 {\"choice\":\"once\"}", "srq-2 {\"choice\":\"deny\"}"), replies.toList())
        assertEquals(
            listOf("You allowed once", "You denied this"),
            chat.state.value.items.map { (it as ChatItem.Note).text },
        )
        assertEquals(null, chat.state.value.error)

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun redirectedTextIsQueuedWhenRejectedOrWhenAnApprovalIsOpen() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
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
                if (method != "process.list") calls += method
                when (method) {
                    "client.capabilities", "process.list" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":true}""")
                    "session.redirect" -> result("""{"status":"rejected","text":"nope"}""")
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
        assertEquals(true, chat.state.value.running)

        chat.send("fix it")
        assertEquals(listOf("fix it"), chat.state.value.queue.map { it.text })
        assertEquals(emptyList<ChatItem>(), chat.state.value.items)
        assertEquals(true, chat.state.value.running)

        socket!!.send("""{"jsonrpc":"2.0","id":"srq-a","method":"approval","params":{"session_id":"rt1","request_id":"ap_1","command":"ls"}}""")
        awaitTrue { chat.state.value.prompts.size == 1 }
        chat.send("again")
        assertEquals(listOf("fix it", "again"), chat.state.value.queue.map { it.text })
        assertEquals(emptyList<ChatItem>(), chat.state.value.items)
        assertEquals(listOf("client.capabilities", "session.resume", "session.redirect"), calls.toList())

        socket!!.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"rt1","payload":{"text":"done","status":"complete"},"seq":1}}""")
        awaitTrue { chat.state.value.prompts.isEmpty() }
        awaitTrue { "prompt.submit" in calls }
        assertEquals(listOf("client.capabilities", "session.resume", "session.redirect", "prompt.submit"), calls.toList())

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun redirectOutcomesKeepOrDropTheBubbleAndStaleIdIsRetriedOnce() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        var resumes = 0
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
                when (method) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> {
                        resumes++
                        calls += "resume"
                        result("""{"session_id":"rt$resumes","stored_session_id":"stored-1","running":true}""")
                    }
                    "session.redirect" -> {
                        val sid = params.getString("session_id")
                        val body = params.getString("text")
                        calls += "redirect $sid $body"
                        when {
                            body == "boom" -> webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":-32000,"message":"gateway broke"}}""")
                            body == "stale" && sid == "rt1" -> webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":4001,"message":"session not found"}}""")
                            body == "later" -> result("""{"status":"queued","text":"later"}""")
                            else -> result("""{"status":"redirected","text":"$body"}""")
                        }
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
        fun users() = chat.state.value.items.filterIsInstance<ChatItem.Message>().map { it.text }
        chat.open()

        chat.send("more")
        assertEquals(listOf("more"), users())
        chat.send("later")
        assertEquals(listOf("more", "later"), users())
        assertEquals(emptyList<String>(), chat.state.value.queue.map { it.text })

        chat.send("boom")
        assertEquals(listOf("more", "later"), users())
        assertEquals("Something went wrong. Try again.", chat.state.value.error)
        assertEquals(true, chat.state.value.running)
        assertEquals(listOf("boom"), chat.state.value.queue.map { it.text })

        chat.send("stale")
        assertEquals(listOf("more", "later", "stale"), users())
        assertEquals(
            listOf("resume", "redirect rt1 more", "redirect rt1 later", "redirect rt1 boom", "redirect rt1 stale", "resume", "redirect rt2 stale"),
            calls.toList(),
        )

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun chatOpensStreamsRedirectsStopsAndResumesOn4001() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        var resumes = 0

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
                when (method) {
                    "client.capabilities" -> { calls += method; result("{}") }
                    "session.resume" -> {
                        calls += "$method ${params.getString("session_id")}"
                        resumes++
                        result("""{"session_id":"rt$resumes","stored_session_id":"stored-1","running":false}""")
                    }
                    "prompt.submit" -> {
                        calls += "$method ${params.getString("session_id")} ${params.getString("text")}"
                        if (params.getString("session_id") == "rt1") {
                            webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":4001,"message":"session not found"}}""")
                        } else {
                            result("""{"status":"streaming"}""")
                            webSocket.send(event("message.start", "{}", 1))
                            webSocket.send(event("message.delta", """{"text":"Hel"}""", 2))
                            webSocket.send(event("message.delta", """{"text":"lo"}""", 3))
                            webSocket.send(event("tool.start", """{"tool_id":"call_1","name":"terminal"}""", 4))
                            webSocket.send(event("tool.complete", """{"tool_id":"call_1","name":"terminal","summary":"exit 0"}""", 5))
                            webSocket.send(event("message.delta", """{"text":"!"}""", 6))
                        }
                    }
                    "session.redirect" -> {
                        calls += "$method ${params.getString("session_id")} ${params.getString("text")} ${params.keys().asSequence().sorted().joinToString(",")}"
                        result("""{"status":"redirected","text":"more"}""")
                    }
                    "session.interrupt" -> {
                        calls += "$method ${params.getString("session_id")}"
                        result("""{"status":"interrupted"}""")
                        webSocket.send(event("message.complete", """{"text":"Hello!","status":"interrupted"}""", 7))
                    }
                }
            }
        }

        val gateway = MockWebServer()
        gateway.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"t","ttl_seconds":30}""")
                request.path!!.startsWith("/api/ws") -> MockResponse().withWebSocketUpgrade(listener)
                request.path!!.startsWith("/api/sessions/stored-1/messages") ->
                    MockResponse().setBody(
                        """{"session_id":"stored-1","messages":[{"role":"user","content":"hi"},{"role":"assistant","content":[{"type":"text","text":"yo"}]},{"role":"tool","content":"x"}]}""",
                    )
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
        fun rows() = chat.state.value.items.map {
            when (it) {
                is ChatItem.Message -> "${it.role}:${it.text}"
                is ChatItem.Tool -> "tool:${it.name}:${it.status}"
                is ChatItem.Note -> "note:${it.text}"
                is ChatItem.CommandOutput -> "command:${it.text}"
                is ChatItem.Reasoning -> "reasoning:${it.text}"
            }
        }

        chat.open()
        assertEquals(listOf("user:hi", "assistant:yo"), rows())

        chat.send("go")
        awaitTrue { rows().size == 6 }
        assertEquals(
            listOf("user:hi", "assistant:yo", "user:go", "assistant:Hello", "tool:terminal:Done", "assistant:!"),
            rows(),
        )
        assertEquals(true, chat.state.value.running)

        chat.send("more")
        chat.stop()
        awaitTrue { !chat.state.value.running }
        assertEquals(false, (chat.state.value.items.last() as ChatItem.Message).streaming)

        assertEquals(
            listOf(
                "client.capabilities",
                "session.resume stored-1",
                "prompt.submit rt1 go",
                "session.resume stored-1",
                "prompt.submit rt2 go",
                "session.redirect rt2 more session_id,text",
                "session.interrupt rt2",
            ),
            calls.toList(),
        )

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }

    @Test
    fun stopReportsWhetherTheInterruptWasAcceptedAndNeverClearsRunning() = runBlocking {
        suspend fun attempt(accept: Boolean): Triple<Boolean, Boolean, String?> {
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val id = JSONObject(text).optString("id")
                    fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                    when (JSONObject(text).optString("method")) {
                        "client.capabilities" -> result("{}")
                        "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":true}""")
                        "session.interrupt" ->
                            if (accept) result("""{"status":"interrupted"}""")
                            else webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":5000,"message":"boom"}}""")
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
            val accepted = chat.stop()
            val out = Triple(accepted, chat.state.value.running, chat.state.value.error)
            link.stop()
            scope.coroutineContext[Job]!!.cancel()
            gateway.shutdown()
            return out
        }

        val failed = attempt(false)
        assertEquals(false, failed.first)
        assertEquals(true, failed.second)
        assertEquals(true, failed.third != null)

        val ok = attempt(true)
        assertEquals(true, ok.first)
        assertEquals(null, ok.third)
    }

    @Test
    fun aSuccessfulStopClearsTheErrorLeftByAFailedStop() = runBlocking {
        var accept = false
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true,"replay_epoch":"ep1"}}}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val id = JSONObject(text).optString("id")
                fun result(body: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":$body}""")
                when (JSONObject(text).optString("method")) {
                    "client.capabilities" -> result("{}")
                    "session.resume" -> result("""{"session_id":"rt1","stored_session_id":"stored-1","running":true}""")
                    "session.interrupt" ->
                        if (accept) result("""{"status":"interrupted"}""")
                        else webSocket.send("""{"jsonrpc":"2.0","id":"$id","error":{"code":5000,"message":"boom"}}""")
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

        assertEquals(false, chat.stop())
        assertEquals(true, chat.state.value.error != null)
        assertEquals(true, chat.state.value.running)

        accept = true
        assertEquals(true, chat.stop())
        assertEquals(null, chat.state.value.error)

        link.stop()
        scope.coroutineContext[Job]!!.cancel()
        gateway.shutdown()
    }
}
