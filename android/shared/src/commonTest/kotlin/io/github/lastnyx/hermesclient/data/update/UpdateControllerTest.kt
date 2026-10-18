package io.github.lastnyx.hermesclient.data.update

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateControllerTest {
    private lateinit var server: MockWebServer
    private val helloSha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun controller(install: (File) -> Unit): UpdateController {
        val updater = Updater(OkHttpClient(), server.url("/").toString().trimEnd('/'), "Owner/repo", "1.0.0")
        val dir = File.createTempFile("updc", "").apply { delete(); mkdirs(); deleteOnExit() }
        return UpdateController(updater, dir, Dispatchers.Unconfined, install)
    }

    private fun enqueueRelease() {
        val asset = """{"name":"app.apk","browser_download_url":"${server.url("/app.apk")}","size":5,"digest":"sha256:$helloSha"}"""
        server.enqueue(MockResponse().setBody("""{"tag_name":"v2.0.0","html_url":"https://example/r","assets":[$asset]}"""))
        server.enqueue(MockResponse().setBody("hello"))
    }

    @Test fun installErrorKeepsUpdateAndShowsFixedSentence() = runBlocking {
        enqueueRelease()
        val c = controller { throw IllegalStateException("secret detail") }
        c.check(silent = false)
        val info = c.state.value.available
        assertNotNull(info)
        c.installAvailable()
        val s = c.state.value
        assertEquals("Couldn't start the install", s.status)
        assertEquals(info, s.available)
        assertFalse(s.busy)
        assertFalse(s.status.contains("secret detail"))
    }

    @Test fun installIsCalledOnceWithTheDownloadedFile() = runBlocking {
        enqueueRelease()
        val files = mutableListOf<File>()
        val c = controller { files.add(it) }
        c.check(silent = false)
        c.installAvailable()
        assertEquals(1, files.size)
        assertTrue(files[0].exists())
        assertEquals("Installing…", c.state.value.status)
    }
}
