package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LinkEvent
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.live.ServerRequest
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
import org.json.JSONArray
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

    private val lock = Any()
    private var lastSeen: Long? = null
    private var heldEpoch: String? = null
    private var replaying = false
    private val held = mutableListOf<LinkEvent>()

    init {
        // Subscribe before any call so no event between a reply and the first collect is lost.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            link.events.collect { event ->
                if (event.sessionId != null && event.sessionId == _state.value.sessionId) {
                    synchronized(lock) { if (replaying) held += event else apply(event) }
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            link.state.collect { link ->
                // Not yet opened means this is the first connect, which open() handles.
                if (link == LinkState.Connected && _state.value.sessionId != null) scope.launch { catchUp() }
            }
        }
    }

    /** Applies one event unless its seq was already seen. Callers hold [lock]. */
    private fun apply(event: LinkEvent) {
        val seq = event.seq
        val seen = lastSeen
        if (seq != null) {
            if (seen != null && seq <= seen) return
            lastSeen = seq
        }
        _state.update { reduceChat(it, event) }
    }

    private suspend fun catchUp() {
        synchronized(lock) { replaying = true }
        try {
            val seen = lastSeen
            if (link.replayEpoch != heldEpoch || seen == null) {
                reload(null)
                return
            }
            val reply = try {
                link.call("session.events.since", JSONObject().put("session_id", _state.value.sessionId).put("last_seen", seen))
            } catch (e: RpcError) {
                if (e.code != 4001) throw e
                reload(null)
                return
            }
            readRequests(reply)
            if (reply.optBoolean("truncated")) {
                reload(reply.optLong("latest_seq"))
                return
            }
            val events = reply.optJSONArray("events") ?: JSONArray()
            synchronized(lock) {
                for (i in 0 until events.length()) {
                    val e = events.getJSONObject(i)
                    apply(LinkEvent(e.optString("type"), e.optString("session_id").ifEmpty { null }, e.optJSONObject("payload"), if (e.has("seq")) e.optLong("seq") else null))
                }
            }
        } catch (e: Exception) {
            fail(e)
        } finally {
            synchronized(lock) {
                replaying = false
                held.forEach { apply(it) }
                held.clear()
            }
        }
    }

    private suspend fun reload(watermark: Long?) {
        val stored = _state.value.storedId ?: throw IOException("Chat not ready")
        val history = withContext(io) { repo.messages(stored) }.getOrThrow()
        _state.update { it.copy(items = history.mapIndexed { i, m -> ChatItem.Message("m$i", m.role, m.text) }) }
        resume()
        synchronized(lock) { held.clear(); lastSeen = watermark }
    }

    private fun readRequests(result: JSONObject) {
        val array = result.optJSONArray("open_requests") ?: return
        val requests = (0 until array.length()).map { array.getJSONObject(it) }.map {
            ServerRequest(it.optString("id"), it.optString("method"), it.optJSONObject("params") ?: JSONObject())
        }
        _state.update { it.copy(openRequests = requests) }
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
        adopt(link.call("session.resume", JSONObject().put("session_id", stored)))
    }

    private fun adopt(result: JSONObject) {
        synchronized(lock) {
            // A new server process restarts seq numbering, so the old watermark is meaningless.
            if (link.replayEpoch != heldEpoch) lastSeen = null
            heldEpoch = link.replayEpoch
        }
        readRequests(result)
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
