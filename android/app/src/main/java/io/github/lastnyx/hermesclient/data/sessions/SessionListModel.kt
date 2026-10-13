package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.live.LinkState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class SessionListState(
    val sessions: List<Session> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
)

/** Holds the visible session list for one gateway; every call runs the blocking repository on [io]. */
class SessionListModel(private val repo: SessionsRepository, private val io: CoroutineDispatcher) {
    private val _state = MutableStateFlow(SessionListState())
    val state: StateFlow<SessionListState> = _state

    suspend fun load() = fetch("") { repo.list() }

    suspend fun refresh() = fetch(_state.value.query) { current ->
        if (current.isBlank()) repo.list() else repo.search(current)
    }

    /** A reconnect may have missed changes, so refresh; skip if a load is already running. */
    suspend fun onLinkState(link: LinkState) {
        if (link == LinkState.Connected && !_state.value.loading) refresh()
    }

    suspend fun search(query: String) = fetch(query) { if (it.isBlank()) repo.list() else repo.search(it.trim()) }

    suspend fun rename(id: String, title: String) {
        withContext(io) { repo.rename(id, title) }.fold(
            { _state.value = _state.value.copy(error = null, sessions = _state.value.sessions.map { if (it.id == id) it.copy(title = title) else it }) },
            { _state.value = _state.value.copy(error = it.message ?: "Rename failed") },
        )
    }

    suspend fun delete(id: String) {
        withContext(io) { repo.delete(id) }.fold(
            { _state.value = _state.value.copy(error = null, sessions = _state.value.sessions.filter { it.id != id }) },
            { _state.value = _state.value.copy(error = it.message ?: "Delete failed") },
        )
    }

    private suspend fun fetch(query: String, call: (String) -> Result<List<Session>>) {
        _state.value = _state.value.copy(loading = true, query = query)
        val result = withContext(io) { call(query) }
        _state.value = result.fold(
            { _state.value.copy(sessions = it, loading = false, error = null) },
            { _state.value.copy(loading = false, error = it.message ?: "Could not load sessions") },
        )
    }
}
