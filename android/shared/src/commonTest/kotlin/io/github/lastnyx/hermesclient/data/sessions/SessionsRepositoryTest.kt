package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.friendlyError
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionsRepositoryTest {
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
    fun listSearchRenameDeleteUseDocumentedEndpoints() {
        val gateway = MockWebServer()
        gateway.start()
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val repo = SessionsRepository(
            AuthedHttp(OkHttpClient(), tokens, "gw-1", gateway.url("/").toString().trimEnd('/'), now = { 1000 }),
            gateway.url("/").toString().trimEnd('/'),
        )

        gateway.enqueue(
            MockResponse().setBody(
                """{"sessions":[
                {"id":"old","title":"Older","preview":"p-old","last_active":1760000000.0},
                {"id":"new","title":null,"preview":"p-new","last_active":1760000500.5}],
                "total":2,"limit":20,"offset":0,"storage":{}}""",
            ),
        )
        val list = repo.list().getOrThrow()
        assertEquals(listOf("new", "old"), list.map { it.id })
        assertEquals("Older", list[1].title)
        assertEquals("p-new", list[0].preview)
        assertEquals(1760000500.5, list[0].lastActive, 0.0)
        val listReq = gateway.takeRequest()
        assertEquals("Bearer at-1", listReq.getHeader("Authorization"))
        assertEquals("recent", listReq.requestUrl!!.queryParameter("order"))
        assertTrue(listReq.requestUrl!!.queryParameter("exclude_sources")!!.contains("cron"))

        gateway.enqueue(
            MockResponse().setBody(
                """{"results":[{"session_id":"tip","id":"root","snippet":"hit","title":"T","preview":"pv","last_active":5.0}]}""",
            ),
        )
        val found = repo.search("hello world").getOrThrow()
        assertEquals(listOf("tip"), found.map { it.id })
        val searchReq = gateway.takeRequest()
        assertEquals("/api/sessions/search", searchReq.requestUrl!!.encodedPath)
        assertEquals("hello world", searchReq.requestUrl!!.queryParameter("q"))
        assertEquals("20", searchReq.requestUrl!!.queryParameter("limit"))

        gateway.enqueue(MockResponse().setBody("""{"ok":true,"title":"Renamed"}"""))
        repo.rename("s1", "Renamed").getOrThrow()
        val patch = gateway.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("/api/sessions/s1", patch.path)
        assertEquals("Renamed", JSONObject(patch.body.readUtf8()).getString("title"))

        gateway.enqueue(MockResponse().setBody("""{"ok":true}"""))
        repo.delete("s1").getOrThrow()
        val del = gateway.takeRequest()
        assertEquals("DELETE", del.method)
        assertEquals("/api/sessions/s1", del.path)

        gateway.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Session is active"}"""))
        assertEquals("That can't be done right now. Try again in a moment.", friendlyError(repo.delete("s2").exceptionOrNull()!!))
        gateway.shutdown()
    }

    private fun profileRequests(profile: String?): List<okhttp3.mockwebserver.RecordedRequest> {
        val gateway = MockWebServer()
        gateway.start()
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val base = gateway.url("/").toString().trimEnd('/')
        val repo = SessionsRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base, profile)
        val bodies = listOf(
            """{"sessions":[]}""", """{"results":[]}""", """{"messages":[]}""", """{"ok":true}""",
            """{"ok":true}""", """{"ok":true}""", """{"ok":true}""",
        )
        bodies.forEach { gateway.enqueue(MockResponse().setBody(it)) }
        repo.list(); repo.search("q"); repo.messages("s1"); repo.delete("s1")
        repo.rename("s1", "T"); repo.setPinned("s1", true); repo.setArchived("s1", true)
        val taken = bodies.map { gateway.takeRequest() }
        gateway.shutdown()
        return taken
    }

    @Test
    fun noProfileOrDefaultSendsNoProfileAnywhere() {
        listOf(null, "default").forEach { p ->
            val taken = profileRequests(p)
            taken.forEach { assertEquals(null, it.requestUrl!!.queryParameter("profile")) }
            taken.drop(4).forEach { assertFalse(JSONObject(it.body.readUtf8()).has("profile")) }
        }
    }

    @Test
    fun aProfileGoesInTheQueryOrThePatchBody() {
        val taken = profileRequests("work")
        taken.take(4).forEach { assertEquals("work", it.requestUrl!!.queryParameter("profile")) }
        val keys = listOf("title", "pinned", "archived")
        taken.drop(4).forEachIndexed { i, req ->
            assertEquals(null, req.requestUrl!!.queryParameter("profile"))
            val body = JSONObject(req.body.readUtf8())
            assertEquals("work", body.getString("profile"))
            assertTrue(body.has(keys[i]))
        }
    }

    @Test
    fun messagesAskForTheLatestPageAndKeepRowIds() {
        val gateway = MockWebServer()
        gateway.start()
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val base = gateway.url("/").toString().trimEnd('/')
        val repo = SessionsRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base)

        gateway.enqueue(
            MockResponse().setBody(
                """{"messages":[
                {"id":7,"role":"user","content":"hi"},
                {"id":8,"role":"tool","content":"x"},
                {"id":9,"role":"assistant","content":"hidden","display_kind":"hidden"},
                {"id":10,"role":"assistant","content":""},
                {"id":null,"role":"assistant","content":"yo"}],
                "pagination":{"limit":120,"offset":0,"order":"latest","returned":5}}""",
            ),
        )
        val page = repo.messages("s1").getOrThrow()
        val url = gateway.takeRequest().requestUrl!!
        assertEquals("latest", url.queryParameter("order"))
        assertEquals("120", url.queryParameter("limit"))
        assertEquals("0", url.queryParameter("offset"))
        assertEquals("false", url.queryParameter("inline_images"))
        assertEquals(listOf("hi", "yo"), page.messages.map { it.text })
        assertEquals(7L, page.messages[0].rowId)
        assertEquals(null, page.messages[1].rowId)
        assertEquals(5, page.returned)

        gateway.enqueue(MockResponse().setBody("""{"messages":[{"id":1,"role":"user","content":"a"},{"role":"user","content":"b"}]}"""))
        val older = repo.messages("s1", 120).getOrThrow()
        assertEquals("120", gateway.takeRequest().requestUrl!!.queryParameter("offset"))
        assertEquals(2, older.returned)
        gateway.shutdown()
    }
}
