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

    private val helloSha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

    private fun tmp() = File.createTempFile("dlapk", ".apk").apply { deleteOnExit() }

    private fun info(sha: String) = UpdateInfo("2.0.0", server.url("/app.apk").toString(), 5, sha, "")

    @Test fun downloadKeepsMatchingFile() {
        server.enqueue(MockResponse().setBody("hello"))
        val dest = tmp()
        val result = updater("1.0.0").download(info(helloSha), dest)
        assertEquals(dest, result.getOrThrow())
        assertTrue(dest.exists())
    }

    @Test fun downloadDeletesMismatchedFile() {
        server.enqueue(MockResponse().setBody("tampered"))
        val dest = tmp()
        val result = updater("1.0.0").download(info(helloSha), dest)
        assertEquals("Download did not match its checksum", result.exceptionOrNull()?.message)
        assertFalse(dest.exists())
    }

    @Test fun downloadHttpErrorCantReachGithub() {
        server.enqueue(MockResponse().setResponseCode(500))
        val dest = tmp()
        val result = updater("1.0.0").download(info(helloSha), dest)
        assertEquals("Can't reach GitHub", result.exceptionOrNull()?.message)
    }

    @Test fun verifyIsCaseInsensitive() {
        val file = File.createTempFile("apk", ".bin").apply { writeText("hello"); deleteOnExit() }
        val hex = "2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824"
        assertTrue(Updater.verify(file, hex))
        assertTrue(Updater.verify(file, hex.lowercase()))
        assertFalse(Updater.verify(file, "00" + hex.drop(2)))
    }
}
