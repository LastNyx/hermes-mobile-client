package io.github.lastnyx.hermesclient.data.capabilities

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private fun repo(gateway: MockWebServer): CapabilitiesRepository {
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val base = gateway.url("/").toString().trimEnd('/')
        return CapabilitiesRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base)
    }

    private val skillsJson = """[{"name":"plan","description":null,"category":null,"enabled":true,"usage":3,"provenance":"hub"},
        {"description":"no name"},{"name":"odd"}]"""
    private val toolsetsJson = """[{"name":"web","label":"Web","description":"d","enabled":true,"configured":false},{"name":"fs"}]"""
    private val serversJson = """{"servers":[{"name":"p","enabled":false,"source":"plugin","plugin":"x","transport":"http","env":{"K":"secret"}},
        {"name":"c","source":"config","plugin":null}]}"""

    @Test
    fun capabilities() = runBlocking {
        // Parsers.
        val skills = parseSkills(JSONArray(skillsJson))
        assertEquals(listOf("plan", "odd"), skills.map { it.name })
        assertNull(skills[0].description)
        assertNull(skills[0].category)
        assertEquals("hub", skills[0].provenance)
        assertFalse(skills[1].enabled)
        val toolsets = parseToolsets(JSONArray(toolsetsJson))
        assertEquals(listOf("Web", "fs"), toolsets.map { it.label })
        assertFalse(toolsets[0].configured)
        assertFalse(toolsets[1].enabled)
        val servers = parseMcpServers(JSONObject(serversJson))
        assertTrue(servers[0].locked)
        assertFalse(servers[0].enabled)
        assertFalse(servers[1].locked)
        assertTrue(servers[1].enabled)
        assertTrue(parseSkills(null).isEmpty())
        assertTrue(parseMcpServers(JSONObject("""{"servers":5}""")).isEmpty())
        assertEquals(listOf("a", "b"), parseMcpTest(JSONObject("""{"ok":true,"tools":[{"name":"a"},{"name":"b"}]}""")).tools)
        assertEquals("boom", parseMcpTest(JSONObject("""{"ok":false,"error":"boom","tools":[]}""")).error)
        assertEquals("Test failed", parseMcpTest(JSONObject("""{"ok":false,"error":null}""")).error)
        assertEquals(200, parseMcpTest(JSONObject("""{"ok":false,"error":"${"e".repeat(300)}"}""")).error?.length)
        assertEquals("Test failed", parseMcpTest(null).error)

        val gateway = MockWebServer()
        gateway.start()
        val repo = repo(gateway)
        val model = CapabilitiesModel(repo, Dispatchers.IO)

        // Load with one list failing.
        gateway.enqueue(MockResponse().setBody(skillsJson))
        gateway.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        gateway.enqueue(MockResponse().setBody(serversJson))
        model.load()
        assertEquals(2, model.state.value.skills.size)
        assertTrue(model.state.value.toolsets.isEmpty())
        assertEquals(2, model.state.value.servers.size)
        assertEquals("Could not load", model.state.value.error)
        repeat(3) { gateway.takeRequest() }

        // Skill toggle: optimistic, then kept.
        gateway.enqueue(MockResponse().setBody("""{"ok":true}""").setBodyDelay(600, TimeUnit.MILLISECONDS).setHeadersDelay(600, TimeUnit.MILLISECONDS))
        val job = launch(Dispatchers.Default) { model.toggleSkill("odd", true) }
        val deadline = System.currentTimeMillis() + 400
        while (!model.state.value.skills[1].enabled && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(model.state.value.skills[1].enabled)
        assertFalse(job.isCompleted)
        job.join()
        assertTrue(model.state.value.skills[1].enabled)
        val r1 = gateway.takeRequest()
        assertEquals("PUT /api/skills/toggle", "${r1.method} ${r1.path}")
        assertEquals(JSONObject("""{"name":"odd","enabled":true}""").toString(), JSONObject(r1.body.readUtf8()).toString())

        // Skill toggle failing with 500 rolls back with the fixed text.
        gateway.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"secret detail"}"""))
        model.toggleSkill("plan", false)
        assertTrue(model.state.value.skills[0].enabled)
        assertEquals("Could not change that. Try again.", model.state.value.error)
        gateway.takeRequest()

        // Toolset: path-encoded name, exact body.
        gateway.enqueue(MockResponse().setBody("""{"ok":true}"""))
        assertTrue(repo.setToolset("a b/c", true).isSuccess)
        val r2 = gateway.takeRequest()
        assertEquals("PUT /api/tools/toolsets/a%20b%2Fc", "${r2.method} ${r2.path}")
        assertEquals("""{"enabled":true}""", r2.body.readUtf8())

        // MCP: 409 rolls back; locked sends nothing.
        gateway.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"plugin"}"""))
        model.toggleMcp("c", false)
        assertTrue(model.state.value.servers[1].enabled)
        assertEquals("Could not change that. Try again.", model.state.value.error)
        val r3 = gateway.takeRequest()
        assertEquals("PUT /api/mcp/servers/c/enabled", "${r3.method} ${r3.path}")
        assertEquals("""{"enabled":false}""", r3.body.readUtf8())
        val before = gateway.requestCount
        model.toggleMcp("p", true)
        assertEquals(before, gateway.requestCount)

        // Dropped connection rolls back too.
        // OkHttp retries once on a dropped connection.
        repeat(2) { gateway.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)) }
        model.toggleMcp("c", false)
        assertTrue(model.state.value.servers[1].enabled)
        repeat(2) { gateway.takeRequest() }

        // Test: ok:false is a stored failure, not a rollback.
        gateway.enqueue(MockResponse().setBody("""{"ok":false,"error":"x","tools":[]}"""))
        model.testMcp("c")
        val r4 = gateway.takeRequest()
        assertEquals("POST /api/mcp/servers/c/test", "${r4.method} ${r4.path}")
        assertEquals(false, model.state.value.testResults["c"]?.ok)
        assertEquals("x", model.state.value.testResults["c"]?.error)
        assertNull(model.state.value.testing)
        gateway.enqueue(MockResponse().setBody("""{"ok":true,"tools":[{"name":"t1"}]}"""))
        model.testMcp("c")
        assertEquals(listOf("t1"), model.state.value.testResults["c"]?.tools)
        gateway.takeRequest()
        assertTrue(CANT_REACH_GATEWAY.isNotEmpty())
        gateway.shutdown()
    }
}
