package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.chat.stringOrNull
import org.json.JSONArray
import org.json.JSONObject

private const val WRAPPER_START = "[OUT-OF-BAND USER MESSAGE"
private const val WRAPPER_END = "[/OUT-OF-BAND USER MESSAGE]"
private const val IMPORTANT = "[IMPORTANT: Background process "

/** Rows Hermes wrote for the model are shown the way Hermes Desktop shows them; null drops the row. */
fun historyRow(row: JSONObject): HistoryMessage? {
    val role = row.optString("role")
    val kind = row.optString("display_kind")
    if ((role != "user" && role != "assistant") || kind == "hidden") return null
    val text = rowText(row)
    // optLong would turn a missing or null id into 0.
    val rowId = if (row.isNull("id")) null else row.optLong("id")
    if (role == "user") {
        val trimmed = text.trim()
        when {
            kind == "steer" -> {
                val shown = row.stringOrNull("display_content")?.takeIf { it.isNotBlank() } ?: unwrapSteer(text)
                return HistoryMessage(role, shown, rowId)
            }
            kind == "process_complete" -> return notice(row, "Background process finished")
            kind == "async_delegation_complete" -> return notice(row, "Background agent finished")
            trimmed.startsWith("[Background process ") && " heartbeat #" in trimmed.lineSequence().first() -> return null
            trimmed.startsWith(IMPORTANT) ->
                return HistoryMessage(role, trimmed.lineSequence().first().removePrefix("[IMPORTANT: ").trimEnd('.', ']'), null, notice = true)
        }
    }
    return if (text.isBlank()) null else HistoryMessage(role, text, rowId)
}

private fun notice(row: JSONObject, fallback: String): HistoryMessage {
    val metadata = when (val raw = row.opt("display_metadata")) {
        is JSONObject -> raw
        is String -> try { JSONObject(raw) } catch (e: Exception) { null }
        else -> null
    }
    val text = metadata?.stringOrNull("display_text")?.takeIf { it.isNotBlank() } ?: fallback
    return HistoryMessage("user", text, null, notice = true)
}

private fun unwrapSteer(text: String): String {
    val lines = text.lines()
    val start = lines.indexOfFirst { it.startsWith(WRAPPER_START) }
    val end = lines.indexOfFirst { it == WRAPPER_END }
    if (start < 0 || end <= start) return text
    return lines.subList(start + 1, end).joinToString("\n").trim()
}

/** REST rows are raw DB rows: the body is `content` (string or list of parts); `text` only exists on RPC snapshots. */
internal fun rowText(row: JSONObject): String {
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
