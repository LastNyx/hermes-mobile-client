package io.github.lastnyx.hermesclient.data.hub

import io.github.lastnyx.hermesclient.data.gateway.Gateway
import org.json.JSONArray

/** [sessionId] is the stored id the sessions list opens (`session_key`, else the runtime `id`). */
data class NeedsMeItem(val gateway: Gateway, val sessionId: String, val title: String?, val since: Long?)

/** Waiting sessions from each gateway's `session.active_list` rows, oldest first; rows without a time go last. */
fun needsMe(lists: Map<Gateway, JSONArray>): List<NeedsMeItem> =
    lists.flatMap { (gateway, rows) ->
        (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            .filter { it.optString("status") == "waiting" }
            .mapNotNull { row ->
                val id = row.optString("session_key").ifEmpty { row.optString("id") }
                if (id.isEmpty()) return@mapNotNull null
                NeedsMeItem(
                    gateway,
                    id,
                    row.optString("title").ifBlank { null },
                    if (row.has("last_active") && !row.isNull("last_active")) row.optLong("last_active") else null,
                )
            }
    }.sortedBy { it.since ?: Long.MAX_VALUE }
