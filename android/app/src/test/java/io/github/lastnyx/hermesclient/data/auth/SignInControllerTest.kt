package io.github.lastnyx.hermesclient.data.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

    @Test
    fun anUnreadableStoreSaysSoAndASignInRepairsIt() = runBlocking {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setBody(tokenJson))
        gateway.enqueue(MockResponse().setBody("""{"display_name":"Ada"}"""))
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val message = "Saved sign-in could not be read; sign in again"
        val storage = MemoryStorage().apply { text = "%%%not-base64" }
        val tokens = TokenStore(storage, XorCipher())
        val controller = SignInController(OkHttpClient(), tokens, completingBrowser(mutableListOf()))

        assertEquals(SignInState.SignedOut(message), controller.stateOf("gw-1"))
        assertEquals(SignInState.SignedOut(null), SignInController(OkHttpClient(), TokenStore(MemoryStorage(), XorCipher()), completingBrowser(mutableListOf())).stateOf("gw-1"))

        val authed = controller.authed("gw-1", base)
        runCatching { authed.execute(okhttp3.Request.Builder().url("$base/api/sessions").build()) }
        assertEquals(SignInState.SignedOut(message), controller.states.value["gw-1"])
        assertEquals(0, gateway.requestCount)

        controller.signIn("gw-1", base)
        assertEquals(SignInState.SignedIn("Ada"), controller.stateOf("gw-1"))
        assertEquals(false, tokens.isUnreadable())
        gateway.shutdown()
    }

    @Test
    fun meFailingStillSignsInWithFallbackName() = runBlocking {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setBody(tokenJson))
        gateway.enqueue(MockResponse().setResponseCode(500))
        gateway.start()
        val controller = SignInController(OkHttpClient(), TokenStore(MemoryStorage(), XorCipher()), completingBrowser(mutableListOf()))

        controller.signIn("gw-1", gateway.url("/").toString().trimEnd('/'))

        assertEquals(SignInState.SignedIn("Signed in"), controller.stateOf("gw-1"))
        gateway.shutdown()
    }

    @Test
    fun signInFailureGivesSignedOutWithMessageAndStoresNothing() = runBlocking {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setResponseCode(400))
        gateway.start()
        val storage = MemoryStorage()
        val controller = SignInController(OkHttpClient(), TokenStore(storage, XorCipher()), completingBrowser(mutableListOf()))

        controller.signIn("gw-1", gateway.url("/").toString().trimEnd('/'))

        assertEquals(SignInState.SignedOut("Sign-in expired, try again"), controller.stateOf("gw-1"))
        assertNull(storage.text)
        gateway.shutdown()
    }

    @Test
    fun secondSignInWhileOneRunsLaunchesNoSecondBrowser() = runBlocking {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setBody(tokenJson))
        gateway.enqueue(MockResponse().setBody("""{"display_name":"Ada"}"""))
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val launches = java.util.Collections.synchronizedList(mutableListOf<String>())
        val release = java.util.concurrent.CountDownLatch(1)
        val browser = BrowserLauncher { url ->
            launches += url
            val q = query(url)
            thread {
                release.await()
                (java.net.URL("${q["redirect_uri"]}?code=gw-code&state=${q["state"]}").openConnection() as HttpURLConnection)
                    .apply { responseCode; disconnect() }
            }
        }
        val controller = SignInController(OkHttpClient(), TokenStore(MemoryStorage(), XorCipher()), browser)

        val first = async { controller.signIn("gw-1", base) }
        while (launches.isEmpty()) delay(10)
        controller.signIn("gw-1", base)
        assertEquals(1, launches.size)
        assertEquals(SignInState.SigningIn, controller.stateOf("gw-1"))

        release.countDown()
        first.await()
        assertEquals(1, launches.size)
        assertEquals(SignInState.SignedIn("Ada"), controller.stateOf("gw-1"))
        gateway.shutdown()
    }

    @Test
    fun refreshOutcomesSurfaceAndFreshSignInOrSignOutResetThem() = runBlocking {
        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setResponseCode(503))
        gateway.enqueue(MockResponse().setResponseCode(401))
        gateway.enqueue(MockResponse().setBody(tokenJson))
        gateway.enqueue(MockResponse().setBody("""{"user_id":"u1","display_name":"Ada"}"""))
        gateway.start()
        val base = gateway.url("/").toString().trimEnd('/')
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("old-at", "old-rt", 1000, "basic", "u1"))
        val controller = SignInController(OkHttpClient(), tokens, completingBrowser(mutableListOf()))
        val authed = controller.authed("gw-1", base, now = { 2000 })
        val request = okhttp3.Request.Builder().url("$base/api/sessions").build()

        runCatching { authed.execute(request) }
        assertEquals(SignInState.Unavailable, controller.stateOf("gw-1"))
        assertEquals("old-rt", tokens.load("gw-1")?.refreshToken)

        runCatching { authed.execute(request) }
        assertEquals(SignInState.SignedOut("Signed out — sign in again"), controller.stateOf("gw-1"))

        controller.signIn("gw-1", base)
        assertEquals(SignInState.SignedIn("Ada"), controller.stateOf("gw-1"))

        controller.signOut("gw-1")
        assertEquals(SignInState.SignedOut(null), controller.stateOf("gw-1"))
        gateway.shutdown()
    }
}
