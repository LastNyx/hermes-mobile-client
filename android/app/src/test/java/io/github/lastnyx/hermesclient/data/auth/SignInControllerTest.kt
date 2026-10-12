package io.github.lastnyx.hermesclient.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import kotlin.concurrent.thread

class SignInControllerTest {
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

    private val tokenJson =
        """{"access_token":"at-1","refresh_token":"rt-1","token_type":"Bearer","expires_at":1760000000,"provider":"basic","user_id":"u1"}"""

    private fun completingBrowser(launches: MutableList<String>, approve: Boolean = true) = BrowserLauncher { url ->
        launches += url
        if (approve) {
            val q = query(url)
            thread {
                (java.net.URL("${q["redirect_uri"]}?code=gw-code&state=${q["state"]}").openConnection() as HttpURLConnection)
                    .apply { responseCode; disconnect() }
            }
        }
    }

    @Test
    fun signInShowsNameSurvivesRestartAndSignOutClears() = runBlocking {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setBody(tokenJson))
        gateway.enqueue(MockResponse().setBody("""{"user_id":"u1","email":"a@b.c","display_name":"Ada"}"""))
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val storage = MemoryStorage()
        val cipher = XorCipher()
        val launches = mutableListOf<String>()
        var finished = 0
        val controller = SignInController(
            OkHttpClient(), TokenStore(storage, cipher), completingBrowser(launches), onFinished = { finished++ },
        )

        assertEquals(SignInState.SignedOut(null), controller.stateOf("gw-1"))
        controller.signIn("gw-1", base)

        assertEquals(SignInState.SignedIn("Ada"), controller.stateOf("gw-1"))
        assertEquals(1, launches.size)
        assertEquals(1, finished)
        gateway.takeRequest()
        val me = gateway.takeRequest()
        assertEquals("/api/auth/me", me.path)
        assertEquals("Bearer at-1", me.getHeader("Authorization"))

        val restarted = SignInController(OkHttpClient(), TokenStore(storage, cipher), completingBrowser(launches))
        assertEquals(SignInState.SignedIn("Signed in"), restarted.stateOf("gw-1"))
        assertEquals(SignInState.SignedOut(null), restarted.stateOf("gw-2"))

        restarted.signOut("gw-1")
        assertEquals(SignInState.SignedOut(null), restarted.stateOf("gw-1"))
        assertNull(TokenStore(storage, cipher).load("gw-1"))
        gateway.shutdown()
    }
}
