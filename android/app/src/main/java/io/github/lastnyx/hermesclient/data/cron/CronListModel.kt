package io.github.lastnyx.hermesclient.data.cron

import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class CronListState(
    val jobs: List<CronJob> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val busy: Set<String> = emptySet(),
    val runs: Map<String, List<CronRun>> = emptyMap(),
    val openRuns: String? = null,
)

class CronListModel(private val repo: CronRepository, private val io: CoroutineDispatcher) {
    private val _state = MutableStateFlow(CronListState())
    val state: StateFlow<CronListState> = _state

    suspend fun load() {
        _state.update { it.copy(loading = true) }
        val result = withContext(io) { repo.jobs() }
        result.onSuccess { jobs -> _state.update { it.copy(jobs = jobs, loading = false, error = null) } }
        result.onFailure { e ->
            val text = if (e.message == CANT_REACH_GATEWAY) CANT_REACH_GATEWAY else "Couldn't load scheduled jobs."
            _state.update { it.copy(loading = false, error = text) }
        }
    }

    suspend fun runNow(id: String) = act(id) { repo.trigger(id) }

    suspend fun pause(id: String) = act(id) { repo.pause(id) }

    suspend fun resume(id: String) = act(id) { repo.resume(id) }

    private suspend fun act(id: String, call: () -> Result<Unit>) {
        _state.update { it.copy(busy = it.busy + id) }
        val result = withContext(io) { call() }
        _state.update { it.copy(busy = it.busy - id) }
        val failure = result.exceptionOrNull()
        if (failure == null) {
            _state.update { it.copy(notice = null) }
            load()
            return
        }
        val http = failure as? CronHttpError
        when {
            http?.code == 409 && http.detail.contains("already running", ignoreCase = true) ->
                _state.update { it.copy(notice = "Already running") }
            http?.code == 404 -> {
                _state.update { it.copy(notice = "Job not found") }
                load()
            }
            failure.message == CANT_REACH_GATEWAY -> _state.update { it.copy(notice = CANT_REACH_GATEWAY) }
            else -> _state.update { it.copy(notice = "Couldn't update the job.") }
        }
    }

    suspend fun toggleRuns(id: String) {
        if (_state.value.openRuns == id) {
            _state.update { it.copy(openRuns = null) }
            return
        }
        _state.update { it.copy(openRuns = id) }
        val result = withContext(io) { repo.runs(id) }
        result.onSuccess { runs -> _state.update { it.copy(runs = it.runs + (id to runs)) } }
        result.onFailure {
            _state.update { it.copy(runs = it.runs + (id to emptyList()), notice = "Couldn't load recent runs.") }
        }
    }
}
