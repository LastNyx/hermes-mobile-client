package io.github.lastnyx.hermesclient.data.hub

import io.github.lastnyx.hermesclient.data.gateway.Gateway
import io.github.lastnyx.hermesclient.data.gateway.GatewayRegistry
import io.github.lastnyx.hermesclient.data.live.LinkEvent
import io.github.lastnyx.hermesclient.data.live.LinkState
import io.github.lastnyx.hermesclient.data.live.LiveLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.json.JSONObject

interface HubLink {
    val state: StateFlow<LinkState>
    val events: SharedFlow<LinkEvent>
    fun start()
    fun stop()
    suspend fun call(method: String, params: JSONObject = JSONObject()): JSONObject
}

class LiveHubLink(private val link: LiveLink) : HubLink {
    override val state get() = link.state
    override val events get() = link.events
    override fun start() = link.start()
    override fun stop() = link.stop()
    override suspend fun call(method: String, params: JSONObject) = link.call(method, params, 15_000)
}

sealed interface GatewayState {
    val gateway: Gateway
    data class SignedOut(override val gateway: Gateway) : GatewayState
    data class Connecting(override val gateway: Gateway) : GatewayState
    data class Online(override val gateway: Gateway, val version: String?, val busy: Int) : GatewayState
    data class Unreachable(override val gateway: Gateway) : GatewayState
}

fun GatewayState.line(): String = when (this) {
    is GatewayState.SignedOut -> "Signed out"
    is GatewayState.Connecting -> "Connecting…"
    is GatewayState.Online -> listOfNotNull("Online", version?.let { "v$it" }, if (busy > 0) "$busy busy" else null).joinToString(" · ")
    is GatewayState.Unreachable -> "Unreachable (other network?)"
}

private val BUSY =setOf("starting", "working", "streaming", "resuming", "waiting")
private const val BUSY_REFRESH_MS = 10_000L

/** One live link per signed-in gateway; LiveLink does its own reconnect backoff, this only maps its state. */
class HubModel(
    private val registry: GatewayRegistry,
    private val signedIn: (String) -> Boolean,
    private val linkFor: (Gateway) -> HubLink,
    private val version: suspend (Gateway) -> String?,
    private val scope: CoroutineScope,
) {
    private class Entry(val link: HubLink, val job: Job)

    private val entries = mutableMapOf<String, Entry>()
    private val current = MutableStateFlow<Map<String, GatewayState>>(emptyMap())
    private val _states = MutableStateFlow<List<GatewayState>>(emptyList())
    val states: StateFlow<List<GatewayState>> = _states

    fun start() = refresh()

    /** Re-reads the registry and sign-in state; call after either changes. */
    @Synchronized
    fun refresh() {
        val gateways = registry.list()
        val wanted = gateways.filter { signedIn(it.id) }.associateBy { it.id }
        entries.keys.filter { it !in wanted }.forEach { id -> entries.remove(id)?.let { close(it) } }
        wanted.values.filter { it.id !in entries }.forEach { entries[it.id] = open(it) }
        val kept = current.value.filterKeys { it in wanted }
        current.value = kept
        publish(gateways)
    }

    private fun close(entry: Entry) {
        entry.job.cancel()
        entry.link.stop()
    }

    private fun open(gateway: Gateway): Entry {
        val link = linkFor(gateway)
        set(GatewayState.Connecting(gateway))
        val job = scope.launch {
            link.state.collectLatest { s ->
                when (s) {
                    LinkState.Connecting -> set(GatewayState.Connecting(gateway))
                    LinkState.SignedOut -> set(GatewayState.SignedOut(gateway))
                    LinkState.Offline, is LinkState.Error -> set(GatewayState.Unreachable(gateway))
                    LinkState.Connected -> watchBusy(gateway, link)
                }
            }
        }
        link.start()
        return Entry(link, job)
    }

    private suspend fun watchBusy(gateway: Gateway, link: HubLink) = coroutineScope {
        val v = runCatching { version(gateway) }.getOrNull()
        var busy = 0
        set(GatewayState.Online(gateway, v, busy))
        suspend fun refreshBusy() {
            val sessions = runCatching { link.call("session.active_list").optJSONArray("sessions") }.getOrNull() ?: return
            busy = (0 until sessions.length()).count { sessions.optJSONObject(it)?.optString("status") in BUSY }
            set(GatewayState.Online(gateway, v, busy))
        }
        launch { link.events.filter { it.type == "sessions.changed" }.collect { refreshBusy() } }
        while (true) {
            refreshBusy()
            delay(BUSY_REFRESH_MS)
        }
    }

    private fun set(state: GatewayState) {
        current.value = current.value + (state.gateway.id to state)
        publish(registry.list())
    }

    private fun publish(gateways: List<Gateway>) {
        _states.value = gateways.map { current.value[it.id] ?: GatewayState.SignedOut(it) }
    }
}
