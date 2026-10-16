package io.github.lastnyx.hermesclient.data.media

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRepositoryReadTextTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private fun rig(): Pair<MockWebServer, MediaRepository> {
        val gateway = MockWebServer()
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        return gateway to MediaRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base)
    }

    @Test fun readsTextAndMapsFailuresToSentences() {
        val (gateway, repo) = rig()
        gateway.enqueue(MockResponse().setBody("""{"text":"hi\n","byteSize":3,"truncated":false,"binary":false,"language":null,"path":"/h/my a.txt"}"""))
        assertEquals(FileText("hi\n", 3, false, false, null), repo.readText("/h/my a.txt").getOrThrow())
        val req = gateway.takeRequest()
        assertEquals("Bearer at-1", req.getHeader("Authorization"))
        assertEquals("/api/fs/read-text", req.requestUrl!!.encodedPath)
        assertEquals("/h/my a.txt", req.requestUrl!!.queryParameter("path"))
        assertNull(req.requestUrl!!.queryParameter("profile"))

        val sentences = mapOf(
            403 to "The gateway won't show this file.",
            404 to "This gateway can't show files yet.",
            405 to "This gateway can't show files yet.",
            413 to "This file is too large to view.",
            500 to "Couldn't open this file.",
        )
        sentences.forEach { (code, sentence) ->
            gateway.enqueue(MockResponse().setResponseCode(code).setBody("""{"detail":"secret body"}"""))
            assertEquals(sentence, repo.readText("/a").exceptionOrNull()!!.message)
        }
        gateway.shutdown()
        assertEquals(CANT_REACH_GATEWAY, repo.readText("/a").exceptionOrNull()!!.message)
    }
}
