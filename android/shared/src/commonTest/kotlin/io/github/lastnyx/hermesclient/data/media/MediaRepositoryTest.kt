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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRepositoryTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private fun rig(maxDownloadBytes: Long = 64L * 1024 * 1024): Pair<MockWebServer, MediaRepository> {
        val gateway = MockWebServer()
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        return gateway to MediaRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base, maxDownloadBytes)
    }

    @Test fun imageSendsEncodedPathAndDecodesDataUrl() {
        val (gateway, repo) = rig()
        gateway.enqueue(MockResponse().setBody("""{"data_url":"data:image/png;base64,AQID"}"""))
        assertArrayEquals(byteArrayOf(1, 2, 3), repo.image("/h/my pic.png").getOrThrow())
        val req = gateway.takeRequest()
        assertEquals("Bearer at-1", req.getHeader("Authorization"))
        assertEquals("/api/media", req.requestUrl!!.encodedPath)
        assertEquals("/h/my pic.png", req.requestUrl!!.queryParameter("path"))
        gateway.shutdown()
    }

    @Test fun imageNullDataUrlFailsAndErrorsUseSentences() {
        val (gateway, repo) = rig()
        gateway.enqueue(MockResponse().setBody("""{"data_url":null}"""))
        assertEquals("Couldn't load this picture.", repo.image("/a.png").exceptionOrNull()!!.message)
        gateway.enqueue(MockResponse().setResponseCode(415).setBody("""{"detail":"secret body"}"""))
        assertEquals("This picture can't be shown.", repo.image("/a.png").exceptionOrNull()!!.message)
        gateway.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"secret body"}"""))
        assertEquals("This picture can't be shown.", repo.image("/a.png").exceptionOrNull()!!.message)
        gateway.shutdown()
    }

    @Test fun downloadSendsSessionIdOnlyWhenGivenAndFallsBackAfter404() {
        val (gateway, repo) = rig()
        gateway.enqueue(MockResponse().setBody("bytes"))
        assertEquals("bytes", String(repo.download("/a/r.pdf", "s1").getOrThrow()))
        val first = gateway.takeRequest().requestUrl!!
        assertEquals("/api/fs/download", first.encodedPath)
        assertEquals("/a/r.pdf", first.queryParameter("path"))
        assertEquals("s1", first.queryParameter("session_id"))

        gateway.enqueue(MockResponse().setResponseCode(404))
        gateway.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", String(repo.download("/a/r.pdf", null).getOrThrow()))
        val a = gateway.takeRequest().requestUrl!!
        val b = gateway.takeRequest().requestUrl!!
        assertEquals("/api/fs/download", a.encodedPath)
        assertNull(a.queryParameter("session_id"))
        assertEquals("/api/files/download", b.encodedPath)
        assertEquals("/a/r.pdf", b.queryParameter("path"))
        gateway.shutdown()
    }

    @Test fun downloadErrorsCapAndUnreachable() {
        val (gateway, repo) = rig(maxDownloadBytes = 4)
        gateway.enqueue(MockResponse().setResponseCode(403))
        assertEquals("The gateway won't hand this file over.", repo.download("/a", null).exceptionOrNull()!!.message)
        gateway.enqueue(MockResponse().setBody("12345678"))
        assertEquals("This file is too large to download.", repo.download("/a", null).exceptionOrNull()!!.message)
        gateway.enqueue(MockResponse().setBody("1234"))
        assertFalse(repo.download("/a", null).isFailure)
        gateway.shutdown()
        assertEquals(CANT_REACH_GATEWAY, repo.download("/a", null).exceptionOrNull()!!.message)
    }
}
