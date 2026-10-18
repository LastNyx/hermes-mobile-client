package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.live.RpcError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectCreateTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private fun model(rpc: (suspend (String, JSONObject) -> JSONObject)?): SessionListModel {
        val gateway = MockWebServer().apply { start() }
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val base = gateway.url("/").toString().trimEnd('/')
        return SessionListModel(
            SessionsRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base),
            Dispatchers.IO,
            rpc,
        )
    }

    private val calls = mutableListOf<Pair<String, String>>()
    private fun ok(): suspend (String, JSONObject) -> JSONObject = { m, p -> calls += m to p.toString(); JSONObject("""{"projects":[]}""") }

    @Test
    fun sendsTheMinimalParamsAndRefreshesOnSuccess() = runBlocking {
        val m = model(ok())
        assertNull(m.createProject(" Demo ", "/srv/demo"))
        assertEquals(listOf("projects.create", "projects.tree"), calls.map { it.first })
        val sent = JSONObject(calls[0].second)
        assertEquals(setOf("name", "folders"), sent.keys().asSequence().toSet())
        assertEquals("Demo", sent.getString("name"))
        assertEquals("""["/srv/demo"]""", sent.getJSONArray("folders").toString())
        assertNull(m.state.value.error)
    }

    @Test
    fun aBlankFolderOmitsFolders() = runBlocking {
        val m = model(ok())
        assertNull(m.createProject("Demo", "  "))
        assertEquals("""{"name":"Demo"}""", calls[0].second)
    }

    @Test
    fun aBlankNameMakesNoCall() = runBlocking {
        val m = model(ok())
        assertEquals("Give the project a name.", m.createProject("  ", "/x"))
        assertEquals(0, calls.size)
        assertNull(m.state.value.error)
    }

    @Test
    fun aDuplicateFolderShowsOurSentenceAndDoesNotRefresh() = runBlocking {
        val m = model { method, _ -> calls += method to ""; throw RpcError(5063, "folder already belongs to project x") }
        assertEquals("That folder already belongs to a project.", m.createProject("Demo", "/x"))
        assertEquals(listOf("projects.create"), calls.map { it.first })
        assertNull(m.state.value.error)
    }
}
