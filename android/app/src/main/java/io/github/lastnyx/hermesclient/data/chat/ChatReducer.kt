package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LinkEvent
import io.github.lastnyx.hermesclient.data.live.ServerRequest

enum class ToolStatus { Running, Done }

sealed interface ChatItem {
    val key: String

    data class Message(override val key: String, val role: String, val text: String, val streaming: Boolean = false) : ChatItem

    data class Tool(
        override val key: String,
        val toolId: String,
        val name: String,
        val status: ToolStatus,
        val detail: String = "",
    ) : ChatItem
}

data class ChatState(
    val sessionId: String? = null,
    val storedId: String? = null,
    val items: List<ChatItem> = emptyList(),
    val running: Boolean = false,
    val error: String? = null,
    /** Pending approval/clarify prompts the gateway still holds; the screen for them is a later ticket. */
    val openRequests: List<ServerRequest> = emptyList(),
)

/** Pure: applies one live-link event for this chat's session. */
fun reduceChat(state: ChatState, event: LinkEvent): ChatState {
    val payload = event.payload
    return when (event.type) {
        "message.start" -> state.copy(running = true, error = null)
        "message.delta" -> {
            val text = payload?.optString("text").orEmpty()
            val last = state.items.lastOrNull()
            if (last is ChatItem.Message && last.role == "assistant" && last.streaming) {
                state.copy(items = state.items.dropLast(1) + last.copy(text = last.text + text), running = true)
            } else {
                state.copy(items = sealed(state.items) + ChatItem.Message("m${state.items.size}", "assistant", text, true), running = true)
            }
        }
        "tool.start" -> {
            val id = payload?.optString("tool_id").orEmpty()
            state.copy(
                items = sealed(state.items) + ChatItem.Tool("t$id", id, payload?.optString("name").orEmpty(), ToolStatus.Running),
                running = true,
            )
        }
        "tool.complete" -> {
            val id = payload?.optString("tool_id").orEmpty()
            val detail = payload?.optString("result_text").orEmpty().ifEmpty { payload?.optString("summary").orEmpty() }
            val name = payload?.optString("name").orEmpty()
            val done = state.items.map { if (it is ChatItem.Tool && it.toolId == id) it.copy(status = ToolStatus.Done, detail = detail) else it }
            if (done.any { it is ChatItem.Tool && it.toolId == id }) {
                state.copy(items = done)
            } else {
                state.copy(items = state.items + ChatItem.Tool("t$id", id, name, ToolStatus.Done, detail))
            }
        }
        "message.complete" -> {
            val items = sealed(state.items)
            val turnHasReply = items.takeLastWhile { !(it is ChatItem.Message && it.role == "user") }
                .any { it is ChatItem.Message && it.role == "assistant" }
            val text = payload?.optString("text").orEmpty()
            val finished = if (!turnHasReply && text.isNotBlank()) items + ChatItem.Message("m${items.size}", "assistant", text) else items
            val failure = if (payload?.optString("status") == "error") {
                payload.optString("error").ifEmpty { "The turn failed" }
            } else null
            state.copy(items = finished, running = false, error = failure)
        }
        "error" -> state.copy(error = payload?.optString("message").orEmpty().ifEmpty { "Something went wrong" }, running = false)
        else -> state
    }
}

private fun sealed(items: List<ChatItem>): List<ChatItem> =
    items.map { if (it is ChatItem.Message && it.streaming) it.copy(streaming = false) else it }
