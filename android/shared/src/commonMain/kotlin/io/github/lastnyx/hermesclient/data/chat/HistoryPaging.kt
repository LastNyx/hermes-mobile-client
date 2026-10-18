package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.sessions.HistoryMessage

/** Older rows go in front; a row already in [current] (same row id) is dropped. Rows without an id are always kept. */
fun mergeHistory(current: List<HistoryMessage>, older: List<HistoryMessage>): List<HistoryMessage> {
    val known = current.mapNotNull { it.rowId }.toSet()
    return older.filter { it.rowId == null || it.rowId !in known } + current
}
