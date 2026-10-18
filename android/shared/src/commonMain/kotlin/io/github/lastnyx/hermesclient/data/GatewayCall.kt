package io.github.lastnyx.hermesclient.data

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import org.json.JSONObject

val JSON_MEDIA = "application/json".toMediaType()

/** Runs [request], maps a 2xx body with [map]; failures are [HttpFailure] or `Exception(CANT_REACH_GATEWAY)`. */
fun <T> gatewayCall(http: AuthedHttp, request: Request, map: (String) -> T): Result<T> = try {
    Result.success(
        http.execute(request).use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpFailure(response.code, detailOf(text))
            map(text)
        },
    )
} catch (e: HttpFailure) {
    Result.failure(e)
} catch (e: IOException) {
    Result.failure(Exception(CANT_REACH_GATEWAY))
} catch (e: Exception) {
    Result.failure(e)
}

private fun detailOf(text: String): String = try {
    (JSONObject(text).opt("detail") as? String).orEmpty()
} catch (e: Exception) {
    ""
}
