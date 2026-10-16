package io.github.lastnyx.hermesclient.data.cron

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.JSON_MEDIA
import io.github.lastnyx.hermesclient.data.gatewayCall
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Scheduled jobs of one gateway over REST. Blocking; call off the main thread. */
class CronRepository(private val http: AuthedHttp, baseUrl: String) {
    private val base = baseUrl.trimEnd('/').toHttpUrl()

    fun jobs(): Result<List<CronJob>> = call(
        Request.Builder().url(base.newBuilder().addPathSegments("api/cron/jobs").addQueryParameter("profile", "all").build()).build(),
    ) { parseJobs(JSONArray(it)) }

    fun runs(id: String): Result<List<CronRun>> = call(
        Request.Builder().url(jobUrl(id, "runs").newBuilder().addQueryParameter("limit", "10").build()).build(),
    ) { parseRuns(JSONObject(it)) }

    fun trigger(id: String): Result<Unit> = post(id, "trigger")

    fun pause(id: String): Result<Unit> = post(id, "pause")

    fun resume(id: String): Result<Unit> = post(id, "resume")

    fun create(name: String, prompt: String, schedule: String): Result<Unit> {
        val body = JSONObject()
        if (name.isNotBlank()) body.put("name", name.trim())
        body.put("prompt", prompt.trim()).put("schedule", schedule.trim()).put("deliver", "local")
        return call(Request.Builder().url(jobsUrl()).post(body.toString().toRequestBody(JSON_MEDIA)).build()) { }
    }

    fun update(id: String, updates: Map<String, String>): Result<Unit> {
        if (updates.isEmpty()) return Result.success(Unit)
        val body = JSONObject().put("updates", JSONObject(updates))
        return call(Request.Builder().url(jobUrl(id)).put(body.toString().toRequestBody(JSON_MEDIA)).build()) { }
    }

    fun delete(id: String): Result<Unit> = call(Request.Builder().url(jobUrl(id)).delete().build()) { }

    private fun jobsUrl(): HttpUrl = base.newBuilder().addPathSegments("api/cron/jobs").build()

    private fun jobUrl(id: String): HttpUrl = jobsUrl().newBuilder().addPathSegment(id).build()

    private fun post(id: String, action: String): Result<Unit> = call(
        Request.Builder().url(jobUrl(id, action)).post("".toRequestBody()).build(),
    ) { }

    // addPathSegment encodes the id, so a slash or space cannot split the path.
    private fun jobUrl(id: String, last: String): HttpUrl =
        base.newBuilder().addPathSegments("api/cron/jobs").addPathSegment(id).addPathSegment(last).build()

    private fun <T> call(request: Request, map: (String) -> T): Result<T> = gatewayCall(http, request, map)
}
