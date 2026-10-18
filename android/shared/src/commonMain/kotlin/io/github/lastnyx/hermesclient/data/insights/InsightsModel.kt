package io.github.lastnyx.hermesclient.data.insights

import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

val INSIGHT_DAYS = listOf(7, 30, 90)

const val INSIGHTS_FAILED = "Could not load insights"

data class InsightsState(
    val days: Int = 30,
    val loading: Boolean = false,
    val data: UsageInsights? = null,
    val error: String? = null,
)

class InsightsModel(private val repo: InsightsRepository, private val io: CoroutineDispatcher) {
    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state

    suspend fun load() {
        _state.update { it.copy(loading = true, error = null) }
        val result = withContext(io) { repo.usage(_state.value.days) }
        result.onSuccess { data -> _state.update { it.copy(loading = false, data = data, error = null) } }
        result.onFailure { e ->
            val text = if (e.message == CANT_REACH_GATEWAY) CANT_REACH_GATEWAY else INSIGHTS_FAILED
            _state.update { it.copy(loading = false, data = null, error = text) }
        }
    }

    suspend fun setDays(days: Int) {
        if (days !in INSIGHT_DAYS) return
        _state.update { it.copy(days = days) }
        load()
    }
}
