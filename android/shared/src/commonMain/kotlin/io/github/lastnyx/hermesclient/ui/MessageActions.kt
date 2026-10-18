package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.ChatItem

data class MessageActions(val edit: Set<String>, val regenerate: String?, val retry: String?)

/** Edit and Regenerate need a stored row id; all three need an idle chat (the gateway answers 4009 busy). */
fun messageActions(items: List<ChatItem>, running: Boolean): MessageActions {
    if (running) return MessageActions(emptySet(), null, null)
    val messages = items.filterIsInstance<ChatItem.Message>()
    val edit = messages.filter { it.role == "user" && it.rowId != null }.map { it.key }.toSet()
    val lastReply = messages.lastOrNull { it.role != "user" && it.text.isNotEmpty() && !it.streaming }
    val regenerate = lastReply?.takeIf { promptBefore(items, it.key)?.rowId != null }
    return MessageActions(edit, regenerate?.key, lastReply?.key)
}

/** The nearest user message before the item with [replyKey]; tools, reasoning and notes in between are skipped. */
fun promptBefore(items: List<ChatItem>, replyKey: String): ChatItem.Message? {
    val at = items.indexOfFirst { it.key == replyKey }
    if (at < 0) return null
    return items.subList(0, at).lastOrNull { it is ChatItem.Message && it.role == "user" } as ChatItem.Message?
}
