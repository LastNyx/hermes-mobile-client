package io.github.lastnyx.hermesclient.data.cron

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CronTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private fun repo(gateway: MockWebServer): CronRepository {
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val base = gateway.url("/").toString().trimEnd('/')
        return CronRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base)
    }

    private val jobsBody = """[{"id":"j1","name":"Daily","schedule_display":"every 1d","state":"scheduled",
        "next_run_at":"2026-10-15T09:00:00+07:00","last_run_at":null,"last_status":"ok","last_error":null}]"""

    @Test
    fun parsersAndFormatters() {
        val jobs = parseJobs(
            JSONArray(
                """[{"id":"j1","name":null,"schedule_display":"every 30m","state":"paused",
                "next_run_at":null,"last_error":null},
                {"id":""},{"id":"unknown"},{"name":"x"},"str",{"id":"j2","name":"Two","next_run_at":"2026-10-15T09:00:00+07:00"}]""",
            ),
        )
        assertEquals(listOf("j1", "j2"), jobs.map { it.id })
        assertEquals("j1", jobs[0].name)
        assertNull(jobs[0].nextRunAt)
        assertNull(jobs[0].lastError)
        assertEquals("every 30m", jobs[0].schedule)
        assertEquals("", jobs[1].state)
        assertEquals(emptyList<CronJob>(), parseJobs(null))

        val runs = parseRuns(
            JSONObject(
                """{"runs":[{"title":null,"preview":null,"started_at":5.0,"source":"cron"},
                {"title":"Out","preview":"p","started_at":4.0,"source":"cron_output"}],"limit":10}""",
            ),
        )
        assertEquals(listOf("Run", "Out"), runs.map { it.title })
        assertEquals("", runs[0].preview)
        assertEquals(emptyList<CronRun>(), parseRuns(JSONObject("{}")))
        assertEquals(emptyList<CronRun>(), parseRuns(null))

        val bkk = ZoneId.of("Asia/Bangkok")
        assertEquals("Oct 15, 09:00", formatCronTime("2026-10-15T09:00:00+07:00", bkk))
        assertEquals("Oct 15, 02:00", formatCronTime("2026-10-15T09:00:00+07:00", ZoneId.of("UTC")))
        assertNull(formatCronTime(null, bkk))
        assertNull(formatCronTime(" ", bkk))
        assertNull(formatCronTime("not a date", bkk))
        assertEquals("", formatRunTime(0.0, bkk))
        assertEquals("Oct 15, 09:00", formatRunTime(1_792_029_600.0, bkk))
    }

    @Test
    fun repositoryUsesDocumentedRoutes() {
        val gateway = MockWebServer()
        gateway.start()
        val repo = repo(gateway)

        gateway.enqueue(MockResponse().setBody(jobsBody))
        assertEquals("Daily", repo.jobs().getOrThrow().single().name)
        val jobsReq = gateway.takeRequest()
        assertEquals("GET", jobsReq.method)
        assertEquals("/api/cron/jobs?profile=all", jobsReq.path)
        assertEquals("Bearer at-1", jobsReq.getHeader("Authorization"))

        gateway.enqueue(MockResponse().setBody("""{"runs":[],"limit":10}"""))
        repo.runs("j1").getOrThrow()
        assertEquals("/api/cron/jobs/j1/runs?limit=10", gateway.takeRequest().path)

        for (action in listOf("trigger", "pause", "resume")) {
            gateway.enqueue(MockResponse().setBody("{}"))
            val call = when (action) { "trigger" -> repo.trigger("j1"); "pause" -> repo.pause("j1"); else -> repo.resume("j1") }
            call.getOrThrow()
            val req = gateway.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/cron/jobs/j1/$action", req.path)
        }

        gateway.enqueue(MockResponse().setBody("{}"))
        repo.pause("a b/c").getOrThrow()
        assertEquals("/api/cron/jobs/a%20b%2Fc/pause", gateway.takeRequest().path)

        gateway.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Job is already running or was claimed by another scheduler"}"""))
        val err = repo.trigger("j1").exceptionOrNull() as CronHttpError
        assertEquals(409, err.code)
        assertTrue(err.detail.contains("already running"))
        gateway.takeRequest()

        gateway.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Job not found"}"""))
        val gone = repo.pause("j1").exceptionOrNull() as CronHttpError
        assertEquals(404, gone.code)
        assertEquals("Job not found", gone.detail)
        gateway.takeRequest()

        gateway.shutdown()
        assertEquals(CANT_REACH_GATEWAY, repo.jobs().exceptionOrNull()?.message)
    }

    @Test
    fun listModelLoadsActsAndReports() = runBlocking {
        val gateway = MockWebServer()
        gateway.start()
        val model = CronListModel(repo(gateway), Dispatchers.Unconfined)

        gateway.enqueue(MockResponse().setBody(jobsBody))
        model.load()
        assertEquals(listOf("j1"), model.state.value.jobs.map { it.id })
        gateway.takeRequest()

        gateway.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        model.load()
        assertEquals(listOf("j1"), model.state.value.jobs.map { it.id })
        assertEquals("Couldn't load scheduled jobs.", model.state.value.error)
        gateway.takeRequest()

        gateway.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Job is already running"}"""))
        model.runNow("j1")
        assertEquals("Already running", model.state.value.notice)
        assertEquals(emptySet<String>(), model.state.value.busy)
        assertEquals("/api/cron/jobs/j1/trigger", gateway.takeRequest().path)
        assertEquals(3, gateway.requestCount)

        gateway.enqueue(MockResponse().setBody("{}"))
        gateway.enqueue(MockResponse().setBody(jobsBody))
        model.pause("j1")
        assertEquals("/api/cron/jobs/j1/pause", gateway.takeRequest().path)
        assertEquals("/api/cron/jobs?profile=all", gateway.takeRequest().path)
        assertNull(model.state.value.notice)

        gateway.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Job not found"}"""))
        gateway.enqueue(MockResponse().setBody("[]"))
        model.resume("j1")
        assertEquals("Job not found", model.state.value.notice)
        assertEquals(emptyList<CronJob>(), model.state.value.jobs)
        gateway.takeRequest(); gateway.takeRequest()

        gateway.enqueue(MockResponse().setBody("""{"runs":[{"title":"T","started_at":1.0}]}"""))
        model.toggleRuns("j1")
        assertEquals("j1", model.state.value.openRuns)
        assertEquals("T", model.state.value.runs["j1"]!!.single().title)
        model.toggleRuns("j1")
        assertNull(model.state.value.openRuns)
        gateway.takeRequest()
        assertEquals(8, gateway.requestCount)
        Unit
    }
}
