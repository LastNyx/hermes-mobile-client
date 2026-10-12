package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SessionListModelTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    @Test
    fun loadSearchRenameDeleteUpdateTheVisibleList() = runBlocking {
        val gateway = MockWebServer()
        gateway.start()
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val base = gateway.url("/").toString().trimEnd('/')
        val model = SessionListModel(
            SessionsRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base),
            Dispatchers.IO,
        )
        val listBody = """{"sessions":[
            {"id":"old","title":"Older","preview":"p-old","last_active":1.0},
            {"id":"new","title":null,"preview":"p-new","last_active":2.0}]}"""

        gateway.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"gateway down"}"""))
        model.load()
        assertEquals("gateway down", model.state.value.error)
        assertFalse(model.state.value.loading)

        gateway.takeRequest()
        gateway.enqueue(MockResponse().setBody(listBody))
        model.load()
        assertNull(model.state.value.error)
        assertEquals(listOf("new", "old"), model.state.value.sessions.map { it.id })
        assertEquals("cron", gateway.takeRequest().requestUrl!!.queryParameter("exclude_sources"))

        gateway.enqueue(
            MockResponse().setBody("""{"results":[{"session_id":"hit","title":"T","preview":"pv","last_active":5.0}]}"""),
        )
        model.search("hello")
        assertEquals(listOf("hit"), model.state.value.sessions.map { it.id })
        assertEquals("/api/sessions/search", gateway.takeRequest().requestUrl!!.encodedPath)

        gateway.enqueue(MockResponse().setBody(listBody))
        model.search("")
        assertEquals(listOf("new", "old"), model.state.value.sessions.map { it.id })
        assertEquals("/api/sessions", gateway.takeRequest().requestUrl!!.encodedPath)

        gateway.enqueue(MockResponse().setBody("""{"ok":true}"""))
        model.rename("old", "Renamed")
        assertEquals("Renamed", model.state.value.sessions.first { it.id == "old" }.title)
        assertEquals("PATCH", gateway.takeRequest().method)

        gateway.enqueue(MockResponse().setBody("""{"ok":true}"""))
        model.delete("new")
        assertEquals(listOf("old"), model.state.value.sessions.map { it.id })
        assertEquals("DELETE", gateway.takeRequest().method)
        gateway.shutdown()
    }
}
