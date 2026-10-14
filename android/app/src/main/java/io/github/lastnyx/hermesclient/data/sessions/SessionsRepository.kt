package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

const val CANT_REACH_GATEWAY = "Can't reach the gateway. Check your connection."

data class Session(
    val id: String,
    val title: String,
    val preview: String,
    val lastActive: Double,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val startedAt: Double = 0.0,
)

/** Pinned first, then most recent activity first. */
internal fun List<Session>.pinnedFirst(): List<Session> =
    sortedWith(compareByDescending<Session> { it.pinned }.thenByDescending { it.lastActive })

data class HistoryMessage(val role: String, val text: String)

/** Sessions of one gateway over REST. Blocking; call off the main thread. */
class SessionsRepository(private val http: AuthedHttp, baseUrl: String) {
    private val base = baseUrl.trimEnd('/')

    /** [archived] is the gateway's filter: `exclude` (default), `only` or `include`. */
    fun list(archived: String = "exclude"): Result<List<Session>> = call(
        Request.Builder().url(
            "$base/api/sessions".toHttpUrl().newBuilder()
                .addQueryParameter("order", "recent")
                .addQueryParameter("archived", archived)
                // Human chats only: Desktop hides scheduled/automated sources from recents.
                .addQueryParameter("exclude_sources", "cron")
                .build(),
        ).build(),
    ) { parse(it.getJSONArray("sessions"), "id") }.map { it.pinnedFirst() }

    fun search(query: String): Result<List<Session>> = call(
        Request.Builder().url(
            "$base/api/sessions/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", "20")
                .build(),
        ).build(),
    ) { parse(it.getJSONArray("results"), "session_id") }

    fun rename(id: String, title: String): Result<Unit> = call(
        Request.Builder().url("$base/api/sessions/$id")
            .patch(JSONObject().put("title", title).toString().toRequestBody("application/json".toMediaType()))
            .build(),
    ) { }

    fun setPinned(id: String, pinned: Boolean): Result<Unit> = patch(id, JSONObject().put("pinned", pinned))

    fun setArchived(id: String, archived: Boolean): Result<Unit> = patch(id, JSONObject().put("archived", archived))

    private fun patch(id: String, body: JSONObject): Result<Unit> = call(
        Request.Builder().url("$base/api/sessions/$id")
            .patch(body.toString().toRequestBody("application/json".toMediaType()))
            .build(),
    ) { }

    fun delete(id: String): Result<Unit> = call(
        Request.Builder().url("$base/api/sessions/$id").delete().build(),
    ) { }

    /** Durable transcript, oldest first; images are never inlined on mobile. */
    fun messages(id: String): Result<List<HistoryMessage>> = call(
        Request.Builder().url(
            "$base/api/sessions/$id/messages".toHttpUrl().newBuilder()
                .addQueryParameter("order", "oldest")
                .addQueryParameter("limit", "500")
                .addQueryParameter("inline_images", "false")
                .build(),
        ).build(),
    ) { body ->
        val rows = body.getJSONArray("messages")
        (0 until rows.length()).map { rows.getJSONObject(it) }.mapNotNull { row ->
            val role = row.optString("role")
            val text = rowText(row)
            val hidden = row.optString("display_kind") == "hidden"
            if (!hidden && (role == "user" || role == "assistant") && text.isNotBlank()) HistoryMessage(role, text) else null
        }
    }

    private fun parse(rows: JSONArray, idKey: String): List<Session> = List(rows.length()) { i ->
        val row = rows.getJSONObject(i)
        Session(
            id = row.getString(idKey),
            title = row.optString("title", "").takeIf { !row.isNull("title") } ?: "",
            preview = if (row.isNull("preview")) "" else row.optString("preview"),
            lastActive = row.optDouble("last_active", 0.0),
            pinned = row.optBoolean("pinned", false),
            archived = row.optBoolean("archived", false),
            startedAt = row.optDouble("started_at", 0.0),
        )
    }

    /** REST rows are raw DB rows: the body is `content` (string or list of parts); `text` only exists on RPC snapshots. */
    private fun rowText(row: JSONObject): String {
        if (!row.isNull("text")) return row.optString("text")
        return when (val content = row.opt("content")) {
            is String -> content
            is JSONArray -> (0 until content.length()).joinToString("\n") { i ->
                val part = content.optJSONObject(i)
                when (part?.optString("type")) {
                    "text", "input_text", "output_text" -> part.optString("text")
                    "image_url", "input_image", "image" -> "[image]"
                    else -> ""
                }
            }.trim()
            else -> ""
        }
    }

    private fun <T> call(request: Request, map: (JSONObject) -> T): Result<T> = try {
        Result.success(
            http.execute(request).use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(text).optString("detail") }.getOrDefault("")
                    throw Exception(detail.ifEmpty { "Gateway returned ${response.code}" })
                }
                map(JSONObject(text))
            },
        )
    } catch (e: IOException) {
        // Never surface OkHttp's text ("failed to connect to ... after 10000ms").
        Result.failure(Exception(CANT_REACH_GATEWAY))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
