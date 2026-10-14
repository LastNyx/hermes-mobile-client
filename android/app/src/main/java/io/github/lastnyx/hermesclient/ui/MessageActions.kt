package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.ChatItem

internal data class LastActions(val retryKey: String?, val editKey: String?)

/** Only the last reply gets Retry and only the last user message gets the menu; /retry and /undo need an idle chat. */
internal fun lastActions(items: List<ChatItem>, running: Boolean): LastActions {
    if (running) return LastActions(null, null)
    val messages = items.filterIsInstance<ChatItem.Message>()
    val lastUser = messages.lastOrNull { it.role == "user" } ?: return LastActions(null, null)
    val lastReply = messages.lastOrNull { it.role != "user" && it.text.isNotEmpty() && !it.streaming }
    return LastActions(lastReply?.key, lastUser.key)
}
