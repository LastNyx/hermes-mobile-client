package io.github.lastnyx.hermesclient.data.auth

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import kotlin.concurrent.thread

class NativeSignInTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate {
            val (k, v) = it.split('=', limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    @Test
    fun signsInThroughLoopbackAndStoresTokenPair() {
        val gateway = MockWebServer()
        gateway.enqueue(
            MockResponse().setBody(
                """{"access_token":"at-1","refresh_token":"rt-1","token_type":"Bearer","expires_at":1760000000,"provider":"basic","user_id":"u1"}"""
            )
        )
        gateway.start()
        val storage = MemoryStorage()
        val store = TokenStore(storage, XorCipher())
        var authorizeUrl = ""
        val browser = BrowserLauncher { url ->
            authorizeUrl = url
            val q = query(url)
            thread {
                val callback = "${q["redirect_uri"]}?code=gw-code&state=${q["state"]}"
                (java.net.URL(callback).openConnection() as HttpURLConnection).apply {
                    responseCode
                    disconnect()
                }
            }
        }

        val prefix = gateway.url("/hermes").toString().trimEnd('/')
        val result = NativeSignIn(OkHttpClient(), store, browser).signIn("gw-1", prefix)

        val pair = result.getOrThrow()
        assertEquals("at-1", pair.accessToken)
        assertEquals("rt-1", pair.refreshToken)
        assertEquals(1760000000L, pair.expiresAt)
        assertEquals(pair, store.load("gw-1"))
        assertNull(store.load("gw-2"))
        assertTrue(!storage.text!!.contains("at-1"))

        val q = query(authorizeUrl)
        assertTrue(authorizeUrl.startsWith("$prefix/auth/native/authorize?"))
        assertEquals("S256", q["code_challenge_method"])
        assertTrue(q["redirect_uri"]!!.matches(Regex("http://127\\.0\\.0\\.1:\\d+/callback")))

        val redeem = gateway.takeRequest()
        assertEquals("/hermes/auth/native/token", redeem.path)
        val body = JSONObject(redeem.body.readUtf8())
        assertEquals("gw-code", body.getString("code"))
        assertEquals(q["code_challenge"], Pkce.challenge(body.getString("code_verifier")))
        assertEquals("uk55vR3nsHoKVjeFGEkFyozXqIinmUdJU4wcHAHxHcQ", Pkce.challenge("dBjftJeZ4CVP-mJ0kz5ttiqAE6y5c7j7GuTNs2fS-qA"))

        store.clear("gw-1")
        assertNull(store.load("gw-1"))
        assertNotNull(gateway.also { it.shutdown() })
    }

    private fun hit(url: String): Int =
        (java.net.URL(url).openConnection() as HttpURLConnection).run { val code = responseCode; disconnect(); code }

    @Test
    fun wrongStateIsRejectedAndFlowKeepsWaiting() {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setBody("""{"access_token":"at-1","refresh_token":"rt-1","expires_at":1760000000}"""))
        gateway.start()
        val store = TokenStore(MemoryStorage(), XorCipher())
        var wrongStatus = 0
        val browser = BrowserLauncher { url ->
            val q = query(url)
            thread {
                wrongStatus = hit("${q["redirect_uri"]}?code=evil&state=nope")
                hit("${q["redirect_uri"]}?code=gw-code&state=${q["state"]}")
            }
        }

        val pair = NativeSignIn(OkHttpClient(), store, browser).signIn("gw-1", gateway.url("/").toString()).getOrThrow()

        assertEquals(400, wrongStatus)
        assertEquals("at-1", pair.accessToken)
        assertEquals("gw-code", JSONObject(gateway.takeRequest().body.readUtf8()).getString("code"))
        gateway.shutdown()
    }

    @Test
    fun errorParameterFailsWithThatMessage() {
        val gateway = MockWebServer()
        gateway.start()
        val storage = MemoryStorage()
        val browser = BrowserLauncher { url ->
            val q = query(url)
            thread { hit("${q["redirect_uri"]}?error=access_denied&state=${q["state"]}") }
        }

        val result = NativeSignIn(OkHttpClient(), TokenStore(storage, XorCipher()), browser)
            .signIn("gw-1", gateway.url("/").toString())

        assertEquals("access_denied", result.exceptionOrNull()?.message)
        assertEquals(0, gateway.requestCount)
        assertNull(storage.text)
        gateway.shutdown()
    }

    @Test
    fun tokenEndpoint400SaysSignInExpired() {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"bad code"}"""))
        gateway.start()
        val storage = MemoryStorage()
        val browser = BrowserLauncher { url ->
            val q = query(url)
            thread { hit("${q["redirect_uri"]}?code=gw-code&state=${q["state"]}") }
        }

        val result = NativeSignIn(OkHttpClient(), TokenStore(storage, XorCipher()), browser)
            .signIn("gw-1", gateway.url("/").toString())

        assertEquals("Sign-in expired, try again", result.exceptionOrNull()?.message)
        assertNull(storage.text)
        gateway.shutdown()
    }
}
