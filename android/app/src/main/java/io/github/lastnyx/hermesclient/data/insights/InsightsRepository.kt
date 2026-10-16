package io.github.lastnyx.hermesclient.data.insights

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/** Usage analytics of one gateway over REST. Blocking; call off the main thread. */
class InsightsRepository(private val http: AuthedHttp, baseUrl: String) {
    private val base = baseUrl.trimEnd('/')

    fun usage(days: Int): Result<UsageInsights> = try {
        val url = "$base/api/analytics/usage".toHttpUrl().newBuilder().addQueryParameter("days", days.toString()).build()
        Result.success(
            http.execute(Request.Builder().url(url).build()).use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw InsightsHttpError(response.code)
                parseUsage(JSONObject(text))
            },
        )
    } catch (e: InsightsHttpError) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(Exception(CANT_REACH_GATEWAY))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private class InsightsHttpError(code: Int) : Exception("Gateway returned $code")
