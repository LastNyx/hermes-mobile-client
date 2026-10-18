package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.ChatItem
import io.github.lastnyx.hermesclient.data.chat.ToolStatus

fun toolLabel(name: String): String {
    val words = name.trim().replace('_', ' ').trim()
    return if (words.isEmpty()) "Tool" else words.replaceFirstChar { it.uppercase() }
}

fun durationText(seconds: Double): String {
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

fun reasoningPreview(text: String, streaming: Boolean): String {
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

const val TOOL_STILL_RUNNING = "Still running…"

fun toolExpandedText(status: ToolStatus, detail: String): String? = when {
    detail.isNotBlank() -> detail
    status == ToolStatus.Running -> TOOL_STILL_RUNNING
    else -> null
}

sealed interface ChatRow {
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

const val LIVE_STEPS_SHOWN = 4

data class ChatRows(val rows: List<ChatRow>, val earlierSteps: Int, val earlierBeforeKey: String?)

private fun isUser(item: ChatItem) = item is ChatItem.Message && item.role == "user"

private fun isFinishedStep(item: ChatItem) = when (item) {
    is ChatItem.Tool -> item.status == ToolStatus.Done
    is ChatItem.Reasoning -> !item.streaming
    else -> false
}

fun turnKey(items: List<ChatItem>): String? = items.lastOrNull(::isUser)?.key

fun earlierStepsText(count: Int): String = if (count == 1) "1 earlier step" else "$count earlier steps"

fun chatRows(
    items: List<ChatItem>,
    running: Boolean,
    showEarlier: Boolean,
    shown: Int = LIVE_STEPS_SHOWN,
): ChatRows {
    if (!running) return ChatRows(groupTools(items), 0, null)
    val turnStart = items.indexOfLast(::isUser) + 1
    val before = groupTools(items.subList(0, turnStart))
    val turn = items.subList(turnStart, items.size)
    val dropCount = if (showEarlier) 0 else (turn.count(::isFinishedStep) - shown).coerceAtLeast(0)
    if (dropCount == 0) return ChatRows(before + turn.map { ChatRow.Single(it) }, 0, null)
    var dropped = 0
    var firstDropped = -1
    val kept = mutableListOf<ChatItem>()
    var beforeKey: String? = null
    turn.forEachIndexed { i, item ->
        if (dropped < dropCount && isFinishedStep(item)) {
            if (firstDropped < 0) firstDropped = i
            dropped++
        } else {
            if (beforeKey == null && firstDropped >= 0) beforeKey = item.key
            kept += item
        }
    }
    return ChatRows(before + kept.map { ChatRow.Single(it) }, dropCount, beforeKey)
}

const val MIN_GROUPED_TOOLS = 3

fun groupTools(items: List<ChatItem>): List<ChatRow> {
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
