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
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPinArchiveTest {
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
    fun pinArchiveUndoAndArchivedPage() = runBlocking {
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
            {"id":"a","title":"A","preview":"","last_active":3.0},
            {"id":"b","title":"B","preview":"","last_active":2.0,"pinned":true},
            {"id":"c","title":"C","preview":"","last_active":1.0,"pinned":true}]}"""

        gateway.enqueue(MockResponse().setBody(listBody))
        model.load()
        assertEquals("exclude", gateway.takeRequest().requestUrl!!.queryParameter("archived"))
        assertEquals(listOf("b", "c", "a"), model.state.value.sessions.map { it.id })
        assertFalse(model.state.value.sessions.first { it.id == "a" }.archived)

        gateway.enqueue(MockResponse().setBody("""{"ok":true}"""))
        model.setPinned("a", true)
        assertEquals("""{"pinned":true}""", gateway.takeRequest().body.readUtf8())
        assertEquals(listOf("a", "b", "c"), model.state.value.sessions.map { it.id })

        gateway.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"nope"}"""))
        model.setArchived("b", true)
        assertEquals("""{"archived":true}""", gateway.takeRequest().body.readUtf8())
        assertEquals(listOf("a", "b", "c"), model.state.value.sessions.map { it.id })
        assertEquals("The gateway hit an error. Try again.", model.state.value.error)

        gateway.enqueue(MockResponse().setBody("""{"ok":true}"""))
        model.setArchived("b", true)
        gateway.takeRequest()
        assertEquals(listOf("a", "c"), model.state.value.sessions.map { it.id })
        assertNull(model.state.value.error)

        gateway.enqueue(MockResponse().setBody("""{"ok":true}"""))
        gateway.enqueue(MockResponse().setBody(listBody))
        model.setArchived("b", false)
        assertEquals("""{"archived":false}""", gateway.takeRequest().body.readUtf8())
        gateway.takeRequest()
        assertEquals(3, model.state.value.sessions.size)

        gateway.enqueue(MockResponse().setBody("""{"sessions":[{"id":"z","title":"Z","last_active":1.0,"archived":true}]}"""))
        model.openArchived()
        assertEquals("only", gateway.takeRequest().requestUrl!!.queryParameter("archived"))
        assertEquals(listOf("z"), model.state.value.sessions.map { it.id })
        assertTrue(model.state.value.sessions.single().archived)

        gateway.enqueue(MockResponse().setBody("""{"ok":true}"""))
        model.setArchived("z", false)
        gateway.takeRequest()
        assertTrue(model.state.value.sessions.isEmpty())
        gateway.shutdown()
    }
}
