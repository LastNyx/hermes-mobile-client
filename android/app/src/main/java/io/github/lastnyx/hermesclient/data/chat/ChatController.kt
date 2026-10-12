package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.live.RpcError
import io.github.lastnyx.hermesclient.data.sessions.SessionsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/** One open chat: history, live events, submit / steer / stop. [storedId] is null for a new chat. */
class ChatController(
    private val link: LiveLink,
    private val repo: SessionsRepository,
    storedId: String?,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow(ChatState(storedId = storedId))
    val state: StateFlow<ChatState> = _state

    init {
        // Subscribe before any call so no event between a reply and the first collect is lost.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            link.events.collect { event ->
                if (event.sessionId != null && event.sessionId == _state.value.sessionId) {
                    _state.update { reduceChat(it, event) }
                }
            }
        }
    }

    suspend fun open() {
        try {
            val stored = _state.value.storedId
            if (stored == null) {
                val result = link.call("session.create", JSONObject())
                adopt(result)
            } else {
                val history = withContext(io) { repo.messages(stored) }.getOrThrow()
                _state.update { it.copy(items = history.mapIndexed { i, m -> ChatItem.Message("m$i", m.role, m.text) }) }
                resume()
            }
        } catch (e: Exception) {
            fail(e)
        }
    }

    suspend fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val wasRunning = _state.value.running
        _state.update { it.copy(items = it.items + ChatItem.Message("m${it.items.size}", "user", trimmed), running = true, error = null) }
        try {
            if (wasRunning) {
                val reply = sessionCall("session.steer") { it.put("text", trimmed) }
                // Turn ended between tap and call: the text is a normal prompt then.
                if (reply.optString("status") == "rejected") submit(trimmed)
            } else {
                submit(trimmed)
            }
        } catch (e: Exception) {
            fail(e)
        }
    }

    suspend fun stop() {
        try {
            sessionCall("session.interrupt") { it }
        } catch (e: Exception) {
            fail(e)
        }
    }

    private suspend fun submit(text: String) {
        sessionCall("prompt.submit") { it.put("text", text) }
    }

    /** Session-scoped call; 4001 means the runtime id is stale, so resume by stored id and retry once. */
    private suspend fun sessionCall(method: String, build: (JSONObject) -> JSONObject): JSONObject {
        fun params() = build(JSONObject().put("session_id", _state.value.sessionId ?: throw IOException("Chat not ready")))
        return try {
            link.call(method, params())
        } catch (e: RpcError) {
            if (e.code != 4001) throw e
            resume()
            link.call(method, params())
        }
    }

    private suspend fun resume() {
        val stored = _state.value.storedId ?: throw IOException("Chat not ready")
        adopt(link.call("session.resume", JSONObject().put("session_id", stored).put("inline_images", false).put("omit_messages", true)))
    }

    private fun adopt(result: JSONObject) {
        _state.update {
            it.copy(
                sessionId = result.getString("session_id"),
                storedId = result.optString("stored_session_id").ifEmpty { it.storedId },
                running = result.optBoolean("running", false),
            )
        }
    }

    private fun fail(e: Exception) {
        val message = if (e is IOException) "Can't reach the gateway. Check your connection." else e.message ?: "Something went wrong"
        _state.update { it.copy(error = message, running = false) }
    }
}
