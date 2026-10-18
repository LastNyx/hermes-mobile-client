package io.github.lastnyx.hermesclient.data.insights

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.gatewayCall
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/** Usage analytics of one gateway over REST. Blocking; call off the main thread. */
class InsightsRepository(private val http: AuthedHttp, baseUrl: String) {
    private val base = baseUrl.trimEnd('/')

    fun usage(days: Int): Result<UsageInsights> {
        val url = "$base/api/analytics/usage".toHttpUrl().newBuilder().addQueryParameter("days", days.toString()).build()
        return gatewayCall(http, Request.Builder().url(url).build()) { parseUsage(JSONObject(it)) }
    }
}
