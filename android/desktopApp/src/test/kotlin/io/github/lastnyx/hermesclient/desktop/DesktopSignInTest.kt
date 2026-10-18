package io.github.lastnyx.hermesclient.desktop

import io.github.lastnyx.hermesclient.data.auth.SignInController
import io.github.lastnyx.hermesclient.data.auth.SignInState
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import kotlin.concurrent.thread

class DesktopSignInTest {
    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate {
            val (k, v) = it.split('=', limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    @Test
    fun signInFallsBackFromBrowseToXdgOpenToShownUrlAndStoresTokenPair() = runBlocking {
        val gateway = MockWebServer()
        gateway.enqueue(
            MockResponse().setBody(
                """{"access_token":"at-1","refresh_token":"rt-1","token_type":"Bearer","expires_at":1760000000,"provider":"basic","user_id":"u1"}""",
            ),
        )
        gateway.enqueue(MockResponse().setBody("""{"user_id":"u1","display_name":"Ada"}"""))
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val order = mutableListOf<String>()
        var shown: String? = null
        val launcher = DesktopBrowserLauncher(
            browse = { order += "browse"; error("no desktop") },
            xdgOpen = { order += "xdg"; error("no xdg-open") },
            showUrl = { url ->
                order += "show"
                shown = url
                val q = query(url)
                thread {
                    (java.net.URL("${q["redirect_uri"]}?code=gw-code&state=${q["state"]}").openConnection() as HttpURLConnection)
                        .apply { responseCode; disconnect() }
                }
            },
        )
        var stored = ""
        val tokens = TokenStore(
            object : io.github.lastnyx.hermesclient.data.auth.TokenStorage {
                override fun load(): String? = stored.ifEmpty { null }
                override fun save(text: String?) { stored = text ?: "" }
            },
            PlainCipher,
        )
        val controller = SignInController(OkHttpClient(), tokens, launcher)

        controller.signIn("gw-1", base)

        assertEquals(listOf("browse", "xdg", "show"), order)
        val q = query(shown!!)
        assertEquals("S256", q["code_challenge_method"])
        assertTrue(q["redirect_uri"]!!.startsWith("http://127.0.0.1:"))
        assertEquals(SignInState.SignedIn("Ada"), controller.stateOf("gw-1"))
        assertNotNull(tokens.load("gw-1"))
        gateway.shutdown()
    }
}
