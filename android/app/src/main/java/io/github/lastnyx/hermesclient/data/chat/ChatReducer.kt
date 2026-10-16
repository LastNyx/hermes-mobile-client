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
        val preview: String = "",
        val durationS: Double? = null,
    ) : ChatItem

    data class Reasoning(override val key: String, val text: String, val streaming: Boolean = false) : ChatItem

    /** Short line left behind by an answered prompt ("You allowed once"). */
    data class Note(override val key: String, val text: String) : ChatItem

    /** Result of a slash command, shown as a card headed by the command. */
    data class CommandOutput(override val key: String, val command: String, val text: String, val failed: Boolean = false) : ChatItem
}

data class ClarifyQuestion(val qid: String, val question: String, val choices: List<String>, val multiSelect: Boolean)

/** What a pending prompt card needs; derived from the raw server request. */
sealed interface Prompt {
    val id: String

    data class Approval(
        override val id: String,
        val command: String,
        val description: String,
        val choices: List<String>,
        val toolName: String,
    ) : Prompt

    data class Clarify(override val id: String, val questions: List<ClarifyQuestion>) : Prompt
}

fun ServerRequest.toPrompt(): Prompt? = when (method) {
    "approval" -> Prompt.Approval(
        id,
        params.optString("command"),
        params.optString("description"),
        params.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            ?: listOf("once", "session", "always", "deny"),
        params.optString("tool_name"),
    )
    "clarify" -> Prompt.Clarify(
        id,
        params.optJSONArray("questions")?.let { qs ->
            (0 until qs.length()).map { i ->
                val q = qs.getJSONObject(i)
                ClarifyQuestion(
                    q.optString("qid"),
                    q.optString("question"),
                    q.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    q.optBoolean("multi_select"),
                )
            }
        } ?: emptyList(),
    )
    else -> null
}

data class ChatState(
    val sessionId: String? = null,
    val storedId: String? = null,
    val items: List<ChatItem> = emptyList(),
    val running: Boolean = false,
    val error: String? = null,
    /** Pending approval/clarify requests (replayed and live, keyed by `srq-` id). */
    val openRequests: List<ServerRequest> = emptyList(),
    /** Drafts waiting for the running turn to end; in memory only. */
    val queue: List<QueueEntry> = emptyList(),
    val processes: List<BackgroundProcess> = emptyList(),
    val hasOlder: Boolean = false,
) {
    val prompts: List<Prompt> get() = openRequests.mapNotNull { it.toPrompt() }
}

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
        "reasoning.delta", "thinking.delta" -> {
            val text = payload?.optString("text").orEmpty()
            val last = state.items.lastOrNull()
            when {
                text.isEmpty() -> state
                last is ChatItem.Reasoning && last.streaming ->
                    state.copy(items = state.items.dropLast(1) + last.copy(text = last.text + text), running = true)
                else -> state.copy(items = sealed(state.items) + ChatItem.Reasoning("r${state.items.size}", text, true), running = true)
            }
        }
        "reasoning.available" -> {
            val text = payload?.optString("text").orEmpty()
            val last = state.items.lastOrNull()
            when {
                text.isEmpty() -> state
                last is ChatItem.Reasoning && last.streaming ->
                    state.copy(items = state.items.dropLast(1) + last.copy(text = text, streaming = false))
                else -> state.copy(items = sealed(state.items) + ChatItem.Reasoning("r${state.items.size}", text))
            }
        }
        "tool.start" -> {
            val id = payload?.optString("tool_id").orEmpty()
            val preview = payload?.optString("preview").orEmpty().ifEmpty { payload?.optString("context").orEmpty() }
                .lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            state.copy(
                items = sealed(state.items) +
                    ChatItem.Tool("t$id", id, payload?.optString("name").orEmpty(), ToolStatus.Running, preview = preview),
                running = true,
            )
        }
        "tool.complete" -> {
            val id = payload?.optString("tool_id").orEmpty()
            val detail = payload?.optString("result_text").orEmpty().ifEmpty { payload?.optString("summary").orEmpty() }
            val name = payload?.optString("name").orEmpty()
            val duration = if (payload?.has("duration_s") == true && !payload.isNull("duration_s")) payload.optDouble("duration_s") else null
            val done = state.items.map {
                if (it is ChatItem.Tool && it.toolId == id) it.copy(status = ToolStatus.Done, detail = detail, durationS = duration) else it
            }
            if (done.any { it is ChatItem.Tool && it.toolId == id }) {
                state.copy(items = done)
            } else {
                state.copy(items = state.items + ChatItem.Tool("t$id", id, name, ToolStatus.Done, detail, durationS = duration))
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
    items.map {
        when {
            it is ChatItem.Message && it.streaming -> it.copy(streaming = false)
            it is ChatItem.Reasoning && it.streaming -> it.copy(streaming = false)
            else -> it
        }
    }
