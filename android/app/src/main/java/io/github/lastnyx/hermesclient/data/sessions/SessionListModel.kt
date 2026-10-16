package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.friendlyError
import io.github.lastnyx.hermesclient.data.live.LinkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SessionListState(
    val sessions: List<Session> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val archivedPage: Boolean = false,
    /** Null = the gateway's projects are unknown, so grouping falls back to the cwd folder name. */
    val projects: List<Project>? = null,
)

/** Holds the visible session list for one gateway; every call runs the blocking repository on [io]. */
class SessionListModel(
    private val repo: SessionsRepository,
    private val io: CoroutineDispatcher,
    private val rpc: (suspend (method: String, params: JSONObject) -> JSONObject)? = null,
) {
    private val _state = MutableStateFlow(SessionListState())
    val state: StateFlow<SessionListState> = _state

    private fun listing(): Result<List<Session>> =
        repo.list(if (_state.value.archivedPage) "only" else "exclude")

    suspend fun load() {
        fetch("") { listing() }
        loadProjects()
    }

    suspend fun refresh() {
        val query = _state.value.query
        fetch(query) { current ->
            if (current.isBlank() || _state.value.archivedPage) listing() else repo.search(current)
        }
        if (query.isBlank() && !_state.value.archivedPage) loadProjects()
    }

    /** Any failure (old gateway, offline, timeout) just means no gateway projects; never an error line. */
    suspend fun loadProjects() {
        val call = rpc
        val projects = if (call == null) null else try {
            parseProjects(call("projects.tree", JSONObject().put("session_limit", 2000)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _state.value = _state.value.copy(projects = projects)
    }

    /** Null on success, else the sentence to show; never touches `state.error`. */
    suspend fun createProject(name: String, folder: String?): String? {
        val call = rpc ?: return "Could not create the project."
        val title = name.trim()
        if (title.isEmpty()) return "Give the project a name."
        val params = JSONObject().put("name", title)
        if (!folder.isNullOrBlank()) params.put("folders", JSONArray().put(folder.trim()))
        try {
            call("projects.create", params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return projectCreateMessage(e)
        }
        loadProjects()
        return null
    }

    /** A reconnect may have missed changes, so refresh; skip if a load is already running. */
    suspend fun onLinkState(link: LinkState) {
        if (link != LinkState.Connected) return
        if (_state.value.loading) loadProjects() else refresh()
    }

    suspend fun search(query: String) = fetch(query) { if (it.isBlank()) repo.list() else repo.search(it.trim()) }

    suspend fun rename(id: String, title: String) {
        withContext(io) { repo.rename(id, title) }.fold(
            { _state.value = _state.value.copy(error = null, sessions = _state.value.sessions.map { if (it.id == id) it.copy(title = title) else it }) },
            { _state.value = _state.value.copy(error = friendlyError(it)) },
        )
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val before = _state.value.sessions
        _state.value = _state.value.copy(error = null, sessions = before.map { if (it.id == id) it.copy(pinned = pinned) else it }.pinnedFirst())
        withContext(io) { repo.setPinned(id, pinned) }.onFailure {
            _state.value = _state.value.copy(sessions = before, error = friendlyError(it))
        }
    }

    /** Archiving or unarchiving takes the row out of the page being shown; Undo on the main list reloads it. */
    suspend fun setArchived(id: String, archived: Boolean) {
        val before = _state.value.sessions
        _state.value = _state.value.copy(error = null, sessions = before.filter { it.id != id })
        withContext(io) { repo.setArchived(id, archived) }.fold(
            { if (!archived && !_state.value.archivedPage) refresh() },
            { _state.value = _state.value.copy(sessions = before, error = friendlyError(it)) },
        )
    }

    suspend fun openArchived() {
        _state.value = _state.value.copy(archivedPage = true, sessions = emptyList(), query = "")
        fetch("") { repo.list(archived = "only") }
    }

    suspend fun closeArchived() {
        _state.value = _state.value.copy(archivedPage = false, sessions = emptyList())
        load()
    }

    suspend fun delete(id: String) {
        withContext(io) { repo.delete(id) }.fold(
            { _state.value = _state.value.copy(error = null, sessions = _state.value.sessions.filter { it.id != id }) },
            { _state.value = _state.value.copy(error = friendlyError(it)) },
        )
    }

    private suspend fun fetch(query: String, call: (String) -> Result<List<Session>>) {
        _state.value = _state.value.copy(loading = true, query = query)
        val result = withContext(io) { call(query) }
        _state.value = result.fold(
            { _state.value.copy(sessions = it, loading = false, error = null) },
            { _state.value.copy(loading = false, error = friendlyError(it)) },
        )
    }
}
