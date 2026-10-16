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

class SubagentsChatTest {
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

    /** [listAnswer] and [interruptAnswer] are full JSON-RPC replies' `result` or `error` member text. */
    private class Rig(@Volatile var listAnswer: String, @Volatile var interruptAnswer: String = """"result":{"found":true}""") {
        val lists = Collections.synchronizedList(mutableListOf<JSONObject>())
        val interrupts = Collections.synchronizedList(mutableListOf<JSONObject>())
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
                    fun reply(member: String) = webSocket.send("""{"jsonrpc":"2.0","id":"$id",$member}""")
                    when (frame.optString("method")) {
                        "client.capabilities", "process.list" -> reply(""""result":{}""")
                        "session.resume", "session.activate" -> reply(""""result":{"session_id":"rt1","stored_session_id":"stored-1","running":false}""")
                        "subagent.list" -> { lists += frame.getJSONObject("params"); reply(this@Rig.listAnswer) }
                        "subagent.interrupt" -> { interrupts += frame.getJSONObject("params"); reply(this@Rig.interruptAnswer) }
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

        const val ONE = """"result":{"subagents":[{"subagent_id":"s1","goal":"dig","status":"running","model":"m1"}],"delegations":[]}"""
        const val NOT_OWNED = """"error":{"code":4001,"message":"session not found or not owned by this transport"}"""
    }

    @Test
    fun openListsSubagentsWithOnlySessionId() = runBlocking {
        val rig = Rig(ONE)
        rig.chat.open()
        awaitTrue { rig.chat.state.value.subagents.isNotEmpty() }
        assertEquals(1, rig.lists.size)
        assertEquals(setOf("session_id"), rig.lists[0].keys().asSequence().toSet())
        assertEquals("s1", rig.chat.state.value.subagents[0].id)
        assertEquals("dig", rig.chat.state.value.subagents[0].goal)
    }

    @Test
    fun notOwnedErrorIsSilent() = runBlocking {
        val rig = Rig(NOT_OWNED)
        rig.chat.open()
        awaitTrue { rig.lists.size >= 2 }
        delay(200)
        val s = rig.chat.state.value
        assertTrue(s.subagents.isEmpty())
        assertNull(s.error)
        assertTrue(s.items.none { it is ChatItem.Note })
    }

    @Test
    fun interruptFoundTrueRefreshesOnce() = runBlocking {
        val rig = Rig(ONE)
        rig.chat.open()
        awaitTrue { rig.chat.state.value.subagents.isNotEmpty() }
        rig.chat.interruptSubagent("s1")
        awaitTrue { rig.lists.size == 2 }
        assertEquals(1, rig.interrupts.size)
        assertEquals(setOf("session_id", "subagent_id"), rig.interrupts[0].keys().asSequence().toSet())
        assertEquals("s1", rig.interrupts[0].getString("subagent_id"))
    }

    @Test
    fun interruptFoundFalseMarksInterrupted() = runBlocking {
        val rig = Rig(ONE, """"result":{"found":false,"subagent_id":"s1"}""")
        rig.chat.open()
        awaitTrue { rig.chat.state.value.subagents.isNotEmpty() }
        rig.chat.interruptSubagent("s1")
        assertEquals(SubagentStatus.Interrupted, rig.chat.state.value.subagents[0].status)
    }

    @Test
    fun interruptErrorAddsOneNote() = runBlocking {
        val rig = Rig(ONE, """"error":{"code":5000,"message":"boom"}""")
        rig.chat.open()
        awaitTrue { rig.chat.state.value.subagents.isNotEmpty() }
        rig.chat.interruptSubagent("s1")
        val s = rig.chat.state.value
        assertEquals(SubagentStatus.Running, s.subagents[0].status)
        val notes = s.items.filterIsInstance<ChatItem.Note>()
        assertEquals(1, notes.size)
        assertEquals("Couldn't stop that subagent. Try again.", notes[0].text)
    }

    @Test
    fun interruptUnknownIdSendsNothing() = runBlocking {
        val rig = Rig(ONE)
        rig.chat.open()
        awaitTrue { rig.chat.state.value.subagents.isNotEmpty() }
        rig.chat.interruptSubagent("nope")
        delay(200)
        assertTrue(rig.interrupts.isEmpty())
    }

    private fun sub(id: String, status: SubagentStatus = SubagentStatus.Running, model: String? = null, activity: List<String> = emptyList()) =
        Subagent(id, id, null, null, 0, "g", 0, 1, model, status, null, null, activity, null, null)

    @Test
    fun parseFullRowAndDefaults() {
        val list = parseSubagentList(JSONObject("""{"subagents":[
            {"subagent_id":"a","parent_id":"p","depth":2,"goal":"G","delegation_id":"d","model":"m","status":"completed","tool_count":3,"last_tool":"t"},
            {"subagent_id":"b"},
            {"subagent_id":"c","goal":null,"model":null,"last_tool":null},
            {"goal":"no id"}, 5]}"""))
        assertEquals(listOf("a", "b", "c"), list.map { it.id })
        val a = list[0]
        assertEquals(listOf("p", 2, "G", "d", "m", SubagentStatus.Completed, 3, "t"), listOf(a.parentId, a.depth, a.goal, a.delegationId, a.model, a.status, a.toolCount, a.lastTool))
        assertEquals("a", a.key)
        assertEquals(SubagentStatus.Running, list[1].status)
        assertEquals(0, list[1].depth)
        assertEquals("", list[1].goal)
        assertNull(list[2].model)
        assertNull(list[2].lastTool)
        assertEquals("", list[2].goal)
        assertTrue(parseSubagentList(null).isEmpty())
        assertTrue(parseSubagentList(JSONObject("{}")).isEmpty())
    }

    @Test
    fun mergeKeepsActivityAppendsAndNeverRevives() {
        val current = listOf(
            sub("a", model = "old", activity = listOf("x")),
            sub("done", SubagentStatus.Completed),
            sub("unlisted"),
        )
        val listed = listOf(sub("done").copy(status = SubagentStatus.Running), sub("a", model = "new"), sub("n"))
        val merged = mergeListed(current, listed)
        assertEquals(listOf("a", "done", "unlisted", "n"), merged.map { it.id })
        assertEquals("new", merged[0].model)
        assertEquals(listOf("x"), merged[0].activity)
        assertEquals(SubagentStatus.Completed, merged[1].status)
    }
}
