package io.github.lastnyx.hermesclient.data.profiles

import io.github.lastnyx.hermesclient.data.HttpFailure
import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.io.IOException
import okhttp3.Request
import org.json.JSONObject

/** Read-only: the app never writes the host's sticky profile (`POST /api/profiles/active`). Blocking; call off the main thread. */
class ProfilesRepository(private val http: AuthedHttp, baseUrl: String) {
    private val base = baseUrl.trimEnd('/')

    fun list(): Result<List<ProfileItem>> = call("/api/profiles") { parseProfiles(it) }

    fun current(): Result<String?> = call("/api/profiles/active") { parseActiveProfile(it) }

    private fun <T> call(path: String, map: (JSONObject) -> T): Result<T> = try {
        Result.success(
            http.execute(Request.Builder().url(base + path).build()).use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw HttpFailure(response.code)
                map(JSONObject(text))
            },
        )
    } catch (e: IOException) {
        Result.failure(IOException(CANT_REACH_GATEWAY))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
