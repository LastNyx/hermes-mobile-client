package io.github.lastnyx.hermesclient.data.live

import io.github.lastnyx.hermesclient.data.auth.AuthState
import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

sealed interface LinkState {
    data object Connecting : LinkState
    data object Connected : LinkState
    data object Offline : LinkState
    data object SignedOut : LinkState
    data class Error(val message: String) : LinkState
}

class RpcError(val code: Int, message: String) : Exception(message)

data class LinkEvent(val type: String, val sessionId: String?, val payload: JSONObject?, val seq: Long?)

data class ServerRequest(val id: String, val method: String, val params: JSONObject)

/** One chat socket to a gateway: ticket auth, handshake, heartbeat, backoff reconnect, JSON-RPC calls. */
class LiveLink(
    http: OkHttpClient,
    private val authed: AuthedHttp,
    baseUrl: String,
    private val scope: CoroutineScope,
    private val heartbeatMs: Long = 15_000,
    private val silenceMs: Long = 45_000,
    private val connectTimeoutMs: Long = 15_000,
    private val random: () -> Double = { Math.random() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val base = baseUrl.trimEnd('/')
    private val wsClient = http.newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).build()
    private val _state = MutableStateFlow<LinkState>(LinkState.Offline)
    val state: StateFlow<LinkState> = _state
    private val _events = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<LinkEvent> = _events
    private val _requests = MutableSharedFlow<ServerRequest>(extraBufferCapacity = 64)
    val serverRequests: SharedFlow<ServerRequest> = _requests

    /** Identifies the server process; ticket 07 compares it across reconnects. */
    @Volatile var replayEpoch: String? = null
        private set

    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    @Volatile private var socket: WebSocket? = null
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        socket?.cancel()
        socket = null
        _state.value = LinkState.Offline
    }

    suspend fun call(method: String, params: JSONObject = JSONObject(), timeoutMs: Long = 120_000): JSONObject {
        val ws = socket ?: throw IOException("Offline")
        val id = "r${nextId.getAndIncrement()}"
        val waiter = CompletableDeferred<JSONObject>()
        pending[id] = waiter
        val frame = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params)
        if (!ws.send(frame.toString())) {
            pending.remove(id)
            throw IOException("Offline")
        }
        return try {
            withTimeout(timeoutMs) { waiter.await() }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Timed out")
        } finally {
            pending.remove(id)
        }
    }

    fun respond(id: String, result: JSONObject) {
        val frame = JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)
        socket?.send(frame.toString())
    }

    private suspend fun run() {
        var attempt = 0
        while (scope.isActive) {
            _state.value = LinkState.Connecting
            val ticket = when (val minted = withContext(Dispatchers.IO) { mintTicket() }) {
                is Minted.Ok -> minted.ticket
                Minted.SignedOut -> { _state.value = LinkState.SignedOut; return }
                Minted.Failed -> null
            }
            if (ticket != null) {
                val openedAt = clock()
                val code = connectOnce(ticket)
                if (code == 4403) {
                    _state.value = LinkState.Error("Gateway refused the chat connection (check its configuration)")
                    return
                }
                if (clock() - openedAt >= 5_000) attempt = 0
            }
            _state.value = LinkState.Offline
            delay((random() * min(15_000.0, 300.0 * (1L shl min(attempt, 20)))).toLong())
            attempt++
        }
    }

    private sealed interface Minted {
        data class Ok(val ticket: String) : Minted
        data object SignedOut : Minted
        data object Failed : Minted
    }

    private fun mintTicket(): Minted {
        val request = Request.Builder().url("$base/api/auth/ws-ticket").post("".toRequestBody()).build()
        return try {
            authed.execute(request).use { response ->
                when {
                    response.code == 401 -> Minted.SignedOut
                    !response.isSuccessful -> Minted.Failed
                    else -> Minted.Ok(JSONObject(response.body!!.string()).getString("ticket"))
                }
            }
        } catch (e: IOException) {
            val auth = authed.state.value
            if (auth == AuthState.SignedOut || auth == AuthState.Expired) Minted.SignedOut else Minted.Failed
        } catch (e: Exception) {
            Minted.Failed
        }
    }

    /** Returns the close code, or -1 when the socket failed. */
    private suspend fun connectOnce(ticket: String): Int = coroutineScope {
        val done = CompletableDeferred<Int>()
        val ready = CompletableDeferred<Boolean>()
        val lastInbound = AtomicLong(clock())
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                lastInbound.set(clock())
                handleFrame(webSocket, text, ready)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                done.complete(code)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done.complete(code) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { done.complete(-1) }
        }
        val wsUrl = base.replaceFirst("http", "ws") + "/api/ws?ticket=" + ticket
        val ws = wsClient.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
        socket = ws
        val watchers = launch {
            if (withTimeoutOrNull(connectTimeoutMs) { ready.await() } == null) { ws.cancel(); return@launch }
            if (!ready.await()) return@launch
            while (true) {
                delay(heartbeatMs)
                val sentAt = clock()
                ws.send("""{"jsonrpc":"2.0","id":"heartbeat-$sentAt","method":"gateway.ping","params":{}}""")
                launch {
                    delay(silenceMs)
                    if (lastInbound.get() < sentAt) ws.cancel()
                }
            }
        }
        try {
            done.await()
        } finally {
            watchers.cancel()
            ws.cancel()
            socket = null
            val failure = IOException("Disconnected")
            pending.keys.toList().forEach { pending.remove(it)?.completeExceptionally(failure) }
        }
    }

    private fun handleFrame(ws: WebSocket, text: String, ready: CompletableDeferred<Boolean>) {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
        val method = frame.optString("method")
        val id = frame.opt("id") as? String
        when {
            method == "event" -> {
                val params = frame.optJSONObject("params") ?: return
                val type = params.optString("type")
                if (type == "gateway.ready") {
                    val payload = params.optJSONObject("payload")
                    replayEpoch = payload?.optString("replay_epoch")?.ifEmpty { null }
                    val caps = JSONObject().put("jsonrpc", "2.0").put("id", "r${nextId.getAndIncrement()}")
                        .put("method", "client.capabilities").put("params", JSONObject().put("server_requests", true))
                    ws.send(caps.toString())
                    _state.value = LinkState.Connected
                    ready.complete(payload?.optBoolean("heartbeat") == true)
                }
                _events.tryEmit(
                    LinkEvent(
                        type,
                        params.optString("session_id").ifEmpty { null },
                        params.optJSONObject("payload"),
                        if (params.has("seq")) params.optLong("seq") else null,
                    ),
                )
            }
            method.isNotEmpty() && id != null && id.startsWith("srq-") ->
                _requests.tryEmit(ServerRequest(id, method, frame.optJSONObject("params") ?: JSONObject()))
            id != null -> {
                val waiter = pending.remove(id) ?: return
                val error = frame.optJSONObject("error")
                if (error != null) waiter.completeExceptionally(RpcError(error.optInt("code"), error.optString("message")))
                else waiter.complete(frame.optJSONObject("result") ?: JSONObject())
            }
        }
    }
}
