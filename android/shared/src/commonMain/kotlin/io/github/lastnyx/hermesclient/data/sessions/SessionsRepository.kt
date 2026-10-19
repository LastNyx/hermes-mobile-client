package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.HttpFailure
import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.profiles.profileParam
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

const val CANT_REACH_GATEWAY = "Can't reach the gateway. Check your connection."

data class Session(
    val id: String,
    val title: String,
    val preview: String,
    val lastActive: Double,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val startedAt: Double = 0.0,
    val active: Boolean = false,
    val unread: Boolean = false,
    // Absent count must not read as an empty draft.
    val messageCount: Int = 1,
    val cwd: String? = null,
)

/** Pinned first, then most recent activity first. */
internal fun List<Session>.pinnedFirst(): List<Session> =
    sortedWith(compareByDescending<Session> { it.pinned }.thenByDescending { it.lastActive })

const val HISTORY_PAGE = 120

data class HistoryMessage(val role: String, val text: String, val rowId: Long? = null, val notice: Boolean = false)

/** [returned] is the raw row count of the reply, before tool/hidden/empty rows were dropped. */
data class HistoryPage(val messages: List<HistoryMessage>, val returned: Int)

/** Sessions of one gateway over REST. Blocking; call off the main thread. */
class SessionsRepository(private val http: AuthedHttp, baseUrl: String, profile: String? = null) {
    private val base = baseUrl.trimEnd('/')
    private val profileName = profileParam(profile)

    private fun HttpUrl.Builder.withProfile(): HttpUrl.Builder = apply {
        profileName?.let { addQueryParameter("profile", it) }
    }

    private fun String.withProfile(): HttpUrl = toHttpUrl().newBuilder().withProfile().build()

    /** [archived] is the gateway's filter: `exclude` (default), `only` or `include`. */
    fun list(archived: String = "exclude"): Result<List<Session>> = call(
        Request.Builder().url(
            "$base/api/sessions".toHttpUrl().newBuilder()
                .addQueryParameter("order", "recent")
                .addQueryParameter("archived", archived)
                // Human chats only: Desktop hides scheduled/automated sources from recents.
                .addQueryParameter("exclude_sources", "cron")
                .withProfile()
                .build(),
        ).build(),
    ) { parse(it.getJSONArray("sessions"), "id") }.map { it.pinnedFirst() }

    fun search(query: String): Result<List<Session>> = call(
        Request.Builder().url(
            "$base/api/sessions/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", "20")
                .withProfile()
                .build(),
        ).build(),
    ) { parse(it.getJSONArray("results"), "session_id") }

    fun rename(id: String, title: String): Result<Unit> = patch(id, JSONObject().put("title", title))

    fun setPinned(id: String, pinned: Boolean): Result<Unit> = patch(id, JSONObject().put("pinned", pinned))

    fun setArchived(id: String, archived: Boolean): Result<Unit> = patch(id, JSONObject().put("archived", archived))

    // PATCH has no query parameter for the profile: it is a body key.
    private fun patch(id: String, body: JSONObject): Result<Unit> = call(
        Request.Builder().url("$base/api/sessions/$id")
            .patch(body.apply { profileName?.let { put("profile", it) } }.toString().toRequestBody("application/json".toMediaType()))
            .build(),
    ) { }

    fun delete(id: String): Result<Unit> = call(
        Request.Builder().url("$base/api/sessions/$id".withProfile()).delete().build(),
    ) { }

    /**
     * One page of the durable transcript, chronological inside the page. `order=latest` makes
     * offset 0 the newest page and each larger offset an older one; images are never inlined on mobile.
     */
    fun messages(id: String, offset: Int = 0, limit: Int = HISTORY_PAGE): Result<HistoryPage> = call(
        Request.Builder().url(
            "$base/api/sessions/$id/messages".toHttpUrl().newBuilder()
                .addQueryParameter("order", "latest")
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("inline_images", "false")
                .withProfile()
                .build(),
        ).build(),
    ) { body ->
        val rows = body.getJSONArray("messages")
        val kept = (0 until rows.length()).map { rows.getJSONObject(it) }.mapNotNull(::historyRow)
        // Rows are filtered above, so paging must go by the raw count.
        HistoryPage(kept, body.optJSONObject("pagination")?.optInt("returned", rows.length()) ?: rows.length())
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
            active = row.optBoolean("is_active", false),
            unread = row.optBoolean("unread", false),
            messageCount = row.optInt("message_count", 1),
            cwd = if (row.isNull("cwd")) null else row.optString("cwd").ifBlank { null },
        )
    }

    private fun <T> call(request: Request, map: (JSONObject) -> T): Result<T> = try {
        Result.success(
            http.execute(request).use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw HttpFailure(response.code)
                }
                map(JSONObject(text))
            },
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}
