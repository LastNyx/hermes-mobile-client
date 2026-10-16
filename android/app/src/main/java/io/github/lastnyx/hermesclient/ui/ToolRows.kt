package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.ChatItem
import io.github.lastnyx.hermesclient.data.chat.ToolStatus

internal fun toolLabel(name: String): String {
    val words = name.trim().replace('_', ' ').trim()
    return if (words.isEmpty()) "Tool" else words.replaceFirstChar { it.uppercase() }
}

internal fun durationText(seconds: Double): String {
    val s = seconds.coerceAtLeast(0.0)
    // Round first so 59.96 reads "1 min", not "60.0 s".
    val tenths = Math.round(s * 10) / 10.0
    return when {
        tenths < 60 -> "%.1f s".format(java.util.Locale.ROOT, tenths)
        else -> {
            val total = Math.round(s).toInt()
            val sec = total % 60
            if (sec == 0) "${total / 60} min" else "${total / 60} min $sec s"
        }
    }
}

internal fun reasoningPreview(text: String, streaming: Boolean): String {
    // A "\r\n" pair reads as one empty line between two separators, which is skipped like any blank line.
    fun isBreak(c: Char) = c == '\n' || c == '\r'
    if (streaming) {
        var end = text.length
        while (true) {
            var start = end
            while (start > 0 && !isBreak(text[start - 1])) start--
            val line = text.substring(start, end).trim()
            if (line.isNotEmpty()) return line
            if (start == 0) return ""
            end = start - 1
        }
    }
    var start = 0
    while (true) {
        var end = start
        while (end < text.length && !isBreak(text[end])) end++
        val line = text.substring(start, end).trim()
        if (line.isNotEmpty()) return line
        if (end >= text.length) return ""
        start = end + 1
    }
}

internal const val TOOL_STILL_RUNNING = "Still running…"

internal fun toolExpandedText(status: ToolStatus, detail: String): String? = when {
    detail.isNotBlank() -> detail
    status == ToolStatus.Running -> TOOL_STILL_RUNNING
    else -> null
}

internal sealed interface ChatRow {
    val key: String
    val first: ChatItem
    val last: ChatItem

    data class Single(val item: ChatItem) : ChatRow {
        override val key get() = item.key
        override val first get() = item
        override val last get() = item
    }

    data class ToolGroup(val tools: List<ChatItem.Tool>) : ChatRow {
        override val key get() = "g${tools.first().key}"
        override val first get() = tools.first()
        override val last get() = tools.last()
        val running get() = tools.any { it.status == ToolStatus.Running }
    }
}

internal const val MIN_GROUPED_TOOLS = 3

internal fun groupTools(items: List<ChatItem>): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var i = 0
    while (i < items.size) {
        if (items[i] !is ChatItem.Tool) {
            rows += ChatRow.Single(items[i++])
            continue
        }
        var end = i
        while (end < items.size && items[end] is ChatItem.Tool) end++
        val run = items.subList(i, end).filterIsInstance<ChatItem.Tool>()
        if (run.size >= MIN_GROUPED_TOOLS) rows += ChatRow.ToolGroup(run) else run.forEach { rows += ChatRow.Single(it) }
        i = end
    }
    return rows
}
