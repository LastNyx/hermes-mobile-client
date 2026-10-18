package io.github.lastnyx.hermesclient.data.insights

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private fun repo(gateway: MockWebServer): InsightsRepository {
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        val base = gateway.url("/").toString().trimEnd('/')
        return InsightsRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base)
    }

    private val full = """{"period_days":30,
        "daily":[{"day":"2026-10-14","input_tokens":1000,"output_tokens":234,"estimated_cost":0.5,"actual_cost":0,"sessions":2,"api_calls":9},
                 {"input_tokens":5}],
        "by_model":[{"model":"m1","input_tokens":1000000,"output_tokens":200000,"estimated_cost":4.2,"sessions":12,"api_calls":340},
                    {"input_tokens":7}],
        "totals":{"total_input":1200000,"total_output":340000,"total_cache_read":80000,"total_estimated_cost":9.9,"total_actual_cost":4.2,"total_sessions":12,"total_api_calls":340},
        "tools":[{"tool":"bash","count":7},{"count":3}],
        "skills":{"top_skills":[{"skill":"plan","total_count":4}]}}"""

    @Test
    fun rows() {
        val u = UsageInsights(
            sessions = 29, apiCalls = 2256, inputTokens = 1_200_000, outputTokens = 340_000, cacheReadTokens = 80_000, cost = 93.534,
            byModel = List(15) { ModelUsage("m$it", 1_900_000, 93.534, 1, 1) },
            tools = List(12) { ToolUsage("t$it", 7) },
            skills = List(11) { SkillUsage("s$it", 4) },
            daily = List(20) { DayUsage("2026-10-%02d".format(it + 1), 1234, 0.5, 1) },
        )
        assertEquals(
            listOf("Chats" to "29", "Requests" to "2256", "Tokens in" to "1.2M", "Tokens out" to "340k", "Cached" to "80k", "Cost" to "$93.53"),
            summaryStats(u).map { it.name to it.value },
        )
        assertEquals(10, modelRows(u).size)
        assertEquals(InsightRow("m0", "1.9M · $93.53"), modelRows(u)[0])
        assertEquals(10, toolRows(u).size)
        assertEquals(InsightRow("t0", "7"), toolRows(u)[0])
        assertEquals(10, skillRows(u).size)
        assertEquals(InsightRow("s0", "4"), skillRows(u)[0])
        assertEquals(14, dayRows(u).size)
        assertEquals(InsightRow("2026-10-20", "1.2k · $0.50"), dayRows(u)[0])
        val none = UsageInsights()
        assertTrue(modelRows(none).isEmpty() && toolRows(none).isEmpty() && skillRows(none).isEmpty() && dayRows(none).isEmpty())
    }

    @Test
    fun insights() {
        val u = parseUsage(JSONObject(full))
        assertEquals(30, u.periodDays)
        assertEquals(12L, u.sessions)
        assertEquals(340L, u.apiCalls)
        assertEquals(1_200_000L, u.inputTokens)
        assertEquals(80_000L, u.cacheReadTokens)
        assertEquals(4.2, u.cost, 0.0001)
        assertEquals(listOf("m1"), u.byModel.map { it.model })
        assertEquals(1_200_000L, u.byModel[0].tokens)
        assertEquals(listOf("bash"), u.tools.map { it.tool })
        assertEquals(listOf("plan"), u.skills.map { it.skill })
        assertEquals(4L, u.skills[0].count)
        assertEquals(listOf("2026-10-14"), u.daily.map { it.day })
        assertEquals(1234L, u.daily[0].tokens)
        assertEquals(0.5, u.daily[0].cost, 0.0001)
        assertFalse(u.isEmpty)

        assertTrue(parseUsage(null).isEmpty)
        assertTrue(parseUsage(JSONObject("{}")).isEmpty)
        val nulls = parseUsage(
            JSONObject("""{"totals":{"total_input":null,"total_output":null,"total_sessions":null,"total_api_calls":null},"skills":{}}"""),
        )
        assertTrue(nulls.isEmpty)
        assertEquals(0L, nulls.inputTokens)
        assertFalse(insightLines(nulls).any { "null" in it })
        assertTrue(parseUsage(JSONObject("""{"skills":{},"tools":null}""")).skills.isEmpty())

        assertEquals(2.5, usageCost(0.0, 2.5), 0.0)
        assertEquals(1.0, usageCost(1.0, 2.5), 0.0)
        assertEquals(0.0, usageCost(0.0, 0.0), 0.0)
        assertEquals(listOf("0", "999", "1k", "1.2k", "1.5M", "5M"), listOf(0L, 999, 1000, 1234, 1_500_000, 5_000_000).map(::formatTokens))
        assertEquals(listOf("$0.00", "<$0.01", "$1.50"), listOf(0.0, 0.004, 1.5).map(::formatCost))

        assertEquals(
            listOf("12 chats, 340 requests", "1.2M in, 340k out, 80k cached", "Cost $4.20"),
            insightLines(u).take(3),
        )
        val many = u.copy(
            byModel = List(15) { ModelUsage("m$it", 1, 0.0, 1, 1) },
            daily = List(20) { DayUsage("d$it", 1, 0.0, 1) },
            tools = emptyList(),
        )
        assertEquals(10, modelLines(many).size)
        assertEquals(14, dayLines(many).size)
        assertEquals("m0  1 tokens  $0.00", modelLines(many)[0])
        assertTrue(toolLines(many).isEmpty())
        assertEquals(listOf("plan  4"), skillLines(u))

        val gateway = MockWebServer()
        gateway.enqueue(MockResponse().setBody(full))
        assertEquals(30, repo(gateway).usage(7).getOrThrow().periodDays)
        val sent = gateway.takeRequest().requestUrl!!
        assertEquals("/api/analytics/usage", sent.encodedPath)
        assertEquals("days=7", sent.encodedQuery)
        gateway.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"secret boom"}"""))
        assertTrue(repo(gateway).usage(7).isFailure)
        gateway.takeRequest()
        val model = InsightsModel(repo(gateway), Dispatchers.Unconfined)
        runBlocking {
            gateway.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"secret boom"}"""))
            model.load()
            assertEquals("Could not load insights", model.state.value.error)
            assertNull(model.state.value.data)
            assertEquals("days=30", gateway.takeRequest().requestUrl!!.encodedQuery)
            gateway.enqueue(MockResponse().setBody(full))
            model.load()
            assertNull(model.state.value.error)
            assertNotNull(model.state.value.data)
            gateway.takeRequest()
            gateway.enqueue(MockResponse().setBody(full))
            model.setDays(5)
            assertEquals(30, model.state.value.days)
            model.setDays(90)
            assertEquals(90, model.state.value.days)
            assertEquals("days=90", gateway.takeRequest().requestUrl!!.encodedQuery)
        }
        gateway.shutdown()
        val down = repo(gateway).usage(7).exceptionOrNull()
        assertEquals(CANT_REACH_GATEWAY, down?.message)
    }
}
