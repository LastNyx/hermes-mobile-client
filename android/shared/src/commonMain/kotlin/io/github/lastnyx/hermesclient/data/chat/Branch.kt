package io.github.lastnyx.hermesclient.data.chat

import org.json.JSONObject

/** [count] Messages are kept; [whole] means the turn is the last one, so the gateway needs no count. */
data class BranchPoint(val count: Int, val whole: Boolean)

/** The point for "keep through the turn that starts at user message [key]", or null when [key] is not a user Message. */
fun branchCount(items: List<ChatItem>, key: String): BranchPoint? {
    val messages = items.filterIsInstance<ChatItem.Message>()
    val at = messages.indexOfFirst { it.key == key }
    if (at < 0 || messages[at].role != "user") return null
    val last = if (messages.getOrNull(at + 1)?.role == "assistant") at + 1 else at
    return BranchPoint(last + 1, last == messages.lastIndex)
}

/** The stored id of the new chat, or null when the reply has none (JSON null counts as none). */
fun branchedId(result: JSONObject): String? =
    if (result.isNull("stored_session_id")) null else result.optString("stored_session_id").ifBlank { null }
