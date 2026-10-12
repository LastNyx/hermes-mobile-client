package io.github.lastnyx.hermesclient.data.update

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdaterTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun updater(current: String) =
        Updater(OkHttpClient(), server.url("/").toString().trimEnd('/'), "Owner/repo", current)

    private fun release(tag: String, assets: String) =
        """{"tag_name":"$tag","html_url":"https://example/r","assets":[$assets]}"""

    private fun apk(digest: String?) =
        """{"name":"app.apk","browser_download_url":"https://example/app.apk","size":1234""" +
            (digest?.let { ""","digest":"$it"""" } ?: "") + "}"

    @Test fun parsesNewerReleaseAndComparesNumerically() {
        server.enqueue(MockResponse().setBody(release("v0.10.0", apk("sha256:ABCDEF"))))
        val outcome = updater("0.9.9").check().getOrThrow()
        val info = (outcome as UpdateOutcome.Available).info
        assertEquals("0.10.0", info.version)
        assertEquals("https://example/app.apk", info.apkUrl)
        assertEquals(1234L, info.size)
        assertEquals("ABCDEF", info.sha256)
        assertEquals("/repos/Owner/repo/releases/latest", server.takeRequest().path)
    }

    @Test fun equalAndOlderAreUpToDate() {
        server.enqueue(MockResponse().setBody(release("v1.2.3", apk("sha256:aa"))))
        assertEquals(UpdateOutcome.UpToDate, updater("1.2.3").check().getOrThrow())
        server.enqueue(MockResponse().setBody(release("v1.2.2", apk("sha256:aa"))))
        assertEquals(UpdateOutcome.UpToDate, updater("1.2.3").check().getOrThrow())
    }

    @Test fun missingDigestFallsBackToSha256Asset() {
        val sibling = """{"name":"app.apk.sha256","browser_download_url":"${server.url("/sum")}","size":10}"""
        server.enqueue(MockResponse().setBody(release("v2.0.0", apk(null) + "," + sibling)))
        server.enqueue(MockResponse().setBody("deadbeef  app.apk\n"))
        val info = (updater("1.0.0").check().getOrThrow() as UpdateOutcome.Available).info
        assertEquals("deadbeef", info.sha256)
    }

    @Test fun notFoundMeansNoneYet() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(UpdateOutcome.NonePublished, updater("1.0.0").check().getOrThrow())
    }

    @Test fun verifyIsCaseInsensitive() {
        val file = File.createTempFile("apk", ".bin").apply { writeText("hello"); deleteOnExit() }
        val hex = "2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824"
        assertTrue(Updater.verify(file, hex))
        assertTrue(Updater.verify(file, hex.lowercase()))
        assertFalse(Updater.verify(file, "00" + hex.drop(2)))
    }
}
