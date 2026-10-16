package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.friendlyError
import io.github.lastnyx.hermesclient.data.live.LinkEvent
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import io.github.lastnyx.hermesclient.data.live.ServerRequest
import io.github.lastnyx.hermesclient.data.live.RpcError
import io.github.lastnyx.hermesclient.data.sessions.HISTORY_PAGE
import io.github.lastnyx.hermesclient.data.sessions.HistoryMessage
import io.github.lastnyx.hermesclient.data.sessions.SessionsRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

sealed interface AttachSendResult {
    data object Sent : AttachSendResult
    data class Failed(val message: String, val text: String) : AttachSendResult
}

private const val ANSWER_NOT_SENT = "Couldn't send your answer. Check your connection and try again."

/** One open chat: history, live events, submit / redirect / stop. [storedId] is null for a new chat. */
class ChatController(
    private val link: LiveLink,
    private val repo: SessionsRepository,
    storedId: String?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val processPollMs: Long = 5000,
    deltaBatchMs: Long = DELTA_BATCH_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newChatCwd: String? = null,
) {
    private var noticeStore = NoticeStore()
    private val batcher = DeltaBatcher(deltaBatchMs) { System.nanoTime() / 1_000_000 }
    private var timer: Job? = null
    private val queueLock = Any()
    private val queue = MessageQueue()
    private var drainPending = false
    private var drainBlocked = false
    private var sendNowId: String? = null
    private var sendNowReady = false

    private val _state = MutableStateFlow(ChatState(storedId = storedId))
    val state: StateFlow<ChatState> = _state

    private val lock = Any()
    private var lastSeen: Long? = null
    private var heldEpoch: String? = null
    private var replaying = false
    private val held = mutableListOf<LinkEvent>()
    private val withdrawn = ConcurrentHashMap.newKeySet<String>()
    private val declined = ConcurrentHashMap.newKeySet<String>()

    init {
        // Subscribe before any call so no event between a reply and the first collect is lost.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            link.events.collect { event ->
                // request.cancel carries no session id; srq- ids are unique, so matching on id is enough.
                if (event.type == "request.cancel") withdraw(event.payload?.optString("id").orEmpty())
                if (event.type == "approval.cancelled" && event.sessionId == _state.value.sessionId) {
                    val ids = event.payload?.optJSONArray("request_ids")
                    val cancelled = ids?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                    _state.update { s -> s.copy(openRequests = s.openRequests.filterNot { it.method == "approval" && it.params.optString("request_id") in cancelled }) }
                    scope.launch { drainQueue() }
                }
                if (event.sessionId != null && event.sessionId == _state.value.sessionId) {
                    synchronized(lock) { if (replaying) held += event else apply(event) }
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            link.serverRequests.collect { request ->
                // Right after resume a request can beat the reply that tells us our runtime session id.
                val forSession = request.params.optString("session_id")
                val mine = _state.value.sessionId
                if (forSession.isEmpty() || mine == null || forSession == mine) admit(listOf(request))
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            link.state.collect { link ->
                // Not yet opened means this is the first connect, which open() handles.
                if (link == LinkState.Connected && _state.value.sessionId != null) scope.launch {
                    // session.events.since replays but does not re-attach: only activate re-binds the new socket.
                    if (!reattach()) catchUp()
                    // Process changes during the drop are otherwise missed; kept out of adopt() so catch-up stays untouched.
                    refreshProcesses()
                }
            }
        }
        // Poll only while something runs; collectLatest cancels the loop when the last one ends.
        scope.launch {
            _state.map { s -> s.processes.any { it.state == ProcessState.Running } }.distinctUntilChanged().collectLatest { running ->
                while (running) {
                    delay(processPollMs)
                    refreshProcesses()
                }
            }
        }
    }

    /** Re-reads the session's background processes; a failure leaves the old list. */
    suspend fun refreshProcesses() {
        try {
            val reply = sessionCall("process.list") { it }
            val incoming = parseProcesses(reply)
            val key = processKey()
            if (key != null && reply.optJSONArray("processes") != null) {
                DismissedProcesses.prune(key, incoming.map { it.id }.toSet())
            }
            val dismissed = key?.let { DismissedProcesses.ids(it) }.orEmpty()
            _state.update { it.copy(processes = mergeProcesses(incoming, dismissed)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to show: the card simply stays as it was.
        }
    }

    suspend fun killProcess(id: String) {
        try {
            val reply = sessionCall("process.kill") { it.put("process_id", id) }
            if (reply.optString("status") == "error") return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        refreshProcesses()
    }

    fun dismissProcess(id: String) {
        val row = _state.value.processes.firstOrNull { it.id == id } ?: return
        if (row.state == ProcessState.Running) return
        val key = processKey() ?: return
        DismissedProcesses.dismiss(key, id)
        _state.update { s -> s.copy(processes = s.processes.filterNot { it.id == id }) }
    }

    // The stored id survives resume; the runtime id does not.
    private fun processKey(): String? = _state.value.let { it.storedId ?: it.sessionId }

    private fun processEvent(event: LinkEvent) {
        val kind = event.payload?.optString("kind")
        if (event.type == "tool.complete" || (event.type == "status.update" && kind == "process")) {
            scope.launch { refreshProcesses() }
        }
    }

    /** Callers hold [lock]. */
    private fun noticeEvent(event: LinkEvent) {
        when (event.type) {
            "notification.show" -> {
                val before = noticeStore.next
                noticeStore = noticeStore.add(event, clock())
                if (noticeStore.next == before) return
                publishNotices()
                val due = noticeStore.notices.lastOrNull { it.uid == before }?.expiresAt ?: return
                scope.launch {
                    delay((due - clock()).coerceAtLeast(0))
                    synchronized(lock) {
                        noticeStore = noticeStore.expire(clock())
                        publishNotices()
                    }
                }
            }
            "notification.clear" -> {
                val key = event.payload?.stringOrNull("key") ?: return
                noticeStore = noticeStore.clear(key)
                publishNotices()
            }
        }
    }

    private fun publishNotices() = _state.update { it.copy(notices = noticeStore.notices) }

    fun dismissNotice(uid: Long) {
        synchronized(lock) {
            noticeStore = noticeStore.dismiss(uid)
            publishNotices()
        }
    }

    /** Applies one event unless its seq was already seen. Callers hold [lock]. */
    private fun apply(event: LinkEvent, detectGap: Boolean = true): Boolean {
        val seq = event.seq
        val seen = lastSeen
        if (seq != null) {
            if (seen != null && seq <= seen) return true
            if (detectGap && seqGap(seen, seq)) {
                // The since-reply returns the missing events and this one, so it is dropped here.
                replaying = true
                scope.launch { catchUp() }
                return false
            }
            lastSeen = seq
        }
        val ready = batcher.offer(event)
        ready.forEach { applyNow(it) }
        if (ready.isEmpty()) batcher.msUntilDue()?.let { armTimer(it) }
        return true
    }

    private fun applyNow(event: LinkEvent) {
        _state.update { reduceChat(it, event) }
        processEvent(event)
        noticeEvent(event)
        if (event.type == "message.complete") turnEnded(event.payload?.optString("status").orEmpty())
    }

    /** Callers hold [lock]. */
    private fun flushPending() = batcher.flush().forEach { applyNow(it) }

    private fun settle() = synchronized(lock) { flushPending() }

    private fun armTimer(ms: Long) {
        if (timer?.isActive == true) return
        timer = scope.launch {
            delay(ms)
            synchronized(lock) {
                timer = null
                flushPending()
            }
        }
    }

    /** Only a clean ending drains; after an error or an interrupt the user decides. */
    private fun turnEnded(status: String) {
        synchronized(queueLock) {
            if (sendNowId != null) sendNowReady = true
            else if (!drainBlocked && status != "error" && status != "interrupted" && queue.entries.isNotEmpty()) drainPending = true
        }
        scope.launch { drainQueue() }
    }

    private fun takeDue(): QueueEntry? = synchronized(queueLock) {
        val s = _state.value
        if (s.running) return null
        val id = sendNowId
        if (id != null) {
            if (!sendNowReady) return null
            sendNowId = null
            sendNowReady = false
            drainPending = false
            return queue.remove(id)?.also { publishQueue() }
        }
        if (drainPending && s.prompts.isEmpty()) {
            drainPending = false
            return queue.next()?.also { publishQueue() }
        }
        null
    }

    private suspend fun drainQueue() {
        val entry = takeDue() ?: return
        if (!deliver(entry.text, entry.text)) {
            // Put it back and wait for the user, as after an error ending.
            synchronized(queueLock) {
                drainBlocked = true
                queue.addFirst(entry.text)?.let { publishQueue() }
            }
        }
    }

    private fun publishQueue() = _state.update { it.copy(queue = queue.entries) }

    /** Parks [text] to be sent after the running turn. */
    fun queue(text: String) {
        synchronized(queueLock) { queue.add(text)?.let { publishQueue() } }
    }

    fun removeQueued(id: String) {
        synchronized(queueLock) { if (queue.remove(id) != null) publishQueue() }
    }

    /** Takes the entry out of the queue and returns its text, for the composer. */
    fun takeQueued(id: String): String? = synchronized(queueLock) { queue.remove(id)?.also { publishQueue() }?.text }

    /** Stops the running turn, then submits exactly this entry once the turn has ended. */
    suspend fun sendNow(id: String) {
        unblockDrain()
        val entry = synchronized(queueLock) {
            if (_state.value.running) {
                sendNowId = id
                sendNowReady = false
                return@synchronized null
            }
            queue.remove(id)?.also { publishQueue() }
        }
        if (entry != null) {
            deliver(entry.text, entry.text)
            return
        }
        if (synchronized(queueLock) { sendNowId } != id) return
        if (!stop()) {
            synchronized(queueLock) { sendNowId = null; sendNowReady = false }
            return
        }
        if (!_state.value.running) {
            synchronized(queueLock) { sendNowReady = true }
            drainQueue()
        }
    }

    /** True when the session was gone and history was reloaded, so the since call is not needed. */
    private suspend fun reattach(): Boolean {
        try {
            link.call("session.activate", JSONObject().put("session_id", _state.value.sessionId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcError) {
            if (e.code != 4001 && e.code != 4007) return false
            try {
                reload(null)
            } catch (c: CancellationException) {
                throw c
            } catch (x: Exception) {
                fail(x)
            }
            return true
        } catch (e: IOException) {
            // The next reconnect or event gap tries again.
        }
        return false
    }

    private suspend fun catchUp() {
        synchronized(lock) {
            flushPending()
            replaying = true
        }
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
                    apply(LinkEvent(e.optString("type"), e.optString("session_id").ifEmpty { null }, e.optJSONObject("payload"), if (e.has("seq")) e.optLong("seq") else null), detectGap = false)
                }
            }
        } catch (e: Exception) {
            fail(e)
        } finally {
            synchronized(lock) {
                replaying = false
                for (e in held) { if (!apply(e)) break }
                held.clear()
            }
        }
    }

    private suspend fun reload(watermark: Long?) {
        val stored = _state.value.storedId ?: throw IOException("Chat not ready")
        loadHistory(stored)
        resume()
        synchronized(lock) { held.clear(); lastSeen = watermark }
    }

    private var history: List<HistoryMessage> = emptyList()
    private var historyOffset = 0
    private var historyGeneration = 0
    private val loadingOlder = AtomicBoolean(false)

    /** [fromEnd] counts back from the newest history row: prepending older rows never shifts it, so id-less keys stay unique. */
    private fun historyItem(m: HistoryMessage, fromEnd: Int) =
        ChatItem.Message(m.rowId?.let { "h$it" } ?: "h-$fromEnd", m.role, if (m.role == "user") historyText(m.text) else m.text, rowId = m.rowId)

    private suspend fun loadHistory(stored: String) {
        val page = withContext(io) { repo.messages(stored) }.getOrThrow()
        synchronized(lock) {
            flushPending()
            historyGeneration++
            history = page.messages
            historyOffset = page.returned
        }
        _state.update { it.copy(items = page.messages.mapIndexed { i, m -> historyItem(m, page.messages.size - 1 - i) }, hasOlder = page.returned >= HISTORY_PAGE) }
    }

    /** Prepends the next older page; live items are never touched. One load at a time. */
    suspend fun loadOlder() {
        if (!_state.value.hasOlder || !loadingOlder.compareAndSet(false, true)) return
        try {
            val stored = _state.value.storedId ?: return
            val (generation, offset) = synchronized(lock) { historyGeneration to historyOffset }
            val page = try {
                withContext(io) { repo.messages(stored, offset = offset, limit = HISTORY_PAGE) }.getOrThrow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't load earlier messages") }
                return
            }
            synchronized(lock) {
                // A reload replaced the transcript while this page was in flight.
                if (generation != historyGeneration) return
                val merged = mergeHistory(history, page.messages)
                val added = merged.take(merged.size - history.size).mapIndexed { i, m -> historyItem(m, merged.size - 1 - i) }
                history = merged
                historyOffset += page.returned
                _state.update { it.copy(items = added + it.items, hasOlder = page.returned >= HISTORY_PAGE) }
            }
        } finally {
            loadingOlder.set(false)
        }
    }

    private fun readRequests(result: JSONObject) {
        val array = result.optJSONArray("open_requests") ?: return
        val requests = (0 until array.length()).map { array.getJSONObject(it) }.map {
            ServerRequest(it.optString("id"), it.optString("method"), it.optJSONObject("params") ?: JSONObject())
        }
        admit(requests)
    }

    /** Merges requests into the pending list by id; sudo/secret are declined on the spot. */
    private fun admit(requests: List<ServerRequest>) {
        for (request in requests) {
            if (request.id in withdrawn) continue
            if (request.method == "sudo" || request.method == "secret") {
                if (declined.add(request.id)) {
                    settle()
                    link.respond(request.id, JSONObject().put("value", ""))
                    _state.update { it.copy(items = it.items + ChatItem.Note("n${it.items.size}", "Answer this on the PC")) }
                }
            } else if (request.method == "approval" || request.method == "clarify") {
                _state.update { s -> if (s.openRequests.any { it.id == request.id }) s else s.copy(openRequests = s.openRequests + request) }
            }
        }
    }

    private fun withdraw(id: String) {
        // The request and its cancel travel on separate flows, so the cancel can be seen first.
        withdrawn.add(id)
        _state.update { s -> s.copy(openRequests = s.openRequests.filterNot { it.id == id }) }
        scope.launch { drainQueue() }
    }

    private fun resolve(id: String, result: JSONObject, note: String) {
        if (_state.value.openRequests.none { it.id == id }) return
        if (!link.respond(id, result)) {
            _state.update { it.copy(error = ANSWER_NOT_SENT) }
            return
        }
        settle()
        _state.update { s ->
            s.copy(
                openRequests = s.openRequests.filterNot { it.id == id },
                items = s.items + ChatItem.Note("n${s.items.size}", note),
            )
        }
        scope.launch { drainQueue() }
    }

    /** [choice] is one of once, session, always, deny. */
    fun answerApproval(id: String, choice: String) {
        val note = when (choice) {
            "once" -> "You allowed once"
            "session" -> "You allowed for this session"
            "always" -> "You always allowed this"
            else -> "You denied this"
        }
        resolve(id, JSONObject().put("choice", choice), note)
    }

    /** Sends nothing until every question id has an answer: the gateway wants one result for all of them. */
    fun answerClarify(id: String, answers: Map<String, String>) {
        val prompt = _state.value.prompts.filterIsInstance<Prompt.Clarify>().firstOrNull { it.id == id } ?: return
        if (prompt.questions.any { answers[it.qid].isNullOrBlank() }) return
        val body = JSONObject()
        answers.forEach { (qid, answer) -> body.put(qid, answer) }
        resolve(id, JSONObject().put("answers", body), "You answered: " + answers.values.joinToString(", "))
    }

    suspend fun open() {
        try {
            val stored = _state.value.storedId
            if (stored == null) {
                val result = link.call("session.create", JSONObject().apply { if (!newChatCwd.isNullOrBlank()) put("cwd", newChatCwd) })
                adopt(result)
            } else {
                loadHistory(stored)
                resume()
            }
            scope.launch { refreshProcesses() }
        } catch (e: Exception) {
            fail(e)
        }
    }

    /**
     * Text that could not be sent is queued and counts as handled. False only when a plain
     * submit failed, so the caller can hand the text back to the user.
     */
    suspend fun send(text: String): Boolean {
        val trimmed = text.trim()
        unblockDrain()
        return deliver(trimmed, trimmed)
    }

    private fun unblockDrain() = synchronized(queueLock) { drainBlocked = false }

    /** [message] goes to the model; the bubble shows [display] (message may be hidden scaffolding). */
    suspend fun sendWithDisplay(message: String, display: String) {
        unblockDrain()
        deliver(message, display)
    }

    /** Reloads the stored transcript, e.g. after /undo removed turns on the gateway. */
    internal suspend fun reloadHistory() {
        settle()
        loadHistory(_state.value.storedId ?: throw IOException("Chat not ready"))
    }

    /** Cuts the chat back to before the user message [rowId] and sends [newText] in its place. */
    suspend fun rewind(rowId: Long, newText: String): RewindResult {
        val text = newText.trim()
        if (text.isEmpty()) return RewindResult.Empty
        settle()
        val before = _state.value
        if (before.running) return RewindResult.Busy
        val at = before.items.indexOfFirst { it is ChatItem.Message && it.role == "user" && it.rowId == rowId }
        if (at < 0) return RewindResult.Stale
        val first = !before.hasOlder && before.items.take(at).none { it is ChatItem.Message && it.role == "user" }
        val result = try {
            sessionCall("prompt.submit") { p -> rewindParams(rowId, text, first).let { extra -> extra.keys().forEach { k -> p.put(k, extra.get(k)) } }; p }
            RewindResult.Sent
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcError) {
            rewindResultFor(e)
        } catch (e: IOException) {
            RewindResult.Unreachable
        } catch (e: Exception) {
            RewindResult.Failed
        }
        if (result == RewindResult.Sent) {
            settle()
            _state.update { s ->
                val kept = s.items.take(at)
                s.copy(items = kept + ChatItem.Message("m${kept.size}", "user", text), running = true, error = null)
            }
        } else if (result == RewindResult.Stale) {
            try {
                reloadHistory()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The chat stays as it was; the next open reads the history again.
            }
        }
        return result
    }

    /** Stored id of a new chat holding the turns up to the one at user message [key], or null (with a fixed error text). */
    suspend fun branch(key: String): String? {
        settle()
        val point = branchCount(_state.value.items, key)
        val id = if (point == null) null else try {
            val reply = if (point.whole) sessionCall("session.branch_whole") { it }
            else sessionCall("session.branch") { it.put("count", point.count) }
            branchedId(reply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (id == null) _state.update { it.copy(error = "Couldn't branch this chat.") }
        return id
    }

    /**
     * Text that cannot go now (redirect rejected or failed, or a prompt is open) is queued.
     * Returns false only when a plain submit failed, so the caller can keep the text.
     */
    private suspend fun deliver(message: String, display: String, dropBubbleOnFailure: Boolean = false): Boolean {
        if (message.isEmpty()) return true
        settle()
        val before = _state.value
        if (before.running && before.prompts.isNotEmpty()) {
            queue(message)
            return true
        }
        val bubbleId = "m${before.items.size}"
        _state.update { it.copy(items = it.items + ChatItem.Message(bubbleId, "user", display), running = true, error = null) }
        fun dropBubble() = _state.update { s -> s.copy(items = s.items.filterNot { it.key == bubbleId }) }
        try {
            if (before.running) {
                val reply = sessionCall("session.redirect") { it.put("text", message) }
                if (reply.optString("status") == "rejected") {
                    dropBubble()
                    queue(message)
                }
            } else {
                submit(message)
            }
        } catch (e: Exception) {
            if (!before.running) {
                if (dropBubbleOnFailure) {
                    dropBubble()
                    _state.update { it.copy(running = false) }
                    return false
                }
                fail(e)
                return false
            }
            dropBubble()
            // The turn we tried to redirect is still running, so leave `running` alone.
            _state.update { it.copy(error = errorText(e)) }
            queue(message)
        }
        return true
    }

    /** Puts local text (e.g. slash command output) into the chat without sending it. */
    fun addNote(text: String) {
        settle()
        _state.update { it.copy(items = it.items + ChatItem.Note("n${it.items.size}", text)) }
    }

    fun addCommandOutput(command: String, text: String, failed: Boolean) {
        settle()
        _state.update { it.copy(items = it.items + ChatItem.CommandOutput("n${it.items.size}", command, text, failed)) }
    }

    /** True when the gateway accepted the interrupt. `running` is left to events: a lost Stop means the turn still runs. */
    suspend fun stop(): Boolean {
        try {
            sessionCall("session.interrupt") { it }
            _state.update { it.copy(error = null) }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = errorText(e)) }
            return false
        }
    }

    private suspend fun submit(text: String) {
        sessionCall("prompt.submit") { it.put("text", text) }
    }

    /** Session-scoped call; 4001 means the runtime id is stale, so resume by stored id and retry once. */
    internal suspend fun sessionCall(method: String, timeoutMs: Long = 120_000, build: (JSONObject) -> JSONObject): JSONObject {
        fun params() = build(JSONObject().put("session_id", _state.value.sessionId ?: throw IOException("Chat not ready")))
        return try {
            link.call(method, params(), timeoutMs)
        } catch (e: RpcError) {
            if (e.code != 4001) throw e
            resume()
            link.call(method, params(), timeoutMs)
        }
    }

    /** Uploads first, then submits one prompt; on any failure nothing stays half-sent and [AttachSendResult.Failed.text] goes back to the user. */
    suspend fun sendWithAttachments(text: String, attachments: List<PreparedAttachment>): AttachSendResult {
        if (_state.value.running) return AttachSendResult.Failed("Wait for the reply to finish before sending attachments.", text)
        unblockDrain()
        val sender = AttachmentSender { method, timeoutMs, build -> sessionCall(method, timeoutMs, build) }
        val uploaded = when (val r = sender.upload(attachments)) {
            is UploadResult.Failed -> return AttachSendResult.Failed(r.message, text)
            is UploadResult.Ok -> r
        }
        val prompt = buildPrompt(text, uploaded.refTexts, attachments.count { it.kind == AttachmentKind.Image })
        if (!deliver(prompt, bubbleText(text, attachments), dropBubbleOnFailure = true)) {
            sender.detach(uploaded.imagePaths)
            return AttachSendResult.Failed("Couldn't send your message. Try again.", text)
        }
        return AttachSendResult.Sent
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

    private fun errorText(e: Exception) = friendlyError(e)

    private fun fail(e: Exception) {
        _state.update { it.copy(error = errorText(e), running = false) }
    }
}
