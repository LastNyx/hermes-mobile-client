package io.github.lastnyx.hermesclient.data.gateway

import io.github.lastnyx.hermesclient.data.HttpFailure
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class GatewayStatus(val version: String)

class GatewayClient(private val http: OkHttpClient) {
    /** Blocking; call off the main thread. Never throws: failures come back as Result.failure. */
    fun status(baseUrl: String): Result<GatewayStatus> = runCatching {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/api/status").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpFailure(response.code)
            val version = JSONObject(response.body!!.string()).getString("version")
            GatewayStatus(version)
        }
    }
}
